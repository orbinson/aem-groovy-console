package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.model.ReportParameter
import be.orbinson.aem.groovy.console.reports.model.ReportParameterType

import java.text.ParseException
import java.text.SimpleDateFormat

/**
 * Validates and coerces submitted (string) parameter values against the parameters declared on a report
 * definition.
 */
class ParameterCoercer {

    private static final List<String> DATE_FORMATS = [
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd"
    ]

    /**
     * Coerce raw parameter values.  A value may be a String, or a List of Strings for a {@code multiple}
     * parameter; a {@code multiple} parameter always coerces to a (possibly empty) List.
     *
     * @param parameters declared parameters
     * @param rawValues submitted values (String or List of Strings per parameter)
     * @return coerced values keyed by parameter name
     * @throws IllegalArgumentException when a required value is missing or a value cannot be coerced
     */
    static Map<String, Object> coerce(List<ReportParameter> parameters, Map<String, Object> rawValues) {
        def values = [:] as Map<String, Object>

        parameters.each { parameter ->
            values[parameter.name] = parameter.multiple ?
                    coerceMultiple(parameter, rawValues?.get(parameter.name)) :
                    coerceSingle(parameter, asScalar(rawValues?.get(parameter.name)))
        }

        values
    }

    private static Object coerceSingle(ReportParameter parameter, String rawValue) {
        if (rawValue == null || rawValue.empty) {
            rawValue = parameter.defaultValue
        }

        if (rawValue == null || rawValue.empty) {
            if (parameter.required) {
                throw new IllegalArgumentException("Missing required parameter: ${parameter.name}")
            }

            return null
        }

        coerceValue(parameter, rawValue)
    }

    private static List<Object> coerceMultiple(ReportParameter parameter, Object rawValue) {
        def rawValues = asList(rawValue).findAll { it != null && !(it as String).empty }

        if (!rawValues && parameter.defaultValue) {
            rawValues = [parameter.defaultValue]
        }

        if (!rawValues && parameter.required) {
            throw new IllegalArgumentException("Missing required parameter: ${parameter.name}")
        }

        rawValues.collect { value -> coerceValue(parameter, value as String) }
    }

    private static String asScalar(Object rawValue) {
        if (rawValue instanceof List) {
            return rawValue ? rawValue[0] as String : null
        }

        rawValue as String
    }

    private static List<Object> asList(Object rawValue) {
        if (rawValue == null) {
            return []
        }

        rawValue instanceof List ? rawValue as List : [rawValue]
    }

    private static Object coerceValue(ReportParameter parameter, String rawValue) {
        switch (parameter.type) {
            case ReportParameterType.NUMBER:
                try {
                    return new BigDecimal(rawValue)
                } catch (NumberFormatException ignored) {
                    throw new IllegalArgumentException("Invalid number for parameter ${parameter.name}: $rawValue")
                }
            case ReportParameterType.BOOLEAN:
                return Boolean.parseBoolean(rawValue)
            case ReportParameterType.DATE:
                return parseDate(parameter.name, rawValue)
            case ReportParameterType.SELECT:
                if (parameter.options && !parameter.options.contains(rawValue)) {
                    throw new IllegalArgumentException(
                            "Invalid option for parameter ${parameter.name}: $rawValue")
                }

                return rawValue
            default:
                return rawValue
        }
    }

    private static Date parseDate(String parameterName, String rawValue) {
        for (String pattern : DATE_FORMATS) {
            try {
                def format = new SimpleDateFormat(pattern)

                format.timeZone = TimeZone.getTimeZone("UTC")
                format.lenient = false

                return format.parse(rawValue)
            } catch (ParseException ignored) {
                // try next format
            }
        }

        throw new IllegalArgumentException("Invalid date for parameter $parameterName: $rawValue")
    }

    private ParameterCoercer() {

    }
}
