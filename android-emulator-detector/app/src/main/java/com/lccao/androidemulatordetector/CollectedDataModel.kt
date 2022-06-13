package com.lccao.androidemulatordetector

data class CollectedDataModel(var collectionDescription: String, var collectedData: Map<String, String>, internal var collectionDurationTimestamp: Long = 0)