<template id="admin-action-dialog">
    <div class="admin-dialog-backdrop" v-if="open" @click.self="cancel">
        <section class="admin-dialog" role="dialog" aria-modal="true" :aria-label="title">
            <h3 class="admin-dialog-title">{{ title }}</h3>
            <p class="admin-dialog-text" v-if="text">{{ text }}</p>

            <div class="admin-dialog-fields" v-if="fields.length">
                <div class="admin-dialog-field" v-for="field in fields" :key="field.key">
                    <span class="field-label">{{ field.label }}</span>

                    <!-- Anything with a fixed set of answers is picked, never
                         typed: a misspelled privilege name used to come back as
                         an API error after the fact. -->
                    <select class="filter-input" v-if="field.type === 'select'"
                        v-model="values[field.key]">
                        <option v-for="option in field.options" :key="String(option.value)"
                            :value="option.value">{{ option.label }}</option>
                    </select>

                    <div class="admin-dialog-checks" v-else-if="field.type === 'checks'">
                        <label class="admin-dialog-check" v-for="option in field.options"
                            :key="String(option.value)">
                            <input type="checkbox" :value="option.value"
                                v-model="values[field.key]">
                            <span>{{ option.label }}</span>
                        </label>
                    </div>

                    <textarea class="filter-input" v-else-if="field.type === 'textarea'"
                        rows="3" v-model="values[field.key]"
                        :placeholder="field.placeholder || ''"></textarea>

                    <input class="filter-input" v-else type="text" v-model="values[field.key]"
                        :placeholder="field.placeholder || ''"
                        :maxlength="field.maxlength || null">

                    <span class="admin-dialog-hint" v-if="field.hint">{{ field.hint }}</span>
                </div>
            </div>

            <p class="admin-dialog-error" v-if="problem || error">{{ problem || error }}</p>

            <div class="admin-dialog-actions">
                <button class="button button-ghost button-small" type="button"
                    :disabled="busy" @click="cancel">Cancel</button>

                <button class="button button-small" :class="danger ? 'button-danger' : ''"
                    type="button" :disabled="busy" @click="submit">
                    {{ busy ? "Working\u2026" : confirmLabel }}
                </button>
            </div>
        </section>
    </div>
</template>

<script>
    /**
     * The one dialog every staff action goes through.
     *
     * It replaces window.prompt, which could only ever ask for a line of text.
     * That is why a privilege change was a comma separated list typed from
     * memory, and why the only feedback for getting it wrong was an API error in
     * an alert box afterwards. Here the caller describes its fields and the
     * values that come from a fixed set are chosen from one.
     *
     * The caller keeps ownership of the request: this component collects values,
     * validates that the required ones are filled in, and hands them back.
     */
    app.component("admin-action-dialog", {
        template: "#admin-action-dialog",
        props: {
            open: Boolean,
            title: { type: String, default: "" },
            text: { type: String, default: "" },
            fields: { type: Array, default: () => [] },
            confirmLabel: { type: String, default: "Confirm" },
            danger: Boolean,
            busy: Boolean,
            /** A failure from the caller's request, shown without closing. */
            error: { type: String, default: "" }
        },
        emits: ["close", "submit"],
        data: () => ({
            values: {},
            problem: ""
        }),
        watch: {
            open(isOpen) {
                if (isOpen) this.reset();
            },
            // The same dialog is reused for every action, so a new field list
            // means a new question and the old answers must not linger.
            fields() {
                if (this.open) this.reset();
            }
        },
        methods: {
            reset() {
                const values = {};

                this.fields.forEach(field => {
                    if (field.type === "checks") {
                        values[field.key] = Array.isArray(field.value)
                            ? field.value.slice()
                            : [];
                    } else if (field.value !== undefined) {
                        values[field.key] = field.value;
                    } else if (field.type === "select" && field.options && field.options.length) {
                        values[field.key] = field.options[0].value;
                    } else {
                        values[field.key] = "";
                    }
                });

                this.values = values;
                this.problem = "";
            },
            cancel() {
                if (!this.busy) this.$emit("close");
            },
            submit() {
                const missing = this.fields.find(field => {
                    if (!field.required) return false;

                    const value = this.values[field.key];

                    return Array.isArray(value)
                        ? value.length === 0
                        : String(value === undefined || value === null ? "" : value).trim() === "";
                });

                if (missing) {
                    this.problem = missing.label + " is required.";
                    return;
                }

                this.problem = "";
                this.$emit("submit", Object.assign({}, this.values));
            },
            onKey(event) {
                if (this.open && event.key === "Escape") this.cancel();
            }
        },
        mounted() {
            document.addEventListener("keydown", this.onKey);
        },
        unmounted() {
            document.removeEventListener("keydown", this.onKey);
        },
        created() {
            if (this.open) this.reset();
        }
    });
</script>
