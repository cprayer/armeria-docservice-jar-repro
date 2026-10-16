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

This builds Armeria from the pinned [upgrade commit](https://github.com/cprayer/armeria/commit/7fa42d7b20cf89348c150b4c43825f331d6b3cc2)
inside Docker. The first build takes several minutes.

```sh
docker build -f Dockerfile.patched -t armeria-jarfile-repro:patched .
docker run --rm armeria-jarfile-repro:patched
```

### Expected results

| Armeria | Output |
| --- | --- |
| 1.42.0 | `RESULT sharedJarFile=closed (Zip file closed)` |
| Upgrade commit | `RESULT sharedJarFile=open` |

`Main` also prints the actual class loader and resource URL. These should show
`LaunchedClassLoader` and `jar:nested:.../BOOT-INF/lib/...`.

## Scope

`Main` obtains the cached jar containing the gRPC descriptor, runs one
`DocStringExtractor` scan, checks that the descriptor was scanned, and checks
whether the shared jar remains open. The production `ClassNotFoundException`
timing race is outside this check.

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
