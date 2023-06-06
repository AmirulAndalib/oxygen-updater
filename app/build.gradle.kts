import java.util.Properties

plugins {
    id(BuildPlugins.ANDROID_APPLICATION)
    id(BuildPlugins.GOOGLE_SERVICES)
    id(BuildPlugins.FIREBASE_CRASHLYTICS)
    kotlin("android")
    kotlin("kapt")
}

fun loadProperties(
    name: String,
    vararg defaults: Pair<String, String>,
) = Properties().apply {
    val logger = logger
    val file = rootProject.file("$name.properties")
    if (!file.exists()) {
        logger.warn("Warning: File '$name.properties' doesn't exist. Creating it with default values.")
        defaults.forEach { setProperty(it.first, it.second) }
        store(file.outputStream(), "Autogenerated by app/build.gradle.kts")
    }

    try {
        file.inputStream().use { load(it) }
    } catch (e: Exception) {
        logger.warn("Warning: Couldn't read properties from $name.properties. $e.")
    }
}

fun arrayForBuildConfig(vararg array: String) = array.joinToString(prefix = "{", postfix = "}") {
    "\"$it\""
}

android {
    namespace = "com.oxygenupdater"

    compileSdk = AndroidSdk.COMPILE
    buildToolsVersion = AndroidSdk.BUILD_TOOLS

    defaultConfig {
        applicationId = "com.arjanvlek.oxygenupdater"

        minSdk = AndroidSdk.MIN
        targetSdk = AndroidSdk.TARGET

        versionCode = 96
        versionName = "5.10.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                // Add Room-specific arguments
                // https://developer.android.com/jetpack/androidx/releases/room#compiler-options
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas",
                    "room.incremental" to "true",
                    "room.expandProjection" to "true"
                )
            }
        }

        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
    }

    bundle {
        language {
            // Because the app has an in-app language switch feature, we need
            // to disable splitting configuration APKs for language resources.
            // This ensures that the app won't crash if the user selects a
            // language that isn't in their device language list.
            // This'll obviously increase APK size significantly.
            enableSplit = false
        }
    }

    packaging {
        resources.excludes.addAll(
            arrayOf(
                "META-INF/NOTICE.txt",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
            )
        )
    }

    signingConfigs {
        val keystore = loadProperties(
            "keystore",
            Pair("storePassword", ""),
            Pair("keyPassword", ""),
            Pair("keyAlias", ""),
            Pair("storeFile", "keyStore.jks")
        )

        create("release") {
            keyAlias = keystore["keyAlias"] as String
            keyPassword = keystore["keyPassword"] as String
            storeFile = file(keystore["storeFile"] as String)
            storePassword = keystore["storePassword"] as String
        }
    }

    buildTypes {
        // Config for releases and testing on a real device
        // Uses the production server, and reads system properties using the OnePlus/OxygenOS specific build.prop values
        getByName("release") {
            buildConfigField("String", "SERVER_DOMAIN", "\"https://oxygenupdater.com/\"")
            buildConfigField("String", "SERVER_API_BASE", "\"api/v2.7/\"")
            buildConfigField("String", "NOTIFICATIONS_PREFIX", "\"\"")
            buildConfigField(
                "String[]",
                "DEVICE_NAME_LOOKUP_KEYS",
                arrayForBuildConfig(
                    "ro.display.series",
                    "ro.build.product",
                )
            )
            buildConfigField(
                "String[]",
                "OS_VERSION_NUMBER_LOOKUP_KEYS",
                arrayForBuildConfig(
                    "ro.rom.version",
                    "ro.oxygen.version",
                    "ro.build.ota.versionname",
                    "ro.vendor.oplus.exp.version",
                    "ro.build.display.ota",
                )
            )
            buildConfigField("String", "OS_OTA_VERSION_NUMBER_LOOKUP_KEY", "\"ro.build.version.ota\"")
            // Latter one is only used on very old OOS versions

            signingConfig = signingConfigs.getByName("release")

            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
        }
        // Config for use during debugging and testing on an emulator
        // Uses the test server, and reads system properties using the default build.prop values present on any Android device/emulator
        getByName("debug") {
            buildConfigField("String", "SERVER_DOMAIN", "\"https://test.oxygenupdater.com/\"")
            buildConfigField("String", "SERVER_API_BASE", "\"api/v2.7/\"")
            buildConfigField("String", "NOTIFICATIONS_PREFIX", "\"test_\"")
            buildConfigField(
                "String[]",
                "DEVICE_NAME_LOOKUP_KEYS",
                arrayForBuildConfig("ro.product.name")
            )
            buildConfigField(
                "String[]",
                "OS_VERSION_NUMBER_LOOKUP_KEYS",
                arrayForBuildConfig("ro.build.version.release")
            )
            buildConfigField("String", "OS_OTA_VERSION_NUMBER_LOOKUP_KEY", "\"ro.build.version.incremental\"")

            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = true

            // Should be this: https://github.com/firebase/firebase-android-sdk/issues/2665#issuecomment-849897741,
            // but that doesn't work for some reason.
            withGroovyBuilder {
                "firebaseCrashlytics" {
                    "mappingFileUploadEnabled"(false)
                }
            }
        }

        val languages = fileTree("src/main/res") {
            include("values-*/strings.xml")
        }.files.map { file: File ->
            file.parentFile.name.replace(
                "values-",
                ""
            )
        }.joinToString { str ->
            "\"$str\""
        }

        val billing = loadProperties(
            "billing",
            Pair("base64PublicKey", "")
        )

        // to distinguish in app drawer and allow multiple builds to exist in parallel on the same device
        buildTypes.forEach {
            it.buildConfigField("String", "AD_BANNER_MAIN_ID", "\"ca-app-pub-1816831161514116/9792024147\"")
            it.buildConfigField("String", "AD_BANNER_NEWS_ID", "\"ca-app-pub-1816831161514116/5072283884\"")
            it.buildConfigField("String", "AD_INTERSTITIAL_NEWS_ID", "\"ca-app-pub-1816831161514116/2367225965\"")
            it.buildConfigField("String", "BASE64_PUBLIC_KEY", "\"${billing["base64PublicKey"]}\"")
            it.buildConfigField("String[]", "SUPPORTED_LANGUAGES", "{\"en\", $languages}")

            if (it.name != "release") {
                it.versionNameSuffix = "-${it.name}"
                it.applicationIdSuffix = ".${it.name}"
                it.resValue("string", "app_name", "Oxygen Updater (${it.name})")
            } else {
                it.resValue("string", "app_name", "Oxygen Updater")
            }

            it.addManifestPlaceholders(
                mapOf(
                    "hostName" to "${if (it.name != "release") "test." else ""}oxygenupdater.com",
                    "advertisingAppId" to "ca-app-pub-1816831161514116~4275332954",
                    "shortcutXml" to "@xml/shortcuts_${it.name.lowercase()}",
                )
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    buildFeatures {
        viewBinding = true
    }

    testBuildType = "debug"
}

buildscript {
    repositories {
        mavenCentral()
    }
}

repositories {
    mavenCentral()
    maven("https://jitpack.io") // for com.github.topjohnwu.libsu
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    coreLibraryDesugaring(Libraries.ANDROID_TOOLS_DESUGAR)

    implementation(Libraries.KOTLIN_COROUTINES_CORE)
    implementation(Libraries.KOTLIN_COROUTINES_ANDROID)

    implementation(AndroidXLibraries.ANNOTATION)
    implementation(AndroidXLibraries.APP_COMPAT)
    implementation(AndroidXLibraries.BROWSER)
    implementation(AndroidXLibraries.CONSTRAINT_LAYOUT)
    implementation(AndroidXLibraries.SWIPE_REFRESH_LAYOUT)
    implementation(AndroidXLibraries.RECYCLER_VIEW)
    implementation(AndroidXLibraries.ROOM_KTX)
    implementation(AndroidXLibraries.ROOM_RUNTIME)
    kapt(AndroidXLibraries.ROOM_COMPILER)

    implementation(AndroidXLibraries.KTX_CORE)
    implementation(AndroidXLibraries.KTX_FRAGMENT)
    implementation(AndroidXLibraries.KTX_LIFECYCLE_LIVEDATA)
    implementation(AndroidXLibraries.KTX_LIFECYCLE_VIEWMODEL)
    implementation(AndroidXLibraries.KTX_PREFERENCE)
    implementation(AndroidXLibraries.KTX_WORK)

    implementation(Libraries.MATERIAL)

    implementation(platform(Libraries.FIREBASE_BOM))
    implementation(Libraries.FIREBASE_ANALYTICS_KTX)
    implementation(Libraries.FIREBASE_CRASHLYTICS_KTX)
    implementation(Libraries.FIREBASE_MESSAGING_KTX)

    implementation(Libraries.GOOGLE_PLAY_BILLING)
    implementation(Libraries.GOOGLE_PLAY_BILLING_KTX)
    implementation(Libraries.PLAY_CORE_IN_APP_UPDATES)
    implementation(Libraries.PLAY_SERVICES_BASE)
    implementation(Libraries.PLAY_SERVICES_ADS)

    implementation(Libraries.KOIN)

    implementation(Libraries.OKHTTP_LOGGING_INTERCEPTOR)
    implementation(Libraries.RETROFIT)
    implementation(Libraries.RETROFIT_CONVERTER_JACKSON)

    implementation(Libraries.JACKSON_KOTLIN_MODULE)

    implementation(Libraries.COIL)

    implementation(Libraries.FACEBOOK_SHIMMER)

    implementation(Libraries.LIBSU_CORE)
    implementation(Libraries.LIBSU_NIO)
    implementation(Libraries.LIBSU_SERVICE)

    testImplementation(TestLibraries.JUNIT4)
    testImplementation(TestLibraries.KOTLIN_TEST_JUNIT)
    testImplementation(TestLibraries.KOIN_TEST)

    androidTestImplementation(TestLibraries.ESPRESSO_CORE)
    androidTestImplementation(TestLibraries.JUNIT_EXT)
    androidTestImplementation(TestLibraries.RULES)
    androidTestImplementation(TestLibraries.RUNNER)
}
