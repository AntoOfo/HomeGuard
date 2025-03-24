package com.example.homeguard

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.withId
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

    // tests if clicking call button opens EmergencyServicesActivity
    @Test
    fun testCallBtnOpensEmergencyServicesActivity() {
        onView(withId(R.id.phoneBtn)).perform(click())

        Intents.intended(hasComponent(EmergencyServicesActivity::class.java.name))
    }

}