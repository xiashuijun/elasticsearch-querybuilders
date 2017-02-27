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

package org.codelibs.elasticsearch.rest.action.document;

import org.codelibs.elasticsearch.action.ActionRequestValidationException;
import org.codelibs.elasticsearch.action.get.GetRequest;
import org.codelibs.elasticsearch.action.get.GetResponse;
import org.codelibs.elasticsearch.client.node.NodeClient;
import org.codelibs.elasticsearch.common.inject.Inject;
import org.codelibs.elasticsearch.common.settings.Settings;
import org.codelibs.elasticsearch.common.xcontent.XContentBuilder;
import org.codelibs.elasticsearch.rest.BaseRestHandler;
import org.codelibs.elasticsearch.rest.BytesRestResponse;
import org.codelibs.elasticsearch.rest.RestController;
import org.codelibs.elasticsearch.rest.RestRequest;
import org.codelibs.elasticsearch.rest.RestResponse;
import org.codelibs.elasticsearch.rest.action.RestResponseListener;
import org.codelibs.elasticsearch.search.fetch.subphase.FetchSourceContext;

import java.io.IOException;

import static org.codelibs.elasticsearch.rest.RestRequest.Method.GET;
import static org.codelibs.elasticsearch.rest.RestStatus.NOT_FOUND;
import static org.codelibs.elasticsearch.rest.RestStatus.OK;

public class RestGetSourceAction extends BaseRestHandler {

    @Inject
    public RestGetSourceAction(Settings settings, RestController controller) {
        super(settings);
        controller.registerHandler(GET, "/{index}/{type}/{id}/_source", this);
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        final GetRequest getRequest = new GetRequest(request.param("index"), request.param("type"), request.param("id"));
        getRequest.operationThreaded(true);
        getRequest.refresh(request.paramAsBoolean("refresh", getRequest.refresh()));
        getRequest.routing(request.param("routing"));  // order is important, set it after routing, so it will set the routing
        getRequest.parent(request.param("parent"));
        getRequest.preference(request.param("preference"));
        getRequest.realtime(request.paramAsBoolean("realtime", getRequest.realtime()));

        getRequest.fetchSourceContext(FetchSourceContext.parseFromRestRequest(request));

        return channel -> {
            if (getRequest.fetchSourceContext() != null && !getRequest.fetchSourceContext().fetchSource()) {
                ActionRequestValidationException validationError = new ActionRequestValidationException();
                validationError.addValidationError("fetching source can not be disabled");
                channel.sendResponse(new BytesRestResponse(channel, validationError));
            } else {
                client.get(getRequest, new RestResponseListener<GetResponse>(channel) {
                    @Override
                    public RestResponse buildResponse(GetResponse response) throws Exception {
                        XContentBuilder builder = channel.newBuilder(response.getSourceInternal(), false);
                        if (response.isSourceEmpty()) { // check if doc source (or doc itself) is missing
                            return new BytesRestResponse(NOT_FOUND, builder);
                        } else {
                            builder.rawValue(response.getSourceInternal());
                            return new BytesRestResponse(OK, builder);
                        }
                    }
                });
            }
        };
    }
}