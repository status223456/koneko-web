<template id="login-view">
    <div class="page">
        <site-nav></site-nav>

        <koneko-slot name="login.top"></koneko-slot>
        <koneko-slot name="page.top"></koneko-slot>

        <section class="card card-narrow">
            <h2>Log in to {{ site.name }}</h2>

            <p class="muted" v-if="user">
                You are already logged in as {{ user.name }}.
                <a :href="'/u/' + user.id">Go to your profile</a>.
            </p>

            <form v-else @submit.prevent="submit">
                <label>
                    Username
                    <input type="text" v-model="username" autocomplete="username" required>
                </label>

                <label>
                    Password
                    <input type="password" v-model="password" autocomplete="current-password" required>
                </label>

                <p class="error" v-if="error">{{ error }}</p>

                <button class="button" type="submit" :disabled="busy">
                    {{ busy ? "Logging in..." : "Log in" }}
                </button>
            </form>

            <p class="muted small" v-if="registration.enabled">
                No account yet? <a href="/register">Create one</a>.
                You can also register from inside the game: start osu! with
                <code>-devserver {{ domain }}</code>.
            </p>

            <p class="muted small" v-else>
                Accounts are created in game. Start osu! with
                <code>-devserver {{ domain }}</code> and use <code>!register</code>,
                or ask on the Discord.
            </p>
        </section>

                <koneko-slot name="page.bottom"></koneko-slot>
        <koneko-slot name="login.bottom"></koneko-slot>

        <site-footer></site-footer>
    </div>
</template>

<script>
    app.component("login-view", {
        template: "#login-view",
        data: () => ({
            username: "",
            password: "",
            error: "",
            busy: false
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
            }
        },
        methods: {
            async submit() {
                this.busy = true;
                this.error = "";

                try {
                    const response = await fetch("/auth/login", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({ username: this.username, password: this.password })
                    });

                    const body = await response.json();

                    if (!response.ok) {
                        this.error = body.status || "Login failed.";
                        return;
                    }

                    // A full reload is deliberate: the session cookie is set,
                    // so the next page load gets a state with the user in it.
                    window.location.href = "/u/" + body.user.id;
                } catch (e) {
                    this.error = "The server could not be reached.";
                } finally {
                    this.busy = false;
                }
            }
        },
        created() {
            this.setTitle("Log in");
        }
    });
</script>
