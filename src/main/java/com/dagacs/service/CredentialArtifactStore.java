package com.dagacs.service;

import java.util.concurrent.ConcurrentHashMap;

public class CredentialArtifactStore {

    private static final ConcurrentHashMap<String, CredentialArtifact> artifacts = new ConcurrentHashMap<>();

    public static void put(String downloadId, CredentialArtifact artifact) {
        artifacts.put(downloadId, artifact);
    }

    public static CredentialArtifact get(String downloadId) {
        return artifacts.get(downloadId);
    }

    public static void remove(String downloadId) {
        artifacts.remove(downloadId);
    }
}
