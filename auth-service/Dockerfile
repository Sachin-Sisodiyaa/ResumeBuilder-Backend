FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG MODULE=auth-service
COPY ${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
