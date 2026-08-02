<template id="beatmapset-view">
    <div class="page page-wide">
        <site-nav></site-nav>

        <koneko-slot name="beatmapset.top"></koneko-slot>
        <koneko-slot name="page.top"></koneko-slot>

        <p class="muted" v-if="loading">Loading the beatmap...</p>

        <section class="card" v-else-if="error">
            <h2>Nothing here</h2>
            <p class="muted">{{ error }}</p>
            <p><a href="/beatmaps">Back to the beatmap listing</a></p>
        </section>

        <template v-else>
            <header class="listing-head">
                <h1>Beatmap information</h1>
            </header>

            <!-- The whole top of the page is one panel standing on the cover of
                 the set: the difficulty bar, the title block and the stats
                 column, exactly as the game's beatmap page arranges them. -->
            <section class="beatmap-hero">
                <div class="beatmap-hero-cover" :style="coverStyle"></div>

                <div class="beatmap-hero-inner">
                    <!-- The difficulty bar. Picking one only swaps what is shown
                         below and writes the hash, so the page is never reloaded. -->
                    <nav class="diff-bar" v-if="difficulties.length">
                        <button class="diff-orb-button" v-for="diff in difficulties" :key="diff.id"
                            :class="{ active: selected && selected.id === diff.id }"
                            :title="diff.version + ' - ' + fmtDecimal(diff.diff) + ' stars'"
                            @click="select(diff)">
                            <span class="diff-orb" :style="{ borderColor: starColor(diff.diff) }">
                                <span class="diff-orb-core" :style="{ background: starColor(diff.diff) }"></span>
                            </span>
                        </button>
                    </nav>

                    <div class="beatmap-hero-grid">
                        <div class="beatmap-hero-main">
                            <div class="beatmap-hero-tags" v-if="selected">
                                <!-- A set the server never rated carries a diff of
                                     zero. Printing "0.00" would read as "trivially
                                     easy", which is a different claim than "not
                                     known", so the pill says so instead. -->
                                <span class="star-pill" v-if="rated"
                                    :style="{ background: starColor(selected.diff) }">
                                    &#9733; {{ fmtDecimal(selected.diff) }}
                                </span>
                                <span class="star-pill star-pill-unrated" v-else
                                    title="The star rating of this difficulty has not been calculated">
                                    &#9733; not rated
                                </span>
                                <span class="beatmap-hero-version">{{ selected.version }}</span>
                            </div>

                            <div class="beatmap-hero-counts" v-if="selected">
                                <span>&#9654; {{ fmtNumber(selected.plays) }}</span>
                                <span>&#9829; {{ fmtNumber(set.favourites || 0) }}</span>
                            </div>

                            <h2 class="beatmap-title">{{ set.title }}</h2>
                            <p class="beatmap-artist">{{ set.artist }}</p>

                            <div class="beatmap-mapper">
                                <span class="beatmap-mapper-avatar">{{ initial }}</span>
                                <span class="beatmap-mapper-text">
                                    <span>
                                        mapped by
                                        <a v-if="set.creator_id" :href="'/u/' + set.creator_id">{{ set.creator }}</a>
                                        <span v-else>{{ set.creator }}</span>
                                    </span>
                                    <span class="muted small">updated {{ fmtDate(set.last_update) }}</span>
                                    <span class="muted small" v-if="set.revision">revision {{ set.revision }}</span>
                                </span>
                            </div>

                            <div class="hero-actions" v-if="set.hosted">
                                <a class="button" :href="downloadUrl">Download</a>
                                <a class="button button-ghost" v-if="set.has_video"
                                   :href="downloadUrl + '?novideo=1'">Without video</a>
                                <a class="button button-ghost" :href="'osu://dl/' + set.set_id">osu!direct</a>
                            </div>
                        </div>

                        <!-- The right column: the status, the four counters and
                             the difficulty settings as bars. -->
                        <aside class="beatmap-hero-side" v-if="selected">
                            <span class="status-pill status-pill-large" :class="'status-' + statusKey">
                                {{ statusName(set.status) }}
                            </span>

                            <div class="beatmap-counters">
                                <div><span class="muted">Length</span><b>{{ fmtLength(selected.total_length) }}</b></div>
                                <div><span class="muted">BPM</span><b>{{ fmtDecimal(selected.bpm, 0) }}</b></div>
                                <div><span class="muted">Max combo</span><b>{{ fmtNumber(selected.max_combo) }}x</b></div>
                                <div><span class="muted">Mode</span><b>{{ modeName(selected.mode) }}</b></div>
                            </div>

                            <div class="diff-attrs">
                                <div class="diff-attr" v-for="attr in attributes" :key="attr.label">
                                    <span class="diff-attr-label">{{ attr.label }}</span>
                                    <span class="diff-attr-bar">
                                        <span class="diff-attr-fill" :style="{ width: attr.percent + '%' }"></span>
                                    </span>
                                    <span class="diff-attr-value">{{ fmtDecimal(attr.value, 1) }}</span>
                                </div>

                                <div class="diff-attr">
                                    <span class="diff-attr-label">Stars</span>
                                    <span class="diff-attr-bar">
                                        <span class="diff-attr-fill diff-attr-stars"
                                              :style="{ width: starPercent + '%', background: starColor(selected.diff) }"></span>
                                    </span>
                                    <span class="diff-attr-value">{{ rated ? fmtDecimal(selected.diff) : "-" }}</span>
                                </div>
                            </div>

                            <div class="pass-rate">
                                <span class="muted small">Pass rate</span>
                                <span class="pass-bar">
                                    <span class="pass-fill" :style="{ width: passPercent + '%' }"></span>
                                </span>
                                <span class="small">{{ passRate }}
                                    <span class="muted">({{ fmtNumber(selected.passes) }} of
                                        {{ fmtNumber(selected.plays) }})</span>
                                </span>
                            </div>
                        </aside>
                    </div>
                </div>
            </section>

            <template v-if="selected">
                <!-- Below the panel: the facts on the left, the technical ids
                     on the right, in the same two column split. -->
                <div class="beatmap-columns">
                    <section class="card">
                        <h2>Difficulty</h2>

                        <dl class="diff-facts">
                            <div><dt>Max combo:</dt><dd>{{ fmtNumber(selected.max_combo) }}x</dd></div>
                            <div><dt>Length:</dt><dd>{{ fmtLength(selected.total_length) }}</dd></div>
                            <div><dt>BPM:</dt><dd>{{ fmtDecimal(selected.bpm, 0) }}</dd></div>
                            <div><dt>Plays:</dt><dd>{{ fmtNumber(selected.plays) }}</dd></div>
                            <div><dt>Passes:</dt><dd>{{ fmtNumber(selected.passes) }}</dd></div>
                            <div><dt>Pass rate:</dt><dd>{{ passRate }}</dd></div>
                        </dl>
                    </section>

                    <section class="card">
                        <h2>Details</h2>

                        <dl class="diff-facts">
                            <div><dt>Status:</dt><dd>{{ statusName(set.status) }}</dd></div>
                            <div><dt>Mode:</dt><dd>{{ modeName(selected.mode) }}</dd></div>
                            <div><dt>Set id:</dt><dd>{{ set.set_id }}</dd></div>
                            <div><dt>Beatmap id:</dt><dd>{{ selected.id }}</dd></div>
                            <div><dt>Difficulties:</dt><dd>{{ fmtNumber(set.difficulty_count) }}</dd></div>
                            <div class="diff-fact-wide"><dt>Checksum:</dt><dd class="mono">{{ selected.md5 }}</dd></div>
                        </dl>
                    </section>
                </div>

                <!-- The leaderboard of the selected difficulty. Fetched by the
                     component itself, so switching difficulties never reloads
                     the page. -->
                <map-scores :beatmap="selected"></map-scores>
            </template>
        </template>

                <koneko-slot name="page.bottom"></koneko-slot>
        <koneko-slot name="beatmapset.bottom"></koneko-slot>

        <site-footer></site-footer>
    </div>
</template>

<script>
    app.component("beatmapset-view", {
        template: "#beatmapset-view",
        data: () => ({
            loading: true,
            error: "",
            set: {},
            banner: "",
            // The difficulty the hash points at, kept as an id.
            selectedId: null
        }),
        computed: {
            difficulties() {
                // Easiest first, which is the order a player expects.
                return (this.set.difficulties || []).slice().sort(function (left, right) {
                    return (left.diff || 0) - (right.diff || 0);
                });
            },
            selected() {
                const list = this.difficulties;

                if (!list.length) return null;

                return list.find(diff => diff.id === this.selectedId) || list[0];
            },
            coverStyle() {
                if (!this.banner) return {};

                return { backgroundImage: "url(" + this.banner + ")" };
            },
            initial() {
                return String(this.set.creator || "?").charAt(0).toUpperCase();
            },
            // Mirrored sets often arrive with a star rating of zero, because
            // nothing on this server ever calculated one. Zero is not a rating,
            // so everything that reads the number checks this first.
            rated() {
                return Number((this.selected && this.selected.diff) || 0) > 0;
            },
            statusKey() {
                return String(this.statusName(this.set.status) || "unknown")
                    .toLowerCase()
                    .replace(/[^a-z0-9]+/g, "-");
            },
            attributes() {
                const diff = this.selected;

                if (!diff) return [];

                // Mania has no approach rate and no circle size worth showing.
                const rows = Number(diff.mode) === 3
                    ? [["Key count", diff.cs], ["HP drain", diff.hp], ["Accuracy", diff.od]]
                    : [["Circle size", diff.cs], ["HP drain", diff.hp],
                       ["Approach rate", diff.ar], ["Accuracy", diff.od]];

                return rows.map(([label, value]) => ({
                    label: label,
                    value: Number(value || 0),
                    percent: Math.max(0, Math.min(100, Number(value || 0) * 10))
                }));
            },
            // The star bar runs to ten stars, so anything above that is full.
            starPercent() {
                if (!this.selected) return 0;

                return Math.max(0, Math.min(100, Number(this.selected.diff || 0) * 10));
            },
            passPercent() {
                const plays = Number((this.selected && this.selected.plays) || 0);

                if (!plays) return 0;

                return Math.round(Number(this.selected.passes || 0) / plays * 100);
            },
            passRate() {
                const plays = Number((this.selected && this.selected.plays) || 0);

                if (!plays) return "-";

                return Math.round(Number(this.selected.passes || 0) / plays * 100) + "%";
            },
            downloadUrl() {
                return "https://osu." + this.$koneko.domain + "/d/" + this.set.set_id;
            }
        },
        methods: {
            slug(version) {
                return String(version || "")
                    .toLowerCase()
                    .replace(/[^a-z0-9]+/g, "-")
                    .replace(/^-|-$/g, "");
            },
            // The star rating colours of the client, so the bar reads the same
            // way as the song select does.
            starColor(stars) {
                const value = Number(stars || 0);

                // Never rated: grey, so it cannot be mistaken for an easy map.
                if (value <= 0) return "#5a5a63";

                if (value < 2) return "#4fc0ff";
                if (value < 2.7) return "#4fffd5";
                if (value < 4) return "#7cff4e";
                if (value < 5.3) return "#f2f261";
                if (value < 6.5) return "#ff8068";
                if (value < 8) return "#ff4e6f";

                return "#a653ff";
            },
            select(diff) {
                this.selectedId = diff.id;

                // Written without a jump, so the page keeps its scroll
                // position while the hash still points at this difficulty.
                const hash = "#" + this.slug(diff.version);

                if (window.location.hash !== hash) {
                    history.replaceState(null, "", window.location.pathname
                        + window.location.search + hash);
                }
            },
            // The hash accepts both a beatmap id and a difficulty name, so a
            // link like /beatmapsets/12#insane works as well as #1234567.
            fromHash() {
                const raw = decodeURIComponent(window.location.hash.replace(/^#/, "")).trim();

                if (!raw) return;

                const list = this.difficulties;
                const wanted = this.slug(raw);

                const match = list.find(diff => String(diff.id) === raw)
                    || list.find(diff => this.slug(diff.version) === wanted);

                if (match) {
                    this.selectedId = match.id;
                }
            },
            apply(body) {
                const envelope = (body && body.beatmapset) || null;
                const set = envelope && envelope.beatmapset;

                if (!set) {
                    this.error = "This beatmap set does not exist here.";
                    this.setTitle("Not found");
                    return false;
                }

                this.set = set;
                this.banner = this.coverUrl(set.set_id, "cover");
                this.error = "";
                this.setTitle(set.artist + " - " + set.title);

                this.fromHash();

                return true;
            }
        },
        async created() {
            const setId = decodeURIComponent(window.location.pathname.split("/").pop());
            const key = "beatmapset:" + setId;

            // Back and forward through the difficulties has to work too.
            this.onHashChange = () => this.fromHash();
            window.addEventListener("hashchange", this.onHashChange);

            // FastLoad: the page of the last visit first, the fresh one after.
            const cached = this.fastLoad(key);

            if (cached && this.apply(cached)) {
                this.loading = false;
            }

            try {
                // The set comes from the API on this origin, not through
                // /data/beatmapset.
                const body = {
                    beatmapset: await this.api("get_beatmapset", { id: setId })
                };

                if (this.apply(body)) {
                    this.fastSave(key, body);
                }
            } catch (e) {
                if (!cached) {
                    this.error = "The server could not be reached.";
                }
            } finally {
                this.loading = false;
            }
        },
        unmounted() {
            window.removeEventListener("hashchange", this.onHashChange);
        }
    });
</script>
