package org.opensearch.dataprepper.plugins.mongo.leader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.model.source.coordinator.enhanced.EnhancedSourceCoordinator;
import org.opensearch.dataprepper.model.source.coordinator.enhanced.EnhancedSourcePartition;
import org.opensearch.dataprepper.plugins.mongo.configuration.CollectionConfig;
import org.opensearch.dataprepper.plugins.mongo.coordination.partition.ExportPartition;
import org.opensearch.dataprepper.plugins.mongo.coordination.partition.GlobalState;
import org.opensearch.dataprepper.plugins.mongo.coordination.partition.LeaderPartition;
import org.opensearch.dataprepper.plugins.mongo.coordination.partition.StreamPartition;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.opensearch.dataprepper.plugins.mongo.leader.LeaderScheduler.DEFAULT_EXTEND_LEASE_MINUTES;
import static org.opensearch.dataprepper.plugins.mongo.leader.LeaderScheduler.EXPORT_PREFIX;

@ExtendWith(MockitoExtension.class)
public class LeaderSchedulerTest {
    @Mock
    private EnhancedSourceCoordinator coordinator;

    @Mock
    private CollectionConfig collectionConfig;

    @Mock
    private CollectionConfig.ExportConfig exportConfig;

    @Captor
    private ArgumentCaptor<EnhancedSourcePartition> enhancedSourcePartitionArgumentCaptor;

    private LeaderScheduler leaderScheduler;
    private LeaderPartition leaderPartition;

    @Test
    void test_non_leader_run() {
        leaderScheduler = new LeaderScheduler(coordinator, List.of(collectionConfig), Duration.ofMillis(100));
        given(coordinator.acquireAvailablePartition(LeaderPartition.PARTITION_TYPE)).willReturn(Optional.empty());

        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> leaderScheduler.run());
        await()
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> verifyNoInteractions(collectionConfig));
        executorService.shutdownNow();
    }

    @Test
    void test_should_init() {

        leaderScheduler = new LeaderScheduler(coordinator, List.of(collectionConfig), Duration.ofMillis(100));
        leaderPartition = new LeaderPartition();
        given(coordinator.acquireAvailablePartition(LeaderPartition.PARTITION_TYPE)).willReturn(Optional.of(leaderPartition));
        given(collectionConfig.isExportRequired()).willReturn(true);
        given(collectionConfig.isStreamRequired()).willReturn(true);
        given(collectionConfig.getExportConfig()).willReturn(exportConfig);
        given(exportConfig.getItemsPerPartition()).willReturn(new Random().nextInt());
        given(collectionConfig.getCollection()).willReturn(UUID.randomUUID().toString());

        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final Future<?> future = executorService.submit(() -> leaderScheduler.run());

        // Acquire the init partition
        await()
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> verify(coordinator, atLeast(1)).acquireAvailablePartition(eq(LeaderPartition.PARTITION_TYPE)));

        future.cancel(true);

        await()
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() ->  verify(coordinator).giveUpPartition(leaderPartition));

        // Should create 1 export partition + 1 stream partitions + 2 global table state
        verify(coordinator, times(4)).createPartition(
                enhancedSourcePartitionArgumentCaptor.capture());
        verify(coordinator, atLeast(1)).saveProgressStateForPartition(leaderPartition, Duration.ofMinutes(DEFAULT_EXTEND_LEASE_MINUTES));

        assertThat(leaderPartition.getProgressState().get().isInitialized(), equalTo(true));
        final List<EnhancedSourcePartition> allEnhancedSourcePartitions =
                enhancedSourcePartitionArgumentCaptor.getAllValues();
        assertThat(allEnhancedSourcePartitions.get(0), instanceOf(GlobalState.class));
        assertThat(allEnhancedSourcePartitions.get(1), instanceOf(ExportPartition.class));
        assertThat(allEnhancedSourcePartitions.get(2), instanceOf(GlobalState.class));
        final GlobalState exportGlobalState = (GlobalState) allEnhancedSourcePartitions.get(2);
        assertThat(exportGlobalState.getPartitionKey(), startsWith(EXPORT_PREFIX));
        final Optional<Map<String, Object>> exportGlobalProgressStateOptional = exportGlobalState.getProgressState();
        assertThat(exportGlobalProgressStateOptional.isPresent(), is(true));
        final Map<String, Object> exportGlobalProgressState = exportGlobalProgressStateOptional.get();
        assertThat(exportGlobalProgressState.get("totalPartitions"), equalTo(0L));
        assertThat(exportGlobalProgressState.get("loadedPartitions"), equalTo(0L));
        assertThat(exportGlobalProgressState.get("loadedRecords"), equalTo(0L));
        assertThat(allEnhancedSourcePartitions.get(3), instanceOf(StreamPartition.class));
        executorService.shutdownNow();
    }

    @Test
    void test_should_init_export() {

        leaderScheduler = new LeaderScheduler(coordinator, List.of(collectionConfig), Duration.ofMillis(100));
        leaderPartition = new LeaderPartition();
        given(coordinator.acquireAvailablePartition(LeaderPartition.PARTITION_TYPE)).willReturn(Optional.of(leaderPartition));
        given(collectionConfig.isExportRequired()).willReturn(true);
        given(collectionConfig.getExportConfig()).willReturn(exportConfig);
        given(exportConfig.getItemsPerPartition()).willReturn(new Random().nextInt());
        given(collectionConfig.getCollection()).willReturn(UUID.randomUUID().toString());

        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final Future<?> future = executorService.submit(() -> leaderScheduler.run());

        // Acquire the init partition
        await()
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() ->  verify(coordinator, atLeast(1)).acquireAvailablePartition(eq(LeaderPartition.PARTITION_TYPE)));

        future.cancel(true);

        await()
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() ->  verify(coordinator).giveUpPartition(leaderPartition));

        // Should create 1 export partition + 1 stream partitions + 2 global table state
        verify(coordinator, times(3)).createPartition(
                enhancedSourcePartitionArgumentCaptor.capture());
        verify(coordinator, atLeast(1)).saveProgressStateForPartition(leaderPartition, Duration.ofMinutes(DEFAULT_EXTEND_LEASE_MINUTES));

        assertThat(leaderPartition.getProgressState().get().isInitialized(), equalTo(true));
        final List<EnhancedSourcePartition> allEnhancedSourcePartitions =
                enhancedSourcePartitionArgumentCaptor.getAllValues();
        assertThat(allEnhancedSourcePartitions.get(0), instanceOf(GlobalState.class));
        assertThat(allEnhancedSourcePartitions.get(1), instanceOf(ExportPartition.class));
        assertThat(allEnhancedSourcePartitions.get(2), instanceOf(GlobalState.class));
        final GlobalState exportGlobalState = (GlobalState) allEnhancedSourcePartitions.get(2);
        assertThat(exportGlobalState.getPartitionKey(), startsWith(EXPORT_PREFIX));
        final Optional<Map<String, Object>> exportGlobalProgressStateOptional = exportGlobalState.getProgressState();
        assertThat(exportGlobalProgressStateOptional.isPresent(), is(true));
        final Map<String, Object> exportGlobalProgressState = exportGlobalProgressStateOptional.get();
        assertThat(exportGlobalProgressState.get("totalPartitions"), equalTo(0L));
        assertThat(exportGlobalProgressState.get("loadedPartitions"), equalTo(0L));
        assertThat(exportGlobalProgressState.get("loadedRecords"), equalTo(0L));
        executorService.shutdownNow();
    }

    @Test
    void test_should_init_stream() {

        leaderScheduler = new LeaderScheduler(coordinator, List.of(collectionConfig), Duration.ofMillis(100));
        leaderPartition = new LeaderPartition();
        given(coordinator.acquireAvailablePartition(LeaderPartition.PARTITION_TYPE)).willReturn(Optional.of(leaderPartition));
        given(collectionConfig.isStreamRequired()).willReturn(true);
        given(collectionConfig.getCollection()).willReturn(UUID.randomUUID().toString());

        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final Future<?> future = executorService.submit(() -> leaderScheduler.run());
        await()
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> verify(coordinator, atLeast(1)).acquireAvailablePartition(eq(LeaderPartition.PARTITION_TYPE)));

        future.cancel(true);

        await()
            .atMost(Duration.ofSeconds(2))
            .untilAsserted(() ->  verify(coordinator).giveUpPartition(leaderPartition));

        // Should create 1 stream partitions + 1 global table state
        verify(coordinator, times(2)).createPartition(
                enhancedSourcePartitionArgumentCaptor.capture());
        verify(coordinator, atLeast(1)).saveProgressStateForPartition(leaderPartition, Duration.ofMinutes(DEFAULT_EXTEND_LEASE_MINUTES));

        assertThat(leaderPartition.getProgressState().get().isInitialized(), equalTo(true));
        final List<EnhancedSourcePartition> allEnhancedSourcePartitions =
                enhancedSourcePartitionArgumentCaptor.getAllValues();
        assertThat(allEnhancedSourcePartitions.get(0), instanceOf(GlobalState.class));
        assertThat(allEnhancedSourcePartitions.get(1), instanceOf(StreamPartition.class));
        executorService.shutdownNow();
    }
}
