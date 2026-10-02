# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY engine engine
COPY backend backend
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl backend -am package -Dmaven.test.skip=true \
    && cp backend/target/backend-*.jar /build/app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin parallax
WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
USER parallax
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
