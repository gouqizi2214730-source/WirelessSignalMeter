package com.craxiom.networksurvey.update

/**
 * 测速节点（v3.74 起从服务器下发，无内置预设兜底）。
 */
data class SpeedNode(
    val name: String,
    val url: String
)
