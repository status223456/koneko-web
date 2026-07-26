# koneko-web

The web frontend for a [bancho.jar](https://github.com/status223456/bancho.jar)
server: Java 25, Gradle, Javalin 7 and Vue 3.

Pages are assembled the way the JavalinVue plugin used to do it - one HTML
layout plus one `.vue` file per component - by `vue/KonekoVue.java`, because the
plugin itself does not exist in Javalin 7 any more.

This project renders pages and talks to the bancho.jar public API. It has no
database and no Redis of its own: every piece of data comes from
`https://api.<domain>/api/v1/...`.

## What is implemented

| Page | Route | Data |
| --- | --- | --- |
| Front page | `/` | `get_server_stats`, `get_leaderboard` |
| Login | `/login` | `oauth/token` (password grant) |
| Profile | `/u/{id or name}` | `get_player_details`, `get_player_scores`, `get_player_beatmapsets`, `get_player_most_played` |
| Own profile | `/me` | redirects to `/u/<your id>` |

## First clone

```
cp .env.example .env
cp config.example.yml config.yml
```

Both `.env` and `config.yml` are gitignored: the repository only ever holds the
`.example` versions, filled with placeholders. Nothing in the repo contains a
real domain, host or secret.

## Configuration

Two files, on purpose:

- **`.env`** - deployment settings (port, domain, API url, timeouts). Same
  style as bancho.jar. Copy `.env.example` to `.env`.
- **`config.yml`** - the texts of the site: server name, front page
  description and the Discord link. Copy `config.example.yml` to `config.yml`.

## Running

On Windows, double click or run from a terminal:

| Script | What it does |
| --- | --- |
| `build.bat` | `gradlew shadowJar` - produces `build\libs\koneko-web-shaded.jar` |
| `run.bat` | creates `.env` and `config.yml` if missing, builds if needed, then starts the jar |
| `dev.bat` | `gradlew run`, for development against the sources |
| `clean.bat` | `gradlew clean` |

On Linux and macOS the same four commands are `make build`, `make run`,
`make dev` and `make clean`.

By hand:

```
cp .env.example .env
cp config.example.yml config.yml
./gradlew shadowJar
java -jar build/libs/koneko-web-shaded.jar
```

The Gradle wrapper is included (Gradle 9.0.0, the same one bancho.jar uses),
so no local Gradle installation is needed - only a JDK 25.

## Putting it behind nginx

`nginx/koneko-web.conf` serves the apex domain from this app while every
subdomain the game client uses stays on bancho.jar:

| Name | Goes to |
| --- | --- |
| `example.com` | koneko-web, `127.0.0.1:8300` |
| `www.example.com` | redirect to the apex |
| `c*.example.com`, `osu.`, `api.` | bancho.jar, `127.0.0.1:8200` |

The apex has to be **removed** from the `server_name` list of the bancho.jar
block first - two blocks cannot claim the same name. The wildcard certificate
already covers everything, so nothing new has to be issued.

During development set `LEVEL=DEV` in `.env` and use `./gradlew run`: the
`.vue` files are then read from disk on every request, so a browser refresh is
enough to see changes, and session cookies are not marked `Secure` so plain
`http://localhost:8300` works.

## How the login works

The browser never talks to the API directly - that would need CORS with
credentials, and the API answers `Access-Control-Allow-Origin: *`, which
browsers refuse to combine with cookies. Instead this app is a small
backend-for-frontend:

1. The browser posts the username and password to `POST /auth/login` here.
2. koneko-web calls `POST /api/v1/oauth/token` (password grant) server side
   and receives the access/refresh pair.
3. The pair is kept in memory, server side, and the browser only gets an
   opaque `koneko_session` cookie (HttpOnly, SameSite=Lax).
4. Access tokens are refreshed automatically when they are about to expire.
   `POST /auth/logout` revokes the refresh token, which takes the whole chain
   down, and drops the session.

Because the token pair never reaches the browser, an XSS on this frontend
cannot walk away with an API token.

## Layout

```
src/main/java/com/osuserverlist/koneko/
  App.java              bootstrap: env, config, Javalin, KonekoVue
  config/               .env and config.yml loading
  api/                  HTTP client for the bancho.jar API
  auth/                 server side sessions and token refresh
  routes/               page routes, /auth/*, /data/*
  vue/                  the KonekoVue state function
src/main/resources/
  vue/layout.html       the single HTML layout KonekoVue serves
  vue/components/*.vue  navigation, footer, small widgets
  vue/views/*.vue       one file per route
  public/css/koneko.css styling
```

## Notes

- Nothing here is compiled yet in CI; run `build.bat` (or `./gradlew
  shadowJar`) once before deploying.
- The Vue version is pinned in `layout.html`. Vue 3 is used with the
  `vueAppName` option, which is what KonekoVue expects for Vue 3.
