<template id="home-view">
    <div class="page">
        <site-nav></site-nav>

        <section class="hero">
            <h1>{{ site.name }}</h1>
            <p class="hero-description">{{ site.description }}</p>

            <div class="hero-actions">
                <a class="button" v-if="!user" href="/login">Log in</a>
                <a class="button" v-else :href="'/u/' + user.id">My profile</a>

                <a class="button button-ghost"
                   v-if="site.links && site.links.discord"
                   :href="site.links.discord" target="_blank" rel="noopener">Join the Discord</a>
            </div>
        </section>

        <section class="stats" v-if="home.showStats">
            <stat-card label="Online" :value="stat('online')"></stat-card>
            <stat-card label="Players" :value="stat('players')"></stat-card>
            <stat-card label="Beatmaps" :value="stat('beatmaps')"></stat-card>
            <stat-card label="Scores" :value="stat('scores')"></stat-card>
        </section>

        <section class="card" v-if="home.showLeaderboard">
            <h2>Top players</h2>

            <p class="muted" v-if="loading">Loading...</p>
            <p class="muted" v-else-if="!leaderboard.length">No ranked players yet.</p>

            <table class="table" v-else>
                <thead>
                    <tr>
                        <th>#</th>
                        <th>Player</th>
                        <th>pp</th>
                        <th>Accuracy</th>
                        <th>Playcount</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="(row, index) in leaderboard" :key="row.id || index">
                        <td>{{ row.rank || index + 1 }}</td>
                        <td><a :href="'/u/' + row.id">{{ row.name }}</a></td>
                        <td>{{ fmtNumber(row.pp) }}</td>
                        <td>{{ fmtAccuracy(row.acc) }}</td>
                        <td>{{ fmtNumber(row.plays) }}</td>
                    </tr>
                </tbody>
            </table>
        </section>

        <section class="card" v-if="home.showConnectGuide">
            <h2>How to connect</h2>
            <ol class="guide">
                <li>Create an account with <code>!register</code> in game, or ask on the Discord.</li>
                <li>Start osu! with <code>osu!.exe -devserver {{ domain }}</code>.</li>
                <li>Log in with your {{ site.name }} username and password.</li>
            </ol>
        </section>

        <site-footer></site-footer>
    </div>
</template>

<script>
    app.component("home-view", {
        template: "#home-view",
        data: () => ({
            loading: true,
            stats: null,
            leaderboard: []
        }),
        computed: {
            site() {
                return this.$koneko.site || {};
            },
            home() {
                return (this.$koneko.site && this.$koneko.site.home) || {};
            },
            user() {
                return this.$koneko.user;
            },
            domain() {
                return this.$koneko.domain;
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
                this.leaderboard = (body.leaderboard && body.leaderboard.results) || [];
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
