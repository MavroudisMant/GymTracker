package com.mavros.gymtracker.data

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun targetAreaToName(area: TargetArea?): String? = area?.name

    @TypeConverter
    fun nameToTargetArea(value: String?): TargetArea? = value?.let(TargetArea::valueOf)
}
