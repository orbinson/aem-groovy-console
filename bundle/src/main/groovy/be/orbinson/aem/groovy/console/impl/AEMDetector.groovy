package be.orbinson.aem.groovy.console.impl

import com.adobe.granite.ui.clientlibs.HtmlLibraryManager
import groovy.util.logging.Slf4j
import org.apache.jackrabbit.api.observation.JackrabbitEventFilter
import org.apache.jackrabbit.api.observation.JackrabbitObservationManager
import org.apache.sling.api.resource.*
import org.apache.sling.jcr.api.SlingRepository
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference

import javax.jcr.RepositoryException
import javax.jcr.Session
import javax.jcr.observation.EventIterator
import javax.jcr.observation.EventListener

@Component(
        service = AEMDetector.class,
        immediate = true
)
@Slf4j("LOG")
class AEMDetector implements EventListener {

    @Reference
    private HtmlLibraryManager htmlLibraryManager;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private SlingRepository repository;

    private Session listenerSession

    @Activate
    protected void activate() {
        setClientLibraryPrimaryType();
        this.listenerSession = this.getServiceSession();
        JackrabbitEventFilter eventFilter = (new JackrabbitEventFilter())
                .setEventTypes(31)
                .setAbsPath("/apps/groovyconsole/clientlibs")
                .setNoLocal(true);
        JackrabbitObservationManager observationManager = (JackrabbitObservationManager) this.listenerSession.getWorkspace()
                .getObservationManager();
        observationManager.addEventListener(this, eventFilter);
    }

    @Deactivate
    protected void deactivate() {
        try {
            if (this.listenerSession != null) {
                try {
                    this.listenerSession.getWorkspace().getObservationManager().removeEventListener(this);
                } catch (RepositoryException e) {
                }

                this.listenerSession.logout();
            }
        } finally {
            this.listenerSession = null;
        }
    }

    private Session getServiceSession() throws RepositoryException {
        return this.repository.loginService("clientlibs-service", (String) null);
    }


    @Override
    void onEvent(EventIterator events) {
        setClientLibraryPrimaryType();
    }

    /**
     * Seeing as we deploy to Sling, the cq:ClientLibraryFolder primaryType is not available
     * it is set through the AEM Detector
     */
    void setClientLibraryPrimaryType() {
        ResourceResolver resourceResolver = null;
        try {
            resourceResolver = resourceResolverFactory.getServiceResourceResolver(null)
            setClientLibraryPrimaryType(resourceResolver)
        } catch (LoginException e) {
            LOG.warn("Could not get service resource resolver", e);
        } finally {
            if (resourceResolver != null) {
                resourceResolver.close();
            }
        }
    }

    void setClientLibraryPrimaryType(ResourceResolver resourceResolver) {
        Resource resource = resourceResolver.getResource("/apps/groovyconsole/clientlibs")
        if (resource != null) {
            ValueMap valueMap = resource.adaptTo(ModifiableValueMap.class)
            valueMap.put("jcr:primaryType", "cq:ClientLibraryFolder")
            resourceResolver.commit()
            htmlLibraryManager.invalidate(resource.getPath());
        }
    }
}
