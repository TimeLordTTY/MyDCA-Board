package com.timelordtty.mydca.notification

import org.junit.Assert.*
import org.junit.Test

class DraftCreationGateTest {
    @Test fun onlyOneConcurrentCreationIsAllowed() {
        assertTrue(DraftCreationGate.tryAcquire("candidate"))
        assertFalse(DraftCreationGate.tryAcquire("candidate"))
        DraftCreationGate.release("candidate")
        assertTrue(DraftCreationGate.tryAcquire("candidate"))
        DraftCreationGate.release("candidate")
    }
}
