package com.example.util

import java.util.Calendar

enum class OpeningStatus {
    OPEN_NOW,
    CLOSED_NOW
}

/**
 * Parser for OpenStreetMap opening_hours strings (e.g. "24/7", "Mo-Su 09:00-18:00", "08:00-20:00").
 * Compatible with minSdk 24 (using Calendar).
 * Handles boundary times and safely returns null on unparseable input without crashing.
 */
object OpeningHoursParser {

    /**
     * Evaluates whether a spot is open now or closed now based on device local time.
     * Returns null if opening hours are unknown or unparseable.
     */
    fun parseOpeningStatus(
        rawHours: String?,
        nowCalendar: Calendar = Calendar.getInstance()
    ): OpeningStatus? {
        if (rawHours.isNullOrBlank()) return null
        val trimmed = rawHours.trim()

        if (trimmed.equals("24/7", ignoreCase = true)) {
            return OpeningStatus.OPEN_NOW
        }

        return try {
            val currentDayOfWeek = nowCalendar.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday, ...
            val currentMinutes = nowCalendar.get(Calendar.HOUR_OF_DAY) * 60 + nowCalendar.get(Calendar.MINUTE)

            // Split rules by semicolon
            val rules = trimmed.split(";").map { it.trim() }
            var foundAnyRule = false

            for (rule in rules) {
                if (rule.isBlank()) continue
                val timeRanges = extractTimeRanges(rule)
                if (timeRanges.isEmpty()) continue
                foundAnyRule = true

                if (!checkRuleDayOfWeek(rule, currentDayOfWeek)) continue

                for ((startMin, endMin) in timeRanges) {
                    val isOpen = if (endMin < startMin) {
                        // Overnight shift (e.g. 22:00-02:00)
                        currentMinutes >= startMin || currentMinutes < endMin
                    } else {
                        // Regular day shift (e.g. 09:00-18:00) - boundary inclusive start, exclusive end
                        currentMinutes in startMin until endMin
                    }

                    if (isOpen) {
                        return OpeningStatus.OPEN_NOW
                    }
                }
                return OpeningStatus.CLOSED_NOW
            }

            if (foundAnyRule) {
                OpeningStatus.CLOSED_NOW
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun checkRuleDayOfWeek(rule: String, currentDayOfWeek: Int): Boolean {
        // Convert Calendar day (1=Sun, 2=Mon...7=Sat) to Mon=1..Sun=7
        val monBasedDay = if (currentDayOfWeek == Calendar.SUNDAY) 7 else currentDayOfWeek - 1

        val timeMatch = Regex("\\d{1,2}:\\d{2}").find(rule)
        val daysPart = if (timeMatch != null) {
            rule.substring(0, timeMatch.range.first).trim()
        } else ""

        if (daysPart.isBlank()) return true

        val dayLower = daysPart.lowercase()
        if (dayLower.contains("mo-su") || dayLower.contains("daily")) return true

        val dayMap = mapOf(
            "mo" to 1, "tu" to 2, "we" to 3, "th" to 4, "fr" to 5, "sa" to 6, "su" to 7
        )

        if (daysPart.contains("-")) {
            val parts = daysPart.split("-").map { it.trim().lowercase() }
            if (parts.size == 2 && dayMap.containsKey(parts[0]) && dayMap.containsKey(parts[1])) {
                val start = dayMap[parts[0]]!!
                val end = dayMap[parts[1]]!!
                return if (start <= end) {
                    monBasedDay in start..end
                } else {
                    monBasedDay >= start || monBasedDay <= end
                }
            }
        }

        val listedDays = daysPart.split(",").map { it.trim().lowercase() }
        for (day in listedDays) {
            if (dayMap[day] == monBasedDay) return true
        }

        return false
    }

    private fun extractTimeRanges(rule: String): List<Pair<Int, Int>> {
        val ranges = mutableListOf<Pair<Int, Int>>()
        val timePattern = Regex("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})")
        val matches = timePattern.findAll(rule)

        for (match in matches) {
            val startH = match.groupValues[1].toIntOrNull() ?: continue
            val startM = match.groupValues[2].toIntOrNull() ?: continue
            val endH = match.groupValues[3].toIntOrNull() ?: continue
            val endM = match.groupValues[4].toIntOrNull() ?: continue

            if (startH !in 0..23 || endH !in 0..23 || startM !in 0..59 || endM !in 0..59) {
                continue
            }

            val startTotal = startH * 60 + startM
            val endTotal = endH * 60 + endM
            ranges.add(Pair(startTotal, endTotal))
        }

        return ranges
    }
}
