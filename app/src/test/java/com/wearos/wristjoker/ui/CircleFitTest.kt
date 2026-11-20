package com.wearos.wristjoker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 圆屏几何测试。
 *
 * 这一层以前没有测试，只靠 `tools/verify_circle.py` 在开发时人工跑 —
 * 于是"每行写死一个宽度"能一路通过：脚本只检查四角在不在圆里，
 * 不检查**有没有把圆用起来**。结果是内容全都挤在 122dp 宽的中间带，
 * 圆屏左右各浪费 40dp，看起来就像"给圆屏加了个安全区"。
 *
 * 这里钉两条：
 * 1. 任何一条横带的四角都不能超出内容圆（半径 108 − 9 = 99dp）；
 * 2. 中间带必须明显比顶部带宽 —— 也就是宽度真的随弦长变化，而不是写死。
 */
class CircleFitTest {

    private val contentRadius = CircleFit.RADIUS - CircleFit.INSET

    private fun assertFits(name: String, bands: List<CircleFit.Band>) {
        val ws = CircleFit.widths(bands)
        val cs = CircleFit.centers(bands)
        assertEquals("横带数量", bands.size, ws.size)

        for (i in bands.indices) {
            val halfW = ws[i] / 2.0
            val farY = abs(cs[i]) + bands[i].h / 2.0
            val corner = hypot(halfW, farY)
            assertTrue(
                "$name 第 $i 带四角半径 ${"%.1f".format(corner)}dp 超出内容圆 $contentRadius dp",
                corner <= contentRadius + 0.05,
            )
            assertTrue("$name 第 $i 带宽必须为正", ws[i] > 0f)
        }

        val total = CircleFit.totalHeight(bands)
        assertTrue(
            "$name 总高 $total dp 超过内容圆直径 ${contentRadius * 2}",
            total <= contentRadius * 2,
        )
    }

    @Test
    fun `牌桌七条横带都落在内容圆内`() {
        assertFits("牌桌", PLAY_BANDS)
    }

    @Test
    fun `商店六条横带都落在内容圆内`() {
        assertFits("商店", SHOP_BANDS)
    }

    @Test
    fun `中间带明显宽于顶部带`() {
        for ((name, bands) in listOf("牌桌" to PLAY_BANDS, "商店" to SHOP_BANDS)) {
            val ws = CircleFit.widths(bands).toList()
            val top = ws.first()
            val widest = ws.max()
            assertTrue(
                "$name 最宽横带 $widest 只比顶部 $top 宽 ${widest - top}dp，说明没按弦长排版",
                widest - top >= 20f,
            )
            // 顶部最窄、越往中间越宽：这是圆屏版式的基本特征
            assertTrue("$name 顶部带应当是最窄的一档之一", top <= ws.min() + 1f)
        }
    }

    @Test
    fun `弦长公式在圆心处等于直径`() {
        assertEquals(
            (CircleFit.RADIUS - CircleFit.INSET) * 2f,
            CircleFit.chord(0f),
            0.01f,
        )
        assertEquals(0f, CircleFit.chord(CircleFit.RADIUS), 0.01f)
    }
}
