<template id="admin-player-view">
    <div class="admin-page">
        <div class="skeleton-rows" v-if="loading">
            <div class="skeleton skeleton-row" v-for="n in 5" :key="n"></div>
        </div>

        <section class="card" v-else-if="error">
            <p class="muted">{{ error }}</p>
            <a class="button button-ghost button-small" href="/admin/moderation">Back to the list</a>
        </section>

        <template v-else>
            <section class="card admin-profile">
                <img class="admin-profile-avatar" :src="avatar" :alt="player.name">

                <div class="admin-profile-text">
                    <h2>
                        <span class="admin-dot" :class="player.online ? 'is-online' : ''"></span>
                        {{ player.name }}
                    </h2>

                    <p class="muted small">
                        <span :class="flagClass(player.country)"></span>
                        #{{ player.id }} &middot; {{ email }} &middot;
                        joined {{ fmtDate(player.creation_time * 1000) }} &middot;
                        last seen {{ fmtRelative(player.latest_activity * 1000) }}
                    </p>

                    <p class="admin-badges">
                        <span class="admin-badge is-bad" v-if="player.restricted">restricted</span>
                        <span class="admin-badge is-warn" v-if="player.silenced">
                            silenced until {{ fmtDate(player.silence_end * 1000) }}
                        </span>
                        <span class="admin-badge is-good" v-if="player.supporter">
                            supporter until {{ fmtDate(player.donor_end * 1000) }}
                        </span>
                        <span class="admin-badge" v-for="role in player.roles" :key="role">{{ role }}</span>
                    </p>

                    <p>
                        <a class="button button-ghost button-small" :href="'/u/' + player.id">
                            Public profile
                        </a>
                        <a class="button button-ghost button-small" href="/admin/moderation">
                            Back to the list
                        </a>
                    </p>
                </div>
            </section>

            <section class="card">
                <h3>Actions</h3>

                <div class="admin-action-row">
                    <template v-if="can('restrict')">
                        <button class="button button-small button-ghost" v-if="player.restricted"
                            @click="act('unrestrict')">Unrestrict</button>
                        <button class="button button-small" v-else @click="act('restrict')">Restrict</button>
                    </template>

                    <template v-if="can('silence')">
                        <button class="button button-small button-ghost" v-if="player.silenced"
                            @click="act('unsilence')">Unsilence</button>
                        <button class="button button-small" v-else @click="act('silence')">Silence</button>
                    </template>

                    <button class="button button-small" v-if="can('note')" @click="act('note')">
                        Add note
                    </button>

                    <button class="button button-small" v-if="can('supporter')" @click="act('donator')">
                        Give supporter
                    </button>

                    <button class="button button-small" v-if="can('country')" @click="act('country')">
                        Change country
                    </button>

                    <button class="button button-small" v-if="can('rename')" @click="act('name')">
                        Rename
                    </button>

                    <button class="button button-small" v-if="can('privileges')"
                        @click="act('privileges-add')">Add privilege</button>

                    <button class="button button-small" v-if="can('privileges')"
                        @click="act('privileges-remove')">Remove privilege</button>

                    <button class="button button-small button-danger" v-if="can('wipe')"
                        @click="act('wipe')">Wipe</button>
                </div>
            </section>

            <!-- Play history sits above the action log on purpose: how much someone
                 has actually played is the context that decides whether a
                 restriction is proportionate. -->
            <section class="card" v-if="stats.length">
                <h3>Play history</h3>

                <table class="table">
                    <thead>
                        <tr>
                            <th>Mode</th>
                            <th>PP</th>
                            <th>Accuracy</th>
                            <th>Plays</th>
                            <th>Playtime</th>
                            <th>Ranked score</th>
                            <th>Max combo</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="row in stats" :key="row.mode">
                            <td>{{ modeName(row.mode) }}</td>
                            <td>{{ fmtNumber(Math.round(row.pp)) }}</td>
                            <td>{{ fmtAccuracy(row.accuracy / 100) }}</td>
                            <td>{{ fmtNumber(row.plays) }}</td>
                            <td>{{ fmtPlaytime(row.playtime) }}</td>
                            <td>{{ fmtNumber(row.ranked_score) }}</td>
                            <td>{{ fmtNumber(row.max_combo) }}</td>
                        </tr>
                    </tbody>
                </table>
            </section>

            <section class="card">
                <h3>Staff history</h3>
                <p class="muted small">{{ fmtNumber(logCount) }} recorded</p>

                <p class="muted" v-if="!logs.length">
                    Nothing has been done to this account yet.
                </p>

                <ul class="admin-log-list" v-else>
                    <li class="admin-log" v-for="entry in logs" :key="entry.id">
                        <span class="admin-badge" :class="actionClass(entry.action)">
                            {{ entry.action }}
                        </span>

                        <span class="admin-log-text">
                            <span class="admin-log-message" v-if="entry.message">{{ entry.message }}</span>
                            <span class="admin-log-message muted" v-else>No reason was given.</span>

                            <span class="admin-log-meta muted">
                                by {{ entry.from_name }} &middot;
                                {{ fmtRelative(entry.time) }} ({{ fmtDate(entry.time) }})
                            </span>
                        </span>
                    </li>
                </ul>
            </section>
        </template>
    </div>
</template>

<script>
    /**
     * One account, as staff need to see it: who they are, what has been done to
     * them and by whom, how much they have played, and the buttons to act.
     *
     * The history is the reason this page exists. bancho.jar could already
     * restrict and wipe accounts, but only ever wrote those decisions to the
     * application log, so the question "what happened to this player, and why"
     * had no answer that a moderator could reach.
     */
    app.component("admin-player-view", {
        template: "#admin-player-view",
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
            error: "",
            userId: 0,
            player: {},
            email: "",
            logs: [],
            logCount: 0,
            stats: []
        }),
        computed: {
            avatar() {
                return "https://a." + this.$koneko.domain + "/" + this.userId;
            }
        },
        methods: {
            actionClass(action) {
                if (["restrict", "wipe"].indexOf(action) !== -1) return "is-bad";
                if (["silence", "privileges", "name"].indexOf(action) !== -1) return "is-warn";
                if (["unrestrict", "unsilence", "supporter"].indexOf(action) !== -1) return "is-good";

                return "";
            },
            async load() {
                try {
                    const answer = await this.session("GET",
                        "/admin/api/player?user_id=" + this.userId);

                    this.player = (answer && answer.player) || {};
                    this.email = (answer && answer.email) || "unknown";
                    this.logs = (answer && answer.logs) || [];
                    this.logCount = (answer && answer.log_count) || 0;
                    this.stats = (answer && answer.stats) || [];
                    this.error = "";

                    this.setTitle(this.player.name || "Player");
                } catch (e) {
                    this.error = e.message || "That account could not be loaded.";
                } finally {
                    this.loading = false;
                }
            },
            async act(action) {
                let body = { user_id: this.userId };

                if (action === "restrict" || action === "unrestrict"
                    || action === "silence" || action === "unsilence") {

                    if (action === "silence") {
                        const duration = window.prompt("How long? For example 30m, 2h, 1d.", "1h");
                        if (duration === null) return;
                        body.duration = duration;
                    }

                    const reason = window.prompt("Reason:");
                    if (reason === null) return;
                    body.reason = reason;

                } else if (action === "note") {
                    const message = window.prompt("Note. Only staff will see this.");
                    if (message === null || !message.trim()) return;
                    body.message = message;

                } else if (action === "donator") {
                    const duration = window.prompt("Supporter for how long? For example 30d.", "30d");
                    if (duration === null) return;
                    body.duration = duration;

                } else if (action === "country") {
                    const country = window.prompt("Two letter country code, for example RU.");
                    if (country === null || !country.trim()) return;
                    body.country = country.trim();

                } else if (action === "name") {
                    const name = window.prompt("New username:", this.player.name);
                    if (name === null || !name.trim()) return;
                    body.name = name.trim();

                } else if (action === "privileges-add" || action === "privileges-remove") {
                    const privileges = window.prompt(
                        "Privilege names, comma separated. For example SUPPORTER, NOMINATOR.");

                    if (privileges === null || !privileges.trim()) return;

                    body.privileges = privileges.split(",")
                        .map(part => part.trim().toUpperCase())
                        .filter(part => part.length > 0);

                } else if (action === "wipe") {
                    if (!window.confirm("Wipe every score and statistic of " + this.player.name
                        + "?\n\nThis cannot be undone.")) return;

                    const mode = window.prompt("Which mode? 0 to 8, or -1 for all of them.", "-1");
                    if (mode === null) return;

                    body.mode = Number(mode);
                }

                try {
                    await this.session("POST", "/admin/api/" + action, body);

                    // The history has just gained a line, so the page is reloaded
                    // rather than patched: the new line is the point.
                    this.loading = true;
                    await this.load();
                } catch (e) {
                    window.alert(e.message || "That could not be done.");
                }
            }
        },
        created() {
            const parts = window.location.pathname.split("/").filter(part => part.length > 0);

            this.userId = Number(parts[parts.length - 1]) || 0;
            this.setTitle("Player");
            this.load();
        }
    });
</script>
