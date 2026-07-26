<template id="stat-card">
    <div class="stat-card">
        <div class="stat-value">{{ display }}</div>
        <div class="stat-label">{{ label }}</div>
    </div>
</template>

<script>
    app.component("stat-card", {
        template: "#stat-card",
        props: {
            label: { type: String, required: true },
            value: { default: null },
            // Already formatted value, used for accuracy and the like.
            text: { default: null }
        },
        computed: {
            display() {
                if (this.text !== null && this.text !== undefined) {
                    return this.text;
                }

                return this.value === null || this.value === undefined
                    ? "-"
                    : Number(this.value).toLocaleString();
            }
        }
    });
</script>
