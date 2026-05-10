FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG MODULE
# This expects you to run 'mvn clean package -DskipTests' first
COPY ${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
