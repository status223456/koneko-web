package com.osuserverlist.koneko.routes;

import java.util.LinkedHashMap;
import java.util.Map;

import com.osuserverlist.koneko.docs.Docs;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

/**
 * The Markdown documents behind the static text pages.
 *
 * <p>The page itself is an ordinary Vue component; the text it shows is fetched
 * from here, already rendered, so a document is a {@code .md} file and nothing
 * else. Public and identical for everyone, which is why it may be cached.
 */
public final class DocsRoutes {

    private DocsRoutes() {
    }

    public static void register(JavalinConfig config) {
        config.routes.get("/data/docs/{name}", DocsRoutes::document);
    }

    private static void document(Context ctx) {
        String name = ctx.pathParam("name");
        String html = Docs.html(name);

        if (html == null) {
            ctx.status(404).json(Map.of("status", "No such document."));
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("html", html);

        ctx.json(body);
    }
}
