package com.lccao.androidemulatordetector

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

@DelicateCoroutinesApi
object DataCollector {
    private val dataCollectorsList: List<() -> CollectedDataModel> = listOf(this::mockFun1, this::mockFun2)
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

    private fun mockFun1(): CollectedDataModel {
        return CollectedDataModel("a", "b", 0)
    }

    private fun mockFun2(): CollectedDataModel {
        return CollectedDataModel("c", "d", 0)
    }
}