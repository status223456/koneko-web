<template id="verify-view">
    <div class="page">
        <site-nav></site-nav>

        <koneko-slot name="verify.top"></koneko-slot>
        <koneko-slot name="page.top"></koneko-slot>

        <section class="card card-narrow">
            <h2>One step left, {{ name }}</h2>

            <p class="muted">
                Your account exists, but it has to be claimed from inside the game before it can
                do anything on {{ site.name }}. Log into osu! once with it and everything unlocks
                by itself &mdash; there is nothing to click here.
            </p>

            <ol>
                <li>Close osu! if it is running.</li>
                <li>
                    Start it with <code>-devserver {{ domain }}</code>, so the game talks to this
                    server instead of the official one.
                </li>
                <li>
                    Log in as <strong>{{ name }}</strong> with the password you just chose.
                </li>
            </ol>

            <p class="muted small">
                This page checks by itself every few seconds and will take you to your profile the
                moment the login goes through.
            </p>

            <p class="error" v-if="error">{{ error }}</p>

            <div class="verify-actions">
                <button class="button" type="button" :disabled="busy" @click="check(true)">
                    {{ busy ? "Checking..." : "I have logged in" }}
                </button>

                <button class="button button-ghost" type="button" @click="logout">Log out</button>
            </div>

            <p class="muted small" v-if="discord">
                Cannot get in? Ask on <a :href="discord" rel="noopener">Discord</a>.
            </p>
        </section>

        <koneko-slot name="page.bottom"></koneko-slot>
        <koneko-slot name="verify.bottom"></koneko-slot>

        <site-footer></site-footer>
    </div>
</template>

<script>
    app.component("verify-view", {
        template: "#verify-view",
        data: () => ({
            busy: false,
            error: "",
            timer: null
        }),
        computed: {
            site() {
                return this.$koneko.site || {};
            },
            user() {
                return this.$koneko.user;
            },
            name() {
                return this.user ? this.user.name : "";
            },
            domain() {
                return this.$koneko.domain;
            },
            discord() {
                return (this.site.links || {}).discord;
            }
        },
        methods: {
            /**
             * Asks this service whether the account has been verified yet. The answer comes
             * from the API, so it flips within seconds of the in-game login.
             */
            async check(manual) {
                if (this.busy) {
                    return;
                }

                this.busy = true;

                try {
                    const response = await fetch("/data/verification", {
                        headers: { Accept: "application/json" }
                    });

                    const body = await response.json();

                    // The session is gone: nothing on this page applies any more.
                    if (!body.loggedIn) {
                        window.location.href = "/login";
                        return;
                    }

                    if (body.verified) {
                        // A full reload, so the next page load gets a state that no longer
                        // has the account locked.
                        window.location.href = "/u/" + this.user.id;
                        return;
                    }

                    if (manual === true) {
                        this.error = "Still not verified. Make sure the game is pointed at "
                            + this.domain + " and that the login succeeded.";
                    }
                } catch (e) {
                    this.error = "The server could not be reached.";
                } finally {
                    this.busy = false;
                }
            },

            /** Logging out is a POST, like everywhere else on the site: it changes something. */
            async logout() {
                await fetch("/auth/logout", { method: "POST" });

                window.location.href = "/";
            }
        },
        mounted() {
            this.setTitle("Verify your account");

            // Polled rather than pushed: the login happens in another program entirely, and a
            // few seconds of waiting is not worth a socket.
            this.timer = window.setInterval(() => this.check(false), 5000);
        },
        unmounted() {
            if (this.timer) {
                window.clearInterval(this.timer);
                this.timer = null;
            }
        }
    });
</script>
