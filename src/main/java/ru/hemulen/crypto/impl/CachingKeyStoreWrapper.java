package ru.hemulen.crypto.impl;

import com.google.common.base.Charsets;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.nio.CharBuffer;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import ru.hemulen.crypto.KeyStoreWrapper;

public final class CachingKeyStoreWrapper implements KeyStoreWrapper  {
    private final KeyStoreWrapper original;
    private final Cache<String, CachingKeyStoreWrapper.PKEntry> keyCache;
    private final LoadingCache<String, X509Certificate> certCache;

    public CachingKeyStoreWrapper(KeyStoreWrapper keyStore, CacheOptions options) {
        this.original = keyStore;
        CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder();
        if (options.getExpireAfterAccess() != null) {
            builder.expireAfterAccess(options.getExpireAfterAccess(), TimeUnit.MILLISECONDS);
        }

        if (options.getExpireAfterWrite() != null) {
            builder.expireAfterWrite(options.getExpireAfterWrite(), TimeUnit.MILLISECONDS);
        }

        if (options.getMaxSize() != null) {
            builder.maximumSize(options.getMaxSize());
        }

        if (options.isCachePrivateKeys()) {
            this.keyCache = builder.build();
        } else {
            this.keyCache = null;
        }

        if (options.isCacheCertificates()) {
            this.certCache = builder.build(new CacheLoader<String, X509Certificate>() {
                public X509Certificate load(String key) throws Exception {
                    return CachingKeyStoreWrapper.this.original.getX509Certificate(key);
                }
            });
        } else {
            this.certCache = null;
        }

    }

    private static <T extends Exception> void checkException(Throwable ex, Class<T> cls) throws T {
        if (cls.isInstance(ex)) {
            throw (T) ex;
        }
    }

    private static byte[] hash(char[] password) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] data = Charsets.UTF_16.encode(CharBuffer.wrap(password)).array();
        md.update(data);
        return md.digest();
    }

    public PrivateKey getPrivateKey(final String alias, final char[] password) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableKeyException {
        if (this.keyCache == null) {
            return this.original.getPrivateKey(alias, password);
        } else {
            try {
                CachingKeyStoreWrapper.PKEntry entry = (CachingKeyStoreWrapper.PKEntry)this.keyCache.get(alias, new Callable<CachingKeyStoreWrapper.PKEntry>() {
                    public CachingKeyStoreWrapper.PKEntry call() throws Exception {
                        PrivateKey pk = CachingKeyStoreWrapper.this.original.getPrivateKey(alias, password);
                        return new CachingKeyStoreWrapper.PKEntry(pk, CachingKeyStoreWrapper.hash(password));
                    }
                });
                if (!Arrays.equals(hash(password), entry.passHash)) {
                    throw new UnrecoverableKeyException("Wrong password!");
                } else {
                    return entry.pk;
                }
            } catch (ExecutionException var5) {
                Throwable cause = var5.getCause();
                checkException(cause, KeyStoreException.class);
                checkException(cause, NoSuchAlgorithmException.class);
                checkException(cause, UnrecoverableKeyException.class);
                checkException(cause, RuntimeException.class);
                throw new RuntimeException(cause);
            }
        }
    }

    public X509Certificate getX509Certificate(String alias) throws CertificateException, KeyStoreException {
        if (this.certCache == null) {
            return this.original.getX509Certificate(alias);
        } else {
            try {
                return (X509Certificate)this.certCache.get(alias);
            } catch (ExecutionException var4) {
                Throwable cause = var4.getCause();
                checkException(cause, CertificateException.class);
                checkException(cause, KeyStoreException.class);
                checkException(cause, RuntimeException.class);
                throw new RuntimeException(cause);
            }
        }
    }

    public KeyStore getKeyStore() {
        return this.original.getKeyStore();
    }

    private static final class PKEntry {
        private final PrivateKey pk;
        private final byte[] passHash;

        private PKEntry(PrivateKey pk, byte[] passHash) {
            this.pk = pk;
            this.passHash = passHash;
        }
    }

}
