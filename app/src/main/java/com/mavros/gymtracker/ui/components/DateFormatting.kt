package com.mavros.gymtracker.ui.components

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val sessionDateFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

/** e.g. "Tue 25 Nov". */
fun LocalDate.toSessionLabel(): String = format(sessionDateFormatter)

/** Whole numbers without a trailing ".0" (80.0 -> "80"), others as-is (82.5 -> "82.5"). */
fun Double.formatWeight(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
