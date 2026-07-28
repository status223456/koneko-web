<template id="admin-overview-view">
    <div class="admin-page">
        <section class="card">
            <h2>Staff panel</h2>

            <p class="muted small" v-if="$koneko.user">
                Signed in as {{ $koneko.user.name }}.
            </p>

            <p class="admin-badges">
                <span class="admin-badge is-good" v-for="role in access.roles" :key="role">
                    {{ role }}
                </span>
            </p>

            <p class="muted small">
                Every action here is written to the staff log with your name on it.
            </p>
        </section>

        <!-- Only the sections this account actually has are offered, so the panel
             never advertises a door that will not open. -->
        <section class="admin-stat-grid">
            <a class="card admin-stat admin-link-card" v-for="entry in entries" :key="entry.id"
                :href="entry.href">
                <span class="admin-stat-label">{{ entry.label }}</span>
                <span class="admin-stat-value">{{ entry.icon }}</span>
                <span class="muted small">{{ entry.hint }}</span>
            </a>
        </section>

        <section class="card" v-if="summary">
            <h3>Right now</h3>

            <table class="table">
                <tbody>
                    <tr><td>Players online</td><td>{{ fmtNumber(summary.online_players) }}</td></tr>
                    <tr><td>Registered accounts</td><td>{{ fmtNumber(summary.registered_players) }}</td></tr>
                    <tr><td>Restricted</td><td>{{ fmtNumber(summary.restricted_players) }}</td></tr>
                    <tr><td>Silenced</td><td>{{ fmtNumber(summary.silenced_players) }}</td></tr>
                </tbody>
            </table>
        </section>
    </div>
</template>

<script>
    /**
     * The landing page of the panel.
     *
     * A nominator sees one card here and a developer sees four, which is the
     * point: the first screen should tell you what your role is for without
     * making you discover it by clicking.
     *
     * This view reads the access document itself rather than taking it from the
     * shell's slot, because it needs the section list outside the template. The
     * request is the same one the shell makes and is served from the same short
     * lived session, so the second call is cheap.
     *
     * The counters only appear for accounts allowed to read server statistics.
     * Failing to fetch them is not worth an error message, so it stays silent.
     */
    app.component("admin-overview-view", {
        template: "#admin-overview-view",
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
            access: { roles: [], sections: [], actions: [] },
            summary: null
        }),
        computed: {
            entries() {
                const sections = this.access.sections || [];

                return [
                    {
                        id: "requests",
                        label: "Requests",
                        icon: "\u266A",
                        href: "/admin/requests",
                        hint: "Maps waiting on a rank decision"
                    },
                    {
                        id: "moderation",
                        label: "Moderation",
                        icon: "\u2691",
                        href: "/admin/moderation",
                        hint: "Find a player and act on them"
                    },
                    {
                        id: "logs",
                        label: "Staff log",
                        icon: "\u2261",
                        href: "/admin/logs",
                        hint: "Everything staff have done"
                    },
                    {
                        id: "server",
                        label: "Server",
                        icon: "\u25D4",
                        href: "/admin/server",
                        hint: "Memory, threads and load"
                    }
                ].filter(entry => sections.indexOf(entry.id) !== -1);
            }
        },
        methods: {
            async loadSummary() {
                try {
                    this.summary = await this.session("GET", "/admin/api/system");
                } catch (e) {
                    this.summary = null;
                }
            },
            async load() {
                try {
                    this.access = await this.session("GET", "/admin/api/access")
                        || { roles: [], sections: [], actions: [] };

                    if ((this.access.sections || []).indexOf("server") !== -1) {
                        await this.loadSummary();
                    }
                } catch (e) {
                    this.access = { roles: [], sections: [], actions: [] };
                }
            }
        },
        created() {
            this.setTitle("Staff panel");
            this.load();
        }
    });
</script>
