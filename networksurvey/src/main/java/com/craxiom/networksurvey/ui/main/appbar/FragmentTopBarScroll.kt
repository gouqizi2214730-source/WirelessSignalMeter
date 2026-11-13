package com.craxiom.networksurvey.ui.main.appbar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll

/** Connects a fragment's Compose content to the top app bar in its host composition. */
class FragmentTopBarScroll {
    var connection by mutableStateOf<NestedScrollConnection?>(null)
}

internal fun Modifier.fragmentTopBarScroll(scroll: FragmentTopBarScroll?): Modifier =
    scroll?.connection?.let { nestedScroll(it) } ?: this
