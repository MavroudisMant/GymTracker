package com.mavros.gymtracker.data

import java.util.Locale

/*
 * Plain-text template format, used for import, export ("share") and the AI prompt.
 *
 *   Template: Upper / Lower
 *   Day: Upper
 *   Section: Warm up (skip)
 *   Band Pull Apart 2x15 [shoulders]
 *   Section: Main
 *   A: Bench Press 4x8 [chest]
 *   A: Barbell Row 4x8 [back]
 *   Plank 3x45s [core]
 *
 * The parser is forgiving about what chatbots tend to add: markdown bullets, numbering, bold,
 * code fences, "Day 1 - Legs" style headers and "Warm up:" lines as section headers.
 */

data class TemplateDraft(
    val name: String,
    val days: List<DayDraft>,
) {
    val allExercises: List<ExerciseDraft> get() = days.flatMap { day -> day.sections.flatMap { it.exercises } }
}

data class DayDraft(
    /** Null when the day has no name (shown as "Day N"). */
    val name: String?,
    /** The first entry holds exercises that come before any section header (name = null). */
    val sections: List<SectionDraft>,
)

data class SectionDraft(
    /** Null for exercises outside any section. */
    val name: String?,
    val includeInWorkout: Boolean = true,
    val exercises: List<ExerciseDraft>,
)

data class ExerciseDraft(
    val name: String,
    val supersetGroup: String? = null,
    val targetSets: Int? = null,
    val targetReps: Int? = null,
    /** Reps were written as seconds ("45s"); only used when the exercise is created. */
    val isTimed: Boolean = false,
    /** Only used when the exercise is created. */
    val targetArea: TargetArea? = null,
)

data class ParseResult(
    val draft: TemplateDraft?,
    /** Things that were ignored; the import can still go ahead. */
    val warnings: List<String>,
    /** Why nothing could be imported; null when [draft] is usable. */
    val error: String?,
)

object TemplateText {
    const val DEFAULT_NAME = "Imported template"

    private val templateHeader = Regex("""^(?:template|routine|program|programme|plan)\s*:\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val dayHeaderNumbered = Regex("""^day\s*(\d+)\s*(?:[:\-–—.)]\s*)?(.*)$""", RegexOption.IGNORE_CASE)
    private val dayHeaderNamed = Regex("""^day\s*[:\-–—]\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val sectionHeader = Regex("""^section\s*:\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val skipMarker = Regex("""\s*[(\[]\s*(?:skip|skipped|not in workout)\s*[)\]]\s*$""", RegexOption.IGNORE_CASE)
    private val supersetPrefix = Regex("""^([A-Za-z])\d{0,2}\s*[:.)]\s+(.+)$""")
    private val areaTag = Regex("""\s*\[([^\]]*)]\s*$""")
    // "3x10", "3 x 8-12", "3×45s", "3 sets of 10", "3 sets x 10 reps"; a range keeps its low end.
    private val setsByReps = Regex(
        """\s+(\d{1,3})\s*(?:sets?\s*(?:of|x|×)?|[x×*])\s*(\d{1,4})(?:\s*[-–]\s*\d{1,4})?\s*(s|sec|secs|seconds|reps?)?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val setsOnly = Regex("""\s+(\d{1,3})\s*sets?\s*$""", RegexOption.IGNORE_CASE)
    private val repsOnly = Regex("""\s+(\d{1,4})(?:\s*[-–]\s*\d{1,4})?\s*(s|sec|secs|seconds|reps?)\s*$""", RegexOption.IGNORE_CASE)
    private val leadingMarkup = Regex("""^(?:[#>*•+\-]+\s*|\d{1,3}[.)]\s+)+""")

    fun parse(text: String): ParseResult {
        val warnings = mutableListOf<String>()
        var templateName: String? = null
        val days = mutableListOf<DayBuilder>()
        fun currentDay(): DayBuilder = days.lastOrNull() ?: DayBuilder(null).also { days += it }

        templateLines(text).forEach { (index, raw) ->
            val lineNumber = index + 1
            val line = clean(raw) ?: return@forEach

            templateHeader.matchEntire(line)?.let { match ->
                templateName = match.groupValues[1].trim().ifEmpty { null }
                return@forEach
            }
            dayHeader(line)?.let { header ->
                days += DayBuilder(header.name)
                return@forEach
            }
            sectionName(line)?.let { header ->
                val skip = skipMarker.containsMatchIn(header)
                val name = header.replace(skipMarker, "").trim().ifEmpty { "Section" }
                currentDay().sections += SectionBuilder(name, includeInWorkout = !skip)
                return@forEach
            }
            parseExercise(line, lineNumber, warnings)?.let { currentDay().currentSection().exercises += it }
        }

        val draft = TemplateDraft(
            name = templateName ?: DEFAULT_NAME,
            days = days.map { it.build() },
        )
        return when {
            draft.allExercises.isEmpty() -> ParseResult(
                null,
                warnings,
                "No exercises found. Each exercise goes on its own line, e.g. “Bench Press 3x10”.",
            )
            else -> ParseResult(draft, warnings, null)
        }
    }

    /** The template as text in the import format; parsing it gives the same template back. */
    fun format(draft: TemplateDraft): String = buildString {
        append("Template: ").append(draft.name).append('\n')
        draft.days.forEachIndexed { index, day ->
            append('\n')
            // An unnamed day is written "Day N" (no colon), which parses back to an unnamed day.
            if (day.name != null) append("Day: ").append(day.name) else append("Day ").append(index + 1)
            append('\n')
            day.sections.forEach { section ->
                if (section.name != null) {
                    append("Section: ").append(section.name)
                    if (!section.includeInWorkout) append(" (skip)")
                    append('\n')
                }
                section.exercises.forEach { append(formatExercise(it)).append('\n') }
            }
        }
    }

    fun formatExercise(exercise: ExerciseDraft): String = buildString {
        exercise.supersetGroup?.let { append(it).append(": ") }
        append(exercise.name)
        val seconds = if (exercise.isTimed) "s" else ""
        when {
            exercise.targetSets != null && exercise.targetReps != null ->
                append(' ').append(exercise.targetSets).append('x').append(exercise.targetReps).append(seconds)
            exercise.targetSets != null -> append(' ').append(exercise.targetSets).append(" sets")
            exercise.targetReps != null -> append(' ').append(exercise.targetReps).append(if (exercise.isTimed) "s" else " reps")
        }
        exercise.targetArea?.let { append(" [").append(it.name.lowercase(Locale.ROOT)).append(']') }
    }

    /**
     * A prompt for a chatbot: the format, an example, and the user's exercises so it reuses their
     * exact names instead of inventing near-duplicates.
     */
    fun aiPrompt(exercises: List<Exercise>): String = buildString {
        appendLine("I use a gym tracker app that imports workout templates from plain text. Write the template I describe at the end in exactly the format below. Put it in one code block with nothing else inside the block.")
        appendLine()
        appendLine("FORMAT")
        appendLine("Template: <template name>")
        appendLine("Day: <day name>")
        appendLine("Section: <section name>          (optional grouping inside a day, e.g. Warm up, Main)")
        appendLine("Section: <section name> (skip)   (part of the plan, but left out of the logged workout)")
        appendLine("<exercise name> <sets>x<reps> [<area>]")
        appendLine("A: <exercise name> 3x10          (consecutive lines with the same letter form a superset)")
        appendLine("<exercise name> 3x45s            (seconds instead of reps, for timed exercises)")
        appendLine()
        appendLine("The target (3x10) and the [area] are optional. Areas: ${TargetArea.entries.joinToString(", ") { it.name.lowercase(Locale.ROOT) }}.")
        appendLine("One exercise per line. No bullets, numbering, notes or rest times.")
        appendLine()
        appendLine("EXAMPLE")
        appendLine("Template: Upper / Lower")
        appendLine("Day: Upper")
        appendLine("Section: Warm up (skip)")
        appendLine("Band Pull Apart 2x15 [shoulders]")
        appendLine("Section: Main")
        appendLine("A: Bench Press 4x8 [chest]")
        appendLine("A: Barbell Row 4x8 [back]")
        appendLine("Plank 3x45s [core]")
        appendLine("Day: Lower")
        appendLine("Squat 4x6 [legs]")
        appendLine()
        appendLine("MY EXERCISES")
        if (exercises.isEmpty()) {
            appendLine("I don't have any exercises yet, so use clear, common names.")
        } else {
            appendLine("If an exercise is one of these, copy its name exactly as written (same spelling, even if it looks like a typo). Only use a new name when none of these fits.")
            exercises.sortedBy { it.normalizedName }.forEach { exercise ->
                val tags = listOfNotNull(
                    exercise.targetArea?.name?.lowercase(Locale.ROOT),
                    "timed".takeIf { exercise.isTimed },
                )
                append("- ").append(exercise.name)
                if (tags.isNotEmpty()) append(" [").append(tags.joinToString(", ")).append(']')
                appendLine()
            }
        }
        appendLine()
        appendLine("WHAT I WANT")
        append("<describe the plan, e.g. 3 days a week, 45 minutes, full body, focus on legs>")
    }

    /**
     * The lines to read, with their 0-based line index. A chatbot reply wraps the template in a
     * ``` code block and chats around it; when there is one, only code-block lines count.
     */
    private fun templateLines(text: String): List<Pair<Int, String>> {
        val lines = text.lines()
        val fences = lines.indices.filter { lines[it].trim().startsWith("```") }
        if (fences.size < 2) return lines.withIndex().map { it.index to it.value }
        val inside = mutableListOf<Pair<Int, String>>()
        var inBlock = false
        lines.forEachIndexed { index, line ->
            if (line.trim().startsWith("```")) {
                inBlock = !inBlock
            } else if (inBlock) {
                inside += index to line
            }
        }
        return inside
    }

    /** Strips markdown/list decoration; null for lines to ignore (blank, comments, code fences). */
    private fun clean(raw: String): String? {
        var line = raw.trim()
        if (line.isEmpty() || line.startsWith("```") || line.startsWith("//")) return null
        line = line.replace("**", "").replace("__", "").replace("`", "")
        line = line.replace(leadingMarkup, "").trim()
        return line.ifEmpty { null }
    }

    /** Non-null if [line] is a day header; its name is null for a bare "Day 2". */
    private fun dayHeader(line: String): DayHeader? {
        dayHeaderNumbered.matchEntire(line)?.let { return DayHeader(it.groupValues[2].trim().ifEmpty { null }) }
        dayHeaderNamed.matchEntire(line)?.let { return DayHeader(it.groupValues[1].trim().ifEmpty { null }) }
        return null
    }

    private fun sectionName(line: String): String? {
        sectionHeader.matchEntire(line)?.let { return it.groupValues[1] }
        // "Warm up:" on its own line reads as a section header.
        if (line.endsWith(':') && line.length > 1) return line.dropLast(1)
        return null
    }

    private fun parseExercise(line: String, lineNumber: Int, warnings: MutableList<String>): ExerciseDraft? {
        var rest = line
        var group: String? = null
        supersetPrefix.matchEntire(rest)?.let {
            group = it.groupValues[1].uppercase(Locale.ROOT)
            rest = it.groupValues[2]
        }

        var area: TargetArea? = null
        var timed = false
        areaTag.find(rest)?.let { match ->
            val tags = match.groupValues[1].split(',').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
            timed = "timed" in tags
            area = tags.firstNotNullOfOrNull { tag -> TargetArea.entries.firstOrNull { it.name.lowercase(Locale.ROOT) == tag } }
            val unknown = tags.filter { tag -> tag != "timed" && TargetArea.entries.none { it.name.lowercase(Locale.ROOT) == tag } }
            if (unknown.isNotEmpty()) {
                warnings += "Line $lineNumber: “${unknown.joinToString()}” isn't an area I know, so it was ignored."
            }
            rest = rest.removeRange(match.range)
        }

        var sets: Int? = null
        var reps: Int? = null
        val padded = " " + rest.trim()
        val both = setsByReps.find(padded)
        val setsMatch = if (both == null) setsOnly.find(padded) else null
        val repsMatch = if (both == null && setsMatch == null) repsOnly.find(padded) else null
        when {
            both != null -> {
                sets = both.groupValues[1].toInt()
                reps = both.groupValues[2].toInt()
                timed = timed || both.groupValues[3].isSecondsUnit()
                rest = padded.removeRange(both.range)
            }
            setsMatch != null -> {
                sets = setsMatch.groupValues[1].toInt()
                rest = padded.removeRange(setsMatch.range)
            }
            repsMatch != null -> {
                reps = repsMatch.groupValues[1].toInt()
                timed = timed || repsMatch.groupValues[2].isSecondsUnit()
                rest = padded.removeRange(repsMatch.range)
            }
        }

        val name = rest.trim().trimEnd('-', '–', '—', ':', ',').trim()
        if (name.isEmpty()) {
            warnings += "Line $lineNumber: no exercise name, skipped."
            return null
        }
        return ExerciseDraft(
            name = name,
            supersetGroup = group,
            targetSets = sets?.takeIf { it > 0 },
            targetReps = reps?.takeIf { it > 0 },
            isTimed = timed,
            targetArea = area,
        )
    }

    private fun String.isSecondsUnit(): Boolean = lowercase(Locale.ROOT).startsWith("s")

    private class DayBuilder(val name: String?) {
        val sections = mutableListOf(SectionBuilder(null, includeInWorkout = true))
        fun currentSection(): SectionBuilder = sections.last()
        fun build(): DayDraft = DayDraft(
            name = name,
            // Keep the unsectioned group only if it has exercises (it must stay first).
            sections = sections.filterIndexed { i, s -> i > 0 || s.exercises.isNotEmpty() }.map { it.build() },
        )
    }

    private class SectionBuilder(val name: String?, val includeInWorkout: Boolean) {
        val exercises = mutableListOf<ExerciseDraft>()
        fun build() = SectionDraft(name, includeInWorkout, exercises.toList())
    }

    private class DayHeader(val name: String?)
}

/** How an imported exercise name relates to the user's existing exercises. */
sealed interface ExerciseMatch {
    /** Same name, ignoring case and surrounding spaces. */
    data class Exact(val exercise: Exercise) : ExerciseMatch

    /** Almost the same: a typo, plural, or punctuation/spacing difference. */
    data class Similar(val exercise: Exercise) : ExerciseMatch

    data object None : ExerciseMatch
}

object ExerciseMatcher {
    fun match(name: String, existing: List<Exercise>): ExerciseMatch {
        val normalized = normalizeExerciseName(name)
        existing.firstOrNull { it.normalizedName == normalized }?.let { return ExerciseMatch.Exact(it) }

        val key = looseKey(name)
        if (key.isEmpty()) return ExerciseMatch.None
        val allowed = when {
            key.length >= 10 -> 2
            key.length >= 5 -> 1
            else -> 0
        }
        val best = existing
            .map { it to levenshtein(key, looseKey(it.name), allowed) }
            .filter { (_, distance) -> distance <= allowed }
            .minByOrNull { (_, distance) -> distance }
        return best?.let { ExerciseMatch.Similar(it.first) } ?: ExerciseMatch.None
    }

    /** Lowercase letters and digits only, each word without a plural "s": "Calf-Raises" -> "calfraise". */
    internal fun looseKey(name: String): String =
        name.lowercase(Locale.ROOT)
            .split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.isNotEmpty() }
            .joinToString("") { word -> if (word.length > 3 && word.endsWith('s')) word.dropLast(1) else word }

    /** Edit distance, giving up early (returns limit + 1) once it must exceed [limit]. */
    internal fun levenshtein(a: String, b: String, limit: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                rowMin = minOf(rowMin, current[j])
            }
            if (rowMin > limit) return limit + 1
            previous = current
        }
        return previous[b.length]
    }
}
