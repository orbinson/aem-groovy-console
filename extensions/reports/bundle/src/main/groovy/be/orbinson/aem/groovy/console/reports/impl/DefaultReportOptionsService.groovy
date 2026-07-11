package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.reports.ReportException
import be.orbinson.aem.groovy.console.reports.ReportOptionsService
import be.orbinson.aem.groovy.console.reports.data.OptionList
import groovy.json.JsonSlurper
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import org.apache.sling.api.resource.ResourceResolver
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.CHARSET

/**
 * Resolves dynamic parameter options by running the author-supplied script through the console.  The script
 * runs as a {@link DefaultReportScriptContext} (so it gets the <code>report</code> and <code>params</code>
 * bindings) and is expected to return a {@link OptionList}, which serializes to a
 * <code>{"options":[{"value":…,"label":…}]}</code> envelope in the script result.
 */
@Component(service = ReportOptionsService, immediate = true)
@Slf4j("LOG")
class DefaultReportOptionsService implements ReportOptionsService {

    @Reference
    private GroovyConsoleService groovyConsoleService

    @Override
    List<Map<String, String>> resolveOptions(String script, Map<String, Object> parameterValues,
                                             ResourceResolver resourceResolver) {
        if (!script?.trim()) {
            return []
        }

        // run on a detached clone so a options script that makes (then fails to commit) transient JCR changes
        // cannot leave them pending on the request-scoped resolver — runScript does not revert on failure
        resourceResolver.clone(null).withCloseable { optionsResolver ->
            def outputStream = new ByteArrayOutputStream()
            def scriptContext = new DefaultReportScriptContext(
                    reportName: "options",
                    parameterValues: parameterValues ?: [:],
                    resourceResolver: optionsResolver,
                    outputStream: outputStream,
                    printStream: new PrintStream(outputStream, true, CHARSET),
                    script: script,
                    userId: optionsResolver.userID
            )

            def response = groovyConsoleService.runScript(scriptContext)

            if (response.exceptionStackTrace) {
                throw new ReportException("Dynamic options script failed:\n${response.exceptionStackTrace}")
            }

            parseOptions(response.result)
        }
    }

    @PackageScope
    static List<Map<String, String>> parseOptions(String result) {
        if (!result) {
            return []
        }

        def parsed
        try {
            parsed = new JsonSlurper().parseText(result)
        } catch (Exception ignored) {
            throw new ReportException("Dynamic options script did not return a valid option list " +
                    "(use report.options())")
        }

        if (!(parsed instanceof Map) || !(parsed[OptionList.JSON_ENVELOPE_KEY] instanceof List)) {
            throw new ReportException("Dynamic options script did not return an option list (use report.options())")
        }

        (parsed[OptionList.JSON_ENVELOPE_KEY] as List).collect { option ->
            def value = (option instanceof Map ? option["value"] : option) as String

            [value: value, label: (option instanceof Map ? option["label"] : null) as String ?: value]
        }.findAll { it.value != null }
    }
}
