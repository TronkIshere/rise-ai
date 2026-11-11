FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 rise && mkdir /shared && chown rise /shared
WORKDIR /app
COPY --from=build /workspace/target/rise-ai-avalanche.jar app.jar
USER rise
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
