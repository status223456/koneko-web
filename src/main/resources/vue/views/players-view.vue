<template id="players-view">
    <div class="page">
        <site-nav></site-nav>

        <koneko-slot name="players.top"></koneko-slot>
        <koneko-slot name="page.top"></koneko-slot>

        <section class="card">
            <h2>Players</h2>

            <div class="filters">
                <label class="field field-wide">
                    <span class="field-label">Name</span>
                    <input class="filter-input" type="search" v-model="search"
                        placeholder="Part of a name" @keyup.enter="reset">
                </label>

                <button class="button" @click="reset">Search</button>
            </div>

            <p class="muted small" v-if="count !== null">
                {{ fmtNumber(count) }} matching {{ count === 1 ? "player" : "players" }}
            </p>
        </section>

        <section class="card">
            <div class="skeleton-rows" v-if="loading">
                <div class="skeleton skeleton-row" v-for="n in 6" :key="n"></div>
            </div>

            <template v-else>
                <p class="muted" v-if="error">{{ error }}</p>
                <p class="muted" v-else-if="!players.length">No player matches that name.</p>

                <div class="player-grid" v-else>
                    <a class="player-chip" v-for="player in players" :key="player.id"
                        :href="'/u/' + player.id">
                        <img class="player-chip-avatar" :src="avatar(player.id)"
                            :alt="player.name" loading="lazy">

                        <span class="player-chip-text">
                            <span class="player-chip-name">{{ player.name }}</span>
                            <span class="player-chip-meta">
                                <span :class="flagClass(player.country)"></span>
                                <span>{{ (player.country || "").toUpperCase() }}</span>
                            </span>
                        </span>
                    </a>
                </div>

                <button class="load-more" v-if="canLoad" :disabled="busy" @click="loadMore">
                    Load more
                </button>
            </template>
        </section>

        <koneko-slot name="page.bottom"></koneko-slot>
        <site-footer></site-footer>
    </div>
</template>

<script>
    /**
     * The player directory. An empty search is a valid one: the API then
     * answers with the first page of every public account, which makes this the
     * one page where a new player can be found without knowing a name.
     */
    app.component("players-view", {
        template: "#players-view",
        data: () => ({
            loading: true,
            busy: false,
            error: "",
            search: "",
            players: [],
            count: null,
            pageSize: 60
        }),
        computed: {
            canLoad() {
                return this.count !== null && this.players.length < Number(this.count);
            }
        },
        methods: {
            avatar(id) {
                return "https://a." + this.$koneko.domain + "/" + id;
            },
            params(offset) {
                return { q: this.search.trim(), limit: this.pageSize, offset: offset };
            },
            // The search text is part of the url, so a result page can be
            // shared and the back button keeps the query.
            pushUrl() {
                const query = this.search.trim()
                    ? "?q=" + encodeURIComponent(this.search.trim())
                    : "";

                window.history.replaceState(null, "", "/players" + query);
            },
            reset() {
                this.pushUrl();
                this.players = [];
                this.count = null;
                this.loading = true;
                this.load();
            },
            async load() {
                const key = "players:" + this.search.trim().toLowerCase();
                const cached = this.fastLoad(key);

                if (cached) {
                    this.apply(cached);
                    this.loading = false;
                }

                try {
                    const answer = await this.api("search_players", this.params(0));

                    this.apply(answer);
                    this.fastSave(key, answer);
                    this.error = "";
                } catch (e) {
                    if (!cached) this.error = "The player list could not be loaded.";
                } finally {
                    this.loading = false;
                }
            },
            apply(answer) {
                this.players = (answer && answer.results) || [];
                this.count = (answer && answer.count) !== undefined
                    ? answer.count
                    : this.players.length;
            },
            async loadMore() {
                if (this.busy) return;

                this.busy = true;

                try {
                    const answer = await this.apiQuietly("search_players",
                        this.params(this.players.length));

                    this.players.push(...((answer && answer.results) || []));
                } finally {
                    this.busy = false;
                }
            }
        },
        created() {
            this.search = new URLSearchParams(window.location.search).get("q") || "";
            this.setTitle("Players");
            this.load();
        }
    });
</script>
