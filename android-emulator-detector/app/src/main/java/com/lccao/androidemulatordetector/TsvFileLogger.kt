package com.lccao.androidemulatordetector

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object TsvFileLogger {

    private var NEW_LINE = System.getProperty("line.separator") ?: "\n"
    private const val MAX_FILE_QUANTITY = 10
    private const val SEPARATOR = "\t"
    private const val MAX_BYTES = 500 * 1024
    private const val LOGS_FOLDER_NAME = "logs"
    private const val LOG_FILE_PREFIX = "log"
    private const val DESCRIPTION_COLUMN_NAME = "descriptions"
    private const val COLLECTED_DATA_COLUMN_NAME = "collected_data"
    private const val COLLECTION_DURATION_COLUMN_NAME = "collection_duration"
    private const val TSV_EXTENSION = ".tsv"
    private const val ARCHIVED_LOGS_FILE_NAME = "logs.zip"
    private const val ZIP_BUFFER_SIZE = 2048

    private val handler: Handler
    private var folderPath: String = ""

    init {
        val handlerThread = HandlerThread("FileLogger")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
    }

    fun setFolderPathFromContext(context: Context) {
        folderPath = context.filesDir.absolutePath + File.separatorChar + LOGS_FOLDER_NAME
    }

    fun log(collectedDataModel: CollectedDataModel) {

        handler.post {
            try {
                val log = buildString {
                    append(collectedDataModel.collectionDescription)
                    append(SEPARATOR)
                    append(collectedDataModel.collectedData)
                    append(SEPARATOR)
                    append(collectedDataModel.collectionDurationTimestamp)
                    append(NEW_LINE)
                }
                writeLog(log)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun writeColumnsNamesToFile(file: File) {
        try {
            val log = buildString {
                append(DESCRIPTION_COLUMN_NAME)
                append(SEPARATOR)
                append(COLLECTED_DATA_COLUMN_NAME)
                append(SEPARATOR)
                append(COLLECTION_DURATION_COLUMN_NAME)
                append(NEW_LINE)
            }
            var fileWriter: FileWriter? = null
            try {
                fileWriter = FileWriter(file, true)
                fileWriter.append(log)
                fileWriter.flush()
                fileWriter.close()
            } catch (e: IOException) {
                fileWriter?.flush()
                fileWriter?.close()
            }
        } catch (ignored: Throwable) {
        }
    }

    fun archiveLogs(listener: LogArchiveListener?) {
        handler.post {
            try {
                val files = logFiles
                var origin: BufferedInputStream
                val dest = FileOutputStream("$folderPath/$ARCHIVED_LOGS_FILE_NAME")
                val out = ZipOutputStream(BufferedOutputStream(dest))
                val data = ByteArray(ZIP_BUFFER_SIZE)
                for (file in files) {
                    val fi = FileInputStream(file)
                    origin = BufferedInputStream(fi, ZIP_BUFFER_SIZE)
                    val entry = ZipEntry(file.substring(file.lastIndexOf("/") + 1))
                    out.putNextEntry(entry)
                    var count: Int
                    while (origin.read(data, 0, ZIP_BUFFER_SIZE).also { count = it } != -1) {
                        out.write(data, 0, count)
                    }
                    origin.close()
                }
                out.finish()
                out.close()
                listener?.let {

                    val archivedLogFile = File("$folderPath/$ARCHIVED_LOGS_FILE_NAME")
                    it.onSuccess(archivedLogFile)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                listener?.onError()
            }
        }
    }

    fun deleteLogFiles() {
        handler.post {
            val dir = File(folderPath)
            val children = dir.list()
            for (i in 0 until (children?.size ?: 0)) {
                File(dir, children!![i]).delete()
            }
        }
    }

    private val logFiles: List<String>
        get() {
            val logsFolder = File(folderPath)
            val files = logsFolder.listFiles()
            val logFilePaths = ArrayList<String>()
            files?.let { logFiles ->
                for (file in logFiles) {
                    file?.let {
                        if (it.isFile && it.absolutePath.endsWith(TSV_EXTENSION)) {
                            logFilePaths.add(it.absolutePath)
                        }
                    }
                }
            }
            return logFilePaths
        }

    private fun writeLog(log: String) {
        var fileWriter: FileWriter? = null
        val logFile = currentLogFile
        try {
            fileWriter = FileWriter(logFile, true)
            fileWriter.append(log)
            fileWriter.flush()
            fileWriter.close()
        } catch (e: IOException) {
            fileWriter?.let {
                try {
                    it.flush()
                    it.close()
                } catch (ignored: IOException) {
                }
            }
        }
    }

    private val currentLogFile: File
        get() {
            val logFolder = File(folderPath)
            if (!logFolder.exists()) {
                logFolder.mkdirs()
            }

            for (i in 0 until MAX_FILE_QUANTITY) {
                val fileName = fileName(i)
                val file = File(logFolder, fileName)

                when {
                    !file.exists() -> {
                        writeColumnsNamesToFile(file)
                        return file
                    }
                    file.length() < MAX_BYTES -> return file
                }
            }

            shiftFiles()

            val lastFileName = fileName(MAX_FILE_QUANTITY - 1)
            val file = File(logFolder, lastFileName)
            if (!file.exists()) {
                writeColumnsNamesToFile(file)
            }
            return file
        }

    private fun shiftFiles() {
        for (i in 1 until MAX_FILE_QUANTITY) {
            val logFolder = File(folderPath)
            val fileName = fileName(i)
            val oldFileName = fileName(i - 1)
            val file = File(logFolder, fileName)
            val oldFile = File(logFolder, oldFileName)
            oldFile.delete()
            file.renameTo(oldFile)
        }
    }

    private fun fileName(index: Int) = String.format(
        Locale.getDefault(),
        "%s_%s$TSV_EXTENSION",
        LOG_FILE_PREFIX,
        index
    )

    interface LogArchiveListener {
        fun onSuccess(archiveFile: File)
        fun onError()
    }
}
