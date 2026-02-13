package coen448.computablefuture.test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AsyncProcessor {

    /**
     * FAIL-FAST (Atomic Policy)
     * If any microservice fails, the whole operation fails (future completes exceptionally).
     * Results are concatenated in the same order as the services list.
     */
    public CompletableFuture<String> processAsyncFailFast(
            List<Microservice> services,
            List<String> messages) {

        if (services.size() != messages.size()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Services and messages must match"));
        }

        List<CompletableFuture<String>> futures =
                IntStream.range(0, services.size())
                        .mapToObj(i -> services.get(i).retrieveAsync(messages.get(i)))
                        .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.joining(" ")));
    }

    /**
     * FAIL-PARTIAL (Best-effort Policy)
     * Failures are skipped; only successful results are returned.
     * The returned future never fails.
     * Successful results preserve original service order.
     */
    public CompletableFuture<List<String>> processAsyncFailPartial(
            List<Microservice> services,
            List<String> messages) {

        if (services.size() != messages.size()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<CompletableFuture<String>> futures =
                IntStream.range(0, services.size())
                        .mapToObj(i -> services.get(i)
                                .retrieveAsync(messages.get(i))
                                .handle((value, ex) -> ex == null ? value : null))
                        .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(r -> r != null)
                        .collect(Collectors.toList()));
    }

    /**
     * FAIL-SOFT (Fallback Policy)
     * Failures are replaced with a fallback value.
     * The returned future never fails.
     * Results are concatenated in the same order as the services list.
     */
    public CompletableFuture<String> processAsyncFailSoft(
            List<Microservice> services,
            List<String> messages,
            String fallback) {

        if (services.size() != messages.size()) {
            return CompletableFuture.completedFuture("");
        }

        List<CompletableFuture<String>> futures =
                IntStream.range(0, services.size())
                        .mapToObj(i -> services.get(i)
                                .retrieveAsync(messages.get(i))
                                .exceptionally(ex -> fallback))
                        .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.joining(" ")));
    }
}
