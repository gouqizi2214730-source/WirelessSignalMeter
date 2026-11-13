package com.craxiom.networksurvey.ui.main.appbar

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.craxiom.networksurvey.R
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import androidx.compose.material3.MaterialTheme

/**
 * KernelSU 3.2.5 风格 Miuix TopAppBar。
 *
 * 使用 Miuix 组件库的 TopAppBar + MiuixScrollBehavior，
 * 替代原先的 Material3 TopAppBar。
 */
@Composable
fun AppBar(
    drawerState: DrawerState? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    @StringRes title: Int? = null,
    appBarActions: List<AppBarAction>? = null,
    scrollBehavior: ScrollBehavior? = null
) {
    TopAppBar(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        title = title?.let { stringResource(id = it) } ?: "",
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (drawerState != null && navigationIcon == null) {
                DrawerIcon(drawerState = drawerState)
            } else {
                navigationIcon?.invoke()
            }
        },
        actions = {
            appBarActions?.forEach { appBarAction ->
                AppBarAction(appBarAction)
            }
        }
    )
}

@Composable
private fun DrawerIcon(drawerState: DrawerState) {
    val coroutineScope = rememberCoroutineScope()
    IconButton(onClick = {
        coroutineScope.launch {
            drawerState.open()
        }
    }) {
        MaterialIcon(
            imageVector = Icons.Rounded.Menu,
            tint = MaterialTheme.colorScheme.onSurface,
            contentDescription = stringResource(id = R.string.drawer_menu_description)
        )
    }
}

@Composable
fun AppBarAction(appBarAction: AppBarAction) {
    IconButton(onClick = appBarAction.onClick) {
        Box {
            Icon(
                painter = painterResource(id = appBarAction.icon),
                modifier = Modifier.size(22.dp),
                tint = if (appBarAction.active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                contentDescription = stringResource(id = appBarAction.description)
            )
            if (appBarAction.active) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }
    }
}

@Composable
fun TitleBar(
    title: String,
    appBarActions: List<AppBarAction>? = null,
    onBackClick: () -> Unit
) {
    TopAppBar(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        title = title,
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(id = R.string.navigate_back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            appBarActions?.forEach { appBarAction ->
                AppBarAction(appBarAction)
            }
        }
    )
}
