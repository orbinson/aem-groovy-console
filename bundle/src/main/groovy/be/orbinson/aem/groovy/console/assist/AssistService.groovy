package be.orbinson.aem.groovy.console.assist

import be.orbinson.aem.groovy.console.api.context.ScriptContext

/**
 * Provides IDE-like code assistance data for the modern console UI: an index of classes visible to
 * the script classloader, class member reflection, and compile-only diagnostics.
 */
interface AssistService {

    /**
     * Find classes visible to the script classloader matching the given prefix.  Matches on fully qualified
     * name prefix or simple name prefix (case-insensitive).
     *
     * @param prefix class name prefix to match
     * @param limit maximum number of results
     * @return map containing the truncation flag and matching classes
     */
    Map<String, Object> findClasses(String prefix, int limit)

    /**
     * Reflect the members (methods, fields, bean properties, and Groovy metaclass methods) of the given class.
     *
     * @param className fully qualified class name
     * @return map containing the class name and its members, or an error message if the class is not loadable
     */
    Map<String, Object> getMembers(String className)

    /**
     * Compile (without running) the script in the given context using the same shell configuration as
     * script execution, returning Monaco-marker-shaped compilation errors.
     *
     * @param scriptContext script context containing the script to compile
     * @return list of marker maps (empty if the script compiles)
     */
    List<Map<String, Object>> compile(ScriptContext scriptContext)
}
