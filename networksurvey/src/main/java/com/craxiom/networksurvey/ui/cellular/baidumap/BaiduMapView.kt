package com.craxiom.networksurvey.ui.cellular.baidumap

import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.map.TextureMapView
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.util.AppLogger

/**
 * Initializes the SDK and creates its view on the UI thread, in that order.
 * Link/load failures are logged and leave the rest of the app usable.
 */
@Composable
internal fun BaiduMapView(
    modifier: Modifier = Modifier,
    onCreate: (TextureMapView) -> Unit,
    onRelease: (TextureMapView) -> Unit,
) {
    var view by remember { mutableStateOf<TextureMapView?>(null) }
    var failed by remember { mutableStateOf(false) }
    val release by rememberUpdatedState(onRelease)
    val owner = LocalLifecycleOwner.current

    fun mapOperation(operation: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            AppLogger.e("BaiduMap", operation, e)
            failed = true
        } catch (e: LinkageError) {
            AppLogger.e("BaiduMap", operation, e)
            failed = true
        }
    }

    Box(modifier) {
        if (failed) {
            Text(
                stringResource(R.string.map_unavailable),
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            AndroidView(
                factory = { context ->
                    FrameLayout(context).apply {
                        mapOperation("Map initialization failed") {
                            val app = context.applicationContext
                            if (!SDKInitializer.isInitialized()) {
                                SDKInitializer.setAgreePrivacy(app, true)
                                SDKInitializer.initialize(app)
                                SDKInitializer.setCoordType(CoordType.BD09LL)
                            }
                            check(SDKInitializer.isInitialized()) { "Map SDK initialization incomplete" }
                            val created = TextureMapView(context)
                            // Retain partially configured views so onRelease can destroy them too.
                            view = created
                            addView(created, FrameLayout.LayoutParams(-1, -1))
                            onCreate(created)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { container ->
                    val current = view
                    view = null
                    if (current != null) {
                        try {
                            release(current)
                        } finally {
                            mapOperation("Map destruction failed") { current.onDestroy() }
                            container.removeAllViews()
                        }
                    }
                },
            )
        }
    }

    DisposableEffect(view, owner) {
        val current = view
        val observer = LifecycleEventObserver { _, event ->
            // onRelease may run before this observer is detached.
            if (current != null && view === current) {
                when (event) {
                    Lifecycle.Event.ON_RESUME ->
                        mapOperation("Map resume failed") { current.onResume() }
                    Lifecycle.Event.ON_PAUSE ->
                        mapOperation("Map pause failed") { current.onPause() }
                    else -> Unit
                }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
