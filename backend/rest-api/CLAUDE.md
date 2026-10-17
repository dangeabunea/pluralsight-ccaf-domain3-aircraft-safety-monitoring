## Module notes (rest-api)

- Read/review API over MongoDB, served on port 38090. It only sees closed events written by the detection service;
  it never talks to Kafka and must not write infringement data itself, only review state (status, comments).
- Ports and adapters: `application/` defines `InfringementEventPort`, `infrastructure/` implements it
  (`InfringementEventQueryAdapter` over `InfringementEventRepository`). Controllers and services depend on the port only.
- Controllers translate exceptions through `EventControllerExceptionHandler`. Throw the existing typed exceptions
  (`EventNotFoundException`, `InvalidSortFieldException`, `InvalidStatusException`, `CommentValidationException`,
  and domain `ReviewConflictException` / `ReviewValidationException`) rather than building error responses in the controller.
- Sort fields are validated against an allow-list (`InvalidSortFieldException`); new sortable fields on list endpoints must be added there.
- Review rules (status transitions, comment validation) belong in `domain/` (`InfringementReview`, `ReviewStatus`), not in the service or controller.
- Request/response DTOs are records in `application/`. Keep `*Document` classes out of API responses; map to `*Data`/`*Response`.
- Spring Boot 4 property namespace is `spring.mongodb.*` (not `spring.data.mongodb.*`); override via `SPRING_MONGODB_URI`.
