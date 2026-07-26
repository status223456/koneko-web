<template id="site-footer">
    <footer class="footer">
        <div class="footer-inner">
            <div class="footer-brand">
                <span class="footer-name">{{ site.name }}</span>
                <span class="footer-note">powered by bancho.jar and koneko-web</span>
            </div>

            <div class="footer-links">
                <a href="/leaderboard">Leaderboard</a>
                <a href="/beatmaps">Beatmaps</a>
                <a v-if="links.discord" :href="links.discord" target="_blank" rel="noopener">Discord</a>
                <a v-if="links.github" :href="links.github" target="_blank" rel="noopener">GitHub</a>
                <a v-if="links.apiDocs" :href="links.apiDocs" target="_blank" rel="noopener">API</a>
            </div>
        </div>
    </footer>
</template>

<script>
    app.component("site-footer", {
        template: "#site-footer",
        computed: {
            site() {
                return this.$koneko.site || {};
            },
            links() {
                return this.site.links || {};
            }
        }
    });
</script>
