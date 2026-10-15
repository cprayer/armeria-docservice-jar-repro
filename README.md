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
| Proposed fix | `RESULT sharedJarFile=open` |

The after result was verified with the same Spring Boot executable jar, replacing
only the `DocStringExtractor` and `ReflectionsUtil` class files in its nested
Armeria jar with those built from the proposed change. The scan then leaves the
`JarFile` shared with Spring Boot's class loader open.

This checks the shared `JarFile` closure directly. The resulting
`ClassNotFoundException` requires a timing race and is not needed for this check.

To try another Armeria version available in Maven:

```sh
./run.sh <armeria-version>
```

The build uses a Maven container. The run uses a separate JRE image with a
non-root user, no network, a read-only filesystem, and no host mounts.
