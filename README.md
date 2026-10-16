# Armeria shared JarFile reproduction

Checks whether Armeria's `DocStringExtractor` closes a cached `JarFile` shared
with Spring Boot's nested-jar class loader, and verifies the Reflections upgrade fixes it.

## Run

Docker is the only prerequisite. Run these commands from the repository root.
Both cases use Java 25 and Spring Boot 3.5.16. The released-Armeria build uses
`spring-boot-maven-plugin:3.5.16` with `repackage`; the patched build uses
`spring-boot-loader:3.5.16` with the same executable-jar layout.

### Before: Armeria 1.42.0

```sh
docker build -t armeria-jarfile-repro:local .
docker run --rm armeria-jarfile-repro:local
```

### After: Reflections 0.9.12 upgrade

This builds Armeria from the pinned [upgrade commit](https://github.com/cprayer/armeria/commit/030f4b313cc3a62d0d8e0f7b19f67d752fe17c4d)
inside Docker. The first build takes several minutes.

```sh
docker build -f Dockerfile.patched -t armeria-jarfile-repro:patched .
docker run --rm armeria-jarfile-repro:patched
```

### Expected results

| Output | Armeria 1.42.0 | Upgrade commit |
| --- | --- | --- |
| `RESULT sharedJarFile=` | `closed (Zip file closed)` | `open` |
| `RESULT mode=none` | `failed=0/174` | `failed=0/174` |
| `RESULT mode=docservice` | `failed=0/174` | `failed=0/174` |
| `RESULT mode=storm` | `failed=173/174` with `scans` below 200 in most runs | `failed=0/174 scans=200/200` |

Example output on Armeria 1.42.0:

```
RESULT sharedJarFile=closed (Zip file closed)
RESULT mode=none failed=0/174
RESULT mode=none failed=0/174
RESULT mode=none failed=0/174
RESULT mode=docservice failed=0/174
RESULT mode=docservice failed=0/174
RESULT mode=docservice failed=0/174
RESULT mode=storm failed=173/174 scans=5/200
  java.lang.ClassNotFoundException: com.linecorp.armeria.client.grpc.GrpcClientBuilder (cause=null)
    at java.base/java.net.URLClassLoader.findClass(Unknown Source)
    at java.base/java.lang.ClassLoader.loadClass(Unknown Source)
    at org.springframework.boot.loader.net.protocol.jar.JarUrlClassLoader.loadClass(JarUrlClassLoader.java:107)
    at org.springframework.boot.loader.launch.LaunchedClassLoader.loadClass(LaunchedClassLoader.java:91)
RESULT mode=storm failed=173/174 scans=19/200
RESULT mode=storm failed=173/174 scans=74/200
```

`Main` also prints the actual class loader and resource URL. These should show
`LaunchedClassLoader` and `jar:nested:.../BOOT-INF/lib/...`.

## Scope

`sharedJarFile` obtains the cached jar containing the gRPC descriptor, runs one
`DocStringExtractor` scan, checks that the descriptor was scanned, and checks
whether the shared jar remains open.

Each `mode` line is a fresh JVM. Four threads keep reading a class from
`armeria-grpc` while the mode runs, then every `armeria-grpc` class is loaded and
`ClassNotFoundException`s without a cause are counted:

- `none`: no scan
- `docservice`: starts a `Server` with `GrpcService` and `DocService` once
- `storm`: repeats `DocStringExtractor` scans with new class loader keys to widen the race window.
  `scans` counts the scans that found the gRPC descriptor

`storm` is a race, so a run can occasionally show `failed=0/174` on 1.42.0.

The patched build compiles core, gRPC, gRPC protocol and protobuf from the same
commit. It uses core's untrimmed shaded jar and skips the DocService web UI.
The checked-out commit is recorded in `/app/armeria-commit.txt`.

Build arguments `ARMERIA_VERSION` (before), `ARMERIA_REF` (after), and
`SPRING_BOOT_VERSION` can be used to change the versions.

## Spring Boot compatibility

The separate [compatibility repro](compatibility/README.md) uses Reflections
0.9.12 directly to check the older `JarFile.stream()` failure and resource/subtype
scanning across five Boot versions. The top-level repro checks the Armeria fix.

```sh
docker build -t reflections-boot-compatibility compatibility
docker run --rm reflections-boot-compatibility
```

[Additional Armeria loader results](VALIDATION.md) record separate checks of
`DocStringExtractor` and `XdsResourceReader` across Boot versions.
