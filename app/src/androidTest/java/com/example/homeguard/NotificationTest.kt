package com.example.homeguard

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationTest {

    @get:Rule
    val testRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)!!

        notificationManager.cancelAll()
    }

    @Test
    fun testTemperatureNotification() {

        testRule.scenario.onActivity { activity ->
            activity.sendTemperatureNotification("High Temperature Detected")
        }

        Thread.sleep(1000)

        val activeNotifications = notificationManager.activeNotifications

        // confirm that at least one notification exists
        assertTrue("Temperature notification was not sent!", activeNotifications.isNotEmpty())

    }

    @Test
    fun testGasNotification() {
        testRule.scenario.onActivity { activity ->
            activity.sendGasNotification("Gas detected!")
        }

        Thread.sleep(1000)

        // get active notis
        val activeNotifications = notificationManager.activeNotifications

        // confirm at least one notification exists
        assertTrue("Gas notification was not sent!", activeNotifications.isNotEmpty())

    }

    @Test
    fun testFireNotification() {
        testRule.scenario.onActivity { activity ->
            activity.sendFireNotification()
        }

        Thread.sleep(1000)

        val activeNotifications = notificationManager.activeNotifications

        assertTrue("Fire notification was not sent!", activeNotifications.isNotEmpty())

    }

    @Test
    fun testFloodNotification() {
        testRule.scenario.onActivity { activity ->
            activity.sendGasNotification("Water detected!")
        }

        Thread.sleep(1000)

        val activeNotifications = notificationManager.activeNotifications

        assertTrue("Flood notification was not sent!", activeNotifications.isNotEmpty())

    }
}