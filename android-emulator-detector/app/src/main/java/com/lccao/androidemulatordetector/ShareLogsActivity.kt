package com.lccao.androidemulatordetector

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.opengl.GLES20
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.core.content.FileProvider
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import java.io.File


class ShareLogsActivity : AppCompatActivity(), CoroutineScope {
    override val coroutineContext = IO
    lateinit var button: Button
    lateinit var loadingIndicator: CircularProgressIndicator
    private val dataCollectorsList: List<() -> CollectedDataModel> = listOf(this::checkOpenGL, this::checkOpenGLLegacy)
    private var isRunningOnEmulator = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_logs)
        button = findViewById(R.id.share_logs)
        loadingIndicator = findViewById(R.id.loading_indicator)
        TsvFileLogger.setFolderPathFromContext(applicationContext)
        TsvFileLogger.deleteLogFiles()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.READ_SMS
            ), 1)
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_SMS),
                1)
        }
    }

    fun share(view: View) {
        this.shareLog()
    }

    fun onCheckboxClicked(view: View) {
        if (view is CheckBox) {
            isRunningOnEmulator = view.isChecked
        }
    }

    private fun getUIDependentCollection(): List<CollectedDataModel> {
        val collectedDataList = mutableListOf<CollectedDataModel>()
        dataCollectorsList.forEach {
            val begin = System.currentTimeMillis()
            val collectedData = it.invoke()
            val end = System.currentTimeMillis()
            collectedData.collectionDurationTimestamp = end - begin
            collectedDataList.add(collectedData)
        }
        return collectedDataList
    }

    private fun checkOpenGLLegacy(): CollectedDataModel {
        return try {
            val opengl: String? = GLES20.glGetString(GLES20.GL_RENDERER)
            CollectedDataModel(
                collectionDescription = "Open GL Gingo",
                collectedData = mapOf("openGLRender" to (opengl ?: "null")),
                emulatorDetected = opengl?.contains("Bluestacks") == true ||
                        opengl?.contains("Translator") == true
            )
        } catch (e: Exception) {
            CollectedDataModel(
                collectionDescription = "Open GL Gingo",
                collectedData = mapOf("Error" to e.toString()),
                emulatorDetected = false
            )
        }
    }

    private fun checkOpenGL(): CollectedDataModel {
        return try {
            val eglCore = EglCore(null, EglCore.FLAG_TRY_GLES3)
            val surface = OffscreenSurface(eglCore, 1, 1)
            surface.makeCurrent()

            val opengl: String? = GLES20.glGetString(GLES20.GL_RENDERER)

            surface.release();
            eglCore.release();
            CollectedDataModel(
                collectionDescription = "Open GL",
                collectedData = mapOf("openGLRender" to (opengl ?: "null")),
                emulatorDetected = opengl?.contains("Bluestacks") == true ||
                        opengl?.contains("Translator") == true
            )
        } catch (e: Exception) {
            CollectedDataModel(
                collectionDescription = "Open GL",
                collectedData = mapOf("Error" to e.toString()),
                emulatorDetected = false
            )
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            1 -> {
                runOnUiThread {
                    val uiCollectedDataList: List<CollectedDataModel> = getUIDependentCollection()

                    GlobalScope.launch() {
                        DataCollector.fetchCollection(uiCollectedDataList)
                        runOnUiThread {
                            button.visibility = View.VISIBLE
                            loadingIndicator.visibility = View.GONE
                        }
                    }
                }
                return
            }
        }
    }

    private fun shareLog() {
        TsvFileLogger.archiveLogs(
            isEmulator = isRunningOnEmulator,
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
                                .putExtra(Intent.EXTRA_EMAIL, arrayOf("lccao@cin.ufpe.br"))
                                .putExtra(Intent.EXTRA_SUBJECT, "Android Emulator Logs")
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