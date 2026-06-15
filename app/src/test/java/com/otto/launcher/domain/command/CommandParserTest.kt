package com.otto.launcher.domain.command

import com.otto.launcher.domain.mode.OttoMode
import com.otto.launcher.domain.time.TimeCategoryIds
import com.otto.launcher.domain.time.TimeMode
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
            "budget" to OttoCommand.OpenTimeBudget,
            "time" to OttoCommand.OpenTimeReview,
            "done" to OttoCommand.FinishTimeBlock,
            "move" to OttoCommand.StartTimeBlock(TimeMode.MOVEMENT),
            "walk" to OttoCommand.StartTimeBlock(TimeMode.MOVEMENT),
            "social" to OttoCommand.StartTimeBlock(TimeMode.RELATIONSHIP),
            "rest" to OttoCommand.StartTimeBlock(TimeMode.REST),
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
    fun parsesTimeBlockCommands() {
        val cases = listOf(
            "start focus" to OttoCommand.StartTimeBlock(TimeMode.FOCUS_WORK),
            "start work" to OttoCommand.StartTimeBlock(TimeMode.FOCUS_WORK),
            "start social" to OttoCommand.StartTimeBlock(TimeMode.RELATIONSHIP),
            "start movement" to OttoCommand.StartTimeBlock(TimeMode.MOVEMENT),
            "start rest" to OttoCommand.StartTimeBlock(TimeMode.REST),
            "start admin" to OttoCommand.StartTimeCategoryBlock(TimeCategoryIds.ADMIN, "admin"),
            "start commute" to OttoCommand.StartTimeCategoryBlock(TimeCategoryIds.COMMUTE, "commute")
        )

        cases.forEach { (input, expected) ->
            assertEquals(expected, parser.parse(input))
        }
    }

    @Test
    fun parsesManualTimeDurations() {
        val cases = listOf(
            "walk 45" to OttoCommand.SaveTimeBlockDuration(TimeCategoryIds.MOVEMENT, 45, "walk"),
            "gym 60" to OttoCommand.SaveTimeBlockDuration(TimeCategoryIds.MOVEMENT, 60, "gym"),
            "social 90" to OttoCommand.SaveTimeBlockDuration(TimeCategoryIds.RELATIONSHIPS, 90, "social"),
            "admin 30" to OttoCommand.SaveTimeBlockDuration(TimeCategoryIds.ADMIN, 30, "admin"),
            "commute 25" to OttoCommand.SaveTimeBlockDuration(TimeCategoryIds.COMMUTE, 25, "commute"),
            "rest 20" to OttoCommand.SaveTimeBlockDuration(TimeCategoryIds.REST, 20, "rest"),
            "drift 10" to OttoCommand.SaveTimeBlockDuration(TimeCategoryIds.DIGITAL_DRIFT, 10, "drift")
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
        assertNull(parser.parse("walk 0"))
        assertNull(parser.parse("walk 1441"))
        assertNull(parser.parse("start everything"))
    }
}
