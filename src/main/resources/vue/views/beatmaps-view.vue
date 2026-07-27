<template id="beatmaps-view">
    <div class="page">
        <site-nav></site-nav>

        <koneko-slot name="beatmaps.top"></koneko-slot>
        <koneko-slot name="page.top"></koneko-slot>

        <section class="card">
            <h2>Beatmaps</h2>

            <div class="filters">
                <input class="filter-input"
                       type="search"
                       v-model="query"
                       @input="debounced"
                       placeholder="Artist, title, mapper or difficulty">

                <select class="filter-select" v-model="status" @change="reload">
                    <option value="">Any status</option>
                    <option value="1">Ranked</option>
                    <option value="2">Approved</option>
                    <option value="3">Qualified</option>
                    <option value="4">Loved</option>
                    <option value="0">Pending</option>
                    <option value="-1">Work in progress</option>
                    <option value="-2">Graveyard</option>
                </select>

                <select class="filter-select" v-model="mode" @change="reload">
                    <option value="">Any mode</option>
                    <option value="0">osu!</option>
                    <option value="1">taiko</option>
                    <option value="2">catch</option>
                    <option value="3">mania</option>
                </select>

                <select class="filter-select" v-model="server" @change="reload">
                    <option value="">Every set</option>
                    <option value="local">Submitted here</option>
                    <option value="osu">Mirrored from osu!</option>
                </select>

                <select class="filter-select" v-model="sort" @change="reload">
                    <option value="updated">Newest</option>
                    <option value="plays">Most played</option>
                    <option value="difficulty">Hardest</option>
                    <option value="title">Title</option>
                </select>
            </div>

            <p class="muted small" v-if="!loading">
                {{ fmtNumber(count) }} sets found
            </p>
        </section>

        <p class="muted" v-if="loading">Searching...</p>

        <section class="card" v-else-if="!sets.length">
            <h2>Nothing found</h2>
            <p class="muted">No beatmap matches this search yet.</p>
        </section>

        <template v-else>
            <div class="mapset-grid">
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
            offset: 0,
            limit: 24,
            count: 0,
            sets: [],
            timer: null
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

            this.load();
        }
    });
</script>
