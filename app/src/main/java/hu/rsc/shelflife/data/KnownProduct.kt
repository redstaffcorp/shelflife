package hu.rsc.shelflife.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Nevalapu "ismert termek" (a legutobb hasznalt mertekegyseggel), hogy egy
 * korabban mar felvett termek a kezi bevitelnel is valaszthato legyen, es a
 * mertekegyseget se kelljen ujra megadni.
 */
data class KnownProduct(val name: String, val unit: String)

/** Vonalkod -> termeknev helyi cache: ismert vonalkodnal nincs halozati hivas. */
@Entity(tableName = "barcode_products")
data class BarcodeProduct(
    @PrimaryKey val barcode: String,
    val name: String
)

/**
 * Az ismert termekek tablaja. Kulcs: a nev kisbetus, trim-elt valtozata
 * (kis-/nagybetu-fuggetlen egyezeshez), `name`: az eredeti iras szerinti nev.
 */
@Entity(tableName = "known_products")
data class KnownProductEntity(
    @PrimaryKey val nameKey: String,
    val name: String,
    val unit: String
) {
    companion object {
        fun keyOf(name: String): String = name.trim().lowercase()
    }
}
