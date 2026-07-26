<template id="score-row">
    <div class="score-row">
        <div class="score-grade" :class="'grade-' + gradeClass">{{ score.grade }}</div>

        <div class="score-map">
            <div class="score-title" v-if="score.beatmap">
                {{ score.beatmap.artist }} - {{ score.beatmap.title }}
            </div>
            <div class="score-title" v-else>Unknown beatmap</div>

            <div class="score-meta">
                <span v-if="score.beatmap">[{{ score.beatmap.version }}]</span>
                <span>{{ fmtDecimal(score.acc) }}%</span>
                <span>{{ fmtNumber(score.max_combo) }}x</span>
                <span>{{ fmtDate(score.play_time) }}</span>
            </div>
        </div>

        <div class="score-pp">{{ fmtDecimal(score.pp, 0) }}pp</div>
    </div>
</template>

<script>
    app.component("score-row", {
        template: "#score-row",
        props: {
            score: { type: Object, required: true }
        },
        computed: {
            gradeClass() {
                const grade = (this.score.grade || "f").toString().toLowerCase();
                return grade.replace("+", "plus");
            }
        }
    });
</script>
