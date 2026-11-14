/*
 *  SIP Inspector - TLS Security Manager
 *  Manages keystores, truststores, and certificates for TLS connections
 */
package sipinspector.Utils;

import javax.net.ssl.*;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.*;

/**
 * Manages TLS security: keystores, truststores, certificates
 * Based on SIP Inspector Pro Version 6 implementation
 */
public class SecurityManager {
    
    private static final String SECURITY_DIR = "security";
    private static final String KEYSTORE_FILE = "SIPInspKeystore.jks";
    private static final String TRUSTSTORE_FILE = "SIPInspTruststore.jks";
    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final String TRUSTSTORE_PASSWORD = "changeit";
    
    private KeyStore keystore;
    private KeyStore truststore;
    private SSLContext sslContext;
    
    public SecurityManager() {
        ensureSecurityDirectory();
    }
    
    /**
     * Ensure security directory exists
     */
    private void ensureSecurityDirectory() {
        File secDir = new File(SECURITY_DIR);
        if (!secDir.exists()) {
            secDir.mkdirs();
        }
    }
    
    /**
     * Initialize SSL context with keystore and truststore
     * Version 6 feature: Trusts all self-signed certificates by default
     */
    public SSLContext initializeSSLContext(boolean trustAllCerts) throws Exception {
        // Load or create keystore
        keystore = loadOrCreateKeyStore(KEYSTORE_FILE, KEYSTORE_PASSWORD);
        
        // Load or create truststore
        truststore = loadOrCreateKeyStore(TRUSTSTORE_FILE, TRUSTSTORE_PASSWORD);
        
        // Initialize key manager factory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keystore, KEYSTORE_PASSWORD.toCharArray());
        
        // Initialize trust manager factory
        TrustManager[] trustManagers;
        if (trustAllCerts) {
            // Version 6 feature: Trust all self-signed certificates
            trustManagers = createTrustAllManager();
        } else {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(truststore);
            trustManagers = tmf.getTrustManagers();
        }
        
        // Create SSL context
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), trustManagers, new SecureRandom());
        
        return sslContext;
    }
    
    /**
     * Load existing keystore or create new one
     */
    private KeyStore loadOrCreateKeyStore(String filename, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        File ksFile = new File(SECURITY_DIR, filename);
        
        if (ksFile.exists()) {
            // Load existing keystore
            try (FileInputStream fis = new FileInputStream(ksFile)) {
                ks.load(fis, password.toCharArray());
            }
        } else {
            // Create new keystore
            ks.load(null, password.toCharArray());
            // Save empty keystore
            try (FileOutputStream fos = new FileOutputStream(ksFile)) {
                ks.store(fos, password.toCharArray());
            }
        }
        
        return ks;
    }
    
    /**
     * Create trust manager that accepts all certificates
     * Version 6 feature for simplified TLS setup
     */
    private TrustManager[] createTrustAllManager() {
        return new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
                
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    // Trust all client certificates
                }
                
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    // Trust all server certificates (including self-signed)
                }
            }
        };
    }
    
    /**
     * Add certificate to keystore
     */
    public void addCertificateToKeyStore(String alias, String certPath, String keyPath) throws Exception {
        // Load certificate
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        java.security.cert.Certificate cert;
        try (FileInputStream fis = new FileInputStream(certPath)) {
            cert = cf.generateCertificate(fis);
        }
        
        // Load private key
        PrivateKey privateKey = loadPrivateKey(keyPath);
        
        // Add to keystore
        keystore.setKeyEntry(alias, privateKey, KEYSTORE_PASSWORD.toCharArray(), 
                            new java.security.cert.Certificate[]{cert});
        
        // Save keystore
        saveKeyStore(keystore, KEYSTORE_FILE, KEYSTORE_PASSWORD);
    }
    
    /**
     * Add trusted certificate to truststore
     */
    public void addTrustedCertificate(String alias, String certPath) throws Exception {
        // Load certificate
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        java.security.cert.Certificate cert;
        try (FileInputStream fis = new FileInputStream(certPath)) {
            cert = cf.generateCertificate(fis);
        }
        
        // Add to truststore
        truststore.setCertificateEntry(alias, cert);
        
        // Save truststore
        saveKeyStore(truststore, TRUSTSTORE_FILE, TRUSTSTORE_PASSWORD);
    }
    
    /**
     * Load private key from PEM file
     */
    private PrivateKey loadPrivateKey(String keyPath) throws Exception {
        // Read PEM file
        String keyPEM = readPEMFile(keyPath);
        
        // Remove PEM headers/footers
        keyPEM = keyPEM.replace("-----BEGIN PRIVATE KEY-----", "")
                       .replace("-----END PRIVATE KEY-----", "")
                       .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                       .replace("-----END RSA PRIVATE KEY-----", "")
                       .replaceAll("\\s", "");
        
        // Decode base64
        byte[] keyBytes = java.util.Base64.getDecoder().decode(keyPEM);
        
        // Generate private key
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(keySpec);
    }
    
    /**
     * Read PEM file content
     */
    private String readPEMFile(String filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }
    
    /**
     * Save keystore to file
     */
    private void saveKeyStore(KeyStore ks, String filename, String password) throws Exception {
        File ksFile = new File(SECURITY_DIR, filename);
        try (FileOutputStream fos = new FileOutputStream(ksFile)) {
            ks.store(fos, password.toCharArray());
        }
    }
    
    /**
     * Get SSL context
     */
    public SSLContext getSSLContext() {
        return sslContext;
    }
    
    /**
     * Check if keystore has certificates
     */
    public boolean hasKeystoreCertificates() throws Exception {
        return keystore != null && keystore.size() > 0;
    }
    
    /**
     * Check if truststore has certificates
     */
    public boolean hasTruststoreCertificates() throws Exception {
        return truststore != null && truststore.size() > 0;
    }
    
    /**
     * Get security directory path
     */
    public static String getSecurityDirectory() {
        return SECURITY_DIR;
    }
}
