package com.craxiom.networksurvey.ui.main.appdrawer

import android.graphics.Canvas
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.craxiom.networksurvey.R
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * KernelSU 风格现代侧边栏：
 * 具有品牌头部、卡片化分组、圆角条目和清晰的排版
 */
@Composable
fun <T : Enum<T>> AppDrawerContent(
    appVersion: String,
    drawerState: DrawerState,
    menuItems: List<DrawerEntry<T>>,
    externalLinks: List<AppDrawerItemInfo<T>>,
    defaultPick: T,
    onClick: (T) -> Unit
) {
    var currentPick by remember { mutableStateOf(defaultPick) }
    val coroutineScope = rememberCoroutineScope()

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 16.dp)
        ) {
            // Header: App Icon & Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                AppIcon()
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Wireless Signal Meter",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Menu Items List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.Start
            ) {
                items(menuItems) { entry ->
                    when (entry) {
                        is DrawerEntry.Header -> SectionHeader(titleResId = entry.titleResId)
                        is DrawerEntry.Item -> AppDrawerItem(item = entry.info) { navOption ->
                            currentPick = navOption
                            coroutineScope.launch {
                                drawerState.close()
                            }
                            onClick(navOption)
                        }
                    }
                }

                item {
                    SectionHeader(titleResId = R.string.nav_section_help_resources)
                }

                items(externalLinks) { item ->
                    AppDrawerItem(item = item) { navOption ->
                        currentPick = navOption
                        coroutineScope.launch {
                            drawerState.close()
                        }
                        onClick(navOption)
                    }
                }
            }

            // App Version
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "v$appVersion",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(@StringRes titleResId: Int) {
    Column(modifier = Modifier.padding(top = 18.dp, bottom = 6.dp, start = 12.dp)) {
        Text(
            text = stringResource(id = titleResId),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() }
        )
    }
}

@Composable
fun AppIcon() {
    val context = LocalContext.current
    val iconBitmap: ImageBitmap? = remember(context) {
        try {
            val drawable = ResourcesCompat.getDrawable(
                context.resources,
                R.mipmap.ic_launcher,
                context.theme
            ) ?: return@remember null

            val width = drawable.intrinsicWidth.coerceAtLeast(1)
            val height = drawable.intrinsicHeight.coerceAtLeast(1)
            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (t: Throwable) {
            Timber.w(t, "Failed to load launcher icon for drawer")
            null
        }
    }

    iconBitmap?.let { bitmap ->
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = "App Icon",
                modifier = Modifier.requiredSize(36.dp)
            )
        }
    }
}
