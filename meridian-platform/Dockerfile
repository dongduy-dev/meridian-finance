# syntax=docker/dockerfile:1

FROM maven:3.9.16-eclipse-temurin-25-noble AS build

WORKDIR /workspace

COPY deployment/JavaTrust.java /opt/meridian-trust/JavaTrust.java
# Mitigate before Maven retrieves artifacts; verify the effective default Java trust store.
RUN javac -d /opt/meridian-trust /opt/meridian-trust/JavaTrust.java \
    && java -cp /opt/meridian-trust JavaTrust --apply \
    && java -cp /opt/meridian-trust JavaTrust --verify

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -Dmaven.artifact.threads=1 -DskipTests package

FROM eclipse-temurin:25-jre-jammy

ARG SOURCE_REVISION=development
LABEL org.opencontainers.image.revision=$SOURCE_REVISION
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 meridian && useradd --uid 10001 --gid 10001 --no-create-home meridian \
    && mkdir -p /var/lib/meridian/documents/staging /var/lib/meridian/documents/objects \
    && chown -R 10001:10001 /var/lib/meridian/documents \
    && chmod 700 /var/lib/meridian/documents /var/lib/meridian/documents/staging /var/lib/meridian/documents/objects
WORKDIR /app

COPY --from=build /opt/meridian-trust/JavaTrust.class /opt/meridian-trust/JavaTrust.class
# Run after package installation, which can regenerate certificate stores.
RUN java -cp /opt/meridian-trust JavaTrust --apply

COPY --from=build /workspace/target/meridian-platform-*.jar /app/meridian-platform.jar

EXPOSE 8080

USER 10001:10001
RUN java -cp /opt/meridian-trust JavaTrust --verify
ENTRYPOINT ["java", "-jar", "/app/meridian-platform.jar"]
