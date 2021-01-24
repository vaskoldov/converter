package ru.hemulen.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

public class PipeInputStream extends InputStream {
    private static final int MAX_UPDATE = 65535;
    private final InputStream wrapped;
    private final MessageDigest digest;
    private final byte[] digestResult;
    private boolean exhausted = false;
    private long size = 0L;

    public PipeInputStream(InputStream arg, MessageDigest argDigest) {
        this.wrapped = arg;
        this.digest = argDigest;
        this.digestResult = null;
    }

    public PipeInputStream(byte[] digestResult) {
        this.wrapped = null;
        this.digest = null;
        this.digestResult = digestResult;
    }

    public byte[] getDigest() {
        return this.digest != null ? this.digest.digest() : this.digestResult;
    }

    public long getSize() {
        return this.size;
    }

    public void close() throws IOException {
        if (this.wrapped != null) {
            this.wrapped.close();
        }

    }

    public boolean markSupported() {
        return false;
    }

    public int read() throws IOException {
        if (this.wrapped != null && !this.exhausted) {
            int value = this.wrapped.read();
            if (value < 0) {
                this.exhausted = true;
            } else {
                ++this.size;
                this.digest.update((byte)value);
            }

            return value;
        } else {
            return -1;
        }
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (this.wrapped != null && !this.exhausted) {
            int read = this.wrapped.read(b, off, len);
            if (read < 0) {
                this.exhausted = true;
                return read;
            } else {
                this.size += (long)read;
                int updOff = off;

                int updLen;
                for(updLen = read; updLen > 65535; updLen -= 65535) {
                    this.digest.update(b, updOff, 65535);
                    updOff += 65535;
                }

                this.digest.update(b, updOff, updLen);
                return read;
            }
        } else {
            return -1;
        }
    }

}
