package com.usethatmeme.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.usethatmeme.IntegrationTestBase;
import com.usethatmeme.application.port.out.GenerationJobStore;
import com.usethatmeme.application.port.out.GenerationJobStore.ClaimedJob;
import com.usethatmeme.application.port.out.PasswordHasher;
import com.usethatmeme.application.port.out.UserRepository;
import com.usethatmeme.domain.user.Role;
import com.usethatmeme.domain.user.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Proves the queue hands every job to exactly one worker, however many compete for it.
 * The background worker is switched off so only this test claims jobs.
 */
@TestPropertySource(properties = "usethatmeme.generation.worker-enabled=false")
class JobQueueConcurrencyTest extends IntegrationTestBase {

    private static final int JOBS = 60;
    private static final int WORKERS = 12;

    @Autowired GenerationJobStore store;
    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;
    @Autowired JdbcTemplate jdbc;

    @Test
    void everyJobIsClaimedExactlyOnceEvenWithManyCompetingWorkers() throws Exception {
        User user = User.register("queue-" + UUID.randomUUID(), hasher.hash("irrelevant-" + UUID.randomUUID()), Role.USER);
        users.save(user);
        Set<UUID> expected = new HashSet<>();
        for (int i = 0; i < JOBS; i++) {
            UUID id = UUID.randomUUID();
            expected.add(id);
            store.enqueue(id, user.id(), "job " + i, GenerationJobStore.QuotaLimits.unlimited());
        }

        List<ClaimedJob> claimed = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(WORKERS)) {
            List<Future<?>> workers = new ArrayList<>();
            for (int w = 0; w < WORKERS; w++) {
                workers.add(pool.submit(() -> {
                    go.await();
                    List<ClaimedJob> batch;
                    while (!(batch = store.claim(3)).isEmpty()) {
                        claimed.addAll(batch);
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> worker : workers) {
                worker.get();
            }
        }

        // Other Spring contexts cached from earlier tests share this database and run their own
        // background worker, so a few jobs may be claimed by them instead. That is fine: what
        // matters is that every job was claimed exactly once by somebody.
        List<UUID> claimedByUs = claimed.stream().map(ClaimedJob::id).filter(expected::contains).toList();
        assertThat(claimedByUs).as("no job claimed twice by our workers").doesNotHaveDuplicates();
        assertThat(claimed).allSatisfy(job -> assertThat(job.attempts()).isEqualTo(1));

        Integer stillPending = jdbc.queryForObject(
                "SELECT count(*) FROM generation_job WHERE requester_id = ?::uuid AND status = 'PENDING'",
                Integer.class, user.id().toString());
        assertThat(stillPending).as("no job left behind").isZero();
        Integer claimedExactlyOnce = jdbc.queryForObject(
                "SELECT count(*) FROM generation_job WHERE requester_id = ?::uuid AND attempts = 1",
                Integer.class, user.id().toString());
        assertThat(claimedExactlyOnce).as("every job claimed exactly once").isEqualTo(JOBS);
    }
}
