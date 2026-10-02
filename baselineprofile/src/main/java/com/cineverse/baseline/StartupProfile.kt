package com.cineverse.baseline

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * The journeys worth compiling ahead of time.
 *
 * A baseline profile is only as good as the paths it walks, and the temptation
 * is to record a cold start and stop there. That gets you a fast splash screen
 * and a janky first scroll. What is recorded here is the first MINUTE of using
 * the app: open it, let the hero settle, scroll the rails, open a title, read
 * the episode list, come back. Those are the frames a new user actually judges
 * it on.
 *
 * Everything is wrapped so a step that cannot run on a given device -- a rail
 * that has not loaded, a sign-in the device does not have -- does not fail the
 * whole generation. A profile missing one journey is useful; no profile is not.
 */
class StartupProfile {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        // The profile is keyed on a stable start-up: waiting for the first
        // frame rather than a fixed delay keeps it honest on a slow device.
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        // The hero and the first rails.
        device.waitForIdle()
        device.wait(Until.hasObject(By.scrollable(true)), 10_000)

        runCatching {
            val list = device.findObject(By.scrollable(true))
            repeat(3) {
                list.setGestureMargin(device.displayWidth / 5)
                list.scroll(Direction.DOWN, 0.8f)
                device.waitForIdle()
            }
            list.scroll(Direction.UP, 1f)
        }

        // A title page: the hero, the head, the segmented control, the episodes.
        runCatching {
            device.findObject(By.descContains("poster"))?.click()
                ?: device.click(device.displayWidth / 4, device.displayHeight / 2)
            device.waitForIdle()
            device.wait(Until.hasObject(By.textContains("Episodes")), 8_000)
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.6f)
            device.waitForIdle()
            device.pressBack()
            device.waitForIdle()
        }

        // The other tabs, each of which brings in its own screenful of code.
        for (tab in listOf("Discover", "My List", "Stats")) {
            runCatching {
                device.findObject(By.text(tab))?.click()
                device.waitForIdle()
                device.wait(Until.hasObject(By.scrollable(true)), 5_000)
            }
        }
        runCatching { device.findObject(By.text("Home"))?.click() }
        device.waitForIdle()
    }

    private companion object {
        // The benchmark variants carry a `.benchmark` suffix so profiling can
        // never replace a real install and sign its owner out of their library.
        const val PACKAGE = "com.cineverse.app.benchmark"
    }
}
