<template id="beatmapset-view">
    <div class="page">
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
            <section class="mapset-hero">
                <img class="mapset-hero-banner"
                     v-if="banner"
                     :src="banner"
                     :alt="set.artist + ' - ' + set.title"
                     @error="banner = ''">
                <div class="mapset-hero-banner mapset-banner-empty" v-else></div>

                <div class="mapset-hero-text">
                    <h1>{{ set.title }}</h1>
                    <p class="mapset-hero-artist">{{ set.artist }}</p>

                    <div class="profile-meta">
                        <span>
                            mapped by
                            <a v-if="set.creator_id" :href="'/u/' + set.creator_id">{{ set.creator }}</a>
                            <span v-else>{{ set.creator }}</span>
                        </span>
                        <span class="badge">{{ statusName(set.status) }}</span>
                        <span>{{ fmtNumber(set.difficulty_count) }} difficulties</span>
                        <span>updated {{ fmtDate(set.last_update) }}</span>
                        <span v-if="set.revision">revision {{ set.revision }}</span>
                    </div>

                    <div class="hero-actions" v-if="set.hosted">
                        <a class="button" :href="downloadUrl">Download</a>
                        <a class="button button-ghost" v-if="set.has_video" :href="downloadUrl + '?novideo=1'">Without video</a>
                    </div>
                </div>
            </section>

            <!-- The difficulty bar. Picking one only swaps what is shown
                 below and writes the hash, so the page is never reloaded. -->
            <nav class="diff-bar" v-if="difficulties.length">
                <button class="diff-chip" v-for="diff in difficulties" :key="diff.id"
                    :class="{ active: selected && selected.id === diff.id }"
                    :title="diff.version + ' - ' + fmtDecimal(diff.diff) + ' stars'"
                    @click="select(diff)">
                    <span class="diff-orb" :style="{ background: starColor(diff.diff) }">
                        {{ fmtDecimal(diff.diff, 1) }}
                    </span>
                    <span class="diff-chip-name">{{ diff.version }}</span>
                </button>
            </nav>

            <template v-if="selected">
                <section class="stats">
                    <stat-card label="Stars" :text="fmtDecimal(selected.diff)"></stat-card>
                    <stat-card label="BPM" :text="fmtDecimal(selected.bpm, 0)"></stat-card>
                    <stat-card label="Length" :text="fmtLength(selected.total_length)"></stat-card>
                    <stat-card label="Plays" :value="selected.plays"></stat-card>
                </section>

                <section class="card">
                    <div class="diff-head">
                        <h2>{{ selected.version }}</h2>
                        <span class="badge">{{ modeName(selected.mode) }}</span>
                        <span class="badge">{{ statusName(selected.status) }}</span>
                    </div>

                    <!-- The four settings players actually look for, as bars,
                         since every one of them runs from 0 to 10. -->
                    <div class="diff-attrs">
                        <div class="diff-attr" v-for="attr in attributes" :key="attr.label">
                            <span class="diff-attr-label">{{ attr.label }}</span>
                            <span class="diff-attr-bar">
                                <span class="diff-attr-fill" :style="{ width: attr.percent + '%' }"></span>
                            </span>
                            <span class="diff-attr-value">{{ fmtDecimal(attr.value, 1) }}</span>
                        </div>
                    </div>

                    <dl class="diff-facts">
                        <div><dt>Max combo:</dt><dd>{{ fmtNumber(selected.max_combo) }}x</dd></div>
                        <div><dt>Length:</dt><dd>{{ fmtLength(selected.total_length) }}</dd></div>
                        <div><dt>BPM:</dt><dd>{{ fmtDecimal(selected.bpm, 0) }}</dd></div>
                        <div><dt>Plays:</dt><dd>{{ fmtNumber(selected.plays) }}</dd></div>
                        <div><dt>Passes:</dt><dd>{{ fmtNumber(selected.passes) }}</dd></div>
                        <div><dt>Pass rate:</dt><dd>{{ passRate }}</dd></div>
                        <div><dt>Beatmap id:</dt><dd>{{ selected.id }}</dd></div>
                        <div class="diff-fact-wide"><dt>Checksum:</dt><dd class="mono">{{ selected.md5 }}</dd></div>
                    </dl>
                </section>

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
            passRate() {
                const plays = Number(this.selected.plays || 0);

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
