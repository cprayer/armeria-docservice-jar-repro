FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
ARG ARMERIA_VERSION=1.42.0
ARG SPRING_BOOT_VERSION=3.5.16
COPY pom.xml .
COPY src ./src
RUN mvn -q -B -DskipTests -Darmeria.version="$ARMERIA_VERSION" \
    -Dspring-boot.version="$SPRING_BOOT_VERSION" package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/repro.jar /app/repro.jar
USER 65532:65532
ENTRYPOINT ["java", "-jar", "/app/repro.jar"]
