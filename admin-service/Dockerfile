FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
ARG MODULE=admin-service

COPY pom.xml ./
COPY admin-service/pom.xml admin-service/pom.xml
COPY ai-service/pom.xml ai-service/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY discovery-service/pom.xml discovery-service/pom.xml
COPY export-service/pom.xml export-service/pom.xml
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY jobmatch-service/pom.xml jobmatch-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY resume-service/pom.xml resume-service/pom.xml
COPY resumeai-web/pom.xml resumeai-web/pom.xml
COPY section-service/pom.xml section-service/pom.xml
COPY template-service/pom.xml template-service/pom.xml
RUN mvn -q -pl ${MODULE} -am dependency:go-offline

COPY . .
RUN mvn -q -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG MODULE=admin-service
RUN apk add --no-cache wget
COPY --from=build /workspace/${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
