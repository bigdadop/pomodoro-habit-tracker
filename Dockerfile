# =============================================================================
#  Pomodoro Habit Tracker - Docker image (multi-stage build)
#
#  Stage 1 compiles the app with Maven + Java 21 and packs it into one JAR.
#  Stage 2 only contains a small Java 21 runtime and that JAR, so the final
#  image does not include Maven, the source code or the build cache.
#
#  Normally you start it with:  docker compose up --build
# =============================================================================

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy only pom.xml first and download the dependencies. Docker caches this
# step, so later builds are fast as long as pom.xml does not change.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Now copy the source code and build the executable JAR.
# The tests are run separately with "mvn clean verify" (see README.md).
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- Stage 2: run ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a normal user instead of root, and prepare the data folder for it.
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/data && chown app:app /app/data

COPY --from=build /build/target/pomodoro-habit-tracker.jar /app/app.jar

# Inside a container the app must listen on 0.0.0.0 (all addresses), otherwise
# Docker's port forwarding cannot reach it. compose.yaml still only publishes
# the port on 127.0.0.1 of your computer.
ENV APP_HOST=0.0.0.0 \
    APP_PORT=8080 \
    APP_DATA_DIR=/app/data

USER app
EXPOSE 8080

# Docker checks every 30 seconds that the app still answers.
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
