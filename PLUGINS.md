# koneko-web plugins

koneko-web loads `.jar` plugins with [PF4J](https://pf4j.org/). A plugin is a
normal jar dropped into the `plugins/` folder next to the site; there is nothing
to rebuild in the site itself and the backend (bancho.jar) is untouched - plugins
talk to it through the same public API the site uses.

```
koneko-web/
  koneko-web.jar
  .env
  config.yml
  plugins/
    my-plugin-1.0.0.jar        <- your plugins
    config/my-plugin.yml       <- settings of one plugin (optional)
    data/my-plugin/            <- private storage of one plugin (created on demand)
```

## What a plugin can do

| Capability | How |
| --- | --- |
| A whole new page at its own URL | `PluginPage` |
| HTTP endpoints (GET/POST/PUT/PATCH/DELETE, JSON, forms, files, SSE) | `PluginAction` |
| Before/after filters around requests | `PluginFilter` |
| Vue components, shipped as `.vue` files in the jar | `PluginComponent`, `registry.components("/vue")` |
| Blocks inserted into the existing pages | `PluginSlot` + `Slots` |
| Navigation bar and footer entries | `PluginNavItem` |
| Hiding or replacing a core navigation link | `registry.hideNavItem(id)` |
| CSS, JS, images, fonts served from the jar | `PluginAsset`, `registry.assets("/static")` |
| Extra `<head>` / end-of-body html | `registry.stylesheet/script/head/bodyEnd` |
| Extra fields in the bootstrap state of every page | `registry.state(key, contributor)` |
| Extra fields in the `/data/*` answers of the site | `Events.Data` |
| Background tasks on a schedule | `PluginJob` |
| Reacting to login, logout, page render, startup, shutdown | `registry.on(Events.X.class, ...)` |
| Calling bancho.jar, anonymously or as the logged in player | `context().api()` |
| Caching, own settings, own data directory, logging | `context().cache()/settings()/dataDir()/logger()` |

Everything is declared in one place, `KonekoExtension#register`.

## The smallest plugin

`build.gradle.kts`:

```kotlin
plugins { java }

repositories { mavenCentral() }

dependencies {
    // The api jar of the site: `./gradlew pluginApiJar` inside koneko-web.
    // compileOnly, never implementation - see the warning below.
    compileOnly(files("libs/koneko-plugin-api.jar"))
    compileOnly("org.pf4j:pf4j:3.13.0")
    annotationProcessor("org.pf4j:pf4j:3.13.0")

    compileOnly("io.javalin:javalin:7.2.2")
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    compileOnly("org.slf4j:slf4j-api:2.0.17")
}

tasks.jar {
    manifest.attributes(
        "Plugin-Id" to "my-plugin",
        "Plugin-Version" to "1.0.0",
        "Plugin-Class" to "com.example.MyPlugin",
        "Plugin-Provider" to "me",
    )
}
```

Instead of the local jar, the api can be taken from JitPack once the site is
pushed and tagged:

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.OpenBancho:koneko-web:v0.1.0")
}
```

JitPack builds from git, so anything not committed and pushed does not exist for
it; tag the commit you want plugin authors to build against. The site ships a
`jitpack.yml` that installs JDK 25 and runs `publishToMavenLocal`, which publishes
exactly the api jar - `com.github.openbancho:koneko-web:<version>`, no transitive
dependencies.

> **Never bundle the API, Javalin, Jackson or slf4j into the plugin jar.** The
> PF4J class loader looks in the plugin first, so a bundled copy would be a
> different class than the host's and the extension would simply be invisible.
> `compileOnly` is the only correct scope.

`MyPlugin.java` - the class named in the manifest:

```java
public class MyPlugin extends org.pf4j.Plugin {
}
```

`MyExtension.java` - the actual plugin:

```java
@org.pf4j.Extension
public class MyExtension implements KonekoExtension {

    private KonekoContext koneko;

    @Override
    public void onStart(KonekoContext context) {
        this.koneko = context;
    }

    @Override
    public void register(PluginRegistry registry) {
        registry.components("/vue")            // every .vue file in the jar
                .assets("/static")              // every file under /static
                .stylesheet(koneko.assetUrl("/my.css"))
                .page(PluginPage.of("/tournaments", "tournaments-view")
                        .withTitle("Tournaments"))
                .navItem(PluginNavItem.of("Tournaments", "/tournaments"))
                .slot(PluginSlot.of(Slots.HOME_TOP, "tournaments-banner"))
                .action(PluginAction.get("/list", ctx -> ctx.json(list())))
                .state("upcoming", ctx -> list())
                .job(PluginJob.every("refresh", 300, this::refresh))
                .on(Events.Login.class, e -> koneko.logger().info("{} is back", e.user().name()));
    }
}
```

Build it, drop the jar in `plugins/`, restart the site. The log line
`plugins: started 1 (my-plugin 1.0.0)` means it is in.

## Manifest attributes

| Attribute | Meaning |
| --- | --- |
| `Plugin-Id` | Unique id. Used in every url of the plugin, so keep it url safe. |
| `Plugin-Version` | Semantic version. |
| `Plugin-Class` | Your `org.pf4j.Plugin` subclass. |
| `Plugin-Provider` | Author, shown in `/data/plugins`. |
| `Plugin-Requires` | Optional version range of the site. |
| `Plugin-Dependencies` | Optional other plugin ids this one needs. |

## Pages

`PluginPage.of(path, component)` mounts an absolute path rendered by one of the
plugin's vue components, exactly like a core page: same shell, same navigation,
same bootstrap state.

```java
PluginPage.of("/tournaments", "tournaments-view")
        .withTitle("Tournaments")   // <title> of the tab
        .withLogin()                // 302 to /login for guests
        .withPrivileges(1 << 2)     // bancho.jar privilege mask required
```

Core paths (`/`, `/login`, `/me`, `/leaderboard`, `/beatmaps`, `/u/*`,
`/beatmapsets/*`, `/data/*`, `/auth/*`, static roots) are reserved. A plugin
asking for one is refused with a log line and the rest of the plugin still
loads.

## Actions

```java
PluginAction.post("/vote", ctx -> { ... }).withLogin()
```

Relative paths are mounted under `/plugins/{pluginId}/`, so this one is
`/plugins/my-plugin/vote`; `context().actionUrl("/vote")` builds the url for you
and `this.pluginAction('my-plugin', '/vote')` does the same in vue. Use
`asAbsolute()` to take a path outside that namespace (still refused for core
paths). The handler is a plain Javalin `Handler`, so everything Javalin can do -
JSON, forms, uploads, streaming, SSE - is available.

`withLogin()` and `withPrivileges(mask)` are enforced by the host before your
handler runs: guests get 401 (or a redirect on pages), and a player without the
privileges gets 403.

## Vue components and slots

`registry.components("/vue")` picks up every `.vue` file under `/vue` in the jar.
The files use the same format as the site's own components: a `<template id="...">`
plus a `<script>` calling `app.component(...)`. Names are namespaced by
convention only - prefix them with your plugin id to avoid collisions; a name a
core component already uses is refused.

Slots are opened by the core pages and rendered in registration order:

| Slot constant | Where |
| --- | --- |
| `Slots.NAV_LINKS`, `NAV_ACTIONS` | inside the navigation bar |
| `Slots.PAGE_TOP`, `PAGE_BOTTOM` | every page, right under the nav / above the footer |
| `Slots.HOME_TOP`, `HOME_BOTTOM` | front page |
| `Slots.PROFILE_TOP`, `PROFILE_BOTTOM` | player profile |
| `Slots.LEADERBOARD_TOP`, `LEADERBOARD_BOTTOM` | ranking |
| `Slots.BEATMAPS_TOP`, `BEATMAPS_BOTTOM` | beatmap search |
| `Slots.BEATMAPSET_TOP`, `BEATMAPSET_BOTTOM` | one beatmap set |
| `Slots.LOGIN_TOP`, `LOGIN_BOTTOM` | login page |
| `Slots.FOOTER_LINKS`, `FOOTER_BOTTOM` | footer |

### The navigation bar

The bar and the footer are built from one list: the core entries plus the ones
plugins added, sorted by `order`. Every entry has an id, so a plugin can hide one
instead of adding a second link next to it:

```java
registry.hideNavItem("community")
        .navItem(PluginNavItem.of("Our Discord", "https://discord.gg/...").asExternal().withOrder(30));
```

Ids of the core entries: `leaderboard`, `beatmaps`, `community`, `api` in the bar,
and `footer.leaderboard`, `footer.beatmaps`, `footer.discord`, `footer.github`,
`footer.api` in the footer. An entry contributed by a plugin has the id
`pluginId:href`, so plugins can hide each other's links too. Unknown ids are
ignored, and an entry whose link is not configured (an empty Discord url, say)
drops out on its own.

Your own pages can open slots too: put `<koneko-slot name="my-plugin.top">` in
your component and another plugin can fill it.

Props passed with `PluginSlot.withProps(map)` arrive as normal props; the
surrounding page hands its own data to every slot component as the
`slot-context` prop.

## Data in the browser

The bootstrap state of every page carries a `plugins` section:

```js
koneko.plugins = {
  ids: ["my-plugin"],
  nav: [ { pluginId, label, href, order, external, footer } ],
  slots: { "home.top": [ { pluginId, component, order, props } ] },
  data: { "my-plugin": { upcoming: [...] } }
}
```

Inside any component:

| Helper | Returns |
| --- | --- |
| `this.pluginData("my-plugin", "upcoming")` | a value from `registry.state(...)` |
| `this.pluginAsset("my-plugin", "/my.css")` | url of a file in the jar |
| `this.pluginAction("my-plugin", "/vote")` | url of one of your endpoints |
| `this.pluginNav("nav" \| "footer")` | navigation entries of all plugins |
| `this.pluginNavHidden()` | ids of the entries plugins asked to hide |
| `this.pluginLoaded("other-plugin")` | whether another plugin is running |

To add fields to the site's existing `/data/*` answers, listen to `Events.Data`
and write into `event.body()`; the key is `home`, `profile`, `scores`,
`leaderboard`, `beatmaps` or `beatmapset`. The cached body is copied before your
listener sees it, so nothing you write leaks into the FastLoad cache.

## Talking to bancho.jar

```java
JsonNode top = koneko.api().get("/api/v1/get_leaderboard", Map.of("mode", "0"));
JsonNode mine = koneko.api().getAsUser(ctx, "/api/v1/get_player_info", Map.of("scope", "all"));
JsonNode any  = koneko.api().request("PATCH", "/api/v1/whatever", Map.of(), json, "application/json", token);
```

`quietly(...)` returns `null` instead of throwing, which is what the site itself
uses for optional blocks. `getAsUser` / `postFormAsUser` / `postJsonAsUser` send
the access token of the logged in player, so a plugin never needs to store
credentials. The backend needs no change for any of this; if a plugin needs an
endpoint bancho.jar does not have, that endpoint is the only thing worth adding
there.

## Settings, storage, cache

`config.yml` of the site:

```yaml
plugins:
  enabled: true
  disabled: []
  settings:
    my-plugin:
      greeting: "Welcome"
      show_on_home: true
```

The same keys may live in `plugins/config/my-plugin.yml` instead (values in
`config.yml` win). Read them with `context().settings()`:
`string/integer/number/bool/list/map/all/as(MyConfig.class)`.

`context().dataDir()` is a private folder for the plugin, created on first use.
`context().cache()` is the site's FastLoad cache, namespaced to the plugin:
`cache().get("top", () -> expensive())` obeys the same TTL as the rest of the
site and is a no-op cache when FastLoad is off.

## .env keys

| Key | Default | Meaning |
| --- | --- | --- |
| `PLUGINS_ENABLED` | `true` | `false` starts the site with no plugin at all |
| `PLUGINS_DIR` | `plugins` | where the jars live |
| `PLUGINS_DISABLED` | empty | plugin ids to load but not start, comma separated |
| `PLUGIN_ASSET_CACHE_SECONDS` | `3600` | browser cache of plugin assets, ignored in DEV |

## Development

Run the site with `LEVEL=DEV`: plugin `.vue` files and assets are re-read on
every request, so a rebuilt jar shows up with a restart and nothing is cached in
between. `GET /data/plugins` lists what is loaded, with id, version, provider,
state and the pages, actions and slots each plugin contributed.

## Failure behaviour

A plugin can never take the site down:

* a jar that fails to load, start or register is logged and skipped;
* an exception in a listener, job or state contributor is logged, the page still
  renders;
* a duplicate page path, component name or reserved path is refused with a log
  line;
* `PLUGINS_ENABLED=false` or `PLUGINS_DISABLED=id` is always a way back in.
