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
                <button class="avatar-picker" type="button" @click="chooseAvatar"
                    aria-label="Choose a new profile picture">
                    <img class="settings-avatar" :src="avatar" :alt="info.name">
                    <span class="avatar-picker-label">Change</span>
                </button>
                <!--
                    Hidden inline as well as in the stylesheet: /css/koneko.css is
                    served from a stable path, so a stale cached copy must never be
                    able to expose the browser's own file button.
                -->
                <input ref="avatarInput" class="avatar-input" type="file" hidden
                    style="display: none"
                    accept="image/png,image/jpeg" @change="openAvatarEditor">

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

            <div class="avatar-editor-backdrop" v-if="avatarEditor.open"
                @click.self="closeAvatarEditor">
                <section class="avatar-editor" role="dialog" aria-modal="true"
                    aria-labelledby="avatar-editor-title">
                    <div class="avatar-editor-head">
                        <div>
                            <h3 id="avatar-editor-title">Crop profile picture</h3>
                            <p class="muted small">Drag a region, or move and resize it by its corners.</p>
                        </div>
                        <button class="avatar-editor-close" type="button"
                            @click="closeAvatarEditor" aria-label="Close">&times;</button>
                    </div>

                    <div ref="cropStage" class="avatar-crop-stage" :style="stageStyle"
                        @pointerdown="beginSelection" @pointermove="dragSelection"
                        @pointerup="endSelection" @pointercancel="endSelection">
                        <img ref="cropImage" class="avatar-crop-image" :style="imageStyle"
                            :src="avatarEditor.objectUrl" alt="" draggable="false"
                            @load="onCropImageLoad">

                        <div class="avatar-crop-shade" v-if="avatarEditor.crop.w > 0"
                            :style="shadeStyle"></div>

                        <div class="avatar-crop-box" v-if="avatarEditor.crop.w > 0"
                            :style="boxStyle" @pointerdown.stop="grabSelection($event, 'move')">
                            <span class="avatar-crop-handle handle-nw"
                                @pointerdown.stop="grabSelection($event, 'nw')"></span>
                            <span class="avatar-crop-handle handle-ne"
                                @pointerdown.stop="grabSelection($event, 'ne')"></span>
                            <span class="avatar-crop-handle handle-sw"
                                @pointerdown.stop="grabSelection($event, 'sw')"></span>
                            <span class="avatar-crop-handle handle-se"
                                @pointerdown.stop="grabSelection($event, 'se')"></span>
                        </div>
                    </div>

                    <p class="muted small avatar-crop-size">Selection: {{ cropSizeLabel }}</p>

                    <p class="form-error" v-if="avatarEditor.error">{{ avatarEditor.error }}</p>

                    <div class="avatar-editor-actions">
                        <button class="button button-quiet" type="button"
                            @click="resetSelection" :disabled="avatarEditor.saving">Reset</button>
                        <button class="button button-ghost" type="button"
                            @click="closeAvatarEditor" :disabled="avatarEditor.saving">Cancel</button>
                        <button class="button" type="button" @click="saveAvatar"
                            :disabled="avatarEditor.saving || avatarEditor.crop.w < 16">
                            {{ avatarEditor.saving ? "Saving…" : "Save picture" }}
                        </button>
                    </div>
                </section>
            </div>

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
            deleteName: "",
            avatarVersion: 0,
            avatarEditor: {
                open: false,
                image: null,
                objectUrl: "",
                crop: { x: 0, y: 0, w: 0, h: 0 },
                stage: { w: 0, h: 0 },
                mode: "",
                pointerId: null,
                originX: 0,
                originY: 0,
                startCrop: null,
                saving: false,
                error: ""
            }
        }),
        computed: {
            avatar() {
                const suffix = this.avatarVersion ? "?v=" + this.avatarVersion : "";
                return "https://a." + this.$koneko.domain + "/" + this.info.id + suffix;
            },
            donor() {
                return this.info.donor_end && this.asDate(this.info.donor_end) > new Date();
            },
            silenced() {
                return this.info.silence_end && this.asDate(this.info.silence_end) > new Date();
            },
            boxStyle() {
                const crop = this.avatarEditor.crop;
                return {
                    position: "absolute",
                    boxSizing: "border-box",
                    left: crop.x + "px",
                    top: crop.y + "px",
                    width: crop.w + "px",
                    height: crop.h + "px"
                };
            },
            shadeStyle() {
                const crop = this.avatarEditor.crop;
                // One element dims everything outside the selection.
                return {
                    position: "absolute",
                    inset: "0",
                    pointerEvents: "none",
                    clipPath: "polygon(0 0, 100% 0, 100% 100%, 0 100%, 0 " + crop.y + "px, "
                        + crop.x + "px " + crop.y + "px, "
                        + crop.x + "px " + (crop.y + crop.h) + "px, "
                        + (crop.x + crop.w) + "px " + (crop.y + crop.h) + "px, "
                        + (crop.x + crop.w) + "px " + crop.y + "px, 0 " + crop.y + "px)"
                };
            },
            // Sizing lives inline for the same reason the file input does: a
            // cached stylesheet must not be able to let the image run off screen.
            stageStyle() {
                return {
                    position: "relative",
                    display: "block",
                    width: "fit-content",
                    maxWidth: "100%",
                    margin: "16px auto 10px",
                    overflow: "hidden",
                    touchAction: "none",
                    userSelect: "none"
                };
            },
            imageStyle() {
                return {
                    display: "block",
                    maxWidth: "100%",
                    maxHeight: "52vh",
                    width: "auto",
                    height: "auto"
                };
            },
            cropSizeLabel() {
                const source = this.sourceRect();
                if (!source) return "none";
                return source.w + " \u00d7 " + source.h + " px";
            }
        },
        methods: {
            chooseAvatar() {
                this.$refs.avatarInput.click();
            },
            openAvatarEditor(event) {
                const file = event.target.files && event.target.files[0];
                event.target.value = "";
                if (!file) return;

                const allowed = ["image/png", "image/jpeg"];
                if (!allowed.includes(file.type)) {
                    this.error = "Choose a PNG or JPEG image.";
                    return;
                }
                if (file.size > 12 * 1024 * 1024) {
                    this.error = "The source image may be at most 12 MB.";
                    return;
                }

                const objectUrl = URL.createObjectURL(file);
                const image = new Image();
                image.onload = () => {
                    if (image.naturalWidth < 64 || image.naturalHeight < 64
                        || image.naturalWidth > 8192 || image.naturalHeight > 8192
                        || image.naturalWidth * image.naturalHeight > 40000000) {
                        URL.revokeObjectURL(objectUrl);
                        this.error = "Use an image between 64px and 8192px (40 megapixels max).";
                        return;
                    }

                    this.avatarEditor.image = image;
                    this.avatarEditor.objectUrl = objectUrl;
                    this.avatarEditor.crop = { x: 0, y: 0, w: 0, h: 0 };
                    this.avatarEditor.error = "";
                    this.avatarEditor.open = true;
                    this.$nextTick(() => {
                        this.measureStage();
                        this.resetSelection();
                        const close = this.$el.querySelector(".avatar-editor-close");
                        if (close) close.focus();
                    });
                };
                image.onerror = () => {
                    URL.revokeObjectURL(objectUrl);
                    this.error = "That file could not be decoded as an image.";
                };
                image.src = objectUrl;
            },
            // The natural size is only known once the file has decoded, so the
            // first selection is measured when the <img> reports it is loaded.
            onCropImageLoad() {
                this.measureStage();
                this.resetSelection();
            },
            // The displayed size of the image, which every selection coordinate is in.
            measureStage() {
                const image = this.$refs.cropImage;
                if (!image) return;
                this.avatarEditor.stage = { w: image.clientWidth, h: image.clientHeight };
            },
            // A sensible starting selection: the largest centred square.
            resetSelection() {
                this.measureStage();
                const stage = this.avatarEditor.stage;
                if (!stage.w || !stage.h) return;
                const side = Math.min(stage.w, stage.h);
                this.avatarEditor.crop = {
                    x: (stage.w - side) / 2,
                    y: (stage.h - side) / 2,
                    w: side,
                    h: side
                };
            },
            stagePoint(event) {
                const bounds = this.$refs.cropStage.getBoundingClientRect();
                const stage = this.avatarEditor.stage;
                return {
                    x: Math.max(0, Math.min(stage.w, event.clientX - bounds.left)),
                    y: Math.max(0, Math.min(stage.h, event.clientY - bounds.top))
                };
            },
            // Dragging on the image itself draws a completely new region.
            beginSelection(event) {
                this.measureStage();
                const point = this.stagePoint(event);
                this.avatarEditor.mode = "draw";
                this.avatarEditor.pointerId = event.pointerId;
                this.avatarEditor.originX = point.x;
                this.avatarEditor.originY = point.y;
                this.avatarEditor.crop = { x: point.x, y: point.y, w: 0, h: 0 };
                this.$refs.cropStage.setPointerCapture(event.pointerId);
            },
            // Dragging the box moves it; dragging a corner resizes that corner freely.
            grabSelection(event, mode) {
                this.measureStage();
                const point = this.stagePoint(event);
                this.avatarEditor.mode = mode;
                this.avatarEditor.pointerId = event.pointerId;
                this.avatarEditor.originX = point.x;
                this.avatarEditor.originY = point.y;
                this.avatarEditor.startCrop = Object.assign({}, this.avatarEditor.crop);
                this.$refs.cropStage.setPointerCapture(event.pointerId);
            },
            dragSelection(event) {
                const editor = this.avatarEditor;
                if (!editor.mode || event.pointerId !== editor.pointerId) return;

                const point = this.stagePoint(event);
                const stage = editor.stage;

                if (editor.mode === "draw") {
                    editor.crop = {
                        x: Math.min(editor.originX, point.x),
                        y: Math.min(editor.originY, point.y),
                        w: Math.abs(point.x - editor.originX),
                        h: Math.abs(point.y - editor.originY)
                    };
                    return;
                }

                const start = editor.startCrop;
                if (!start) return;

                if (editor.mode === "move") {
                    editor.crop = {
                        x: Math.max(0, Math.min(stage.w - start.w,
                            start.x + (point.x - editor.originX))),
                        y: Math.max(0, Math.min(stage.h - start.h,
                            start.y + (point.y - editor.originY))),
                        w: start.w,
                        h: start.h
                    };
                    return;
                }

                const left = editor.mode.includes("w") ? point.x : start.x;
                const top = editor.mode.includes("n") ? point.y : start.y;
                const right = editor.mode.includes("e") ? point.x : start.x + start.w;
                const bottom = editor.mode.includes("s") ? point.y : start.y + start.h;

                editor.crop = {
                    x: Math.min(left, right),
                    y: Math.min(top, bottom),
                    w: Math.abs(right - left),
                    h: Math.abs(bottom - top)
                };
            },
            endSelection(event) {
                if (event.pointerId !== this.avatarEditor.pointerId) return;
                this.avatarEditor.mode = "";
                this.avatarEditor.pointerId = null;
                this.avatarEditor.startCrop = null;

                // A stray click rather than a drag leaves the previous region alone.
                if (this.avatarEditor.crop.w < 16 || this.avatarEditor.crop.h < 16) {
                    this.resetSelection();
                }
            },
            // The selection translated back into pixels of the original file.
            sourceRect() {
                const editor = this.avatarEditor;
                const image = editor.image;
                if (!image || !editor.stage.w || !editor.stage.h || editor.crop.w < 16) return null;

                const scaleX = image.naturalWidth / editor.stage.w;
                const scaleY = image.naturalHeight / editor.stage.h;

                return {
                    x: Math.round(editor.crop.x * scaleX),
                    y: Math.round(editor.crop.y * scaleY),
                    w: Math.max(1, Math.round(editor.crop.w * scaleX)),
                    h: Math.max(1, Math.round(editor.crop.h * scaleY))
                };
            },
            async saveAvatar() {
                const source = this.sourceRect();
                if (this.avatarEditor.saving || !source) return;
                this.avatarEditor.saving = true;
                this.avatarEditor.error = "";

                try {
                    // Downscaled to at most 512px on the long edge, keeping the
                    // exact region and proportions the player selected.
                    const longest = Math.max(source.w, source.h);
                    const factor = longest > 512 ? 512 / longest : 1;
                    const canvas = document.createElement("canvas");
                    canvas.width = Math.max(1, Math.round(source.w * factor));
                    canvas.height = Math.max(1, Math.round(source.h * factor));

                    const context = canvas.getContext("2d");
                    context.imageSmoothingEnabled = true;
                    context.imageSmoothingQuality = "high";
                    context.drawImage(this.avatarEditor.image,
                        source.x, source.y, source.w, source.h,
                        0, 0, canvas.width, canvas.height);

                    const blob = await new Promise((resolve, reject) => {
                        canvas.toBlob(value => value ? resolve(value)
                            : reject(new Error("Could not prepare the image.")), "image/png");
                    });

                    if (blob.size > 4 * 1024 * 1024) {
                        throw new Error("The cropped image is too large. Try another photo.");
                    }

                    const response = await fetch("/account/avatar", {
                        method: "POST",
                        headers: { "Accept": "application/json", "Content-Type": "image/png" },
                        credentials: "same-origin",
                        body: blob
                    });
                    let answer = null;
                    try { answer = await response.json(); } catch (ignored) {}

                    if (!response.ok) {
                        throw new Error((answer && answer.status) || "The avatar could not be saved.");
                    }

                    this.avatarVersion = Date.now();
                    this.notice = "Profile picture saved.";
                    this.error = "";
                    this.closeAvatarEditor();
                } catch (e) {
                    this.avatarEditor.error = e.message || "The avatar could not be saved.";
                } finally {
                    this.avatarEditor.saving = false;
                }
            },
            closeAvatarEditor() {
                if (this.avatarEditor.saving) return;
                this.avatarEditor.open = false;
                this.avatarEditor.image = null;
                this.avatarEditor.mode = "";
                this.avatarEditor.pointerId = null;
                this.avatarEditor.crop = { x: 0, y: 0, w: 0, h: 0 };
                if (this.avatarEditor.objectUrl) {
                    URL.revokeObjectURL(this.avatarEditor.objectUrl);
                    this.avatarEditor.objectUrl = "";
                }
            },
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
                        // The game client links to /home/account/edit#avatar, which
                        // is redirected here; the fragment survives the redirect.
                        if (this.info) this.$nextTick(this.jumpToAvatar);
                    }
                },
            jumpToAvatar() {
                if (window.location.hash !== "#avatar") return;

                const picker = this.$el.querySelector(".avatar-picker");
                if (!picker) return;

                picker.scrollIntoView({ block: "center", behavior: "smooth" });
                // Focused rather than clicked: browsers only open a file dialog
                // in response to a real gesture, so the button is offered instead.
                picker.focus();
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
        },
        mounted() {
            // Escape and a window resize both have to reach the dialog, and
            // neither of them is delivered to an element that is not focused.
            this.onEditorKey = event => {
                if (event.key === "Escape" && this.avatarEditor.open) this.closeAvatarEditor();
            };
            this.onEditorResize = () => {
                if (this.avatarEditor.open) this.resetSelection();
            };
            document.addEventListener("keydown", this.onEditorKey);
            window.addEventListener("resize", this.onEditorResize);
        },
        beforeUnmount() {
            document.removeEventListener("keydown", this.onEditorKey);
            window.removeEventListener("resize", this.onEditorResize);
            if (this.avatarEditor.objectUrl) URL.revokeObjectURL(this.avatarEditor.objectUrl);
        }
    });
</script>
