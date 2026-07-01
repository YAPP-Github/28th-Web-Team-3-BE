FROM eclipse-temurin:25-jre

WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app

COPY build/docker/app.jar app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
