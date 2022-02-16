package com.lccao.androidemulatordetector

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object DataCollector {
    val isCollecting: AtomicBoolean = AtomicBoolean(false)
    val hasCollected: AtomicBoolean = AtomicBoolean(false)
    val dataCollectorsList: List<() -> CollectedDataModel> = listOf(this::mockFun1, this::mockFun2)
    val collectedDataList: AtomicReference<MutableList<CollectedDataModel>> = AtomicReference(mutableListOf())

    fun startCollection() {
        isCollecting.set(true)
        dataCollectorsList.forEach {
            val begin = System.currentTimeMillis()
            var collectedData = it.invoke()
            val end = System.currentTimeMillis()
            collectedData.collectionTimestamp = end - begin
            collectedDataList.get().add(collectedData)
        }
        hasCollected.set(true)
    }

    fun mockFun1(): CollectedDataModel {
        return CollectedDataModel("a", "b", 0)
    }

    fun mockFun2(): CollectedDataModel {
        return CollectedDataModel("c", "d", 0)
    }
}