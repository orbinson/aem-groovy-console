package be.orbinson.aem.groovy.console.components

import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.models.annotations.Model
import org.apache.sling.models.annotations.injectorspecific.OSGiService
import org.apache.sling.models.annotations.injectorspecific.Self

/**
 * Decides which console UI (classic or modern) a request resolves to, based on the request
 * selector or, in its absence, the configured default UI.
 */
@Model(adaptables = SlingHttpServletRequest)
class ConsoleRouter {

    static final String UI_CLASSIC = "classic"

    static final String UI_MODERN = "modern"

    @OSGiService
    private ConfigurationService configurationService

    @Self
    private SlingHttpServletRequest request

    String getActiveUi() {
        def selectors = request.requestPathInfo.selectors as List

        if (selectors.contains(UI_CLASSIC)) {
            UI_CLASSIC
        } else if (selectors.contains(UI_MODERN)) {
            UI_MODERN
        } else {
            configurationService.defaultUi == UI_CLASSIC ? UI_CLASSIC : UI_MODERN
        }
    }

    boolean isModern() {
        activeUi == UI_MODERN
    }

    boolean isClassic() {
        activeUi == UI_CLASSIC
    }
}
