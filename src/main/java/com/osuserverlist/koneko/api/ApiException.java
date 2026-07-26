package com.osuserverlist.koneko.api;

/**
 * Thrown when the bancho.jar API answers with anything but a success, or when
 * it cannot be reached at all.
 *
 * <p>The status code is kept so routes can pass a meaningful code back to the
 * browser instead of turning every API problem into a 500.
 */
public class ApiException extends Exception {

    private static final long serialVersionUID = 1L;

    /** HTTP status the API answered with, or 503 when it was unreachable. */
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public ApiException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
