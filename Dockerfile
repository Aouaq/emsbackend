# Both stages float on the 17 tag, which moves. For a deployment you can roll back
# to, have CI resolve it once and build from the digest - `eclipse-temurin:17-jre@sha256:...`
# - so an image rebuilt from the same commit is byte-identical. Currently 17.0.21+9.
FROM eclipse-temurin:17-jdk AS build

WORKDIR /workspace
COPY gradle gradle
COPY gradlew gradlew
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon \
    && find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

FROM eclipse-temurin:17-jre

RUN groupadd --system electricwave \
    && useradd --system --gid electricwave --home-dir /app electricwave

WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar

USER electricwave
EXPOSE 8082

# The base configuration sets spring.profiles.default=dev, whose profile ships
# working development secrets (JWT signing key, crypto master key, bootstrap
# admin password). Pin the profile here so an image started without an explicit
# SPRING_PROFILES_ACTIVE cannot silently boot with them; SecurityStartupValidator
# rejects those values outside the dev profile as a second line of defence.
ENV SPRING_PROFILES_ACTIVE=prod

# Sized against the *container's* limit rather than the host's. Without a limit the
# JVM reads the host's RAM and takes a quarter of it, which on a 2 GB VPS already
# running Postgres and Redis is how all three end up fighting over the same page
# cache. With `mem_limit` set in compose this follows it automatically, so one number
# governs the box. Overridable: JAVA_TOOL_OPTIONS given at run time replaces this.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"

# Readiness, deliberately not the aggregate /actuator/health - that one folds in the
# SMTP probe, so a mail outage would report a working application as unhealthy and an
# orchestrator would restart it in a loop.
#
# Stated here and not only in compose, because the thing most likely to run this image
# is a plain `docker run` on a VPS, and there a healthcheck declared in a compose file
# this host never reads does nothing at all.
#
# start-period covers a cold boot: Flyway, Hibernate and the security wiring take
# roughly 35 seconds on a small VPS, and failures during that window are not counted.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 CMD curl -fsS http://localhost:8082/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
