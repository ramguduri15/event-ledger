FROM openjdk:21-slim AS builder
WORKDIR /build
COPY target/event-gateway-*.jar app.jar

FROM openjdk:21-slim
WORKDIR /app
COPY --from=builder /build/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
