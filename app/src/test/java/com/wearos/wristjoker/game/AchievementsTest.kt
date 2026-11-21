package com.wearos.wristjoker.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 成就与解锁的纯函数判定。
 *
 * 这部分一旦写错，症状是"打了十局某个成就还是没亮"，而玩家不会报 bug，
 * 只会觉得这个系统是假的 —— 所以边界值（差一、刚好、超过）都要钉住。
 */
class AchievementsTest {

    private fun stats(
        runs: Int = 0,
        wins: Int = 0,
        bestAnte: Int = 0,
        bestHand: Long = 0,
        totalHands: Long = 0,
        totalDiscards: Long = 0,
        bestMoney: Int = 0,
        bestStreak: Int = 0,
        seen: Int = 0,
    ) = CareerStats(
        runs = runs,
        wins = wins,
        bestAnte = bestAnte,
        bestHand = bestHand,
        totalHands = totalHands,
        totalDiscards = totalDiscards,
        bestMoney = bestMoney,
        bestStreak = bestStreak,
        seenJokers = seen,
    )

    @Test
    fun `空成绩单一条都不给`() {
        assertTrue(achievementsOf(CareerStats()).isEmpty())
    }

    @Test
    fun `第一局只解锁坐下来`() {
        val got = achievementsOf(stats(runs = 1))
        assertEquals(setOf(Ach.FIRST_RUN.id), got)
    }

    @Test
    fun `底注门槛是闭区间`() {
        // 打到 4 关不该有「过半」，5 关才有
        assertFalse(Ach.ANTE5.id in achievementsOf(stats(bestAnte = 4)))
        assertTrue(Ach.ANTE5.id in achievementsOf(stats(bestAnte = 5)))
        // 8 关同时满足 5 关那一条：达成集合是"全部已达成"，不是"增量"
        val at8 = achievementsOf(stats(bestAnte = 8))
        assertTrue(Ach.ANTE5.id in at8 && Ach.ANTE8.id in at8)
    }

    @Test
    fun `单手指阈值按 10 万与百万分档`() {
        assertFalse(Ach.HAND_10K.id in achievementsOf(stats(bestHand = 9_999)))
        assertTrue(Ach.HAND_10K.id in achievementsOf(stats(bestHand = 10_000)))
        assertTrue(Ach.HAND_100K.id in achievementsOf(stats(bestHand = 100_000)))
        assertTrue(Ach.HAND_1M.id in achievementsOf(stats(bestHand = 1_000_000)))
        // 十万档不该顺带点亮百万档
        assertFalse(Ach.HAND_1M.id in achievementsOf(stats(bestHand = 100_000)))
    }

    @Test
    fun `通关一次解锁开张与全图鉴之外的胜场档`() {
        val got = achievementsOf(stats(runs = 1, wins = 1, bestAnte = 8))
        assertTrue(Ach.FIRST_WIN.id in got)
        assertFalse(Ach.WINS_5.id in got)
    }

    @Test
    fun `连胜与弃牌按累计量判定`() {
        assertTrue(Ach.STREAK_3.id in achievementsOf(stats(bestStreak = 3)))
        assertFalse(Ach.STREAK_5.id in achievementsOf(stats(bestStreak = 3)))
        assertTrue(Ach.DISCARD_500.id in achievementsOf(stats(totalDiscards = 500)))
    }

    @Test
    fun `稀有档要通关或打到第八关才进商店池`() {
        // 全新账号：常见/罕见可以，稀有不行
        val fresh = CareerStats()
        assertTrue(Unlocks.rarityAllowed(Rarity.COMMON, fresh))
        assertTrue(Unlocks.rarityAllowed(Rarity.UNCOMMON, fresh))
        assertFalse(Unlocks.rarityAllowed(Rarity.RARE, fresh))
        // 传说永远不进商店（只能靠「灵魂」）
        assertFalse(Unlocks.rarityAllowed(Rarity.LEGENDARY, stats(wins = 99, bestAnte = 12)))

        assertTrue(Unlocks.rarityAllowed(Rarity.RARE, stats(wins = 1)))
        assertTrue(Unlocks.rarityAllowed(Rarity.RARE, stats(bestAnte = 8)))
    }

    @Test
    fun `牌组按最高底注分三档开放`() {
        val decks = DeckType.entries
        // 前 5 个始终开放
        decks.take(5).forEach { assertTrue(Unlocks.deckUnlocked(it, 0)) }
        // 第 6–10 个要 A3
        assertFalse(Unlocks.deckUnlocked(decks[5], 2))
        assertTrue(Unlocks.deckUnlocked(decks[5], 3))
        // 后 5 个要 A6
        assertFalse(Unlocks.deckUnlocked(decks[10], 5))
        assertTrue(Unlocks.deckUnlocked(decks[10], 6))
    }

    @Test
    fun `同一种子两次开局牌序一致`() {
        val a = GameEngine(seed = 20261121L)
        a.startRun(DeckType.RED, seedOverride = 20261121L)
        val b = GameEngine(seed = 20261121L)
        b.startRun(DeckType.RED, seedOverride = 20261121L)
        assertEquals(a.snapshot().drawPileCards.map { it.id }, b.snapshot().drawPileCards.map { it.id })

        // 换一颗种子就该不一样 —— 否则"种子"只是装饰
        val c = GameEngine(seed = 1L)
        c.startRun(DeckType.RED, seedOverride = 1L)
        assertFalse(
            a.snapshot().drawPileCards.map { it.id } == c.snapshot().drawPileCards.map { it.id }
        )
    }

    @Test
    fun `每日挑战的日期键换算成不同种子`() {
        val d1 = GameEngine(0L)
        d1.startDaily(DeckType.RED, 20261121)
        val d2 = GameEngine(0L)
        d2.startDaily(DeckType.RED, 20261122)
        assertFalse(d1.snapshot().seed == d2.snapshot().seed)
        assertTrue(d1.snapshot().daily)
    }
}
