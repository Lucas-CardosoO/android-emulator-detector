package com.lccao.androidemulatordetector

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

@DelicateCoroutinesApi
object DataCollector {
    private val dataCollectorsList: List<() -> CollectedDataModel> = listOf(this::mockFun1, this::mockFun2)
    val collectedDataList: AtomicReference<MutableList<CollectedDataModel>> = AtomicReference(mutableListOf())

    suspend fun fetchCollection() = coroutineScope {
        val wrapper = JNIWrapper()
        val abi = wrapper.getABI()
        val isemu = wrapper.isemu()
        Log.d("TESTE","TESTE: ${abi}, ${isemu}")
        dataCollectorsList.forEach {
            val begin = System.currentTimeMillis()
            val collectedData = it.invoke()
            val end = System.currentTimeMillis()
            collectedData.collectionDurationTimestamp = end - begin
            collectedDataList.get().add(collectedData)
            TsvFileLogger.log(collectedData)
        }
    }

    private fun mockFun1(): CollectedDataModel {
        return CollectedDataModel("a", "b", 0)
    }

    private fun mockFun2(): CollectedDataModel {
        return CollectedDataModel("c", "d", 0)
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