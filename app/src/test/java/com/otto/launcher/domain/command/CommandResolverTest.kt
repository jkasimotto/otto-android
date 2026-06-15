package com.otto.launcher.domain.command

import com.otto.launcher.domain.policy.AppDescriptor
import com.otto.launcher.domain.policy.AppGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandResolverTest {
    private val apps = listOf(
        AppDescriptor("Maps", "com.google.android.apps.maps", "MapsActivity"),
        AppDescriptor("Reddit", "com.reddit.frontpage", "RedditActivity"),
        AppDescriptor("Food Notes", "com.example.foodnotes", "FoodActivity")
    )

    @Test
    fun builtInCommandsWinOverAppSearch() {
        val result = CommandResolver().resolve("food", apps)

        assertEquals(CommandResult.BuiltIn(OttoCommand.CaptureFood), result)
    }

    @Test
    fun allowedAppsAppearForNormalSearch() {
        val result = CommandResolver().resolve("map", apps)

        val appResults = result as CommandResult.AppResults
        assertEquals("Maps", appResults.results.first().label)
        assertEquals(AppGate.Allowed, appResults.results.first().gate)
    }

    @Test
    fun distractingAppsDoNotAppearAsCasualSuggestions() {
        val result = CommandResolver().resolve("red", apps)

        assertTrue(result is CommandResult.NoResult)
    }

    @Test
    fun distractingAppsAppearOnStrongMatchWithGate() {
        val result = CommandResolver().resolve("redd", apps)

        val appResults = result as CommandResult.AppResults
        assertEquals("Reddit", appResults.results.first().label)
        assertTrue(appResults.results.first().gate is AppGate.Distraction)
    }
}

