package fr.edf.jenkins.plugins.mac.ssh.key.verifiers

import java.util.logging.Level
import java.util.logging.Logger

import org.apache.commons.lang.StringUtils
import org.kohsuke.stapler.DataBoundConstructor

import com.trilead.ssh2.ServerHostKeyVerifier
import com.trilead.ssh2.signature.KeyAlgorithm
import com.trilead.ssh2.signature.KeyAlgorithmManager

import fr.edf.jenkins.plugins.mac.Messages
import fr.edf.jenkins.plugins.mac.ssh.key.MacHostKey

class MacHostKeyVerifier implements ServerHostKeyVerifier {

    private static final Logger LOGGER = Logger.getLogger(MacHostKeyVerifier.name)

    private final MacHostKey parsedKey

    @DataBoundConstructor
    MacHostKeyVerifier(final String key) {
        super()
        try {
            this.parsedKey = parseKey(key)
        } catch (MacHostKeyVerifierException e) {
            throw new IllegalArgumentException("Invalid key: " + e.getMessage(), e)
        }
    }

    /**
     * @return the algorithm type and the key as String
     */
    public String getKey() {
        return null != parsedKey ? parsedKey.algorithm + " " + Base64.getEncoder().encodeToString(parsedKey.key) : ""
    }

    String[] getAlgorithms() {
        String[] algorithms = [parsedKey.getAlgorithm()]
        return algorithms
    }

    /**
     * {@inheritDoc}
     */
    @Override
    boolean verifyServerHostKey(String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey) throws Exception {
        final MacHostKey serverParsedKey = new MacHostKey(serverHostKeyAlgorithm, serverHostKey)
        if (parsedKey.equals(serverParsedKey)) {
            LOGGER.log(Level.FINE, Messages.MacHostKeyVerifier_KeyTrusted(hostname))
            return true
        } else {
            LOGGER.log(Level.WARNING, Messages.MacHostKeyVerifier_KeyNotTrusted(hostname))
            return false
        }
    }

    /**
     * Check the validity of the host key
     * @param key
     * @return MacHostKey
     * @throws MacHostKeyVerifierException
     */
    private static MacHostKey parseKey(String key) throws MacHostKeyVerifierException {
        if (StringUtils.isEmpty(key) || !key.contains(" ")) {
            throw new MacHostKeyVerifierException(Messages.MacHostKeyVerifier_TwoPartKey())
        }
        StringTokenizer tokenizer = new StringTokenizer(key, " ")
        String algorithm = tokenizer.nextToken()
        byte[] keyValue = Base64.getDecoder().decode(tokenizer.nextToken())
        if (null == keyValue) {
            throw new MacHostKeyVerifierException(Messages.MacHostKeyVerifier_Base64EncodedKeyValueRequired())
        }
        KeyAlgorithm keyAlgorithm = KeyAlgorithmManager.getSupportedAlgorithms().find { it.getKeyFormat().equals(algorithm) }
        if (null == keyAlgorithm) {
            throw new MacHostKeyVerifierException(Messages.MacHostKeyVerifier_UnexpectedKeyAlgorithm(algorithm))
        }
        try {
            keyAlgorithm.decodePublicKey(keyValue)
        } catch (IOException ex) {
            throw new MacHostKeyVerifierException(Messages.MacHostKeyVerifier_KeyValueDoesNotParse(algorithm), ex)
        }
        return new MacHostKey(algorithm, keyValue)
    }
}
