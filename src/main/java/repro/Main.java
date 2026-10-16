package repro;

import java.net.JarURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.jar.JarFile;

import com.linecorp.armeria.server.docs.DocStringExtractor;

public final class Main {

    public static void main(String[] args) throws Exception {
        final ClassLoader classLoader = Main.class.getClassLoader();
        final URL resource = classLoader.getResource("META-INF/armeria/grpc/armeria-main.dsc");
        if (resource == null) {
            throw new IllegalStateException("armeria-grpc descriptor not found");
        }
        System.out.println("classLoader=" + classLoader.getClass().getName());
        System.out.println("resource=" + resource);

        final JarFile shared = ((JarURLConnection) resource.openConnection()).getJarFile();
        final DocStringExtractor extractor = new DocStringExtractor("META-INF/armeria/grpc", "repro.grpc.dir") {
            @Override
            protected Map<String, String> getDocStringsFromFiles(Map<String, byte[]> files) {
                if (!files.containsKey("META-INF/armeria/grpc/armeria-main.dsc")) {
                    throw new AssertionError("armeria-grpc descriptor was not scanned");
                }
                return Map.of();
            }
        };
        extractor.getAllDocStrings(classLoader);

        try {
            shared.getEntry("META-INF/armeria/grpc/armeria-main.dsc");
            System.out.println("RESULT sharedJarFile=open");
        } catch (IllegalStateException e) {
            System.out.println("RESULT sharedJarFile=closed (" + e.getMessage() + ')');
        }
    }
}
