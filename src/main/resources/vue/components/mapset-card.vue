<template id="mapset-card">
    <a class="mapset-card" :href="'/beatmapsets/' + set.set_id">
        <div class="mapset-banner">
            <img v-if="banner"
                 :src="banner"
                 :alt="set.artist + ' - ' + set.title"
                 loading="lazy"
                 @error="banner = ''">
            <div class="mapset-banner-empty" v-else>
                <span>{{ set.title }}</span>
            </div>

            <span class="badge mapset-card-status">{{ statusName(set.status) }}</span>
        </div>

        <div class="mapset-card-body">
            <div class="mapset-card-title">{{ set.title }}</div>
            <div class="mapset-card-artist">{{ set.artist }}</div>

            <div class="mapset-card-meta">
                <span>by {{ set.creator }}</span>
                <span>{{ modeName(set.mode) }}</span>
                <span>{{ set.difficulty_count }} diffs</span>
                <span>{{ fmtLength(set.total_length) }}</span>
            </div>

            <div class="mapset-card-meta">
                <span>{{ fmtNumber(set.plays) }} plays</span>
                <span v-if="set.hosted" class="badge">hosted here</span>
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
        }
    });
</script>
