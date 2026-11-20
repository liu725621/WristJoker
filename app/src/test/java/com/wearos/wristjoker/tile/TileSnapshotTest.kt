package com.wearos.wristjoker.tile

import com.wearos.wristjoker.game.DeckType
import com.wearos.wristjoker.game.Phase
import com.wearos.wristjoker.game.RunState
import com.wearos.wristjoker.game.ShopState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 卡片文案的映射。
 *
 * 这些东西在真机上验证一次要抬手十几次，而且"商店态显示的是不是钱"
 * 这种问题肉眼很容易看漏 —— 所以全部压成纯函数单测。
 */
class TileSnapshotTest {

    private fun state(
        phase: Phase = Phase.PLAYING,
        ante: Int = 3,
        blindIndex: Int = 0,
        score: Long = 12_480,
        target: Long = 18_000,
        money: Int = 12,
    ) = RunState(
        seed = 1,
        deckType = DeckType.RED,
        phase = phase,
        ante = ante,
        blindIndex = blindIndex,
        score = score,
        money = money,
        handsLeft = 3,
        discardsLeft = 2,
        handSize = 8,
        master = emptyList(),
        drawPile = emptyList(),
        discardPile = emptyList(),
        hand = emptyList(),
        jokers = emptyList(),
        consumables = emptyList(),
        levels = emptyMap(),
        vouchers = emptyList(),
        bossSeq = emptyList(),
        pendingTags = emptyList(),
        playedTypes = emptyList(),
        destroyed = emptyList(),
        shop = ShopState(),
        rerollCost = 5,
        cashOut = null,
        handsPlayed = 0,
        bestHand = 0,
        blindsCleared = 5,
        lastGain = 0,
        endless = false,
        message = "",
        target = target,
    )

    @Test
    fun `没有存档时是引导态`() {
        val s = TileSnapshot.of(null, bestAnte = 5)
        assertFalse(s.running)
        assertEquals("轻触开局", s.big)
        assertTrue(s.ratio < 0f)          // 不画进度条
        assertEquals("最高 A V", s.meta)
    }

    @Test
    fun `牌桌态显示分数与进度`() {
        val s = TileSnapshot.of(state(score = 9_000, target = 18_000), bestAnte = 0)
        assertTrue(s.running)
        assertEquals("A III · 小盲", s.kicker)
        assertEquals("9,000", s.big)
        assertEquals("/ 18,000", s.sub)
        assertEquals(0.5f, s.ratio, 0.001f)
        assertEquals("\$12 · 手 3 · 弃 2", s.meta)
    }

    @Test
    fun `分数打超了进度条封顶`() {
        val s = TileSnapshot.of(state(score = 40_000, target = 18_000), bestAnte = 0)
        assertEquals(1f, s.ratio, 0.001f)
    }

    @Test
    fun `Boss 关标记出来`() {
        val s = TileSnapshot.of(state(blindIndex = 2), bestAnte = 0)
        assertTrue(s.boss)
        assertEquals("A III · BOSS", s.kicker)
    }

    @Test
    fun `商店态把主数字让给金钱`() {
        val s = TileSnapshot.of(state(phase = Phase.SHOP, money = 31), bestAnte = 0)
        assertTrue(s.running)
        assertEquals("\$31", s.big)
        assertTrue(s.ratio < 0f)
    }

    @Test
    fun `结束态不当成进行中`() {
        assertFalse(TileSnapshot.of(state(phase = Phase.WIN), bestAnte = 3).running)
        assertFalse(TileSnapshot.of(state(phase = Phase.GAME_OVER), bestAnte = 0).running)
        assertFalse(TileSnapshot.of(state(phase = Phase.MENU), bestAnte = 0).running)
    }

    @Test
    fun `大数字按位数缩写`() {
        assertEquals("999", TileSnapshot.compact(999))
        assertEquals("12,480", TileSnapshot.compact(12_480))
        assertEquals("99,999", TileSnapshot.compact(99_999))
        assertEquals("248K", TileSnapshot.compact(248_000))
        assertEquals("1.2M", TileSnapshot.compact(1_240_000))
    }

    @Test
    fun `目标分为零时不除零`() {
        // 旧存档没有 tgt 行，解码出来是 0；这里不能崩，也不能出现 Infinity
        val s = TileSnapshot.of(state(score = 100, target = 0), bestAnte = 0)
        assertTrue(s.ratio in 0f..1f)
    }
}
