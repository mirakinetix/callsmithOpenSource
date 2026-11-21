package com.mirakinetix.callsmith.domain.http;

/** Transport failures whose messages are safe for the UI and diagnostics. */
public final class HttpFailure extends IllegalStateException {
    public enum Kind {
        INVALID("Invalid HTTP request. Check the URL, method and headers."),
        VARIABLE_ENVIRONMENT("Select an environment to resolve request variables before sending."),
        VARIABLE_MISSING("A request variable is undefined in the selected environment. Define it before sending."),
        VARIABLE_UNAVAILABLE("A request variable could not be read. Unlock Secret Storage if needed and check the selected environment."),
        AUTH_CONFLICT("The authentication helper conflicts with an existing credential."),
        AUTH_INVALID("Complete the selected authentication fields with valid values."),
        BODY("The selected request body format conflicts with its fields or Content-Type."),
        UPLOAD("The selected file is missing, unreadable or changed. Select it again."),
        ORIGIN("Redirect blocked because its destination uses a different origin."),
        DOWNGRADE("Redirect blocked because HTTPS would downgrade to HTTP."),
        LOCATION("Redirect blocked because Location is invalid or ambiguous."),
        LOOP("Redirect blocked because the request entered a loop."),
        REDIRECT_LIMIT("Redirect blocked after five hops."),
        LIMIT("The response exceeded Callsmith's 8 MiB size limit."),
        COMPRESSION("The compressed response could not be decoded."),
        CONNECT("Could not connect to the server."),
        TLS("The HTTPS connection could not be verified."),
        TIMEOUT("The request timed out."),
        INTERRUPTED("The request was cancelled."),
        NETWORK("The HTTP exchange failed.");

        private final String message;
        Kind(String message) { this.message = message; }
    }

    private final Kind kind;
    public HttpFailure(Kind kind) {
        super(kind.message);
        this.kind = kind;
    }
    public Kind kind() { return kind; }
}
