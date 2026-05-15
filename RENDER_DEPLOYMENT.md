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

Each service Dockerfile expects its jar to exist under
`<service-name>/target/*.jar` before Docker builds. Run Maven packaging before
building an image:

```bash
mvn clean package -DskipTests
```

For GitHub-based Render Docker deploys, this style only works if the packaged
jar files are present in the build context.
