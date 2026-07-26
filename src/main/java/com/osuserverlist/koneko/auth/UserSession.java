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

    private final int userId;
    private final String username;
    private final int privileges;

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

    public void touch() {
        this.lastSeen = Instant.now();
    }
}
