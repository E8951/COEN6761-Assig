# Failure Semantics for Concurrent Microservice Execution

This document defines the failure-handling semantics used when multiple microservices execute concurrently using `CompletableFuture`.

## Assumptions
- Microservices are invoked concurrently.
- Each service receives its corresponding message: `services[i]` uses `messages[i]`.
- Unless stated otherwise, results are combined in the original order of the `services` list (not completion order).
- Tests enforce liveness using timeouts to ensure the system does not hang indefinitely.

---

## Policy A: Fail-Fast (Atomic)

### Intent
Return a complete result only if **all** microservices succeed; otherwise fail the whole operation.

### Semantics
- If **any** microservice invocation fails (completes exceptionally), the returned `CompletableFuture` completes exceptionally.
- No partial result is returned to the caller.
- Other microservices may still be running in the background; this policy does not explicitly cancel them.

### Return Contract
- Return type: `CompletableFuture<String>`
- Completion: **Exceptional** if any service fails
- Success result: concatenation of all results in service-list order, separated by spaces.

### Example
Services: [S1, S2, S3]  
Results:  ["A", exception, "C"]  
Outcome: returned future completes exceptionally.

---

## Policy B: Fail-Partial (Best-Effort)

### Intent
Return as many successful results as possible even if some microservices fail.

### Semantics
- Failed microservice invocations are ignored (their results are not included).
- The returned `CompletableFuture` **never completes exceptionally** due to a service failure.
- Other microservices still execute concurrently; failures do not affect successes.

### Return Contract
- Return type: `CompletableFuture<List<String>>`
- Completion: **Normal** (never exceptional due to service failures)
- Success result: list of successful results only, preserving original service-list order.

### Example
Services: [S1, S2, S3]  
Results:  ["A", exception, "C"]  
Outcome: ["A", "C"]

---

## Policy C: Fail-Soft (Fallback)

### Intent
Always return a complete result by replacing failures with a fallback value.

### Semantics
- If a microservice invocation fails, its result is replaced with a predefined fallback string.
- The returned `CompletableFuture` **never completes exceptionally** due to a service failure.
- Other microservices still execute concurrently; failures are localized to their own slot in the output.

### Return Contract
- Return type: `CompletableFuture<String>`
- Completion: **Normal** (never exceptional due to service failures)
- Success result: concatenation of results in service-list order; failures are replaced by the fallback value.

### Example
Services: [S1, S2, S3]  
Results:  ["A", exception, "C"], fallback = "F"  
Outcome: "A F C"

---

## Edge Cases
- If `services.size() != messages.size()`:
    - Fail-Fast: completes exceptionally with `IllegalArgumentException`
    - Fail-Partial: returns an empty list
    - Fail-Soft: returns an empty string
- If multiple services fail:
    - Fail-Fast: fails (exception)
    - Fail-Partial: returns only successes (possibly empty)
    - Fail-Soft: replaces each failure with fallback

---

## Test Coverage Mapping
These semantics are validated by unit tests in `AsyncProcessorTest.java`:
- Fail-Fast: verifies exception propagation when any service fails
- Fail-Partial: verifies failed services are excluded and the future still completes normally
- Fail-Soft: verifies failures are replaced by fallback and the future completes normally
- Liveness: verifies all policies complete within a bounded time using timeouts
