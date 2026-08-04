<template id="reset-password-view">
    <div class="page">
        <site-nav></site-nav>

        <koneko-slot name="page.top"></koneko-slot>

        <section class="card card-narrow">
            <h2>Choose a new password</h2>

            <p class="muted" v-if="checking">Checking the link...</p>

            <!-- An unusable link is said so before any form is drawn: nobody should think of a
                 password, type it twice and only then be told the link expired last week. -->
            <template v-else-if="invalid">
                <p class="error">{{ invalid }}</p>

                <p class="muted small">
                    Reset links are handed out by staff and last a limited time, and each one
                    works once. Ask on the Discord for a new one.
                </p>

                <p><a class="button button-ghost button-small" href="/">Back to the front page</a></p>
            </template>

            <template v-else-if="done">
                <p>Your password has been changed. You can log in with it now.</p>

                <p class="muted small">
                    Anyone who was signed in to this account in the game has been
                    disconnected, including you.
                </p>

                <p><a class="button" href="/login">Log in</a></p>
            </template>

            <template v-else>
                <p class="muted">
                    You are setting the password of <strong>{{ username }}</strong>.
                    If that is not your account, close this page.
                </p>

                <form @submit.prevent="submit">
                    <!-- Present so browsers offer to save the new password against the right
                         account, and disabled so the name cannot be edited here. -->
                    <input type="text" autocomplete="username" :value="username" hidden readonly>

                    <label>
                        New password
                        <input type="password" v-model="password" autocomplete="new-password"
                            minlength="8" maxlength="32" required>
                    </label>

                    <label>
                        Repeat it
                        <input type="password" v-model="repeat" autocomplete="new-password"
                            minlength="8" maxlength="32" required>
                    </label>

                    <p class="muted small">
                        Between 8 and 32 characters, with more than three different ones.
                    </p>

                    <p class="error" v-if="error">{{ error }}</p>

                    <button class="button" type="submit" :disabled="busy">
                        {{ busy ? "Saving..." : "Set password" }}
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
     * Redeeming a password reset link.
     *
     * This page is the other half of the panel's "Reset password" button. Staff can mint a
     * ticket for an account but never see or choose a password: the player brings the ticket
     * here and types their own, so a forgotten password does not end with a member of staff
     * knowing what the new one is.
     *
     * The ticket in the query string is the only credential involved, which is why the route
     * is open to anyone - somebody who has forgotten their password has nothing to log in
     * with, and that is the whole reason they were given a link.
     */
    app.component("reset-password-view", {
        template: "#reset-password-view",
        data: () => ({
            token: "",
            username: "",
            password: "",
            repeat: "",
            checking: true,
            invalid: "",
            error: "",
            busy: false,
            done: false
        }),
        methods: {
            /** Reads the ticket without spending it, so a reload does not burn the link. */
            async check() {
                if (!this.token) {
                    this.invalid = "This address is missing its reset token.";
                    this.checking = false;
                    return;
                }

                try {
                    // Fetched by hand rather than through the api() helper, which reports a
                    // status code: the API explains why a link is dead, and that sentence is
                    // the only thing worth showing here.
                    const response = await fetch(this.apiBase() + "/users/password/reset?token="
                        + encodeURIComponent(this.token), {
                        headers: { "Accept": "application/json" },
                        credentials: "omit"
                    });

                    const body = await response.json().catch(() => ({}));

                    if (!response.ok) {
                        // Only the API's own sentence is shown. Anything else answering here -
                        // a proxy, or a build without this endpoint - reports a bare status
                        // code, which means nothing to whoever opened the link.
                        this.invalid = typeof body.status === "string" && body.status.trim()
                            ? body.status
                            : "This password reset link is no longer valid.";
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
                if (this.password !== this.repeat) {
                    this.error = "The two passwords are not the same.";
                    return;
                }

                this.busy = true;
                this.error = "";

                try {
                    // Straight to the API rather than through a web route: there is no session
                    // to attach, and the ticket is what authorises the change.
                    const response = await fetch(this.apiBase() + "/users/password/reset", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        credentials: "omit",
                        body: JSON.stringify({
                            token: this.token,
                            new_password: this.password
                        })
                    });

                    const body = await response.json().catch(() => ({}));

                    if (!response.ok) {
                        this.error = typeof body.status === "string" && body.status.trim()
                            ? body.status
                            : "The password could not be changed.";
                        return;
                    }

                    // Nothing is kept around afterwards: the ticket is spent and the password
                    // has no business staying in memory.
                    this.password = "";
                    this.repeat = "";
                    this.done = true;
                } catch (e) {
                    this.error = "The server could not be reached.";
                } finally {
                    this.busy = false;
                }
            }
        },
        created() {
            this.token = new URLSearchParams(window.location.search).get("token") || "";
            this.setTitle("Reset password");
            this.check();
        }
    });
</script>
