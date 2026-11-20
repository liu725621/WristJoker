package com.wearos.wristjoker.ui

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 圆屏几何：**按弦长排版**，而不是往圆里塞一个矩形安全区。
 *
 * 上一版所有行都写成固定宽度（110 / 132 / 154 / 146 / 150 / 154 / 106dp）。
 * 那套数字的本质是「以最窄的那一行（按钮 106dp）为准，全屏都别超过它」——
 * 于是圆屏 216dp 的直径只用到一半，左右两大块面积全是黑的，玩家一眼就能
 * 看出「这是个方形界面被裁成了圆」。
 *
 * 圆屏的正确用法是：**越靠上下，可用宽度越窄；中间最宽**。一行在距离圆心
 * `dy` 处能用的最大宽度就是那一段的弦长 `2·√(R² − dy²)`。按这个公式排，
 * 每一行都会自然贴上圆弧，视觉上"长在圆里"，而不是"被圆切掉"。
 *
 * | dy  | 弦长（R=101） | 上一版用的宽 |
 * |-----|--------------|-------------|
 * | 0   | 202          | 154         |
 * | 30  | 193          | 154         |
 * | 50  | 175          | 132         |
 * | 65  | 155          | 110         |
 * | 80  | 123          | 106         |
 *
 * 这不是"更激进的适配"，而是把原本就存在、却被浪费掉的 40~50dp 拿回来。
 * 手牌因此可以做成原作那样的**单排扇形**（8 张 25dp 的牌横向摊开 151dp），
 * 而不必退回 2×4 网格。
 */
object CircleFit {
    /** Galaxy Watch7：432px @2x = 216dp 见方，半径 108dp。 */
    const val SCREEN = 216f
    const val RADIUS = 108f

    /**
     * 距玻璃边缘的余量。
     *
     * 9dp = 屏幕边缘 5dp 渐暗带 + 2dp 弧切误差 + 2dp 真机字体行高溢出。
     * 这不是"安全区"，是**内容圆的半径**：内容贴着这个圆排，圆外 9dp 留白。
     */
    const val INSET = 9f

    /** [dy] 为相对圆心的纵向偏移（向下为正），返回该高度可用的弦长。 */
    fun chord(dy: Float, inset: Float = INSET): Float {
        val r = RADIUS - inset
        val d = abs(dy)
        if (d >= r) return 0f
        return 2f * sqrt(r * r - d * d)
    }

    /**
     * 一行的高度与**与下一行之间的间距**。
     *
     * 拆成两段而不是写成一个"行距"，是因为手牌那一行需要额外留 3dp
     * 给扇形上扬的牌角 —— 牌会画到行框外面，靠间距兜住。
     */
    data class Band(val h: Float, val gap: Float)

    fun totalHeight(bands: List<Band>): Float {
        var t = 0f
        bands.forEachIndexed { i, b ->
            t += b.h
            if (i < bands.lastIndex) t += b.gap
        }
        return t
    }

    /** 每一行中心相对圆心的 y（整体在圆内垂直居中）。 */
    fun centers(bands: List<Band>): FloatArray {
        var c = -totalHeight(bands) / 2f
        return FloatArray(bands.size) { i ->
            val cy = c + bands[i].h / 2f
            c += bands[i].h + bands[i].gap
            cy
        }
    }

    /**
     * 每一行可用的宽度。
     *
     * 注意取的是**这一行上下沿里离圆心更远的那条边**的弦长，不是中心线的。
     * 用中心线会让"高行"的四个角戳出圆外：一行高 h、中心距圆心 dy 时，
     * 四角半径 = √(r² + dy·h + h²/4) > r。手牌那行 42dp 高，在 dy=26.5
     * 处会多出 7dp，正好把牌角顶到玻璃边上。
     */
    fun widths(bands: List<Band>, inset: Float = INSET): FloatArray {
        val cs = centers(bands)
        return FloatArray(cs.size) { i ->
            chord(abs(cs[i]) + bands[i].h / 2f, inset)
        }
    }
}
