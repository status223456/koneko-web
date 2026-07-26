<template id="site-nav">
    <header class="nav">
        <div class="nav-inner">
            <a class="nav-brand" href="/">
                <span class="nav-logo">
                    <img v-if="logo" :src="logo" :alt="site.name">
                    <span v-else>{{ initials }}</span>
                </span>

                <span class="nav-brand-text">
                    <span class="nav-name">{{ site.name }}</span>
                    <span class="nav-tagline" v-if="site.tagline">{{ site.tagline }}</span>
                </span>
            </a>

            <nav class="nav-links">
                <a href="/leaderboard">Leaderboard</a>
                <a href="/beatmaps">Beatmaps</a>
                <a v-if="links.discord" :href="links.discord" target="_blank" rel="noopener">Community</a>
                <a v-if="links.apiDocs" :href="links.apiDocs" target="_blank" rel="noopener">API</a>
            </nav>

            <div class="nav-actions">
                <template v-if="user">
                    <a class="nav-user" :href="'/u/' + user.id">
                        <img class="nav-user-avatar" :src="avatar" :alt="user.name">
                        <span>{{ user.name }}</span>
                    </a>
                    <button class="button button-ghost button-small" @click="logout">Log out</button>
                </template>

                <a class="button button-small" v-else href="/login">Log in</a>
            </div>
        </div>
    </header>
</template>

<script>
    app.component("site-nav", {
        template: "#site-nav",
        computed: {
            site() {
                return this.$koneko.site || {};
            },
            links() {
                return this.site.links || {};
            },
            home() {
                return this.site.home || {};
            },
            logo() {
                return this.home.logoImage || "";
            },
            // Without a logo the mark falls back to the initials of the
            // server name, so the bar never has an empty hole in it.
            initials() {
                const name = String(this.site.name || "").trim();

                if (!name) return "?";

                return name.split(/\s+/).slice(0, 2)
                    .map(word => word.charAt(0).toUpperCase()).join("");
            },
            user() {
                return this.$koneko.user;
            },
            avatar() {
                return "https://a." + this.$koneko.domain + "/" + this.user.id;
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
