<template id="restrictions-view">
    <div class="page">
        <site-nav></site-nav>

        <koneko-slot name="page.top"></koneko-slot>

        <section class="card doc-page">
            <p class="muted" v-if="loading">Loading...</p>

            <p class="error" v-else-if="failed">
                This page could not be loaded.
                <a v-if="discord" :href="discord" target="_blank" rel="noopener">Ask on our Discord
                    server</a><span v-else>Ask on our Discord server</span> if you need help with a
                restricted account.
            </p>

            <!-- The document is rendered on the server from the .md file and
                 escaped there, so nothing a document says can bring markup of
                 its own onto the page. -->
            <div class="doc-body" v-else v-html="html"></div>
        </section>

        <koneko-slot name="page.bottom"></koneko-slot>

        <site-footer></site-footer>
    </div>
</template>

<script>
    app.component("restrictions-view", {
        template: "#restrictions-view",
        data: () => ({
            loading: true,
            failed: false,
            html: ""
        }),
        computed: {
            discord() {
                return ((this.$koneko.site || {}).links || {}).discord;
            }
        },
        async created() {
            this.setTitle("Account restrictions");

            const cached = this.fastLoad("docs:restrictions");

            if (cached) {
                this.html = cached.html;
                this.loading = false;
            }

            try {
                // Not an API call: the document lives in this service, next to
                // the component that shows it.
                const body = await this.session("GET", "/data/docs/restrictions");

                this.html = body.html || "";
                this.fastSave("docs:restrictions", { html: this.html });
            } catch (e) {
                this.failed = !this.html;
            } finally {
                this.loading = false;
            }
        }
    });
</script>
