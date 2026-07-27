<template id="score-view">
    <div class="page">
        <site-nav></site-nav>

        <koneko-slot name="score.top"></koneko-slot>
        <koneko-slot name="page.top"></koneko-slot>

        <section class="card" v-if="loading">
            <div class="skeleton skeleton-title"></div>
            <div class="skeleton-lines">
                <div class="skeleton skeleton-line" v-for="n in 5" :key="n"></div>
            </div>
        </section>

        <section class="card" v-else-if="error">
            <h2>Score not found</h2>
            <p class="muted">{{ error }}</p>
            <a class="button" href="/leaderboard">Back to the leaderboard</a>
        </section>

        <template v-else>
            <section class="card score-hero">
                <div class="score-hero-map">
                    <img class="score-hero-cover" v-if="map" :src="coverUrl(map.set_id, 'card')"
                        :alt="map.title" loading="lazy">

                    <div class="score-hero-text">
                        <h2 v-if="map">
                            <a :href="'/beatmapsets/' + map.set_id">{{ map.artist }} - {{ map.title }}</a>
                        </h2>
                        <h2 v-else>Score #{{ score.id }}</h2>

                        <p class="muted" v-if="map">
                            [{{ map.version }}] mapped by {{ map.creator }}
                        </p>

                        <p class="score-hero-tags">
                            <span class="badge">{{ modeName(score.mode) }}</span>
                            <span class="badge" v-if="map">{{ statusName(map.status) }}</span>
                            <span class="badge" v-if="score.mods">{{ fmtMods(score.mods) }}</span>
                            <span class="badge badge-quiet" v-if="score.perfect">Perfect combo</span>
                        </p>
                    </div>

                    <div class="score-hero-grade" :class="'grade-' + String(score.grade).toLowerCase()">
                        {{ score.grade }}
                    </div>
                </div>

                <p class="muted small" v-if="score.play_time">
                    Set {{ fmtRelative(score.play_time) }} ({{ fmtDate(score.play_time) }})
                </p>
            </section>

            <section class="card">
                <h3>Result</h3>

                <div class="score-figures">
                    <div class="score-figure">
                        <span class="score-figure-label">Score</span>
                        <span class="score-figure-value">{{ fmtNumber(score.score) }}</span>
                    </div>
                    <div class="score-figure">
                        <span class="score-figure-label">Accuracy</span>
                        <span class="score-figure-value">{{ fmtAccuracy(score.acc) }}</span>
                    </div>
                    <div class="score-figure">
                        <span class="score-figure-label">pp</span>
                        <span class="score-figure-value">{{ fmtDecimal(score.pp) }}</span>
                    </div>
                    <div class="score-figure">
                        <span class="score-figure-label">Combo</span>
                        <span class="score-figure-value">
                            {{ fmtNumber(score.max_combo) }}x<template v-if="map && map.max_combo">
                                <span class="muted">/{{ fmtNumber(map.max_combo) }}</span>
                            </template>
                        </span>
                    </div>
                </div>

                <h3>Hits</h3>

                <div class="hit-counts">
                    <span class="hit-count hit-300">{{ fmtNumber(score.n300) }}<small>300</small></span>
                    <span class="hit-count hit-geki" v-if="score.ngeki">{{ fmtNumber(score.ngeki) }}<small>geki</small></span>
                    <span class="hit-count hit-100">{{ fmtNumber(score.n100) }}<small>100</small></span>
                    <span class="hit-count hit-katu" v-if="score.nkatu">{{ fmtNumber(score.nkatu) }}<small>katu</small></span>
                    <span class="hit-count hit-50">{{ fmtNumber(score.n50) }}<small>50</small></span>
                    <span class="hit-count hit-miss">{{ fmtNumber(score.nmiss) }}<small>miss</small></span>
                </div>

                <p class="muted small" v-if="score.time_elapsed">
                    Play time {{ fmtLength(Math.round(score.time_elapsed / 1000)) }}
                </p>
            </section>

            <section class="card" v-if="map">
                <h3>Beatmap</h3>

                <div class="map-stats">
                    <span><b>CS</b> {{ fmtDecimal(map.cs) }}</span>
                    <span><b>AR</b> {{ fmtDecimal(map.ar) }}</span>
                    <span><b>OD</b> {{ fmtDecimal(map.od) }}</span>
                    <span><b>HP</b> {{ fmtDecimal(map.hp) }}</span>
                    <span><b>Stars</b> {{ fmtDecimal(map.diff) }}</span>
                    <span><b>BPM</b> {{ fmtNumber(map.bpm) }}</span>
                    <span><b>Length</b> {{ fmtLength(map.total_length) }}</span>
                </div>

                <p class="muted small">
                    {{ fmtNumber(map.plays) }} plays, {{ fmtNumber(map.passes) }} passes
                </p>

                <div class="score-links">
                    <a class="button" :href="'/beatmapsets/' + map.set_id">Beatmap page</a>
                    <a class="button button-quiet" :href="'osu://b/' + map.id">Open in osu!</a>
                </div>
            </section>
        </template>

        <koneko-slot name="page.bottom"></koneko-slot>
        <site-footer></site-footer>
    </div>
</template>

<script>
    /**
     * One score in full. Every score row on the site links here, which is what
     * makes a play something that can be linked to at all - the leaderboard row
     * only has room for the numbers, not for the hit counts or the map it was
     * set on.
     *
     * The score answer already carries its beatmap, so this is a single call.
     */
    app.component("score-view", {
        template: "#score-view",
        data: () => ({
            loading: true,
            error: "",
            score: null
        }),
        computed: {
            map() {
                return (this.score && this.score.beatmap) || null;
            },
            scoreId() {
                const parts = window.location.pathname.split("/").filter(Boolean);

                return parts[parts.length - 1] || "";
            }
        },
        methods: {
            async load() {
                const key = "score:" + this.scoreId;
                const cached = this.fastLoad(key);

                if (cached) {
                    this.score = cached;
                    this.loading = false;
                }

                try {
                    const answer = await this.api("get_score_details", { id: this.scoreId });

                    this.score = (answer && answer.score) || null;

                    if (!this.score) {
                        this.error = "This score does not exist any more.";
                    } else {
                        this.fastSave(key, this.score);
                        this.setTitle(this.map
                            ? this.map.artist + " - " + this.map.title
                            : "Score");
                    }
                } catch (e) {
                    if (!cached) {
                        this.error = e && e.status === 404
                            ? "This score does not exist."
                            : "The score could not be loaded.";
                    }
                } finally {
                    this.loading = false;
                }
            }
        },
        created() {
            this.setTitle("Score");
            this.load();
        }
    });
</script>
