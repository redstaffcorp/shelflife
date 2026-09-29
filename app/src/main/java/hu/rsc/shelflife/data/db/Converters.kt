package hu.rsc.shelflife.data.db

import androidx.room.TypeConverter
import java.time.LocalDate

/** LocalDate <-> epoch-nap (Long), idozona-fuggetlen es rendezheto. */
class Converters {
    @TypeConverter
    fun fromEpochDay(value: Long?): LocalDate? = value?.let { LocalDate.ofEpochDay(it) }

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
}
