package com.craxiom.networksurvey.ui.kernelsu

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported

/**
 * 液态透镜折射效果（SDF + 色散）
 */
fun BackdropEffectScope.lens(
    refractionHeight: Float = 20f,
    refractionAmount: Float = 15f,
    depthEffect: Boolean = true,
    chromaticAberration: Float = 0f
) {
    if (!isRuntimeShaderSupported() || refractionHeight <= 0 || refractionAmount <= 0) return
    if (padding < refractionAmount) {
        padding = refractionAmount
    }
}
