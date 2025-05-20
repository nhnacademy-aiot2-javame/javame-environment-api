FROM openjdk:21-jdk-slim

LABEL authors="nhnacademy"

WORKDIR /app

COPY target/*.jar javame-environment-api.jar

EXPOSE 10273

CMD ["java", "-jar", "javame-environment-api.jar"]
