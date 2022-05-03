package com.lccao.androidemulatordetector

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

@DelicateCoroutinesApi
object DataCollector {
    private val dataCollectorsList: List<() -> CollectedDataModel> = listOf(this::isEmu)
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

        return CollectedDataModel("isEmu vectorization detection. isEmu may be -1 if running on unsupported hardware", "ABI:${abi}, isEmu:${isemu}")
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