package dev.tomerklein.dradis.ui

import dev.tomerklein.dradis.mqtt.ConnState
import dev.tomerklein.dradis.ui.components.ChipState
import dev.tomerklein.dradis.ui.components.chipStateFor
import org.junit.Assert.assertEquals
import org.junit.Test

class StateChipTest {
    @Test fun connectedIsSuccess() = assertEquals(ChipState.SUCCESS, chipStateFor(ConnState.CONNECTED))
    @Test fun connectingIsWarning() = assertEquals(ChipState.WARNING, chipStateFor(ConnState.CONNECTING))
    @Test fun disconnectedAndUnauthorizedAreError() {
        assertEquals(ChipState.ERROR, chipStateFor(ConnState.DISCONNECTED))
        assertEquals(ChipState.ERROR, chipStateFor(ConnState.UNAUTHORIZED))
    }
}
