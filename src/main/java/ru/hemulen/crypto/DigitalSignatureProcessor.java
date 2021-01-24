package ru.hemulen.crypto;

import org.w3c.dom.Element;
import ru.hemulen.crypto.PipeInputStream;
import ru.hemulen.crypto.exceptions.DocumentIsNotSignedException;
import ru.hemulen.crypto.exceptions.SignatureProcessingException;
import ru.hemulen.crypto.exceptions.SignatureValidationException;
import ru.hemulen.crypto.impl.ValidationResult;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Подписание, проверка ЭЦП.
 * Реализации должны быть потокобезопасны.
 */
public interface DigitalSignatureProcessor {
    public static enum SIG_POSITION {FIRST, LAST}

    // ================================ Подпись XML

    /**
     * Подписать XML-фрагмент по технологии XMLDSig enveloped.
     * Cгенерированный {http://www.w3.org/2000/09/xmldsig#}Signature будет добавлен как первый child к подписанному элементу.
     * Канонизация - http://www.w3.org/2001/10/xml-exc-c14n#
     * Расчёт хэш-кода - ГОСТ Р 34.11-94, http://www.w3.org/2001/04/xmldsig-more#gostr3411
     * Подписание - ГОСТ Р 34.10-2001, http://www.w3.org/2001/04/xmldsig-more#gostr34102001-gostr3411 ????
     *
     * @param argDocumentFragment2Sign XML-фрагмент, который необходимо подписать.
     * @param argPrivateKey            Секретный ключ.
     * @param argCertificate           Сетрификат.
     * @throws SignatureProcessingException Оборачивает любые exceptions, брошенные нижележащим ПО.
     *                                      Кроме того, выбрасывается, если какой-либо из аргументов - null.
     */
    void signXMLDSigEnveloped(Element argDocumentFragment2Sign, PrivateKey argPrivateKey, X509Certificate argCertificate) throws SignatureProcessingException;

    /**
     * Подписать XML-фрагмент по технологии XMLDSig detached.
     * Этим же способом нужно пользоваться, когда нужна enveloped подпись, но элемент
     * {http://www.w3.org/2000/09/xmldsig#}Signature должен быть не child, а более отдалённым descendant
     * подписываемого элемента. Присоединение {http://www.w3.org/2000/09/xmldsig#}Signature в нужную точку
     * XML-дерева в этом случае делается вручную.
     * Канонизация - http://www.w3.org/2001/10/xml-exc-c14n#
     * Расчёт хэш-кода - ГОСТ Р 34.11-94, http://www.w3.org/2001/04/xmldsig-more#gostr3411
     * Подписание - ГОСТ Р 34.10-2001, http://www.w3.org/2001/04/xmldsig-more#gostr34102001-gostr3411 ????
     *
     * @param argDocument2Sign XML-фрагмент, который необходимо подписать.
     * @param argSignatureId   signature id, если его нужно вставить в ЭЦП.
     * @param argPrivateKey    Секретный ключ.
     * @param argCertificate   Сетрификат.
     * @return XML элемент {http://www.w3.org/2000/09/xmldsig#}Signature, представляющий ЭЦП.
     * <p/>
     * USAGE: CORE{SignatureOperationsImpl}
     * @throws SignatureProcessingException Оборачивает любые exceptions, брошенные нижележащим ПО.
     *                                      Кроме того, выбрасывается, если какой-либо из аргументов - null.
     */
    void signXMLDSigEnveloped(Element argDocument2Sign, String argXPath2Element2Sign, SIG_POSITION argSignaturePosition, String argSignatureId, PrivateKey argPrivateKey, X509Certificate argCertificate) throws SignatureProcessingException;

    /**
     * Подписать XML-фрагмент по технологии XMLDSig detached.
     * Этим же способом нужно пользоваться, когда нужна enveloped подпись, но элемент
     * {http://www.w3.org/2000/09/xmldsig#}Signature должен быть не child, а более отдалённым descendant
     * подписываемого элемента. Присоединение {http://www.w3.org/2000/09/xmldsig#}Signature в нужную точку
     * XML-дерева в этом случае делается вручную.
     * Канонизация - http://www.w3.org/2001/10/xml-exc-c14n#
     * Расчёт хэш-кода - ГОСТ Р 34.11-94, http://www.w3.org/2001/04/xmldsig-more#gostr3411
     * Подписание - ГОСТ Р 34.10-2001, http://www.w3.org/2001/04/xmldsig-more#gostr34102001-gostr3411 ???? Уже должно быть не так
     *
     * @param argDocument2Sign XML-фрагмент, который необходимо подписать.
     * @param argSignatureId   signature id, если его нужно вставить в ЭЦП.
     * @param argPrivateKey    Секретный ключ.
     * @param argCertificate   Сетрификат.
     * @return XML элемент {http://www.w3.org/2000/09/xmldsig#}Signature, представляющий ЭЦП.
     * <p/>
     * USAGE: CORE{SignatureOperationsImpl}
     * @throws SignatureProcessingException Оборачивает любые exceptions, брошенные нижележащим ПО.
     *                                      Кроме того, выбрасывается, если какой-либо из аргументов - null.
     */
    Element signXMLDSigDetached(Element argDocument2Sign, String argSignatureId, PrivateKey argPrivateKey, X509Certificate argCertificate) throws SignatureProcessingException;

    // ================================ Проверка подписи XML

    /**
     * Проверяет ЭЦП формата XMLDSig enveloped.
     *
     * @param argSignedContent Подписанный XML-фрагмент.
     * @throws ru.hemulen.crypto.exceptions.DocumentIsNotSignedException Выбрасывается, если XML-фрагмент не содержить ЭЦП в формате XMLDSig.
     * @throws SignatureValidationException                                  Выбрасывается, если ЭЦП не прошла проверку.
     * @throws SignatureProcessingException                                  Оборачивает любые exceptions, брошенные нижележащим ПО.
     *                                                                       Кроме того, выбрасывается, если аргумент - null.
     */
    X509Certificate validateXMLDSigEnvelopedSignature(Element argSignedContent) throws SignatureProcessingException, SignatureValidationException, DocumentIsNotSignedException;

    /**
     * Проверяет ЭЦП формата XMLDSig detached.
     *
     * @param argSignedContent     Подписанный XML-фрагмент.
     * @param argDetachedSignature Элемент {http://www.w3.org/2000/09/xmldsig#}Signature, <b>либо любой его ancestor</b>.
     *                             Если под переданным ancestor находятся несколько элементов {http://www.w3.org/2000/09/xmldsig#}Signature, реализация должна определить,
     *                             какой из них подписывает фрагмент, переданный в argSignedContent.
     * @throws SignatureValidationException Выбрасывается, если ЭЦП не прошла проверку.
     * @throws SignatureProcessingException Оборачивает любые exceptions, брошенные нижележащим ПО.
     *                                      Кроме того, выбрасывается, если аргумент - null.
     *                                      <p/>
     *                                      USAGE: CORE{SignatureOperationsImpl}
     */
    X509Certificate validateXMLDSigDetachedSignature(Element argSignedContent, Element argDetachedSignature) throws SignatureProcessingException, SignatureValidationException;

    List<ValidationResult> validateXMLDSigEnvelopedAllSignature(Element xmlWithSignature) throws SignatureValidationException;

    // ================================ PKCS7

    /**
     * Хэширование данных, алгоритм расчёта хэш-кода - ГОСТ Р 34.11-94
     *
     * @return объект MessageDigest для хэширования
     * @throws SignatureProcessingException если алгоритм расчёта хэш-кода ГОСТ Р 34.11-94 не поддерживается
     */
    MessageDigest getDigest(String var1) throws SignatureProcessingException;

    MessageDigest getDigest(X509Certificate argStreamToBeWrapped) throws SignatureProcessingException;

    /**
     * Обернуть InputStream в обёртку, которая будет при чтении потока на лету подсчитывать message digest.
     * Смысл операции в том, чтобы избежать дополнительного чтения файла для проверки ЭЦП или подписания.
     * После чтения файла (и закрытия потоков), PipeInputStream можно передать методам  signPKCS7Detached или validatePKCS7Signature
     * с соответствующими сигнатурами. При этом готовый message digest будет взят из PipeInputStream.
     *
     * @param argStreamToBeWrapped поток, который нужно обернуть.
     * @return поток, из которого теперь нужно будет читать там, где раньше читалось из потока - аргумента метода.
     */
    PipeInputStream getPipeStream(InputStream argStreamToBeWrapped, X509Certificate argCertificate) throws SignatureProcessingException;

    byte[] signPKCS7Detached(InputStream argContent2Sign, PrivateKey argPrivateKey, X509Certificate argCertificate) throws SignatureProcessingException;

    /**
     * Подписать поток байтов, вернуть ЭЦП в формате PKCS#7.
     * Алгоритм расчёта хэш-кода - ГОСТ Р 34.11-94
     * Алгоритм подписания - ГОСТ Р 34.10-2001
     *
     * @param argContent2Sign Поток байтов на подпись.
     * @param argPrivateKey   Секретный ключ.
     * @return Подпись - PKCS#7, сериализованная в поток байтов.
     * @throws SignatureProcessingException Оборачивает любые exceptions, брошенные нижележащим ПО.
     *                                      Кроме того, выбрасывается, если какой-либо из аргументов - null.
     */
    byte[] signPKCS7Detached(byte[] argContent2Sign, PrivateKey argPrivateKey, X509Certificate argCertificate) throws SignatureProcessingException;

    /**
     * Проверяет ЭЦП формата PKCS#7.
     *
     * @param argDigest    Digest, полученный из PipeInputStream, через который прочитали файл, на котором нужно проверить ЭЦП.
     * @param argSignature ЭЦП в формате PKCS#7, сериализованная в поток байтов.
     * @return Сетрификат, которым был подписан контент.
     * @throws SignatureValidationException Выбрасывается, если ЭЦП не прошла проверку.
     * @throws SignatureProcessingException Оборачивает любые exceptions, брошенные нижележащим ПО.
     *                                      Кроме того, выбрасывается, если какой-либо из аргументов - null.
     *                                      <p/>
     *                                      USAGE: CORE{SignatureOperationsImpl}
     */
    X509Certificate validatePKCS7Signature(InputStream argDigest, byte[] argSignature) throws SignatureProcessingException, SignatureValidationException;

    X509Certificate validatePKCS7Signature(byte[] argDigest, byte[] argSignature) throws SignatureProcessingException, SignatureValidationException;
}
