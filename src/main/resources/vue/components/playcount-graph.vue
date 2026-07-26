<template id="playcount-graph">
    <div class="graph" v-if="points.length > 1">
        <div class="graph-plot">
            <span class="graph-max">{{ fmtNumber(max) }}</span>

            <svg class="graph-svg" viewBox="0 0 1000 200" preserveAspectRatio="none">
                <polyline class="graph-line" :points="line" vector-effect="non-scaling-stroke"></polyline>
            </svg>

            <div class="graph-dots">
                <span class="graph-dot" v-for="(point, i) in coords" :key="i"
                    :style="{ left: point.left + '%', bottom: point.bottom + '%' }"
                    :title="point.plays + ' plays in ' + point.month"></span>
            </div>
        </div>

        <div class="graph-axis">
            <span v-for="(point, i) in points" :key="i">{{ label(point.month) }}</span>
        </div>
    </div>

    <p class="muted" v-else>Not enough plays yet.</p>
</template>

<script>
    app.component("playcount-graph", {
        template: "#playcount-graph",
        props: {
            months: { type: Array, default: () => [] }
        },
        computed: {
            points() {
                return (this.months || []).map(point => ({
                    month: point.month,
                    plays: Number(point.plays || 0)
                }));
            },
            max() {
                // A flat zero line still needs a scale, hence the floor of 1.
                return Math.max(1, ...this.points.map(point => point.plays));
            },
            coords() {
                const last = Math.max(1, this.points.length - 1);

                return this.points.map((point, i) => ({
                    month: point.month,
                    plays: point.plays,
                    left: (i / last) * 100,
                    bottom: (point.plays / this.max) * 100
                }));
            },
            line() {
                return this.coords
                    .map(point => (point.left / 100 * 1000) + ","
                        + (200 - point.bottom / 100 * 200))
                    .join(" ");
            }
        },
        methods: {
            label(month) {
                const parts = String(month || "").split("-");

                if (parts.length !== 2) return month;

                const names = ["Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

                return names[Number(parts[1]) - 1] + " " + parts[0];
            }
        }
    });
</script>
