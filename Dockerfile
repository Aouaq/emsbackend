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

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
