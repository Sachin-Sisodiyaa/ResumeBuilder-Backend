# ResumeBuilder-Backend

Spring Boot multi-module backend scaffold for the ResumeAI case study defined in `docs/Sprint-CaseStudy.pdf`.

## Services

- `auth-service`
- `resume-service`
- `section-service`
- `ai-service`
- `template-service`
- `export-service`
- `jobmatch-service`
- `notification-service`
- `resumeai-web`
- `gateway-service`
- `discovery-service`
- `admin-service`

## What is implemented

- Maven parent project with 9 service folders matching the PDF.
- One runnable Spring Boot app per service.
- MVC-style layered structure inside each service: `controller`, `service`, `repository`, `model`, and DTO packages.
- Lombok support is configured in the parent Maven build to reduce boilerplate for models and constructor injection.
- REST routes aligned to the case study under `/api/v1/...`.
- Spring Data JPA persistence backed by a separate MySQL database per service.
- Seed data for admin auth and resume templates.
- Mocked AI/job integrations plus local SQL-tracked export jobs so the platform behavior is demonstrable end to end.
- JUnit 5 + Mockito unit tests for the service layer in every module under `src/test/java`.

## Ports

- `auth-service` -> `8081`
- `resume-service` -> `8082`
- `section-service` -> `8083`
- `ai-service` -> `8084`
- `template-service` -> `8085`
- `export-service` -> `8086`
- `jobmatch-service` -> `8087`
- `notification-service` -> `8088`
- `resumeai-web` -> `8091`
- `gateway-service` -> `8090`
- `discovery-service` -> `8761`
- `admin-service` -> `9090`

## Run

Create the service databases once. The datasource defaults use the MySQL credential already present in the codebase: `root` / `18052004`.

```bash
mysql -u root -p < docs/schema-init.sql
```

Each service also includes `createDatabaseIfNotExist=true`, so a MySQL user with create-database permissions can bootstrap the schemas on startup.

Build everything:

```bash
mvn clean package
```

Swagger/OpenAPI UI is available on each running service at:

```text
http://localhost:<service-port>/swagger-ui.html
```

Run a single service:

```bash
mvn -pl auth-service spring-boot:run
```

Run another example:

```bash
mvn -pl resume-service spring-boot:run
```

Run the gateway used by the frontend:

```bash
mvn -pl gateway-service spring-boot:run
```

For end-to-end frontend use, start discovery first, then admin, then the app services and gateway:

```bash
mvn -pl discovery-service spring-boot:run
mvn -pl admin-service spring-boot:run
mvn -pl auth-service,resume-service,section-service,ai-service,template-service,export-service,jobmatch-service,notification-service,payment-service,resumeai-web,gateway-service spring-boot:run
```

When stopping the stack, use the reverse order: stop the app services and gateway first, then admin, then discovery. If discovery is stopped first, the remaining services may log temporary Eureka heartbeat or de-registration connection errors because `localhost:8761` is already gone; those shutdown-time errors are harmless.

The Angular app should call the gateway on `http://localhost:8090`. The gateway now uses Eureka-backed `lb://...` routes to proxy traffic to the underlying services plus `resumeai-web`.

Eureka dashboard:

```text
http://localhost:8761
```

Spring Boot Admin dashboard:

```text
http://localhost:9090
```

## SMTP Setup For Real Password Reset Emails

The auth service already includes Spring Mail. To send real forgot-password emails, set these environment variables before starting `auth-service`:

```powershell
$env:MAIL_HOST="smtp.gmail.com"
$env:MAIL_PORT="587"
$env:MAIL_PROTOCOL="smtp"
$env:MAIL_USERNAME="your-email@gmail.com"
$env:MAIL_PASSWORD="your-app-password"
$env:MAIL_FROM="your-email@gmail.com"
$env:RESET_PASSWORD_BASE_URL="http://localhost:3000/reset-password"
```

Then run:

```bash
mvn -pl auth-service spring-boot:run
```

Notes:

- For Gmail, use a Google App Password, not your normal account password.
- `RESET_PASSWORD_BASE_URL` should point to your frontend reset-password page in the environment you are running.
- If `MAIL_USERNAME` and `MAIL_PASSWORD` are not valid for the SMTP server, forgot-password requests will fail to deliver real email.

## Data And Export Storage

- `auth-service` -> `resumeai_auth`
- `resume-service` -> `resumeai_resume`
- `section-service` -> `resumeai_section`
- `ai-service` -> `resumeai_ai`
- `template-service` -> `resumeai_template`
- `export-service` -> `resumeai_export`
- `jobmatch-service` -> `resumeai_jobmatch`
- `notification-service` -> `resumeai_notification`

Export metadata and generated PDF/DOCX/JSON bytes are stored in MySQL through `ExportJob`. The exporter may briefly write a generated file under `export-service/exports`, then persists it into the `resumeai_export.export_jobs.file_content` BLOB column and serves downloads from the export API. This intentionally replaces the AWS S3 storage from the case-study requirement for this local implementation.

## Redis, RabbitMQ, And SonarQube

Redis is used opportunistically by `auth-service` for password reset tokens and by `ai-service` for monthly quota counters. If Redis is not running, both services fall back to the existing database/in-memory behavior and keep starting normally.

RabbitMQ export queueing is available behind a flag. Async export processing is enabled by default; without RabbitMQ it uses the local worker, and with RabbitMQ it publishes jobs to the broker for the listener to consume.

Start Redis and RabbitMQ locally:

```powershell
docker compose -f docker-compose.infra.yml up -d
```

RabbitMQ management UI is available at `http://localhost:15672` with `guest` / `guest`.

Enable broker-backed export processing and Redis health checks in `.env` or your shell:

```powershell
$env:EXPORT_ASYNC_ENABLED="true"
$env:EXPORT_RABBITMQ_ENABLED="true"
$env:RABBITMQ_HOST="localhost"
$env:RABBITMQ_PORT="5672"
$env:RABBITMQ_USERNAME="guest"
$env:RABBITMQ_PASSWORD="guest"
$env:RABBIT_HEALTH_ENABLED="true"
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
$env:REDIS_HEALTH_ENABLED="true"
mvn -pl export-service spring-boot:run
```

Run SonarQube analysis from the backend root after starting a local SonarQube server:

```bash
mvn verify sonar:sonar
```

## Notes

- This is an MVP scaffold based directly on the PDF architecture, not a production-complete system yet.
- Security, JWT signing, OAuth, Flyway, and external provider SDKs are represented as service-level placeholders and mock behavior for now.
- The code is structured so those integrations can be swapped in next without changing the service boundaries.
