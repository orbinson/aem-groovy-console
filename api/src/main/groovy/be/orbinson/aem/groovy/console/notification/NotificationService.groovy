package be.orbinson.aem.groovy.console.notification

import be.orbinson.aem.groovy.console.response.RunScriptResponse
import org.osgi.annotation.versioning.ConsumerType

/**
 * Services may implement this interface to provide additional notifications for Groovy Console script executions.
 */
@ConsumerType
interface NotificationService {

    /**
     * Send a notification for the given script response.
     *
     * @param response script execution response
     */
    void notify(RunScriptResponse response)
}
