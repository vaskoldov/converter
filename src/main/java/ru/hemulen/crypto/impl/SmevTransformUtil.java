package ru.hemulen.crypto.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class SmevTransformUtil {
    private SmevTransformUtil() {
    }

    public static byte[] transform(byte[] bytes) throws Exception {
        SmevTransformSpi transform = new SmevTransformSpi();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        InputStream inputStream = new ByteArrayInputStream(bytes);
        Throwable var4 = null;

        byte[] var5;
        try {
            transform.process(inputStream, outputStream);
            var5 = outputStream.toByteArray();
        } catch (Throwable var14) {
            var4 = var14;
            throw var14;
        } finally {
            if (inputStream != null) {
                if (var4 != null) {
                    try {
                        inputStream.close();
                    } catch (Throwable var13) {
                        var4.addSuppressed(var13);
                    }
                } else {
                    inputStream.close();
                }
            }

        }

        return var5;
    }

}
