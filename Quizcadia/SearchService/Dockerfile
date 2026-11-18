# Build the jar first: mvn -pl <module> -am package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/app.jar app.jar
USER 1000
# Java Flight Recorder always on with its low-overhead settings (about 1% CPU), keeping the last 30 minutes (at
# most 25 MB, in /tmp): when something was slow, deploy/profile.sh fetches what happened. See deploy/README.md.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-XX:+ExitOnOutOfMemoryError", \
    "-XX:StartFlightRecording=name=continuous,settings=default,maxage=30m,maxsize=25m", "-jar", "app.jar"]
