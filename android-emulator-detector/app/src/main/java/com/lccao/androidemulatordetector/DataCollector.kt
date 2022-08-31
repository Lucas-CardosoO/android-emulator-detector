package com.lccao.androidemulatordetector

import android.Manifest.permission.INTERNET
import android.Manifest.permission.READ_PHONE_STATE
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import android.text.TextUtils
import androidx.core.content.ContextCompat
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.atomic.AtomicReference

typealias PropertyName = String
typealias PropertyValue = String
typealias Property = Pair<PropertyName, PropertyValue?>

@DelicateCoroutinesApi
object DataCollector {
    private val EMULATOR_FILES = mapOf(
        // Genymotion files
        "genymotion" to listOf(
            "/dev/socket/genyd",
            "/dev/socket/baseband_genyd",
        ),

        // Nox files
        "nox" to listOf(
            "fstab.nox",
            "init.nox.rc",
            "ueventd.nox.rc",
        ),
        // Andy files
        "andy" to listOf(
            "fstab.andy",
            "ueventd.andy.rc",
        ),
        // x86 Files only marked as Emulator when detected along with 5 qemu system properties
        "x86" to listOf(
            "ueventd.android_x86.rc",
            "x86.prop",
            "ueventd.ttVM_x86.rc",
            "init.ttVM_x86.rc",
            "fstab.ttVM_x86",
            "fstab.vbox86",
            "init.vbox86.rc",
            "ueventd.vbox86.rc",
        ),
        // Pipes
        "pipes" to listOf(
            "/dev/socket/qemud",
            "/dev/qemu_pipe",
        ),
    )

    private val QEMU_DRIVERS = arrayOf("goldfish")

    private val PROPERTIES = listOf(
        Property("init.svc.qemud", null),
        Property("init.svc.qemu-props", null),
        Property("qemu.hw.mainkeys", null),
        Property("qemu.sf.fake_camera", null),
        Property("qemu.sf.lcd_density", null),
        Property("ro.bootloader", "unknown"),
        Property("ro.bootmode", "unknown"),
        Property("ro.hardware", "goldfish"),
        Property("ro.kernel.android.qemud", null),
        Property("ro.kernel.qemu.gles", null),
        Property("ro.kernel.qemu", "1"),
        Property("ro.product.device", "generic"),
        Property("ro.product.model", "sdk"),
        Property("ro.product.name", "sdk"),
        Property("ro.serialno", null),
    )

    private val DEVICE_IDS = arrayOf(
        "000000000000000",
        "e21833235b6eef10",
        "012345678912345"
    )

    private val PHONE_NUMBERS = arrayOf(
        "15555215554", "15555215556", "15555215558", "15555215560", "15555215562", "15555215564",
        "15555215566", "15555215568", "15555215570", "15555215572", "15555215574", "15555215576",
        "15555215578", "15555215580", "15555215582", "15555215584"
    )

    private val IMSI_IDS = arrayOf(
        "310260000000000"
    )

    private const val IP = "10.0.2.15"

    private const val MIN_PROPERTIES_THRESHOLD = 5

    private val dataCollectorsList: List<() -> CollectedDataModel> =
        listOf(this::isEmulator, this::buildCharacteristics, this::emulatorFiles,
            this::checkQEmuDrivers, this::checkQEmuProps, this::checkTelephony,
            this::checkIp, this::checkPackageName, this::checkAvailableActivities,
            this::checkRunningServices)
    val collectedDataList: AtomicReference<MutableList<CollectedDataModel>> =
        AtomicReference(mutableListOf())

    suspend fun fetchCollection() = coroutineScope {
        dataCollectorsList.forEach {
            val begin = System.currentTimeMillis()
            val collectedData = it.invoke()
            val end = System.currentTimeMillis()
            collectedData.collectionDurationTimestamp = end - begin
            collectedDataList.get().add(collectedData)
            TsvFileLogger.log(collectedData)
        }
    }

    private fun isEmulator(): CollectedDataModel {
        val wrapper = JNIWrapper()
        val abi = wrapper.getABI()
        val isEmulator = wrapper.isEmulator()

        val collectedData = mapOf("ABI" to abi, "isEmu" to "$isEmulator")
        return CollectedDataModel(
            "isEmu vectorization detection. isEmu may be -1 if running on unsupported hardware",
            collectedData,
            isEmulator > 0
        )
    }

    @SuppressLint("HardwareIds")
    private fun buildCharacteristics(): CollectedDataModel {
        val collectedData = mapOf(
            "model" to Build.MODEL,
            "fingerprint" to Build.FINGERPRINT,
            "hardware" to Build.HARDWARE,
            "manufacturer" to Build.MANUFACTURER,
            "product" to Build.PRODUCT,
            "board" to Build.BOARD,
            "bootloader" to Build.BOOTLOADER,
            "serial" to (Build.SERIAL),
            "brand" to Build.BRAND,
            "device" to Build.DEVICE,
        )

        return CollectedDataModel("Build data", collectedData, checkBasic())
    }

    private fun checkBasic(): Boolean {
        var result = (Build.FINGERPRINT.startsWith("generic")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.lowercase(Locale.getDefault()).contains("droid4x")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HARDWARE == "goldfish" || Build.HARDWARE == "vbox86" || Build.PRODUCT == "sdk" || Build.PRODUCT == "google_sdk" || Build.PRODUCT == "sdk_x86" || Build.PRODUCT == "vbox86p" || Build.BOARD.lowercase(
            Locale.getDefault()
        ).contains("nox")
                || Build.BOOTLOADER.lowercase(Locale.getDefault()).contains("nox")
                || Build.HARDWARE.lowercase(Locale.getDefault()).contains("nox")
                || Build.PRODUCT.lowercase(Locale.getDefault()).contains("nox")
                || Build.SERIAL.lowercase(Locale.getDefault()).contains("nox"))
        if (result) return true
        result = result or (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
        if (result) return true
        result = result or ("google_sdk" == Build.PRODUCT)
        return result
    }

    private fun emulatorFiles(): CollectedDataModel {
        val collectedData = HashMap<String, String>()
        val QEmuProps = checkQEmuProps()
        EMULATOR_FILES.forEach { emulatorEntry ->
            val files = emulatorEntry.value.filter {
                val emulatorFile = File(it)
                emulatorFile.exists()
            }
            if (files.isNotEmpty()) {
                collectedData[emulatorEntry.key] = files.toString()
            }
        }
        return CollectedDataModel("Emulator files", collectedData, collectedData.isNotEmpty() && (collectedData["x86"] == null || QEmuProps.emulatorDetected))
    }

    private fun checkQEmuDrivers(): CollectedDataModel {
        val driversFiles = arrayOf(File("/proc/tty/drivers"), File("/proc/cpuinfo"))
        val collectedData = emptyMap<String, String>().toMutableMap()
        var detectedFile = false
        for (driversFile in driversFiles) {
            if (driversFile.exists() && driversFile.canRead()) {
                try {
                    val data = ByteArray(1024)
                    val inputStream: InputStream = FileInputStream(driversFile)
                    inputStream.read(data)
                    inputStream.close()

                    val driverData = String(data)
                    val detectedDrivers = QEMU_DRIVERS.filter { driverData.contains(it) }

                    if (detectedDrivers.isNotEmpty()) {
                        detectedFile = true
                        collectedData[driversFile.name] = detectedDrivers.toString()
                    }
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    collectedData["Collection of ${driversFile.name} Failed with exception"] = exception.toString()
                }
            }
        }
        return CollectedDataModel("Quemu known drivers", collectedData, detectedFile)
    }

    private fun checkQEmuProps(): CollectedDataModel {
        var foundProps = 0
        val collectedData = emptyMap<String, String>().toMutableMap()
        for (property in PROPERTIES) {
            val propertyValue = getProp(App.appContext, property.first)
            if (property.second != null) {
                propertyValue?.let {
                    if (it.contains(property.second!!)) {
                        foundProps++
                    }
                    collectedData[property.first] = propertyValue
                }
            } else {
                propertyValue?.let {
                    foundProps++
                    collectedData[property.first] = propertyValue
                }
            }
        }
        return CollectedDataModel("QEmuProps", collectedData, collectedData.size >= MIN_PROPERTIES_THRESHOLD)
    }

    @SuppressLint("PrivateApi")
    private fun getProp(context: Context, property: String): String? {
        try {
            val classLoader: ClassLoader = context.classLoader
            val systemProperties = classLoader.loadClass("android.os.SystemProperties")
            val get: Method = systemProperties.getMethod("get", String::class.java)
            val params = arrayOfNulls<Any>(1)
            params[0] = property
            return get.invoke(systemProperties, params) as String
        } catch (exception: java.lang.Exception) {
        }
        return null
    }

    @SuppressLint("HardwareIds")
    private fun checkTelephony(): CollectedDataModel {
        return when {
            (ContextCompat.checkSelfPermission(App.appContext, READ_PHONE_STATE)) == PackageManager.PERMISSION_GRANTED && isSupportTelephony() -> {
                val telephonyManager = App.appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val phoneNumber = telephonyManager.line1Number
                val deviceId = telephonyManager.deviceId // May not have permission, check
                val imsi = telephonyManager.subscriberId
                val operatorName = telephonyManager.networkOperatorName

                CollectedDataModel(
                    "Check Telephony",
                    mapOf("Phone Number" to phoneNumber, "Device ID" to deviceId, "IMSI" to imsi, "Network Operator Name" to operatorName),
                    PHONE_NUMBERS.contains(phoneNumber) ||
                            DEVICE_IDS.contains(deviceId) || IMSI_IDS.contains(imsi) || operatorName.equals("android", ignoreCase = true)
                )
            }
            !isSupportTelephony() -> {
                CollectedDataModel("Check Telephony", mapOf("Collection Failed" to "No Support for Telephony Permission"), false)
            }
            else -> {
                CollectedDataModel("Check Telephony", mapOf("Collection Failed" to "No READ_PHONE_STATE Permission"), false)
            }
        }
    }

    private fun isSupportTelephony(): Boolean {
        val packageManager: PackageManager = App.appContext.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }

    private fun checkIp(): CollectedDataModel {
        if (ContextCompat.checkSelfPermission(App.appContext, INTERNET)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val stringBuilder = StringBuilder()
            try {
                val builder = ProcessBuilder("/system/bin/netcfg")
                builder.directory(File("/system/bin/"))
                builder.redirectErrorStream(true)
                val process = builder.start()
                val inputStream = process.inputStream
                val re = ByteArray(1024)
                while (inputStream.read(re) != -1) {
                    stringBuilder.append(String(re))
                }
                inputStream.close()
            } catch (ex: java.lang.Exception) {
                return CollectedDataModel("Check IP", mapOf("Exception" to ex.toString()), false)
            }
            val netData = stringBuilder.toString()

            var netDataArray: Array<String> = emptyArray()
            val netDataList: MutableList<String> = mutableListOf()
            var detectedList: List<String> = emptyList()

            if (!TextUtils.isEmpty(netData)) {
                netDataArray = netData.split("\n").toTypedArray()
                val pattern = "\\s+".toRegex()
                for (s in netDataArray)
                    netDataList.add(s.replace(pattern, " "))
                detectedList = netDataList.filter { (it.contains("wlan0") || it.contains("tunl0") || it.contains("eth0")) && it.contains(IP) }
            }
            return CollectedDataModel("Check IP", mapOf("Net Data" to netDataList.toString(), "Detected Array" to detectedList.toString()), detectedList.isNotEmpty())
        } else {
            return CollectedDataModel("Check IP", mapOf("Missing permissions" to INTERNET), false)
        }
    }

    private fun checkAvailableActivities(): CollectedDataModel {
        val context = App.appContext
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val availableActivities = packageManager.queryIntentActivities(intent, 0)
        val availableActivitiesName: MutableList<String> = mutableListOf()
        val suspectAvailableActivitiesName: MutableList<String> = mutableListOf()
        for (resolveInfo in availableActivities) {
            availableActivitiesName.add(resolveInfo.activityInfo.packageName)
            if (resolveInfo.activityInfo.packageName.startsWith("com.bluestacks.")) {
                suspectAvailableActivitiesName.add(resolveInfo.activityInfo.packageName)
            }
        }

        return CollectedDataModel("Suspect Available Activities",
            mapOf(
                "Suspect Activities" to suspectAvailableActivitiesName.toString()
            ),
            suspectAvailableActivitiesName.isNotEmpty()
        )
    }

    private fun checkPackageName(): CollectedDataModel {
        val context = App.appContext
        val packageManager = context.packageManager
        val packages = packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
        val suspectPackagesFound: MutableList<String> = mutableListOf()
        for (packageInfo in packages) {
            val packageName = packageInfo.packageName
            if (packageName.startsWith("com.vphone.") ||
                packageName.startsWith("com.bignox.") ||
                packageName.startsWith("com.nox.mopen.app") ||
                packageName.startsWith("me.haima.") ||
                packageName.startsWith("com.bluestacks.") ||
                (packageName.startsWith("cn.itools.")
                        && Build.PRODUCT.startsWith("iToolsAVM")) ||
                packageName.startsWith("com.kop.") ||
                packageName.startsWith("com.kaopu.") ||
                packageName.startsWith("com.microvirt.") ||
                (packageName == "com.google.android.launcher.layouts.genymotion")) {
                suspectPackagesFound.add(packageName)
            }
        }

        return CollectedDataModel(
            collectionDescription = "Check Installed Packages",
            collectedData = mapOf("Suspect Packages" to suspectPackagesFound.toString()),
            emulatorDetected = suspectPackagesFound.isNotEmpty()
        )
    }

    private fun checkRunningServices(): CollectedDataModel {
        val context = App.appContext
        val manager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val serviceInfos = manager.getRunningServices(30)
        val suspectRunningServices: MutableList<String> = mutableListOf()
        for (serviceInfo in serviceInfos) {
            val serviceName = serviceInfo.service.className
            if (serviceName.startsWith("com.bluestacks.")) {
                suspectRunningServices.add(serviceName)
            }
        }
        
        return CollectedDataModel(
            collectionDescription = "Check Running Services",
            collectedData = mapOf("Suspect Running Services" to suspectRunningServices.toString()),
            emulatorDetected = suspectRunningServices.isNotEmpty()
        )
    }
}

class JNIWrapper {
    external fun isEmulator(): Int
    external fun getABI(): String

    companion object {
        init {
            System.loadLibrary("isemu")
        }
    }
}