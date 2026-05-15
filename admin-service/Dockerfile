FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG MODULE=admin-service
COPY ${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
