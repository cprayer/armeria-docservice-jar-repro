# Armeria closes a JarFile shared with Spring Boot's class loader

This Spring Boot executable jar contains Armeria and `armeria-grpc`. It gets the
cached `JarFile` for a gRPC descriptor, runs one `DocStringExtractor` scan, and
checks whether the same `JarFile` is still open.
The build packages Armeria under `BOOT-INF/lib`, and `java -jar` starts Spring
Boot's `JarLauncher`. `Main` prints the actual class loader and resource URL so
the nested-jar execution environment is visible. The released-Armeria build uses
`spring-boot-maven-plugin:3.5.16` with the `repackage` goal. The patched build uses
`spring-boot-loader:3.5.16` and the same executable-jar layout.

## Run

Docker is the only prerequisite. Run these commands from the repository root:

```sh
docker build -t armeria-jarfile-repro:local .
docker run --rm armeria-jarfile-repro:local
```

The output identifies Spring Boot's nested-jar loader and the closed shared jar:

```text
classLoader=org.springframework.boot.loader.launch.LaunchedClassLoader
resource=jar:nested:/app/repro.jar/!BOOT-INF/lib/armeria-grpc-1.42.0.jar!/META-INF/armeria/grpc/armeria-main.dsc
RESULT sharedJarFile=closed (Zip file closed)
```

## Before and after

| Armeria code | Result |
| --- | --- |
| 1.42.0 | `RESULT sharedJarFile=closed (Zip file closed)` |
| Reflections 0.9.12 upgrade | `RESULT sharedJarFile=open` |

### Build and run the patched Armeria

The patched build fetches the Armeria repository over HTTPS and builds the pinned
[Reflections 0.9.12 upgrade commit](https://github.com/cprayer/armeria/commit/7fa42d7b20cf89348c150b4c43825f331d6b3cc2)
inside Docker. The source repository must be public for this command to work.
The first build compiles Armeria and takes several minutes.

```sh
docker build -f Dockerfile.patched -t armeria-jarfile-repro:patched .
docker run --rm armeria-jarfile-repro:patched
```

Expected output: `RESULT sharedJarFile=open`.

`Dockerfile.patched` runs Armeria's Gradle `shadedJar` tasks for core, gRPC,
gRPC protocol and protobuf, and packages those jars with their runtime dependencies
and the Spring Boot loader. Core uses its untrimmed shaded jar to avoid building
every Armeria module for ProGuard trimming. All four Armeria jars come from the
same commit; no released Armeria jars are substituted into this image.
`/app/armeria-commit.txt` records the checked-out commit.

To test another commit, pass `--build-arg ARMERIA_REF=<commit>`.
To use a different public repository, pass
`--build-arg ARMERIA_REPOSITORY=https://github.com/owner/armeria.git`.

This checks the shared `JarFile` closure directly. The resulting
`ClassNotFoundException` requires a timing race and is not needed for this check.

To try another Armeria version available in Maven, add
`--build-arg ARMERIA_VERSION=<version>` to the original build command.
Both builds default to Spring Boot 3.5.16; `SPRING_BOOT_VERSION` is a build argument.

The build uses a Maven container. The run uses a separate JRE image with a
non-root user. No host directories are mounted.

## Spring Boot compatibility of Reflections 0.9.12

The [compatibility repro](compatibility/README.md) checks the older
`JarFile.stream()` failure that blocked the dependency upgrade. It tests
2.3.4.RELEASE as the failing control, followed by 2.3.12.RELEASE, 2.7.18,
3.5.16 and 4.1.1.
Build and run the entire matrix inside Docker:

```sh
docker build -t reflections-boot-compatibility compatibility
docker run --rm reflections-boot-compatibility
```

All checks run on Java 25. The compatibility matrix requires no host shell script.
Unlike the top-level repro, this matrix uses Reflections directly without Armeria:
the top-level repro checks the Armeria fix, while `compatibility/` checks the
Spring Boot compatibility needed to upgrade Reflections.

In a separate local validation, the upgraded Armeria core, gRPC and xDS shaded jars
were checked in executable nested jars, using Docker Java 25.0.4.1 and fresh JVMs
for every check:

| Spring Boot loader | `JarFile.stream()` | `DocStringExtractor` | `XdsResourceReader` |
| --- | --- | --- | --- |
| 2.3.4.RELEASE | `zip file closed` | `zip file closed` | `zip file closed` |
| 2.3.12.RELEASE | pass | descriptor found, shared jar open | type registered, shared jar open |
| 2.7.18 | pass | descriptor found, shared jar open | type registered, shared jar open |
| 3.5.16 | pass | descriptor found, shared jar open | type registered, shared jar open |
| 4.1.1 | pass | descriptor found, shared jar open | type registered, shared jar open |

These checks exercise the executable-jar loader and scanning paths. They do not
start a Spring application context.
This table records that separate validation; neither the top-level Docker commands
nor `compatibility/` run this Armeria and xDS matrix. The top-level commands compare
DocStringExtractor ownership on Spring Boot 3.5.16.
