# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

# Copy Gradle wrapper and build files first for better layer caching
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle.kts ./
COPY settings.gradle* ./

RUN chmod +x gradlew

# Warm the Gradle cache
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies || true

# Copy sources
COPY src/ src/

# Build shaded JAR
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon shadowJar

FROM eclipse-temurin:25-jre

WORKDIR /app

RUN mkdir -p logs plugins data .config

COPY --from=build /app/build/libs/koneko-web-shaded.jar ./koneko-web.jar

ENV JAVA_OPTS=""
ENV JAVA_XMS=512m
ENV JAVA_XMX=2g

ENTRYPOINT ["sh", "-c", "exec java -Xms${JAVA_XMS} -Xmx${JAVA_XMX} ${JAVA_OPTS} -jar koneko-web.jar"]