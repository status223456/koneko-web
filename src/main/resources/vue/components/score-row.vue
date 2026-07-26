<template id="score-row">
    <div class="score-row">
        <!-- Set banner. Sets uploaded here have none on osu!'s CDN, in which
             case the image simply never loads and the row keeps its plain
             background. -->
        <div class="score-cover" v-if="coverStyle" :style="coverStyle"></div>

        <div class="score-grade" :class="'grade-' + gradeClass">{{ score.grade }}</div>

        <div class="score-map">
            <div class="score-title">
                <a v-if="score.beatmap" :href="'/beatmapsets/' + score.beatmap.set_id">
                    {{ score.beatmap.artist }} - {{ score.beatmap.title }}
                    <span class="muted" v-if="score.beatmap.version">[{{ score.beatmap.version }}]</span>
                </a>
                <span v-else>Unknown beatmap</span>

                <span class="mods" v-if="mods.length">{{ mods.join(", ") }}</span>
            </div>

            <div class="score-meta">
                <span>{{ fmtAccuracy(score.acc) }}</span>
                <span>{{ fmtNumber(score.max_combo) }}x</span>
                <span>{{ fmtNumber(score.score) }}</span>
                <span>{{ fmtRelative(score.play_time) }}</span>
            </div>
        </div>

        <div class="score-numbers">
            <div class="score-pp">{{ fmtDecimal(score.pp, 0) }}<span class="unit">pp</span></div>
            <!-- Only the best scores are weighted, the rest have no index. -->
            <div class="score-weight" v-if="weighted !== null">
                {{ weightPercent }}% {{ weighted }}pp
            </div>
        </div>
    </div>
</template>

<script>
    app.component("score-row", {
        template: "#score-row",
        props: {
            score: { type: Object, required: true },
            // Position in the best-scores list, used for the pp weighting.
            index: { default: null }
        },
        computed: {
            coverStyle() {
                const setId = this.score.beatmap && this.score.beatmap.set_id;
                if (!setId) return null;

                return { backgroundImage: 'url("' + this.coverUrl(setId, "cover") + '")' };
            },
            gradeClass() {
                const grade = (this.score.grade || "f").toString().toLowerCase();
                return grade.replace("+", "plus");
            },
            mods() {
                return this.fmtMods(this.score.mods);
            },
            weightFactor() {
                if (this.index === null || this.index === undefined) return null;
                return Math.pow(0.95, Number(this.index));
            },
            weightPercent() {
                return this.weightFactor === null
                    ? ""
                    : Math.round(this.weightFactor * 100);
            },
            weighted() {
                if (this.weightFactor === null) return null;
                return Math.round(Number(this.score.pp || 0) * this.weightFactor);
            }
        }
    });
</script>
