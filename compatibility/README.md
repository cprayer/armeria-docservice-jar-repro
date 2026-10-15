# Reflections 0.9.12 and Spring Boot nested jars

Armeria pins Reflections to 0.9.11 because of a `JarFile.stream()` failure reported
with older Spring Boot loaders. Spring Boot fixed the missing
`JarFileWrapper.stream()` override in 2.3.6.RELEASE. This repro checks that
Reflections 0.9.12 works with later loaders, including real resource and subtype
scans.

## Run

Docker is the only prerequisite. All builds and runs use Java 25.

```sh
docker compose -f compatibility/compose.yaml up --build --abort-on-container-exit --exit-code-from compatibility
```

Run this from the repository root. The image builds five executable jars with
Spring Boot 2.3.4.RELEASE, 2.3.12.RELEASE, 2.7.18, 3.5.16 and 4.1.1. A Java program inside the
container runs the matrix and checks the results. No host shell script is used.

The runner checks each jar's `Spring-Boot-Version` manifest entry, then starts a fresh JVM for each check:

- `stream`: call `JarFile.stream()` on the Reflections jar nested in the executable jar.
- `scan`: scan that jar with `ResourcesScanner` and `SubTypesScanner`, assert that
  resources and classes were found, then assert that the cached `JarFile` is still open.

The 2.3.4.RELEASE runs must fail with `zip file closed`; they are the control.
Every other run must succeed. The container exits unsuccessfully on an unexpected result.

The runtime container has no network or host mounts, runs as a non-root user,
and has a read-only filesystem. Only a small `/tmp` tmpfs is writable.

2.3.12.RELEASE is the last public 2.3.x release in
[Maven Central](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-loader/maven-metadata.xml).
Including it shows both the affected and fixed loaders within the same 2.3 release line.

## Scope

This isolates Spring Boot loader compatibility. It uses Reflections 0.9.12 directly,
without Armeria or a Spring application context. Armeria's actual
`DocStringExtractor` shared-jar failure has a separate repro in the parent directory.

Sources: [Reflections report](https://github.com/ronmamo/reflections/issues/279),
[Spring Boot fix](https://github.com/spring-projects/spring-boot/issues/23821),
[Reflections caching fix](https://github.com/ronmamo/reflections/commit/e56cd651de47c50c8d18f213c7ea3aed9db2fbf0).

## Verified results

Docker Compose completed all ten checks on Java 25:

| Spring Boot | `JarFile.stream()` | Reflections resource and subtype scans |
| --- | --- | --- |
| 2.3.4.RELEASE | `zip file closed` (expected control) | `zip file closed` (expected control) |
| 2.3.12.RELEASE | pass | pass, shared jar open |
| 2.7.18 | pass | pass, shared jar open |
| 3.5.16 | pass | pass, shared jar open |
| 4.1.1 | pass | pass, shared jar open |
