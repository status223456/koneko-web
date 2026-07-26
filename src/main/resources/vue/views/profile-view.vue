<template id="profile-view">
    <div class="page">
        <site-nav></site-nav>

        <p class="muted" v-if="loading">Loading the profile...</p>

        <section class="card" v-else-if="error">
            <h2>Nothing here</h2>
            <p class="muted">{{ error }}</p>
        </section>

        <template v-else>
            <section class="profile-head">
                <img class="avatar" :src="avatarUrl" :alt="info.name">

                <div class="profile-identity">
                    <h1>{{ info.name }}</h1>
                    <div class="profile-meta">
                        <span v-if="info.country">{{ info.country.toUpperCase() }}</span>
                        <span>Joined {{ fmtDate(info.creation_time) }}</span>
                        <span v-if="isSelf" class="badge">This is you</span>
                    </div>
                </div>
            </section>

            <section class="stats" v-if="stats">
                <stat-card label="Global rank" :value="stats.rank"></stat-card>
                <stat-card label="pp" :value="stats.pp"></stat-card>
                <stat-card label="Playcount" :value="stats.plays"></stat-card>
                <stat-card label="Ranked score" :value="stats.rscore"></stat-card>
            </section>

            <section class="card" v-if="info.userpage_content">
                <h2>About</h2>
                <p class="userpage">{{ info.userpage_content }}</p>
            </section>

            <section class="card">
                <h2>Best scores</h2>
                <p class="muted" v-if="!best.length">No scores yet.</p>
                <score-row v-for="score in best" :key="score.id" :score="score"></score-row>
            </section>

            <section class="card">
                <h2>Recent scores</h2>
                <p class="muted" v-if="!recent.length">No scores yet.</p>
                <score-row v-for="score in recent" :key="score.id" :score="score"></score-row>
            </section>

            <section class="card">
                <h2>Submitted beatmaps</h2>
                <p class="muted" v-if="!beatmapsets.length">
                    This player has not submitted any beatmaps yet.
                </p>

                <div class="mapset" v-for="set in beatmapsets" :key="set.set_id">
                    <div class="mapset-head">
                        <span class="mapset-title">{{ set.artist }} - {{ set.title }}</span>
                        <span class="badge">{{ statusName(set.status) }}</span>
                    </div>

                    <div class="mapset-meta">
                        <span>set #{{ set.set_id }}</span>
                        <span>revision {{ set.revision }}</span>
                        <span>{{ (set.difficulties || []).length }} difficulties</span>
                        <span>updated {{ fmtDate(set.last_update) }}</span>
                    </div>

                    <ul class="mapset-diffs">
                        <li v-for="diff in set.difficulties" :key="diff.id">
                            [{{ diff.version }}]
                            <span class="muted">{{ fmtDecimal(diff.diff) }} stars</span>
                        </li>
                    </ul>
                </div>
            </section>
        </template>

        <site-footer></site-footer>
    </div>
</template>

<script>
    app.component("profile-view", {
        template: "#profile-view",
        data: () => ({
            loading: true,
            error: "",
            player: null,
            best: [],
            recent: [],
            beatmapsets: []
        }),
        computed: {
            info() {
                return (this.player && this.player.info) || {};
            },
            stats() {
                // get_player_details returns the stats keyed by mode; the
                // profile shows standard until a mode switcher exists.
                const stats = this.player && this.player.stats;
                if (!stats) return null;
                return stats["0"] || stats[0] || null;
            },
            isSelf() {
                return this.$koneko.user && this.$koneko.user.id === this.info.id;
            },
            avatarUrl() {
                return "https://a." + this.$koneko.domain + "/" + (this.info.id || 0);
            }
        },
        methods: {
            statusName(status) {
                // The bancho.jar ranked status scale (RankedStatus).
                const names = {
                    "-2": "graveyard",
                    "-1": "work in progress",
                    "0": "pending",
                    "1": "ranked",
                    "2": "approved",
                    "3": "qualified",
                    "4": "loved"
                };
                return names[String(status)] || "unknown";
            }
        },
        async created() {
            const identifier = decodeURIComponent(window.location.pathname.split("/").pop());

            try {
                const response = await fetch("/data/profile/" + encodeURIComponent(identifier));
                const body = await response.json();

                if (!response.ok) {
                    this.error = body.status || "This player does not exist.";
                    this.setTitle("Not found");
                    return;
                }

                this.player = body.player;
                this.best = (body.best && body.best.results) || [];
                this.recent = (body.recent && body.recent.results) || [];
                this.beatmapsets = (body.beatmapsets && body.beatmapsets.results) || [];

                this.setTitle(this.info.name);
            } catch (e) {
                this.error = "The server could not be reached.";
            } finally {
                this.loading = false;
            }
        }
    });
</script>
