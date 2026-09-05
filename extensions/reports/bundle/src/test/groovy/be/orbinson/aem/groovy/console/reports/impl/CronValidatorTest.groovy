package be.orbinson.aem.groovy.console.reports.impl

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

class CronValidatorTest {

    @Test
    void "accepts a valid 6-field expression"() {
        CronValidator.validate("0 0 6 * * ?")
    }

    @Test
    void "accepts a valid 7-field expression with a year"() {
        CronValidator.validate("0 30 2 ? * MON 2026")
    }

    @Test
    void "rejects a blank expression"() {
        assertThrows(IllegalArgumentException) { CronValidator.validate("  ") }
        assertThrows(IllegalArgumentException) { CronValidator.validate(null) }
    }

    @Test
    void "rejects the wrong number of fields"() {
        assertThrows(IllegalArgumentException) { CronValidator.validate("0 0 6 * *") }
        assertThrows(IllegalArgumentException) { CronValidator.validate("0 0 6 * * ? 2026 extra") }
    }

    @Test
    void "rejects sub-minute schedules"() {
        assertThrows(IllegalArgumentException) { CronValidator.validate("* * * * * ?") }
        assertThrows(IllegalArgumentException) { CronValidator.validate("0/5 * * * * ?") }
        assertThrows(IllegalArgumentException) { CronValidator.validate("99 0 6 * * ?") }
    }
}
