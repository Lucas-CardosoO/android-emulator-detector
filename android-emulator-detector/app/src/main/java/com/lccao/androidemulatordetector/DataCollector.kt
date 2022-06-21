package com.lccao.androidemulatordetector

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.reflect.Method
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

    private val MIN_PROPERTIES_THRESHOLD = 5

    private val dataCollectorsList: List<() -> CollectedDataModel> =
        listOf(this::isEmu, this::buildCharacteristics, this::emulatorFiles, this::checkQEmuDrivers)
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

    private fun isEmu(): CollectedDataModel {
        val wrapper = JNIWrapper()
        val abi = wrapper.getABI()
        val isemu = wrapper.isemu()

        val collectedData = mapOf("ABI" to abi, "isEmu" to "$isemu")
        return CollectedDataModel(
            "isEmu vectorization detection. isEmu may be -1 if running on unsupported hardware",
            collectedData
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

        return CollectedDataModel("Build data", collectedData)
    }

    private fun emulatorFiles(): CollectedDataModel {
        val collectedData = HashMap<String, String>()
        EMULATOR_FILES.forEach {
            val files = checkFiles(it.value)
            if (files.isNotEmpty()) {
                collectedData[it.key] = files.toString()
            }
        }
        return CollectedDataModel("Emulator files", collectedData)
    }


    private fun checkFiles(files: List<String>): List<String> {
        val detectedFiles = mutableListOf<String>()
        for (file in files) {
            val emulatorFile = File(file)
            if (emulatorFile.exists()) {
                detectedFiles.add(file)
            }
        }
        return detectedFiles
    }

    private fun checkQEmuDrivers(): CollectedDataModel {
        val driversFiles = arrayOf(File("/proc/tty/drivers"), File("/proc/cpuinfo"))
        for (drivers_file in driversFiles) {
            if (drivers_file.exists() && drivers_file.canRead()) {
                val data = ByteArray(1024)
                try {
                    val inputStream: InputStream = FileInputStream(drivers_file)
                    inputStream.read(data)
                    inputStream.close()
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    return CollectedDataModel("Quemu known drivers", mapOf("Collection Failed with exception" to exception.toString()))
                }
                val driver_data = String(data)
                for (known_qemu_driver in QEMU_DRIVERS) {
                    if (driver_data.contains(known_qemu_driver)) {
                        return CollectedDataModel("Quemu known drivers", mapOf(known_qemu_driver to "true"))
                    }
                }
            }
        }
        return CollectedDataModel("Quemu known drivers", mapOf("No file was detected, files checked" to driversFiles.toString()))
    }

//    private fun checkQEmuProps(): Boolean {
//        var found_props = 0
//        for (property in PROPERTIES) {
//            property.
////            val property_value: String = getProp(AppConte, property.PropertyName)
//            if (property.seek_value == null && property_value != null) {
//                found_props++
//            }
//            if (property.seek_value != null
//                && property_value.contains(property.seek_value)
//            ) {
//                found_props++
//            }
//        }
//        if (found_props >= MIN_PROPERTIES_THRESHOLD) {
//            log("Check QEmuProps is detected")
//            return true
//        }
//        return false
//    }

    @SuppressLint("PrivateApi")
    private fun getProp(context: Context, property: String): String? {
        try {
            val classLoader: ClassLoader = context.getClassLoader()
            val systemProperties = classLoader.loadClass("android.os.SystemProperties")
            val get: Method = systemProperties.getMethod("get", String::class.java)
            val params = arrayOfNulls<Any>(1)
            params[0] = property
            return get.invoke(systemProperties, params) as String?
        } catch (exception: java.lang.Exception) {
            // empty catch
        }
        return null
    }
}

class JNIWrapper {
    external fun isemu(): Int
    external fun getABI(): String

    companion object {
        init {
            System.loadLibrary("isemu")
        }
    }
}