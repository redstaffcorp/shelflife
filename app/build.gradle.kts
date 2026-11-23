plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/** A Google hivatalos TESZT-reklamazonositoi (debug es staging build). */
fun com.android.build.api.dsl.VariantDimension.useTestAdIds() {
    manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
    buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
    buildConfigField("String", "ADMOB_NATIVE_ID", "\"ca-app-pub-3940256099942544/2247696110\"")
    buildConfigField("boolean", "USE_TEST_ADS", "true")
}

android {
    namespace = "hu.rsc.shelflife"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "hu.rsc.shelflife"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AdMob: alapertelmezesben (debug) a Google hivatalos TESZT azonositoi,
        // hogy fejlesztes kozben ne a sajat egysegeken legyen megjelenes/kattintas.
        // A sajat (eles) azonositok a release buildTypes-ban vannak.
        useTestAdIds()

        // Sajat tesztkeszulekek (hash-elt eszkozazonositok, vesszovel elvalasztva).
        // Ezeken az eszkozokon az ELES azonositokkal is csak teszt-reklam jon --
        // igy a Play-rol (belso teszt) telepitett release build sem general
        // ervenytelen forgalmat. NEM a repoban: a felhasznaloi
        // ~/.gradle/gradle.properties-ben (Windows: C:\Users\<nev>\.gradle\gradle.properties):
        //   shelflife.admobTestDeviceIds=33BE2250B43518CCDA7DE426D04EE231,...
        // A hash-t az SDK kiirja a logcatba ("setTestDeviceIds(Arrays.asList(...))").
        val testDeviceIds = providers.gradleProperty("shelflife.admobTestDeviceIds").getOrElse("")
        buildConfigField("String", "ADMOB_TEST_DEVICE_IDS", "\"$testDeviceIds\"")
    }

    buildTypes {
        release {
            // AdMob: SAJAT eles azonositok (csak release build -- ez megy a Play-re).
            manifestPlaceholders["admobAppId"] = "ca-app-pub-6858754562565967~9687495703"
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-6858754562565967/8486094672\"")
            buildConfigField("String", "ADMOB_NATIVE_ID", "\"ca-app-pub-6858754562565967/5859931332\"")
            buildConfigField("boolean", "USE_TEST_ADS", "false")

            // R8: kodzsugoritas, obfuszkalas es eroforras-szukites. Az
            // alapertelmezett Android keep-szabalyokat az uj DSL automatikusan
            // hozzaadja; a sajat szabalyok: src/main/keepRules/*.keep
            optimization {
                enable = true
            }
        }

        // "staging": PONT olyan, mint a release (R8, nem debuggable), de a
        // Google TESZT-reklamazonositoival es debug-alairassal. Sajat eszkozon
        // ezzel tesztelj release-szeru buildet: Build Variants -> staging, vagy
        //   gradlew installStaging
        // Kulon applicationId (.staging), igy a Play-rol telepitett eles
        // verzio mellett is fenn lehet. Play-re SOHA nem ez megy fel.
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            useTestAdIds()
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Room: a sema JSON-ba exportalva (app/schemas), hogy kesobbi
// verzioknal a migraciok tesztelhetok legyenek. Ezt a mappat commitold!
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Helyi adatbazis (a korabbi SharedPreferences+JSON helyett)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Pilot: kamera + on-device ML Kit -- 100% offline, nincs halozati hivas
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition)

    // Lejarati ertesitesek: WorkManager -- naponkenti hatterellenorzes,
    // NINCS foreground service, NINCS exact alarm.
    implementation(libs.androidx.work.runtime.ktx)

    // Reklam: AdMob (banner a lista aljan + nativ a statisztikan) es a
    // hozzajarulas-kezeles (UMP, EGT-ben kotelezo).
    implementation(libs.play.services.ads)
    implementation(libs.ump)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

