package be.orbinson.aem.groovy.console.reports.impl

import groovy.transform.Synchronized
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Modified
import org.osgi.service.metatype.annotations.Designate

@Component(service = ReportsConfigurationService, immediate = true)
@Designate(ocd = ReportsConfigurationProperties)
class DefaultReportsConfigurationService implements ReportsConfigurationService {

    private int defaultPageSize

    private int maxResultRows

    @Activate
    @Modified
    @Synchronized
    void activate(ReportsConfigurationProperties properties) {
        defaultPageSize = properties.defaultPageSize()
        maxResultRows = properties.maxResultRows()
    }

    @Override
    int getDefaultPageSize() {
        defaultPageSize
    }

    @Override
    int getMaxResultRows() {
        maxResultRows
    }
}
