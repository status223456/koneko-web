<template id="mapset-card">
    <!-- One row of the listing: the cover on the left, the text on top of a
         dimmed copy of the same cover on the right. The difficulty dots sit
         under the status, the way the song select shows a set. -->
    <a class="mapset-card" :href="'/beatmapsets/' + set.set_id">
        <div class="mapset-card-cover" :style="coverStyle"></div>

        <div class="mapset-card-thumb">
            <img v-if="banner"
                 :src="banner"
                 :alt="set.artist + ' - ' + set.title"
                 loading="lazy"
                 @error="banner = ''">
            <div class="mapset-thumb-empty" v-else></div>

            <span class="mapset-card-video" v-if="set.has_video" title="Has a video">&#9654;</span>
        </div>

        <div class="mapset-card-body">
            <div class="mapset-card-title">{{ set.title }}</div>
            <div class="mapset-card-artist">by {{ set.artist }}</div>
            <div class="mapset-card-mapper">mapped by <span>{{ set.creator }}</span></div>

            <div class="mapset-card-foot">
                <span class="status-pill" :class="'status-' + statusKey">{{ statusName(set.status) }}</span>

                <!-- One dot per difficulty, coloured by its star rating. When
                     the listing sends no difficulty list, the count is shown
                     instead of guessed colours. -->
                <span class="diff-dots" v-if="dots.length">
                    <span class="diff-dot" v-for="(dot, index) in dots" :key="index"
                        :style="{ background: dot.color }"
                        :title="dot.title"></span>
                    <span class="diff-dots-more" v-if="extra">+{{ extra }}</span>
                </span>
                <span class="mapset-card-count" v-else>{{ set.difficulty_count }} diffs</span>
            </div>

            <div class="mapset-card-meta">
                <span>{{ modeName(set.mode) }}</span>
                <span>{{ fmtLength(set.total_length) }}</span>
                <span>{{ fmtNumber(set.plays) }} plays</span>
                <span v-if="set.hosted">hosted here</span>
            </div>
        </div>
    </a>
</template>

<script>
    app.component("mapset-card", {
        template: "#mapset-card",
        props: {
            set: { type: Object, required: true }
        },
        data() {
            // The banner is the card sized cover of the set; an empty string
            // means "there is none", which the template renders as a gradient.
            return { banner: this.coverUrl(this.set.set_id, "card") };
        },
        computed: {
            coverStyle() {
                if (!this.banner) return {};

                return { backgroundImage: "url(" + this.banner + ")" };
            },
            // The status drives the pill colour, so it has to survive as a
            // class name: "work in progress" becomes "work-in-progress".
            statusKey() {
                return String(this.statusName(this.set.status) || "unknown")
                    .toLowerCase()
                    .replace(/[^a-z0-9]+/g, "-");
            },
            sorted() {
                return (this.set.difficulties || []).slice().sort(function (left, right) {
                    return (left.diff || 0) - (right.diff || 0);
                });
            },
            // A long set would push the row out of shape, so the dots stop at
            // ten and the rest are counted.
            dots() {
                return this.sorted.slice(0, 10).map(diff => ({
                    color: this.starColor(diff.diff),
                    title: Number(diff.diff || 0) > 0
                        ? diff.version + " - " + this.fmtDecimal(diff.diff, 2) + " stars"
                        : diff.version + " - not rated"
                }));
            },
            extra() {
                return Math.max(0, this.sorted.length - 10);
            }
        },
        methods: {
            // The star rating colours of the client, so a set reads the same
            // way here as it does in song select.
            starColor(stars) {
                const value = Number(stars || 0);

                // A set mirrored without a calculated rating comes in at zero.
                // Grey says "unknown"; blue would say "easy".
                if (value <= 0) return "#5a5a63";

                if (value < 2) return "#4fc0ff";
                if (value < 2.7) return "#4fffd5";
                if (value < 4) return "#7cff4e";
                if (value < 5.3) return "#f2f261";
                if (value < 6.5) return "#ff8068";
                if (value < 8) return "#ff4e6f";

                return "#a653ff";
            }
        }
    });
</script>
