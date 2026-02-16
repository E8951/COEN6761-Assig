# COEN6761-Assig

This project demonstrates concurrent microservice orchestration using `CompletableFuture` with three failure policies implemented in `AsyncProcessor`:

- **Fail-Fast**: fail the overall operation when any service fails.
- **Fail-Partial**: keep successful results and discard failed ones.
- **Fail-Soft**: replace failed service outputs with a fallback value.

## Tests

Unit tests for these semantics are in:

- `src/test/java/coen448/computablefuture/test/AsyncProcessorTest.java`

They validate:

- success and failure behavior for each policy,
- ordering guarantees,
- mismatched-input edge-case contracts,
- completion within bounded time.

Detailed policy semantics are documented in:

- `docs/failure-semantics.md`
