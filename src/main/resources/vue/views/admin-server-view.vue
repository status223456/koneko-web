<template id="admin-server-view">
    <div class="admin-page">
        <section class="card">
            <h2>Server load</h2>

            <p class="muted small" v-if="stats.sampled_at">
                Sampled {{ fmtRelative(stats.sampled_at) }}
                <span v-if="auto">&middot; refreshing every 5 seconds</span>
            </p>

            <div class="admin-action-row">
                <button class="button button-small" :disabled="loading" @click="load">Refresh</button>

                <label class="admin-check">
                    <input type="checkbox" v-model="auto">
                    <span>Keep refreshing</span>
                </label>
            </div>
        </section>

        <div class="skeleton-rows" v-if="loading && !stats.sampled_at">
            <div class="skeleton skeleton-row" v-for="n in 4" :key="n"></div>
        </div>

        <section class="card" v-else-if="error">
            <p class="muted">{{ error }}</p>
        </section>

        <template v-else>
            <section class="admin-stat-grid">
                <div class="card admin-stat">
                    <span class="admin-stat-label">Players online</span>
                    <span class="admin-stat-value">{{ fmtNumber(stats.online_players) }}</span>
                </div>

                <div class="card admin-stat">
                    <span class="admin-stat-label">Multiplayer matches</span>
                    <span class="admin-stat-value">{{ fmtNumber(stats.multiplayer_matches) }}</span>
                </div>

                <div class="card admin-stat">
                    <span class="admin-stat-label">Chat channels</span>
                    <span class="admin-stat-value">{{ fmtNumber(stats.chat_channels) }}</span>
                </div>

                <div class="card admin-stat">
                    <span class="admin-stat-label">Uptime</span>
                    <span class="admin-stat-value">{{ uptime }}</span>
                </div>
            </section>

            <section class="card">
                <h3>Memory</h3>

                <!-- Committed rather than max is the useful denominator: the heap
                     the process actually holds is what the machine is paying for. -->
                <div class="admin-meter">
                    <div class="admin-meter-fill" :style="{ width: heapPercent + '%' }"
                        :class="heapPercent > 85 ? 'is-hot' : ''"></div>
                </div>

                <p class="muted small">
                    Heap {{ mb(stats.heap_used) }} of {{ mb(stats.heap_committed) }} committed
                    <span v-if="stats.heap_max > 0">, {{ mb(stats.heap_max) }} maximum</span>
                    ({{ heapPercent }}%)
                </p>

                <table class="table">
                    <tbody>
                        <tr><td>Heap used</td><td>{{ mb(stats.heap_used) }}</td></tr>
                        <tr><td>Heap committed</td><td>{{ mb(stats.heap_committed) }}</td></tr>
                        <tr v-if="stats.heap_max > 0"><td>Heap maximum</td><td>{{ mb(stats.heap_max) }}</td></tr>
                        <tr><td>Off heap</td><td>{{ mb(stats.non_heap_used) }}</td></tr>
                        <tr><td>Runtime free</td><td>{{ mb(stats.memory_free) }}</td></tr>
                        <tr><td>Runtime total</td><td>{{ mb(stats.memory_total) }}</td></tr>
                    </tbody>
                </table>
            </section>

            <section class="card">
                <h3>Processor and threads</h3>

                <table class="table">
                    <tbody>
                        <tr><td>Cores</td><td>{{ stats.cpu_cores }}</td></tr>

                        <!-- These three are unavailable on some JVMs, which report a
                             negative number rather than failing. Showing "-1%" would
                             be worse than showing nothing. -->
                        <tr v-if="stats.cpu_process >= 0">
                            <td>This process</td>
                            <td>{{ fmtDecimal(stats.cpu_process * 100, 1) }}%</td>
                        </tr>
                        <tr v-if="stats.cpu_system >= 0">
                            <td>Whole machine</td>
                            <td>{{ fmtDecimal(stats.cpu_system * 100, 1) }}%</td>
                        </tr>
                        <tr v-if="stats.load_average >= 0">
                            <td>Load average</td>
                            <td>{{ fmtDecimal(stats.load_average, 2) }}</td>
                        </tr>

                        <tr><td>Threads</td><td>{{ fmtNumber(stats.threads) }}</td></tr>
                        <tr><td>Threads at peak</td><td>{{ fmtNumber(stats.threads_peak) }}</td></tr>
                        <tr><td>Collections</td><td>{{ fmtNumber(stats.gc_count) }}</td></tr>
                        <tr><td>Time collecting</td><td>{{ fmtNumber(stats.gc_time_ms) }} ms</td></tr>
                    </tbody>
                </table>
            </section>

            <section class="card">
                <h3>Accounts and build</h3>

                <table class="table">
                    <tbody>
                        <tr><td>Registered</td><td>{{ fmtNumber(stats.registered_players) }}</td></tr>
                        <tr><td>Restricted</td><td>{{ fmtNumber(stats.restricted_players) }}</td></tr>
                        <tr><td>Silenced</td><td>{{ fmtNumber(stats.silenced_players) }}</td></tr>
                        <tr><td>bancho.jar</td><td>{{ stats.version }}</td></tr>
                        <tr><td>Java</td><td>{{ stats.java_version }}</td></tr>
                        <tr><td>Host</td><td>{{ stats.os }}</td></tr>
                    </tbody>
                </table>
            </section>
        </template>
    </div>
</template>

<script>
    /**
     * What the machine is doing right now.
     *
     * The numbers come from the JVM's own management beans, so this page costs
     * almost nothing to open and needs no metrics stack behind it. It is not a
     * replacement for one: there is no history here, only the present moment,
     * which is what you want when someone says the server feels slow.
     */
    app.component("admin-server-view", {
        template: "#admin-server-view",
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
            stats: {},
            auto: false,
            timer: null
        }),
        computed: {
            heapPercent() {
                const total = Number(this.stats.heap_committed) || 0;
                if (!total) return 0;

                return Math.round((Number(this.stats.heap_used) || 0) / total * 100);
            },
            uptime() {
                const seconds = Math.floor((Number(this.stats.uptime_ms) || 0) / 1000);

                const days = Math.floor(seconds / 86400);
                const hours = Math.floor((seconds % 86400) / 3600);
                const minutes = Math.floor((seconds % 3600) / 60);

                if (days) return days + "d " + hours + "h";
                if (hours) return hours + "h " + minutes + "m";

                return minutes + "m";
            }
        },
        watch: {
            auto(on) {
                this.stop();

                if (on) this.timer = window.setInterval(this.load, 5000);
            }
        },
        methods: {
            mb(bytes) {
                const value = Number(bytes) || 0;

                if (value >= 1073741824) return this.fmtDecimal(value / 1073741824, 2) + " GiB";

                return this.fmtDecimal(value / 1048576, 1) + " MiB";
            },
            stop() {
                if (this.timer) {
                    window.clearInterval(this.timer);
                    this.timer = null;
                }
            },
            async load() {
                try {
                    this.stats = await this.session("GET", "/admin/api/system") || {};
                    this.error = "";
                } catch (e) {
                    this.error = e.message || "The statistics could not be loaded.";

                    // A failing poll that keeps firing turns one error into a
                    // stream of them.
                    this.auto = false;
                } finally {
                    this.loading = false;
                }
            }
        },
        created() {
            this.setTitle("Server");
            this.load();
        },
        unmounted() {
            this.stop();
        }
    });
</script>
