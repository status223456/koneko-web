<template id="beatmaps-view">
    <div class="page page-wide">
        <site-nav></site-nav>

        <koneko-slot name="beatmaps.top"></koneko-slot>
        <koneko-slot name="page.top"></koneko-slot>

        <!-- The listing header: one title bar, one search panel, one sort bar.
             The same three pieces the game's own beatmap listing has. -->
        <header class="listing-head">
            <h1>Beatmap listing</h1>
        </header>

        <section class="search-panel">
            <div class="search-field">
                <input class="search-field-input"
                       type="search"
                       v-model="query"
                       @input="debounced"
                       placeholder="type in keywords...">
            </div>

            <!-- Every row is one facet: a label on the left, the choices as
                 plain text on the right. Nothing here is a dropdown, so the
                 whole filter state is readable at a glance. -->
            <div class="facet">
                <span class="facet-label">Mode</span>
                <div class="facet-options">
                    <button class="facet-option" v-for="option in modeOptions" :key="'m' + option.value"
                        :class="{ active: mode === option.value }"
                        @click="pick('mode', option.value)">{{ option.name }}</button>
                </div>
            </div>

            <div class="facet">
                <span class="facet-label">Categories</span>
                <div class="facet-options">
                    <button class="facet-option" v-for="option in statusOptions" :key="'s' + option.value"
                        :class="{ active: status === option.value }"
                        @click="pick('status', option.value)">{{ option.name }}</button>
                </div>
            </div>

            <div class="facet">
                <span class="facet-label">Source</span>
                <div class="facet-options">
                    <button class="facet-option" v-for="option in serverOptions" :key="'v' + option.value"
                        :class="{ active: server === option.value }"
                        @click="pick('server', option.value)">{{ option.name }}</button>
                </div>
            </div>
        </section>

        <div class="sort-bar">
            <span class="sort-bar-label">Sort by</span>

            <button class="sort-option" v-for="option in sortOptions" :key="option.value"
                :class="{ active: sort === option.value }"
                @click="pick('sort', option.value)">{{ option.name }}</button>

            <div class="sort-bar-right">
                <span class="muted small" v-if="!loading">{{ fmtNumber(count) }} sets</span>

                <!-- Two densities of the same list, kept in the browser so the
                     choice survives a reload. -->
                <button class="layout-toggle" :class="{ active: layout === 'grid' }"
                    title="Cards" @click="setLayout('grid')">&#9638;</button>
                <button class="layout-toggle" :class="{ active: layout === 'list' }"
                    title="Rows" @click="setLayout('list')">&#9776;</button>
            </div>
        </div>

        <p class="muted" v-if="loading">Searching the beatmaps...</p>

        <section class="card" v-else-if="!sets.length">
            <h2>No maps match that search</h2>
            <p class="muted">Try another title, artist or mapper, or clear the filters to see
                every set on the server.</p>
        </section>

        <template v-else>
            <div class="mapset-grid" :class="'mapset-grid-' + layout">
                <mapset-card v-for="set in sets" :key="set.set_id" :set="set"></mapset-card>
            </div>

            <div class="pager">
                <button class="button button-ghost" :disabled="!hasPrev" @click="prev">Previous</button>
                <span class="muted small">page {{ page }} of {{ pages }}</span>
                <button class="button button-ghost" :disabled="!hasNext" @click="next">Next</button>
            </div>
        </template>

                <koneko-slot name="page.bottom"></koneko-slot>
        <koneko-slot name="beatmaps.bottom"></koneko-slot>

        <site-footer></site-footer>
    </div>
</template>

<script>
    app.component("beatmaps-view", {
        template: "#beatmaps-view",
        data: () => ({
            loading: true,
            query: "",
            status: "",
            mode: "",
            server: "",
            sort: "updated",
            layout: "grid",
            offset: 0,
            limit: 24,
            count: 0,
            sets: [],
            timer: null,
            // The facets, written once here instead of as markup four times
            // over. The empty value always means "no filter".
            modeOptions: [
                { value: "", name: "Any" },
                { value: "0", name: "osu!" },
                { value: "1", name: "osu!taiko" },
                { value: "2", name: "osu!catch" },
                { value: "3", name: "osu!mania" }
            ],
            statusOptions: [
                { value: "", name: "Any" },
                { value: "1", name: "Ranked" },
                { value: "2", name: "Approved" },
                { value: "3", name: "Qualified" },
                { value: "4", name: "Loved" },
                { value: "0", name: "Pending" },
                { value: "-1", name: "Work in progress" },
                { value: "-2", name: "Graveyard" }
            ],
            serverOptions: [
                { value: "", name: "Every set" },
                { value: "local", name: "Submitted here" },
                { value: "osu", name: "Mirrored from osu!" }
            ],
            sortOptions: [
                { value: "title", name: "Title" },
                { value: "updated", name: "Date added" },
                { value: "difficulty", name: "Difficulty" },
                { value: "plays", name: "Plays" }
            ]
        }),
        computed: {
            hasPrev() {
                return this.offset > 0;
            },
            hasNext() {
                return this.offset + this.limit < this.count;
            },
            page() {
                return Math.floor(this.offset / this.limit) + 1;
            },
            pages() {
                return Math.max(1, Math.ceil(this.count / this.limit));
            }
        },
        methods: {
            // One handler for every facet, since picking any of them means the
            // same thing: set the field, go back to the first page, search.
            pick(field, value) {
                if (this[field] === value) return;

                this[field] = value;
                this.reload();
            },
            setLayout(layout) {
                this.layout = layout;

                try {
                    window.localStorage.setItem("beatmaps:layout", layout);
                } catch (e) {
                    // Private windows refuse storage; the choice then lasts
                    // for this page only, which is no worse than nothing.
                }
            },
            search() {
                const params = new URLSearchParams();

                if (this.query) params.set("q", this.query);
                if (this.status !== "") params.set("status", this.status);
                if (this.mode !== "") params.set("mode", this.mode);
                if (this.server) params.set("server", this.server);
                if (this.sort && this.sort !== "updated") params.set("sort", this.sort);
                if (this.offset) params.set("offset", this.offset);

                return params.toString();
            },
            // The same filters as the url, in the shape the API takes them.
            // Empty values are dropped by the api helper.
            params() {
                return {
                    q: this.query,
                    status: this.status,
                    mode: this.mode,
                    server: this.server,
                    sort: this.sort && this.sort !== "updated" ? this.sort : "",
                    offset: this.offset,
                    limit: this.limit
                };
            },
            apply(body) {
                const page = (body && body.beatmapsets) || {};

                this.sets = page.results || [];
                this.count = page.count || 0;

                if (typeof page.limit === "number" && page.limit > 0) {
                    this.limit = page.limit;
                }
            },
            async load() {
                const search = this.search();

                // The url always mirrors the filters, so a search can be shared
                // and survives a refresh.
                window.history.replaceState({}, "", search ? "/beatmaps?" + search : "/beatmaps");

                // FastLoad: the same search from the last visit is shown at once.
                const cached = this.fastLoad("beatmaps:" + search);

                if (cached) {
                    this.apply(cached);
                }

                this.loading = !cached;

                try {
                    // The listing is asked of the API on this origin instead
                    // of being proxied through /data/beatmaps.
                    const body = {
                        beatmapsets: await this.api("search_beatmapsets", this.params())
                    };

                    this.apply(body);
                    this.fastSave("beatmaps:" + search, body);
                } catch (e) {
                    // Whatever was on screen stays there.
                } finally {
                    this.loading = false;
                }
            },
            reload() {
                this.offset = 0;
                this.load();
            },
            debounced() {
                // One request per pause in typing, not one per keystroke.
                window.clearTimeout(this.timer);
                this.timer = window.setTimeout(() => this.reload(), 300);
            },
            prev() {
                this.offset = Math.max(0, this.offset - this.limit);
                this.load();
                window.scrollTo({ top: 0 });
            },
            next() {
                if (!this.hasNext) return;

                this.offset = this.offset + this.limit;
                this.load();
                window.scrollTo({ top: 0 });
            }
        },
        created() {
            this.setTitle("Beatmaps");

            const params = new URLSearchParams(window.location.search);

            this.query = params.get("q") || "";
            this.status = params.get("status") || "";
            this.mode = params.get("mode") || "";
            this.server = params.get("server") || "";
            this.sort = params.get("sort") || "updated";
            this.offset = Math.max(0, parseInt(params.get("offset") || "0", 10) || 0);

            try {
                this.layout = window.localStorage.getItem("beatmaps:layout") === "list"
                    ? "list"
                    : "grid";
            } catch (e) {
                this.layout = "grid";
            }

            this.load();
        }
    });
</script>
