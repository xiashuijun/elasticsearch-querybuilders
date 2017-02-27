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
package org.codelibs.elasticsearch.rest.action.admin.indices;

import org.codelibs.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesRequest;
import org.codelibs.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesResponse;
import org.codelibs.elasticsearch.client.node.NodeClient;
import org.codelibs.elasticsearch.common.bytes.BytesArray;
import org.codelibs.elasticsearch.common.inject.Inject;
import org.codelibs.elasticsearch.common.settings.Settings;
import org.codelibs.elasticsearch.rest.BaseRestHandler;
import org.codelibs.elasticsearch.rest.BytesRestResponse;
import org.codelibs.elasticsearch.rest.RestController;
import org.codelibs.elasticsearch.rest.RestRequest;
import org.codelibs.elasticsearch.rest.RestResponse;
import org.codelibs.elasticsearch.rest.action.RestResponseListener;

import java.io.IOException;
import java.util.Set;

import static org.codelibs.elasticsearch.rest.RestRequest.Method.HEAD;
import static org.codelibs.elasticsearch.rest.RestStatus.NOT_FOUND;
import static org.codelibs.elasticsearch.rest.RestStatus.OK;

public class RestHeadIndexTemplateAction extends BaseRestHandler {

    @Inject
    public RestHeadIndexTemplateAction(Settings settings, RestController controller) {
        super(settings);

        controller.registerHandler(HEAD, "/_template/{name}", this);
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        GetIndexTemplatesRequest getIndexTemplatesRequest = new GetIndexTemplatesRequest(request.param("name"));
        getIndexTemplatesRequest.local(request.paramAsBoolean("local", getIndexTemplatesRequest.local()));
        getIndexTemplatesRequest.masterNodeTimeout(request.paramAsTime("master_timeout", getIndexTemplatesRequest.masterNodeTimeout()));
        return channel ->
                client.admin()
                        .indices()
                        .getTemplates(getIndexTemplatesRequest, new RestResponseListener<GetIndexTemplatesResponse>(channel) {
                            @Override
                            public RestResponse buildResponse(GetIndexTemplatesResponse getIndexTemplatesResponse) {
                                boolean templateExists = getIndexTemplatesResponse.getIndexTemplates().size() > 0;
                                if (templateExists) {
                                    return new BytesRestResponse(OK, BytesRestResponse.TEXT_CONTENT_TYPE, BytesArray.EMPTY);
                                } else {
                                    return new BytesRestResponse(NOT_FOUND, BytesRestResponse.TEXT_CONTENT_TYPE, BytesArray.EMPTY);
                                }
                            }
                        });
    }

    @Override
    protected Set<String> responseParams() {
        return Settings.FORMAT_PARAMS;
    }

}