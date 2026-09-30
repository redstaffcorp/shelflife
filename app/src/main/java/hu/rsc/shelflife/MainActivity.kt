package hu.rsc.shelflife

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import hu.rsc.shelflife.data.NotificationSettingsStore
import hu.rsc.shelflife.data.OnboardingStore
import hu.rsc.shelflife.notify.NotificationHelper
import hu.rsc.shelflife.notify.ReminderScheduler
import hu.rsc.shelflife.ui.PantryScreen
import hu.rsc.shelflife.ui.onboarding.OnboardingScreen
import hu.rsc.shelflife.ui.stats.StatsScreen
import hu.rsc.shelflife.ui.theme.ShelfLifeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A lejarati ertesitesekhez a csatornat mindig letre kell hozni
        // (API 26+ kotelezo), es ha a felhasznalo korabban mar bekapcsolta
        // az emlekeztetot, minden indulaskor ujra-ellenorizzuk/utemezzuk a
        // WorkManager periodikus feladatat -- olcso, idempotens hivas,
        // biztositja, hogy egy ujratelepites vagy WorkManager-reset utan is
        // fusson tovabb.
        NotificationHelper.ensureChannel(this)
        if (NotificationSettingsStore(this).enabled) {
            ReminderScheduler.schedule(this)
        }

        setContent {
            ShelfLifeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    val onboardingStore = remember { OnboardingStore(context) }
                    // Elso inditaskor bemutato (a kamera-engedely kerese elott),
                    // kesobb a menubol ujra megnyithato.
                    var showOnboarding by rememberSaveable { mutableStateOf(!onboardingStore.completed) }
                    var showStats by rememberSaveable { mutableStateOf(false) }
                    if (showOnboarding) {
                        OnboardingScreen(onFinish = {
                            onboardingStore.completed = true
                            showOnboarding = false
                        })
                    } else {
                        CameraPermissionGate {
                            if (showStats) {
                                StatsScreen(onBack = { showStats = false })
                            } else {
                                PantryScreen(
                                    onShowOnboarding = { showOnboarding = true },
                                    onShowStats = { showStats = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A kamera-engedelyt kerjuk be. A kamera / OCR / lejarati datum
 * felismeres tovabbra is 100%-ban a telefonon, halozat nelkul tortenik.
 * Az app csak az opcionalis, ingyenes Open Food Facts termeknev-
 * lekerdezeshez hasznal internetet, es annak hianyaban is tokeletesen
 * mukodik (kezi termeknev-bevitel mindig elerheto). A lejarati
 * ertesitesekhez szukseges kulon engedelykeres az ertesitesi
 * beallitasok kozott, kontextusban tortenik (lasd
 * NotificationSettingsDialog), nem itt az app indulasakor.
 */
@Composable
private fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    if (hasPermission) {
        content()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.camera_permission_rationale))
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text(stringResource(R.string.camera_permission_grant))
            }
        }
    }
}
