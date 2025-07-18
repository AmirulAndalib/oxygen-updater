package com.oxygenupdater.models

import android.os.Build
import android.os.Build.UNKNOWN
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.util.Log
import com.oxygenupdater.BuildConfig
import com.oxygenupdater.models.SystemVersionProperties.OplusPipelineLookupKeys
import com.oxygenupdater.utils.logInfo
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.util.Scanner

/**
 * Contains some properties of the OS / ROM installed on the device.
 *
 * Used to read / extract OPPO/OnePlus-specific properties from the ROM.
 */
object SystemVersionProperties {

    private const val TAG = "SystemVersionProperties"
    private const val SecurityPatchLookupKey = "ro.build.version.security_patch"

    /** Required for workaround #1 */
    private const val H2OS = "H2OS"

    /** Required for workaround #2 */
    private val OxygenOsPrefix = "Oxygen ?OS ?".toRegex()

    /** Required for workaround #1 */
    private const val RomVersionLookupKey = "ro.rom.version"

    /** Required for workaround #4 */
    private const val BuildSoftVersionLookupKey = "ro.build.soft.version"

    /** This includes both [Build.DISPLAY] and OnePlus SOTA info */
    private const val FullOsVersionLookupKey = "persist.sys.oplus.ota_ver_display"

    /** Required for workaround #3 */
    private const val OnePlus3 = "OnePlus3"

    /** Required for workaround #6 */
    private val OnePlusPadAndPad2 = arrayOf("OPD2203", "OPD2403")

    /** Required for workaround #5 */
    private val OnePlus7Series = arrayOf("OnePlus7", "OnePlus7Pro")

    /** Required for workaround #4 & #5 */
    private val OnePlus7TSeries = arrayOf("OnePlus7T", "OnePlus7TPro")

    /**
     * This isn't a workaround, but was introduced in the first Open Beta for 7T-series.
     * These keys will be checked on all devices, for better future-proofing.
     *
     * Note: this value used to be saved to shared prefs prior to v6 stable, but now it's in `isEuBuild`.
     */
    private val BuildEuLookupKeys = arrayOf("ro.build.eu", "ro.vendor.build.eu")

    /**
     * This is a workaround for Nord 2 (maybe future devices too), since it doesn't have any of the above EU keys.
     * This key will be checked *after* the above keys are checked, as a backup (if above keys aren't found).
     *
     * Note: to keep things simple, we're checking if the value corresponding to this key starts with `EU`,
     * even though we've seen that (at least on Nord 2), it's `EUEX` for EU devices, and `IN` for India.
     */
    private val OplusPipelineLookupKeys = arrayOf(
        "ro.oplus.pipeline_key",
        "ro.oplus.pipeline.region",
        "ro.vendor.oplus.regionmark",
    )

    /** Required for workaround #5*/
    private const val VendorOpIndia = "ro.vendor.op.india"

    /** Required for `osType` */
    private const val BuildOsTypeLookupKey = "ro.build.os_type"

    /** Matchable name of the device */
    val deviceProductName: String

    /** Human-readable OS version. Shown within the UI of the app */
    val osVersion: String

    /** Used to check for updates and shown in the "Device" tab */
    val otaVersion: String

    /** Shown in the "Device" tab */
    val securityPatchDate: String

    /** Used for checking device/OS compatibility */
    val fingerprint = Build.FINGERPRINT.trim()
    val brandLowercase = (Build.BRAND.takeIf {
        it != UNKNOWN
    } ?: Build.MANUFACTURER.takeIf {
        it != UNKNOWN
    } ?: fingerprint.split("/").getOrNull(0))?.trim()?.lowercase() ?: UNKNOWN

    /**
     * This prop is present only on 7-series and above, on OS versions before the Oppo merger (ColorOS base).
     * Possible values:
     * 1. Stable: if property is present, but has no value
     * 2. Beta: if property is present and has "Beta" as its value
     * 3. Alpha: if property is present and has "Alpha" as its value
     * 4. <blank>: if property isn't present at all (i.e. unknown OS type)
     */
    val osType: String

    /** @see OplusPipelineLookupKeys */
    val pipeline: String

    val isEuBuild: Boolean

    init {
        // Default to something sensible or set to Build.UNKNOWN
        var deviceProductName = Build.PRODUCT
        var osVersion = Build.DISPLAY
        var otaVersion = UNKNOWN
        var osType = UNKNOWN
        var pipeline = UNKNOWN
        var isEuBuild = false
        var securityPatchDate = if (SDK_INT >= VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH
        } else UNKNOWN // read later on

        try {
            if (!useSystemProperties) throw UnsupportedOperationException("`useSystemProperties` is false")

            if (deviceProductName == OnePlus3) {
                // Workaround #3: don't use `ro.product.name` for OP3/3T; they have the same value.
                // Note: prior to v5.10.0, `ro.product.name` was read only on devices from 7-series onwards, and only
                // when `ro.display.series` also existed. It was meant as a workaround that added support for regional
                // variants. As a result, the app never used `ro.product.name` on devices & OS versions released after
                // the Oppo merger (ColorOS base), instead relying on `ro.build.product`. This caused issues with 10T
                // on OOS13, where `ro.build.product` had a value of `qssi` for some reason.
                deviceProductName = pickFirstValid(BuildConfig.DEVICE_NAME_LOOKUP_KEYS, deviceProductName) { _, value -> value }
            } else if (OnePlus7TSeries.contains(deviceProductName)) {
                // Workaround #4 (Build.PRODUCT + ro.build.soft.version): support Indian variants for 7T-series,
                // on OS versions released before the Oppo merger (ColorOS base).
                val buildSoftVersion = systemProperty(BuildSoftVersionLookupKey)

                // Append _IN to mark device as Indian variant
                if (buildSoftVersion.getOrNull(0) == 'I') deviceProductName += "_IN"
            } else if (SDK_INT == VERSION_CODES.R && OnePlus7Series.contains(deviceProductName)) {
                // Workaround #5 (Build.PRODUCT + ro.vendor.op.india): differentiate between 7-series GLO/IND on the
                // last two OOS11 builds (11.0.8.1 & 11.0.9.1). This property was used by system OTA to deliver the
                // correct regional OOS12 build (GLO: H.31, IND: H.30). There's another OOS12 workaround below.
                val india = systemProperty(VendorOpIndia)
                if (india == "1" || india == "true") deviceProductName += "_IN"
            }

            // Prefer `Build.DISPLAY` on Android>13/T, to pick the new OOS13.1 format: KB2001_13.1.0.513(EX01),
            // which corresponds to the KB2001_11_F.66 version number. Below OOS13.1, `Build.DISPLAY` is the version
            // number, so we're not losing any info.
            if (osVersion == UNKNOWN || SDK_INT < VERSION_CODES.TIRAMISU) osVersion = pickFirstValid(
                BuildConfig.OS_VERSION_NUMBER_LOOKUP_KEYS, osVersion
            ) { key, value ->
                if (key != RomVersionLookupKey) return@pickFirstValid value

                // Workaround #1 (ro.rom.version): ignore if value has the "H2OS" prefix (seen on OOS 2 & 3).
                if (value.contains(H2OS)) return@pickFirstValid null

                // Workaround #2 (ro.rom.version): remove redundant "Oxygen OS " prefix from value, because the app
                // shows only the number or adds custom formatting. Seen on devices from OnePlus 7-series onwards,
                // on OS versions released before the Oppo merger (ColorOS base).
                if (value.contains(OxygenOsPrefix)) value.replace(OxygenOsPrefix, "") else value
            } else systemProperty(FullOsVersionLookupKey).let {
                // Prefer OS version with SOTA info, if available
                if (it != UNKNOWN) osVersion = it
            }

            pipeline = pickFirstValid(OplusPipelineLookupKeys, pipeline) { _, value -> value }
            if (SDK_INT >= VERSION_CODES.S && pipeline != UNKNOWN) {
                if (deviceProductName in OnePlusPadAndPad2) {
                    // Skip EUEX because that's already supported as OPD2203EEA/OPD2403EEA
                    if (pipeline != "EUEX") deviceProductName += pipeline
                } else if (OnePlus7Series.contains(deviceProductName) || OnePlus7TSeries.contains(deviceProductName)) {
                    // Workaround #5 (Build.PRODUCT + ro.vendor.oplus.regionmark): differentiate between GLO/IND
                    // builds for 7- & 7T-series on OOS12. This affects H.31/H.30 & F.17 builds, where the same
                    // model number is used for both regions. Not sure if future builds would also be affected.
                    if (pipeline.startsWith("IN")) deviceProductName += "_IN"
                } else if ("oppo" == brandLowercase) {
                    // Special handling for OPPO phones. They often use the same model number across all regions.
                    // Append pipeline to model number, only if it's not already part of it. A quick check is to
                    // see if the last character is a digit, since pipelines are almost exclusively alphabets.
                    val lastChar = pipeline.lastOrNull() ?: '0'
                    if (lastChar in '0'..'9') deviceProductName += pipeline
                }
            }

            val euBooleanStr = pickFirstValid(BuildEuLookupKeys) { _, value -> value }
            isEuBuild = if (euBooleanStr == UNKNOWN) pipeline.startsWith("EU") else euBooleanStr.toBoolean()

            otaVersion = systemProperty(BuildConfig.OS_OTA_VERSION_NUMBER_LOOKUP_KEY)

            // This prop is present only on 7-series and above
            osType = systemProperty(BuildOsTypeLookupKey).let {
                if (it.isBlank()) "Stable" else if (it == UNKNOWN) "" else it
            }

            // On Android >= 6/Marshmallow, security patch is picked up from `Build.VERSION.SECURITY_PATCH` above
            if (SDK_INT < VERSION_CODES.M) {
                securityPatchDate = systemProperty(SecurityPatchLookupKey)
            }
        } catch (e: Exception) {
            try {
                logInfo(TAG, "Fast path via `android.os.SystemProperties` failed; falling back to slower getprop output parse", e)

                val getBuildPropProcess = Runtime.getRuntime().exec("getprop")

                val scanner = Scanner(getBuildPropProcess.inputStream).useDelimiter("\\A")
                val properties = if (scanner.hasNext()) scanner.next() else ""

                getBuildPropProcess.destroy()

                if (deviceProductName == OnePlus3) {
                    // Workaround #3: don't use `ro.product.name` for OP3/3T; they have the same value.
                    // Note: prior to v5.10.0, `ro.product.name` was read only on devices from 7-series onwards, and only
                    // when `ro.display.series` also existed. It was meant as a workaround that added support for regional
                    // variants. As a result, the app never used `ro.product.name` on devices & OS versions released after
                    // the Oppo merger (ColorOS base), instead relying on `ro.build.product`. This caused issues with 10T
                    // on OOS13, where `ro.build.product` had a value of `qssi` for some reason.
                    deviceProductName = readBuildPropItem(BuildConfig.DEVICE_NAME_LOOKUP_KEYS, properties, deviceProductName)
                } else if (OnePlus7TSeries.contains(deviceProductName)) {
                    // Workaround #4 (Build.PRODUCT + ro.build.soft.version): support Indian variants for 7T-series,
                    // on OS versions released before the Oppo merger (ColorOS base).
                    val buildSoftVersion = readBuildPropItem(BuildSoftVersionLookupKey, properties)

                    // Append _IN to mark device as Indian variant
                    if (buildSoftVersion.getOrNull(0) == 'I') deviceProductName += "_IN"
                } else if (SDK_INT == VERSION_CODES.R && OnePlus7Series.contains(deviceProductName)) {
                    // Workaround #5 (Build.PRODUCT + ro.vendor.op.india): differentiate between 7-series GLO/IND on the
                    // last two OOS11 builds (11.0.8.1 & 11.0.9.1). This property was used by system OTA to deliver the
                    // correct regional OOS12 build (GLO: H.31, IND: H.30). There's another OOS12 workaround below.
                    val india = readBuildPropItem(VendorOpIndia, properties)
                    if (india == "1" || india == "true") deviceProductName += "_IN"
                }

                // Prefer `Build.DISPLAY` on Android>13/T, to pick the new OOS13.1 format: KB2001_13.1.0.513(EX01),
                // which corresponds to the KB2001_11_F.66 version number. Below OOS13.1, `Build.DISPLAY` is the version
                // number, so we're not losing any info.
                if (osVersion == UNKNOWN || SDK_INT < VERSION_CODES.TIRAMISU) osVersion = readBuildPropItem(
                    BuildConfig.OS_VERSION_NUMBER_LOOKUP_KEYS, properties, osVersion
                ) else readBuildPropItem(FullOsVersionLookupKey, properties).let {
                    // Prefer OS version with SOTA info, if available
                    if (it != UNKNOWN) osVersion = it
                }

                pipeline = readBuildPropItem(OplusPipelineLookupKeys, properties, pipeline)
                if (SDK_INT >= VERSION_CODES.S && pipeline != UNKNOWN) {
                    if (deviceProductName in OnePlusPadAndPad2) {
                        // Skip EUEX because that's already supported as OPD2203EEA/OPD2403EEA
                        if (pipeline != "EUEX") deviceProductName += pipeline
                    } else if (OnePlus7Series.contains(deviceProductName) || OnePlus7TSeries.contains(deviceProductName)) {
                        // Workaround #5 (Build.PRODUCT + ro.vendor.oplus.regionmark): differentiate between GLO/IND
                        // builds for 7- & 7T-series on OOS12. This affects H.31/H.30 & F.17 builds, where the same
                        // model number is used for both regions. Not sure if future builds would also be affected.
                        if (pipeline.startsWith("IN")) deviceProductName += "_IN"
                    } else if ("oppo" == brandLowercase) {
                        // Special handling for OPPO phones. They often use the same model number across all regions.
                        // Append pipeline to model number, only if it's not already part of it. A quick check is to
                        // see if the last character is a digit, since pipelines are almost exclusively alphabets.
                        val lastChar = pipeline.lastOrNull() ?: '0'
                        if (lastChar in '0'..'9') deviceProductName += pipeline
                    }
                }

                val euBooleanStr = readBuildPropItem(BuildEuLookupKeys, properties)
                isEuBuild = if (euBooleanStr == UNKNOWN) pipeline.startsWith("EU") else euBooleanStr.toBoolean()

                otaVersion = readBuildPropItem(BuildConfig.OS_OTA_VERSION_NUMBER_LOOKUP_KEY, properties)

                // This prop is present only on 7-series and above
                osType = readBuildPropItem(BuildOsTypeLookupKey, properties).let {
                    if (it.isBlank()) "Stable" else if (it == UNKNOWN) "" else it
                }

                // On Android >= 6/Marshmallow, security patch is picked up from `Build.VERSION.SECURITY_PATCH` above
                if (SDK_INT < VERSION_CODES.M) {
                    securityPatchDate = readBuildPropItem(SecurityPatchLookupKey, properties)
                }
            } catch (e: Exception) {
                Log.e(TAG, e.localizedMessage ?: "$e", e)
            }
        }

        this.deviceProductName = deviceProductName
        this.osVersion = osVersion
        this.otaVersion = otaVersion
        this.securityPatchDate = securityPatchDate
        this.osType = osType
        this.pipeline = pipeline
        this.isEuBuild = isEuBuild
    }

    private inline fun pickFirstValid(
        keys: Array<String>,
        default: String = UNKNOWN,
        workarounds: (key: String, value: String) -> String?,
    ): String {
        for (key in keys) {
            val value = systemProperty(key)
            if (value.isEmpty() || value == UNKNOWN) continue // invalid, skip to next iteration

            val newValue = workarounds(key, value)
            if (newValue != null) return newValue // break on first valid value

            // otherwise let the loop continue to the next iteration
        }

        return default
    }

    @Throws(IOException::class)
    private fun readBuildPropItem(itemKey: String, properties: String?) = readBuildPropItem(arrayOf(itemKey), properties)

    @Throws(IOException::class)
    private fun readBuildPropItem(itemKeys: Array<String>, properties: String?, default: String = UNKNOWN): String {
        if (properties.isNullOrEmpty()) return default

        var result = default

        // Some keys are not present on all devices, so check multiple in-order
        itemKeys.forEach { item ->
            val reader = BufferedReader(StringReader(properties))
            var inputLine: String?

            while (reader.readLine().also { inputLine = it } != null) {
                if (inputLine!!.contains(item)) {
                    // getprop output format is `[<item>]: [<value>]`, and we only need <value>.
                    // This is more efficient way to get rid of unneeded parts of the string, as
                    // opposed to `replace("[$item]: ", "").replace("[", "").replace("]", "")`.
                    result = inputLine.drop(
                        item.length + 5 /* 2 for surrounding `[]`, 2 for `: `, and 1 more for `[` */
                    ).dropLast(1 /* last `]` */)

                    if (item == RomVersionLookupKey) {
                        // Workaround #1 (ro.rom.version): ignore if value has the "H2OS" prefix (seen on OOS 2 & 3).
                        if (result.contains(H2OS)) {
                            result = UNKNOWN
                            continue
                        }

                        // Workaround #2 (ro.rom.version): remove redundant "Oxygen OS " prefix from value, because the app
                        // shows only the number or adds custom formatting. Seen on devices from OnePlus 7-series onwards,
                        // on OS versions released before the Oppo merger (ColorOS base).
                        if (result.contains(OxygenOsPrefix)) {
                            result = result.replace(OxygenOsPrefix, "")
                        }
                    }

                    // Return the first successfully detected item. This because some keys have multiple values which all exist in the same properties file.
                    return result
                }
            }
        }

        return result
    }
}
