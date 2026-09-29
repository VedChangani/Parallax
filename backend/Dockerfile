# syntax=docker/dockerfile:1
#
# Backend image. Build context is the REPOSITORY ROOT (see compose.yaml): the
# backend depends on the sibling `engine` module, so both must be in context.

# --- build: compile engine + backend from the root reactor -------------------
# Plain `mvn` rather than ./mvnw: the wrapper is a shell script whose line
# endings can be rewritten to CRLF by a Windows checkout, which breaks it in
# a Linux container. Tests are skipped here; `./mvnw verify` is the test gate.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY engine engine
COPY backend backend
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl backend -am package -Dmaven.test.skip=true \
    && cp backend/target/backend-*.jar /build/app.jar

# --- runtime: JRE only, non-root ---------------------------------------------
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin parallax
WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
USER parallax
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
