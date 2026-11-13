package com.craxiom.networksurvey.util

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import com.craxiom.networksurvey.R

/**
 * 运行时主题色解析器：根据用户在设置页选择的自定义主题色（theme_seed）+ 深浅模式，
 * 返回对应的 XML 肤色。供传统 View / XML 体系（设置页、服务小区详情卡片头部带状、进度条、
 * 标题文字等）使用，使这些控件也能跟随自定义主题色，而不只是 Compose 层。
 *
 * 与 Compose 侧 ui/theme/Color.kt 的 seed 色板保持同一套色值（primary 主色）。
 */
object ThemeColorResolver {

    /** 返回当前主题种子：0 默认青蓝 / 1 蓝 / 2 绿 / 3 紫 / 4 橙。 */
    @JvmStatic fun seed(context: Context): Int = ThemePref.getSeed(context)

    private fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    /**
     * 解析主色（primary）。XML 层用它覆盖卡片头部带状 / 进度条 / 标题强调色等。
     */
    @JvmStatic fun accentColor(context: Context): Int {
        return when (seed(context)) {
            ThemePref.SEED_BLUE -> resolve(context, R.color.seed_blue_primary, R.color.seed_blue_primary_dark)
            ThemePref.SEED_GREEN -> resolve(context, R.color.seed_green_primary, R.color.seed_green_primary_dark)
            ThemePref.SEED_PURPLE -> resolve(context, R.color.seed_purple_primary, R.color.seed_purple_primary_dark)
            ThemePref.SEED_ORANGE -> resolve(context, R.color.seed_orange_primary, R.color.seed_orange_primary_dark)
            else -> resolve(context, R.color.seed_teal_primary, R.color.seed_teal_primary_dark)
        }
    }

    /** 解析卡片头部带状色（对应 Compose 的 primaryContainer，浅色/深色分别取对应值）。 */
    @JvmStatic fun cardHeaderColor(context: Context): Int {
        return when (seed(context)) {
            ThemePref.SEED_BLUE -> resolve(context, R.color.seed_blue_container, R.color.seed_blue_container_dark)
            ThemePref.SEED_GREEN -> resolve(context, R.color.seed_green_container, R.color.seed_green_container_dark)
            ThemePref.SEED_PURPLE -> resolve(context, R.color.seed_purple_container, R.color.seed_purple_container_dark)
            ThemePref.SEED_ORANGE -> resolve(context, R.color.seed_orange_container, R.color.seed_orange_container_dark)
            else -> resolve(context, R.color.seed_teal_container, R.color.seed_teal_container_dark)
        }
    }

    /** 卡片头部带状上的前景色（对应 Compose 的 onPrimaryContainer）。 */
    @JvmStatic fun cardHeaderForegroundColor(context: Context): Int {
        return resolve(context, R.color.seed_on_container, R.color.seed_on_container)
    }

    private fun resolve(context: Context, lightRes: Int, darkRes: Int): Int {
        return ContextCompat.getColor(context, if (isNight(context)) darkRes else lightRes)
    }
}
