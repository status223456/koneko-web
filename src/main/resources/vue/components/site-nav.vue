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

            <!-- One list, built from the core entries and the ones plugins
                 added, sorted by order. A plugin can hide any of them by id,
                 so a core link can be replaced rather than duplicated. -->
            <nav class="nav-links">
                <a v-for="item in navLinks" :key="item.id" :href="item.href"
                    :target="item.external ? '_blank' : null"
                    :rel="item.external ? 'noopener' : null">{{ item.label }}</a>

                <koneko-slot name="nav.links"></koneko-slot>
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

                <koneko-slot name="nav.actions"></koneko-slot>
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
            // The links the site itself owns. Every one has an id, which is
            // what registry.hideNavItem(id) refers to.
            coreLinks() {
                return [
                    { id: "leaderboard", label: "Leaderboard", href: "/leaderboard", order: 10 },
                    { id: "beatmaps", label: "Beatmaps", href: "/beatmaps", order: 20 },
                    { id: "community", label: "Community", href: this.links.discord, order: 30, external: true },
                    { id: "api", label: "API", href: this.links.apiDocs, order: 40, external: true }
                ];
            },
            // Plugin entries are already filtered by the server: one that needs
            // privileges is never sent to a browser that may not see it.
            navLinks() {
                return this.mergeNav(this.coreLinks, this.pluginNav("nav"));
            },
            avatar() {
                return "https://a." + this.$koneko.domain + "/" + this.user.id;
            }
        },
        methods: {
            // Core entries and plugin entries, minus everything hidden, in
            // order. Entries without a href (an unset Discord link, say) drop
            // out on their own.
            mergeNav(core, contributed) {
                const hidden = this.pluginNavHidden();

                return core.concat(contributed)
                    .filter(item => item.href && hidden.indexOf(item.id) === -1)
                    .sort((left, right) => (left.order || 0) - (right.order || 0));
            },
            async logout() {
                await fetch("/auth/logout", { method: "POST" });
                window.location.href = "/";
            }
        }
    });
</script>
