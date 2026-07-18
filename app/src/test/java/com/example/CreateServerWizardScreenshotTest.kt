package com.example

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.servercreation.CreateServerWizardScreen
import com.example.ui.theme.MineHostAppTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-420dpi")
class CreateServerWizardScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun captureAllSteps() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val mainViewModel = MainViewModel(application)
        composeTestRule.setContent {
            MineHostAppTheme {
                CreateServerWizardScreen(
                    mainViewModel = mainViewModel,
                    onBack = {},
                    onDone = {}
                )
            }
        }

        val stepNames = listOf("Basics", "Engine", "Version", "World", "Performance", "Network", "Review")
        
        for (i in 0 until 7) {
            composeTestRule.waitForIdle()
            composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/step_${i + 1}_${stepNames[i]}.png")
            if (i < 6) {
                composeTestRule.onNodeWithText("Continue").performClick()
            }
        }
    }
}
