FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 set -eu; \
    mkdir -p /app/repros; \
    for version in 2.3.4.RELEASE 2.7.18 3.5.16 4.1.1; do \
        mvn -B -ntp -DskipTests -Dspring-boot.version="$version" clean package; \
        cp target/repro.jar "/app/repros/$version.jar"; \
    done
COPY CompatibilityMatrix.java .
RUN javac -d /app/runner CompatibilityMatrix.java

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/repros /app/repros
COPY --from=build /app/runner /app/runner
USER 65532:65532
ENTRYPOINT ["java", "-cp", "/app/runner", "CompatibilityMatrix"]
