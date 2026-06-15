package com.otto.launcher.domain.command

import com.otto.launcher.domain.mode.OttoMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandParserTest {
    private val parser = CommandParser()

    @Test
    fun parsesBuiltInCommands() {
        val cases = listOf(
            "food" to OttoCommand.CaptureFood,
            "food 2400" to OttoCommand.CaptureFoodWithEnergy(2400),
            "drink" to OttoCommand.CaptureDrink,
            "w 82.4" to OttoCommand.SaveWeight(82.4),
            "weight 82,4" to OttoCommand.SaveWeight(82.4),
            "sleep" to OttoCommand.StartSleep,
            "wake" to OttoCommand.EndSleep,
            "today" to OttoCommand.OpenToday,
            "review" to OttoCommand.OpenReview,
            "week" to OttoCommand.OpenWeek,
            "focus" to OttoCommand.SetMode(OttoMode.FOCUS),
            "open" to OttoCommand.SetMode(OttoMode.OPEN),
            "settings" to OttoCommand.OpenMaintenance(MaintenanceSection.SETTINGS),
            "logs" to OttoCommand.OpenMaintenance(MaintenanceSection.LOGS),
            "update" to OttoCommand.OpenMaintenance(MaintenanceSection.UPDATE)
        )

        cases.forEach { (input, expected) ->
            assertEquals(expected, parser.parse(input))
        }
    }

    @Test
    fun parsesNoteAndTaskText() {
        assertEquals(
            OttoCommand.SaveNote("buy milk"),
            parser.parse("note buy milk")
        )
        assertEquals(
            OttoCommand.SaveTask("call mum"),
            parser.parse("task call mum")
        )
    }

    @Test
    fun ignoresIncompleteCommands() {
        assertNull(parser.parse(""))
        assertNull(parser.parse("note"))
        assertNull(parser.parse("task "))
        assertNull(parser.parse("food soon"))
        assertNull(parser.parse("w soon"))
    }
}

