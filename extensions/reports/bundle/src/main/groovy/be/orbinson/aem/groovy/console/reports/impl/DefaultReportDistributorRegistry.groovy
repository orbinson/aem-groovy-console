package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.ReportDistributor
import be.orbinson.aem.groovy.console.reports.ReportDistributorRegistry
import groovy.transform.Synchronized
import groovy.util.logging.Slf4j
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

import java.util.concurrent.CopyOnWriteArrayList

@Component(service = ReportDistributorRegistry, immediate = true)
@Slf4j("LOG")
class DefaultReportDistributorRegistry implements ReportDistributorRegistry {

    private volatile List<ReportDistributor> distributors = new CopyOnWriteArrayList<>()

    @Override
    List<ReportDistributor> getDistributors() {
        distributors.toSorted { distributor -> distributor.id }
    }

    @Override
    ReportDistributor getDistributor(String id) {
        distributors.find { distributor -> distributor.id == id }
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    @Synchronized
    void bindReportDistributor(ReportDistributor distributor) {
        distributors.add(distributor)

        LOG.info("added report distributor : {}", distributor.id)
    }

    @Synchronized
    void unbindReportDistributor(ReportDistributor distributor) {
        distributors.remove(distributor)

        LOG.info("removed report distributor : {}", distributor.id)
    }
}
