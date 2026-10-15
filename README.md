# DocStringExtractor closes a JarFile shared with Spring Boot's class loader

A Spring Boot executable jar with `armeria` and `armeria-grpc` only.

```
./run.sh <armeria-version> <spring-boot-version> <none|docservice|storm> <runs>
./run.sh 1.42.0 3.5.16 storm 10
```

- `none`: no scan, only concurrent resource lookups (control)
- `docservice`: starts a `Server` with `GrpcService` and `DocService`, the same as a real application
- `storm`: scans `META-INF/armeria/grpc` repeatedly with `DocStringExtractor` to widen the race window

Each run then loads every class in `armeria-grpc` and counts `ClassNotFoundException`s without a cause.

## Result

```
RESULT armeria=1.42.0 mode=storm loaders=4 failed=173/174 first=com.linecorp.armeria.client.grpc.GrpcClientBuilder
java.lang.ClassNotFoundException: com.linecorp.armeria.client.grpc.GrpcClientBuilder
	at java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:445)
	at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:592)
	at org.springframework.boot.loader.net.protocol.jar.JarUrlClassLoader.loadClass(JarUrlClassLoader.java:107)
	at org.springframework.boot.loader.launch.LaunchedClassLoader.loadClass(LaunchedClassLoader.java:91)
```

Once it happens, the class loader cannot load any class from the jar that was not loaded yet until the JVM restarts.

## Cause

1. Reflections' jar `UrlType` gets the `JarFile` with `JarURLConnection.getJarFile()` (`useCaches=true`), which is the cached instance shared with the class loader, and closes it after scanning.
2. Spring Boot's `JarUrlConnection.connect()` gets the cached `JarFile`, looks up the entry and then caches it again with `cacheIfAbsent`. If the `JarFile` is closed between the two, the closed `JarFile` is put back into the cache.
3. Every later lookup through the class loader uses the closed `JarFile` and fails. JDK's `URLClassPath` swallows the exception, so a `ClassNotFoundException` without a cause is thrown.
