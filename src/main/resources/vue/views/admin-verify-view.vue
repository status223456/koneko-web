<template id="admin-verify-view">
    <div class="page">
        <site-nav></site-nav>

        <koneko-slot name="page.top"></koneko-slot>

        <section class="card card-narrow">
            <h2>Staff verification</h2>

            <p class="muted" v-if="loading">Checking your account...</p>

            <template v-else-if="!enabled">
                <p class="muted">
                    The staff panel is only open to a session that has answered a code from an
                    authenticator, and this account does not have one yet. Set it up in your
                    settings and come back here.
                </p>

                <p class="muted small">
                    The same authenticator is what the game will ask for when you log in from a
                    computer it does not recognise.
                </p>

                <div class="row-buttons">
                    <a class="button" href="/settings">Open settings</a>

                    <button class="button button-ghost" type="button" :disabled="busy"
                        @click="load">
                        {{ busy ? "Checking..." : "I have set it up" }}
                    </button>
                </div>
            </template>

            <template v-else>
                <p class="muted">
                    Enter the six digits your authenticator is showing. This browser will not be
                    asked again for the rest of the day.
                </p>

                <form @submit.prevent="submit">
                    <input class="code-input" type="text" v-model="code" inputmode="numeric"
                        autocomplete="one-time-code" maxlength="7" placeholder="000000"
                        :disabled="busy">

                    <div class="row-buttons">
                        <button class="button" type="submit" :disabled="busy">
                            {{ busy ? "Checking..." : "Open the panel" }}
                        </button>
                    </div>
                </form>
            </template>

            <p class="error" v-if="error">{{ error }}</p>
        </section>

        <koneko-slot name="page.bottom"></koneko-slot>

        <site-footer></site-footer>
    </div>
</template>

<script>
    app.component("admin-verify-view", {
        template: "#admin-verify-view",
        data: () => ({
            loading: true,
            enabled: false,
            code: "",
            busy: false,
            error: ""
        }),
        methods: {
            /**
             * Whether this account has an authenticator, which decides which half of this page
             * is shown. Read again on demand, so somebody who just set one up in another tab
             * does not have to reload by hand.
             */
            async load() {
                if (this.busy) return;

                this.busy = true;
                this.error = "";

                try {
                    const answer = await this.session("GET", "/account/2fa");

                    this.enabled = !!(answer && answer.enabled);

                    if (!this.enabled) {
                        this.error = "Still nothing set up on this account.";
                    }
                } catch (e) {
                    this.error = e.message;
                } finally {
                    this.loading = false;
                    this.busy = false;
                }
            },
            async submit() {
                if (this.busy) return;

                const code = this.code.replace(/[^0-9]/g, "");

                if (code.length !== 6) {
                    this.error = "The code is six digits.";

                    return;
                }

                this.busy = true;
                this.error = "";

                try {
                    await this.session("POST", "/admin/verify", { code: code });

                    // A full load rather than a route change: the gate is on the server, and
                    // this is the request that finds it open.
                    window.location.href = "/admin";
                } catch (e) {
                    this.error = e.message;
                    this.code = "";
                } finally {
                    this.busy = false;
                }
            }
        },
        created() {
            this.setTitle("Staff verification");

            // The first load is not a click, so it must not be refused by the busy flag or
            // report "nothing set up" as an error before anything has been asked.
            this.session("GET", "/account/2fa").then(answer => {
                this.enabled = !!(answer && answer.enabled);
            }).catch(e => {
                this.error = e.message;
            }).then(() => {
                this.loading = false;
            });
        }
    });
</script>
