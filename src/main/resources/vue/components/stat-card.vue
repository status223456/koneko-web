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
            value: { default: null }
        },
        computed: {
            display() {
                return this.value === null || this.value === undefined
                    ? "-"
                    : Number(this.value).toLocaleString();
            }
        }
    });
</script>
