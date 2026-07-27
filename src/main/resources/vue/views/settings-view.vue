<template id="settings-view">
    <div class="page">
        <site-nav></site-nav>

        <koneko-slot name="settings.top"></koneko-slot>
        <koneko-slot name="page.top"></koneko-slot>

        <section class="card" v-if="loading">
            <div class="skeleton skeleton-title"></div>
            <div class="skeleton-lines">
                <div class="skeleton skeleton-line" v-for="n in 4" :key="n"></div>
            </div>
        </section>

        <section class="card" v-else-if="!info">
            <h2>Settings</h2>
            <p class="muted">{{ error || "Log in to change your account." }}</p>
            <a class="button" href="/login">Log in</a>
        </section>

        <template v-else>
            <section class="card settings-head">
                <img class="settings-avatar" :src="avatar" :alt="info.name">

                <div>
                    <h2>{{ info.name }}</h2>
                    <p class="muted small">
                        <span :class="flagClass(info.country)"></span>
                        {{ (info.country || "").toUpperCase() }}
                        &middot; joined {{ fmtDate(info.creation_time) }}
                    </p>
                    <p class="settings-tags">
                        <span class="badge" v-if="info.custom_badge_name">{{ info.custom_badge_name }}</span>
                        <span class="badge badge-quiet" v-if="donor">Supporter until {{ fmtDate(info.donor_end) }}</span>
                        <span class="badge badge-warn" v-if="silenced">Silenced until {{ fmtDate(info.silence_end) }}</span>
                    </p>
                    <p class="muted small">
                        <a :href="'/u/' + info.id">View public profile</a>
                    </p>
                </div>
            </section>

            <p class="form-note" v-if="notice">{{ notice }}</p>
            <p class="form-error" v-if="error">{{ error }}</p>

            <section class="card">
                <h3>Profile</h3>
                <p class="muted small">Everything here is shown on your public profile.</p>

                <form class="stack" @submit.prevent="save('update', profile, 'Profile saved.')">
                    <label class="field">
                        <span class="field-label">Default mode</span>
                        <select class="filter-input" v-model.number="profile.preferred_mode">
                            <option v-for="mode in modes" :key="mode" :value="mode">
                                {{ modeName(mode) }}
                            </option>
                        </select>
                    </label>

                    <label class="field">
                        <span class="field-label">Play style</span>
                        <span class="checks">
                            <label class="check" v-for="style in playStyles" :key="style.bit">
                                <input type="checkbox" :value="style.bit" v-model="styleBits">
                                <span>{{ style.label }}</span>
                            </label>
                        </span>
                    </label>

                    <label class="field">
                        <span class="field-label">Badge name</span>
                        <input class="filter-input" type="text" v-model="profile.custom_badge_name"
                            placeholder="Shown next to your name">
                    </label>

                    <label class="field">
                        <span class="field-label">Badge icon</span>
                        <input class="filter-input" type="text" v-model="profile.custom_badge_icon"
                            placeholder="An icon name, e.g. fas fa-star">
                    </label>

                    <label class="field">
                        <span class="field-label">Userpage</span>
                        <textarea class="filter-input" rows="8" v-model="profile.userpage_content"
                            placeholder="Tell people about yourself"></textarea>
                    </label>

                    <button class="button" type="submit" :disabled="busy">Save profile</button>
                </form>
            </section>

            <section class="card">
                <h3>Email</h3>
                <p class="muted small">Currently {{ info.email }}.</p>

                <form class="stack" @submit.prevent="save('email', email, 'Email changed.')">
                    <label class="field">
                        <span class="field-label">New email</span>
                        <input class="filter-input" type="email" v-model="email.email" required>
                    </label>

                    <label class="field">
                        <span class="field-label">Current password</span>
                        <input class="filter-input" type="password" v-model="email.current_password"
                            autocomplete="current-password" required>
                    </label>

                    <button class="button" type="submit" :disabled="busy">Change email</button>
                </form>
            </section>

            <section class="card">
                <h3>Password</h3>
                <p class="muted small">
                    Changing it signs you out here and everywhere else, the game client
                    included.
                </p>

                <form class="stack" @submit.prevent="changePassword">
                    <label class="field">
                        <span class="field-label">Current password</span>
                        <input class="filter-input" type="password" v-model="password.current_password"
                            autocomplete="current-password" required>
                    </label>

                    <label class="field">
                        <span class="field-label">New password</span>
                        <input class="filter-input" type="password" v-model="password.new_password"
                            autocomplete="new-password" required>
                    </label>

                    <label class="field">
                        <span class="field-label">Repeat new password</span>
                        <input class="filter-input" type="password" v-model="passwordRepeat"
                            autocomplete="new-password" required>
                    </label>

                    <button class="button" type="submit" :disabled="busy">Change password</button>
                </form>
            </section>

            <section class="card card-danger">
                <h3>Delete account</h3>
                <p class="muted small">
                    Your scores, your profile and your name go with it, and none of it
                    can be brought back.
                </p>

                <form class="stack" @submit.prevent="deleteAccount">
                    <label class="field">
                        <span class="field-label">Type your name to confirm</span>
                        <input class="filter-input" type="text" v-model="deleteName"
                            :placeholder="info.name">
                    </label>

                    <label class="field">
                        <span class="field-label">Current password</span>
                        <input class="filter-input" type="password" v-model="remove.current_password"
                            autocomplete="current-password" required>
                    </label>

                    <button class="button button-danger" type="submit"
                        :disabled="busy || deleteName !== info.name">
                        Delete my account
                    </button>
                </form>
            </section>
        </template>

        <koneko-slot name="page.bottom"></koneko-slot>
        <site-footer></site-footer>
    </div>
</template>

<script>
    /**
     * The own account. Everything on this page goes through this service rather
     * than straight to the API, because all of it needs the access token that
     * the session holds - the browser never has one.
     *
     * The API asks for the current password again on the email, password and
     * delete forms, so those three ask for it here too.
     */
    app.component("settings-view", {
        template: "#settings-view",
        data: () => ({
            loading: true,
            busy: false,
            error: "",
            notice: "",
            info: null,
            scope: "",
            modes: [0, 1, 2, 3, 4, 5, 6, 8],
            // The bits of the play style field, as the game client sends them.
            playStyles: [
                { bit: 1, label: "Mouse" },
                { bit: 2, label: "Tablet" },
                { bit: 4, label: "Keyboard" },
                { bit: 8, label: "Touchscreen" }
            ],
            styleBits: [],
            profile: {
                userpage_content: "",
                preferred_mode: 0,
                play_style: 0,
                custom_badge_name: "",
                custom_badge_icon: ""
            },
            email: { email: "", current_password: "" },
            password: { new_password: "", current_password: "" },
            passwordRepeat: "",
            remove: { current_password: "" },
            deleteName: ""
        }),
        computed: {
            avatar() {
                return "https://a." + this.$koneko.domain + "/" + this.info.id;
            },
            donor() {
                return this.info.donor_end && this.asDate(this.info.donor_end) > new Date();
            },
            silenced() {
                return this.info.silence_end && this.asDate(this.info.silence_end) > new Date();
            }
        },
        methods: {
            async load() {
                try {
                    const answer = await this.session("GET", "/account/me");

                    this.info = (answer && answer.info) || null;
                    this.scope = (answer && answer.scope) || "";

                    if (this.info) {
                        this.profile.userpage_content = this.info.userpage_content || "";
                        this.profile.preferred_mode = this.info.preferred_mode || 0;
                        this.profile.custom_badge_name = this.info.custom_badge_name || "";
                        this.profile.custom_badge_icon = this.info.custom_badge_icon || "";
                        this.styleBits = this.playStyles
                            .filter(style => (this.info.play_style & style.bit) !== 0)
                            .map(style => style.bit);
                    }
                } catch (e) {
                    // 401 is not an error worth shouting about: it only means
                    // the session is gone, and the card below says so.
                    if (e && e.status !== 401) this.error = e.message;
                } finally {
                    this.loading = false;
                }
            },
            async save(action, body, done) {
                if (this.busy) return;

                this.busy = true;
                this.error = "";
                this.notice = "";

                if (action === "update") {
                    body.play_style = this.styleBits.reduce((sum, bit) => sum + bit, 0);
                }

                try {
                    await this.session("POST", "/account/" + action, body);

                    this.notice = done;

                    return true;
                } catch (e) {
                    this.error = e.message;

                    return false;
                } finally {
                    this.busy = false;
                }
            },
            async changePassword() {
                if (this.password.new_password !== this.passwordRepeat) {
                    this.error = "The two new passwords are not the same.";

                    return;
                }

                const ok = await this.save("password", this.password, "Password changed.");

                // The session is gone on the server the moment this succeeds,
                // so the browser is sent to the login page rather than left on
                // a page whose every button would now fail.
                if (ok) window.location.href = "/login";
            },
            async deleteAccount() {
                if (this.deleteName !== this.info.name) return;

                const ok = await this.save("delete", this.remove, "Account deleted.");

                if (ok) window.location.href = "/";
            }
        },
        created() {
            this.setTitle("Settings");
            this.load();
        }
    });
</script>
