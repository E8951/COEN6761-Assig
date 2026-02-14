package coen448.computablefuture.test;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncProcessorTest {

    /**
     * Simple stub (test double).
     * No Mockito allowed.
     */
    static class StubMicroservice extends Microservice {

        private final CompletableFuture<String> future;

        StubMicroservice(String id, CompletableFuture<String> future) {
            super(id);
            this.future = future;
        }

        @Override
        public CompletableFuture<String> retrieveAsync(String input) {
            return future;
        }
    }

    // =========================================================
    // TASK 2A — FAIL FAST POLICY TESTS
    // =========================================================

    @Test
    void failFast_allSuccessful_returnsConcatenatedResult() throws Exception {

        Microservice s1 = new StubMicroservice("S1",
                CompletableFuture.completedFuture("A"));
        Microservice s2 = new StubMicroservice("S2",
                CompletableFuture.completedFuture("B"));

        AsyncProcessor processor = new AsyncProcessor();

        String result = processor.processAsyncFailFast(
                List.of(s1, s2),
                List.of("m1", "m2")
        ).get(1, TimeUnit.SECONDS);

        assertEquals("A B", result);
    }

    @Test
    void failFast_oneFailure_propagatesException() {

        Microservice success = new StubMicroservice("OK",
                CompletableFuture.completedFuture("OK"));
        Microservice failure = new StubMicroservice("FAIL",
                CompletableFuture.failedFuture(new RuntimeException("boom")));

        AsyncProcessor processor = new AsyncProcessor();

        CompletableFuture<String> result =
                processor.processAsyncFailFast(
                        List.of(success, failure),
                        List.of("m1", "m2"));

        assertThrows(ExecutionException.class,
                () -> result.get(1, TimeUnit.SECONDS));
    }

    // =========================================================
    // TASK 2B — FAIL PARTIAL POLICY TESTS
    // =========================================================

    @Test
    void failPartial_someFailures_returnsOnlySuccessfulResults() throws Exception {

        Microservice s1 = new StubMicroservice("S1",
                CompletableFuture.completedFuture("R1"));
        Microservice s2 = new StubMicroservice("S2",
                CompletableFuture.failedFuture(new RuntimeException("error")));
        Microservice s3 = new StubMicroservice("S3",
                CompletableFuture.completedFuture("R3"));

        AsyncProcessor processor = new AsyncProcessor();

        List<String> results =
                processor.processAsyncFailPartial(
                                List.of(s1, s2, s3),
                                List.of("a", "b", "c"))
                        .get(1, TimeUnit.SECONDS);

        assertEquals(List.of("R1", "R3"), results);
    }

    @Test
    void failPartial_allFailures_returnsEmptyList() throws Exception {

        Microservice s1 = new StubMicroservice("S1",
                CompletableFuture.failedFuture(new RuntimeException()));
        Microservice s2 = new StubMicroservice("S2",
                CompletableFuture.failedFuture(new RuntimeException()));

        AsyncProcessor processor = new AsyncProcessor();

        List<String> results =
                processor.processAsyncFailPartial(
                                List.of(s1, s2),
                                List.of("a", "b"))
                        .get(1, TimeUnit.SECONDS);

        assertTrue(results.isEmpty());
    }

    // =========================================================
    // TASK 2C — FAIL SOFT POLICY TESTS
    // =========================================================

    @Test
    void failSoft_someFailures_replacesWithFallback() throws Exception {

        Microservice s1 = new StubMicroservice("S1",
                CompletableFuture.completedFuture("OK"));
        Microservice s2 = new StubMicroservice("S2",
                CompletableFuture.failedFuture(new RuntimeException("error")));

        AsyncProcessor processor = new AsyncProcessor();

        String result =
                processor.processAsyncFailSoft(
                                List.of(s1, s2),
                                List.of("a", "b"),
                                "FALLBACK")
                        .get(1, TimeUnit.SECONDS);

        assertEquals("OK FALLBACK", result);
    }

    @Test
    void failSoft_allFailures_allReplacedWithFallback() throws Exception {

        Microservice s1 = new StubMicroservice("S1",
                CompletableFuture.failedFuture(new RuntimeException()));
        Microservice s2 = new StubMicroservice("S2",
                CompletableFuture.failedFuture(new RuntimeException()));

        AsyncProcessor processor = new AsyncProcessor();

        String result =
                processor.processAsyncFailSoft(
                                List.of(s1, s2),
                                List.of("a", "b"),
                                "F")
                        .get(1, TimeUnit.SECONDS);

        assertEquals("F F", result);
    }

    // =========================================================
    // LIVENESS TEST — ensures no deadlock / infinite wait
    // =========================================================

    @RepeatedTest(3)
    void liveness_allPolicies_completeWithinTimeout() {

        Microservice slow = new StubMicroservice("SLOW",
                CompletableFuture.supplyAsync(() -> {
                    try { Thread.sleep(50); }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "OK";
                }));

        Microservice fail = new StubMicroservice("FAIL",
                CompletableFuture.failedFuture(new RuntimeException()));

        AsyncProcessor processor = new AsyncProcessor();

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {

            assertThrows(ExecutionException.class,
                    () -> processor.processAsyncFailFast(
                                    List.of(slow, fail),
                                    List.of("a", "b"))
                            .get(1, TimeUnit.SECONDS));

            List<String> partial =
                    processor.processAsyncFailPartial(
                                    List.of(slow, fail),
                                    List.of("a", "b"))
                            .get(1, TimeUnit.SECONDS);

            assertEquals(List.of("OK"), partial);

            String soft =
                    processor.processAsyncFailSoft(
                                    List.of(slow, fail),
                                    List.of("a", "b"),
                                    "F")
                            .get(1, TimeUnit.SECONDS);

            assertEquals("OK F", soft);
        });
    }
}

//End