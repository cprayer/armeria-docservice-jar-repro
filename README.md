# Armeria closes a JarFile shared with Spring Boot's class loader

This Spring Boot executable jar contains Armeria and `armeria-grpc`. It gets the
cached `JarFile` for a gRPC descriptor, runs one `DocStringExtractor` scan, and
checks whether the same `JarFile` is still open.

## Run

Docker is the only prerequisite.

```sh
./run.sh
```

## Before and after

| Armeria code | Result |
| --- | --- |
| 1.42.0 | `RESULT sharedJarFile=closed (Zip file closed)` |
| Reflections 0.9.12 upgrade | `RESULT sharedJarFile=open` |

The after result is checked with Armeria artifacts built from the Reflections
0.9.12 upgrade. This changes the dependency and its internal API callers; it does
not add a custom URL handler or change Reflections' global Vfs configuration.

This checks the shared `JarFile` closure directly. The resulting
`ClassNotFoundException` requires a timing race and is not needed for this check.

To try another Armeria version available in Maven:

```sh
./run.sh <armeria-version>
```

The build uses a Maven container. The run uses a separate JRE image with a
non-root user, no network, a read-only filesystem, and no host mounts.

## Spring Boot compatibility of Reflections 0.9.12

The [compatibility repro](compatibility/README.md) checks the older
`JarFile.stream()` failure that blocked the dependency upgrade. It tests
2.3.4.RELEASE as the failing control, followed by 2.3.12.RELEASE, 2.7.18,
3.5.16 and 4.1.1.
Build and run the entire matrix inside Docker:

```sh
docker compose -f compatibility/compose.yaml up --build --abort-on-container-exit --exit-code-from compatibility
```

All checks use Java 25. The compatibility matrix requires no host shell script.

The upgraded Armeria core, gRPC and xDS shaded jars were also checked in executable
nested jars, using Docker Java 25.0.4.1 and fresh JVMs for every check:

| Spring Boot loader | `JarFile.stream()` | `DocStringExtractor` | `XdsResourceReader` |
| --- | --- | --- | --- |
| 2.3.4.RELEASE | `zip file closed` | `zip file closed` | `zip file closed` |
| 2.3.12.RELEASE | pass | descriptor found, shared jar open | type registered, shared jar open |
| 2.7.18 | pass | descriptor found, shared jar open | type registered, shared jar open |
| 3.5.16 | pass | descriptor found, shared jar open | type registered, shared jar open |
| 4.1.1 | pass | descriptor found, shared jar open | type registered, shared jar open |

These checks exercise the executable-jar loader and scanning paths. They do not
start a Spring application context.
