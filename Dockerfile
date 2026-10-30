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
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
