package com.mories.control_droid.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentsSmokeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun controlActionButton_invokesClickHandler() {
        var clicked = false

        composeTestRule.setContent {
            ControlActionButton(label = "Run", onClick = { clicked = true })
        }

        composeTestRule.onNodeWithText("Run").performClick()

        assertTrue(clicked)
    }

    @Test
    fun deviceCard_rendersDeviceDetails() {
        composeTestRule.setContent {
            DeviceCard(name = "Living Room", ip = "192.168.1.20", onClick = {})
        }

        composeTestRule.onNodeWithText("Living Room").assertExists()
        composeTestRule.onNodeWithText("IP: 192.168.1.20").assertExists()
    }

    @Test
    fun appToolbar_rendersTitleAndAction() {
        var clicked = false

        composeTestRule.setContent {
            AppToolbar(title = "Settings", onBackClick = {}) {
                TextButton(onClick = { clicked = true }) {
                    Text("Save")
                }
            }
        }

        composeTestRule.onNodeWithText("Settings").assertExists()
        composeTestRule.onNodeWithContentDescription("Back").assertExists()
        composeTestRule.onNodeWithText("Save").performClick()

        assertTrue(clicked)
    }
}
