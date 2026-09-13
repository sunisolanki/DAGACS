package com.dagacs.service;

import java.util.concurrent.atomic.AtomicBoolean;

public class CredentialArtifact {

    private final String downloadId;
    private final byte[] xlsxBytes;
    private final AtomicBoolean downloaded = new AtomicBoolean(false);

    public CredentialArtifact(String downloadId, byte[] xlsxBytes) {
        this.downloadId = downloadId;
        this.xlsxBytes = xlsxBytes;
    }

    public String getDownloadId() {
        return downloadId;
    }

    public byte[] getXlsxBytes() {
        return xlsxBytes;
    }

    public boolean tryMarkDownloaded() {
        return downloaded.compareAndSet(false, true);
    }
}
