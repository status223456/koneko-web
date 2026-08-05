<template id="verify-client-view">
    <div class="page">
        <site-nav></site-nav>

        <koneko-slot name="page.top"></koneko-slot>

        <section class="card card-narrow">
            <h2>Verify this computer</h2>

            <p class="muted" v-if="checking">Looking for the login...</p>

            <!-- A dead link is said so before a code is asked for: nobody should dig their
                 phone out only to be told the login it belonged to expired. -->
            <template v-else-if="invalid">
                <p class="error">{{ invalid }}</p>

                <p class="muted small">
                    This page is opened by the game when an account with two factor
                    authentication logs in from a computer it has not seen before. Start osu!
                    and try to log in again to get here with a fresh link.
                </p>

                <p><a class="button button-ghost button-small" href="/">Back to the front page</a></p>
            </template>

            <template v-else-if="done">
                <p>This computer is verified. Log in again in the game and it will let you in.</p>

                <p class="muted small">
                    It will not ask again here for the next six months. Turning two factor
                    authentication off and on again in the settings forgets it, and so does
                    changing computers.
                </p>

                <p><a class="button" href="/settings">Settings</a></p>
            </template>

            <template v-else>
                <p class="muted">
                    Somebody is logging in as <strong>{{ username }}</strong> from this
                    computer. Enter the code from your authenticator app to allow it.
                    <br>
                    If that is not you, close this page and change your password.
                </p>

                <form class="stack" @submit.prevent="submit">
                    <label class="field">
                        <span class="field-label">Code from your app</span>
                        <input class="filter-input code-input" type="text" v-model="code"
                            inputmode="numeric" autocomplete="one-time-code" maxlength="7"
                            placeholder="000000" required autofocus>
                    </label>

                    <p class="error" v-if="error">{{ error }}</p>

                    <button class="button" type="submit" :disabled="busy">
                        {{ busy ? "Checking..." : "Verify" }}
                    </button>
                </form>
            </template>
        </section>

        <koneko-slot name="page.bottom"></koneko-slot>

        <site-footer></site-footer>
    </div>
</template>

<script>
    /**
     * Answering a login that is waiting on a two factor code.
     *
     * The game client opens this page by itself: when the server refuses a login with the
     * verification reply, the client goes to osu.<domain>/client-verifications/create with the
     * hash of the machine it is running on, and that address redirects here.
     *
     * There is no session involved, on purpose. Whoever is looking at this page is somebody who
     * cannot get past a login screen, and requiring them to log in on the website first would
     * be a circle. The hash in the address names the login being answered and the code from the
     * account's authenticator settles it; the hash alone gives nothing away.
     */
    app.component("verify-client-view", {
        template: "#verify-client-view",
        data: () => ({
            clientHash: "",
            username: "",
            code: "",
            checking: true,
            invalid: "",
            error: "",
            busy: false,
            done: false
        }),
        methods: {
            /** Reads who is waiting, without spending anything: a reload is harmless. */
            async check() {
                if (!this.clientHash) {
                    this.invalid = "This address is missing its client hash.";
                    this.checking = false;
                    return;
                }

                try {
                    // Fetched by hand rather than through the api() helper, which reports a
                    // status code: the API explains why a link is dead, and that sentence is
                    // the only thing worth showing here.
                    const response = await fetch(this.apiBase() + "/client-verification?ch="
                        + encodeURIComponent(this.clientHash), {
                        headers: { "Accept": "application/json" },
                        credentials: "omit"
                    });

                    const body = await response.json().catch(() => ({}));

                    if (!response.ok) {
                        this.invalid = typeof body.status === "string" && body.status.trim()
                            ? body.status
                            : "This verification link is no longer valid.";
                        return;
                    }

                    this.username = body.username || "your account";
                } catch (e) {
                    this.invalid = "The server could not be reached.";
                } finally {
                    this.checking = false;
                }
            },
            async submit() {
                const code = this.code.replace(/[^0-9]/g, "");

                if (code.length !== 6) {
                    this.error = "The code is six digits.";
                    return;
                }

                this.busy = true;
                this.error = "";

                try {
                    const response = await fetch(this.apiBase() + "/client-verification", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        credentials: "omit",
                        body: JSON.stringify({ ch: this.clientHash, code: code })
                    });

                    const body = await response.json().catch(() => ({}));

                    if (!response.ok) {
                        this.error = typeof body.status === "string" && body.status.trim()
                            ? body.status
                            : "That code could not be checked.";
                        return;
                    }

                    this.code = "";
                    this.done = true;
                } catch (e) {
                    this.error = "The server could not be reached.";
                } finally {
                    this.busy = false;
                }
            }
        },
        created() {
            this.clientHash = new URLSearchParams(window.location.search).get("ch") || "";
            this.setTitle("Verify this computer");
            this.check();
        }
    });
</script>
