package com.example.homeguard

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    // rule to launch home screen before each test
    @get:Rule
    var activityRule = ActivityScenarioRule(MainActivity::class.java)

    // sets up intents before tests
    @Before
    fun setup() {
        Intents.init()
    }

    @After
    fun cleanUp() {
        Intents.release()
    }

    // tests if clicking info btn opens info activity
    @Test
    fun testInfoBtnOpensInfoActivity() {
        onView(withId(R.id.infoBtn)).perform(click())

        Intents.intended(hasComponent(InfoActivity::class.java.name))
    }

    // tests if clicking call button opens emergency activity
    @Test
    fun testCallBtnOpensEmergencyServicesActivity() {
        onView(withId(R.id.phoneBtn)).perform(click())

        Intents.intended(hasComponent(EmergencyServicesActivity::class.java.name))
    }

    // test if clicking fire tile opens camera feed activity
    @Test
    fun testFireTileOpensCameraFeedActivity() {
        onView(withId(R.id.fireTile)).perform(click())

        Intents.intended(hasComponent(CameraFeedActivity::class.java.name))
    }

    // test if clicking gas tile opens gas dialog
    @Test
    fun testGasTileOpensGasDetailsDialog() {
        onView(withId(R.id.gasTile)).perform(click())

        // checks if dialog is shown by seeing if it has this text displayed
        onView(withText("Gas Level Details")).check(matches(isDisplayed()))
    }

    // test if clicking flood tile opens flood dialog
    @Test
    fun testFloodTileOpensFloodDetailsDialog() {
        onView(withId(R.id.floodTile)).perform(click())

        onView(withText("Water Level Details")).check(matches(isDisplayed()))
    }

    // tests if clicking temp tile opens the temp dialog
    @Test
    fun testTempTileOpensTempDetailsDialog() {
        onView(withId(R.id.temperatureTile)).perform(click())

        onView(withText("Temperature Details")).check(matches(isDisplayed()))
    }

}