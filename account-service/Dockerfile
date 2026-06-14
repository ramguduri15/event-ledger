FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build
COPY target/account-service-*.jar app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /build/app.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
