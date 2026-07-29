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
                return !!(this.registration.captcha
                    && this.registration.captchaSiteKey
                    && this.registration.captchaScript
                    && this.registration.captchaGlobal);
            }
        },
        methods: {
            captchaApi() {
                const global = this.registration.captchaGlobal;
                const api = global ? window[global] : null;

                return api && typeof api.render === "function" ? api : null;
            },

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
                    const payload = {
                        username: this.username,
                        email: this.email,
                        password: this.password,
                        password_confirmation: this.confirmation
                    };

                    // The token field is named by the provider, so the backend
                    // reads it back under the same name it told us to use.
                    if (this.captchaEnabled) {
                        payload[this.registration.captchaField] = this.captchaToken;
                    }

                    const response = await fetch("/auth/register", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify(payload)
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

            mountCaptcha() {
                if (!this.captchaEnabled || this.user) {
                    return;
                }

                if (this.captchaApi()) {
                    this.renderCaptcha();
                    return;
                }

                // Several mounts of this view share one script tag and one
                // onload callback queue.
                window.__konekoCaptchaQueue = window.__konekoCaptchaQueue || [];
                window.__konekoCaptchaQueue.push(() => this.renderCaptcha());

                if (window.__konekoCaptchaLoading) {
                    return;
                }

                window.__konekoCaptchaLoading = true;

                window.onloadKonekoCaptcha = () => {
                    const queue = window.__konekoCaptchaQueue || [];
                    window.__konekoCaptchaQueue = [];
                    queue.forEach(render => render());
                };

                const source = this.registration.captchaScript;
                const separator = source.indexOf("?") === -1 ? "?" : "&";

                const script = document.createElement("script");
                script.src = source + separator + "render=explicit&onload=onloadKonekoCaptcha";
                script.async = true;
                script.defer = true;

                script.onerror = () => {
                    window.__konekoCaptchaLoading = false;
                    this.error = "The captcha could not be loaded. Please reload the page.";
                };

                document.head.appendChild(script);
            },

            renderCaptcha() {
                const api = this.captchaApi();

                if (!api || !this.$refs.captcha || this.widgetId !== null) {
                    return;
                }

                this.widgetId = api.render(this.$refs.captcha, {
                    sitekey: this.registration.captchaSiteKey,
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

                const api = this.captchaApi();

                if (api && api.reset && this.widgetId !== null) {
                    api.reset(this.widgetId);
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
            const api = this.captchaApi();

            if (this.widgetId === null) {
                return;
            }

            if (api && typeof api.remove === "function") {
                api.remove(this.widgetId);
            } else if (api && api.reset) {
                api.reset(this.widgetId);
            }

            this.widgetId = null;
        }
    });
</script>
