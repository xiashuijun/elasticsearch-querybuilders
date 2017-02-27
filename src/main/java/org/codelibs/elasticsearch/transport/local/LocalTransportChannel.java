/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.codelibs.elasticsearch.transport.local;

import org.codelibs.elasticsearch.Version;
import org.codelibs.elasticsearch.common.bytes.BytesReference;
import org.codelibs.elasticsearch.common.io.stream.BytesStreamOutput;
import org.codelibs.elasticsearch.common.util.concurrent.ThreadContext;
import org.codelibs.elasticsearch.transport.RemoteTransportException;
import org.codelibs.elasticsearch.transport.TransportChannel;
import org.codelibs.elasticsearch.transport.TransportResponse;
import org.codelibs.elasticsearch.transport.TransportResponseOptions;
import org.codelibs.elasticsearch.transport.TransportServiceAdapter;
import org.codelibs.elasticsearch.transport.TransportStatus;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocalTransportChannel implements TransportChannel {

    private static final String LOCAL_TRANSPORT_PROFILE = "default";

    private final LocalTransport sourceTransport;
    private final TransportServiceAdapter sourceTransportServiceAdapter;
    // the transport we will *send to*
    private final LocalTransport targetTransport;
    private final String action;
    private final long requestId;
    private final Version version;
    private final long reservedBytes;
    private final ThreadContext threadContext;
    private final AtomicBoolean closed = new AtomicBoolean();

    public LocalTransportChannel(LocalTransport sourceTransport, TransportServiceAdapter sourceTransportServiceAdapter,
                                 LocalTransport targetTransport, String action, long requestId, Version version, long reservedBytes,
                                 ThreadContext threadContext) {
        this.sourceTransport = sourceTransport;
        this.sourceTransportServiceAdapter = sourceTransportServiceAdapter;
        this.targetTransport = targetTransport;
        this.action = action;
        this.requestId = requestId;
        this.version = version;
        this.reservedBytes = reservedBytes;
        this.threadContext = threadContext;
    }

    @Override
    public String action() {
        return action;
    }

    @Override
    public String getProfileName() {
        return LOCAL_TRANSPORT_PROFILE;
    }

    @Override
    public void sendResponse(TransportResponse response) throws IOException {
        sendResponse(response, TransportResponseOptions.EMPTY);
    }

    @Override
    public void sendResponse(TransportResponse response, TransportResponseOptions options) throws IOException {
        try (BytesStreamOutput stream = new BytesStreamOutput()) {
            stream.setVersion(version);
            stream.writeLong(requestId);
            byte status = 0;
            status = TransportStatus.setResponse(status);
            stream.writeByte(status); // 0 for request, 1 for response.
            threadContext.writeTo(stream);
            response.writeTo(stream);
            sendResponseData(BytesReference.toBytes(stream.bytes()));
            sourceTransportServiceAdapter.onResponseSent(requestId, action, response, options);
        }
    }

    @Override
    public void sendResponse(Exception exception) throws IOException {
        BytesStreamOutput stream = new BytesStreamOutput();
        stream.setVersion(version);
        writeResponseExceptionHeader(stream);
        RemoteTransportException tx = new RemoteTransportException(targetTransport.nodeName(),
                targetTransport.boundAddress().boundAddresses()[0], action, exception);
        stream.writeException(tx);
        sendResponseData(BytesReference.toBytes(stream.bytes()));
        sourceTransportServiceAdapter.onResponseSent(requestId, action, exception);
    }

    private void sendResponseData(byte[] data) {
        close();
        targetTransport.receiveMessage(version, data, action, null, sourceTransport);
    }

    private void close() {
        // attempt to close once atomically
        if (closed.compareAndSet(false, true) == false) {
            throw new IllegalStateException("Channel is already closed");
        }
        sourceTransport.inFlightRequestsBreaker().addWithoutBreaking(-reservedBytes);
    }

    @Override
    public long getRequestId() {
        return requestId;
    }

    @Override
    public String getChannelType() {
        return "local";
    }

    private void writeResponseExceptionHeader(BytesStreamOutput stream) throws IOException {
        stream.writeLong(requestId);
        byte status = 0;
        status = TransportStatus.setResponse(status);
        status = TransportStatus.setError(status);
        stream.writeByte(status);
        threadContext.writeTo(stream);
    }
}