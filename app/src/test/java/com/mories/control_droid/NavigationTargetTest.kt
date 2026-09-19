package com.mories.control_droid

import com.mories.control_droid.ui.NavigationTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTargetTest {
    @Test
    fun routeHelpers_buildParameterizedRoutes() {
        assertEquals("control/{id}", NavigationTarget.Control.withParam("id"))
        assertEquals("control/device-1", NavigationTarget.Control.withArg("device-1"))
    }

    @Test
    fun fromRoute_matchesBaseAndParameterizedRoutes() {
        assertEquals(NavigationTarget.Home, NavigationTarget.fromRoute("home"))
        assertEquals(
            NavigationTarget.Control,
            NavigationTarget.fromRoute("control/device-1")
        )
    }
}
