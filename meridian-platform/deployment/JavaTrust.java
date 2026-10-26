import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.naming.ldap.LdapName;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

/** Build-time mitigation and offline verification: https://ops.java/emudhra (2026-10-24). */
public final class JavaTrust {
    private static final Set<X500Principal> REMOVED = principals(
            "CN=emSign ECC Root CA - G3, O=eMudhra Technologies Limited, OU=emSign PKI, C=IN",
            "CN=emSign Root CA - G1, O=eMudhra Technologies Limited, OU=emSign PKI, C=IN",
            "CN=emSign Root CA - G2, O=eMudhra Technologies Limited, OU=emSign PKI, C=IN",
            "CN=AAA Certificate Services, O=Comodo CA Limited, L=Salford, ST=Greater Manchester, C=GB");
    private static final char[] PASSWORD = "changeit".toCharArray();

    public static void main(String[] args) throws Exception {
        require(args.length == 1 && Set.of("--apply", "--verify").contains(args[0]),
                "Usage: JavaTrust --apply | --verify");
        require(Runtime.version().feature() == 25, "Java 25 required");
        require(System.getProperty("javax.net.ssl.trustStore") == null,
                "Custom trustStore requires separate verification");
        Path security = Path.of(System.getProperty("java.home"), "lib", "security");
        require(!Files.exists(security.resolve("jssecacerts")), "Unexpected jssecacerts override");
        Path cacerts = security.resolve("cacerts");
        KeyStore store = KeyStore.getInstance(cacerts.toFile(), PASSWORD);
        Map<String, String> preserved = snapshot(store, false);
        require(!preserved.isEmpty(), "Empty unaffected trust store");
        for (String alias : Collections.list(store.aliases())) {
            X509Certificate cert = certificate(store, alias);
            X500Principal subject = cert.getSubjectX500Principal();
            // A familiar CN with a different full identity is ambiguous, never silently removed/ignored.
            if (REMOVED.stream().anyMatch(name -> commonName(name).equalsIgnoreCase(commonName(subject)))) {
                require(REMOVED.contains(subject), "Uncertain affected identity: " + subject);
            }
            if (REMOVED.contains(subject)) {
                require(args[0].equals("--apply"), "Affected trusted root remains: " + subject);
                require(subject.equals(cert.getIssuerX500Principal()) && cert.getBasicConstraints() >= 0,
                        "Affected identity is not a self-issued CA: " + subject);
                cert.verify(cert.getPublicKey());
                System.out.println("REMOVE " + alias + " | " + subject + " | SHA256=" + fingerprint(cert));
                store.deleteEntry(alias);
            }
        }
        if (args[0].equals("--apply")) {
            try (var output = Files.newOutputStream(cacerts)) {
                store.store(output, PASSWORD);
            }
        }
        KeyStore actual = KeyStore.getInstance(cacerts.toFile(), PASSWORD);
        require(snapshot(actual, true).equals(preserved), "Unaffected certificates changed during mitigation");
        Set<X500Principal> subjects = Collections.list(actual.aliases()).stream()
                .map(alias -> certificate(actual, alias).getSubjectX500Principal()).collect(Collectors.toSet());
        require(Collections.disjoint(subjects, REMOVED), "Affected root persisted");

        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null); // JSSE's effective default store, not a separately supplied keystore.
        Set<String> effective = Arrays.stream(factory.getTrustManagers())
                .filter(X509TrustManager.class::isInstance).map(X509TrustManager.class::cast)
                .flatMap(manager -> Arrays.stream(manager.getAcceptedIssuers()))
                .map(JavaTrust::fingerprint).collect(Collectors.toSet());
        require(effective.equals(Set.copyOf(preserved.values())), "Effective JSSE trust differs from verified cacerts");
        REMOVED.stream().map(X500Principal::getName).sorted().forEach(name -> System.out.println("ABSENT " + name));
        System.out.println("PASS " + System.getProperty("java.vendor") + " " + Runtime.version()
                + "; " + actual.size() + " anchors; unaffected fingerprints preserved; effective default JSSE trust verified");
    }

    private static Set<X500Principal> principals(String... names) {
        return Arrays.stream(names).map(X500Principal::new).collect(Collectors.toUnmodifiableSet());
    }

    private static String commonName(X500Principal name) {
        try {
            var values = new LdapName(name.getName()).getRdns().stream()
                    .filter(rdn -> rdn.getType().equalsIgnoreCase("CN")).toList();
            require(values.size() <= 1, "Ambiguous common name: " + name);
            return values.isEmpty() ? "" : values.getFirst().getValue().toString();
        } catch (javax.naming.InvalidNameException ex) {
            throw new IllegalStateException("Invalid certificate identity", ex);
        }
    }

    private static X509Certificate certificate(KeyStore store, String alias) {
        try {
            require(store.isCertificateEntry(alias), "Unexpected non-certificate entry: " + alias);
            require(store.getCertificate(alias) instanceof X509Certificate, "Non-X509 entry: " + alias);
            return (X509Certificate) store.getCertificate(alias);
        } catch (java.security.KeyStoreException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Map<String, String> snapshot(KeyStore store, boolean includeAffected) throws Exception {
        Map<String, String> result = new TreeMap<>();
        for (String alias : Collections.list(store.aliases())) {
            X509Certificate cert = certificate(store, alias);
            if (includeAffected || !REMOVED.contains(cert.getSubjectX500Principal())) {
                result.put(alias, fingerprint(cert));
            }
        }
        return result;
    }

    private static String fingerprint(X509Certificate cert) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
