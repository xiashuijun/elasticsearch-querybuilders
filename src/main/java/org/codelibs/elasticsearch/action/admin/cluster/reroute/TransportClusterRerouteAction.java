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

package org.codelibs.elasticsearch.action.admin.cluster.reroute;

import org.codelibs.elasticsearch.querybuilders.log4j.Logger;
import org.codelibs.elasticsearch.querybuilders.log4j.message.ParameterizedMessage;
import org.codelibs.elasticsearch.querybuilders.log4j.util.Supplier;
import org.codelibs.elasticsearch.action.ActionListener;
import org.codelibs.elasticsearch.action.support.ActionFilters;
import org.codelibs.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.codelibs.elasticsearch.cluster.AckedClusterStateUpdateTask;
import org.codelibs.elasticsearch.cluster.ClusterState;
import org.codelibs.elasticsearch.cluster.block.ClusterBlockException;
import org.codelibs.elasticsearch.cluster.block.ClusterBlockLevel;
import org.codelibs.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.codelibs.elasticsearch.cluster.routing.allocation.AllocationService;
import org.codelibs.elasticsearch.cluster.routing.allocation.RoutingExplanations;
import org.codelibs.elasticsearch.cluster.service.ClusterService;
import org.codelibs.elasticsearch.common.Priority;
import org.codelibs.elasticsearch.common.inject.Inject;
import org.codelibs.elasticsearch.common.settings.Settings;
import org.codelibs.elasticsearch.threadpool.ThreadPool;
import org.codelibs.elasticsearch.transport.TransportService;

/**
 */
public class TransportClusterRerouteAction extends TransportMasterNodeAction<ClusterRerouteRequest, ClusterRerouteResponse> {

    private final AllocationService allocationService;

    @Inject
    public TransportClusterRerouteAction(Settings settings, TransportService transportService, ClusterService clusterService, ThreadPool threadPool,
                                         AllocationService allocationService, ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver) {
        super(settings, ClusterRerouteAction.NAME, transportService, clusterService, threadPool, actionFilters, indexNameExpressionResolver, ClusterRerouteRequest::new);
        this.allocationService = allocationService;
    }

    @Override
    protected String executor() {
        // we go async right away
        return ThreadPool.Names.SAME;
    }

    @Override
    protected ClusterBlockException checkBlock(ClusterRerouteRequest request, ClusterState state) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    @Override
    protected ClusterRerouteResponse newResponse() {
        return new ClusterRerouteResponse();
    }

    @Override
    protected void masterOperation(final ClusterRerouteRequest request, final ClusterState state, final ActionListener<ClusterRerouteResponse> listener) {
        clusterService.submitStateUpdateTask("cluster_reroute (api)", new ClusterRerouteResponseAckedClusterStateUpdateTask(logger,
            allocationService, request, listener));
    }

    static class ClusterRerouteResponseAckedClusterStateUpdateTask extends AckedClusterStateUpdateTask<ClusterRerouteResponse> {

        private final ClusterRerouteRequest request;
        private final ActionListener<ClusterRerouteResponse> listener;
        private final Logger logger;
        private final AllocationService allocationService;
        private volatile ClusterState clusterStateToSend;
        private volatile RoutingExplanations explanations;

        ClusterRerouteResponseAckedClusterStateUpdateTask(Logger logger, AllocationService allocationService, ClusterRerouteRequest request,
                                                          ActionListener<ClusterRerouteResponse> listener) {
            super(Priority.IMMEDIATE, request, listener);
            this.request = request;
            this.listener = listener;
            this.logger = logger;
            this.allocationService = allocationService;
        }

        @Override
        protected ClusterRerouteResponse newResponse(boolean acknowledged) {
            return new ClusterRerouteResponse(acknowledged, clusterStateToSend, explanations);
        }

        @Override
        public void onAckTimeout() {
            listener.onResponse(new ClusterRerouteResponse(false, clusterStateToSend, new RoutingExplanations()));
        }

        @Override
        public void onFailure(String source, Exception e) {
            logger.debug((Supplier<?>) () -> new ParameterizedMessage("failed to perform [{}]", source), e);
            super.onFailure(source, e);
        }

        @Override
        public ClusterState execute(ClusterState currentState) {
            AllocationService.CommandsResult commandsResult =
                allocationService.reroute(currentState, request.getCommands(), request.explain(), request.isRetryFailed());
            clusterStateToSend = commandsResult.getClusterState();
            explanations = commandsResult.explanations();
            if (request.dryRun()) {
                return currentState;
            }
            return commandsResult.getClusterState();
        }
    }
}
