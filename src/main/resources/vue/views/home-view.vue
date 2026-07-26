<template id="home-view">
    <div class="page">
        <site-nav></site-nav>

        <div class="announcement" v-if="home.announcement">
            <span class="announcement-icon">i</span>
            <span>{{ home.announcement }}</span>
            <a v-if="home.announcementLink" :href="home.announcementLink"
               target="_blank" rel="noopener">here!</a>
        </div>

        <section class="hero-band" :style="heroStyle">
            <div class="hero-inner">
                <div class="hero-logo">
                    <img v-if="home.logoImage" :src="home.logoImage" :alt="site.name">
                    <span v-else>{{ initials }}</span>
                </div>

                <h1 class="hero-title">Welcome to {{ site.name }}</h1>
                <p class="hero-tagline" v-if="site.tagline">{{ site.tagline }}</p>

                <div class="hero-actions">
                    <a class="button" v-if="!user" href="/login">Get started</a>
                    <a class="button" v-else :href="'/u/' + user.id">My profile</a>

                    <a class="button button-ghost" v-if="home.showConnectGuide" href="#connect">How to connect</a>
                    <a class="button button-ghost" v-else-if="links.discord"
                       :href="links.discord" target="_blank" rel="noopener">Join the Discord</a>
                </div>
            </div>
        </section>

        <section class="stat-strip" v-if="home.showStats">
            <div class="stat-strip-item">
                <span class="stat-strip-value">{{ fmtNumber(stat('players')) }}</span>
                <span class="stat-strip-label">Registered players</span>
            </div>
            <div class="stat-strip-item">
                <span class="stat-strip-value">{{ fmtNumber(stat('online')) }}</span>
                <span class="stat-strip-label">Online now</span>
            </div>
            <div class="stat-strip-item">
                <span class="stat-strip-value">{{ fmtNumber(stat('beatmaps')) }}</span>
                <span class="stat-strip-label">Beatmaps</span>
            </div>
            <div class="stat-strip-item">
                <span class="stat-strip-value">{{ fmtNumber(stat('scores')) }}</span>
                <span class="stat-strip-label">Total scores</span>
            </div>
        </section>

        <section class="tiles">
            <a class="tile" :href="home.showConnectGuide ? '#connect' : '/login'">
                <span class="tile-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                        <path d="M8 5l11 7-11 7z" stroke-linejoin="round"></path>
                    </svg>
                </span>
                <span class="tile-title">Play</span>
                <span class="tile-text">Connect to {{ site.name }} and start playing today.</span>
            </a>

            <a class="tile" href="/leaderboard">
                <span class="tile-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                        <path d="M8 4h8v5a4 4 0 01-8 0z" stroke-linejoin="round"></path>
                        <path d="M16 5h3v2a3 3 0 01-3 3M8 5H5v2a3 3 0 003 3"></path>
                        <path d="M12 13v4M9 20h6" stroke-linecap="round"></path>
                    </svg>
                </span>
                <span class="tile-title">Leaderboard</span>
                <span class="tile-text">Compare with other players and climb the rankings.</span>
            </a>

            <a class="tile" href="/beatmaps">
                <span class="tile-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                        <circle cx="12" cy="12" r="8"></circle>
                        <circle cx="12" cy="12" r="2.5"></circle>
                    </svg>
                </span>
                <span class="tile-title">Beatmaps</span>
                <span class="tile-text">Browse every map on the server, ranked and submitted.</span>
            </a>

            <a class="tile" v-if="links.discord" :href="links.discord" target="_blank" rel="noopener">
                <span class="tile-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                        <circle cx="9" cy="9" r="3"></circle>
                        <path d="M3.5 19a5.5 5.5 0 0111 0" stroke-linecap="round"></path>
                        <path d="M16 7a3 3 0 010 6M17 19a5.5 5.5 0 00-1.6-3.9" stroke-linecap="round"></path>
                    </svg>
                </span>
                <span class="tile-title">Community</span>
                <span class="tile-text">Meet the team and join our growing community.</span>
            </a>
        </section>

        <section class="card" id="connect" v-if="home.showConnectGuide">
            <h2>How to connect</h2>

            <p class="hero-description">{{ site.description }}</p>

            <ol class="guide">
                <li>Create an account with <code>!register</code> in game, or ask on the Discord.</li>
                <li>Start osu! with <code>osu!.exe -devserver {{ domain }}</code>.</li>
                <li>Log in with your {{ site.name }} username and password.</li>
            </ol>
        </section>

        <section class="card" v-else>
            <h2>About {{ site.name }}</h2>
            <p class="hero-description">{{ site.description }}</p>
        </section>

        <site-footer></site-footer>
    </div>
</template>

<script>
    app.component("home-view", {
        template: "#home-view",
        data: () => ({
            loading: true,
            stats: null
        }),
        computed: {
            site() {
                return this.$koneko.site || {};
            },
            home() {
                return this.site.home || {};
            },
            links() {
                return this.site.links || {};
            },
            user() {
                return this.$koneko.user;
            },
            domain() {
                return this.$koneko.domain;
            },
            initials() {
                const name = String(this.site.name || "").trim();

                if (!name) return "?";

                return name.split(/\s+/).slice(0, 2)
                    .map(word => word.charAt(0).toUpperCase()).join("");
            },
            // The band keeps its gradient when no image is configured, so the
            // front page looks finished out of the box.
            heroStyle() {
                const image = this.home.heroImage;

                return image ? { backgroundImage: "url('" + image + "')" } : {};
            }
        },
        methods: {
            // DataRoutes already normalises the counter names, so a missing
            // section is the only case left to handle.
            stat(name) {
                return this.stats ? this.stats[name] : null;
            },
            apply(body) {
                this.stats = body.stats || null;
            }
        },
        async created() {
            this.setTitle(null);

            // FastLoad: paint the numbers of the last visit right away, so
            // the page is never empty while the API answer is on its way.
            const cached = this.fastLoad("home");

            if (cached) {
                this.apply(cached);
                this.loading = false;
            }

            try {
                const response = await fetch("/data/home");
                const body = await response.json();

                this.apply(body);
                this.fastSave("home", body);
            } catch (e) {
                // The cached page stays on screen when the API is down.
            } finally {
                this.loading = false;
            }
        }
    });
</script>
