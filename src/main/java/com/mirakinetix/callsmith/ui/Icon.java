package com.mirakinetix.callsmith.ui;

public enum Icon {

    // Core actions
    ADD("plus"),
    REMOVE("minus"),
    EDIT("pencil"),
    DELETE("trash", "delete"),
    SAVE("save", "save"),
    COPY("copy", "duplicate"),
    REFRESH("refresh-cw", "refresh"),
    CLOSE("x"),
    CHECK("check"),
    CANCEL("circle-x", "cancel"),
    SEARCH("search", "search"),
    SETTINGS("settings", "settings"),
    MENU("menu"),

    // HTTP / API
    SEND("send", "send-request"),
    REQUEST("send", "request"),
    RESPONSE("message-square", "response"),
    LINK("link"),
    EXTERNAL_LINK("external-link"),
    DOWNLOAD("download"),
    UPLOAD("upload"),
    PLAY("play"),
    STOP("square"),

    // Collections
    COLLECTION("layers", "collections"),
    FOLDER("folder", "folder"),
    FOLDER_OPEN("folder-open"),
    FILE("file"),
    DUPLICATE("copy", "duplicate"),

    // Environments
    ENVIRONMENT("globe"),
    VARIABLE("braces"),
    SECRET("key-round", "secret-protected"),
    EYE("eye"),
    EYE_OFF("eye-off"),
    ACTIVATE("circle-check"),

    // Navigation
    HOME("house", "home"),
    BACK("arrow-left"),
    FORWARD("arrow-right"),
    CHEVRON_DOWN("chevron-down"),
    CHEVRON_RIGHT("chevron-right"),
    EXPAND("maximize-2", "expand"),
    COLLAPSE("minimize-2", "minimize"),

    // Response / status
    SUCCESS("circle-check", "success"),
    ERROR("circle-x", "error"),
    WARNING("triangle-alert", "warning"),
    INFO("info", "information"),
    LOADING("loader-circle"),
    CLOCK("clock", "timeout"),
    SIZE("ruler"),
    HEADERS("list", "headers"),
    BODY("file-text", "body-text"),

    // Developer tools
    TERMINAL("terminal"),
    CODE("code"),
    JSON("braces", "body-json"),
    XML("code-xml", "body-xml"),
    GRAPHQL("share-2"),
    WEBSOCKET("radio"),
    LOGS("scroll-text"),
    ABOUT("info", "about"),
    NEW_REQUEST("plus", "new-request"),
    SAVE_AS("save", "save-as"),
    MOVE("arrow-right", "move"),
    IMPORT("upload", "import"),
    EXPORT("download", "export"),
    QUERY("list", "query-parameters"),
    AUTH("key-round", "authentication"),
    LOCK("key-round", "lock"),
    UNLOCK("key-round", "unlock"),
    UPLOAD_FILE("upload", "multipart-upload"),
    SAVE_RESPONSE("download", "save-response"),
    RENAME("pencil", "rename");

    private final String resourceName;
    private final String approvedName;

    Icon(String resourceName) {
        this(resourceName, null);
    }

    Icon(String resourceName, String approvedName) {
        this.resourceName = resourceName;
        this.approvedName = approvedName;
    }

    String approvedResource() {
        return approvedName == null ? null : "/icons/callsmith/" + approvedName + ".png";
    }

    public String resourceName() {
        return resourceName;
    }
}