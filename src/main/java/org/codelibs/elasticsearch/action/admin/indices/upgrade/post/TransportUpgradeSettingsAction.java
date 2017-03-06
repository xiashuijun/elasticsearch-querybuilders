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

package org.codelibs.elasticsearch.action.admin.indices.upgrade.post;

import org.codelibs.elasticsearch.querybuilders.log4j.message.ParameterizedMessage;
import org.codelibs.elasticsearch.querybuilders.log4j.util.Supplier;
import org.codelibs.elasticsearch.action.ActionListener;
import org.codelibs.elasticsearch.action.support.ActionFilters;
import org.codelibs.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.codelibs.elasticsearch.cluster.ClusterState;
import org.codelibs.elasticsearch.cluster.ack.ClusterStateUpdateResponse;
import org.codelibs.elasticsearch.cluster.block.ClusterBlockException;
import org.codelibs.elasticsearch.cluster.block.ClusterBlockLevel;
import org.codelibs.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.codelibs.elasticsearch.cluster.metadata.MetaDataUpdateSettingsService;
import org.codelibs.elasticsearch.cluster.service.ClusterService;
import org.codelibs.elasticsearch.common.inject.Inject;
import org.codelibs.elasticsearch.common.settings.Settings;
import org.codelibs.elasticsearch.threadpool.ThreadPool;
import org.codelibs.elasticsearch.transport.TransportService;

/**
 *
 */
public class TransportUpgradeSettingsAction extends TransportMasterNodeAction<UpgradeSettingsRequest, UpgradeSettingsResponse> {

    private final MetaDataUpdateSettingsService updateSettingsService;

    @Inject
    public TransportUpgradeSettingsAction(Settings settings, TransportService transportService, ClusterService clusterService, ThreadPool threadPool,
                                          MetaDataUpdateSettingsService updateSettingsService, IndexNameExpressionResolver indexNameExpressionResolver, ActionFilters actionFilters) {
        super(settings, UpgradeSettingsAction.NAME, transportService, clusterService, threadPool, actionFilters, indexNameExpressionResolver, UpgradeSettingsRequest::new);
        this.updateSettingsService = updateSettingsService;
    }

    @Override
    protected String executor() {
        // we go async right away....
        return ThreadPool.Names.SAME;
    }

    @Override
    protected ClusterBlockException checkBlock(UpgradeSettingsRequest request, ClusterState state) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    @Override
    protected UpgradeSettingsResponse newResponse() {
        return new UpgradeSettingsResponse();
    }

    @Override
    protected void masterOperation(final UpgradeSettingsRequest request, final ClusterState state, final ActionListener<UpgradeSettingsResponse> listener) {
        UpgradeSettingsClusterStateUpdateRequest clusterStateUpdateRequest = new UpgradeSettingsClusterStateUpdateRequest()
                .ackTimeout(request.timeout())
                .versions(request.versions())
                .masterNodeTimeout(request.masterNodeTimeout());

        updateSettingsService.upgradeIndexSettings(clusterStateUpdateRequest, new ActionListener<ClusterStateUpdateResponse>() {
            @Override
            public void onResponse(ClusterStateUpdateResponse response) {
                listener.onResponse(new UpgradeSettingsResponse(response.isAcknowledged()));
            }

            @Override
            public void onFailure(Exception t) {
                logger.debug((Supplier<?>) () -> new ParameterizedMessage("failed to upgrade minimum compatibility version settings on indices [{}]", request.versions().keySet()), t);
                listener.onFailure(t);
            }
        });
    }
}
