<template id="not-found-view">
    <div class="page">
        <site-nav></site-nav>

        <koneko-slot name="page.top"></koneko-slot>

        <section class="card card-narrow">
            <p class="muted small">Error 404</p>
            <h2>This page does not exist</h2>
            <p class="muted">The link may be broken, or the page has moved. The front page
                and the leaderboard are still where you left them.</p>
            <a class="button" href="/">Go to the front page</a>
        </section>

                <koneko-slot name="page.bottom"></koneko-slot>

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
