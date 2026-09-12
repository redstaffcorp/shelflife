package hu.rsc.shelflife.ui.theme

import androidx.compose.ui.graphics.Color

// ShelfLife brand paletta: friss zold (elelmiszer / frissesseg), meleg
// krem/barna arnyalatok (kamra-polc erzet). Tudatosan visszafogott, hogy
// "vallalhato" (nem jatekos) hatast keltsen, de ne legyen unalmas.

// --- Vilagos tema ---
val GreenPrimaryLight = Color(0xFF2E6B4F)
val OnGreenPrimaryLight = Color(0xFFFFFFFF)
val GreenPrimaryContainerLight = Color(0xFFB6F0CE)
val OnGreenPrimaryContainerLight = Color(0xFF002114)

val TanSecondaryLight = Color(0xFF6F5B40)
val OnTanSecondaryLight = Color(0xFFFFFFFF)
val TanSecondaryContainerLight = Color(0xFFF6DFBB)
val OnTanSecondaryContainerLight = Color(0xFF261A04)

val AmberTertiaryLight = Color(0xFF8A5300)
val OnAmberTertiaryLight = Color(0xFFFFFFFF)
val AmberTertiaryContainerLight = Color(0xFFFFDDB3)
val OnAmberTertiaryContainerLight = Color(0xFF2B1700)

val BackgroundLight = Color(0xFFFBFAF3)
val OnBackgroundLight = Color(0xFF1A1C18)
val SurfaceLight = Color(0xFFFBFAF3)
val OnSurfaceLight = Color(0xFF1A1C18)
val SurfaceVariantLight = Color(0xFFE0E4D6)
val OnSurfaceVariantLight = Color(0xFF44483E)
val OutlineLight = Color(0xFF74796D)

// --- Sotet tema ---
val GreenPrimaryDark = Color(0xFF9BD4B3)
val OnGreenPrimaryDark = Color(0xFF00391F)
val GreenPrimaryContainerDark = Color(0xFF14513A)
val OnGreenPrimaryContainerDark = Color(0xFFB6F0CE)

val TanSecondaryDark = Color(0xFFDBC3A1)
val OnTanSecondaryDark = Color(0xFF3D2E15)
val TanSecondaryContainerDark = Color(0xFF554429)
val OnTanSecondaryContainerDark = Color(0xFFF6DFBB)

val AmberTertiaryDark = Color(0xFFFFB865)
val OnAmberTertiaryDark = Color(0xFF482900)
val AmberTertiaryContainerDark = Color(0xFF6B3D00)
val OnAmberTertiaryContainerDark = Color(0xFFFFDDB3)

val BackgroundDark = Color(0xFF11140F)
val OnBackgroundDark = Color(0xFFE2E3DA)
val SurfaceDark = Color(0xFF11140F)
val OnSurfaceDark = Color(0xFFE2E3DA)
val SurfaceVariantDark = Color(0xFF44483E)
val OnSurfaceVariantDark = Color(0xFFC4C8BA)
val OutlineDark = Color(0xFF8E9388)

// --- Lejarat-surgosseg jelzoszinek (mindket temaban hasznalva) ---
// Ezek szandekosan temafuggetlenek: egy szinkodolt sav/felirat allapotjelzo,
// nem szoveges kontraszt-kritikus felulet.
val ExpiryFresh = Color(0xFF2E6B4F)   // meg sok ideje van
val ExpirySoon = Color(0xFFB8790A)    // hamarosan lejar (pl. <= 3 nap)
val ExpiryOverdue = Color(0xFFB3261E) // mar lejart
