package org.apache.groovy.json.internal;

import org.apache.groovy.json.DefaultFastStringService;
import org.apache.groovy.json.FastStringService;

/**
 * To be removed once GROOVY-10881 is fixed
 */
public class FastStringUtils {

    private static class ServiceHolder {
        static final FastStringService INSTANCE = loadService();

        // FIX for GROOVY-10881
        private static FastStringService loadService() {
            return new DefaultFastStringService();
        }
    }

    private static FastStringService getService() {
        return ServiceHolder.INSTANCE;
    }

    /**
     * @param string string to grab array from.
     * @return char array from string
     */
    public static char[] toCharArray(final String string) {
        return getService().toCharArray(string);
    }

    /**
     * @param charSequence to grab array from.
     * @return char array from char sequence
     */
    public static char[] toCharArray(final CharSequence charSequence) {
        return toCharArray(charSequence.toString());
    }

    /**
     * @param chars to shove array into.
     * @return new string with chars copied into it
     */
    public static String noCopyStringFromChars(final char[] chars) {
        return getService().noCopyStringFromChars(chars);
    }
}