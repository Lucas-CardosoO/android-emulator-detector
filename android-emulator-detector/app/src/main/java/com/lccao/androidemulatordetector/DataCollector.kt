package com.lccao.androidemulatordetector

import java.util.concurrent.atomic.AtomicBoolean

object DataCollector {
    val isCollecting: AtomicBoolean = AtomicBoolean(false)
    val hasCollected: AtomicBoolean = AtomicBoolean(false)
    val dataCollectorsList: List<() -> DataCollector> = listOf()

    fun startCollection() {
        isCollecting.set(true)
        dataCollectorsList.forEach {

        }
    }
}