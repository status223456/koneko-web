package com.osuserverlist.koneko.docs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.osuserverlist.koneko.App;

/**
 * The static text pages of the site, written as Markdown files.
 *
 * <p>A document is one {@code .md} file under {@code /docs}: the restrictions
 * page is {@code docs/restrictions.md}, so its text can be rewritten without
 * touching a component. A server may also override any of them by dropping a
 * file of the same name into {@code .config/docs/}, which is read first - that
 * way a rule page can be reworded per server without a rebuild.
 *
 * <p>Documents may use a couple of placeholders, which are filled in from
 * {@code config.yml} before rendering:
 *
 * <ul>
 *   <li>{@code {{discord}}} - the Discord invite of this server;</li>
 *   <li>{@code {{server}}} - the name of this server.</li>
 * </ul>
 */
public final class Docs {

    private static final Logger logger = LoggerFactory.getLogger("Docs");

    /** Where a server may put its own copy of a document. */
    private static final Path OVERRIDE_ROOT = Paths.get(".config", "docs");

    /** The documents shipped with the site, inside the jar. */
    private static final String CLASSPATH_ROOT = "/docs";

    /** No slashes and no dots: a name can never walk out of the docs folder. */
    private static final Pattern SAFE_NAME = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");

    /** Rendered documents, keyed by name. Cleared per request in DEV. */
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private Docs() {
    }

    /**
     * The rendered HTML of one document, or {@code null} when there is no such
     * document.
     */
    public static String html(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            return null;
        }

        if (App.env != null && App.env.isDev()) {
            CACHE.remove(name);
        }

        String cached = CACHE.get(name);

        if (cached != null) {
            return cached;
        }

        String markdown = read(name);

        if (markdown == null) {
            return null;
        }

        String html = Markdown.render(fill(markdown));

        CACHE.put(name, html);

        return html;
    }

    /** The document's source, from the override folder or from the jar. */
    private static String read(String name) {
        Path override = OVERRIDE_ROOT.resolve(name + ".md");

        if (Files.isRegularFile(override)) {
            try {
                return Files.readString(override, StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.warn("Could not read the document <{}> from <{}>", name, override, e);
            }
        }

        try (InputStream in = Docs.class.getResourceAsStream(CLASSPATH_ROOT + "/" + name + ".md")) {
            if (in == null) {
                return null;
            }

            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Could not read the document <{}>", name, e);

            return null;
        }
    }

    /**
     * Fills the placeholders a document may use.
     *
     * <p>The Discord invite is the important one: every "ask us about it" in
     * the texts points at the server's own Discord, and the address of that
     * lives in {@code config.yml} rather than in the text.
     */
    private static String fill(String markdown) {
        String discord = App.site == null ? "" : App.site.getLinks().getDiscord();
        String server = App.site == null ? "" : App.site.getServer().getName();

        // With no invite configured the links would point nowhere, so the
        // sentence keeps its words and simply stops being a link.
        String filled = discord == null || discord.isBlank()
                ? markdown.replaceAll("\\[([^]]+)]\\(\\{\\{discord}}\\)", "$1")
                : markdown.replace("{{discord}}", discord);

        return filled.replace("{{server}}", server == null ? "" : server);
    }
}
