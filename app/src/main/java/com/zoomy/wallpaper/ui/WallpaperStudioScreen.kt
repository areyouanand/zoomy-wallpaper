package com.zoomy.wallpaper.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.zoomy.wallpaper.encoder.WallpaperRenderSpec
import com.zoomy.wallpaper.encoder.WallpaperVideoGenerator
import com.zoomy.wallpaper.model.BezierControlPoints
import com.zoomy.wallpaper.model.WallpaperPreferences
import com.zoomy.wallpaper.util.StackBlurEngine
import com.zoomy.wallpaper.util.ToastUtils
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WallpaperStudioScreen(
    previewBitmap: Bitmap?,
    hasExistingVideo: Boolean,
    screenAspect: Float,
    maxDeviceFps: Float,
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    onPickImage: () -> Unit,
    onImportSystemWallpaper: () -> Unit,
    onRotate90: () -> Unit,
    onOpenLiveWallpaperPicker: () -> Unit,
    onGenerateWallpaper: (
        spec: WallpaperRenderSpec,
        onProgress: (Int, Int, Long) -> Unit,
        onComplete: (Boolean) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { WallpaperPreferences.getInstance() }
    val coroutineScope = rememberCoroutineScope()

    var showSettingsPage by remember { mutableStateOf(false) }

    var curveType by remember { mutableIntStateOf(prefs.getCurveType()) }
    var customX1 by remember { mutableFloatStateOf(prefs.getBezierControlPoints().x1) }
    var customY1 by remember { mutableFloatStateOf(prefs.getBezierControlPoints().y1) }
    var customX2 by remember { mutableFloatStateOf(prefs.getBezierControlPoints().x2) }
    var customY2 by remember { mutableFloatStateOf(prefs.getBezierControlPoints().y2) }

    // Group 1: Triggers & Delay
    var triggerMode by remember { mutableIntStateOf(prefs.getTriggerMode()) } // 0 = Every Home Return, 1 = Unlock Only
    var delayTimeMs by remember { mutableIntStateOf(prefs.getDelayTime().toInt()) }

    // Group 3: Real-Time Engine & Interactivity
    var engineMode by remember { mutableIntStateOf(prefs.getEngineMode()) }   // 0 = Real-Time 120Hz, 1 = MP4 Video
    var parallaxPercent by remember { mutableIntStateOf(prefs.getParallaxPercent()) }
    var depthZoomEnabled by remember { mutableStateOf(prefs.isDepthZoomEnabled()) }

    var fps by remember { mutableFloatStateOf(prefs.getFps()) }
    var encodeMode by remember { mutableIntStateOf(prefs.getEncodeMode()) } // 1 = HEVC, 0 = AVC
    var zoomMode by remember { mutableIntStateOf(prefs.getZoomMode()) }     // 0 = Out->1x, 1 = 1x->In, 2 = Pulse
    var addFps by remember { mutableIntStateOf(prefs.getAddFps()) }
    var scalePercent by remember { mutableIntStateOf((prefs.getScaleSize() * 100f).roundToInt()) }
    var blurTimePercent by remember { mutableIntStateOf(prefs.getBlurTimePercent()) }
    var startBlur by remember { mutableIntStateOf(prefs.getStartBlur()) }
    var endBlur by remember { mutableIntStateOf(prefs.getEndBlur()) }
    var startDimPercent by remember { mutableIntStateOf(prefs.getStartDimPercent()) }
    var endDimPercent by remember { mutableIntStateOf(prefs.getEndDimPercent()) }

    // Group 2: Scrims, Color Bloom & Dark Mode Auto-Dim
    var topScrimPercent by remember { mutableIntStateOf(prefs.getTopScrimPercent()) }
    var bottomScrimPercent by remember { mutableIntStateOf(prefs.getBottomScrimPercent()) }
    var colorBloomPercent by remember { mutableIntStateOf(prefs.getColorBloomPercent()) }
    var darkModeExtraDimPercent by remember { mutableIntStateOf(prefs.getDarkModeExtraDimPercent()) }

    var bitRateProgress by remember { mutableIntStateOf(prefs.getBitRateProgress()) }
    var rewindOnHide by remember { mutableStateOf(prefs.isRewindOnHide()) }
    var debounceVisibility by remember { mutableStateOf(prefs.isDebounceVisibility()) }

    var panX by remember { mutableFloatStateOf(prefs.getPanX()) }
    var panY by remember { mutableFloatStateOf(prefs.getPanY()) }

    var showCurveSheet by remember { mutableStateOf(false) }

    val previewAnimProgress = remember { Animatable(1f) }
    var isPlayingPreview by remember { mutableStateOf(false) }

    var isGenerating by remember { mutableStateOf(false) }
    var genCurrentFrame by remember { mutableIntStateOf(0) }
    var genTotalFrames by remember { mutableIntStateOf(1) }
    var genElapsedMs by remember { mutableLongStateOf(0L) }

    fun saveAllPreferences() {
        prefs.setCurveType(curveType)
        prefs.setBezierControlPoints(BezierControlPoints(customX1, customY1, customX2, customY2))
        prefs.setEngineMode(engineMode)
        prefs.setTriggerMode(triggerMode)
        prefs.setDelayTime(delayTimeMs)
        prefs.setParallaxPercent(parallaxPercent)
        prefs.setDepthZoomEnabled(depthZoomEnabled)
        prefs.setFps(fps)
        prefs.setEncodeMode(encodeMode)
        prefs.setZoomMode(zoomMode)
        prefs.setAddFps(addFps)
        prefs.setScaleSize(scalePercent / 100f)
        prefs.setBlurTimePercent(blurTimePercent)
        prefs.setStartBlur(startBlur)
        prefs.setEndBlur(endBlur)
        prefs.setStartDimPercent(startDimPercent)
        prefs.setEndDimPercent(endDimPercent)
        prefs.setTopScrimPercent(topScrimPercent)
        prefs.setBottomScrimPercent(bottomScrimPercent)
        prefs.setColorBloomPercent(colorBloomPercent)
        prefs.setDarkModeExtraDimPercent(darkModeExtraDimPercent)
        prefs.setBitRateProgress(bitRateProgress)
        prefs.setRewindOnHide(rewindOnHide)
        prefs.setDebounceVisibility(debounceVisibility)
        prefs.setPanX(panX)
        prefs.setPanY(panY)
        prefs.notifyConfigUpdated()
    }

    fun reloadStateFromPreferences() {
        curveType = prefs.getCurveType()
        val cp = prefs.getBezierControlPoints()
        customX1 = cp.x1
        customY1 = cp.y1
        customX2 = cp.x2
        customY2 = cp.y2
        engineMode = prefs.getEngineMode()
        triggerMode = prefs.getTriggerMode()
        delayTimeMs = prefs.getDelayTime().toInt()
        parallaxPercent = prefs.getParallaxPercent()
        depthZoomEnabled = prefs.isDepthZoomEnabled()
        fps = prefs.getFps()
        encodeMode = prefs.getEncodeMode()
        zoomMode = prefs.getZoomMode()
        addFps = prefs.getAddFps()
        scalePercent = (prefs.getScaleSize() * 100f).roundToInt()
        blurTimePercent = prefs.getBlurTimePercent()
        startBlur = prefs.getStartBlur()
        endBlur = prefs.getEndBlur()
        startDimPercent = prefs.getStartDimPercent()
        endDimPercent = prefs.getEndDimPercent()
        topScrimPercent = prefs.getTopScrimPercent()
        bottomScrimPercent = prefs.getBottomScrimPercent()
        colorBloomPercent = prefs.getColorBloomPercent()
        darkModeExtraDimPercent = prefs.getDarkModeExtraDimPercent()
        bitRateProgress = prefs.getBitRateProgress()
        rewindOnHide = prefs.isRewindOnHide()
        debounceVisibility = prefs.isDebounceVisibility()
        panX = prefs.getPanX()
        panY = prefs.getPanY()
    }

    if (showSettingsPage) {
        BackHandler { showSettingsPage = false }
        SettingsScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            engineMode = engineMode,
            onEngineModeChange = {
                engineMode = it
                prefs.setEngineMode(it)
                prefs.notifyConfigUpdated()
            },
            rewindOnHide = rewindOnHide,
            onRewindOnHideChange = {
                rewindOnHide = it
                prefs.setRewindOnHide(it)
                prefs.notifyConfigUpdated()
            },
            debounceVisibility = debounceVisibility,
            onDebounceVisibilityChange = {
                debounceVisibility = it
                prefs.setDebounceVisibility(it)
                prefs.notifyConfigUpdated()
            },
            onResetAllDefaults = {
                prefs.resetAllToDefaults()
                reloadStateFromPreferences()
            },
            onBack = { showSettingsPage = false }
        )
        return
    }

    val croppedPreviewBitmap = remember(previewBitmap, panX, panY, screenAspect) {
        previewBitmap?.let { bmp ->
            WallpaperVideoGenerator.cropToScreenAspect(bmp, screenAspect, panX, panY)
        }
    }

    val preBlurredStartBitmap = remember(croppedPreviewBitmap, startBlur) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && croppedPreviewBitmap != null && startBlur > 0) {
            StackBlurEngine.fastBlurScaled(croppedPreviewBitmap, (startBlur / 3).coerceAtLeast(1))
        } else null
    }

    val currentSpec = remember(
        curveType, customX1, customY1, customX2, customY2,
        engineMode, triggerMode, delayTimeMs, parallaxPercent, depthZoomEnabled,
        encodeMode, fps, addFps, bitRateProgress, zoomMode,
        scalePercent, blurTimePercent, startBlur, endBlur,
        startDimPercent, endDimPercent, topScrimPercent, bottomScrimPercent,
        colorBloomPercent, darkModeExtraDimPercent, panX, panY
    ) {
        WallpaperRenderSpec(
            imagePath = "",
            rotationDegrees = 0,
            panX = panX,
            panY = panY,
            curveType = curveType,
            customCurve = BezierControlPoints(customX1, customY1, customX2, customY2),
            engineMode = engineMode,
            triggerMode = triggerMode,
            delayTimeMs = delayTimeMs,
            parallaxPercent = parallaxPercent,
            depthZoomEnabled = depthZoomEnabled,
            encodeMode = encodeMode,
            fps = fps,
            addFps = addFps,
            bitRateProgress = bitRateProgress,
            zoomMode = zoomMode,
            scaleSize = scalePercent / 100f,
            blurTimePercent = blurTimePercent,
            startBlur = startBlur,
            endBlur = endBlur,
            startDimPercent = startDimPercent,
            endDimPercent = endDimPercent,
            topScrimPercent = topScrimPercent,
            bottomScrimPercent = bottomScrimPercent,
            colorBloomPercent = colorBloomPercent,
            darkModeExtraDimPercent = darkModeExtraDimPercent
        )
    }

    fun triggerLivePreview() {
        if (croppedPreviewBitmap == null) return
        coroutineScope.launch {
            isPlayingPreview = true
            previewAnimProgress.snapTo(0f)
            val durationMs = ((currentSpec.totalFrames / currentSpec.fps) * 1000f).roundToInt().coerceIn(300, 4000)
            previewAnimProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
            )
            isPlayingPreview = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Zoomy Wallpaper",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (engineMode == 0) {
                                "Real-Time 120Hz Engine • Instant Apply"
                            } else {
                                "Hardware MediaCodec MP4 Engine"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsPage = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (engineMode == 0) {
                        // Real-Time 120Hz Engine: Instant Save + Apply
                        OutlinedButton(
                            onClick = {
                                saveAllPreferences()
                                ToastUtils.showShort("Settings applied live to active wallpaper!")
                            },
                            enabled = croppedPreviewBitmap != null,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Update Live")
                        }
                        Button(
                            onClick = {
                                saveAllPreferences()
                                onOpenLiveWallpaperPicker()
                            },
                            enabled = croppedPreviewBitmap != null,
                            modifier = Modifier.weight(1.3f),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Set Live Wallpaper")
                        }
                    } else {
                        // MP4 Video Engine
                        if (hasExistingVideo) {
                            OutlinedButton(
                                onClick = {
                                    saveAllPreferences()
                                    onOpenLiveWallpaperPicker()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 14.dp)
                            ) {
                                Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Apply Last")
                            }
                        }
                        Button(
                            onClick = {
                                saveAllPreferences()
                                isGenerating = true
                                genCurrentFrame = 0
                                genTotalFrames = currentSpec.totalFrames
                                genElapsedMs = 0L

                                onGenerateWallpaper(
                                    currentSpec,
                                    { frame, total, elapsed ->
                                        genCurrentFrame = frame
                                        genTotalFrames = total
                                        genElapsedMs = elapsed
                                    },
                                    {
                                        isGenerating = false
                                    }
                                )
                            },
                            enabled = croppedPreviewBitmap != null && !isGenerating,
                            modifier = Modifier.weight(1.3f),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (hasExistingVideo) "Regenerate MP4" else "Generate MP4")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Interactive Live Preview & Crop/Pan Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Interactive Wallpaper Preview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (croppedPreviewBitmap != null) {
                                    "Drag image to pan crop • Tap Preview to test effect"
                                } else {
                                    "Pick an image or import your system wallpaper"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (croppedPreviewBitmap != null) {
                            FilledTonalButton(
                                onClick = { triggerLivePreview() },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isPlayingPreview) "Playing..." else "Preview")
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .height(290.dp)
                            .aspectRatio(screenAspect.coerceIn(0.42f, 0.65f))
                            .clip(RoundedCornerShape(22.dp))
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(22.dp)
                            )
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(croppedPreviewBitmap) {
                                if (croppedPreviewBitmap == null) return@pointerInput
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    panX = (panX - dragAmount.x / 250f).coerceIn(-1f, 1f)
                                    panY = (panY - dragAmount.y / 250f).coerceIn(-1f, 1f)
                                }
                            }
                            .clickable(enabled = croppedPreviewBitmap == null) {
                                onPickImage()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (croppedPreviewBitmap != null) {
                            AnimatedWallpaperPreviewBox(
                                bitmap = croppedPreviewBitmap,
                                preBlurredStartBitmap = preBlurredStartBitmap,
                                rawProgress = previewAnimProgress.value,
                                spec = currentSpec,
                                isDarkSystem = WallpaperVideoGenerator.isSystemInDarkMode(context)
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Tap to choose image",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = onPickImage,
                            label = { Text("Gallery") },
                            leadingIcon = {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        AssistChip(
                            onClick = onImportSystemWallpaper,
                            label = { Text("System Wallpaper") },
                            leadingIcon = {
                                Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        if (croppedPreviewBitmap != null) {
                            AssistChip(
                                onClick = onRotate90,
                                label = { Text("Rotate 90°") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )
                            if (panX != 0f || panY != 0f) {
                                AssistChip(
                                    onClick = {
                                        panX = 0f
                                        panY = 0f
                                    },
                                    label = { Text("Center Crop") },
                                    leadingIcon = {
                                        Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 1.5 Presets Card (Beginner-friendly one-tap animation/effect feels)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Animation & Effect Presets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "One-tap bundles of curve, zoom, blur, dimming and scrims for different feels",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val presets = listOf(
                        "iOS Classic" to {
                            curveType = 0
                            zoomMode = 0
                            fps = 120f
                            addFps = 60
                            encodeMode = 1
                            scalePercent = 30
                            blurTimePercent = 50
                            startBlur = 180
                            endBlur = 0
                            startDimPercent = 10
                            endDimPercent = 0
                            colorBloomPercent = 50
                            parallaxPercent = 35
                            depthZoomEnabled = true
                            delayTimeMs = 0
                            triggerMode = 0
                            topScrimPercent = 40
                            bottomScrimPercent = 40
                            darkModeExtraDimPercent = 0
                            bitRateProgress = 40
                            customX1 = 0.3f
                            customY1 = 1.0f
                            customX2 = 0.3f
                            customY2 = 0.0f
                        },
                        "Cinematic" to {
                            curveType = 0
                            zoomMode = 0
                            scalePercent = 25
                            blurTimePercent = 80
                            startBlur = 180
                            endBlur = 20
                            startDimPercent = 30
                            endDimPercent = 10
                            colorBloomPercent = 60
                            parallaxPercent = 50
                            depthZoomEnabled = true
                            delayTimeMs = 100
                            triggerMode = 0
                            topScrimPercent = 40
                            bottomScrimPercent = 50
                            darkModeExtraDimPercent = 20
                        },
                        "Minimal" to {
                            curveType = 1
                            zoomMode = 0
                            scalePercent = 8
                            blurTimePercent = 30
                            startBlur = 100
                            endBlur = 0
                            startDimPercent = 0
                            endDimPercent = 0
                            colorBloomPercent = 0
                            parallaxPercent = 20
                            depthZoomEnabled = false
                            delayTimeMs = 0
                            triggerMode = 0
                            topScrimPercent = 0
                            bottomScrimPercent = 0
                            darkModeExtraDimPercent = 0
                        },
                        "AMOLED Dream" to {
                            curveType = 2
                            zoomMode = 2
                            scalePercent = 20
                            blurTimePercent = 60
                            startBlur = 180
                            endBlur = 30
                            startDimPercent = 20
                            endDimPercent = 15
                            colorBloomPercent = 80
                            parallaxPercent = 40
                            depthZoomEnabled = true
                            delayTimeMs = 50
                            triggerMode = 0
                            topScrimPercent = 30
                            bottomScrimPercent = 40
                            darkModeExtraDimPercent = 40
                        },
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { (name, apply) ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    apply()
                                    saveAllPreferences()
                                    ToastUtils.showShort("Preset applied: $name")
                                },
                                label = { Text(name) }
                            )
                        }
                    }
                }
            }

            // 2. Group 1 & 3: Smart Triggers, Engine Mode & Launcher Interactivity Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Trigger & Real-Time Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Engine Mode",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = engineMode == 0,
                            onClick = { engineMode = 0 },
                            label = {
                                Text(
                                    "Real-Time 120Hz (Instant)",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = engineMode == 1,
                            onClick = { engineMode = 1 },
                            label = {
                                Text(
                                    "MP4 Video Engine",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "Animation Trigger Condition",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = triggerMode == 0,
                            onClick = { triggerMode = 0 },
                            label = {
                                Text(
                                    "Every Home Return",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = triggerMode == 1,
                            onClick = { triggerMode = 1 },
                            label = {
                                Text(
                                    "Device Unlock Only",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    ParameterSlider(
                        title = "Playback Start Delay",
                        valueLabel = "${delayTimeMs} ms",
                        value = delayTimeMs.toFloat(),
                        valueRange = 0f..500f,
                        onValueChange = { delayTimeMs = (it / 5f).roundToInt() * 5 },
                        onReset = { delayTimeMs = 0 }
                    )

                    if (engineMode == 0) {
                        HorizontalDivider()

                        ParameterSlider(
                            title = "Home Screen Swipe Parallax",
                            valueLabel = if (parallaxPercent == 0) "Off" else "$parallaxPercent%",
                            value = parallaxPercent.toFloat(),
                            valueRange = 0f..100f,
                            onValueChange = { parallaxPercent = (it / 5f).roundToInt() * 5 },
                            onReset = { parallaxPercent = 35 }
                        )
                    }
                }
            }

            // 3. Animation Curve & Zoom Motion Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Animation Curve & Zoom Motion",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Surface(
                        onClick = { showCurveSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (curveType) {
                                        0 -> "Mode 1: iOS Spring Ease-Out (0.16, 1.0, 0.30, 1.0)"
                                        1 -> "Mode 2: Linear-Out Slow-In"
                                        2 -> "Mode 3: Fast-Out Slow-In"
                                        else -> "Custom Cubic Bézier (${(customX1 * 100).roundToInt()}, ${(customY1 * 100).roundToInt()}, ${(customX2 * 100).roundToInt()}, ${(customY2 * 100).roundToInt()})"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Tap to open interactive curve editor & live preview",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.Tune, contentDescription = "Edit Curve")
                        }
                    }

                    Text(
                        text = "Zoom Direction",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = zoomMode == 0,
                            onClick = { zoomMode = 0 },
                            label = { Text("Zoom Out → 1× (iOS)") }
                        )
                        FilterChip(
                            selected = zoomMode == 1,
                            onClick = { zoomMode = 1 },
                            label = { Text("1× → Zoom In") }
                        )
                        FilterChip(
                            selected = zoomMode == 2,
                            onClick = { zoomMode = 2 },
                            label = { Text("Zoom Pulse") }
                        )
                    }

                    HorizontalDivider()

                    val durationMs = ((currentSpec.totalFrames / fps) * 1000f).roundToInt()
                    ParameterSlider(
                        title = "Animation Duration",
                        valueLabel = "${durationMs} ms",
                        value = addFps.toFloat(),
                        valueRange = 0f..120f,
                        onValueChange = { addFps = (it / 5f).roundToInt() * 5 },
                        onReset = { addFps = 60 }
                    )

                    ParameterSlider(
                        title = "Zoom Scale",
                        valueLabel = "$scalePercent%",
                        value = scalePercent.toFloat(),
                        valueRange = 0f..50f,
                        onValueChange = { scalePercent = (it / 5f).roundToInt() * 5 },
                        onReset = { scalePercent = 20 }
                    )
                }
            }

            // 4. Group 2: Blur, iOS Color Wake, Dimming & Edge Scrims Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Blur, Color Wake & Scrims",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    ParameterSlider(
                        title = "Gaussian Blur Duration",
                        valueLabel = "$blurTimePercent%",
                        value = blurTimePercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { blurTimePercent = (it / 5f).roundToInt() * 5 },
                        onReset = { blurTimePercent = 50 }
                    )

                    ParameterSlider(
                        title = "Start Blur Intensity",
                        valueLabel = "$startBlur",
                        value = startBlur.toFloat(),
                        valueRange = 0f..180f,
                        onValueChange = { startBlur = (it / 5f).roundToInt() * 5 },
                        onReset = { startBlur = 180 }
                    )

                    ParameterSlider(
                        title = "Resting Home Screen Blur (iOS Home Blur)",
                        valueLabel = if (endBlur == 0) "Off (0)" else "$endBlur",
                        value = endBlur.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { endBlur = (it / 5f).roundToInt() * 5 },
                        onReset = { endBlur = 0 }
                    )

                    ParameterSlider(
                        title = "iOS Color Wake (Saturation & Brightness Bloom)",
                        valueLabel = if (colorBloomPercent == 0) "Off" else "$colorBloomPercent%",
                        value = colorBloomPercent.toFloat(),
                        valueRange = 0f..100f,
                        onValueChange = { colorBloomPercent = (it / 5f).roundToInt() * 5 },
                        onReset = { colorBloomPercent = 30 }
                    )

                    HorizontalDivider()

                    ParameterSlider(
                        title = "Start Unlock Dimming",
                        valueLabel = "$startDimPercent%",
                        value = startDimPercent.toFloat(),
                        valueRange = 0f..60f,
                        onValueChange = { startDimPercent = (it / 5f).roundToInt() * 5 },
                        onReset = { startDimPercent = 0 }
                    )

                    ParameterSlider(
                        title = "Resting Home Screen Dimming",
                        valueLabel = "$endDimPercent%",
                        value = endDimPercent.toFloat(),
                        valueRange = 0f..60f,
                        onValueChange = { endDimPercent = (it / 5f).roundToInt() * 5 },
                        onReset = { endDimPercent = 0 }
                    )

                    ParameterSlider(
                        title = "Auto Extra Dim in System Dark Mode",
                        valueLabel = if (darkModeExtraDimPercent == 0) "Off" else "+$darkModeExtraDimPercent%",
                        value = darkModeExtraDimPercent.toFloat(),
                        valueRange = 0f..60f,
                        onValueChange = { darkModeExtraDimPercent = (it / 5f).roundToInt() * 5 },
                        onReset = { darkModeExtraDimPercent = 0 }
                    )

                    HorizontalDivider()

                    ParameterSlider(
                        title = "Top Status Bar Scrim Gradient",
                        valueLabel = if (topScrimPercent == 0) "Off" else "$topScrimPercent%",
                        value = topScrimPercent.toFloat(),
                        valueRange = 0f..80f,
                        onValueChange = { topScrimPercent = (it / 5f).roundToInt() * 5 },
                        onReset = { topScrimPercent = 0 }
                    )

                    ParameterSlider(
                        title = "Bottom Dock Scrim Gradient",
                        valueLabel = if (bottomScrimPercent == 0) "Off" else "$bottomScrimPercent%",
                        value = bottomScrimPercent.toFloat(),
                        valueRange = 0f..80f,
                        onValueChange = { bottomScrimPercent = (it / 5f).roundToInt() * 5 },
                        onReset = { bottomScrimPercent = 0 }
                    )
                }
            }

            // 5. MP4 Video Encoder Card (Shown when MP4 Video Engine is active)
            if (engineMode == 1) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "MP4 Video Encoder Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "Target Frame Rate (Display supports up to ${maxDeviceFps.roundToInt()}Hz)",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(60f, 90f, 120f).forEach { rate ->
                                FilterChip(
                                    selected = fps == rate,
                                    onClick = { fps = rate },
                                    label = { Text("${rate.roundToInt()} FPS") }
                                )
                            }
                        }

                        Text(
                            text = "Hardware Video Codec",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = encodeMode == 1,
                                onClick = { encodeMode = 1 },
                                label = { Text("HEVC (H.265)") }
                            )
                            FilterChip(
                                selected = encodeMode == 0,
                                onClick = { encodeMode = 0 },
                                label = { Text("AVC (H.264)") }
                            )
                        }

                        val mbps = currentSpec.calculateBitRateBps() / 1_000_000f
                        ParameterSlider(
                            title = "Video Bitrate",
                            valueLabel = String.format(Locale.US, "%.1f Mbps", mbps),
                            value = bitRateProgress.toFloat(),
                            valueRange = 0f..40f,
                            onValueChange = { bitRateProgress = ((it / 5f).roundToInt() * 5).coerceAtLeast(1) },
                            onReset = { bitRateProgress = 20 }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCurveSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCurveSheet = false },
            sheetState = sheetState
        ) {
            CurveEditorSheetContent(
                croppedPreviewBitmap = croppedPreviewBitmap,
                preBlurredStartBitmap = preBlurredStartBitmap,
                screenAspect = screenAspect,
                spec = currentSpec,
                isDarkSystem = WallpaperVideoGenerator.isSystemInDarkMode(context),
                curveType = curveType,
                x1 = customX1,
                y1 = customY1,
                x2 = customX2,
                y2 = customY2,
                onSelectPreset = { preset -> curveType = preset },
                onCustomChange = { nx1, ny1, nx2, ny2 ->
                    curveType = 3
                    customX1 = nx1
                    customY1 = ny1
                    customX2 = nx2
                    customY2 = ny2
                },
                onDone = { showCurveSheet = false }
            )
        }
    }

    if (isGenerating) {
        val pct = ((genCurrentFrame.toFloat() / genTotalFrames.coerceAtLeast(1)) * 100f).roundToInt().coerceIn(0, 100)
        val elapsedSec = genElapsedMs / 1000f
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Generating Live Wallpaper") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier.size(72.dp),
                            strokeWidth = 6.dp
                        )
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Frame $genCurrentFrame / $genTotalFrames • ${String.format(Locale.US, "%.1fs", elapsedSec)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Multi-core StackBlur + Hardware ${if (encodeMode == 1) "HEVC" else "AVC"} @ ${fps.roundToInt()} FPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun AnimatedWallpaperPreviewBox(
    bitmap: Bitmap,
    preBlurredStartBitmap: Bitmap?,
    rawProgress: Float,
    spec: WallpaperRenderSpec,
    isDarkSystem: Boolean
) {
    val curveInterpolator = remember(spec.curveType, spec.customCurve) {
        spec.createInterpolator()
    }
    val curveProgress = curveInterpolator.getInterpolation(rawProgress.coerceIn(0f, 1f)).coerceIn(0f, 1f)

    val insetFactor = WallpaperVideoGenerator.computeZoomInsetFactor(
        zoomMode = spec.zoomMode,
        scaleSize = spec.scaleSize,
        curveProgress = curveProgress
    )
    val visualScale = (1f / (1f - insetFactor * 1.5f).coerceAtLeast(0.4f)).coerceIn(1f, 2.2f)

    val blurFraction = (spec.blurTimePercent / 100f).coerceIn(0.01f, 1f)
    val blurRemaining = if (rawProgress < blurFraction) {
        val bt = (rawProgress / blurFraction).coerceIn(0f, 1f)
        (1f - bt)
    } else {
        0f
    }
    val blurRadiusVal = spec.endBlur + (spec.startBlur - spec.endBlur) * blurRemaining
    val blurDp = (blurRadiusVal / 7.5f).dp

    val extraNightDim = if (isDarkSystem) spec.darkModeExtraDimPercent else 0
    val effectiveEndDim = (spec.endDimPercent + extraNightDim).coerceIn(0, 85) / 100f
    val effectiveStartDim = max(spec.startDimPercent / 100f, effectiveEndDim)
    val currentDim = (effectiveStartDim + (effectiveEndDim - effectiveStartDim) * curveProgress).coerceIn(0f, 0.85f)

    val (sat, bright) = WallpaperVideoGenerator.computeColorBloom(
        colorBloomPercent = spec.colorBloomPercent,
        blurFractionRemaining = blurRemaining
    )
    val colorFilter = remember(sat, bright) {
        if (sat == 1f && bright == 0f) {
            null
        } else {
            val cm = ColorMatrix().apply {
                setToSaturation(sat)
                if (bright != 0f) {
                    val shift = ColorMatrix(
                        floatArrayOf(
                            1f, 0f, 0f, 0f, bright,
                            0f, 1f, 0f, 0f, bright,
                            0f, 0f, 1f, 0f, bright,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                    timesAssign(shift)
                }
            }
            ColorFilter.colorMatrix(cm)
        }
    }

    val topScrimAlpha = (spec.topScrimPercent / 100f).coerceIn(0f, 0.85f)
    val bottomScrimAlpha = (spec.bottomScrimPercent / 100f).coerceIn(0f, 0.85f)

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Wallpaper Preview",
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = visualScale
                    scaleY = visualScale
                }
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadiusVal > 0.5f) {
                        Modifier.blur(blurDp)
                    } else {
                        Modifier
                    }
                )
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && preBlurredStartBitmap != null && blurRadiusVal > 1f) {
            val blurAlpha = (blurRadiusVal / spec.startBlur.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            Image(
                bitmap = preBlurredStartBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                alpha = blurAlpha,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = visualScale
                        scaleY = visualScale
                    }
            )
        }
        if (currentDim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = currentDim))
            )
        }
        if (topScrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.22f)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = topScrimAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        if (bottomScrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.26f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = bottomScrimAlpha)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun ParameterSlider(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    steps: Int = 0
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            FilledTonalIconButton(
                onClick = onReset,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset $title",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = if (steps > 0) steps else {
                (((valueRange.endInclusive - valueRange.start) / 5f).roundToInt() - 1).coerceAtLeast(0)
            }
        )
    }
}

@Composable
private fun CurveEditorSheetContent(
    croppedPreviewBitmap: Bitmap?,
    preBlurredStartBitmap: Bitmap?,
    screenAspect: Float,
    spec: WallpaperRenderSpec,
    isDarkSystem: Boolean,
    curveType: Int,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    onSelectPreset: (Int) -> Unit,
    onCustomChange: (Float, Float, Float, Float) -> Unit,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetAnimProgress = remember { Animatable(1f) }
    var isPlayingCurve by remember { mutableStateOf(false) }

    fun playCurveAnimation() {
        scope.launch {
            isPlayingCurve = true
            sheetAnimProgress.snapTo(0f)
            val durationMs = ((spec.totalFrames / spec.fps) * 1000f).roundToInt().coerceIn(300, 4000)
            sheetAnimProgress.animateTo(1f, tween(durationMs, easing = LinearEasing))
            isPlayingCurve = false
        }
    }

    LaunchedEffect(curveType) {
        playCurveAnimation()
    }

    val interpolator = remember(curveType, x1, y1, x2, y2) {
        spec.createInterpolator()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Animation Curve Studio",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Customize speed progression & preview live",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDone) {
                Text("Done", fontWeight = FontWeight.SemiBold)
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val secondaryColor = MaterialTheme.colorScheme.tertiary
                    val gridColor = MaterialTheme.colorScheme.outlineVariant

                    Box(
                        modifier = Modifier
                            .size(172.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            for (i in 0..4) {
                                val gx = (w / 4f) * i
                                val gy = (h / 4f) * i
                                drawLine(gridColor.copy(alpha = 0.5f), Offset(gx, 0f), Offset(gx, h), strokeWidth = 1.dp.toPx())
                                drawLine(gridColor.copy(alpha = 0.5f), Offset(0f, gy), Offset(w, gy), strokeWidth = 1.dp.toPx())
                            }

                            drawLine(
                                color = gridColor,
                                start = Offset(0f, h),
                                end = Offset(w, 0f),
                                strokeWidth = 1.2.dp.toPx()
                            )

                            val (cpX1, cpY1, cpX2, cpY2) = when (curveType) {
                                0 -> listOf(0.16f, 1.0f, 0.30f, 1.0f)
                                1 -> listOf(0.0f, 0.0f, 0.2f, 1.0f)
                                2 -> listOf(0.4f, 0.0f, 0.2f, 1.0f)
                                else -> listOf(x1, y1, x2, y2)
                            }
                            val p1Offset = Offset(cpX1 * w, h - cpY1 * h)
                            val p2Offset = Offset(cpX2 * w, h - cpY2 * h)
                            drawLine(secondaryColor.copy(alpha = 0.55f), Offset(0f, h), p1Offset, strokeWidth = 1.5.dp.toPx())
                            drawLine(secondaryColor.copy(alpha = 0.55f), Offset(w, 0f), p2Offset, strokeWidth = 1.5.dp.toPx())
                            drawCircle(secondaryColor, radius = 3.5.dp.toPx(), center = p1Offset)
                            drawCircle(secondaryColor, radius = 3.5.dp.toPx(), center = p2Offset)

                            val path = Path()
                            val steps = 80
                            for (step in 0..steps) {
                                val t = step.toFloat() / steps.toFloat()
                                val py = interpolator.getInterpolation(t).coerceIn(0f, 1f)
                                val px = t * w
                                val screenY = h - (py * h)
                                if (step == 0) path.moveTo(px, screenY) else path.lineTo(px, screenY)
                            }
                            drawPath(
                                path = path,
                                color = primaryColor,
                                style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                            )

                            val curT = sheetAnimProgress.value.coerceIn(0f, 1f)
                            val curY = interpolator.getInterpolation(curT).coerceIn(0f, 1f)
                            drawCircle(
                                color = primaryColor,
                                radius = 5.5.dp.toPx(),
                                center = Offset(curT * w, h - curY * h)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .height(172.dp)
                            .aspectRatio(screenAspect.coerceIn(0.44f, 0.58f))
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (croppedPreviewBitmap != null) {
                            AnimatedWallpaperPreviewBox(
                                bitmap = croppedPreviewBitmap,
                                preBlurredStartBitmap = preBlurredStartBitmap,
                                rawProgress = sheetAnimProgress.value,
                                spec = spec,
                                isDarkSystem = isDarkSystem
                            )
                        } else {
                            val prog = interpolator.getInterpolation(sheetAnimProgress.value).coerceIn(0f, 1f)
                            val scale = 1f - 0.35f * prog
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(0.75f)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                FilledTonalButton(
                    onClick = { playCurveAnimation() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isPlayingCurve) {
                            "Playing Preview (${(sheetAnimProgress.value * 100).roundToInt()}%)"
                        } else {
                            "Play Curve Preview"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Curve Preset",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = curveType == 0,
                    onClick = { onSelectPreset(0) },
                    label = {
                        Text(
                            "Mode 1 • iOS",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = curveType == 1,
                    onClick = { onSelectPreset(1) },
                    label = {
                        Text(
                            "Mode 2 • Smooth",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = curveType == 2,
                    onClick = { onSelectPreset(2) },
                    label = {
                        Text(
                            "Mode 3 • Fast-Out",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = curveType == 3,
                    onClick = { onSelectPreset(3) },
                    label = {
                        Text(
                            "Custom Bézier",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cubic Bézier Control Points",
                        style = MaterialTheme.typography.labelLarge
                    )
                    TextButton(
                        onClick = { onCustomChange(0.16f, 1.0f, 0.30f, 1.0f) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset P1/P2", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CompactControlSlider(
                        label = "X1",
                        value = x1,
                        onValueChange = { onCustomChange((it * 20f).roundToInt() / 20f, y1, x2, y2) },
                        modifier = Modifier.weight(1f)
                    )
                    CompactControlSlider(
                        label = "Y1",
                        value = y1,
                        onValueChange = { onCustomChange(x1, (it * 20f).roundToInt() / 20f, x2, y2) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CompactControlSlider(
                        label = "X2",
                        value = x2,
                        onValueChange = { onCustomChange(x1, y1, (it * 20f).roundToInt() / 20f, y2) },
                        modifier = Modifier.weight(1f)
                    )
                    CompactControlSlider(
                        label = "Y2",
                        value = y2,
                        onValueChange = { onCustomChange(x1, y1, x2, (it * 20f).roundToInt() / 20f) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactControlSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 19
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${(value * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            steps = steps
        )
    }
}
