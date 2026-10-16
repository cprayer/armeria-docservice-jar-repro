# Additional Armeria loader checks

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
