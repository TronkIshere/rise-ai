FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --create-home rise && mkdir -p /data && chown rise /data
WORKDIR /data
COPY --from=build /workspace/target/rise-ai-monitoring.jar /app/app.jar
USER rise
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
