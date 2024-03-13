package org.opensearch.dataprepper.plugins.mongo.leader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.dataprepper.model.source.coordinator.enhanced.EnhancedSourceCoordinator;
import org.opensearch.dataprepper.model.source.coordinator.enhanced.EnhancedSourcePartition;
import org.opensearch.dataprepper.plugins.mongo.configuration.CollectionConfig;
import org.opensearch.dataprepper.plugins.mongo.coordination.partition.LeaderPartition;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class LeaderSchedulerTest {
    @Mock
    private EnhancedSourceCoordinator coordinator;

    @Mock
    private CollectionConfig collectionConfig;

    @Mock
    private CollectionConfig.ExportConfig exportConfig;

    private LeaderScheduler leaderScheduler;
    private LeaderPartition leaderPartition;

    @Test
    void test_non_leader_run() throws InterruptedException {
        leaderScheduler = new LeaderScheduler(coordinator, List.of(collectionConfig));
        given(coordinator.acquireAvailablePartition(LeaderPartition.PARTITION_TYPE)).willReturn(Optional.empty());

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> leaderScheduler.run());
        Thread.sleep(100);
        executorService.shutdownNow();

        verifyNoInteractions(collectionConfig);
    }

    @Test
    void test_should_init() throws InterruptedException {

        leaderScheduler = new LeaderScheduler(coordinator, List.of(collectionConfig));
        leaderPartition = new LeaderPartition();
        given(coordinator.acquireAvailablePartition(LeaderPartition.PARTITION_TYPE)).willReturn(Optional.of(leaderPartition));
        given(collectionConfig.getIngestionMode()).willReturn(CollectionConfig.IngestionMode.EXPORT_STREAM);
        given(collectionConfig.getExportConfig()).willReturn(exportConfig);
        given(exportConfig.getItemsPerPartition()).willReturn(new Random().nextInt());
        given(collectionConfig.getCollection()).willReturn(UUID.randomUUID().toString());

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> leaderScheduler.run());
        Thread.sleep(100);
        executorService.shutdownNow();


        // Acquire the init partition
        verify(coordinator).acquireAvailablePartition(eq(LeaderPartition.PARTITION_TYPE));

        // Should create 1 export partition + 1 stream partitions + 1 global table state
        verify(coordinator, times(3)).createPartition(any(EnhancedSourcePartition.class));
        verify(coordinator).giveUpPartition(leaderPartition);

        assertThat(leaderPartition.getProgressState().get().isInitialized(), equalTo(true));
    }

    @Test
    void test_should_init_export() throws InterruptedException {

        leaderScheduler = new LeaderScheduler(coordinator, List.of(collectionConfig));
        leaderPartition = new LeaderPartition();
        given(coordinator.acquireAvailablePartition(LeaderPartition.PARTITION_TYPE)).willReturn(Optional.of(leaderPartition));
        given(collectionConfig.getIngestionMode()).willReturn(CollectionConfig.IngestionMode.EXPORT);
        given(collectionConfig.getExportConfig()).willReturn(exportConfig);
        given(exportConfig.getItemsPerPartition()).willReturn(new Random().nextInt());
        given(collectionConfig.getCollection()).willReturn(UUID.randomUUID().toString());

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> leaderScheduler.run());
        Thread.sleep(100);
        executorService.shutdownNow();


        // Acquire the init partition
        verify(coordinator).acquireAvailablePartition(eq(LeaderPartition.PARTITION_TYPE));

        // Should create 1 export partition + 1 stream partitions + 1 global table state
        verify(coordinator, times(2)).createPartition(any(EnhancedSourcePartition.class));
        verify(coordinator).giveUpPartition(leaderPartition);

        assertThat(leaderPartition.getProgressState().get().isInitialized(), equalTo(true));
    }

    @Test
    void test_should_init_stream() throws InterruptedException {

        leaderScheduler = new LeaderScheduler(coordinator, List.of(collectionConfig));
        leaderPartition = new LeaderPartition();
        given(coordinator.acquireAvailablePartition(LeaderPartition.PARTITION_TYPE)).willReturn(Optional.of(leaderPartition));
        given(collectionConfig.getIngestionMode()).willReturn(CollectionConfig.IngestionMode.STREAM);
        given(collectionConfig.getCollection()).willReturn(UUID.randomUUID().toString());

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> leaderScheduler.run());
        Thread.sleep(100);
        executorService.shutdownNow();


        // Acquire the init partition
        verify(coordinator).acquireAvailablePartition(eq(LeaderPartition.PARTITION_TYPE));

        // Should create 1 export partition + 1 stream partitions + 1 global table state
        verify(coordinator, times(2)).createPartition(any(EnhancedSourcePartition.class));
        verify(coordinator).giveUpPartition(leaderPartition);

        assertThat(leaderPartition.getProgressState().get().isInitialized(), equalTo(true));
    }
}
