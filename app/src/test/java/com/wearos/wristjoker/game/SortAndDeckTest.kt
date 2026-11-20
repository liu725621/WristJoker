package com.wearos.wristjoker.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 排序与牌堆查看的数据层。
 *
 * 这两项都是"看得见的功能"，但真正容易错的是数据层：
 * 排序会动 `hand` 这个可变列表，一旦按下标而不是按 id 处理，选中集合就会错位；
 * 牌堆暴露如果漏过滤已销毁的牌（玻璃碎裂），查看器里会看到不存在的牌。
 */
class SortAndDeckTest {

    private fun playing(seed: Long): GameEngine =
        GameEngine(seed).also {
            it.startRun(DeckType.RED)
            it.selectBlind()
        }

    @Test
    fun `按点数排序后手牌升序`() {
        // 多个种子：一局的手牌是随机的，单一种子可能本来就接近有序
        for (seed in 1L..12L) {
            val e = playing(seed)
            e.sortHand(SortMode.RANK)
            val orders = e.snapshot().hand.map { it.rank.order }
            assertEquals("seed=$seed", orders.sorted(), orders)
        }
    }

    @Test
    fun `按花色排序后同花色相邻`() {
        for (seed in 1L..12L) {
            val e = playing(seed)
            e.sortHand(SortMode.SUIT)
            val suits = e.snapshot().hand.map { it.suit.ordinal }
            assertEquals("seed=$seed", suits.sorted(), suits)
        }
    }

    @Test
    fun `排序不改变手牌组成`() {
        val e = playing(31)
        val before = e.snapshot().hand.map { it.id }.toSet()
        e.sortHand(SortMode.SUIT)
        val after = e.snapshot().hand.map { it.id }.toSet()
        assertEquals(before, after)
    }

    @Test
    fun `排序不丢失选中集合`() {
        val e = playing(32)
        val hand = e.snapshot().hand
        // 选第 2、4 张（下标），排序后仍应选中同样两张牌
        e.toggle(hand[1].id)
        e.toggle(hand[3].id)
        val picked = e.snapshot().selected
        e.sortHand(SortMode.RANK)
        assertEquals(picked, e.snapshot().selected)
    }

    @Test
    fun `牌堆查看的数量与剩余数一致`() {
        val e = playing(41)
        val ui = e.snapshot()
        assertEquals(ui.deckLeft, ui.drawPileCards.size)
        // 抽牌堆、手牌、弃牌堆互不重叠
        val drawIds = ui.drawPileCards.map { it.id }.toSet()
        val handIds = ui.hand.map { it.id }.toSet()
        assertTrue(drawIds.intersect(handIds).isEmpty())
    }

    @Test
    fun `出牌后弃牌堆里能看到打出的牌`() {
        val e = playing(51)
        val first = e.snapshot().hand.first()
        e.toggle(first.id)
        e.play()
        val ui = e.snapshot()
        assertTrue(ui.discardCards.isNotEmpty())
        assertTrue(ui.discardCards.any { it.id == first.id })
        // 不应有重复 id
        assertEquals(ui.discardCards.size, ui.discardCards.map { it.id }.toSet().size)
    }

    @Test
    fun `非出牌阶段排序不生效`() {
        val e = GameEngine(61)
        e.startRun(DeckType.RED)
        // 停在盲注选择页：没有手牌，排序应是空操作而不是抛异常
        e.sortHand(SortMode.RANK)
        assertEquals(0, e.snapshot().hand.size)
    }
}
