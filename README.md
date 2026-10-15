# Armeria closes a JarFile shared with Spring Boot's class loader

This Spring Boot executable jar contains Armeria and `armeria-grpc`. It gets the
cached `JarFile` for a gRPC descriptor, runs one `DocStringExtractor` scan, and
checks whether the same `JarFile` is still open.

## Run

Docker is the only prerequisite.

```sh
./run.sh
```

With Armeria 1.42.0, the result is:

```text
RESULT sharedJarFile=closed (Zip file closed)
```

The scan closes the cached `JarFile` even though Spring Boot's class loader also
uses it. The proposed fix makes the scan use an uncached connection, so the
result becomes `sharedJarFile=open`.

This checks the shared `JarFile` closure directly. The resulting
`ClassNotFoundException` requires a timing race and is not needed for this check.

To try another Armeria version available in Maven:

```sh
./run.sh <armeria-version>
```

The build uses a Maven container. The run uses a separate JRE image with a
non-root user, no network, a read-only filesystem, and no host mounts.
