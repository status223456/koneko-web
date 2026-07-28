<template id="admin-logs-view">
    <div class="admin-page">
        <section class="card">
            <h2>Staff log</h2>
            <p class="muted small">
                Everything staff have done, newest first. This is the record, so it is
                readable but never editable.
            </p>

            <div class="filters">
                <label class="field">
                    <span class="field-label">Action</span>
                    <select class="filter-input" v-model="action" @change="reset">
                        <option value="">Everything</option>
                        <option value="restrict">Restrict</option>
                        <option value="unrestrict">Unrestrict</option>
                        <option value="silence">Silence</option>
                        <option value="unsilence">Unsilence</option>
                        <option value="wipe">Wipe</option>
                        <option value="supporter">Supporter</option>
                        <option value="privileges">Privileges</option>
                        <option value="name">Rename</option>
                        <option value="country">Country</option>
                        <option value="note">Note</option>
                        <option value="rank">Rank</option>
                    </select>
                </label>

                <p class="muted small" v-if="count !== null">
                    {{ fmtNumber(count) }} {{ count === 1 ? "entry" : "entries" }}
                </p>
            </div>
        </section>

        <section class="card">
            <div class="skeleton-rows" v-if="loading">
                <div class="skeleton skeleton-row" v-for="n in 6" :key="n"></div>
            </div>

            <template v-else>
                <p class="muted" v-if="error">{{ error }}</p>
                <p class="muted" v-else-if="!entries.length">Nothing has been recorded yet.</p>

                <ul class="admin-log-list" v-else>
                    <li class="admin-log" v-for="entry in entries" :key="entry.id">
                        <span class="admin-badge" :class="actionClass(entry.action)">
                            {{ entry.action }}
                        </span>

                        <span class="admin-log-text">
                            <span class="admin-log-message">
                                <strong>{{ entry.from_name }}</strong>

                                <!-- A rank entry points at a beatmap, everything else
                                     at an account, so the same column links to two
                                     different places. -->
                                <template v-if="entry.action === 'rank'">
                                    &rarr; beatmap
                                    <a :href="'/b/' + entry.to_id">{{ entry.to_id }}</a>
                                </template>
                                <template v-else-if="entry.to_id">
                                    &rarr;
                                    <a :href="'/admin/moderation/' + entry.to_id">
                                        {{ entry.to_name }}
                                    </a>
                                </template>
                            </span>

                            <span class="admin-log-message" v-if="entry.message">{{ entry.message }}</span>

                            <span class="admin-log-meta muted">
                                {{ fmtRelative(entry.time) }} ({{ fmtDate(entry.time) }})
                            </span>
                        </span>
                    </li>
                </ul>

                <button class="load-more" v-if="canLoad" :disabled="paging" @click="loadMore">
                    Load more
                </button>
            </template>
        </section>
    </div>
</template>

<script>
    /**
     * The whole audit trail in one place.
     *
     * The per-player history answers "what happened to this account". This
     * answers the other question, "what has been happening lately", which is how
     * you notice a pattern: one moderator restricting far more than the rest, or
     * a burst of activity at an odd hour.
     */
    app.component("admin-logs-view", {
        template: "#admin-logs-view",
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
            error: "",
            action: "",
            entries: [],
            count: null,
            pageSize: 50
        }),
        computed: {
            canLoad() {
                return this.count !== null && this.entries.length < Number(this.count);
            }
        },
        methods: {
            actionClass(action) {
                if (["restrict", "wipe"].indexOf(action) !== -1) return "is-bad";
                if (["silence", "privileges", "name"].indexOf(action) !== -1) return "is-warn";
                if (["unrestrict", "unsilence", "supporter", "rank"].indexOf(action) !== -1) return "is-good";

                return "";
            },
            reset() {
                this.entries = [];
                this.count = null;
                this.loading = true;
                this.load(0);
            },
            async load(offset) {
                try {
                    const parts = ["limit=" + this.pageSize, "offset=" + (offset || 0)];

                    if (this.action) parts.push("action=" + encodeURIComponent(this.action));

                    const answer = await this.session("GET", "/admin/api/logs?" + parts.join("&"));
                    const rows = (answer && answer.results) || [];

                    if (offset) {
                        this.entries.push(...rows);
                    } else {
                        this.entries = rows;
                    }

                    this.count = (answer && answer.count) !== undefined
                        ? answer.count
                        : this.entries.length;

                    this.error = "";
                } catch (e) {
                    this.error = e.message || "The log could not be loaded.";
                } finally {
                    this.loading = false;
                }
            },
            async loadMore() {
                if (this.paging) return;

                this.paging = true;

                try {
                    await this.load(this.entries.length);
                } finally {
                    this.paging = false;
                }
            }
        },
        created() {
            this.setTitle("Staff log");
            this.load(0);
        }
    });
</script>
