package com.otto.launcher.domain.policy

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPolicyEngineTest {
    @Test
    fun workAppsAreAllowedInsideWeekdayWindow() {
        val engine = AppPolicyEngine(clock = fixedClock("2026-06-15T10:00:00Z"))
        val gate = engine.gateFor(AppDescriptor("Slack", "com.Slack", "SlackActivity"))

        assertEquals(AppGate.Allowed, gate)
    }

    @Test
    fun workAppsAreClosedOutsideWeekdayWindow() {
        val engine = AppPolicyEngine(clock = fixedClock("2026-06-15T20:00:00Z"))
        val gate = engine.gateFor(AppDescriptor("Slack", "com.Slack", "SlackActivity"))

        assertTrue(gate is AppGate.WorkWindowClosed)
    }

    @Test
    fun distractingAppsRequireGate() {
        val engine = AppPolicyEngine(clock = fixedClock("2026-06-15T10:00:00Z"))
        val gate = engine.gateFor(AppDescriptor("Reddit", "com.reddit.frontpage", "RedditActivity"))

        assertTrue(gate is AppGate.Distraction)
    }

    @Test
    fun messengerIsPeopleNotDistraction() {
        val engine = AppPolicyEngine(clock = fixedClock("2026-06-15T10:00:00Z"))
        val app = AppDescriptor("Messenger", "com.facebook.orca", "MessengerActivity")

        assertEquals(AppTier.PEOPLE, engine.policyFor(app).tier)
        assertEquals(AppGate.Allowed, engine.gateFor(app))
    }

    @Test
    fun facebookAppRemainsDistraction() {
        val engine = AppPolicyEngine(clock = fixedClock("2026-06-15T10:00:00Z"))
        val gate = engine.gateFor(AppDescriptor("Facebook", "com.facebook.katana", "FacebookActivity"))

        assertTrue(gate is AppGate.Distraction)
    }

    private fun fixedClock(value: String): Clock {
        return Clock.fixed(Instant.parse(value), ZoneId.of("UTC"))
    }
}
