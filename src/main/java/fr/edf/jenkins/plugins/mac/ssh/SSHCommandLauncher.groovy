package fr.edf.jenkins.plugins.mac.ssh

import java.util.logging.Level
import java.util.logging.Logger

import org.antlr.v4.runtime.misc.NotNull
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringUtils
import org.kohsuke.accmod.Restricted
import org.kohsuke.accmod.restrictions.NoExternalUse

import com.trilead.ssh2.ChannelCondition
import com.trilead.ssh2.Connection
import com.trilead.ssh2.SCPClient
import com.trilead.ssh2.Session

import fr.edf.jenkins.plugins.mac.ssh.connection.SSHConnectionConfiguration
import fr.edf.jenkins.plugins.mac.ssh.connection.SSHConnectionFactory

/**
 * Runner of SSH command.
 * @author Mathieu DELROCQ
 *
 */
protected class SSHCommandLauncher {

    private static final Logger LOGGER = Logger.getLogger(SSHCommandLauncher.name)

    final static String UTF8 = "UTF-8"

    /**
     * Execute a command with the given connection
     * @param connectionConfiguration
     * @param ignoreError : if true don't throw exception if return status != 0
     * @param command : ssh command to launch
     * @return
     * @throws Exception if cannot execute the command or if the command return an error
     */
    @Restricted(NoExternalUse)
    protected static String executeCommand(@NotNull SSHConnectionConfiguration connectionConfiguration, @NotNull boolean ignoreError, @NotNull String command) throws Exception {
        Connection connection = null
        Session session = null
        try {
            connection = SSHConnectionFactory.getSshConnection(connectionConfiguration)
            session = connection.openSession()
            LOGGER.log(Level.FINE, "Executing command {0}", command)
            session.execCommand(command)
            session.waitForCondition(ChannelCondition.EXIT_STATUS | ChannelCondition.EXIT_SIGNAL, 5000)
            LOGGER.log(Level.FINEST, "Exit SIGNAL : {0}", session.getExitSignal())
            LOGGER.log(Level.FINEST,"Exit STATUS : {0}", null != session.getExitStatus() ? session.getExitStatus().intValue() : null)
            session.close()
            connection.close()
            String out = convertInputStream(session.getStdout())
            String err = convertInputStream(session.getStderr())
            LOGGER.log(Level.FINEST, out)
            LOGGER.log(Level.FINEST,err)
            if(!ignoreError && null != session.exitStatus && session.exitStatus.intValue() != 0) {
                String error = String.format("Failed to execute command %s", command)
                LOGGER.log(Level.SEVERE, error)
                throw new Exception(error)
            }
            LOGGER.log(Level.FINE, "Executed command {0} with exit status {1}", command, null != session.exitStatus ? session.exitStatus.intValue() : null)
            return StringUtils.isNotEmpty(out) ? out : StringUtils.isNotEmpty(err) ? err : ""
        } catch(Exception e) {
            if(session != null) session.close()
            if(connection != null) connection.close()
            throw e
        }
    }

    /**
     * Create a file on the remote machine in the output directory with the given input content and the given file name.
     * @param input : content of the file
     * @param fileName : name of the file
     * @param outputDir : directory where the file will be created
     * @param connectionConfiguration : connection informations of the remote machine
     * @throws Exception
     */
    protected static void sendFile(SSHConnectionConfiguration connectionConfiguration, InputStream input, String fileName, String outputDir) throws Exception {
        Connection connection = null
        SCPClient scpCli = null
        try {
            connection = SSHConnectionFactory.getSshConnection(connectionConfiguration)
            scpCli = new SCPClient(connection)
            scpCli.put(input.getBytes(), fileName, outputDir)
            scpCli = null
            connection.close()
        }catch(Exception e) {
            scpCli = null
            connection.close()
            throw e
        }
    }

    /**
     * return the string value of an InputStream
     * @param out
     * @return output
     */
    private static String convertInputStream(@NotNull InputStream out) {
        StringWriter result = new StringWriter()
        IOUtils.copy(out, result, UTF8)
        return result.toString()
    }
}