package hu.rsc.shelflife.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Egy kamraban/hutoben levo tetel. Room-tablaban tarolva (pantry_items),
 * hogy a lejarati ertesitesek akkor is mukodjenek, ha az app epp nincs
 * megnyitva. A LocalDate mezok epoch-napkent (Long) kerulnek az
 * adatbazisba (lasd db/Converters).
 */
@Entity(tableName = "pantry_items")
data class PantryItem(
    // A felvitel idopontja ms-ban (egyben a "Felvitel" szerinti rendezes kulcsa).
    @PrimaryKey val id: Long,
    // Null, ha a tetel teljesen kezi uton, vonalkod-szkenneles nelkul jott letre.
    val barcode: String?,
    val productName: String,
    val expiry: LocalDate,
    // Automatikusan toltott, amikor a tetel eloszor elmentesre kerul.
    val recordedAt: LocalDate,
    // Opcionalis, csak szam. Null = nincs megadva.
    val quantity: Int?,
    // Mennyisegi mertekegyseg (pl. "darab"). Ismert termeknel az utoljara
    // hasznalt mertekegyseg ujra felajanlodik (lasd KnownProduct).
    val quantityUnit: String,
    val enteredManually: Boolean,
    val ambiguousDayMonth: Boolean,
    // Igaz, ha a lejarat becsles ("~1 het", "Kesobb"). A listaban "~ becsult"
    // jelolest kap, es a "Becsult" szurovel kesobb pontosithato.
    val expiryEstimated: Boolean = false,
    // Melyik lejarati datumra kuldtunk mar ertesitest -- ha a felhasznalo
    // modositja a lejaratot, ez ervenyet veszti, es az app ujra kuldhet.
    val notifiedForExpiry: LocalDate? = null,
    // Hol tarolja a felhasznalo (null = nincs megadva). [v2]
    val location: StorageLocation? = null,
    // ACTIVE = a kamraban van; CONSUMED/WASTED = elfogyott / kidobtuk. A lezart
    // tetelek nem latszanak a listaban, de megmaradnak (kesobbi statisztika). [v2]
    @ColumnInfo(defaultValue = "ACTIVE")
    val status: ItemStatus = ItemStatus.ACTIVE,
    // Mikor lett lezarva (elfogyott/kidobva). [v2]
    val finishedAt: LocalDate? = null,
    // "Holnap szolj": eddig a napig nem kuldunk ra ertesitest. [v2]
    val snoozedUntil: LocalDate? = null
)

/** Tarolasi hely. Az enum neve kerul az adatbazisba -- atnevezni tilos! */
enum class StorageLocation { FRIDGE, FREEZER, PANTRY }

/** A tetel allapota. Az enum neve kerul az adatbazisba -- atnevezni tilos! */
enum class ItemStatus { ACTIVE, CONSUMED, WASTED }
