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

import com.linecorp.armeria.common.util.Version;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.docs.DocService;
import com.linecorp.armeria.server.docs.DocStringExtractor;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.server.grpc.GrpcHealthCheckService;

/**
 * Usage: java -jar repro.jar [none|docservice|storm] [loaderThreads]
 */
public final class Main {

    private static final String PROBE = "com/linecorp/armeria/server/grpc/GrpcService.class";

    public static void main(String[] args) throws Exception {
        final String mode = args.length > 0 ? args[0] : "storm";
        if ("xds-close".equals(mode)) {
            xdsClose();
            return;
        }
        final int loaderThreads = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        final ClassLoader cl = Main.class.getClassLoader();
        final List<String> victims = classesInJarOf(cl, PROBE);

        final AtomicBoolean stop = new AtomicBoolean();
        String scanned = "-";
        final List<Thread> loaders = new ArrayList<>();
        for (int i = 0; i < loaderThreads; i++) {
            loaders.add(new Thread(() -> {
                while (!stop.get()) {
                    try (InputStream in = cl.getResourceAsStream(PROBE)) {
                        in.read();
                    } catch (Throwable ignored) {
                    }
                }
            }));
        }
        loaders.forEach(t -> t.setDaemon(true));

        loaders.forEach(Thread::start);
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
                final GrpcDescriptorExtractor extractor = new GrpcDescriptorExtractor();
                for (int i = 0; i < 200; i++) {
                    try {
                        scanned = extractor.getAllDocStrings(new ClassLoader(cl) {}).get("files");
                    } catch (Exception e) {
                        System.out.println("SCAN FAILED at " + i + ": " + e);
                        break;
                    }
                }
                break;
            default:
                throw new IllegalArgumentException(mode);
        }
        stop.set(true);
        for (Thread t : loaders) {
            t.join();
        }

        int failed = 0;
        ClassNotFoundException first = null;
        for (String v : victims) {
            final ClassNotFoundException e = tryLoad(cl, v);
            if (e != null) {
                failed++;
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first != null) {
            first.printStackTrace(System.out);
        }
        System.out.printf("RESULT armeria=%s mode=%s loaders=%d scanned=%s failed=%d/%d first=%s%n",
                          Version.get("armeria").artifactVersion(), mode, loaderThreads, scanned,
                          failed, victims.size(), first == null ? null : first.getMessage());
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

    private static void xdsClose() throws Exception {
        final URL url = Main.class.getClassLoader().getResource(
                "io/envoyproxy/envoy/config/bootstrap/v3/Bootstrap.class");
        final JarFile shared = ((JarURLConnection) url.openConnection()).getJarFile();
        Class.forName("com.linecorp.armeria.xds.XdsResourceReader").getMethod("typeRegistry").invoke(null);
        String state;
        try {
            shared.getEntry("io/envoyproxy/envoy/config/bootstrap/v3/Bootstrap.class");
            state = "open";
        } catch (IllegalStateException e) {
            state = "closed (" + e.getMessage() + ')';
        }
        System.out.printf("RESULT armeria=%s mode=xds-close sharedJarFile=%s%n",
                          Version.get("armeria").artifactVersion(), state);
        System.exit(0);
    }

    private static List<String> classesInJarOf(ClassLoader cl, String resource) throws Exception {
        // e.g. jar:nested:/path/repro.jar/!BOOT-INF/lib/armeria-grpc-1.27.1.jar!/com/...
        final URL url = cl.getResource(resource);
        final String spec = url.toString();
        final int sep = spec.indexOf("/!");
        final String outer = spec.substring("jar:nested:".length(), sep);
        final String nested = spec.substring(sep + 2, spec.indexOf("!/", sep + 2));
        final List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(outer)) {
            final ZipEntry entry = zip.getEntry(nested);
            try (InputStream in = zip.getInputStream(entry);
                 ZipInputStream zin = new ZipInputStream(in)) {
                for (ZipEntry e; (e = zin.getNextEntry()) != null; ) {
                    final String n = e.getName();
                    if (n.endsWith(".class") && !n.startsWith("META-INF/") && !n.endsWith("-info.class")) {
                        names.add(n.substring(0, n.length() - 6).replace('/', '.'));
                    }
                }
            }
        }
        return names;
    }

    private static final class GrpcDescriptorExtractor extends DocStringExtractor {

        GrpcDescriptorExtractor() {
            super("META-INF/armeria/grpc", "repro.grpc.dir");
        }

        @Override
        protected Map<String, String> getDocStringsFromFiles(Map<String, byte[]> files) {
            return Map.of("files", String.valueOf(files.size()));
        }
    }
}
