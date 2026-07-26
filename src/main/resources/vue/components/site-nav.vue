<template id="site-nav">
    <header class="nav">
        <a class="nav-brand" href="/">
            <span class="nav-name">{{ site.name }}</span>
            <span class="nav-tagline" v-if="site.tagline">{{ site.tagline }}</span>
        </a>

        <nav class="nav-links">
            <a href="/">Home</a>
            <a href="/beatmaps">Beatmaps</a>
            <a v-if="site.links && site.links.discord" :href="site.links.discord" target="_blank" rel="noopener">Discord</a>
            <a v-if="site.links && site.links.apiDocs" :href="site.links.apiDocs" target="_blank" rel="noopener">API</a>

            <template v-if="user">
                <a :href="'/u/' + user.id">{{ user.name }}</a>
                <button class="link-button" @click="logout">Log out</button>
            </template>
            <a v-else href="/login">Log in</a>
        </nav>
    </header>
</template>

<script>
    app.component("site-nav", {
        template: "#site-nav",
        computed: {
            site() {
                return this.$koneko.site || {};
            },
            user() {
                return this.$koneko.user;
            }
        },
        methods: {
            async logout() {
                await fetch("/auth/logout", { method: "POST" });
                window.location.href = "/";
            }
        }
    });
</script>
