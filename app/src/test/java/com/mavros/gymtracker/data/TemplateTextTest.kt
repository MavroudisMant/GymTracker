package com.mavros.gymtracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateTextTest {
    private fun parse(text: String): TemplateDraft =
        requireNotNull(TemplateText.parse(text).draft) { "expected a draft" }

    @Test
    fun parsesTheDocumentedFormat() {
        val draft = parse(
            """
            Template: Upper / Lower
            Day: Upper
            Section: Warm up (skip)
            Band Pull Apart 2x15 [shoulders]
            Section: Main
            A: Bench Press 4x8 [chest]
            A: Barbell Row 4x8 [back]
            Plank 3x45s [core]
            Day: Lower
            Squat 4x6 [legs]
            """.trimIndent(),
        )
        assertEquals("Upper / Lower", draft.name)
        assertEquals(listOf("Upper", "Lower"), draft.days.map { it.name })

        val upper = draft.days[0]
        assertEquals(listOf("Warm up", "Main"), upper.sections.map { it.name })
        assertEquals(false, upper.sections[0].includeInWorkout)
        assertEquals(true, upper.sections[1].includeInWorkout)

        val bench = upper.sections[1].exercises[0]
        assertEquals(ExerciseDraft("Bench Press", "A", 4, 8, false, TargetArea.CHEST), bench)
        val plank = upper.sections[1].exercises[2]
        assertEquals(ExerciseDraft("Plank", null, 3, 45, true, TargetArea.CORE), plank)

        // Exercises before any section header go in the unsectioned group.
        assertEquals(listOf(null), draft.days[1].sections.map { it.name })
        assertEquals("Squat", draft.days[1].sections[0].exercises.single().name)
    }

    @Test
    fun toleratesChatbotMarkdown() {
        val draft = parse(
            """
            Here's your plan:
            ## **Day 1 - Push**
            Warm-up:
            - Arm Circles 2x20
            Main:
            1. Bench Press 3 x 8-12
            2. Overhead Press 3 sets of 10
            * Dips 3 sets
            • Wall Sit 60s
            """.trimIndent(),
        )
        // "Here's your plan:" ends with a colon, so it's read as a section before Day 1; it holds
        // no exercises and lands in an implicit first day, which has none either.
        val push = draft.days.single { it.name == "Push" }
        assertEquals(listOf("Warm-up", "Main"), push.sections.map { it.name })
        val main = push.sections[1].exercises
        assertEquals(ExerciseDraft("Bench Press", targetSets = 3, targetReps = 8), main[0])
        assertEquals(ExerciseDraft("Overhead Press", targetSets = 3, targetReps = 10), main[1])
        assertEquals(ExerciseDraft("Dips", targetSets = 3), main[2])
        assertEquals(ExerciseDraft("Wall Sit", targetReps = 60, isTimed = true), main[3])
    }

    @Test
    fun onlyReadsInsideCodeBlocksWhenThereAreAny() {
        val draft = parse(
            """
            Sure! Here's a plan that focuses on strength.
            ```text
            Template: Strength
            Day: A
            Squat 5x5
            ```
            This plan keeps sessions short. Rest 2 minutes between sets.
            """.trimIndent(),
        )
        assertEquals("Strength", draft.name)
        assertEquals(listOf("Squat"), draft.allExercises.map { it.name })
    }

    @Test
    fun exercisesWithoutAnyDayGoInOneUnnamedDay() {
        val draft = parse("Squat 3x5\nDeadlift 1x5")
        assertEquals(TemplateText.DEFAULT_NAME, draft.name)
        assertNull(draft.days.single().name)
        assertEquals(2, draft.allExercises.size)
    }

    @Test
    fun bareNumberedDayHasNoName() {
        val draft = parse("Day 1\nSquat\nDay 2: Pull\nRow")
        assertEquals(listOf(null, "Pull"), draft.days.map { it.name })
    }

    @Test
    fun keepsNumbersThatArePartOfTheName() {
        val draft = parse("Curl 21s\nT-Bar Row 3x10")
        // "21s" reads as a timed target; that's the documented meaning of a trailing "s".
        assertEquals(ExerciseDraft("Curl", targetReps = 21, isTimed = true), draft.allExercises[0])
        assertEquals("T-Bar Row", draft.allExercises[1].name)
    }

    @Test
    fun unknownAreaIsAWarningNotAnError() {
        val result = TemplateText.parse("Hip Thrust 3x10 [glutes]")
        assertNotNull(result.draft)
        assertNull(result.draft!!.allExercises.single().targetArea)
        assertTrue(result.warnings.single().contains("glutes"))
    }

    @Test
    fun timedTagMarksTimed() {
        val draft = parse("Dead Hang [arms, timed]")
        assertEquals(ExerciseDraft("Dead Hang", isTimed = true, targetArea = TargetArea.ARMS), draft.allExercises.single())
    }

    @Test
    fun noExercisesIsAnError() {
        val result = TemplateText.parse("Template: Empty\nDay: One\n\n```")
        assertNull(result.draft)
        assertNotNull(result.error)
    }

    @Test
    fun formatThenParseRoundTrips() {
        val original = TemplateDraft(
            name = "Plan",
            days = listOf(
                DayDraft(
                    name = null,
                    sections = listOf(
                        SectionDraft(null, true, listOf(ExerciseDraft("Squat", targetSets = 5, targetReps = 5))),
                        SectionDraft(
                            "Finisher",
                            false,
                            listOf(
                                ExerciseDraft("Plank", "A", 3, 45, isTimed = true, targetArea = TargetArea.CORE),
                                ExerciseDraft("Dips", "A", targetSets = 3),
                                ExerciseDraft("Push ups", targetReps = 20),
                                ExerciseDraft("Biceps Curl (έξω στροφή)"),
                            ),
                        ),
                    ),
                ),
                DayDraft(name = "Pull", sections = listOf(SectionDraft(null, true, listOf(ExerciseDraft("Row"))))),
            ),
        )
        assertEquals(original, parse(TemplateText.format(original)))
    }

    @Test
    fun aiPromptListsExactNamesAndTags() {
        val prompt = TemplateText.aiPrompt(
            listOf(
                Exercise.create("Culf Raises", TargetArea.LEGS),
                Exercise.create("Wall Sit", TargetArea.LEGS, isTimed = true),
                Exercise.create("Mobility"),
            ),
        )
        assertTrue(prompt.contains("- Culf Raises [legs]"))
        assertTrue(prompt.contains("- Wall Sit [legs, timed]"))
        assertTrue(prompt.contains("- Mobility\n"))
    }

    // --- matching ---

    private val existing = listOf(
        Exercise.create("Culf Raises").copy(id = 1),
        Exercise.create("Bench Press").copy(id = 2),
        Exercise.create("Push ups").copy(id = 3),
        Exercise.create("Row").copy(id = 4),
    )

    @Test
    fun exactMatchIgnoresCaseAndSpaces() {
        assertEquals(ExerciseMatch.Exact(existing[1]), ExerciseMatcher.match("  bench PRESS ", existing))
    }

    @Test
    fun similarCatchesTyposPluralsAndPunctuation() {
        assertEquals(ExerciseMatch.Similar(existing[0]), ExerciseMatcher.match("Calf Raises", existing))
        assertEquals(ExerciseMatch.Similar(existing[0]), ExerciseMatcher.match("Culf Raise", existing))
        assertEquals(ExerciseMatch.Similar(existing[2]), ExerciseMatcher.match("Push-Ups", existing))
        assertEquals(ExerciseMatch.Similar(existing[2]), ExerciseMatcher.match("Pushups", existing))
    }

    @Test
    fun differentExercisesDontMatch() {
        assertEquals(ExerciseMatch.None, ExerciseMatcher.match("Incline Bench Press", existing))
        assertEquals(ExerciseMatch.None, ExerciseMatcher.match("Squat", existing))
        // Short names need an exact (loose) match: "Rows" is "Row", "Dow" isn't.
        assertEquals(ExerciseMatch.Similar(existing[3]), ExerciseMatcher.match("Rows", existing))
        assertEquals(ExerciseMatch.None, ExerciseMatcher.match("Dow", existing))
    }
}
