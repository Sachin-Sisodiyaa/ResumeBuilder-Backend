FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
ARG MODULE=ai-service


COPY ai-service/pom.xml ai-service/pom.xml

RUN mvn -q -pl ${MODULE} -am dependency:go-offline

COPY . .
RUN mvn -q -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
ARG MODULE=ai-service
COPY --from=build /workspace/${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
