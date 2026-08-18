# DB-01 — Flyway cutover (deferred)

Phase 1 of routing activation keeps the existing repo pattern: **Hibernate `ddl-auto=update`**. Mixing Flyway mid-flight was deferred here.

SQL under this folder is the draft for a future Flyway baseline (`spring.flyway.enabled=true` + `ddl-auto=validate`). Do not enable Flyway until this task owns schema migrations end-to-end.
