<template id="site-footer">
    <footer class="footer">
        <div class="footer-inner">
            <div class="footer-brand">
                <span class="footer-name">{{ site.name }}</span>
                <span class="footer-note">powered by bancho.jar and koneko-web</span>
            </div>

            <!-- Same list as the navigation bar: core entries plus the ones
                 plugins marked as footer entries, hideable by id. -->
            <div class="footer-links">
                <a v-for="item in footerLinks" :key="item.id" :href="item.href"
                    :target="item.external ? '_blank' : null"
                    :rel="item.external ? 'noopener' : null">{{ item.label }}</a>

                <koneko-slot name="footer.links"></koneko-slot>
            </div>
        </div>

        <koneko-slot name="footer.bottom"></koneko-slot>
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
            },
            // Ids are prefixed with footer., so hiding a footer link never
            // touches the one in the navigation bar.
            coreLinks() {
                return [
                    { id: "footer.leaderboard", label: "Leaderboard", href: "/leaderboard", order: 10 },
                    { id: "footer.beatmaps", label: "Beatmaps", href: "/beatmaps", order: 20 },
                    { id: "footer.discord", label: "Discord", href: this.links.discord, order: 30, external: true },
                    { id: "footer.github", label: "GitHub", href: this.links.github, order: 40, external: true },
                    { id: "footer.api", label: "API", href: this.links.apiDocs, order: 50, external: true }
                ];
            },
            footerLinks() {
                const hidden = this.pluginNavHidden();

                return this.coreLinks.concat(this.pluginNav("footer"))
                    .filter(item => item.href && hidden.indexOf(item.id) === -1)
                    .sort((left, right) => (left.order || 0) - (right.order || 0));
            }
        }
    });
</script>
