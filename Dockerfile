# DAGACS backend - Render deployment image (Spring Boot fat JAR).
#
# Stage 1: build the runnable fat JAR with Maven + Java 17.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

# Stage 2: lean Java 17 runtime.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/dagacs-backend-1.0.0.jar app.jar
RUN groupadd -r dagacs && useradd -r -g dagacs -d /app dagacs \
    && chown dagacs:dagacs app.jar
USER dagacs

# Informational only for Render routing; the app binds to Render's PORT below.
EXPOSE 8080

# Render injects PORT into the container environment. Exec-form commands do
# not expand environment variables, so a shell-based CMD is required here to
# actually expand "$PORT" into Spring Boot's --server.port.
CMD ["/bin/sh", "-c", "exec java -jar /app/app.jar --server.port=\"$PORT\""]