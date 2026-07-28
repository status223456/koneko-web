<template id="admin-requests-view">
    <div class="admin-page">
        <section class="card">
            <h2>Rank requests</h2>
            <p class="muted small" v-if="count !== null">
                {{ fmtNumber(count) }} open {{ count === 1 ? "request" : "requests" }}
            </p>

            <label class="admin-check">
                <input type="checkbox" v-model="wholeSet">
                <span>Apply to every difficulty of the set</span>
            </label>

            <p class="muted small">
                A request is for one difficulty, but ranking is usually a decision about the whole
                set. With this on, deciding one closes every request for its siblings too.
            </p>
        </section>

        <section class="card">
            <div class="skeleton-rows" v-if="loading">
                <div class="skeleton skeleton-row" v-for="n in 5" :key="n"></div>
            </div>

            <template v-else>
                <p class="muted" v-if="error">{{ error }}</p>
                <p class="muted" v-else-if="!requests.length">Nothing is waiting. The queue is empty.</p>

                <div class="admin-request-list" v-else>
                    <article class="admin-request" v-for="request in requests" :key="request.request_id">
                        <!-- The card itself is the link to the map, so a nominator can
                             go and listen to it before deciding. -->
                        <a class="admin-request-body" :href="mapUrl(request)">
                            <img class="admin-request-cover" v-if="!request.missing"
                                :src="coverUrl(request.set_id, 'card')" :alt="request.title"
                                loading="lazy">

                            <div class="admin-request-text">
                                <template v-if="request.missing">
                                    <span class="admin-request-title">Beatmap {{ request.map_id }}</span>
                                    <span class="admin-request-meta warn">
                                        This beatmap is no longer cached. Reject to clear it.
                                    </span>
                                </template>

                                <template v-else>
                                    <span class="admin-request-title">
                                        {{ request.artist }} - {{ request.title }}
                                    </span>
                                    <span class="admin-request-meta">
                                        [{{ request.version }}] by {{ request.creator }}
                                    </span>
                                    <span class="admin-request-meta">
                                        {{ fmtDecimal(request.stars) }}* &middot;
                                        {{ fmtDecimal(request.bpm, 0) }} BPM &middot;
                                        {{ fmtLength(request.total_length) }} &middot;
                                        {{ statusName(request.status) }}
                                    </span>
                                </template>

                                <span class="admin-request-meta muted">
                                    Asked by {{ request.requested_by }},
                                    {{ fmtRelative(request.requested_at) }}
                                </span>
                            </div>
                        </a>

                        <div class="admin-request-actions" v-if="can('rank')">
                            <select class="filter-input" v-model.number="status" aria-label="Status">
                                <option :value="1">Ranked</option>
                                <option :value="2">Approved</option>
                                <option :value="4">Loved</option>
                            </select>

                            <button class="button button-small" :disabled="busy === request.map_id"
                                @click="resolve(request, 'accept')">Accept</button>

                            <button class="button button-ghost button-small"
                                :disabled="busy === request.map_id"
                                @click="resolve(request, 'reject')">Reject</button>
                        </div>
                    </article>
                </div>

                <button class="load-more" v-if="canLoad" :disabled="paging" @click="loadMore">
                    Load more
                </button>
            </template>
        </section>
    </div>
</template>

<script>
    /**
     * The nominator queue.
     *
     * Accepting changes what every player sees, so it is confirmed and the
     * resulting status is chosen explicitly rather than assumed. Rejecting only
     * closes the request and leaves the map exactly as it was.
     */
    app.component("admin-requests-view", {
        template: "#admin-requests-view",
        props: {
            /**
             * Handed down by admin-panel-view, which owns the shell now.
             *
             * The default refuses everything: a section rendered without it shows
             * no action buttons at all, which is the safe way to be wrong.
             */
            can: { type: Function, default: () => () => false }
        },
        data: () => ({
            loading: true,
            paging: false,
            busy: 0,
            error: "",
            requests: [],
            count: null,
            status: 1,
            wholeSet: false,
            pageSize: 50
        }),
        computed: {
            canLoad() {
                return this.count !== null && this.requests.length < Number(this.count);
            }
        },
        methods: {
            // A missing beatmap has no set to link to, so the row stays put.
            mapUrl(request) {
                if (request.missing || !request.set_id) return "#";

                return "/beatmapsets/" + request.set_id + "#/" + request.map_id;
            },
            async load(offset) {
                try {
                    const answer = await this.session("GET",
                        "/admin/api/requests?limit=" + this.pageSize + "&offset=" + (offset || 0));

                    const rows = (answer && answer.results) || [];

                    if (offset) {
                        this.requests.push(...rows);
                    } else {
                        this.requests = rows;
                    }

                    this.count = (answer && answer.count) !== undefined
                        ? answer.count
                        : this.requests.length;

                    this.error = "";
                } catch (e) {
                    this.error = e.message || "The queue could not be loaded.";
                } finally {
                    this.loading = false;
                }
            },
            async loadMore() {
                if (this.paging) return;

                this.paging = true;

                try {
                    await this.load(this.requests.length);
                } finally {
                    this.paging = false;
                }
            },
            async resolve(request, action) {
                const what = action === "accept"
                    ? "Accept as " + this.statusName(this.status) + "?"
                    : "Reject this request?";

                const scope = this.wholeSet && request.set_id
                    ? "\n\nThis applies to every difficulty of the set."
                    : "";

                if (!window.confirm(what + scope)) return;

                this.busy = request.map_id;

                try {
                    await this.session("POST", "/admin/api/requests-resolve", {
                        map_id: request.map_id,
                        action: action,
                        status: this.status,
                        whole_set: this.wholeSet
                    });

                    // Decided requests leave the queue rather than sitting there
                    // looking undecided until a reload.
                    this.requests = this.requests.filter(row => row.map_id !== request.map_id);

                    if (this.count !== null) this.count = Math.max(0, Number(this.count) - 1);
                } catch (e) {
                    window.alert(e.message || "That could not be done.");
                } finally {
                    this.busy = 0;
                }
            }
        },
        created() {
            this.setTitle("Requests");
            this.load(0);
        }
    });
</script>
