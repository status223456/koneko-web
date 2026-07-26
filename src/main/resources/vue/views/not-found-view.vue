<template id="not-found-view">
    <div class="page">
        <site-nav></site-nav>

        <section class="card card-narrow">
            <h2>404</h2>
            <p class="muted">This page does not exist.</p>
            <a class="button" href="/">Back to the front page</a>
        </section>

        <site-footer></site-footer>
    </div>
</template>

<script>
    app.component("not-found-view", {
        template: "#not-found-view",
        created() {
            this.setTitle("404");
        }
    });
</script>
