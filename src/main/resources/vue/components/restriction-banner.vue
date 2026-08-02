<template id="restriction-banner">
    <!--
        The strip a restricted account sees. It floats above the page rather
        than sitting in the flow: nothing below it moves, and whatever is
        behind it stays visible through the tint.

        Two parts, as on the reference: the label on the left, in a darker
        panel, and the explanation on the right.

        Deliberately in English regardless of anything else on the site: this
        is the one message a player has to be able to read and act on.
    -->
    <div class="restriction-banner" role="alert" v-if="restricted">
        <div class="restriction-banner-label">
            <span class="restriction-banner-icon" aria-hidden="true">!</span>

            <span class="restriction-banner-label-text">
                <span class="restriction-banner-title">Alert</span>
                <span class="restriction-banner-subtitle">Your account has been restricted!</span>
            </span>
        </div>

        <div class="restriction-banner-body">
            <p class="restriction-banner-text">
                While your account is restricted you cannot interact with other players and your
                scores are visible only to you. This is usually the result of an automated process
                and the restriction is normally lifted within 24 hours.
            </p>

            <a class="restriction-banner-link" href="/restrictions">Click here to find out more.</a>
        </div>
    </div>
</template>

<script>
    app.component("restriction-banner", {
        template: "#restriction-banner",
        computed: {
            user() {
                return this.$koneko.user;
            },
            // The server sends the flag it read off the account's privileges,
            // but the raw bitmask is checked too: an older bootstrap payload
            // (a cached page, a plugin that rebuilt the user) still works.
            restricted() {
                const user = this.user;

                if (!user) return false;

                if (typeof user.restricted === "boolean") return user.restricted;

                return (Number(user.priv || 0) & 1) === 0;
            }
        }
    });
</script>
