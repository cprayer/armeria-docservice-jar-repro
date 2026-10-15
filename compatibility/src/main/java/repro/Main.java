package repro;

import java.net.JarURLConnection;
import java.net.URL;
import java.util.jar.JarFile;

import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.Scanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

public final class Main {

    public static void main(String[] args) throws Exception {
        final ClassLoader loader = Main.class.getClassLoader();
        final URL resource = loader.getResource("org/reflections/Reflections.class");
        final JarFile shared = ((JarURLConnection) resource.openConnection()).getJarFile();
        if ("stream".equals(args[0])) {
            System.out.println("RESULT entries=" + shared.stream().count());
            return;
        }

        final Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage("org.reflections", loader))
                .addClassLoader(loader)
                .setScanners(new ResourcesScanner(), new SubTypesScanner()));
        if (!reflections.getResources(name -> name.equals("pom.properties")).contains(
                "META-INF/maven/org.reflections/reflections/pom.properties")) {
            throw new AssertionError("resource scan failed");
        }
        if (!reflections.getSubTypesOf(Scanner.class).contains(ResourcesScanner.class)) {
            throw new AssertionError("subtype scan failed");
        }
        if (shared.getEntry("org/reflections/Reflections.class") == null) {
            throw new AssertionError("shared jar entry missing");
        }
        System.out.println("RESULT resources=found subtypes=found sharedJarFile=open");
    }
}
