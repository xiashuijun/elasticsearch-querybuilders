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

package org.codelibs.elasticsearch;

import org.codelibs.elasticsearch.action.support.replication.ReplicationOperation;
import org.codelibs.elasticsearch.cluster.action.shard.ShardStateAction;
import org.codelibs.elasticsearch.common.io.stream.StreamInput;
import org.codelibs.elasticsearch.common.io.stream.StreamOutput;
import org.codelibs.elasticsearch.common.io.stream.Writeable;
import org.codelibs.elasticsearch.common.logging.LoggerMessageFormat;
import org.codelibs.elasticsearch.common.xcontent.ToXContent;
import org.codelibs.elasticsearch.common.xcontent.XContentBuilder;
import org.codelibs.elasticsearch.common.xcontent.XContentParser;
import org.codelibs.elasticsearch.index.Index;
import org.codelibs.elasticsearch.index.shard.ShardId;
import org.codelibs.elasticsearch.rest.RestStatus;
import org.codelibs.elasticsearch.transport.TcpTransport;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.unmodifiableMap;
import static org.codelibs.elasticsearch.cluster.metadata.IndexMetaData.INDEX_UUID_NA_VALUE;
import static org.codelibs.elasticsearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.codelibs.elasticsearch.common.xcontent.XContentParserUtils.throwUnknownField;

/**
 * A base class for all elasticsearch exceptions.
 */
public class ElasticsearchException extends RuntimeException implements ToXContent, Writeable {

    static final Version UNKNOWN_VERSION_ADDED = Version.fromId(0);

    /**
     * Passed in the {@link Params} of {@link #toXContent(XContentBuilder, org.codelibs.elasticsearch.common.xcontent.ToXContent.Params, Throwable)}
     * to control if the {@code caused_by} element should render. Unlike most parameters to {@code toXContent} methods this parameter is
     * internal only and not available as a URL parameter.
     */
    public static final String REST_EXCEPTION_SKIP_CAUSE = "rest.exception.cause.skip";
    /**
     * Passed in the {@link Params} of {@link #toXContent(XContentBuilder, org.codelibs.elasticsearch.common.xcontent.ToXContent.Params, Throwable)}
     * to control if the {@code stack_trace} element should render. Unlike most parameters to {@code toXContent} methods this parameter is
     * internal only and not available as a URL parameter. Use the {@code error_trace} parameter instead.
     */
    public static final String REST_EXCEPTION_SKIP_STACK_TRACE = "rest.exception.stacktrace.skip";
    public static final boolean REST_EXCEPTION_SKIP_STACK_TRACE_DEFAULT = true;
    public static final boolean REST_EXCEPTION_SKIP_CAUSE_DEFAULT = false;
    private static final String INDEX_HEADER_KEY = "es.index";
    private static final String INDEX_HEADER_KEY_UUID = "es.index_uuid";
    private static final String SHARD_HEADER_KEY = "es.shard";
    private static final String RESOURCE_HEADER_TYPE_KEY = "es.resource.type";
    private static final String RESOURCE_HEADER_ID_KEY = "es.resource.id";

    private static final String TYPE = "type";
    private static final String REASON = "reason";
    private static final String CAUSED_BY = "caused_by";
    private static final String STACK_TRACE = "stack_trace";
    private static final String HEADER = "header";
    private static final String ERROR = "error";
    private static final String ROOT_CAUSE = "root_cause";

    private static final Map<Integer, FunctionThatThrowsIOException<StreamInput, ? extends ElasticsearchException>> ID_TO_SUPPLIER;
    private static final Map<Class<? extends ElasticsearchException>, ElasticsearchExceptionHandle> CLASS_TO_ELASTICSEARCH_EXCEPTION_HANDLE;
    private final Map<String, List<String>> headers = new HashMap<>();

    /**
     * Construct a <code>ElasticsearchException</code> with the specified cause exception.
     */
    public ElasticsearchException(Throwable cause) {
        super(cause);
    }

    /**
     * Construct a <code>ElasticsearchException</code> with the specified detail message.
     *
     * The message can be parameterized using <code>{}</code> as placeholders for the given
     * arguments
     *
     * @param msg  the detail message
     * @param args the arguments for the message
     */
    public ElasticsearchException(String msg, Object... args) {
        super(LoggerMessageFormat.format(msg, args));
    }

    /**
     * Construct a <code>ElasticsearchException</code> with the specified detail message
     * and nested exception.
     *
     * The message can be parameterized using <code>{}</code> as placeholders for the given
     * arguments
     *
     * @param msg   the detail message
     * @param cause the nested exception
     * @param args  the arguments for the message
     */
    public ElasticsearchException(String msg, Throwable cause, Object... args) {
        super(LoggerMessageFormat.format(msg, args), cause);
    }

    public ElasticsearchException(StreamInput in) throws IOException {
        super(in.readOptionalString(), in.readException());
        readStackTrace(this, in);
        headers.putAll(in.readMapOfLists(StreamInput::readString, StreamInput::readString));
    }

    /**
     * Adds a new header with the given key.
     * This method will replace existing header if a header with the same key already exists
     */
    public void addHeader(String key, String... value) {
        this.headers.put(key, Arrays.asList(value));
    }

    /**
     * Adds a new header with the given key.
     * This method will replace existing header if a header with the same key already exists
     */
    public void addHeader(String key, List<String> value) {
        this.headers.put(key, value);
    }


    /**
     * Returns a set of all header keys on this exception
     */
    public Set<String> getHeaderKeys() {
        return headers.keySet();
    }

    /**
     * Returns the list of header values for the given key or {@code null} if not header for the
     * given key exists.
     */
    public List<String> getHeader(String key) {
        return headers.get(key);
    }

    /**
     * Returns the rest status code associated with this exception.
     */
    public RestStatus status() {
        Throwable cause = unwrapCause();
        if (cause == this) {
            return RestStatus.INTERNAL_SERVER_ERROR;
        } else {
            return ExceptionsHelper.status(cause);
        }
    }

    /**
     * Unwraps the actual cause from the exception for cases when the exception is a
     * {@link ElasticsearchWrapperException}.
     *
     * @see ExceptionsHelper#unwrapCause(Throwable)
     */
    public Throwable unwrapCause() {
        return ExceptionsHelper.unwrapCause(this);
    }

    /**
     * Return the detail message, including the message from the nested exception
     * if there is one.
     */
    public String getDetailedMessage() {
        if (getCause() != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(toString()).append("; ");
            if (getCause() instanceof ElasticsearchException) {
                sb.append(((ElasticsearchException) getCause()).getDetailedMessage());
            } else {
                sb.append(getCause());
            }
            return sb.toString();
        } else {
            return super.toString();
        }
    }


    /**
     * Retrieve the innermost cause of this exception, if none, returns the current exception.
     */
    public Throwable getRootCause() {
        Throwable rootCause = this;
        Throwable cause = getCause();
        while (cause != null && cause != rootCause) {
            rootCause = cause;
            cause = cause.getCause();
        }
        return rootCause;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(this.getMessage());
        out.writeException(this.getCause());
        writeStackTraces(this, out);
        out.writeMapOfLists(headers, StreamOutput::writeString, StreamOutput::writeString);
    }

    public static ElasticsearchException readException(StreamInput input, int id) throws IOException {
        FunctionThatThrowsIOException<StreamInput, ? extends ElasticsearchException> elasticsearchException = ID_TO_SUPPLIER.get(id);
        if (elasticsearchException == null) {
            throw new IllegalStateException("unknown exception for id: " + id);
        }
        return elasticsearchException.apply(input);
    }

    /**
     * Returns <code>true</code> iff the given class is a registered for an exception to be read.
     */
    public static boolean isRegistered(Class<? extends Throwable> exception, Version version) {
        ElasticsearchExceptionHandle elasticsearchExceptionHandle = CLASS_TO_ELASTICSEARCH_EXCEPTION_HANDLE.get(exception);
        if (elasticsearchExceptionHandle != null) {
            return version.onOrAfter(elasticsearchExceptionHandle.versionAdded);
        }
        return false;
    }

    static Set<Class<? extends ElasticsearchException>> getRegisteredKeys() { // for testing
        return CLASS_TO_ELASTICSEARCH_EXCEPTION_HANDLE.keySet();
    }

    /**
     * Returns the serialization id the given exception.
     */
    public static int getId(Class<? extends ElasticsearchException> exception) {
        return CLASS_TO_ELASTICSEARCH_EXCEPTION_HANDLE.get(exception).id;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        Throwable ex = ExceptionsHelper.unwrapCause(this);
        if (ex != this) {
            toXContent(builder, params, this);
        } else {
            builder.field(TYPE, getExceptionName());
            builder.field(REASON, getMessage());
            for (String key : headers.keySet()) {
                if (key.startsWith("es.")) {
                    List<String> values = headers.get(key);
                    xContentHeader(builder, key.substring("es.".length()), values);
                }
            }
            innerToXContent(builder, params);
            renderHeader(builder, params);
            if (params.paramAsBoolean(REST_EXCEPTION_SKIP_STACK_TRACE, REST_EXCEPTION_SKIP_STACK_TRACE_DEFAULT) == false) {
                builder.field(STACK_TRACE, ExceptionsHelper.stackTrace(this));
            }
        }
        return builder;
    }

    /**
     * Renders additional per exception information into the xcontent
     */
    protected void innerToXContent(XContentBuilder builder, Params params) throws IOException {
        causeToXContent(builder, params);
    }

    /**
     * Renders a cause exception as xcontent
     */
    protected void causeToXContent(XContentBuilder builder, Params params) throws IOException {
        final Throwable cause = getCause();
        if (cause != null && params.paramAsBoolean(REST_EXCEPTION_SKIP_CAUSE, REST_EXCEPTION_SKIP_CAUSE_DEFAULT) == false) {
            builder.field(CAUSED_BY);
            builder.startObject();
            toXContent(builder, params, cause);
            builder.endObject();
        }
    }

    protected final void renderHeader(XContentBuilder builder, Params params) throws IOException {
        boolean hasHeader = false;
        for (String key : headers.keySet()) {
            if (key.startsWith("es.")) {
                continue;
            }
            if (hasHeader == false) {
                builder.startObject(HEADER);
                hasHeader = true;
            }
            List<String> values = headers.get(key);
            xContentHeader(builder, key, values);
        }
        if (hasHeader) {
            builder.endObject();
        }
    }

    private void xContentHeader(XContentBuilder builder, String key, List<String> values) throws IOException {
        if (values != null && values.isEmpty() == false) {
            if (values.size() == 1) {
                builder.field(key, values.get(0));
            } else {
                builder.startArray(key);
                for (String value : values) {
                    builder.value(value);
                }
                builder.endArray();
            }
        }
    }

    /**
     * Static toXContent helper method that also renders non {@link org.codelibs.elasticsearch.ElasticsearchException} instances as XContent.
     */
    public static void toXContent(XContentBuilder builder, Params params, Throwable ex) throws IOException {
        ex = ExceptionsHelper.unwrapCause(ex);
        if (ex instanceof ElasticsearchException) {
            ((ElasticsearchException) ex).toXContent(builder, params);
        } else {
            builder.field(TYPE, getExceptionName(ex));
            builder.field(REASON, ex.getMessage());
            if (ex.getCause() != null) {
                builder.field(CAUSED_BY);
                builder.startObject();
                toXContent(builder, params, ex.getCause());
                builder.endObject();
            }
            if (params.paramAsBoolean(REST_EXCEPTION_SKIP_STACK_TRACE, REST_EXCEPTION_SKIP_STACK_TRACE_DEFAULT) == false) {
                builder.field(STACK_TRACE, ExceptionsHelper.stackTrace(ex));
            }
        }
    }

    /**
     * Generate a {@link ElasticsearchException} from a {@link XContentParser}. This does not
     * return the original exception type (ie NodeClosedException for example) but just wraps
     * the type, the reason and the cause of the exception. It also recursively parses the
     * tree structure of the cause, returning it as a tree structure of {@link ElasticsearchException}
     * instances.
     */
    public static ElasticsearchException fromXContent(XContentParser parser) throws IOException {
        XContentParser.Token token = parser.nextToken();
        ensureExpectedToken(XContentParser.Token.FIELD_NAME, token, parser::getTokenLocation);

        String type = null, reason = null, stack = null;
        ElasticsearchException cause = null;
        Map<String, Object> headers = new HashMap<>();

        do {
            String currentFieldName = parser.currentName();
            token = parser.nextToken();
            if (token.isValue()) {
                if (TYPE.equals(currentFieldName)) {
                    type = parser.text();
                } else if (REASON.equals(currentFieldName)) {
                    reason = parser.text();
                } else if (STACK_TRACE.equals(currentFieldName)) {
                    stack = parser.text();
                } else {
                    // Everything else is considered as a header
                    headers.put(currentFieldName, parser.text());
                }
            } else if (token == XContentParser.Token.START_OBJECT) {
                if (CAUSED_BY.equals(currentFieldName)) {
                    cause = fromXContent(parser);
                } else if (HEADER.equals(currentFieldName)) {
                    headers.putAll(parser.map());
                } else {
                    throwUnknownField(currentFieldName, parser.getTokenLocation());
                }
            }
        } while ((token = parser.nextToken()) == XContentParser.Token.FIELD_NAME);

        StringBuilder message = new StringBuilder("Elasticsearch exception [");
        message.append(TYPE).append('=').append(type).append(", ");
        message.append(REASON).append('=').append(reason);
        if (stack != null) {
            message.append(", ").append(STACK_TRACE).append('=').append(stack);
        }
        message.append(']');

        ElasticsearchException e = new ElasticsearchException(message.toString(), cause);
        for (Map.Entry<String, Object> header : headers.entrySet()) {
            e.addHeader(header.getKey(), String.valueOf(header.getValue()));
        }
        return e;
    }

    /**
     * Returns the root cause of this exception or multiple if different shards caused different exceptions
     */
    public ElasticsearchException[] guessRootCauses() {
        final Throwable cause = getCause();
        if (cause != null && cause instanceof ElasticsearchException) {
            return ((ElasticsearchException) cause).guessRootCauses();
        }
        return new ElasticsearchException[]{this};
    }

    /**
     * Returns the root cause of this exception or multiple if different shards caused different exceptions.
     * If the given exception is not an instance of {@link org.codelibs.elasticsearch.ElasticsearchException} an empty array
     * is returned.
     */
    public static ElasticsearchException[] guessRootCauses(Throwable t) {
        Throwable ex = ExceptionsHelper.unwrapCause(t);
        if (ex instanceof ElasticsearchException) {
            return ((ElasticsearchException) ex).guessRootCauses();
        }
        return new ElasticsearchException[]{new ElasticsearchException(t.getMessage(), t) {
            @Override
            protected String getExceptionName() {
                return getExceptionName(getCause());
            }
        }};
    }

    protected String getExceptionName() {
        return getExceptionName(this);
    }

    /**
     * Returns a underscore case name for the given exception. This method strips <tt>Elasticsearch</tt> prefixes from exception names.
     */
    public static String getExceptionName(Throwable ex) {
        String simpleName = ex.getClass().getSimpleName();
        if (simpleName.startsWith("Elasticsearch")) {
            simpleName = simpleName.substring("Elasticsearch".length());
        }
        // TODO: do we really need to make the exception name in underscore casing?
        return toUnderscoreCase(simpleName);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (headers.containsKey(INDEX_HEADER_KEY)) {
            builder.append(getIndex());
            if (headers.containsKey(SHARD_HEADER_KEY)) {
                builder.append('[').append(getShardId()).append(']');
            }
            builder.append(' ');
        }
        return builder.append(ExceptionsHelper.detailedMessage(this).trim()).toString();
    }

    /**
     * Deserializes stacktrace elements as well as suppressed exceptions from the given output stream and
     * adds it to the given exception.
     */
    public static <T extends Throwable> T readStackTrace(T throwable, StreamInput in) throws IOException {
        final int stackTraceElements = in.readVInt();
        StackTraceElement[] stackTrace = new StackTraceElement[stackTraceElements];
        for (int i = 0; i < stackTraceElements; i++) {
            final String declaringClasss = in.readString();
            final String fileName = in.readOptionalString();
            final String methodName = in.readString();
            final int lineNumber = in.readVInt();
            stackTrace[i] = new StackTraceElement(declaringClasss, methodName, fileName, lineNumber);
        }
        throwable.setStackTrace(stackTrace);

        int numSuppressed = in.readVInt();
        for (int i = 0; i < numSuppressed; i++) {
            throwable.addSuppressed(in.readException());
        }
        return throwable;
    }

    /**
     * Serializes the given exceptions stacktrace elements as well as it's suppressed exceptions to the given output stream.
     */
    public static <T extends Throwable> T writeStackTraces(T throwable, StreamOutput out) throws IOException {
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        out.writeVInt(stackTrace.length);
        for (StackTraceElement element : stackTrace) {
            out.writeString(element.getClassName());
            out.writeOptionalString(element.getFileName());
            out.writeString(element.getMethodName());
            out.writeVInt(element.getLineNumber());
        }
        Throwable[] suppressed = throwable.getSuppressed();
        out.writeVInt(suppressed.length);
        for (Throwable t : suppressed) {
            out.writeException(t);
        }
        return throwable;
    }

    /**
     * This is the list of Exceptions Elasticsearch can throw over the wire or save into a corruption marker. Each value in the enum is a
     * single exception tying the Class to an id for use of the encode side and the id back to a constructor for use on the decode side. As
     * such its ok if the exceptions to change names so long as their constructor can still read the exception. Each exception is listed
     * in id order below. If you want to remove an exception leave a tombstone comment and mark the id as null in
     * ExceptionSerializationTests.testIds.ids.
     */
    enum ElasticsearchExceptionHandle {
        INDEX_SHARD_SNAPSHOT_FAILED_EXCEPTION(org.codelibs.elasticsearch.index.snapshots.IndexShardSnapshotFailedException.class,
                org.codelibs.elasticsearch.index.snapshots.IndexShardSnapshotFailedException::new, 0, UNKNOWN_VERSION_ADDED),
        DFS_PHASE_EXECUTION_EXCEPTION(org.codelibs.elasticsearch.search.dfs.DfsPhaseExecutionException.class,
                org.codelibs.elasticsearch.search.dfs.DfsPhaseExecutionException::new, 1, UNKNOWN_VERSION_ADDED),
        EXECUTION_CANCELLED_EXCEPTION(org.codelibs.elasticsearch.common.util.CancellableThreads.ExecutionCancelledException.class,
                org.codelibs.elasticsearch.common.util.CancellableThreads.ExecutionCancelledException::new, 2, UNKNOWN_VERSION_ADDED),
        MASTER_NOT_DISCOVERED_EXCEPTION(org.codelibs.elasticsearch.discovery.MasterNotDiscoveredException.class,
                org.codelibs.elasticsearch.discovery.MasterNotDiscoveredException::new, 3, UNKNOWN_VERSION_ADDED),
        ELASTICSEARCH_SECURITY_EXCEPTION(org.codelibs.elasticsearch.ElasticsearchSecurityException.class,
                org.codelibs.elasticsearch.ElasticsearchSecurityException::new, 4, UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_RESTORE_EXCEPTION(org.codelibs.elasticsearch.index.snapshots.IndexShardRestoreException.class,
                org.codelibs.elasticsearch.index.snapshots.IndexShardRestoreException::new, 5, UNKNOWN_VERSION_ADDED),
        INDEX_CLOSED_EXCEPTION(org.codelibs.elasticsearch.indices.IndexClosedException.class,
                org.codelibs.elasticsearch.indices.IndexClosedException::new, 6, UNKNOWN_VERSION_ADDED),
        BIND_HTTP_EXCEPTION(org.codelibs.elasticsearch.http.BindHttpException.class,
                org.codelibs.elasticsearch.http.BindHttpException::new, 7, UNKNOWN_VERSION_ADDED),
        REDUCE_SEARCH_PHASE_EXCEPTION(org.codelibs.elasticsearch.action.search.ReduceSearchPhaseException.class,
                org.codelibs.elasticsearch.action.search.ReduceSearchPhaseException::new, 8, UNKNOWN_VERSION_ADDED),
        NODE_CLOSED_EXCEPTION(org.codelibs.elasticsearch.node.NodeClosedException.class,
                org.codelibs.elasticsearch.node.NodeClosedException::new, 9, UNKNOWN_VERSION_ADDED),
        SNAPSHOT_FAILED_ENGINE_EXCEPTION(org.codelibs.elasticsearch.index.engine.SnapshotFailedEngineException.class,
                org.codelibs.elasticsearch.index.engine.SnapshotFailedEngineException::new, 10, UNKNOWN_VERSION_ADDED),
        SHARD_NOT_FOUND_EXCEPTION(org.codelibs.elasticsearch.index.shard.ShardNotFoundException.class,
                org.codelibs.elasticsearch.index.shard.ShardNotFoundException::new, 11, UNKNOWN_VERSION_ADDED),
        CONNECT_TRANSPORT_EXCEPTION(org.codelibs.elasticsearch.transport.ConnectTransportException.class,
                org.codelibs.elasticsearch.transport.ConnectTransportException::new, 12, UNKNOWN_VERSION_ADDED),
        NOT_SERIALIZABLE_TRANSPORT_EXCEPTION(org.codelibs.elasticsearch.transport.NotSerializableTransportException.class,
                org.codelibs.elasticsearch.transport.NotSerializableTransportException::new, 13, UNKNOWN_VERSION_ADDED),
        RESPONSE_HANDLER_FAILURE_TRANSPORT_EXCEPTION(org.codelibs.elasticsearch.transport.ResponseHandlerFailureTransportException.class,
                org.codelibs.elasticsearch.transport.ResponseHandlerFailureTransportException::new, 14, UNKNOWN_VERSION_ADDED),
        INDEX_CREATION_EXCEPTION(org.codelibs.elasticsearch.indices.IndexCreationException.class,
                org.codelibs.elasticsearch.indices.IndexCreationException::new, 15, UNKNOWN_VERSION_ADDED),
        INDEX_NOT_FOUND_EXCEPTION(org.codelibs.elasticsearch.index.IndexNotFoundException.class,
                org.codelibs.elasticsearch.index.IndexNotFoundException::new, 16, UNKNOWN_VERSION_ADDED),
        ILLEGAL_SHARD_ROUTING_STATE_EXCEPTION(org.codelibs.elasticsearch.cluster.routing.IllegalShardRoutingStateException.class,
                org.codelibs.elasticsearch.cluster.routing.IllegalShardRoutingStateException::new, 17, UNKNOWN_VERSION_ADDED),
        BROADCAST_SHARD_OPERATION_FAILED_EXCEPTION(org.codelibs.elasticsearch.action.support.broadcast.BroadcastShardOperationFailedException.class,
                org.codelibs.elasticsearch.action.support.broadcast.BroadcastShardOperationFailedException::new, 18, UNKNOWN_VERSION_ADDED),
        RESOURCE_NOT_FOUND_EXCEPTION(org.codelibs.elasticsearch.ResourceNotFoundException.class,
                org.codelibs.elasticsearch.ResourceNotFoundException::new, 19, UNKNOWN_VERSION_ADDED),
        ACTION_TRANSPORT_EXCEPTION(org.codelibs.elasticsearch.transport.ActionTransportException.class,
                org.codelibs.elasticsearch.transport.ActionTransportException::new, 20, UNKNOWN_VERSION_ADDED),
        ELASTICSEARCH_GENERATION_EXCEPTION(org.codelibs.elasticsearch.ElasticsearchGenerationException.class,
                org.codelibs.elasticsearch.ElasticsearchGenerationException::new, 21, UNKNOWN_VERSION_ADDED),
        //      22 was CreateFailedEngineException
        INDEX_SHARD_STARTED_EXCEPTION(org.codelibs.elasticsearch.index.shard.IndexShardStartedException.class,
                org.codelibs.elasticsearch.index.shard.IndexShardStartedException::new, 23, UNKNOWN_VERSION_ADDED),
        SEARCH_CONTEXT_MISSING_EXCEPTION(org.codelibs.elasticsearch.search.SearchContextMissingException.class,
                org.codelibs.elasticsearch.search.SearchContextMissingException::new, 24, UNKNOWN_VERSION_ADDED),
        GENERAL_SCRIPT_EXCEPTION(org.codelibs.elasticsearch.script.GeneralScriptException.class,
                org.codelibs.elasticsearch.script.GeneralScriptException::new, 25, UNKNOWN_VERSION_ADDED),
        BATCH_OPERATION_EXCEPTION(org.codelibs.elasticsearch.index.shard.TranslogRecoveryPerformer.BatchOperationException.class,
                org.codelibs.elasticsearch.index.shard.TranslogRecoveryPerformer.BatchOperationException::new, 26, UNKNOWN_VERSION_ADDED),
        SNAPSHOT_CREATION_EXCEPTION(org.codelibs.elasticsearch.snapshots.SnapshotCreationException.class,
                org.codelibs.elasticsearch.snapshots.SnapshotCreationException::new, 27, UNKNOWN_VERSION_ADDED),
        DELETE_FAILED_ENGINE_EXCEPTION(org.codelibs.elasticsearch.index.engine.DeleteFailedEngineException.class,
                org.codelibs.elasticsearch.index.engine.DeleteFailedEngineException::new, 28, UNKNOWN_VERSION_ADDED),
        DOCUMENT_MISSING_EXCEPTION(org.codelibs.elasticsearch.index.engine.DocumentMissingException.class,
                org.codelibs.elasticsearch.index.engine.DocumentMissingException::new, 29, UNKNOWN_VERSION_ADDED),
        SNAPSHOT_EXCEPTION(org.codelibs.elasticsearch.snapshots.SnapshotException.class,
                org.codelibs.elasticsearch.snapshots.SnapshotException::new, 30, UNKNOWN_VERSION_ADDED),
        INVALID_ALIAS_NAME_EXCEPTION(org.codelibs.elasticsearch.indices.InvalidAliasNameException.class,
                org.codelibs.elasticsearch.indices.InvalidAliasNameException::new, 31, UNKNOWN_VERSION_ADDED),
        INVALID_INDEX_NAME_EXCEPTION(org.codelibs.elasticsearch.indices.InvalidIndexNameException.class,
                org.codelibs.elasticsearch.indices.InvalidIndexNameException::new, 32, UNKNOWN_VERSION_ADDED),
        INDEX_PRIMARY_SHARD_NOT_ALLOCATED_EXCEPTION(org.codelibs.elasticsearch.indices.IndexPrimaryShardNotAllocatedException.class,
                org.codelibs.elasticsearch.indices.IndexPrimaryShardNotAllocatedException::new, 33, UNKNOWN_VERSION_ADDED),
        TRANSPORT_EXCEPTION(org.codelibs.elasticsearch.transport.TransportException.class,
                org.codelibs.elasticsearch.transport.TransportException::new, 34, UNKNOWN_VERSION_ADDED),
        ELASTICSEARCH_PARSE_EXCEPTION(org.codelibs.elasticsearch.ElasticsearchParseException.class,
                org.codelibs.elasticsearch.ElasticsearchParseException::new, 35, UNKNOWN_VERSION_ADDED),
        SEARCH_EXCEPTION(org.codelibs.elasticsearch.search.SearchException.class,
                org.codelibs.elasticsearch.search.SearchException::new, 36, UNKNOWN_VERSION_ADDED),
        MAPPER_EXCEPTION(org.codelibs.elasticsearch.index.mapper.MapperException.class,
                org.codelibs.elasticsearch.index.mapper.MapperException::new, 37, UNKNOWN_VERSION_ADDED),
        INVALID_TYPE_NAME_EXCEPTION(org.codelibs.elasticsearch.indices.InvalidTypeNameException.class,
                org.codelibs.elasticsearch.indices.InvalidTypeNameException::new, 38, UNKNOWN_VERSION_ADDED),
        SNAPSHOT_RESTORE_EXCEPTION(org.codelibs.elasticsearch.snapshots.SnapshotRestoreException.class,
                org.codelibs.elasticsearch.snapshots.SnapshotRestoreException::new, 39, UNKNOWN_VERSION_ADDED),
        PARSING_EXCEPTION(org.codelibs.elasticsearch.common.ParsingException.class, org.codelibs.elasticsearch.common.ParsingException::new, 40,
            UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_CLOSED_EXCEPTION(org.codelibs.elasticsearch.index.shard.IndexShardClosedException.class,
                org.codelibs.elasticsearch.index.shard.IndexShardClosedException::new, 41, UNKNOWN_VERSION_ADDED),
        RECOVER_FILES_RECOVERY_EXCEPTION(org.codelibs.elasticsearch.indices.recovery.RecoverFilesRecoveryException.class,
                org.codelibs.elasticsearch.indices.recovery.RecoverFilesRecoveryException::new, 42, UNKNOWN_VERSION_ADDED),
        TRUNCATED_TRANSLOG_EXCEPTION(org.codelibs.elasticsearch.index.translog.TruncatedTranslogException.class,
                org.codelibs.elasticsearch.index.translog.TruncatedTranslogException::new, 43, UNKNOWN_VERSION_ADDED),
        RECOVERY_FAILED_EXCEPTION(org.codelibs.elasticsearch.indices.recovery.RecoveryFailedException.class,
                org.codelibs.elasticsearch.indices.recovery.RecoveryFailedException::new, 44, UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_RELOCATED_EXCEPTION(org.codelibs.elasticsearch.index.shard.IndexShardRelocatedException.class,
                org.codelibs.elasticsearch.index.shard.IndexShardRelocatedException::new, 45, UNKNOWN_VERSION_ADDED),
        NODE_SHOULD_NOT_CONNECT_EXCEPTION(org.codelibs.elasticsearch.transport.NodeShouldNotConnectException.class,
        org.codelibs.elasticsearch.transport.NodeShouldNotConnectException::new, 46, UNKNOWN_VERSION_ADDED),
        INDEX_TEMPLATE_ALREADY_EXISTS_EXCEPTION(org.codelibs.elasticsearch.indices.IndexTemplateAlreadyExistsException.class,
                org.codelibs.elasticsearch.indices.IndexTemplateAlreadyExistsException::new, 47, UNKNOWN_VERSION_ADDED),
        TRANSLOG_CORRUPTED_EXCEPTION(org.codelibs.elasticsearch.index.translog.TranslogCorruptedException.class,
                org.codelibs.elasticsearch.index.translog.TranslogCorruptedException::new, 48, UNKNOWN_VERSION_ADDED),
        CLUSTER_BLOCK_EXCEPTION(org.codelibs.elasticsearch.cluster.block.ClusterBlockException.class,
                org.codelibs.elasticsearch.cluster.block.ClusterBlockException::new, 49, UNKNOWN_VERSION_ADDED),
        FETCH_PHASE_EXECUTION_EXCEPTION(org.codelibs.elasticsearch.search.fetch.FetchPhaseExecutionException.class,
        org.codelibs.elasticsearch.search.fetch.FetchPhaseExecutionException::new, 50, UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_ALREADY_EXISTS_EXCEPTION(org.codelibs.elasticsearch.index.IndexShardAlreadyExistsException.class,
                org.codelibs.elasticsearch.index.IndexShardAlreadyExistsException::new, 51, UNKNOWN_VERSION_ADDED),
        VERSION_CONFLICT_ENGINE_EXCEPTION(org.codelibs.elasticsearch.index.engine.VersionConflictEngineException.class,
                org.codelibs.elasticsearch.index.engine.VersionConflictEngineException::new, 52, UNKNOWN_VERSION_ADDED),
        ENGINE_EXCEPTION(org.codelibs.elasticsearch.index.engine.EngineException.class, org.codelibs.elasticsearch.index.engine.EngineException::new, 53,
            UNKNOWN_VERSION_ADDED),
        // 54 was DocumentAlreadyExistsException, which is superseded by VersionConflictEngineException
        NO_SUCH_NODE_EXCEPTION(org.codelibs.elasticsearch.action.NoSuchNodeException.class, org.codelibs.elasticsearch.action.NoSuchNodeException::new, 55,
            UNKNOWN_VERSION_ADDED),
        SETTINGS_EXCEPTION(org.codelibs.elasticsearch.common.settings.SettingsException.class,
                org.codelibs.elasticsearch.common.settings.SettingsException::new, 56, UNKNOWN_VERSION_ADDED),
        INDEX_TEMPLATE_MISSING_EXCEPTION(org.codelibs.elasticsearch.indices.IndexTemplateMissingException.class,
                org.codelibs.elasticsearch.indices.IndexTemplateMissingException::new, 57, UNKNOWN_VERSION_ADDED),
        SEND_REQUEST_TRANSPORT_EXCEPTION(org.codelibs.elasticsearch.transport.SendRequestTransportException.class,
                org.codelibs.elasticsearch.transport.SendRequestTransportException::new, 58, UNKNOWN_VERSION_ADDED),
        ES_REJECTED_EXECUTION_EXCEPTION(org.codelibs.elasticsearch.common.util.concurrent.EsRejectedExecutionException.class,
                org.codelibs.elasticsearch.common.util.concurrent.EsRejectedExecutionException::new, 59, UNKNOWN_VERSION_ADDED),
        EARLY_TERMINATION_EXCEPTION(org.codelibs.elasticsearch.common.lucene.Lucene.EarlyTerminationException.class,
                org.codelibs.elasticsearch.common.lucene.Lucene.EarlyTerminationException::new, 60, UNKNOWN_VERSION_ADDED),
        // 61 used to be for RoutingValidationException
        NOT_SERIALIZABLE_EXCEPTION_WRAPPER(org.codelibs.elasticsearch.common.io.stream.NotSerializableExceptionWrapper.class,
                org.codelibs.elasticsearch.common.io.stream.NotSerializableExceptionWrapper::new, 62, UNKNOWN_VERSION_ADDED),
        ALIAS_FILTER_PARSING_EXCEPTION(org.codelibs.elasticsearch.indices.AliasFilterParsingException.class,
                org.codelibs.elasticsearch.indices.AliasFilterParsingException::new, 63, UNKNOWN_VERSION_ADDED),
        // 64 was DeleteByQueryFailedEngineException, which was removed in 5.0
        GATEWAY_EXCEPTION(org.codelibs.elasticsearch.gateway.GatewayException.class, org.codelibs.elasticsearch.gateway.GatewayException::new, 65,
            UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_NOT_RECOVERING_EXCEPTION(org.codelibs.elasticsearch.index.shard.IndexShardNotRecoveringException.class,
                org.codelibs.elasticsearch.index.shard.IndexShardNotRecoveringException::new, 66, UNKNOWN_VERSION_ADDED),
        HTTP_EXCEPTION(org.codelibs.elasticsearch.http.HttpException.class, org.codelibs.elasticsearch.http.HttpException::new, 67, UNKNOWN_VERSION_ADDED),
        ELASTICSEARCH_EXCEPTION(org.codelibs.elasticsearch.ElasticsearchException.class,
                org.codelibs.elasticsearch.ElasticsearchException::new, 68, UNKNOWN_VERSION_ADDED),
        SNAPSHOT_MISSING_EXCEPTION(org.codelibs.elasticsearch.snapshots.SnapshotMissingException.class,
                org.codelibs.elasticsearch.snapshots.SnapshotMissingException::new, 69, UNKNOWN_VERSION_ADDED),
        PRIMARY_MISSING_ACTION_EXCEPTION(org.codelibs.elasticsearch.action.PrimaryMissingActionException.class,
                org.codelibs.elasticsearch.action.PrimaryMissingActionException::new, 70, UNKNOWN_VERSION_ADDED),
        FAILED_NODE_EXCEPTION(org.codelibs.elasticsearch.action.FailedNodeException.class, org.codelibs.elasticsearch.action.FailedNodeException::new, 71,
            UNKNOWN_VERSION_ADDED),
        SEARCH_PARSE_EXCEPTION(org.codelibs.elasticsearch.search.SearchParseException.class, org.codelibs.elasticsearch.search.SearchParseException::new, 72,
            UNKNOWN_VERSION_ADDED),
        CONCURRENT_SNAPSHOT_EXECUTION_EXCEPTION(org.codelibs.elasticsearch.snapshots.ConcurrentSnapshotExecutionException.class,
                org.codelibs.elasticsearch.snapshots.ConcurrentSnapshotExecutionException::new, 73, UNKNOWN_VERSION_ADDED),
        BLOB_STORE_EXCEPTION(org.codelibs.elasticsearch.common.blobstore.BlobStoreException.class,
                org.codelibs.elasticsearch.common.blobstore.BlobStoreException::new, 74, UNKNOWN_VERSION_ADDED),
        INCOMPATIBLE_CLUSTER_STATE_VERSION_EXCEPTION(org.codelibs.elasticsearch.cluster.IncompatibleClusterStateVersionException.class,
                org.codelibs.elasticsearch.cluster.IncompatibleClusterStateVersionException::new, 75, UNKNOWN_VERSION_ADDED),
        RECOVERY_ENGINE_EXCEPTION(org.codelibs.elasticsearch.index.engine.RecoveryEngineException.class,
                org.codelibs.elasticsearch.index.engine.RecoveryEngineException::new, 76, UNKNOWN_VERSION_ADDED),
        UNCATEGORIZED_EXECUTION_EXCEPTION(org.codelibs.elasticsearch.common.util.concurrent.UncategorizedExecutionException.class,
                org.codelibs.elasticsearch.common.util.concurrent.UncategorizedExecutionException::new, 77, UNKNOWN_VERSION_ADDED),
        TIMESTAMP_PARSING_EXCEPTION(org.codelibs.elasticsearch.action.TimestampParsingException.class,
                org.codelibs.elasticsearch.action.TimestampParsingException::new, 78, UNKNOWN_VERSION_ADDED),
        ROUTING_MISSING_EXCEPTION(org.codelibs.elasticsearch.action.RoutingMissingException.class,
                org.codelibs.elasticsearch.action.RoutingMissingException::new, 79, UNKNOWN_VERSION_ADDED),
        INDEX_FAILED_ENGINE_EXCEPTION(org.codelibs.elasticsearch.index.engine.IndexFailedEngineException.class,
                org.codelibs.elasticsearch.index.engine.IndexFailedEngineException::new, 80, UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_RESTORE_FAILED_EXCEPTION(org.codelibs.elasticsearch.index.snapshots.IndexShardRestoreFailedException.class,
                org.codelibs.elasticsearch.index.snapshots.IndexShardRestoreFailedException::new, 81, UNKNOWN_VERSION_ADDED),
        REPOSITORY_EXCEPTION(org.codelibs.elasticsearch.repositories.RepositoryException.class,
                org.codelibs.elasticsearch.repositories.RepositoryException::new, 82, UNKNOWN_VERSION_ADDED),
        RECEIVE_TIMEOUT_TRANSPORT_EXCEPTION(org.codelibs.elasticsearch.transport.ReceiveTimeoutTransportException.class,
                org.codelibs.elasticsearch.transport.ReceiveTimeoutTransportException::new, 83, UNKNOWN_VERSION_ADDED),
        NODE_DISCONNECTED_EXCEPTION(org.codelibs.elasticsearch.transport.NodeDisconnectedException.class,
                org.codelibs.elasticsearch.transport.NodeDisconnectedException::new, 84, UNKNOWN_VERSION_ADDED),
        ALREADY_EXPIRED_EXCEPTION(org.codelibs.elasticsearch.index.AlreadyExpiredException.class,
                org.codelibs.elasticsearch.index.AlreadyExpiredException::new, 85, UNKNOWN_VERSION_ADDED),
        AGGREGATION_EXECUTION_EXCEPTION(org.codelibs.elasticsearch.search.aggregations.AggregationExecutionException.class,
                org.codelibs.elasticsearch.search.aggregations.AggregationExecutionException::new, 86, UNKNOWN_VERSION_ADDED),
        // 87 used to be for MergeMappingException
        INVALID_INDEX_TEMPLATE_EXCEPTION(org.codelibs.elasticsearch.indices.InvalidIndexTemplateException.class,
                org.codelibs.elasticsearch.indices.InvalidIndexTemplateException::new, 88, UNKNOWN_VERSION_ADDED),
        REFRESH_FAILED_ENGINE_EXCEPTION(org.codelibs.elasticsearch.index.engine.RefreshFailedEngineException.class,
                org.codelibs.elasticsearch.index.engine.RefreshFailedEngineException::new, 90, UNKNOWN_VERSION_ADDED),
        AGGREGATION_INITIALIZATION_EXCEPTION(org.codelibs.elasticsearch.search.aggregations.AggregationInitializationException.class,
                org.codelibs.elasticsearch.search.aggregations.AggregationInitializationException::new, 91, UNKNOWN_VERSION_ADDED),
        DELAY_RECOVERY_EXCEPTION(org.codelibs.elasticsearch.indices.recovery.DelayRecoveryException.class,
                org.codelibs.elasticsearch.indices.recovery.DelayRecoveryException::new, 92, UNKNOWN_VERSION_ADDED),
        // 93 used to be for IndexWarmerMissingException
        NO_NODE_AVAILABLE_EXCEPTION(org.codelibs.elasticsearch.client.transport.NoNodeAvailableException.class,
                org.codelibs.elasticsearch.client.transport.NoNodeAvailableException::new, 94, UNKNOWN_VERSION_ADDED),
        INVALID_SNAPSHOT_NAME_EXCEPTION(org.codelibs.elasticsearch.snapshots.InvalidSnapshotNameException.class,
                org.codelibs.elasticsearch.snapshots.InvalidSnapshotNameException::new, 96, UNKNOWN_VERSION_ADDED),
        ILLEGAL_INDEX_SHARD_STATE_EXCEPTION(org.codelibs.elasticsearch.index.shard.IllegalIndexShardStateException.class,
                org.codelibs.elasticsearch.index.shard.IllegalIndexShardStateException::new, 97, UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_SNAPSHOT_EXCEPTION(org.codelibs.elasticsearch.index.snapshots.IndexShardSnapshotException.class,
                org.codelibs.elasticsearch.index.snapshots.IndexShardSnapshotException::new, 98, UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_NOT_STARTED_EXCEPTION(org.codelibs.elasticsearch.index.shard.IndexShardNotStartedException.class,
                org.codelibs.elasticsearch.index.shard.IndexShardNotStartedException::new, 99, UNKNOWN_VERSION_ADDED),
        SEARCH_PHASE_EXECUTION_EXCEPTION(org.codelibs.elasticsearch.action.search.SearchPhaseExecutionException.class,
                org.codelibs.elasticsearch.action.search.SearchPhaseExecutionException::new, 100, UNKNOWN_VERSION_ADDED),
        ACTION_NOT_FOUND_TRANSPORT_EXCEPTION(org.codelibs.elasticsearch.transport.ActionNotFoundTransportException.class,
                org.codelibs.elasticsearch.transport.ActionNotFoundTransportException::new, 101, UNKNOWN_VERSION_ADDED),
        TRANSPORT_SERIALIZATION_EXCEPTION(org.codelibs.elasticsearch.transport.TransportSerializationException.class,
                org.codelibs.elasticsearch.transport.TransportSerializationException::new, 102, UNKNOWN_VERSION_ADDED),
        REMOTE_TRANSPORT_EXCEPTION(org.codelibs.elasticsearch.transport.RemoteTransportException.class,
                org.codelibs.elasticsearch.transport.RemoteTransportException::new, 103, UNKNOWN_VERSION_ADDED),
        ENGINE_CREATION_FAILURE_EXCEPTION(org.codelibs.elasticsearch.index.engine.EngineCreationFailureException.class,
                org.codelibs.elasticsearch.index.engine.EngineCreationFailureException::new, 104, UNKNOWN_VERSION_ADDED),
        ROUTING_EXCEPTION(org.codelibs.elasticsearch.cluster.routing.RoutingException.class,
                org.codelibs.elasticsearch.cluster.routing.RoutingException::new, 105, UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_RECOVERY_EXCEPTION(org.codelibs.elasticsearch.index.shard.IndexShardRecoveryException.class,
                org.codelibs.elasticsearch.index.shard.IndexShardRecoveryException::new, 106, UNKNOWN_VERSION_ADDED),
        REPOSITORY_MISSING_EXCEPTION(org.codelibs.elasticsearch.repositories.RepositoryMissingException.class,
                org.codelibs.elasticsearch.repositories.RepositoryMissingException::new, 107, UNKNOWN_VERSION_ADDED),
        DOCUMENT_SOURCE_MISSING_EXCEPTION(org.codelibs.elasticsearch.index.engine.DocumentSourceMissingException.class,
                org.codelibs.elasticsearch.index.engine.DocumentSourceMissingException::new, 109, UNKNOWN_VERSION_ADDED),
        // 110 used to be FlushNotAllowedEngineException
        NO_CLASS_SETTINGS_EXCEPTION(org.codelibs.elasticsearch.common.settings.NoClassSettingsException.class,
                org.codelibs.elasticsearch.common.settings.NoClassSettingsException::new, 111, UNKNOWN_VERSION_ADDED),
        BIND_TRANSPORT_EXCEPTION(org.codelibs.elasticsearch.transport.BindTransportException.class,
                org.codelibs.elasticsearch.transport.BindTransportException::new, 112, UNKNOWN_VERSION_ADDED),
        ALIASES_NOT_FOUND_EXCEPTION(org.codelibs.elasticsearch.rest.action.admin.indices.AliasesNotFoundException.class,
                org.codelibs.elasticsearch.rest.action.admin.indices.AliasesNotFoundException::new, 113, UNKNOWN_VERSION_ADDED),
        INDEX_SHARD_RECOVERING_EXCEPTION(org.codelibs.elasticsearch.index.shard.IndexShardRecoveringException.class,
                org.codelibs.elasticsearch.index.shard.IndexShardRecoveringException::new, 114, UNKNOWN_VERSION_ADDED),
        TRANSLOG_EXCEPTION(org.codelibs.elasticsearch.index.translog.TranslogException.class,
                org.codelibs.elasticsearch.index.translog.TranslogException::new, 115, UNKNOWN_VERSION_ADDED),
        PROCESS_CLUSTER_EVENT_TIMEOUT_EXCEPTION(org.codelibs.elasticsearch.cluster.metadata.ProcessClusterEventTimeoutException.class,
                org.codelibs.elasticsearch.cluster.metadata.ProcessClusterEventTimeoutException::new, 116, UNKNOWN_VERSION_ADDED),
        RETRY_ON_PRIMARY_EXCEPTION(ReplicationOperation.RetryOnPrimaryException.class,
                ReplicationOperation.RetryOnPrimaryException::new, 117, UNKNOWN_VERSION_ADDED),
        ELASTICSEARCH_TIMEOUT_EXCEPTION(org.codelibs.elasticsearch.ElasticsearchTimeoutException.class,
                org.codelibs.elasticsearch.ElasticsearchTimeoutException::new, 118, UNKNOWN_VERSION_ADDED),
        QUERY_PHASE_EXECUTION_EXCEPTION(org.codelibs.elasticsearch.search.query.QueryPhaseExecutionException.class,
                org.codelibs.elasticsearch.search.query.QueryPhaseExecutionException::new, 119, UNKNOWN_VERSION_ADDED),
        REPOSITORY_VERIFICATION_EXCEPTION(org.codelibs.elasticsearch.repositories.RepositoryVerificationException.class,
                org.codelibs.elasticsearch.repositories.RepositoryVerificationException::new, 120, UNKNOWN_VERSION_ADDED),
        INVALID_AGGREGATION_PATH_EXCEPTION(org.codelibs.elasticsearch.search.aggregations.InvalidAggregationPathException.class,
                org.codelibs.elasticsearch.search.aggregations.InvalidAggregationPathException::new, 121, UNKNOWN_VERSION_ADDED),
        // 123 used to be IndexAlreadyExistsException and was renamed
        RESOURCE_ALREADY_EXISTS_EXCEPTION(ResourceAlreadyExistsException.class,
            ResourceAlreadyExistsException::new, 123, UNKNOWN_VERSION_ADDED),
        // 124 used to be Script.ScriptParseException
        HTTP_ON_TRANSPORT_EXCEPTION(TcpTransport.HttpOnTransportException.class,
                TcpTransport.HttpOnTransportException::new, 125, UNKNOWN_VERSION_ADDED),
        MAPPER_PARSING_EXCEPTION(org.codelibs.elasticsearch.index.mapper.MapperParsingException.class,
                org.codelibs.elasticsearch.index.mapper.MapperParsingException::new, 126, UNKNOWN_VERSION_ADDED),
        SEARCH_CONTEXT_EXCEPTION(org.codelibs.elasticsearch.search.SearchContextException.class,
                org.codelibs.elasticsearch.search.SearchContextException::new, 127, UNKNOWN_VERSION_ADDED),
        SEARCH_SOURCE_BUILDER_EXCEPTION(org.codelibs.elasticsearch.search.builder.SearchSourceBuilderException.class,
                org.codelibs.elasticsearch.search.builder.SearchSourceBuilderException::new, 128, UNKNOWN_VERSION_ADDED),
        ENGINE_CLOSED_EXCEPTION(org.codelibs.elasticsearch.index.engine.EngineClosedException.class,
                org.codelibs.elasticsearch.index.engine.EngineClosedException::new, 129, UNKNOWN_VERSION_ADDED),
        NO_SHARD_AVAILABLE_ACTION_EXCEPTION(org.codelibs.elasticsearch.action.NoShardAvailableActionException.class,
                org.codelibs.elasticsearch.action.NoShardAvailableActionException::new, 130, UNKNOWN_VERSION_ADDED),
        UNAVAILABLE_SHARDS_EXCEPTION(org.codelibs.elasticsearch.action.UnavailableShardsException.class,
                org.codelibs.elasticsearch.action.UnavailableShardsException::new, 131, UNKNOWN_VERSION_ADDED),
        FLUSH_FAILED_ENGINE_EXCEPTION(org.codelibs.elasticsearch.index.engine.FlushFailedEngineException.class,
                org.codelibs.elasticsearch.index.engine.FlushFailedEngineException::new, 132, UNKNOWN_VERSION_ADDED),
        CIRCUIT_BREAKING_EXCEPTION(org.codelibs.elasticsearch.common.breaker.CircuitBreakingException.class,
                org.codelibs.elasticsearch.common.breaker.CircuitBreakingException::new, 133, UNKNOWN_VERSION_ADDED),
        NODE_NOT_CONNECTED_EXCEPTION(org.codelibs.elasticsearch.transport.NodeNotConnectedException.class,
                org.codelibs.elasticsearch.transport.NodeNotConnectedException::new, 134, UNKNOWN_VERSION_ADDED),
        STRICT_DYNAMIC_MAPPING_EXCEPTION(org.codelibs.elasticsearch.index.mapper.StrictDynamicMappingException.class,
                org.codelibs.elasticsearch.index.mapper.StrictDynamicMappingException::new, 135, UNKNOWN_VERSION_ADDED),
        RETRY_ON_REPLICA_EXCEPTION(org.codelibs.elasticsearch.action.support.replication.TransportReplicationAction.RetryOnReplicaException.class,
                org.codelibs.elasticsearch.action.support.replication.TransportReplicationAction.RetryOnReplicaException::new, 136,
            UNKNOWN_VERSION_ADDED),
        TYPE_MISSING_EXCEPTION(org.codelibs.elasticsearch.indices.TypeMissingException.class,
                org.codelibs.elasticsearch.indices.TypeMissingException::new, 137, UNKNOWN_VERSION_ADDED),
        FAILED_TO_COMMIT_CLUSTER_STATE_EXCEPTION(org.codelibs.elasticsearch.discovery.Discovery.FailedToCommitClusterStateException.class,
                org.codelibs.elasticsearch.discovery.Discovery.FailedToCommitClusterStateException::new, 140, UNKNOWN_VERSION_ADDED),
        QUERY_SHARD_EXCEPTION(org.codelibs.elasticsearch.index.query.QueryShardException.class,
                org.codelibs.elasticsearch.index.query.QueryShardException::new, 141, UNKNOWN_VERSION_ADDED),
        NO_LONGER_PRIMARY_SHARD_EXCEPTION(ShardStateAction.NoLongerPrimaryShardException.class,
                ShardStateAction.NoLongerPrimaryShardException::new, 142, UNKNOWN_VERSION_ADDED),
        SCRIPT_EXCEPTION(org.codelibs.elasticsearch.script.ScriptException.class, org.codelibs.elasticsearch.script.ScriptException::new, 143,
            UNKNOWN_VERSION_ADDED),
        NOT_MASTER_EXCEPTION(org.codelibs.elasticsearch.cluster.NotMasterException.class, org.codelibs.elasticsearch.cluster.NotMasterException::new, 144,
            UNKNOWN_VERSION_ADDED),
        STATUS_EXCEPTION(org.codelibs.elasticsearch.ElasticsearchStatusException.class, org.codelibs.elasticsearch.ElasticsearchStatusException::new, 145,
            UNKNOWN_VERSION_ADDED),
        TASK_CANCELLED_EXCEPTION(org.codelibs.elasticsearch.tasks.TaskCancelledException.class,
            org.codelibs.elasticsearch.tasks.TaskCancelledException::new, 146, Version.V_5_1_1_UNRELEASED),
        SHARD_LOCK_OBTAIN_FAILED_EXCEPTION(org.codelibs.elasticsearch.env.ShardLockObtainFailedException.class,
                                           org.codelibs.elasticsearch.env.ShardLockObtainFailedException::new, 147, Version.V_5_0_2),
        UNKNOWN_NAMED_OBJECT_EXCEPTION(org.codelibs.elasticsearch.common.xcontent.NamedXContentRegistry.UnknownNamedObjectException.class,
                org.codelibs.elasticsearch.common.xcontent.NamedXContentRegistry.UnknownNamedObjectException::new, 148, Version.V_5_2_0_UNRELEASED);

        final Class<? extends ElasticsearchException> exceptionClass;
        final FunctionThatThrowsIOException<StreamInput, ? extends ElasticsearchException> constructor;
        final int id;
        final Version versionAdded;

        <E extends ElasticsearchException> ElasticsearchExceptionHandle(Class<E> exceptionClass,
                                                                        FunctionThatThrowsIOException<StreamInput, E> constructor, int id,
                                                                        Version versionAdded) {
            // We need the exceptionClass because you can't dig it out of the constructor reliably.
            this.exceptionClass = exceptionClass;
            this.constructor = constructor;
            this.versionAdded = versionAdded;
            this.id = id;
        }
    }

    static {
        ID_TO_SUPPLIER = unmodifiableMap(Arrays
                .stream(ElasticsearchExceptionHandle.values()).collect(Collectors.toMap(e -> e.id, e -> e.constructor)));
        CLASS_TO_ELASTICSEARCH_EXCEPTION_HANDLE = unmodifiableMap(Arrays
                .stream(ElasticsearchExceptionHandle.values()).collect(Collectors.toMap(e -> e.exceptionClass, e -> e)));
    }

    public Index getIndex() {
        List<String> index = getHeader(INDEX_HEADER_KEY);
        if (index != null && index.isEmpty() == false) {
            List<String> index_uuid = getHeader(INDEX_HEADER_KEY_UUID);
            return new Index(index.get(0), index_uuid.get(0));
        }

        return null;
    }

    public ShardId getShardId() {
        List<String> shard = getHeader(SHARD_HEADER_KEY);
        if (shard != null && shard.isEmpty() == false) {
            return new ShardId(getIndex(), Integer.parseInt(shard.get(0)));
        }
        return null;
    }

    public void setIndex(Index index) {
        if (index != null) {
            addHeader(INDEX_HEADER_KEY, index.getName());
            addHeader(INDEX_HEADER_KEY_UUID, index.getUUID());
        }
    }

    public void setIndex(String index) {
        if (index != null) {
            setIndex(new Index(index, INDEX_UUID_NA_VALUE));
        }
    }

    public void setShard(ShardId shardId) {
        if (shardId != null) {
            setIndex(shardId.getIndex());
            addHeader(SHARD_HEADER_KEY, Integer.toString(shardId.id()));
        }
    }

    public void setShard(String index, int shardId) {
            setIndex(index);
            addHeader(SHARD_HEADER_KEY, Integer.toString(shardId));
    }

    public void setResources(String type, String... id) {
        assert type != null;
        addHeader(RESOURCE_HEADER_ID_KEY, id);
        addHeader(RESOURCE_HEADER_TYPE_KEY, type);
    }

    public List<String> getResourceId() {
        return getHeader(RESOURCE_HEADER_ID_KEY);
    }

    public String getResourceType() {
        List<String> header = getHeader(RESOURCE_HEADER_TYPE_KEY);
        if (header != null && header.isEmpty() == false) {
            assert header.size() == 1;
            return header.get(0);
        }
        return null;
    }

    public static void renderException(XContentBuilder builder, Params params, Exception e) throws IOException {
        builder.startObject(ERROR);
        final ElasticsearchException[] rootCauses = ElasticsearchException.guessRootCauses(e);
        builder.field(ROOT_CAUSE);
        builder.startArray();
        for (ElasticsearchException rootCause : rootCauses) {
            builder.startObject();
            rootCause.toXContent(builder, new ToXContent.DelegatingMapParams(
                    Collections.singletonMap(ElasticsearchException.REST_EXCEPTION_SKIP_CAUSE, "true"), params));
            builder.endObject();
        }
        builder.endArray();
        ElasticsearchException.toXContent(builder, params, e);
        builder.endObject();
    }

    interface FunctionThatThrowsIOException<T, R> {
        R apply(T t) throws IOException;
    }

    // lower cases and adds underscores to transitions in a name
    private static String toUnderscoreCase(String value) {
        StringBuilder sb = new StringBuilder();
        boolean changed = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) {
                if (!changed) {
                    // copy it over here
                    for (int j = 0; j < i; j++) {
                        sb.append(value.charAt(j));
                    }
                    changed = true;
                    if (i == 0) {
                        sb.append(Character.toLowerCase(c));
                    } else {
                        sb.append('_');
                        sb.append(Character.toLowerCase(c));
                    }
                } else {
                    sb.append('_');
                    sb.append(Character.toLowerCase(c));
                }
            } else {
                if (changed) {
                    sb.append(c);
                }
            }
        }
        if (!changed) {
            return value;
        }
        return sb.toString();
    }

}