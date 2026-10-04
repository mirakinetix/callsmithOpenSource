package com.mirakinetix.callsmith.ui;

public enum Icon {

    // Core actions
    ADD("plus"),
    REMOVE("minus"),
    EDIT("pencil"),
    DELETE("trash"),
    SAVE("save"),
    COPY("copy"),
    REFRESH("refresh-cw"),
    CLOSE("x"),
    CHECK("check"),
    CANCEL("circle-x"),
    SEARCH("search"),
    SETTINGS("settings"),
    MENU("menu"),

    // HTTP / API
    SEND("send"),
    REQUEST("send"),
    RESPONSE("message-square"),
    LINK("link"),
    EXTERNAL_LINK("external-link"),
    DOWNLOAD("download"),
    UPLOAD("upload"),
    PLAY("play"),
    STOP("square"),

    // Collections
    COLLECTION("layers"),
    FOLDER("folder"),
    FOLDER_OPEN("folder-open"),
    FILE("file"),
    DUPLICATE("copy"),

    // Environments
    ENVIRONMENT("globe"),
    VARIABLE("braces"),
    SECRET("key-round"),
    EYE("eye"),
    EYE_OFF("eye-off"),
    ACTIVATE("circle-check"),

    // Navigation
    HOME("house"),
    BACK("arrow-left"),
    FORWARD("arrow-right"),
    CHEVRON_DOWN("chevron-down"),
    CHEVRON_RIGHT("chevron-right"),
    EXPAND("maximize-2"),
    COLLAPSE("minimize-2"),

    // Response / status
    SUCCESS("circle-check"),
    ERROR("circle-x"),
    WARNING("triangle-alert"),
    INFO("info"),
    LOADING("loader-circle"),
    CLOCK("clock"),
    SIZE("ruler"),
    HEADERS("list"),
    BODY("file-text"),

    // Developer tools
    TERMINAL("terminal"),
    CODE("code"),
    JSON("braces"),
    XML("code-xml"),
    GRAPHQL("share-2"),
    WEBSOCKET("radio"),
    LOGS("scroll-text");

    private final String resourceName;

    Icon(String resourceName) {
        this.resourceName = resourceName;
    }

    public String resourceName() {
        return resourceName;
    }
}