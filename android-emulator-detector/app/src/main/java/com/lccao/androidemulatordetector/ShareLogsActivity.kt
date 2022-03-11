package com.lccao.androidemulatordetector

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ShareCompat
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.FileProvider
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main
import java.io.File

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
        TsvFileLogger.setFolderPathFromContext(applicationContext)
        TsvFileLogger.deleteLogFiles()

        GlobalScope.launch {
            DataCollector.fetchCollection()

            button.visibility = View.VISIBLE
            loadingIndicator.visibility = View.GONE
        }
    }

    fun share(view: View) {
//        val sendIntent = Intent().apply {
//            action = Intent.ACTION_SEND
//            type = "text/plain"
//            putExtra(Intent.EXTRA_SUBJECT, "Share file")
//            putExtra(Intent.EXTRA_TEXT, "Chose application")
//        }
//        startActivity(sendIntent)
        this.shareLog()
    }

    private fun shareLog() {
        TsvFileLogger.archiveLogs(
            object : TsvFileLogger.LogArchiveListener {
                override fun onSuccess(archiveFile: File) {
                    try {
                        if (archiveFile.length() > 0) {
                            val uri: Uri =
                                FileProvider.getUriForFile(
                                    this@ShareLogsActivity,
                                    packageName,
                                    archiveFile
                                )
                            val intent = IntentBuilder.from(this@ShareLogsActivity)
                                .setStream(uri)
                                .setType("message/rfc822")
                                .intent
                                .setAction(Intent.ACTION_SEND)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            startActivity(
                                Intent.createChooser(
                                    intent,
                                    "Share"
                                )
                            )
                        } else {
                            runOnUiThread {
                                Toast.makeText(
                                    this@ShareLogsActivity,
                                    "Empty File",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } catch (e: IllegalStateException) {
                        runOnUiThread {
                            Toast.makeText(
                                this@ShareLogsActivity,
                                "Error",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                override fun onError() {
                    runOnUiThread {
                        Toast.makeText(
                            this@ShareLogsActivity,
                            "Error",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

}