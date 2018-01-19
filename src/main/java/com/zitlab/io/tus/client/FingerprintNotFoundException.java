package com.zitlab.io.tus.client;

public class FingerprintNotFoundException  extends Exception {
    public FingerprintNotFoundException(String fingerprint) {
        super("fingerprint not in storage found: " + fingerprint);
    }
}