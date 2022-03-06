package com.lccao.androidemulatordetector

import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main

class ShareLogsActivity : AppCompatActivity(), CoroutineScope {
    override val coroutineContext = Main
    lateinit var button: Button
    lateinit var loadingIndicator: CircularProgressIndicator

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_logs)
        button = findViewById(R.id.share_logs)
        loadingIndicator = findViewById(R.id.loading_indicator)

        GlobalScope.launch {
            DataCollector.fetchCollection()

            button.visibility = View.VISIBLE
            loadingIndicator.visibility = View.GONE
        }
    }

    fun share(view: View) {
        val collectedData = DataCollector.collectedDataList
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Share file")
            putExtra(Intent.EXTRA_TEXT, "Chose application")
        }
        startActivity(sendIntent)
    }
}