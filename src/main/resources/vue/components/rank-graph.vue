<template id="rank-graph">
    <div class="rank-graph" v-if="coords.length > 1">
        <!-- The plot carries no frame of its own: it sits inside the profile
             card, so a bordered box would read as a second panel. Only the
             line, a soft fill under it and two dim dates are drawn. -->
        <div class="rank-plot">
            <svg class="rank-svg" viewBox="0 0 1000 120" preserveAspectRatio="none">
                <defs>
                    <linearGradient id="rank-fade" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stop-color="var(--accent)" stop-opacity=".28"></stop>
                        <stop offset="100%" stop-color="var(--accent)" stop-opacity="0"></stop>
                    </linearGradient>
                </defs>

                <polygon class="rank-fill" :points="area" fill="url(#rank-fade)"></polygon>
                <polyline class="rank-line" :points="line" vector-effect="non-scaling-stroke"></polyline>
            </svg>

            <!-- The best position of the span labels the top, so the axis runs
                 upside down: a line going up means a climb towards #1. -->
            <span class="rank-peak">#{{ fmtNumber(best) }}</span>
            <span class="rank-floor" v-if="worst !== best">#{{ fmtNumber(worst) }}</span>

            <div class="rank-dots">
                <span class="rank-dot" v-for="(point, i) in coords" :key="i"
                    :style="{ left: point.left + '%', bottom: point.bottom + '%' }"
                    :title="'#' + fmtNumber(point.rank) + ' on ' + point.date"></span>
            </div>
        </div>

        <div class="rank-axis">
            <span>{{ label(coords[0].date) }}</span>
            <span>{{ label(coords[coords.length - 1].date) }}</span>
        </div>
    </div>

    <p class="muted" v-else>Not ranked yet.</p>
</template>

<script>
    app.component("rank-graph", {
        template: "#rank-graph",
        props: {
            days: { type: Array, default: () => [] },
            // The position held right now, which the profile page knows anyway.
            // It stands in for a history that is not there yet, so the plot
            // shows where the account is instead of nothing at all.
            rank: { type: Number, default: 0 }
        },
        computed: {
            // Days before the account had a position at all carry no rank and
            // are left out rather than drawn as a drop to zero.
            stored() {
                return (this.days || [])
                    .filter(point => point && point.rank)
                    .map(point => ({
                        date: String(point.date || ""),
                        rank: Number(point.rank)
                    }));
            },
            points() {
                const stored = this.stored;

                if (stored.length > 1) return stored;

                // Nothing has been recorded yet, so the current position is
                // held flat across the span until real points pile up.
                const rank = Number(this.rank) || (stored.length ? stored[0].rank : 0);

                if (!rank) return [];

                const span = (this.days || [])
                    .map(point => String((point && point.date) || ""))
                    .filter(date => date.length === 10);

                const first = span.length ? span[0] : this.today();
                const last = span.length ? span[span.length - 1] : this.today();

                return [
                    { date: first, rank: rank },
                    { date: last, rank: rank }
                ];
            },
            /** The closest to #1 the player came, which sits at the top. */
            best() {
                return this.points.length ? Math.min(...this.points.map(p => p.rank)) : 0;
            },
            worst() {
                return this.points.length ? Math.max(...this.points.map(p => p.rank)) : 0;
            },
            coords() {
                const points = this.points;

                if (points.length < 2) return [];

                const last = points.length - 1;
                // A player who never moved would divide by zero, so a flat line
                // is drawn a little above the middle, where it has room to fall.
                const span = this.worst - this.best;

                return points.map((point, i) => ({
                    date: point.date,
                    rank: point.rank,
                    left: (i / last) * 100,
                    // 10-90% rather than 0-100%: the line never touches the
                    // edges, which is what keeps it from looking like a border.
                    bottom: span > 0
                        ? 10 + ((this.worst - point.rank) / span) * 80
                        : 62
                }));
            },
            line() {
                return this.coords.map(point => this.at(point)).join(" ");
            },
            /** The same line, closed along the bottom so it can be filled. */
            area() {
                const coords = this.coords;

                if (coords.length < 2) return "";

                return "0,120 " + coords.map(point => this.at(point)).join(" ") + " 1000,120";
            }
        },
        methods: {
            at(point) {
                return (point.left / 100 * 1000) + "," + (120 - point.bottom / 100 * 120);
            },
            today() {
                const now = new Date();
                const pad = value => (value < 10 ? "0" + value : String(value));

                return now.getFullYear() + "-" + pad(now.getMonth() + 1) + "-" + pad(now.getDate());
            },
            label(date) {
                const parts = String(date || "").split("-");

                if (parts.length !== 3) return date;

                const names = ["Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

                return Number(parts[2]) + " " + names[Number(parts[1]) - 1];
            }
        }
    });
</script>
