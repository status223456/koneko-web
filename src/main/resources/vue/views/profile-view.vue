<template id="profile-view">
    <div class="page">
        <site-nav></site-nav>

        <koneko-slot name="profile.top"></koneko-slot>
        <koneko-slot name="page.top"></koneko-slot>

        <section class="card" v-if="error">
            <h2>Nothing here</h2>
            <p class="muted">{{ error }}</p>
        </section>

        <!-- The shell is painted at once and every card below fills itself in
             when its own answer lands, so nothing waits for the slowest one. -->
        <template v-else>
            <section class="mode-bar">
                <div class="mode-tabs">
                    <button class="mode-tab" v-for="tab in modes" :key="tab.id"
                        :class="{ active: base === tab.id }" @click="pickBase(tab.id)">
                        {{ tab.label }}
                    </button>
                </div>

                <div class="mode-variants">
                    <button class="mode-variant" :class="{ active: !variant }"
                        @click="pickVariant('')">Vanilla</button>
                    <button class="mode-variant" :class="{ active: variant === 'relax' }"
                        :disabled="!relaxAllowed" @click="pickVariant('relax')">Relax</button>
                    <button class="mode-variant" :class="{ active: variant === 'autopilot' }"
                        :disabled="!autopilotAllowed" @click="pickVariant('autopilot')">Autopilot</button>
                </div>
            </section>

            <section class="card profile-card">
                <div class="skeleton-profile" v-if="pending.header">
                    <div class="skeleton skeleton-avatar"></div>

                    <div class="skeleton-lines">
                        <div class="skeleton skeleton-line skeleton-title"></div>
                        <div class="skeleton skeleton-line skeleton-line-wide"></div>
                        <div class="skeleton skeleton-line"></div>
                        <div class="skeleton skeleton-line skeleton-line-short"></div>
                    </div>
                </div>

                <div class="profile-top" v-else>
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
                                    <span class="country-tag" v-if="flagClass(info.country)"
                                        :class="flagClass(info.country)"
                                        :title="info.country.toUpperCase()"></span>
                                </span>
                                <span class="profile-rank-value">{{ fmtRank(stats.country_rank) }}</span>
                            </div>

                            <div class="profile-rank profile-rank-followers" v-if="!pending.header">
                                <span class="profile-rank-label">Followers</span>
                                <span class="profile-rank-value">{{ fmtNumber(info.followers || 0) }}</span>
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
            </section>

            <section class="card">
                <h2>Best scores</h2>

                <div class="skeleton-rows" v-if="pending.best">
                    <div class="skeleton skeleton-row" v-for="n in 5" :key="n"></div>
                </div>

                <template v-else>
                    <p class="muted" v-if="!best.length">No scores yet.</p>

                    <score-row v-for="(score, i) in best" :key="score.id"
                        :score="score" :index="i"></score-row>

                    <button class="load-more" v-if="canLoad('best')" :disabled="busy.best"
                        @click="loadMore('best')">Load more</button>
                </template>
            </section>

            <section class="card">
                <h2>Last scores</h2>

                <div class="skeleton-rows" v-if="pending.recent">
                    <div class="skeleton skeleton-row" v-for="n in 5" :key="n"></div>
                </div>

                <template v-else>
                    <p class="muted" v-if="!recent.length">No scores yet.</p>

                    <score-row v-for="score in recent" :key="score.id" :score="score"></score-row>

                    <button class="load-more" v-if="canLoad('recent')" :disabled="busy.recent"
                        @click="loadMore('recent')">Load more</button>
                </template>
            </section>

            <section class="card">
                <h2>First places <span class="muted" v-if="counts.first">({{ fmtNumber(counts.first) }})</span></h2>

                <div class="skeleton-rows" v-if="pending.first">
                    <div class="skeleton skeleton-row" v-for="n in 3" :key="n"></div>
                </div>

                <template v-else>
                    <p class="muted" v-if="!firstPlaces.length">No number one scores yet.</p>

                    <score-row v-for="score in firstPlaces" :key="score.id" :score="score"></score-row>

                    <button class="load-more" v-if="canLoad('first')" :disabled="busy.first"
                        @click="loadMore('first')">Load more</button>
                </template>
            </section>

            <section class="card">
                <h2>Playcount graph</h2>

                <div class="skeleton skeleton-graph" v-if="pending.playcounts"></div>
                <playcount-graph v-else :months="playcounts"></playcount-graph>
            </section>

            <section class="card" v-if="pending.medals || achievements.length">
                <h2>
                    Medals
                    <span class="muted" v-if="!pending.medals">({{ medals.length }} / {{ medalTotal }})</span>
                </h2>

                <div class="skeleton-medals" v-if="pending.medals">
                    <div class="skeleton skeleton-medal" v-for="n in 12" :key="n"></div>
                </div>

                <p class="muted" v-else-if="!medals.length">No medals yet.</p>

                <div class="medals" v-else>
                    <div class="medal" v-for="medal in medals" :key="medal.id">
                        <img class="medal-icon" :src="medalIcon(medal.file)"
                            :alt="medal.name" loading="lazy">

                        <div class="medal-tip">
                            <span class="medal-name">{{ medal.name }}</span>
                            <span class="medal-desc">{{ medal.description }}</span>
                        </div>
                    </div>
                </div>
            </section>

            <section class="card">
                <h2>Most played</h2>

                <div class="skeleton-rows" v-if="pending.mostPlayed">
                    <div class="skeleton skeleton-row" v-for="n in 3" :key="n"></div>
                </div>

                <p class="muted" v-else-if="!mostPlayed.length">
                    No plays in this mode yet.
                </p>

                <div class="played" v-for="map in mostPlayed" :key="map.map_id" v-else>
                    <a class="played-title" :href="'/beatmapsets/' + map.set_id">
                        {{ map.artist }} - {{ map.title }}
                        <span class="muted">[{{ map.version }}]</span>
                    </a>

                    <span class="played-count">{{ fmtNumber(map.playcount) }}x</span>
                </div>
            </section>

            <section class="card">
                <h2>Submitted beatmaps</h2>

                <div class="skeleton-rows" v-if="pending.beatmapsets">
                    <div class="skeleton skeleton-row" v-for="n in 2" :key="n"></div>
                </div>

                <p class="muted" v-else-if="!beatmapsets.length">
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

                <koneko-slot name="page.bottom"></koneko-slot>
        <koneko-slot name="profile.bottom"></koneko-slot>

        <site-footer></site-footer>
    </div>
</template>

<script>
    app.component("profile-view", {
        template: "#profile-view",
        data: () => ({
            error: "",
            identifier: "",
            player: null,
            // One flag per card. Every section fetches itself, shows its own
            // loader and clears its own flag, so the page is usable as soon as
            // the first answer is in instead of when the last one is.
            pending: {
                header: true,
                best: true,
                recent: true,
                first: true,
                playcounts: true,
                mostPlayed: true,
                medals: true,
                beatmapsets: true
            },
            // Raised on every mode switch: answers of the previous run are
            // dropped instead of painted over the new mode.
            generation: 0,
            // The sections as they arrive, kept so the FastLoad copy of the
            // page can be written while the rest is still on its way.
            snapshot: {},
            // How much of a list the first page holds, and how far the
            // playcount graph looks back.
            pageSize: 10,
            playcountMonths: 12,
            // 0-3 is the plain mode, the variant shifts it the way the
            // server numbers them: +4 for relax, 8 for autopilot.
            base: 0,
            variant: "",
            best: [],
            recent: [],
            firstPlaces: [],
            playcounts: [],
            mostPlayed: [],
            beatmapsets: [],
            achievements: [],
            // Medals that exist on the server, reported by the API so the
            // ratio does not have to be counted from the list.
            medalTotal: 0,
            counts: { best: 0, recent: 0, first: 0 },
            busy: { best: false, recent: false, first: false },
            modes: [
                { id: 0, label: "osu!" },
                { id: 1, label: "osu!taiko" },
                { id: 2, label: "osu!catch" },
                { id: 3, label: "osu!mania" }
            ]
        }),
        computed: {
            medals() {
                return this.achievements.filter(medal => medal.unlocked);
            },
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
            // The icons are downloaded by the game server on its first start
            // and served from the assets host, the same place the client
            // takes them from.
            medalIcon(file) {
                return "https://assets." + this.$koneko.domain
                    + "/medals/client/" + file + "@2x.png";
            },
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
                    // First places have their own route, the other two are
                    // scopes of the score route.
                    const endpoint = kind === "first"
                        ? "get_player_first_places"
                        : "get_player_scores";

                    const params = Object.assign({}, this.apiWho(this.identifier), {
                        mode: this.mode,
                        limit: this.pageSize,
                        offset: this.listOf(kind).length
                    });

                    if (kind !== "first") params.scope = kind;

                    const answer = await this.apiQuietly(endpoint, params);
                    const page = (answer && answer.results) || [];

                    this.listOf(kind).push(...page);
                } catch (e) {
                    // A failed page just leaves the button in place.
                } finally {
                    this.busy[kind] = false;
                }
            },
            // The whole page of the last visit at once: a revisit paints filled
            // cards immediately and the fresh answers replace them silently.
            apply(body) {
                this.player = body.player || null;

                this.best = (body.best && body.best.results) || [];
                this.recent = (body.recent && body.recent.results) || [];
                this.firstPlaces = (body.firstPlaces && body.firstPlaces.results) || [];
                this.beatmapsets = (body.beatmapsets && body.beatmapsets.results) || [];
                this.playcounts = (body.playcounts && body.playcounts.months) || [];

                this.achievements = (body.achievements && body.achievements.results) || [];
                this.medalTotal = (body.achievements && body.achievements.total)
                    || this.achievements.length;

                this.counts = {
                    best: (body.best && body.best.count) || this.best.length,
                    recent: (body.recent && body.recent.count) || this.recent.length,
                    first: (body.firstPlaces && body.firstPlaces.count) || this.firstPlaces.length
                };

                this.settled();
                this.setTitle(this.info.name);
            },
            /** Nothing is being waited for any more: drop every loader. */
            settled() {
                Object.keys(this.pending).forEach(name => {
                    this.pending[name] = false;
                });
            },
            cacheKey() {
                return "profile:" + this.identifier.toLowerCase() + ":" + this.mode;
            },
            /**
             * Takes the loader off one card and keeps the FastLoad copy of the
             * page in step: the sections land one by one, so the stored page
             * grows with them instead of being written once at the end.
             */
            settle(name, part, value) {
                this.pending[name] = false;

                if (part) {
                    this.snapshot[part] = value;
                    this.fastSave(this.cacheKey(), this.snapshot);
                }
            },
            notFound() {
                this.error = "This player does not exist.";
                this.setTitle("Not found");
                this.settled();
            },
            /**
             * The player card, and the only section that decides whether the
             * profile exists at all. It is also the only one that is not per
             * mode: the details carry the stats of every mode at once, so a
             * mode switch does not refetch it.
             */
            async loadHeader(run, who) {
                try {
                    const details = await this.api("get_player_details",
                        Object.assign({}, who, { scope: "all" }));

                    if (run !== this.generation) return;

                    const player = (details && details.player) || null;

                    if (!player) {
                        this.notFound();
                        return;
                    }

                    this.player = player;
                    this.setTitle(this.info.name);
                    this.settle("header", "player", player);
                } catch (e) {
                    if (run !== this.generation) return;

                    this.pending.header = false;

                    if (e && e.status === 404) {
                        this.notFound();
                        return;
                    }

                    // A cached player stays on screen; without one there is
                    // nothing to show but the failure.
                    if (!this.player) {
                        this.error = "The server could not be reached.";
                    }
                }
            },
            /**
             * Fetches one card on its own: it carries its own loader, fills
             * itself in when its answer lands, and a failure leaves the rest of
             * the page untouched.
             */
            async loadSection(run, name, part, endpoint, params, apply) {
                const answer = await this.apiQuietly(endpoint, params);

                // A mode switch while this was in flight makes the answer
                // stale, so it is dropped rather than painted.
                if (run !== this.generation) return;

                apply(answer);
                this.settle(name, part, answer);
            },
            load() {
                // Everything an earlier run may still deliver is stale now.
                const run = ++this.generation;

                const who = this.apiWho(this.identifier);
                const paging = { mode: this.mode, limit: this.pageSize };
                const cached = this.fastLoad(this.cacheKey());

                this.snapshot = cached ? Object.assign({}, cached) : {};

                if (cached) {
                    this.apply(cached);
                } else {
                    // The player card survives a mode switch: the numbers it
                    // shows come from details that are already here.
                    this.pending.header = !this.player;
                    this.pending.best = true;
                    this.pending.recent = true;
                    this.pending.first = true;
                    this.pending.playcounts = true;
                    this.pending.mostPlayed = true;
                    this.pending.medals = true;
                    this.pending.beatmapsets = true;
                }

                if (this.player) {
                    this.snapshot.player = this.player;
                } else {
                    this.loadHeader(run, who);
                }

                // The requests all leave at once and are not awaited here:
                // whichever card is ready first is on screen first.
                this.loadSection(run, "best", "best", "get_player_scores",
                    Object.assign({}, who, paging, { scope: "best" }),
                    answer => {
                        this.best = (answer && answer.results) || [];
                        this.counts.best = (answer && answer.count) || this.best.length;
                    });

                this.loadSection(run, "recent", "recent", "get_player_scores",
                    Object.assign({}, who, paging, { scope: "recent" }),
                    answer => {
                        this.recent = (answer && answer.results) || [];
                        this.counts.recent = (answer && answer.count) || this.recent.length;
                    });

                this.loadSection(run, "first", "firstPlaces", "get_player_first_places",
                    Object.assign({}, who, paging),
                    answer => {
                        this.firstPlaces = (answer && answer.results) || [];
                        this.counts.first = (answer && answer.count) || this.firstPlaces.length;
                    });

                this.loadSection(run, "playcounts", "playcounts", "get_player_playcounts",
                    Object.assign({}, who, { mode: this.mode, months: this.playcountMonths }),
                    answer => {
                        this.playcounts = (answer && answer.months) || [];
                    });

                this.loadSection(run, "mostPlayed", "mostPlayed", "get_player_most_played",
                    Object.assign({}, who, paging),
                    answer => {
                        this.mostPlayed = (answer && answer.results) || [];
                    });

                // Medals belong to the player, not to a mode.
                this.loadSection(run, "medals", "achievements", "get_player_achievements", who,
                    answer => {
                        this.achievements = (answer && answer.results) || [];
                        this.medalTotal = (answer && answer.total) || this.achievements.length;
                    });

                this.loadSection(run, "beatmapsets", "beatmapsets", "get_player_beatmapsets",
                    Object.assign({}, who, { limit: this.pageSize }),
                    answer => {
                        this.beatmapsets = (answer && answer.results) || [];
                    });
            },
            reload() {
                this.error = "";
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
