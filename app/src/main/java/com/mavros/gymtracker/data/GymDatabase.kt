package com.mavros.gymtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Exercise::class,
        Template::class,
        TemplateDay::class,
        TemplateExercise::class,
        TemplateSection::class,
        WorkoutSession::class,
        SessionExercise::class,
        SetEntry::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class GymDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun templateDao(): TemplateDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val DB_NAME = "gym-tracker.db"

        @Volatile
        private var instance: GymDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Exercise ADD COLUMN isTimed INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE TemplateExercise ADD COLUMN targetSets INTEGER")
                db.execSQL("ALTER TABLE TemplateExercise ADD COLUMN targetReps INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `TemplateSection` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`templateDayId` INTEGER NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`templateDayId`) REFERENCES `TemplateDay`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_TemplateSection_templateDayId` ON `TemplateSection` (`templateDayId`)")
                db.execSQL("ALTER TABLE TemplateExercise ADD COLUMN sectionId INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE TemplateSection ADD COLUMN includeInWorkout INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE SessionExercise ADD COLUMN sectionName TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE `SetEntry_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionExerciseId` INTEGER NOT NULL, `setNumber` INTEGER NOT NULL, `weight` REAL, " +
                        "`reps` INTEGER NOT NULL, FOREIGN KEY(`sessionExerciseId`) REFERENCES `SessionExercise`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "INSERT INTO `SetEntry_new` (`id`, `sessionExerciseId`, `setNumber`, `weight`, `reps`) " +
                        "SELECT `id`, `sessionExerciseId`, `setNumber`, `weight`, `reps` FROM `SetEntry`",
                )
                db.execSQL("DROP TABLE `SetEntry`")
                db.execSQL("ALTER TABLE `SetEntry_new` RENAME TO `SetEntry`")
                db.execSQL("CREATE INDEX `index_SetEntry_sessionExerciseId` ON `SetEntry` (`sessionExerciseId`)")
            }
        }

        fun getInstance(context: Context): GymDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, GymDatabase::class.java, DB_NAME)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
    }
}
