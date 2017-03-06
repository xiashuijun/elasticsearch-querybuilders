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

package org.codelibs.elasticsearch.action.get;

import org.codelibs.elasticsearch.querybuilders.log4j.message.ParameterizedMessage;
import org.codelibs.elasticsearch.querybuilders.log4j.util.Supplier;
import org.codelibs.elasticsearch.ElasticsearchException;
import org.codelibs.elasticsearch.action.support.ActionFilters;
import org.codelibs.elasticsearch.action.support.TransportActions;
import org.codelibs.elasticsearch.action.support.single.shard.TransportSingleShardAction;
import org.codelibs.elasticsearch.cluster.ClusterState;
import org.codelibs.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.codelibs.elasticsearch.cluster.routing.ShardIterator;
import org.codelibs.elasticsearch.cluster.service.ClusterService;
import org.codelibs.elasticsearch.common.inject.Inject;
import org.codelibs.elasticsearch.common.settings.Settings;
import org.codelibs.elasticsearch.index.IndexService;
import org.codelibs.elasticsearch.index.get.GetResult;
import org.codelibs.elasticsearch.index.shard.IndexShard;
import org.codelibs.elasticsearch.index.shard.ShardId;
import org.codelibs.elasticsearch.indices.IndicesService;
import org.codelibs.elasticsearch.threadpool.ThreadPool;
import org.codelibs.elasticsearch.transport.TransportService;

public class TransportShardMultiGetAction extends TransportSingleShardAction<MultiGetShardRequest, MultiGetShardResponse> {

    private static final String ACTION_NAME = MultiGetAction.NAME + "[shard]";

    private final IndicesService indicesService;

    @Inject
    public TransportShardMultiGetAction(Settings settings, ClusterService clusterService, TransportService transportService,
                                        IndicesService indicesService, ThreadPool threadPool, ActionFilters actionFilters,
                                        IndexNameExpressionResolver indexNameExpressionResolver) {
        super(settings, ACTION_NAME, threadPool, clusterService, transportService, actionFilters, indexNameExpressionResolver,
                MultiGetShardRequest::new, ThreadPool.Names.GET);
        this.indicesService = indicesService;
    }

    @Override
    protected boolean isSubAction() {
        return true;
    }

    @Override
    protected MultiGetShardResponse newResponse() {
        return new MultiGetShardResponse();
    }

    @Override
    protected boolean resolveIndex(MultiGetShardRequest request) {
        return true;
    }

    @Override
    protected ShardIterator shards(ClusterState state, InternalRequest request) {
        return clusterService.operationRouting()
                .getShards(state, request.request().index(), request.request().shardId(), request.request().preference());
    }

    @Override
    protected MultiGetShardResponse shardOperation(MultiGetShardRequest request, ShardId shardId) {
        IndexService indexService = indicesService.indexServiceSafe(shardId.getIndex());
        IndexShard indexShard = indexService.getShard(shardId.id());

        if (request.refresh() && !request.realtime()) {
            indexShard.refresh("refresh_flag_mget");
        }

        MultiGetShardResponse response = new MultiGetShardResponse();
        for (int i = 0; i < request.locations.size(); i++) {
            MultiGetRequest.Item item = request.items.get(i);
            try {
                GetResult getResult = indexShard.getService().get(item.type(), item.id(), item.storedFields(), request.realtime(), item.version(),
                    item.versionType(), item.fetchSourceContext());
                response.add(request.locations.get(i), new GetResponse(getResult));
            } catch (Exception e) {
                if (TransportActions.isShardNotAvailableException(e)) {
                    throw (ElasticsearchException) e;
                } else {
                    logger.debug((Supplier<?>) () -> new ParameterizedMessage("{} failed to execute multi_get for [{}]/[{}]", shardId,
                        item.type(), item.id()), e);
                    response.add(request.locations.get(i), new MultiGetResponse.Failure(request.index(), item.type(), item.id(), e));
                }
            }
        }

        return response;
    }
}
