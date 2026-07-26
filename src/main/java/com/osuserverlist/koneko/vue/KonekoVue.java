package com.osuserverlist.koneko.vue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javalin.http.Context;
import io.javalin.http.Handler;

/**
 * A tiny Vue layer in the spirit of the JavalinVue plugin.
 *
 * <p>The plugin itself only exists up to Javalin 6 - Javalin 7 dropped it, so
 * this class does the same three things it did, and nothing more:
 *
 * <ul>
 *   <li>{@code layout.html} is the single HTML shell of the site;</li>
 *   <li>{@code @componentRegistration} is replaced by every {@code .vue} file
 *       found under {@code /vue}, so a new component is a new file and nothing
 *       else;</li>
 *   <li>{@code @routeComponent} is replaced by the component of the route being
 *       served, which is what makes one route one line.</li>
 * </ul>
 *
 * <p>In production everything is read from the classpath once and cached, so a
 * page render is two string replacements. In development ({@code LEVEL=DEV})
 * the files are re-read from {@code src/main/resources/vue} on every request,
 * so editing a component only needs a refresh.
 */
public final class KonekoVue {

    private static final Logger logger = LoggerFactory.getLogger("KonekoVue");

    private static final String CLASSPATH_ROOT = "/vue";
    private static final Path DEV_ROOT = Paths.get("src", "main", "resources", "vue");

    private static final String LAYOUT = "layout.html";
    private static final String COMPONENT_MARKER = "@componentRegistration";
    private static final String ROUTE_MARKER = "@routeComponent";

    private static boolean devMode;

    private static volatile String cachedLayout;
    private static volatile String cachedComponents;

    private KonekoVue() {
    }

    /**
     * @param dev when true, the .vue files are re-read per request from the
     *            source tree instead of the classpath.
     */
    public static void configure(boolean dev) {
        devMode = dev;
        cachedLayout = null;
        cachedComponents = null;
    }

    /** A handler rendering one component as a full page. */
    public static Handler component(String name) {
        return component(name, 200);
    }

    /** A handler rendering one component as a full page with a given status. */
    public static Handler component(String name, int status) {
        return ctx -> render(ctx, name, status);
    }

    private static void render(Context ctx, String component, int status) throws IOException {
        String html = layout()
                .replace(COMPONENT_MARKER, components())
                .replace(ROUTE_MARKER, "<" + component + "></" + component + ">");

        ctx.status(status);
        ctx.contentType("text/html; charset=utf-8");
        ctx.result(html);
    }

    private static String layout() throws IOException {
        String cached = cachedLayout;

        if (cached != null && !devMode) {
            return cached;
        }

        String layout = readFile(LAYOUT);

        if (!layout.contains(ROUTE_MARKER)) {
            logger.warn("{} has no {} marker, pages will render empty", LAYOUT, ROUTE_MARKER);
        }

        cachedLayout = layout;
        return layout;
    }

    private static String components() throws IOException {
        String cached = cachedComponents;

        if (cached != null && !devMode) {
            return cached;
        }

        StringBuilder builder = new StringBuilder();

        for (String file : componentFiles()) {
            builder.append("<!-- ").append(file).append(" -->\n");
            builder.append(readFile(file)).append('\n');
        }

        String components = builder.toString();

        cachedComponents = components;
        return components;
    }

    /** Every .vue file under the vue root, as paths relative to that root. */
    private static List<String> componentFiles() throws IOException {
        if (devMode && Files.isDirectory(DEV_ROOT)) {
            return collect(DEV_ROOT);
        }

        URL url = KonekoVue.class.getResource(CLASSPATH_ROOT);

        if (url == null) {
            throw new IOException("The " + CLASSPATH_ROOT + " directory is missing from the classpath");
        }

        URI uri;

        try {
            uri = url.toURI();
        } catch (URISyntaxException e) {
            throw new IOException("Could not read " + CLASSPATH_ROOT, e);
        }

        if (!"jar".equals(uri.getScheme())) {
            return collect(Paths.get(uri));
        }

        // Running from the shaded jar: mount it as a filesystem to list it. The
        // filesystem is left open on purpose, the result is cached anyway.
        FileSystem fs;

        try {
            fs = FileSystems.newFileSystem(uri, Map.of());
        } catch (FileSystemAlreadyExistsException e) {
            fs = FileSystems.getFileSystem(uri);
        }

        return collect(fs.getPath(CLASSPATH_ROOT));
    }

    private static List<String> collect(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            List<String> names = new ArrayList<>();

            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".vue"))
                    .forEach(path -> names.add(root.relativize(path).toString().replace('\\', '/')));

            // Components before views, so the shared pieces are defined first.
            names.sort(String::compareTo);
            return names;
        } catch (UncheckedIOException e) {
            throw new IOException("Could not list the vue files", e);
        }
    }

    private static String readFile(String relative) throws IOException {
        if (devMode) {
            Path path = DEV_ROOT.resolve(relative);

            if (Files.isRegularFile(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        }

        try (InputStream in = KonekoVue.class.getResourceAsStream(CLASSPATH_ROOT + "/" + relative)) {
            if (in == null) {
                throw new IOException("Missing vue resource: " + relative);
            }

            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
