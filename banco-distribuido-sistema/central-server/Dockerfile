FROM openjdk:25-jdk-slim

WORKDIR /app

COPY target/central-server-1.0-SNAPSHOT.jar app.jar

EXPOSE 8000

CMD ["java", "-jar", "app.jar"]