# Render Docker Deployment

This backend is a Maven multi-module project. Each microservice depends on the
root `pom.xml`, so Docker builds must use `ResumeBuilder-Backend` as the build
context.

Do not set the Render root directory to an individual service folder such as
`ai-service`. That context does not contain the parent Maven project.

Use these settings for every backend microservice:

```text
Root Directory: ResumeBuilder-Backend
Dockerfile Path: <service-name>/Dockerfile
Docker Build Context Directory: .
```

Examples:

```text
Root Directory: ResumeBuilder-Backend
Dockerfile Path: ai-service/Dockerfile
Docker Build Context Directory: .
```

```text
Root Directory: ResumeBuilder-Backend
Dockerfile Path: admin-service/Dockerfile
Docker Build Context Directory: .
```

Each service Dockerfile builds its own jar inside Docker. Do not use
`COPY target/*.jar` Dockerfiles for Render GitHub deploys unless you commit
`target/` artifacts, which is not recommended.
