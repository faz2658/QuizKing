# Build the jar first: mvn -pl <module> -am package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/app.jar app.jar
USER 1000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
