package com.mirakinetix.callsmith.application.environment;

public interface SecretStorage {
    boolean isConfigured();
    boolean isUnlocked();
    void initialize(char[] passphrase);
    void unlock(char[] passphrase);
    void lock();
}
