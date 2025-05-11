FROM openjdk:25-jdk-slim

WORKDIR /app

COPY target/worker-node-1.0-SNAPSHOT.jar app.jar

EXPOSE 8081

CMD ["java", "-jar", "app.jar"]