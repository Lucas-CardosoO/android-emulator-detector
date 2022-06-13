package com.lccao.androidemulatordetector

import android.annotation.SuppressLint
import android.os.Build
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@DelicateCoroutinesApi
object DataCollector {
    private val EMULATOR_FILES = arrayOf(
        // Genymotion files
        "/dev/socket/genyd",
        "/dev/socket/baseband_genyd",

        // Nox files
        "fstab.nox",
        "init.nox.rc",
        "ueventd.nox.rc",

        // Andy files
        "fstab.andy",
        "ueventd.andy.rc",

        // x86 Files
        "ueventd.android_x86.rc",
        "x86.prop",
        "ueventd.ttVM_x86.rc",
        "init.ttVM_x86.rc",
        "fstab.ttVM_x86",
        "fstab.vbox86",
        "init.vbox86.rc",
        "ueventd.vbox86.rc",

        // Pipes
        "/dev/socket/qemud",
        "/dev/qemu_pipe",
    )

    private val QEMU_DRIVERS = arrayOf("goldfish")

    private val dataCollectorsList: List<() -> CollectedDataModel> = listOf(this::isEmu, this::buildCharacteristics)
    val collectedDataList: AtomicReference<MutableList<CollectedDataModel>> = AtomicReference(mutableListOf())

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
        return CollectedDataModel("isEmu vectorization detection. isEmu may be -1 if running on unsupported hardware", collectedData)
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

    }


    private fun checkFiles(files: Array<String>, type: String): Boolean {
        for (file in files) {
            val emulatorFile = File(file)
            if (emulatorFile.exists()) {
                return true
            }
        }
        return false
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