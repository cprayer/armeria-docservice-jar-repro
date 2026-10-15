import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

public final class CompatibilityMatrix {

    public static void main(String[] args) throws Exception {
        final String[] versions = { "2.3.4.RELEASE", "2.7.18", "3.5.16", "4.1.1" };
        for (String version : versions) {
            final String jarPath = "/app/repros/" + version + ".jar";
            try (JarFile jar = new JarFile(jarPath)) {
                final String actual = jar.getManifest().getMainAttributes().getValue("Spring-Boot-Version");
                if (!version.equals(actual)) {
                    throw new AssertionError("Expected Spring Boot " + version + ", got " + actual);
                }
            }
            for (String mode : new String[] { "stream", "scan" }) {
                final Process process = new ProcessBuilder(
                        "java", "-jar", jarPath, mode)
                        .redirectErrorStream(true).start();
                final String output = new String(process.getInputStream().readAllBytes(),
                                                  StandardCharsets.UTF_8);
                final int status = process.waitFor();
                final boolean control = version.equals("2.3.4.RELEASE");
                final boolean expected = control
                        ? status == 1 && (output.contains("zip file closed") || output.contains("Zip file closed"))
                        : status == 0;
                if (!expected) {
                    System.err.println(output);
                    throw new AssertionError(version + " " + mode + ": unexpected exit=" + status);
                }
                System.out.println("PASS " + version + " " + mode + ": " +
                                   (control ? "zip file closed (expected control failure)" : "success"));
            }
        }
    }
}
