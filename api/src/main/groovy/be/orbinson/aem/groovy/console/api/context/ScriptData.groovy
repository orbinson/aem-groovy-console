package be.orbinson.aem.groovy.console.api.context

import org.apache.sling.api.resource.ResourceResolver
import org.osgi.annotation.versioning.ConsumerType

/**
 * Script data for saving scripts.
 */
@ConsumerType
interface ScriptData {

    /**
     * Resource resolver for saving scripts.
     *
     * @return resource resolver
     */
    ResourceResolver getResourceResolver()

    /**
     * File name to be saved.
     *
     * @return file name
     */
    String getFileName()

    /**
     * Script content to be saved.
     *
     * @return script content
     */
    String getScript()
}
