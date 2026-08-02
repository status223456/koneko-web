package com.osuserverlist.koneko.auth;

import java.time.Instant;

import com.osuserverlist.koneko.api.TokenPair;

import lombok.Getter;
import lombok.Setter;

/**
 * One logged in browser, server side.
 *
 * <p>The token pair lives here and nowhere else; the browser holds only the
 * random session id. That way a script injected into a page cannot read an API
 * token, because there is none to read.
 */
@Getter
public final class UserSession {

    /** The VERIFIED bit of the API's Privileges, mirrored here. */
    private static final int VERIFIED = 1 << 1;

    /**
     * The UNRESTRICTED bit of the API's Privileges. A restriction clears it, so
     * an account missing it is one the staff has taken out of the community.
     */
    private static final int UNRESTRICTED = 1 << 0;

    private final int userId;
    private final String username;

    /**
     * Not final, because it can change while the session is open: an account registered on
     * the website gains its VERIFIED bit the moment it logs into the game, and a session that
     * could not see that would keep the player locked out until they logged out and back in.
     */
    @Setter
    private volatile int privileges;

    /**
     * When the API was last asked whether this account is verified yet. Only used while it is
     * not, so a verified session never pays for the check.
     */
    @Setter
    private volatile Instant lastVerificationCheck = Instant.EPOCH;

    @Setter
    private volatile TokenPair tokens;

    @Setter
    private volatile Instant lastSeen = Instant.now();

    public UserSession(int userId, String username, int privileges, TokenPair tokens) {
        this.userId = userId;
        this.username = username;
        this.privileges = privileges;
        this.tokens = tokens;
    }

    /** True once the account has completed the in-game login that verifies it. */
    public boolean isVerified() {
        return (privileges & VERIFIED) != 0;
    }

    /** True while the account is restricted, which is what the banner reads. */
    public boolean isRestricted() {
        return (privileges & UNRESTRICTED) == 0;
    }

    public void touch() {
        this.lastSeen = Instant.now();
    }
}
