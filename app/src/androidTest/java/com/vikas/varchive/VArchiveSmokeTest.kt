package com.vikas.varchive

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VArchiveSmokeTest {
    @Test fun primaryNavigationAndBrandingRender() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(device.wait(Until.hasObject(By.text("VArchive")), 5_000))
            device.findObject(By.text("Files")).click()
            assertTrue(device.wait(Until.hasObject(By.text("No folders granted")), 5_000))
            device.findObject(By.text("Settings")).click()
            assertTrue(device.wait(Until.hasObject(By.text("About VArchive")), 5_000))
            device.findObject(By.text("About VArchive")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Created by V!K@$")), 5_000))
        }
    }
}
