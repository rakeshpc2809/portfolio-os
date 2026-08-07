FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/core-node-3.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "--add-opens=java.base/java.nio=ALL-UNNAMED", "-jar", "app.jar"]
