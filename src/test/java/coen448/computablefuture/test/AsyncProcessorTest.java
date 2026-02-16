package coen448.computablefuture.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.junit.jupiter.api.Test;

class AsyncProcessorTest {

    private final AsyncProcessor processor = new AsyncProcessor();

    @Test
    void processAsyncFailFast_returnsOrderedConcatenation_whenAllSucceed() {
        List<Microservice> services = List.of(
                new StubMicroservice("S1", false),
                new StubMicroservice("S2", false),
                new StubMicroservice("S3", false));

        String result = processor.processAsyncFailFast(services, List.of("a", "b", "c")).join();

        assertEquals("S1:A S2:B S3:C", result);
    }

    @Test
    void processAsyncFailFast_completesExceptionally_whenAnyServiceFails() {
        List<Microservice> services = List.of(
                new StubMicroservice("S1", false),
                new StubMicroservice("S2", true),
                new StubMicroservice("S3", false));

        CompletionException ex = assertThrows(CompletionException.class,
                () -> processor.processAsyncFailFast(services, List.of("a", "b", "c")).join());

        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("S2 failed"));
    }

    @Test
    void processAsyncFailPartial_returnsOnlySuccessfulValues_inServiceOrder() {
        List<Microservice> services = List.of(
                new StubMicroservice("S1", false),
                new StubMicroservice("S2", true),
                new StubMicroservice("S3", false));

        List<String> result = processor.processAsyncFailPartial(services, List.of("a", "b", "c")).join();

        assertEquals(List.of("S1:A", "S3:C"), result);
    }

    @Test
    void processAsyncFailSoft_replacesFailuresWithFallback() {
        List<Microservice> services = List.of(
                new StubMicroservice("S1", false),
                new StubMicroservice("S2", true),
                new StubMicroservice("S3", false));

        String result = processor.processAsyncFailSoft(services, List.of("a", "b", "c"), "FALLBACK").join();

        assertEquals("S1:A FALLBACK S3:C", result);
    }

    @Test
    void processAsyncPolicies_handleMismatchedInputSizes_perContract() {
        List<Microservice> services = List.of(new StubMicroservice("S1", false));

        CompletionException failFast = assertThrows(CompletionException.class,
                () -> processor.processAsyncFailFast(services, List.of("a", "b")).join());
        assertInstanceOf(IllegalArgumentException.class, failFast.getCause());

        List<String> failPartial = processor.processAsyncFailPartial(services, List.of("a", "b")).join();
        assertEquals(List.of(), failPartial);

        String failSoft = processor.processAsyncFailSoft(services, List.of("a", "b"), "FALLBACK").join();
        assertEquals("", failSoft);
    }

    @Test
    void allPolicies_completeWithinReasonableTime() {
        List<Microservice> services = List.of(
                new StubMicroservice("S1", false),
                new StubMicroservice("S2", false),
                new StubMicroservice("S3", false));
        List<String> messages = List.of("a", "b", "c");

        assertTrue(assertTimeout(Duration.ofSeconds(1),
                () -> processor.processAsyncFailFast(services, messages).join())
                .contains("S1:A"));

        assertEquals(3, assertTimeout(Duration.ofSeconds(1),
                () -> processor.processAsyncFailPartial(services, messages).join()).size());

        assertTrue(assertTimeout(Duration.ofSeconds(1),
                () -> processor.processAsyncFailSoft(services, messages, "FALLBACK").join())
                .contains("S3:C"));
    }

    private static class StubMicroservice extends Microservice {

        private final String serviceId;
        private final boolean shouldFail;

        StubMicroservice(String serviceId, boolean shouldFail) {
            super(serviceId);
            this.serviceId = serviceId;
            this.shouldFail = shouldFail;
        }

        @Override
        public CompletableFuture<String> retrieveAsync(String input) {
            if (shouldFail) {
                return CompletableFuture.failedFuture(new IllegalStateException(serviceId + " failed"));
            }
            return CompletableFuture.completedFuture(serviceId + ":" + input.toUpperCase());
        }
    }
}
