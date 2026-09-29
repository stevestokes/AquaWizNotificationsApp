package app.aquawiznotifier

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test fun newerSemanticVersionIsDetected() {
        assertTrue(UpdateChecker.isNewer("v0.2.1", "0.2.0"))
        assertTrue(UpdateChecker.isNewer("v1.0.0", "0.9.9"))
        assertTrue(UpdateChecker.isNewer("v0.3.0-beta.1", "0.2.9"))
    }

    @Test fun sameOrOlderVersionIsIgnored() {
        assertFalse(UpdateChecker.isNewer("v0.2.0", "0.2.0"))
        assertFalse(UpdateChecker.isNewer("v0.1.9", "0.2.0"))
        assertFalse(UpdateChecker.isNewer("not-a-version", "0.2.0"))
    }
}
