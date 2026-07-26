package com.osuserverlist.koneko.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Reading files out of a plugin jar.
 *
 * <p>Two ways in, because both are needed: the plugin class loader for a single
 * known resource, and the jar itself when a whole directory has to be listed -
 * a class loader cannot enumerate a folder.
 */
final class PluginResources {

    private PluginResources() {
    }

    /** Normalises a resource path to the leading-slash form. */
    static String normalise(String resource) {
        if (resource == null || resource.isBlank()) {
            return "/";
        }

        String path = resource.trim().replace('\\', '/');

        return path.startsWith("/") ? path : "/" + path;
    }

    /** Strips the leading slash, which is what a class loader wants. */
    static String classpathName(String resource) {
        String path = normalise(resource);
        return path.substring(1);
    }

    /** Reads a resource as UTF-8 text, or null when it is not there. */
    static String readText(ClassLoader loader, String resource) throws IOException {
        byte[] bytes = readBytes(loader, resource);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    /** Reads a resource as bytes, or null when it is not there. */
    static byte[] readBytes(ClassLoader loader, String resource) throws IOException {
        if (loader == null) {
            return null;
        }

        try (InputStream in = loader.getResourceAsStream(classpathName(resource))) {
            return in == null ? null : in.readAllBytes();
        }
    }

    /**
     * Lists the files under a directory of a plugin jar.
     *
     * @param pluginPath the jar file, or a directory when the plugin is not
     *                   packaged (which happens while developing one)
     * @param root       resource directory, e.g. {@code /vue}
     * @param suffix     extension filter, e.g. {@code .vue}, or null for all
     * @return resource paths in the leading-slash form, sorted
     */
    static List<String> list(Path pluginPath, String root, String suffix) throws IOException {
        String prefix = classpathName(root);

        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }

        List<String> found = new ArrayList<>();

        if (pluginPath == null) {
            return found;
        }

        if (Files.isDirectory(pluginPath)) {
            Path base = pluginPath.resolve(prefix.isEmpty() ? "." : prefix);

            if (!Files.isDirectory(base)) {
                return found;
            }

            try (var files = Files.walk(base)) {
                final String directory = prefix;

                files.filter(Files::isRegularFile).forEach(file -> {
                    String name = pluginPath.relativize(file).toString().replace('\\', '/');

                    if (name.startsWith(directory) && (suffix == null || name.endsWith(suffix))) {
                        found.add("/" + name);
                    }
                });
            }

            found.sort(String::compareTo);
            return found;
        }

        try (JarFile jar = new JarFile(pluginPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();

                if (!name.startsWith(prefix)) {
                    continue;
                }

                if (suffix == null || name.endsWith(suffix)) {
                    found.add("/" + name);
                }
            }
        }

        found.sort(String::compareTo);
        return found;
    }

    /**
     * Rejects anything that could climb out of the asset root. Plugin asset
     * urls come from the browser, so this is the one place where a path really
     * is untrusted input.
     */
    static boolean isSafePath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        String value = path.replace('\\', '/');

        if (value.contains("..") || value.contains("//") || value.startsWith("/")) {
            return false;
        }

        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);

            boolean allowed = Character.isLetterOrDigit(c)
                    || c == '/' || c == '.' || c == '-' || c == '_';

            if (!allowed) {
                return false;
            }
        }

        return true;
    }

    /** Content type for an asset, guessed from the extension. */
    static String contentTypeOf(String path) {
        String name = path == null ? "" : path.toLowerCase(java.util.Locale.ROOT);

        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }

        if (name.endsWith(".js") || name.endsWith(".mjs")) {
            return "application/javascript; charset=utf-8";
        }

        if (name.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }

        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }

        if (name.endsWith(".png")) {
            return "image/png";
        }

        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }

        if (name.endsWith(".gif")) {
            return "image/gif";
        }

        if (name.endsWith(".webp")) {
            return "image/webp";
        }

        if (name.endsWith(".ico")) {
            return "image/x-icon";
        }

        if (name.endsWith(".woff2")) {
            return "font/woff2";
        }

        if (name.endsWith(".woff")) {
            return "font/woff";
        }

        if (name.endsWith(".ttf")) {
            return "font/ttf";
        }

        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }

        if (name.endsWith(".txt")) {
            return "text/plain; charset=utf-8";
        }

        return "application/octet-stream";
    }
}
