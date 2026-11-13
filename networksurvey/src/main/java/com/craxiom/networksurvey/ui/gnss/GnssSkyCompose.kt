package com.craxiom.networksurvey.ui.gnss

import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.craxiom.networksurvey.gpstest.ui.gnss.model.SignalInfoViewModel
import com.craxiom.networksurvey.ui.theme.NsTheme

/**
 * Java 友好的桥，将 GNSS 天空图 Compose 界面装入 Fragment 的 [ComposeView]。
 * 卫星星座/ C·N0 指示条/图例等上游自绘 View 由 Fragment 同步 inflate 后通过 [legacyView] 提供，
 * 本桥只负责 NsTheme 包裹与生命周期策略。
 */
object GnssSkyCompose {

    @JvmStatic
    fun setContent(
        composeView: ComposeView,
        viewModel: SignalInfoViewModel,
        legacyView: () -> View?,
    ) {
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            NsTheme {
                GnssSkyScreen(viewModel = viewModel, legacyView = legacyView)
            }
        }
    }
}
