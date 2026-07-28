<template id="admin-moderation-view">
    <div class="admin-page">
        <section class="card">
            <h2>Moderation</h2>

            <!-- Only one tab exists today. It is drawn as a tab anyway because
                 clans and beatmap reports are the obvious next ones, and adding a
                 second tab later should not move this page around. -->
            <div class="admin-tabs">
                <span class="admin-tab is-active">Players</span>
            </div>

            <div class="filters">
                <label class="field admin-field-grow">
                    <span class="field-label">Name or id</span>
                    <input class="filter-input" type="search" v-model="search"
                        placeholder="Part of a name, or an exact id" @keyup.enter="reset">
                </label>

                <label class="field">
                    <span class="field-label">Show</span>
                    <select class="filter-input" v-model="filter" @change="reset">
                        <option value="all">Everyone</option>
                        <option value="online">Online now</option>
                        <option value="restricted">Restricted</option>
                        <option value="silenced">Silenced</option>
                        <option value="supporters">Supporters</option>
                        <option value="staff">Staff</option>
                    </select>
                </label>

                <button class="button" @click="reset">Search</button>
            </div>

            <p class="muted small" v-if="count !== null">
                {{ fmtNumber(count) }} matching {{ count === 1 ? "account" : "accounts" }}
            </p>
        </section>

        <section class="card">
            <div class="skeleton-rows" v-if="loading">
                <div class="skeleton skeleton-row" v-for="n in 6" :key="n"></div>
            </div>

            <template v-else>
                <p class="muted" v-if="error">{{ error }}</p>
                <p class="muted" v-else-if="!players.length">No account matches that.</p>

                <!-- A flat table, one account per line. A moderator scans this list
                     looking for one name: rows that stay one line high mean the
                     whole page can be read without scrolling past artwork. -->
                <div class="admin-table-wrap" v-else>
                    <table class="table admin-player-table">
                        <thead>
                            <tr>
                                <th>Player</th>
                                <th>Id</th>
                                <th>Status</th>
                                <th>Roles</th>
                                <th>Last seen</th>
                                <th class="admin-row-actions-head">Actions</th>
                            </tr>
                        </thead>

                        <tbody>
                            <tr v-for="player in players" :key="player.id">
                                <!-- The name opens this account's page in the panel, not
                                     the public profile: a moderator wants the history,
                                     not the player's best scores. -->
                                <td>
                                    <a class="admin-row-player" :href="'/admin/moderation/' + player.id">
                                        <span class="admin-dot" :class="player.online ? 'is-online' : ''"
                                            :title="player.online ? 'Online' : 'Offline'"></span>
                                        <span :class="flagClass(player.country)"
                                            v-if="flagClass(player.country)"></span>
                                        <span>{{ player.name }}</span>
                                    </a>
                                </td>

                                <td class="muted">#{{ player.id }}</td>

                                <td>
                                    <span class="admin-badges">
                                        <span class="admin-badge is-bad" v-if="player.restricted">restricted</span>
                                        <span class="admin-badge is-warn" v-if="player.silenced">silenced</span>
                                        <span class="admin-badge is-good" v-if="player.supporter">supporter</span>
                                        <span class="muted small"
                                            v-if="!player.restricted && !player.silenced && !player.supporter">
                                            &mdash;
                                        </span>
                                    </span>
                                </td>

                                <!-- Only the highest role, with a count for the rest and
                                     the full list on hover. Printing all of them is what
                                     made these rows tall in the first place, and the
                                     account's own page lists them anyway. -->
                                <td class="muted small" :title="(player.roles || []).join(', ')">
                                    {{ roleSummary(player) }}
                                </td>

                                <td class="muted small">{{ fmtRelative(player.latest_activity * 1000) }}</td>

                                <!-- The quick actions. Anything that cannot be undone
                                     lives on the player's own page instead, where the
                                     history is in view. -->
                                <td>
                                    <div class="admin-row-actions">
                                        <template v-if="can('restrict')">
                                            <button class="button button-small button-ghost"
                                                v-if="player.restricted" @click="act(player, 'unrestrict')">
                                                Unrestrict
                                            </button>
                                            <button class="button button-small" v-else
                                                @click="act(player, 'restrict')">Restrict</button>
                                        </template>

                                        <template v-if="can('silence')">
                                            <button class="button button-small button-ghost"
                                                v-if="player.silenced" @click="act(player, 'unsilence')">
                                                Unsilence
                                            </button>
                                            <button class="button button-small" v-else
                                                @click="act(player, 'silence')">Silence</button>
                                        </template>

                                        <button class="button button-small" v-if="can('supporter')"
                                            @click="act(player, 'donator')"
                                            title="Give supporter">Supporter</button>

                                        <button class="button button-small button-danger" v-if="can('wipe')"
                                            @click="act(player, 'wipe')">Wipe</button>
                                    </div>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>

                <button class="load-more" v-if="canLoad" :disabled="paging" @click="loadMore">
                    Load more
                </button>
            </template>
        </section>

        <admin-action-dialog :open="!!dialog"
            :title="dialog ? dialog.title : ''"
            :text="dialog ? dialog.text || '' : ''"
            :fields="dialog ? dialog.fields : []"
            :confirm-label="dialog ? dialog.confirm : 'Confirm'"
            :danger="dialog ? !!dialog.danger : false"
            :busy="dialogBusy" :error="dialogError"
            @close="closeDialog" @submit="runAction"></admin-action-dialog>
    </div>
</template>

<script>
    /**
     * The player list a moderator works from.
     *
     * Unlike the public directory this shows restricted and unverified accounts,
     * because those are the ones worth finding. Every destructive action asks for
     * a reason and confirms first: the reason is what the next moderator to open
     * this account will read, and it is the only part of the decision that
     * survives.
     */
    app.component("admin-moderation-view", {
        template: "#admin-moderation-view",
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
            search: "",
            filter: "all",
            players: [],
            count: null,
            pageSize: 60,
            dialog: null,
            dialogBusy: false,
            dialogError: ""
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
            /**
             * The highest role, plus how many are hidden.
             *
             * The API returns them most significant first, so the first entry is
             * the one that decides how this account is treated.
             */
            roleSummary(player) {
                const roles = (player && player.roles) || [];

                if (!roles.length) {
                    return "\u2014";
                }

                return roles.length > 1
                    ? roles[0] + " +" + (roles.length - 1)
                    : roles[0];
            },
            query(offset) {
                const parts = [
                    "limit=" + this.pageSize,
                    "offset=" + (offset || 0),
                    "filter=" + encodeURIComponent(this.filter)
                ];

                if (this.search.trim()) {
                    parts.push("q=" + encodeURIComponent(this.search.trim()));
                }

                return "/admin/api/players?" + parts.join("&");
            },
            reset() {
                this.players = [];
                this.count = null;
                this.loading = true;
                this.load(0);
            },
            async load(offset) {
                try {
                    const answer = await this.session("GET", this.query(offset));
                    const rows = (answer && answer.results) || [];

                    if (offset) {
                        this.players.push(...rows);
                    } else {
                        this.players = rows;
                    }

                    this.count = (answer && answer.count) !== undefined
                        ? answer.count
                        : this.players.length;

                    this.error = "";
                } catch (e) {
                    this.error = e.message || "The player list could not be loaded.";
                } finally {
                    this.loading = false;
                }
            },
            async loadMore() {
                if (this.paging) return;

                this.paging = true;

                try {
                    await this.load(this.players.length);
                } finally {
                    this.paging = false;
                }
            },
            /**
             * Opens the dialog for one action against one account.
             *
             * These were chains of window.prompt, which can only ask for a line
             * of text. Every value the server actually validates -- a mode
             * number, a privilege name -- was therefore typed from memory and
             * only checked once the request had already been sent. Anything from
             * a fixed set is now picked from a list. Durations stay typed: "30m"
             * is quicker to write than to find in a list.
             */
            act(player, action) {
                this.dialogError = "";
                this.dialog = Object.assign({ action: action, player: player },
                    this.spec(player, action));
            },
            modeOptions() {
                const options = [{ value: -1, label: "Every mode" }];

                for (let mode = 0; mode <= 8; mode += 1) {
                    options.push({ value: mode, label: this.modeName(mode) });
                }

                return options;
            },
            spec(player, action) {
                const who = player.name;
                const reason = required => ({
                    key: "reason",
                    label: "Reason",
                    required: required,
                    hint: "Kept in this account's staff history."
                });

                if (action === "restrict") {
                    return {
                        title: "Restrict " + who,
                        confirm: "Restrict",
                        danger: true,
                        fields: [reason(true)]
                    };
                }

                if (action === "unrestrict") {
                    return {
                        title: "Unrestrict " + who,
                        confirm: "Unrestrict",
                        fields: [reason(false)]
                    };
                }

                if (action === "silence") {
                    return {
                        title: "Silence " + who,
                        confirm: "Silence",
                        fields: [
                            {
                                key: "duration",
                                label: "Duration",
                                value: "1h",
                                required: true,
                                hint: "For example 30m, 2h or 1d."
                            },
                            reason(true)
                        ]
                    };
                }

                if (action === "unsilence") {
                    return {
                        title: "Unsilence " + who,
                        confirm: "Unsilence",
                        fields: [reason(false)]
                    };
                }

                if (action === "donator") {
                    return {
                        title: "Give supporter to " + who,
                        confirm: "Give supporter",
                        fields: [{
                            key: "duration",
                            label: "Duration",
                            value: "30d",
                            required: true,
                            hint: "For example 30d. Use 0 to take it away."
                        }]
                    };
                }

                if (action === "wipe") {
                    return {
                        title: "Wipe " + who,
                        text: "Every score and statistic in the chosen mode is removed."
                            + " This cannot be undone.",
                        confirm: "Wipe",
                        danger: true,
                        fields: [{
                            key: "mode",
                            label: "Mode",
                            type: "select",
                            options: this.modeOptions()
                        }]
                    };
                }

                return { title: "Confirm", confirm: "Confirm", fields: [] };
            },
            async runAction(values) {
                if (!this.dialog) return;

                const body = Object.assign({ user_id: this.dialog.player.id }, values);

                this.dialogBusy = true;
                this.dialogError = "";

                try {
                    await this.session("POST", "/admin/api/" + this.dialog.action, body);

                    this.dialog = null;

                    // Reloading the row is cheaper than reasoning about what the
                    // action did to it, and it cannot disagree with the server.
                    this.reset();
                } catch (e) {
                    // The dialog stays open carrying the failure, so whatever was
                    // just typed does not have to be typed again.
                    this.dialogError = e.message || "That could not be done.";
                } finally {
                    this.dialogBusy = false;
                }
            },
            closeDialog() {
                this.dialog = null;
                this.dialogError = "";
            }
        },
        created() {
            this.search = new URLSearchParams(window.location.search).get("q") || "";
            this.setTitle("Moderation");
            this.load(0);
        }
    });
</script>
