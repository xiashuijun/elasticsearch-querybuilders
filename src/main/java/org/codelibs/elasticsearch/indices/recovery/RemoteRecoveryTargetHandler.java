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
package org.codelibs.elasticsearch.indices.recovery;

import org.apache.lucene.store.RateLimiter;
import org.codelibs.elasticsearch.ElasticsearchException;
import org.codelibs.elasticsearch.cluster.node.DiscoveryNode;
import org.codelibs.elasticsearch.common.bytes.BytesReference;
import org.codelibs.elasticsearch.index.shard.ShardId;
import org.codelibs.elasticsearch.index.store.Store;
import org.codelibs.elasticsearch.index.store.StoreFileMetaData;
import org.codelibs.elasticsearch.index.translog.Translog;
import org.codelibs.elasticsearch.transport.EmptyTransportResponseHandler;
import org.codelibs.elasticsearch.transport.TransportRequestOptions;
import org.codelibs.elasticsearch.transport.TransportService;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class RemoteRecoveryTargetHandler implements RecoveryTargetHandler {
    private final TransportService transportService;
    private final long recoveryId;
    private final ShardId shardId;
    private final DiscoveryNode targetNode;
    private final RecoverySettings recoverySettings;

    private final TransportRequestOptions translogOpsRequestOptions;
    private final TransportRequestOptions fileChunkRequestOptions;

    private final AtomicLong bytesSinceLastPause = new AtomicLong();

    private final Consumer<Long> onSourceThrottle;

    public RemoteRecoveryTargetHandler(long recoveryId, ShardId shardId, TransportService transportService, DiscoveryNode targetNode,
                                       RecoverySettings recoverySettings, Consumer<Long> onSourceThrottle) {
        this.transportService = transportService;


        this.recoveryId = recoveryId;
        this.shardId = shardId;
        this.targetNode = targetNode;
        this.recoverySettings = recoverySettings;
        this.onSourceThrottle = onSourceThrottle;
        this.translogOpsRequestOptions = TransportRequestOptions.builder()
                .withCompress(true)
                .withType(TransportRequestOptions.Type.RECOVERY)
                .withTimeout(recoverySettings.internalActionLongTimeout())
                .build();
        this.fileChunkRequestOptions = TransportRequestOptions.builder()
                .withCompress(false)  // lucene files are already compressed and therefore compressing this won't really help much so
                // we are saving the cpu for other things
                .withType(TransportRequestOptions.Type.RECOVERY)
                .withTimeout(recoverySettings.internalActionTimeout())
                .build();

    }

    @Override
    public void prepareForTranslogOperations(int totalTranslogOps, long maxUnsafeAutoIdTimestamp) throws IOException {
        transportService.submitRequest(targetNode, PeerRecoveryTargetService.Actions.PREPARE_TRANSLOG,
                new RecoveryPrepareForTranslogOperationsRequest(recoveryId, shardId, totalTranslogOps, maxUnsafeAutoIdTimestamp),
                TransportRequestOptions.builder().withTimeout(recoverySettings.internalActionTimeout()).build(),
                EmptyTransportResponseHandler.INSTANCE_SAME).txGet();
    }

    @Override
    public void finalizeRecovery() {
        transportService.submitRequest(targetNode, PeerRecoveryTargetService.Actions.FINALIZE,
                new RecoveryFinalizeRecoveryRequest(recoveryId, shardId),
                TransportRequestOptions.builder().withTimeout(recoverySettings.internalActionLongTimeout()).build(),
                EmptyTransportResponseHandler.INSTANCE_SAME).txGet();
    }

    @Override
    public void ensureClusterStateVersion(long clusterStateVersion) {
        transportService.submitRequest(targetNode, PeerRecoveryTargetService.Actions.WAIT_CLUSTERSTATE,
            new RecoveryWaitForClusterStateRequest(recoveryId, shardId, clusterStateVersion),
            TransportRequestOptions.builder().withTimeout(recoverySettings.internalActionLongTimeout()).build(),
            EmptyTransportResponseHandler.INSTANCE_SAME).txGet();
    }

    @Override
    public void indexTranslogOperations(List<Translog.Operation> operations, int totalTranslogOps) {
        final RecoveryTranslogOperationsRequest translogOperationsRequest = new RecoveryTranslogOperationsRequest(
                recoveryId, shardId, operations, totalTranslogOps);
        transportService.submitRequest(targetNode, PeerRecoveryTargetService.Actions.TRANSLOG_OPS, translogOperationsRequest,
                translogOpsRequestOptions, EmptyTransportResponseHandler.INSTANCE_SAME).txGet();
    }

    @Override
    public void receiveFileInfo(List<String> phase1FileNames, List<Long> phase1FileSizes, List<String> phase1ExistingFileNames,
                                List<Long> phase1ExistingFileSizes, int totalTranslogOps) {

        RecoveryFilesInfoRequest recoveryInfoFilesRequest = new RecoveryFilesInfoRequest(recoveryId, shardId,
                phase1FileNames, phase1FileSizes, phase1ExistingFileNames, phase1ExistingFileSizes, totalTranslogOps);
        transportService.submitRequest(targetNode, PeerRecoveryTargetService.Actions.FILES_INFO, recoveryInfoFilesRequest,
                TransportRequestOptions.builder().withTimeout(recoverySettings.internalActionTimeout()).build(),
                EmptyTransportResponseHandler.INSTANCE_SAME).txGet();

    }

    @Override
    public void cleanFiles(int totalTranslogOps, Store.MetadataSnapshot sourceMetaData) throws IOException {
        transportService.submitRequest(targetNode, PeerRecoveryTargetService.Actions.CLEAN_FILES,
                new RecoveryCleanFilesRequest(recoveryId, shardId, sourceMetaData, totalTranslogOps),
                TransportRequestOptions.builder().withTimeout(recoverySettings.internalActionTimeout()).build(),
                EmptyTransportResponseHandler.INSTANCE_SAME).txGet();
    }

    @Override
    public void writeFileChunk(StoreFileMetaData fileMetaData, long position, BytesReference content, boolean
            lastChunk, int totalTranslogOps) throws IOException {
        // Pause using the rate limiter, if desired, to throttle the recovery
        final long throttleTimeInNanos;
        // always fetch the ratelimiter - it might be updated in real-time on the recovery settings
        final RateLimiter rl = recoverySettings.rateLimiter();
        if (rl != null) {
            long bytes = bytesSinceLastPause.addAndGet(content.length());
            if (bytes > rl.getMinPauseCheckBytes()) {
                // Time to pause
                bytesSinceLastPause.addAndGet(-bytes);
                try {
                    throttleTimeInNanos = rl.pause(bytes);
                    onSourceThrottle.accept(throttleTimeInNanos);
                } catch (IOException e) {
                    throw new ElasticsearchException("failed to pause recovery", e);
                }
            } else {
                throttleTimeInNanos = 0;
            }
        } else {
            throttleTimeInNanos = 0;
        }

        transportService.submitRequest(targetNode, PeerRecoveryTargetService.Actions.FILE_CHUNK,
                new RecoveryFileChunkRequest(recoveryId, shardId, fileMetaData, position, content, lastChunk,
                        totalTranslogOps,
                                /* we send totalOperations with every request since we collect stats on the target and that way we can
                                 * see how many translog ops we accumulate while copying files across the network. A future optimization
                                 * would be in to restart file copy again (new deltas) if we have too many translog ops are piling up.
                                 */
                        throttleTimeInNanos), fileChunkRequestOptions, EmptyTransportResponseHandler.INSTANCE_SAME).txGet();
    }
}