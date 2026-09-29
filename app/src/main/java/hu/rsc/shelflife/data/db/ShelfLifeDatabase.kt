package hu.rsc.shelflife.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import hu.rsc.shelflife.data.BarcodeProduct
import hu.rsc.shelflife.data.KnownProductEntity
import hu.rsc.shelflife.data.PantryItem

/**
 * Az app helyi (felho nelkuli) adatbazisa.
 *
 * FONTOS: sema-valtozasnal a `version` emelese mellett MINDIG irj
 * Migration-t (es teszteld az app/schemas ala exportalt semakkal).
 * Szandekosan nincs fallbackToDestructiveMigration: egy frissites soha
 * ne torolje csendben a felhasznalo kamrajat.
 */
@Database(
    entities = [PantryItem::class, BarcodeProduct::class, KnownProductEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // v2: location, status, finishedAt, snoozedUntil oszlopok (csak hozzaadas).
        AutoMigration(from = 1, to = 2)
    ]
)
@TypeConverters(Converters::class)
abstract class ShelfLifeDatabase : RoomDatabase() {

    abstract fun pantryItemDao(): PantryItemDao
    abstract fun productDao(): ProductDao

    companion object {
        private const val DB_NAME = "shelflife.db"

        @Volatile
        private var instance: ShelfLifeDatabase? = null

        fun get(context: Context): ShelfLifeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShelfLifeDatabase::class.java,
                    DB_NAME
                ).build().also { instance = it }
            }
    }
}
