package be.orbinson.aem.groovy.console.impl

import com.day.cq.wcm.api.PageManagerFactory
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(
        service = AEMDetector.class,
        immediate = true
)
class AEMDetector {

    // Dependency to detect AEM
    @Reference
    private PageManagerFactory pageManagerFactory;

}
