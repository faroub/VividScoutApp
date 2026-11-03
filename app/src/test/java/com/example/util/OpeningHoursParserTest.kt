package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class OpeningHoursParserTest {

    private fun createCalendar(hour: Int, minute: Int, dayOfWeek: Int = Calendar.MONDAY): Calendar {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, dayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    @Test
    fun parseOpeningStatus_twentyFourSeven_returnsOpenNow() {
        val cal = createCalendar(3, 15)
        val status = OpeningHoursParser.parseOpeningStatus("24/7", cal)
        assertEquals(OpeningStatus.OPEN_NOW, status)
    }

    @Test
    fun parseOpeningStatus_boundaryTimes_evaluatesOpenAndClosedAccurately() {
        val hours = "Mo-Fr 09:00-18:00"

        // 08:59 (1 min before opening) -> CLOSED_NOW
        val cal859 = createCalendar(8, 59, Calendar.MONDAY)
        assertEquals(OpeningStatus.CLOSED_NOW, OpeningHoursParser.parseOpeningStatus(hours, cal859))

        // 09:00 (exact opening boundary) -> OPEN_NOW
        val cal900 = createCalendar(9, 0, Calendar.MONDAY)
        assertEquals(OpeningStatus.OPEN_NOW, OpeningHoursParser.parseOpeningStatus(hours, cal900))

        // 17:59 (1 min before closing) -> OPEN_NOW
        val cal1759 = createCalendar(17, 59, Calendar.MONDAY)
        assertEquals(OpeningStatus.OPEN_NOW, OpeningHoursParser.parseOpeningStatus(hours, cal1759))

        // 18:00 (exact closing boundary) -> CLOSED_NOW
        val cal1800 = createCalendar(18, 0, Calendar.MONDAY)
        assertEquals(OpeningStatus.CLOSED_NOW, OpeningHoursParser.parseOpeningStatus(hours, cal1800))
    }

    @Test
    fun parseOpeningStatus_dayOfWeekFilter_respectsOpeningDays() {
        val hours = "Mo-Fr 09:00-18:00"

        // Saturday 12:00 -> CLOSED_NOW
        val calSat = createCalendar(12, 0, Calendar.SATURDAY)
        assertEquals(OpeningStatus.CLOSED_NOW, OpeningHoursParser.parseOpeningStatus(hours, calSat))
    }

    @Test
    fun parseOpeningStatus_unparseableString_returnsNullWithoutCrashing() {
        val garbage1 = "GARBAGE_UNPARSEABLE_STRING_123!!!"
        val result1 = OpeningHoursParser.parseOpeningStatus(garbage1)
        assertNull("Unparseable opening hours must return null without crashing", result1)

        val garbage2 = "Mo-Fr 99:99-88:88"
        val result2 = OpeningHoursParser.parseOpeningStatus(garbage2)
        assertNull("Invalid time boundaries must return null without crashing", result2)

        assertNull("Null opening hours string must return null", OpeningHoursParser.parseOpeningStatus(null))
        assertNull("Blank opening hours string must return null", OpeningHoursParser.parseOpeningStatus("   "))
    }
}
