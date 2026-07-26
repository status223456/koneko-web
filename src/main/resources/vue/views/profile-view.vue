<template id="profile-view">
    <div class="page">
        <site-nav></site-nav>

        <p class="muted" v-if="loading">Loading the profile...</p>

        <section class="card" v-else-if="error">
            <h2>Nothing here</h2>
            <p class="muted">{{ error }}</p>
        </section>

        <template v-else>
            <section class="mode-bar">
                <div class="mode-tabs">
                    <button class="mode-tab" v-for="tab in modes" :key="tab.id"
                        :class="{ active: base === tab.id }" @click="pickBase(tab.id)">
                        {{ tab.label }}
                    </button>
                </div>

                <div class="mode-variants">
                    <button class="mode-variant" :class="{ active: variant === 'relax' }"
                        :disabled="!relaxAllowed" @click="pickVariant('relax')">RELAX</button>
                    <button class="mode-variant" :class="{ active: variant === 'autopilot' }"
                        :disabled="!autopilotAllowed" @click="pickVariant('autopilot')">AUTOPILOT</button>
                </div>
            </section>

            <section class="card profile-card">
                <div class="profile-top">
                    <img class="profile-avatar" :src="avatarUrl" :alt="info.name">

                    <div class="profile-identity">
                        <h1 class="profile-name">{{ info.name }}</h1>

                        <div class="profile-ranks">
                            <div class="profile-rank">
                                <span class="profile-rank-label">Rank</span>
                                <span class="profile-rank-value">{{ fmtRank(stats.rank) }}</span>
                            </div>

                            <div class="profile-rank">
                                <span class="profile-rank-label">
                                    Country Rank
                                    <span class="country-tag" v-if="info.country">
                                        {{ info.country.toUpperCase() }}
                                    </span>
                                </span>
                                <span class="profile-rank-value">{{ fmtRank(stats.country_rank) }}</span>
                            </div>
                        </div>

                        <div class="grade-counts">
                            <div class="grade-count" v-for="grade in grades" :key="grade.cls">
                                <span class="grade-mark" :class="'grade-' + grade.cls">{{ grade.mark }}</span>
                                <span class="grade-value">{{ fmtNumber(grade.value) }}</span>
                            </div>
                        </div>

                        <div class="level-row">
                            <span class="level-badge">{{ stats.level || 1 }}</span>
                            <span class="level-bar">
                                <span class="level-fill" :style="{ width: levelProgress + '%' }"></span>
                            </span>
                        </div>
                    </div>

                    <dl class="profile-facts">
                        <div><dt>Joined:</dt><dd>{{ fmtRelative(info.creation_time) }}</dd></div>
                        <div><dt>Last Activity:</dt><dd>{{ fmtRelative(info.latest_activity) }}</dd></div>
                        <div><dt>PP:</dt><dd>{{ fmtNumber(stats.pp) }}</dd></div>
                        <div><dt>Playcount:</dt><dd>{{ fmtNumber(stats.plays) }}</dd></div>
                        <div><dt>Replay views:</dt><dd>{{ fmtNumber(stats.replay_views) }}</dd></div>
                        <div><dt>Total Score:</dt><dd>{{ fmtNumber(stats.tscore) }}</dd></div>
                        <div><dt>Ranked Score:</dt><dd>{{ fmtNumber(stats.rscore) }}</dd></div>
                        <div><dt>Max Combo:</dt><dd>{{ fmtNumber(stats.max_combo) }}</dd></div>
                        <div><dt>Total Hits:</dt><dd>{{ fmtNumber(stats.total_hits) }}</dd></div>
                        <div><dt>Accuracy:</dt><dd>{{ fmtAccuracy(stats.acc) }}</dd></div>
                        <div class="played-for">
                            <dt>Played for:</dt>
                            <dd>{{ fmtPlaytime(stats.playtime) }}</dd>
                        </div>
                    </dl>
                </div>

                <div class="profile-followers">
                    {{ fmtNumber(info.followers || 0) }} Followers
                </div>
            </section>

            <section class="card">
                <h2>Best scores</h2>
                <p class="muted" v-if="!best.length">No scores yet.</p>

                <score-row v-for="(score, i) in best" :key="score.id"
                    :score="score" :index="i"></score-row>

                <button class="load-more" v-if="canLoad('best')" :disabled="busy.best"
                    @click="loadMore('best')">Load more</button>
            </section>

            <section class="card">
                <h2>Last scores</h2>
                <p class="muted" v-if="!recent.length">No scores yet.</p>

                <score-row v-for="score in recent" :key="score.id" :score="score"></score-row>

                <button class="load-more" v-if="canLoad('recent')" :disabled="busy.recent"
                    @click="loadMore('recent')">Load more</button>
            </section>

            <section class="card">
                <h2>First places <span class="muted" v-if="counts.first">({{ fmtNumber(counts.first) }})</span></h2>
                <p class="muted" v-if="!firstPlaces.length">No number one scores yet.</p>

                <score-row v-for="score in firstPlaces" :key="score.id" :score="score"></score-row>

                <button class="load-more" v-if="canLoad('first')" :disabled="busy.first"
                    @click="loadMore('first')">Load more</button>
            </section>

            <section class="card">
                <h2>Playcount graph</h2>
                <playcount-graph :months="playcounts"></playcount-graph>
            </section>

            <section class="card">
                <h2>Submitted beatmaps</h2>
                <p class="muted" v-if="!beatmapsets.length">
                    This player has not submitted any beatmaps yet.
                </p>

                <div class="mapset" v-for="set in beatmapsets" :key="set.set_id">
                    <div class="mapset-head">
                        <a class="mapset-title" :href="'/beatmapsets/' + set.set_id">
                            {{ set.artist }} - {{ set.title }}
                        </a>
                        <span class="badge">{{ statusName(set.status) }}</span>
                    </div>

                    <div class="mapset-meta">
                        <span>set #{{ set.set_id }}</span>
                        <span>{{ (set.difficulties || []).length }} difficulties</span>
                        <span>updated {{ fmtDate(set.last_update) }}</span>
                    </div>
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
            identifier: "",
            player: null,
            // 0-3 is the plain mode, the variant shifts it the way the
            // server numbers them: +4 for relax, 8 for autopilot.
            base: 0,
            variant: "",
            best: [],
            recent: [],
            firstPlaces: [],
            playcounts: [],
            beatmapsets: [],
            counts: { best: 0, recent: 0, first: 0 },
            busy: { best: false, recent: false, first: false },
            modes: [
                { id: 0, label: "osu!" },
                { id: 1, label: "taiko" },
                { id: 2, label: "catch" },
                { id: 3, label: "mania" }
            ]
        }),
        computed: {
            mode() {
                if (this.variant === "autopilot") return 8;
                if (this.variant === "relax") return this.base + 4;

                return this.base;
            },
            // Relax has no mania, autopilot is standard only.
            relaxAllowed() {
                return this.base !== 3;
            },
            autopilotAllowed() {
                return this.base === 0;
            },
            info() {
                return (this.player && this.player.info) || {};
            },
            stats() {
                const stats = this.player && this.player.stats;

                if (!stats) return {};

                return stats[String(this.mode)] || {};
            },
            levelProgress() {
                const progress = Number(this.stats.level_progress || 0);

                return Math.max(0, Math.min(100, progress));
            },
            isSelf() {
                return this.$koneko.user && this.$koneko.user.id === this.info.id;
            },
            avatarUrl() {
                return "https://a." + this.$koneko.domain + "/" + (this.info.id || 0);
            }
        },
        methods: {
            pickBase(id) {
                if (this.base === id) return;

                this.base = id;

                // Keep the variant only where it exists for the new mode.
                if (this.variant === "relax" && !this.relaxAllowed) this.variant = "";
                if (this.variant === "autopilot" && !this.autopilotAllowed) this.variant = "";

                this.reload();
            },
            pickVariant(name) {
                this.variant = this.variant === name ? "" : name;
                this.reload();
            },
            listOf(kind) {
                if (kind === "best") return this.best;
                if (kind === "first") return this.firstPlaces;

                return this.recent;
            },
            canLoad(kind) {
                return this.listOf(kind).length < Number(this.counts[kind] || 0);
            },
            async loadMore(kind) {
                if (this.busy[kind]) return;

                this.busy[kind] = true;

                try {
                    const query = new URLSearchParams({
                        scope: kind,
                        mode: String(this.mode),
                        offset: String(this.listOf(kind).length)
                    });

                    const response = await fetch("/data/scores/"
                        + encodeURIComponent(this.identifier) + "?" + query.toString());

                    if (!response.ok) return;

                    const body = await response.json();
                    const page = (body.scores && body.scores.results) || [];

                    this.listOf(kind).push(...page);
                } catch (e) {
                    // A failed page just leaves the button in place.
                } finally {
                    this.busy[kind] = false;
                }
            },
            apply(body) {
                this.player = body.player;

                this.best = (body.best && body.best.results) || [];
                this.recent = (body.recent && body.recent.results) || [];
                this.firstPlaces = (body.firstPlaces && body.firstPlaces.results) || [];
                this.beatmapsets = (body.beatmapsets && body.beatmapsets.results) || [];
                this.playcounts = (body.playcounts && body.playcounts.months) || [];

                this.counts = {
                    best: (body.best && body.best.count) || this.best.length,
                    recent: (body.recent && body.recent.count) || this.recent.length,
                    first: (body.firstPlaces && body.firstPlaces.count) || this.firstPlaces.length
                };

                this.setTitle(this.info.name);
            },
            async load() {
                const key = "profile:" + this.identifier.toLowerCase() + ":" + this.mode;
                const cached = this.fastLoad(key);

                if (cached) {
                    this.apply(cached);
                    this.loading = false;
                }

                try {
                    const response = await fetch("/data/profile/"
                        + encodeURIComponent(this.identifier) + "?mode=" + this.mode);

                    const body = await response.json();

                    if (!response.ok) {
                        if (!cached) {
                            this.error = body.status || "This player does not exist.";
                            this.setTitle("Not found");
                        }

                        return;
                    }

                    this.apply(body);
                    this.fastSave(key, body);
                } catch (e) {
                    if (!cached) {
                        this.error = "The server could not be reached.";
                    }
                } finally {
                    this.loading = false;
                }
            },
            reload() {
                this.loading = !this.player;
                this.load();
            }
        },
        created() {
            this.identifier = decodeURIComponent(window.location.pathname.split("/").pop());

            const mode = Number(new URLSearchParams(window.location.search).get("mode") || 0);

            if (mode === 8) {
                this.variant = "autopilot";
            } else if (mode >= 4 && mode <= 6) {
                this.base = mode - 4;
                this.variant = "relax";
            } else if (mode >= 1 && mode <= 3) {
                this.base = mode;
            }

            this.load();
        }
    });
</script>
