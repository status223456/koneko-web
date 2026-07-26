<template id="site-footer">
    <footer class="footer">
        <span>{{ site.name }}</span>
        <span class="footer-sep">-</span>
        <span>powered by bancho.jar and koneko-web</span>

        <span class="footer-links">
            <a v-if="site.links && site.links.discord" :href="site.links.discord" target="_blank" rel="noopener">Discord</a>
            <a v-if="site.links && site.links.github" :href="site.links.github" target="_blank" rel="noopener">GitHub</a>
        </span>
    </footer>
</template>

<script>
    app.component("site-footer", {
        template: "#site-footer",
        computed: {
            site() {
                return this.$koneko.site || {};
            }
        }
    });
</script>
