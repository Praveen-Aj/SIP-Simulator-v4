package test;

import sipinspector.Utils.SecurityManager;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.util.Base64;

/**
 * Validation test for PKCS#1 and PKCS#8 RSA private key loading in SecurityManager.
 */
public class SecurityManagerKeyParsingTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   SecurityManager PKCS#1 / PKCS#8 Key Parsing Test");
        System.out.println("==================================================");

        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            RSAPrivateCrtKey priv = (RSAPrivateCrtKey) kp.getPrivate();
            PublicKey pub = kp.getPublic();

            // 1. Test PKCS#8 Key loading
            testPkcs8KeyLoading(priv, pub);

            // 2. Test PKCS#1 Key loading
            testPkcs1KeyLoading(priv, pub);

            System.out.println("\n--------------------------------------------------");
            System.out.printf("Results: %d Passed, %d Failed%n", testsPassed, testsFailed);
            System.out.println("--------------------------------------------------");

            if (testsFailed > 0) {
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void testPkcs8KeyLoading(RSAPrivateCrtKey originalKey, PublicKey pubKey) {
        System.out.print("[TEST 1] PKCS#8 standard private key parsing... ");
        File tempKeyFile = null;
        try {
            tempKeyFile = File.createTempFile("test_pkcs8_", ".key");
            byte[] encoded = originalKey.getEncoded();
            String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
            String pem = "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
            try (FileWriter fw = new FileWriter(tempKeyFile)) {
                fw.write(pem);
            }

            SecurityManager sm = new SecurityManager();
            java.lang.reflect.Method m = SecurityManager.class.getDeclaredMethod("loadPrivateKey", String.class);
            m.setAccessible(true);
            PrivateKey parsedKey = (PrivateKey) m.invoke(sm, tempKeyFile.getAbsolutePath());

            if (parsedKey == null) {
                throw new AssertionError("Parsed key is null");
            }

            // Verify with digital signature
            verifySignature(parsedKey, pubKey);

            System.out.println("PASSED");
            testsPassed++;
        } catch (Throwable t) {
            System.out.println("FAILED: " + t.getMessage());
            t.printStackTrace();
            testsFailed++;
        } finally {
            if (tempKeyFile != null) tempKeyFile.delete();
        }
    }

    private static void testPkcs1KeyLoading(RSAPrivateCrtKey originalKey, PublicKey pubKey) {
        System.out.print("[TEST 2] PKCS#1 (BEGIN RSA PRIVATE KEY) key parsing... ");
        File tempKeyFile = null;
        try {
            tempKeyFile = File.createTempFile("test_pkcs1_", ".key");
            byte[] pkcs1Der = encodePkcs1Der(originalKey);
            String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pkcs1Der);
            String pem = "-----BEGIN RSA PRIVATE KEY-----\n" + b64 + "\n-----END RSA PRIVATE KEY-----\n";
            try (FileWriter fw = new FileWriter(tempKeyFile)) {
                fw.write(pem);
            }

            SecurityManager sm = new SecurityManager();
            java.lang.reflect.Method m = SecurityManager.class.getDeclaredMethod("loadPrivateKey", String.class);
            m.setAccessible(true);
            PrivateKey parsedKey = (PrivateKey) m.invoke(sm, tempKeyFile.getAbsolutePath());

            if (parsedKey == null) {
                throw new AssertionError("Parsed key is null");
            }

            // Verify with digital signature
            verifySignature(parsedKey, pubKey);

            System.out.println("PASSED");
            testsPassed++;
        } catch (Throwable t) {
            System.out.println("FAILED: " + t.getMessage());
            t.printStackTrace();
            testsFailed++;
        } finally {
            if (tempKeyFile != null) tempKeyFile.delete();
        }
    }

    private static void verifySignature(PrivateKey priv, PublicKey pub) throws Exception {
        byte[] challenge = "SIP Simulator Verification Challenge".getBytes("UTF-8");
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(priv);
        sig.update(challenge);
        byte[] signatureBytes = sig.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pub);
        verifier.update(challenge);
        if (!verifier.verify(signatureBytes)) {
            throw new AssertionError("RSA signature verification failed!");
        }
    }

    private static byte[] encodePkcs1Der(RSAPrivateCrtKey key) throws IOException {
        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        writeDerInteger(inner, BigInteger.ZERO); // version 0
        writeDerInteger(inner, key.getModulus());
        writeDerInteger(inner, key.getPublicExponent());
        writeDerInteger(inner, key.getPrivateExponent());
        writeDerInteger(inner, key.getPrimeP());
        writeDerInteger(inner, key.getPrimeQ());
        writeDerInteger(inner, key.getPrimeExponentP());
        writeDerInteger(inner, key.getPrimeExponentQ());
        writeDerInteger(inner, key.getCrtCoefficient());

        byte[] innerBytes = inner.toByteArray();
        ByteArrayOutputStream outer = new ByteArrayOutputStream();
        outer.write(0x30); // SEQUENCE tag
        writeDerLength(outer, innerBytes.length);
        outer.write(innerBytes);
        return outer.toByteArray();
    }

    private static void writeDerInteger(OutputStream out, BigInteger val) throws IOException {
        out.write(0x02); // INTEGER tag
        byte[] bytes = val.toByteArray();
        writeDerLength(out, bytes.length);
        out.write(bytes);
    }

    private static void writeDerLength(OutputStream out, int length) throws IOException {
        if (length < 128) {
            out.write(length);
        } else if (length < 256) {
            out.write(0x81);
            out.write(length);
        } else {
            out.write(0x82);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        }
    }
}
