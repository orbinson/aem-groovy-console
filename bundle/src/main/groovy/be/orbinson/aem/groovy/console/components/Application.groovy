package be.orbinson.aem.groovy.console.components

import be.orbinson.aem.groovy.console.impl.AEMDetector
import org.apache.sling.api.resource.Resource
import org.apache.sling.models.annotations.Model
import org.apache.sling.models.annotations.injectorspecific.InjectionStrategy
import org.apache.sling.models.annotations.injectorspecific.OSGiService

@Model(adaptables = Resource)
class Application {

    @OSGiService(injectionStrategy = InjectionStrategy.OPTIONAL)
    private AEMDetector aemDetector;

    boolean isAem() {
        return aemDetector != null;
    }

    boolean isSling() {
        return aemDetector == null;
    }
}