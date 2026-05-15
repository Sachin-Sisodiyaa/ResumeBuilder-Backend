FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG MODULE=ai-service
COPY ${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
