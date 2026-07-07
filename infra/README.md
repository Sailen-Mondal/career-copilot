# Infrastructure

`docker-compose.yml` starts the local Postgres and Redis dependencies.

The real production version should add:

- Encrypted secrets or cloud KMS
- Scheduled Postgres backups
- Structured log shipping
- Alerting for circuit-breaker trips and automation failures
- Dependency scanning in CI
