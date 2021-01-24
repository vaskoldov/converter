package ru.hemulen.crypto;

import org.apache.xml.security.Init;
import org.apache.xml.security.exceptions.AlgorithmAlreadyRegisteredException;
import org.apache.xml.security.transforms.Transform;
import ru.hemulen.crypto.exceptions.SigLibInitializationException;
import ru.hemulen.crypto.impl.CacheOptions;
import ru.hemulen.crypto.impl.CachingKeyStoreWrapper;
import ru.hemulen.crypto.impl.DigitalSignatureProcessorImpl;
import ru.hemulen.crypto.impl.SmevTransformSpi;
import ru.hemulen.crypto.impl.jcp.KeyStoreWrapperJCP;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Properties;

public class DigitalSignatureFactory {
    private static String providerName = "JCP2";
    private static volatile DigitalSignatureProcessor fnsProcessor = null;
    private static volatile KeyStoreWrapper fnsKeyStoreWrapper = null;
    private static volatile DigitalSignatureProcessor egrnProcessor = null;
    private static volatile KeyStoreWrapper egrnKeyStoreWrapper = null;
    private static boolean isFNSProcessorAvailable;
    private static boolean isEGRNProcessorAvailable;

    public static synchronized void init(Properties props) throws SigLibInitializationException {
        // Формируем данные подписи ФНС
        String fnsKeyAlias = props.getProperty("FNS_SIGN_ALIAS");
        String fnsKeystoreName = "";
        if (!fnsKeyAlias.isEmpty()) {
            if (fnsKeyAlias.contains("\\")) {
                fnsKeystoreName = fnsKeyAlias.substring(0, fnsKeyAlias.indexOf("\\"));
            } else {
                fnsKeystoreName = "HDImageStore";
            }
        }

        // Формируем данные подписи ЕГРН
        String egrnKeyAlias = props.getProperty("EGRN_SIGN_ALIAS");
        String egrnKeystoreName = "";
        if (!egrnKeyAlias.isEmpty()) {
            if (egrnKeyAlias.contains("\\")) {
                egrnKeystoreName = egrnKeyAlias.substring(0, egrnKeyAlias.indexOf("\\"));
            } else {
                egrnKeystoreName = "HDImageStore";
            }
        }

        // Выполняем инициализацию фабрики
        System.setProperty("org.apache.xml.security.ignoreLineBreaks", "true");
        System.setProperty("org.apache.xml.security.resource.config", "resource/jcp.xml");
        initXmlSec();

        // Инициализируем процессор для подписи ФНС
        if (!fnsKeyAlias.isEmpty()) {
            fnsProcessor = new DigitalSignatureProcessorImpl();
            isFNSProcessorAvailable = true;
            try {
                fnsKeyStoreWrapper = new KeyStoreWrapperJCP(fnsKeystoreName);
            } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
                isFNSProcessorAvailable = false;
            }
        }

        // Инициализируем процессор для подписи ЕГРН
        if (!egrnKeyAlias.isEmpty()) {
            egrnProcessor = new DigitalSignatureProcessorImpl();
            isEGRNProcessorAvailable = true;
            try {
                egrnKeyStoreWrapper = new KeyStoreWrapperJCP(egrnKeystoreName);
            } catch (IOException | CertificateException | NoSuchAlgorithmException | KeyStoreException e) {
                isEGRNProcessorAvailable = false;
            }
        }

    }

    private static void initXmlSec() throws SigLibInitializationException {
        try {
            Init.init();
            try {
                Transform.register(SmevTransformSpi.ALGORITHM_URN, SmevTransformSpi.class.getName());
            } catch (AlgorithmAlreadyRegisteredException e) {
                // Игнорируем это исключение
            }
        } catch (Exception e) {
            throw new SigLibInitializationException("Возникли проблемы при инициализации XmlSec!", e);
        }
    }

    public static DigitalSignatureProcessor getFNSDigitalSignatureProcessor() throws SigLibInitializationException {
        DigitalSignatureProcessor p = fnsProcessor;
        if (p == null) {
            throw new SigLibInitializationException("Перед использованием фабрику необходимо инициализировать!");
        } else {
            return p;
        }
    }

    public static DigitalSignatureProcessor getEGRNDigitalSignatureProcessor() {
        DigitalSignatureProcessor p = egrnProcessor;
        if (p == null) {
            throw new SigLibInitializationException("Перед использованием фабрику необходимо инициализировать!");
        } else {
            return p;
        }
    }

    public static KeyStoreWrapper getFNSKeyStoreWrapper() throws SigLibInitializationException {
        return getFNSKeyStoreWrapper(null);
    }

    public static KeyStoreWrapper getFNSKeyStoreWrapper(CacheOptions options) throws SigLibInitializationException {
        ru.hemulen.crypto.KeyStoreWrapper ks = fnsKeyStoreWrapper;
        if (ks == null) {
            throw new SigLibInitializationException("Перед использованием фабрику необходимо инициализировать!");
        } else {
            return (options == null ? ks : new CachingKeyStoreWrapper(ks, options));
        }
    }

    public static KeyStoreWrapper getEGRNKeyStoreWrapper() throws SigLibInitializationException {
        return getEGRNKeyStoreWrapper(null);
    }

    public static KeyStoreWrapper getEGRNKeyStoreWrapper(CacheOptions options) throws SigLibInitializationException {
        ru.hemulen.crypto.KeyStoreWrapper ks = egrnKeyStoreWrapper;
        if (ks == null) {
            throw new SigLibInitializationException("Перед использованием фабрику необходимо инициализировать!");
        } else {
            return (options == null ? ks : new CachingKeyStoreWrapper(ks, options));
        }
    }

    public static boolean getFNSProcessorState() {
        return isFNSProcessorAvailable;
    }

    public static boolean getEGRNProcessorState() {
        return isEGRNProcessorAvailable;
    }

}
