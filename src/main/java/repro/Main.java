package repro;

import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.docs.DocService;
import com.linecorp.armeria.server.docs.DocStringExtractor;
import com.linecorp.armeria.server.grpc.GrpcHealthCheckService;
import com.linecorp.armeria.server.grpc.GrpcService;

public final class Main {

    private static final String DESCRIPTOR = "META-INF/armeria/grpc/armeria-main.dsc";
    private static final String PROBE = "com/linecorp/armeria/server/grpc/GrpcService.class";
    private static final List<String> RACE_MODES = List.of("none", "docservice", "storm");
    private static final int RUNS = 3;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            race(args[0], args.length > 1);
            return;
        }
        checkSharedJarFile();
        final String java = ProcessHandle.current().info().command().orElse("java");
        final String jar = System.getProperty("java.class.path");
        for (String mode : RACE_MODES) {
            for (int i = 0; i < RUNS; i++) {
                final List<String> command = new ArrayList<>(List.of(java, "-jar", jar, mode));
                if (i == 0) {
                    command.add("trace");
                }
                new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.INHERIT)
                                           .redirectError(ProcessBuilder.Redirect.DISCARD)
                                           .start().waitFor();
            }
        }
    }

    private static void checkSharedJarFile() throws Exception {
        final ClassLoader classLoader = Main.class.getClassLoader();
        final URL resource = classLoader.getResource(DESCRIPTOR);
        if (resource == null) {
            throw new IllegalStateException("armeria-grpc descriptor not found");
        }
        System.out.println("classLoader=" + classLoader.getClass().getName());
        System.out.println("resource=" + resource);

        final JarFile shared = ((JarURLConnection) resource.openConnection()).getJarFile();
        final DocStringExtractor extractor = new DocStringExtractor("META-INF/armeria/grpc", "repro.grpc.dir") {
            @Override
            protected Map<String, String> getDocStringsFromFiles(Map<String, byte[]> files) {
                if (!files.containsKey(DESCRIPTOR)) {
                    throw new AssertionError("armeria-grpc descriptor was not scanned");
                }
                return Map.of();
            }
        };
        extractor.getAllDocStrings(classLoader);

        try {
            shared.getEntry(DESCRIPTOR);
            System.out.println("RESULT sharedJarFile=open");
        } catch (IllegalStateException e) {
            System.out.println("RESULT sharedJarFile=closed (" + e.getMessage() + ')');
        }
    }

    private static void race(String mode, boolean trace) throws Exception {
        final ClassLoader cl = Main.class.getClassLoader();
        final List<String> classes = classesInJarOf(cl, PROBE);

        final AtomicBoolean stop = new AtomicBoolean();
        String scans = "";
        final List<Thread> readers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final Thread t = new Thread(() -> {
                while (!stop.get()) {
                    try (InputStream in = cl.getResourceAsStream(PROBE)) {
                        in.read();
                    } catch (Throwable ignored) {
                    }
                }
            });
            t.setDaemon(true);
            t.start();
            readers.add(t);
        }

        switch (mode) {
            case "none":
                break;
            case "docservice":
                final Server server = Server.builder()
                                            .http(0)
                                            .service(GrpcService.builder()
                                                                .addService(GrpcHealthCheckService.of())
                                                                .build())
                                            .serviceUnder("/docs", DocService.builder().build())
                                            .build();
                server.start().join();
                Thread.sleep(3000);
                server.stop().join();
                break;
            case "storm":
                final DocStringExtractor extractor =
                        new DocStringExtractor("META-INF/armeria/grpc", "repro.grpc.dir") {
                            @Override
                            protected Map<String, String> getDocStringsFromFiles(Map<String, byte[]> files) {
                                return files.containsKey(DESCRIPTOR) ? Map.of(DESCRIPTOR, "") : Map.of();
                            }
                        };
                int found = 0;
                for (int i = 0; i < 200; i++) {
                    try {
                        found += extractor.getAllDocStrings(new ClassLoader(cl) {}).size();
                    } catch (Exception e) {
                        break;
                    }
                }
                scans = " scans=" + found + "/200";
                break;
            default:
                throw new IllegalArgumentException(mode);
        }
        stop.set(true);
        for (Thread t : readers) {
            t.join();
        }

        int failed = 0;
        ClassNotFoundException first = null;
        for (String name : classes) {
            final ClassNotFoundException e = tryLoad(cl, name);
            if (e != null) {
                failed++;
                if (first == null) {
                    first = e;
                }
            }
        }
        System.out.printf("RESULT mode=%s failed=%d/%d%s%n", mode, failed, classes.size(), scans);
        if (trace && first != null) {
            final StackTraceElement[] frames = first.getStackTrace();
            System.out.println("  " + first + " (cause=" + first.getCause() + ')');
            for (int i = 0; i < Math.min(4, frames.length); i++) {
                System.out.println("    at " + frames[i]);
            }
        }
        System.exit(0);
    }

    private static ClassNotFoundException tryLoad(ClassLoader cl, String name) {
        try {
            Class.forName(name, false, cl);
        } catch (ClassNotFoundException e) {
            if (name.equals(e.getMessage()) && e.getCause() == null) {
                return e;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static List<String> classesInJarOf(ClassLoader cl, String resource) throws Exception {
        final String spec = cl.getResource(resource).toString();
        final int sep = spec.indexOf("/!");
        final String outer = spec.substring("jar:nested:".length(), sep);
        final String nested = spec.substring(sep + 2, spec.indexOf("!/", sep + 2));
        final List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(outer);
             InputStream in = zip.getInputStream(zip.getEntry(nested));
             ZipInputStream zin = new ZipInputStream(in)) {
            for (ZipEntry e; (e = zin.getNextEntry()) != null; ) {
                final String n = e.getName();
                if (n.endsWith(".class") && !n.startsWith("META-INF/") && !n.endsWith("-info.class")) {
                    names.add(n.substring(0, n.length() - 6).replace('/', '.'));
                }
            }
        }
        return names;
    }
}
