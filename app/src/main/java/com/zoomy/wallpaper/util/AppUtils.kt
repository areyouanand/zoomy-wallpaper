package com.zoomy.wallpaper.util

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Point
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object AppUtils {
    lateinit var app: Application
        private set

    fun init(application: Application) {
        app = application
    }

    @Suppress("DEPRECATION")
    fun getScreenWidth(): Int {
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return app.resources.displayMetrics.widthPixels
        val point = Point()
        wm.defaultDisplay.getRealSize(point)
        return point.x
    }

    @Suppress("DEPRECATION")
    fun getScreenHeight(): Int {
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return app.resources.displayMetrics.heightPixels
        val point = Point()
        wm.defaultDisplay.getRealSize(point)
        return point.y
    }
}

class PrefsStore private constructor(name: String) {
    val sp: SharedPreferences = AppUtils.app.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun put(key: String, value: String) = sp.edit().putString(key, value).apply()
    fun put(key: String, value: Int) = sp.edit().putInt(key, value).apply()
    fun put(key: String, value: Float) = sp.edit().putFloat(key, value).apply()
    fun put(key: String, value: Boolean) = sp.edit().putBoolean(key, value).apply()
    fun putLong(key: String, value: Long) = sp.edit().putLong(key, value).apply()

    fun getString(key: String, defaultValue: String = ""): String = sp.getString(key, defaultValue) ?: defaultValue
    fun getInt(key: String, defaultValue: Int = -1): Int = sp.getInt(key, defaultValue)
    fun getFloat(key: String, defaultValue: Float = -1f): Float = sp.getFloat(key, defaultValue)
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = sp.getBoolean(key, defaultValue)
    fun getLong(key: String, defaultValue: Long = 0L): Long = sp.getLong(key, defaultValue)

    companion object {
        @Volatile
        private var instance: PrefsStore? = null

        fun getInstance(): PrefsStore {
            return instance ?: synchronized(this) {
                instance ?: PrefsStore("zoomy_prefs").also { instance = it }
            }
        }
    }
}

object ToastUtils {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentToast: Toast? = null

    fun showShort(text: CharSequence) = show(text, Toast.LENGTH_SHORT)
    fun showLong(text: CharSequence) = show(text, Toast.LENGTH_LONG)

    private fun show(text: CharSequence, duration: Int) {
        mainHandler.post {
            currentToast?.cancel()
            currentToast = Toast.makeText(AppUtils.app, text, duration).also { it.show() }
        }
    }
}

object FileUtils {
    fun getFileByPath(path: String?): File? {
        if (path.isNullOrBlank()) return null
        return File(path)
    }

    fun createOrExistsDir(dir: File?): Boolean {
        return dir != null && if (dir.exists()) dir.isDirectory else dir.mkdirs()
    }

    fun deleteFilesInDir(dirPath: String?): Boolean {
        return deleteFilesInDir(getFileByPath(dirPath))
    }

    fun deleteFilesInDir(dir: File?): Boolean {
        if (dir == null) return false
        if (!dir.exists()) return true
        if (!dir.isDirectory) return false
        val files = dir.listFiles() ?: return true
        for (file in files) {
            if (file.isFile) {
                if (!file.delete()) return false
            } else if (file.isDirectory) {
                if (!deleteDir(file)) return false
            }
        }
        return true
    }

    fun deleteDir(dir: File?): Boolean {
        if (dir == null) return false
        if (!dir.exists()) return true
        if (!dir.isDirectory) return false
        val files = dir.listFiles()
        if (!files.isNullOrEmpty()) {
            for (file in files) {
                if (file.isFile) {
                    if (!file.delete()) return false
                } else if (file.isDirectory) {
                    if (!deleteDir(file)) return false
                }
            }
        }
        return dir.delete()
    }

    fun moveDir(srcDir: File?, destDir: File?): Boolean {
        if (srcDir == null || destDir == null) return false
        if (!srcDir.exists() || !srcDir.isDirectory) return false
        if (destDir.exists()) {
            deleteFilesInDir(destDir)
        }
        if (!createOrExistsDir(destDir)) return false
        val files = srcDir.listFiles() ?: return true
        for (file in files) {
            val target = File(destDir, file.name)
            if (file.isFile) {
                file.copyTo(target, overwrite = true)
                file.delete()
            } else if (file.isDirectory) {
                moveDir(file, target)
            }
        }
        return deleteDir(srcDir)
    }
}

object ImageUtils {
    fun isEmptyBitmap(bitmap: Bitmap?): Boolean {
        return bitmap == null || bitmap.width == 0 || bitmap.height == 0
    }

    fun clip(src: Bitmap?, x: Int, y: Int, width: Int, height: Int): Bitmap? {
        if (isEmptyBitmap(src)) return null
        return Bitmap.createBitmap(src!!, x, y, width, height)
    }

    fun rotate(src: Bitmap?, degrees: Int, px: Float, py: Float): Bitmap? {
        if (isEmptyBitmap(src)) return null
        if (degrees == 0) return src
        val matrix = Matrix()
        matrix.setRotate(degrees.toFloat(), px, py)
        return Bitmap.createBitmap(src!!, 0, 0, src.width, src.height, matrix, true)
    }

    fun save(
        src: Bitmap?,
        filePath: String?,
        format: Bitmap.CompressFormat,
        quality: Int = 100,
        recycle: Boolean = false
    ): Boolean {
        if (isEmptyBitmap(src) || filePath.isNullOrBlank()) return false
        val file = File(filePath)
        if (file.exists() && !file.delete()) return false
        if (!FileUtils.createOrExistsDir(file.parentFile)) return false
        return try {
            if (!file.createNewFile()) return false
            BufferedOutputStream(FileOutputStream(file)).use { os ->
                val ok = src!!.compress(format, quality, os)
                if (recycle && !src.isRecycled) {
                    src.recycle()
                }
                ok
            }
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun decodeBounds(context: Context, pathOrUri: String, options: BitmapFactory.Options): Bitmap? {
        return try {
            val file = File(pathOrUri)
            if (file.exists() && file.canRead()) {
                BitmapFactory.decodeFile(file.absolutePath, options)
            } else {
                val uri = Uri.parse(pathOrUri)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decodeBitmap(context: Context, pathOrUri: String, sampleSize: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
            }
            val file = File(pathOrUri)
            if (file.exists() && file.canRead()) {
                BitmapFactory.decodeFile(file.absolutePath, options)
            } else {
                val uri = Uri.parse(pathOrUri)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
