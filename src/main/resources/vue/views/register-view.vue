<template id="register-view">
    <div class="page">
        <site-nav></site-nav>

        <koneko-slot name="register.top"></koneko-slot>
        <koneko-slot name="page.top"></koneko-slot>

        <section class="card card-narrow">
            <h2>Create an account on {{ site.name }}</h2>

            <p class="muted" v-if="user">
                You are already logged in as {{ user.name }}.
                <a :href="'/u/' + user.id">Go to your profile</a>.
            </p>

            <p class="muted" v-else-if="!registration.enabled">
                Registration is currently closed. Ask on the Discord if you think this is a mistake.
            </p>

            <form v-else @submit.prevent="submit">
                <label>
                    Username
                    <input type="text" v-model="username" autocomplete="username"
                        minlength="2" maxlength="15" required>
                </label>

                <p class="muted small">
                    2 to 15 characters. Letters, digits, and <code>_ - [ ]</code>; either spaces or
                    underscores, not both. This is the name you log into osu! with.
                </p>

                <label>
                    Email
                    <input type="email" v-model="email" autocomplete="email" maxlength="254" required>
                </label>

                <label>
                    Password
                    <input type="password" v-model="password" autocomplete="new-password"
                        minlength="8" maxlength="32" required>
                </label>

                <label>
                    Repeat password
                    <input type="password" v-model="confirmation" autocomplete="new-password"
                        minlength="8" maxlength="32" required>
                </label>

                <p class="muted small">
                    8 to 32 characters, and more than three different ones.
                </p>

                <!-- The Cloudflare Turnstile widget renders itself into this box.
                     It only exists when a site key is configured, and its answer
                     is verified by the backend, never here. -->
                <div class="captcha" v-if="captchaEnabled" ref="captcha"></div>

                <p class="error" v-if="error">{{ error }}</p>

                <button class="button" type="submit" :disabled="busy">
                    {{ busy ? "Creating your account..." : "Create account" }}
                </button>
            </form>

            <p class="muted small" v-if="!user">
                Already have an account? <a href="/login">Log in</a>.
                You can also register from inside the game: start osu! with
                <code>-devserver {{ domain }}</code>.
            </p>
        </section>

        <koneko-slot name="page.bottom"></koneko-slot>
        <koneko-slot name="register.bottom"></koneko-slot>

        <site-footer></site-footer>
    </div>
</template>

<script>
    app.component("register-view", {
        template: "#register-view",
        data: () => ({
            username: "",
            email: "",
            password: "",
            confirmation: "",
            error: "",
            busy: false,

            // Filled by the Turnstile callback, cleared after every attempt:
            // a token may only be verified once.
            captchaToken: "",
            widgetId: null
        }),
        computed: {
            site() {
                return this.$koneko.site || {};
            },
            user() {
                return this.$koneko.user;
            },
            domain() {
                return this.$koneko.domain;
            },
            registration() {
                return this.$koneko.registration || { enabled: false };
            },
            captchaEnabled() {
                return !!(this.registration.captcha && this.registration.turnstileSiteKey);
            }
        },
        methods: {
            async submit() {
                if (this.password !== this.confirmation) {
                    this.error = "The two passwords do not match.";
                    return;
                }

                if (this.captchaEnabled && !this.captchaToken) {
                    this.error = "Please complete the captcha.";
                    return;
                }

                this.busy = true;
                this.error = "";

                try {
                    const response = await fetch("/auth/register", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({
                            username: this.username,
                            email: this.email,
                            password: this.password,
                            password_confirmation: this.confirmation,
                            "cf-turnstile-response": this.captchaToken
                        })
                    });

                    const body = await response.json();

                    if (!response.ok) {
                        this.error = body.status || "The account could not be created.";

                        // The token was spent on this attempt, whatever the
                        // outcome, so a new challenge is needed for the next one.
                        this.resetCaptcha();
                        return;
                    }

                    // The account exists but no session was handed over; the
                    // login form is the only sensible place to go.
                    if (body.status === "created") {
                        window.location.href = "/login";
                        return;
                    }

                    // Straight to the verification page: the account exists and is logged in,
                    // but nothing else on the site will answer it until it has been claimed
                    // from inside the game. Sending them to a profile would only bounce them
                    // here anyway.
                    window.location.href = "/verify";
                } catch (e) {
                    this.error = "The server could not be reached.";
                    this.resetCaptcha();
                } finally {
                    this.busy = false;
                }
            },

            /**
             * Loads the Turnstile script once and renders the widget explicitly,
             * so no other page of the site pays for a script it never uses.
             */
            mountCaptcha() {
                if (!this.captchaEnabled || this.user) {
                    return;
                }

                if (window.turnstile) {
                    this.renderCaptcha();
                    return;
                }

                // Several mounts of this view share one script tag and one
                // onload callback queue.
                window.__konekoTurnstileQueue = window.__konekoTurnstileQueue || [];
                window.__konekoTurnstileQueue.push(() => this.renderCaptcha());

                if (window.__konekoTurnstileLoading) {
                    return;
                }

                window.__konekoTurnstileLoading = true;

                window.onloadKonekoTurnstile = () => {
                    const queue = window.__konekoTurnstileQueue || [];
                    window.__konekoTurnstileQueue = [];
                    queue.forEach(render => render());
                };

                const script = document.createElement("script");
                script.src = "https://challenges.cloudflare.com/turnstile/v0/api.js"
                    + "?render=explicit&onload=onloadKonekoTurnstile";
                script.async = true;
                script.defer = true;

                script.onerror = () => {
                    window.__konekoTurnstileLoading = false;
                    this.error = "The captcha could not be loaded. Please reload the page.";
                };

                document.head.appendChild(script);
            },

            renderCaptcha() {
                if (!window.turnstile || !this.$refs.captcha || this.widgetId !== null) {
                    return;
                }

                this.widgetId = window.turnstile.render(this.$refs.captcha, {
                    sitekey: this.registration.turnstileSiteKey,
                    callback: token => {
                        this.captchaToken = token;
                    },
                    "expired-callback": () => {
                        this.captchaToken = "";
                    },
                    "error-callback": () => {
                        this.captchaToken = "";
                    }
                });
            },

            resetCaptcha() {
                this.captchaToken = "";

                if (window.turnstile && this.widgetId !== null) {
                    window.turnstile.reset(this.widgetId);
                }
            }
        },
        created() {
            this.setTitle("Create an account");
        },
        mounted() {
            this.mountCaptcha();
        },
        unmounted() {
            if (window.turnstile && this.widgetId !== null) {
                window.turnstile.remove(this.widgetId);
                this.widgetId = null;
            }
        }
    });
</script>
