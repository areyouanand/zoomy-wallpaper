package com.zoomy.wallpaper.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.zoomy.wallpaper.encoder.WallpaperRenderSpec
import com.zoomy.wallpaper.encoder.WallpaperVideoGenerator
import com.zoomy.wallpaper.model.WallpaperPreferences
import com.zoomy.wallpaper.service.ZoomyWallpaperService
import com.zoomy.wallpaper.ui.theme.ZoomyTheme
import com.zoomy.wallpaper.util.AppUtils
import com.zoomy.wallpaper.util.FileUtils
import com.zoomy.wallpaper.util.ImageUtils
import com.zoomy.wallpaper.util.PrefsStore
import com.zoomy.wallpaper.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private var imagePath: String = ""
    private var baseDirPath: String = ""
    private var videoDirPath: String = ""
    private var videoTempDirPath: String = ""
    private var rotationDegrees: Int = 0

    private var rawPreviewBitmap: Bitmap? = null
    private var displayedPreviewBitmap by mutableStateOf<Bitmap?>(null)
    private var hasExistingVideo by mutableStateOf(false)
    private var themeModeState by mutableIntStateOf(0)

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            handlePickedImageUri(uri)
        }
    }

    private val fallbackGetContentLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handlePickedImageUri(uri)
        }
    }

    private val storagePermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        importSystemWallpaperInternal()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val externalFilesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        baseDirPath = externalFilesDir?.absolutePath ?: filesDir.absolutePath
        FileUtils.deleteFilesInDir("$baseDirPath/temp")
        videoDirPath = "$baseDirPath/video/"
        videoTempDirPath = "$baseDirPath/videoTemp/"
        FileUtils.deleteFilesInDir(videoTempDirPath)
        FileUtils.createOrExistsDir(FileUtils.getFileByPath(videoDirPath))
        FileUtils.createOrExistsDir(FileUtils.getFileByPath(videoTempDirPath))

        themeModeState = WallpaperPreferences.getInstance().getThemeMode()
        hasExistingVideo = File(videoDirPath, "AnimVideo.mp4").exists()

        restoreSavedWallpaperPreview()

        val screenAspect = AppUtils.getScreenWidth().toFloat() / AppUtils.getScreenHeight().toFloat()
        val maxDeviceFps = WallpaperPreferences.getInstance().detectMaxDisplayFps()

        setContent {
            ZoomyTheme(themeMode = themeModeState) {
                WallpaperStudioScreen(
                    previewBitmap = displayedPreviewBitmap,
                    hasExistingVideo = hasExistingVideo,
                    screenAspect = screenAspect,
                    maxDeviceFps = maxDeviceFps,
                    themeMode = themeModeState,
                    onThemeModeChange = { newMode ->
                        themeModeState = newMode
                        WallpaperPreferences.getInstance().setThemeMode(newMode)
                    },
                    onPickImage = { launchPhotoPicker() },
                    onImportSystemWallpaper = { requestAndImportSystemWallpaper() },
                    onRotate90 = { rotatePreview(90) },
                    onOpenLiveWallpaperPicker = { openLiveWallpaperPicker() },
                    onGenerateWallpaper = { spec, onProgress, onComplete ->
                        generateWallpaperAsync(spec, onProgress, onComplete)
                    }
                )
            }
        }
    }

    private fun launchPhotoPicker() {
        try {
            pickMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (_: Exception) {
            fallbackGetContentLauncher.launch("image/*")
        }
    }

    private fun handlePickedImageUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val destFile = File(baseDirPath, "selected_wallpaper.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0L) {
                    imagePath = destFile.absolutePath
                    PrefsStore.getInstance().put("path", imagePath)
                    PrefsStore.getInstance().put("rotate", 0)
                    WallpaperPreferences.getInstance().setPanX(0f)
                    WallpaperPreferences.getInstance().setPanY(0f)
                    WallpaperPreferences.getInstance().notifyConfigUpdated()
                    loadPreviewFromPath(imagePath, initialRotate = 0)
                } else {
                    ToastUtils.showShort("Failed to copy selected image")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtils.showShort("Error loading image: ${e.message}")
            }
        }
    }

    private fun requestAndImportSystemWallpaper() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            importSystemWallpaperInternal()
        } else {
            storagePermLauncher.launch(perm)
        }
    }

    @SuppressLint("MissingPermission")
    private fun importSystemWallpaperInternal() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val wm = WallpaperManager.getInstance(this@MainActivity)
                val destFile = File(baseDirPath, "selected_wallpaper.jpg")
                var imported = false

                try {
                    wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { pfd ->
                        FileInputStream(pfd.fileDescriptor).use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        imported = destFile.exists() && destFile.length() > 0L
                    }
                } catch (_: Exception) {
                }

                if (!imported) {
                    val drawable: Drawable? = try {
                        wm.drawable
                    } catch (_: Exception) {
                        null
                    }
                    if (drawable != null) {
                        val bmp = drawableToBitmap(drawable)
                        imported = ImageUtils.save(
                            bmp,
                            destFile.absolutePath,
                            Bitmap.CompressFormat.JPEG,
                            98,
                            false
                        )
                    }
                }

                if (imported) {
                    imagePath = destFile.absolutePath
                    PrefsStore.getInstance().put("path", imagePath)
                    PrefsStore.getInstance().put("rotate", 0)
                    WallpaperPreferences.getInstance().setPanX(0f)
                    WallpaperPreferences.getInstance().setPanY(0f)
                    WallpaperPreferences.getInstance().notifyConfigUpdated()
                    loadPreviewFromPath(imagePath, initialRotate = 0)
                    ToastUtils.showShort("Imported current system wallpaper")
                } else {
                    ToastUtils.showShort("Could not read system wallpaper (Android restricts access when a live wallpaper is active; please pick from Gallery)")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ToastUtils.showShort("Could not read system wallpaper: ${e.message}")
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: AppUtils.getScreenWidth()
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: AppUtils.getScreenHeight()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun restoreSavedWallpaperPreview() {
        val savedPath = PrefsStore.getInstance().getString("path")
        val savedRotate = PrefsStore.getInstance().getInt("rotate", 0).coerceAtLeast(0)
        if (savedPath.isNotEmpty() && File(savedPath).exists()) {
            imagePath = savedPath
            lifecycleScope.launch(Dispatchers.IO) {
                loadPreviewFromPath(savedPath, savedRotate)
            }
        }
    }

    private suspend fun loadPreviewFromPath(path: String, initialRotate: Int) {
        val bmp = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                inJustDecodeBounds = true
            }
            ImageUtils.decodeBounds(this@MainActivity, path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null
            val targetPreviewWidth = (AppUtils.getScreenWidth() / 2).coerceAtLeast(540)
            val sampleSize = (options.outWidth / targetPreviewWidth).coerceAtLeast(1)
            ImageUtils.decodeBitmap(this@MainActivity, path, sampleSize)
        }
        if (bmp != null) {
            withContext(Dispatchers.Main) {
                rawPreviewBitmap = bmp
                rotationDegrees = initialRotate % 360
                displayedPreviewBitmap = if (rotationDegrees == 0) {
                    bmp
                } else {
                    ImageUtils.rotate(bmp, rotationDegrees, bmp.width / 2f, bmp.height / 2f) ?: bmp
                }
            }
        }
    }

    private fun rotatePreview(deltaDegrees: Int) {
        val raw = rawPreviewBitmap ?: return
        rotationDegrees = (rotationDegrees + deltaDegrees) % 360
        PrefsStore.getInstance().put("rotate", rotationDegrees)
        WallpaperPreferences.getInstance().notifyConfigUpdated()
        displayedPreviewBitmap = if (rotationDegrees == 0) {
            raw
        } else {
            ImageUtils.rotate(raw, rotationDegrees, raw.width / 2f, raw.height / 2f) ?: raw
        }
    }

    private fun generateWallpaperAsync(
        spec: WallpaperRenderSpec,
        onProgress: (Int, Int, Long) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        if (imagePath.isEmpty()) {
            ToastUtils.showShort("Please select a wallpaper first")
            onComplete(false)
            return
        }

        val fullSpec = spec.copy(
            imagePath = imagePath,
            rotationDegrees = rotationDegrees
        )

        PrefsStore.getInstance().put("path", imagePath)
        PrefsStore.getInstance().put("rotate", rotationDegrees)

        lifecycleScope.launch {
            val result = WallpaperVideoGenerator.generateVideo(
                context = this@MainActivity,
                spec = fullSpec,
                videoTempDir = File(videoTempDirPath),
                videoFinalDir = File(videoDirPath),
                onProgress = { frame, total, elapsed ->
                    runOnUiThread {
                        onProgress(frame, total, elapsed)
                    }
                }
            )
            result.onSuccess {
                hasExistingVideo = true
                onComplete(true)
                try {
                    WallpaperManager.getInstance(this@MainActivity).clear()
                } catch (_: Exception) {
                }
                openLiveWallpaperPicker()
            }.onFailure { err ->
                onComplete(false)
                ToastUtils.showLong("Failed to generate wallpaper: ${err.message}")
            }
        }
    }

    private fun openLiveWallpaperPicker() {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, ZoomyWallpaperService::class.java)
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            ToastUtils.showShort("Live wallpaper picker not found on this device: $e")
        }
    }
}
