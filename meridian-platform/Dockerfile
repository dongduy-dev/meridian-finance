FROM maven:3.9.16-eclipse-temurin-25-noble AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

COPY --from=build /workspace/target/meridian-platform-*.jar /app/meridian-platform.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/meridian-platform.jar"]
