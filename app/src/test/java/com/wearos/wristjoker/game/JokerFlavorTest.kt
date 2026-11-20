package com.wearos.wristjoker.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 台词完整性：150 张小丑都得有一句话，而且不能有两张说同一句。
 *
 * 这条测试防的是"新增小丑忘了写台词"——图鉴里那格会空着，
 * 比没有台词更明显。
 */
class JokerFlavorTest {

    @Test
    fun everyJokerHasAFlavorLine() {
        val missing = Jokers.ALL.map { it.id }.filter { JokerFlavor.of(it).isNullOrBlank() }
        assertTrue("缺少台词：$missing", missing.isEmpty())
    }

    @Test
    fun flavorCountMatchesJokerCount() {
        assertEquals(Jokers.ALL.size, JokerFlavor.size)
    }

    @Test
    fun flavorLinesFitDetailPage() {
        val tooLong = JokerFlavor.ids
            .mapNotNull { id -> JokerFlavor.of(id)?.let { id to it } }
            .filter { it.second.length > 20 }
        assertTrue("台词超过 20 字，详情页一行放不下：$tooLong", tooLong.isEmpty())
    }

    @Test
    fun noDuplicatedFlavorLines() {
        val lines = JokerFlavor.ids.mapNotNull { JokerFlavor.of(it) }
        assertEquals("有重复台词", lines.size, lines.toSet().size)
    }
}
