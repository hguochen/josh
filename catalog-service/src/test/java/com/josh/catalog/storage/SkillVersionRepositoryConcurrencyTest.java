package com.josh.catalog.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * phase2_design_specification.md Immediate Fixes ("Concurrent publish of a new
 * skill name fails ungracefully"): proves what really happens — with real
 * threads against the real SQLite file, not a mock — when two writers race to
 * insert the same (name, version). Two threads target the identical row
 * (rather than relying on CatalogService's own read-then-write timing, which
 * isn't reliably reproducible from a test), synchronized with a CyclicBarrier
 * so both genuinely overlap. This is also what confirmed
 * UncategorizedSQLException (not the usual DataIntegrityViolationException —
 * Spring has no SQLite entry in its error-code translation table) as the type
 * CatalogService's fix actually needs to catch, rather than guessing at it.
 */
@SpringBootTest
class SkillVersionRepositoryConcurrencyTest {

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void catalogStorageRoot(DynamicPropertyRegistry registry) {
        registry.add("catalog.storage.root", () -> storageRoot.toString());
    }

    @Autowired
    private SkillVersionRepository repository;

    @Test
    void concurrentInsertsOfTheSameNameAndVersionCollideCleanlyInsteadOfCorruptingData() throws Exception {
        String name = "concurrency-probe-" + System.nanoTime();
        int threadCount = 2;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        List<Future<Exception>> results = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            int attempt = i;
            results.add(pool.submit(() -> {
                barrier.await();
                try {
                    repository.insert(new SkillVersion(
                        null, name, 1, "desc", "author-" + attempt,
                        Instant.now().toString(), "checksum-" + attempt, name + "/1.zip"
                    ));
                    return null;
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        pool.shutdown();

        List<Exception> outcomes = new ArrayList<>();
        for (Future<Exception> future : results) {
            outcomes.add(future.get(10, TimeUnit.SECONDS));
        }

        long failureCount = outcomes.stream().filter(Objects::nonNull).count();
        assertThat(failureCount).isEqualTo(1);

        Exception failure = outcomes.stream().filter(Objects::nonNull).findFirst().orElseThrow();
        assertThat(failure).isInstanceOf(UncategorizedSQLException.class);
        assertThat(((UncategorizedSQLException) failure).getSQLException().getErrorCode()).isEqualTo(19);

        // Data integrity held: exactly one row exists, not zero, not two.
        assertThat(repository.findAllVersions(name)).hasSize(1);
    }
}
