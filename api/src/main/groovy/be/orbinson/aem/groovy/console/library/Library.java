package be.orbinson.aem.groovy.console.library;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.LOCAL_VARIABLE, ElementType.FIELD})
public @interface Library {

    /**
     * JCR paths to a groovy file or directory of groovy files
     */
    String[] value();

}
