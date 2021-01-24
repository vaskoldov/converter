package ru.hemulen.crypto.impl;

public final class CacheOptions {
    private Long expireAfterAccess = null;
    private Long expireAfterWrite = null;
    private Long maxSize = null;
    private boolean cachePrivateKeys = false;
    private boolean cacheCertificates = false;

    public CacheOptions() {
    }

    public CacheOptions expireAfterAccess(long ms) {
        this.expireAfterAccess = ms;
        return this;
    }

    public CacheOptions expireAfterWrite(long ms) {
        this.expireAfterWrite = ms;
        return this;
    }

    public CacheOptions maxSize(long size) {
        this.maxSize = size;
        return this;
    }

    public CacheOptions cachePrivateKeys() {
        this.cachePrivateKeys = true;
        return this;
    }

    public CacheOptions cacheCertificates() {
        this.cacheCertificates = true;
        return this;
    }

    public Long getExpireAfterAccess() {
        return this.expireAfterAccess;
    }

    public Long getExpireAfterWrite() {
        return this.expireAfterWrite;
    }

    public Long getMaxSize() {
        return this.maxSize;
    }

    public boolean isCachePrivateKeys() {
        return this.cachePrivateKeys;
    }

    public boolean isCacheCertificates() {
        return this.cacheCertificates;
    }

}
