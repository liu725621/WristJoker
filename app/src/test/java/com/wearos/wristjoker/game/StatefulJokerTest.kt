package com.wearos.wristjoker.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 五条有状态小丑的规则回归测试。
 *
 * 这五张上一版是"照着名字猜"的实现，本轮回退到 card.lua 源码：
 * - 超新星：把该牌型本局打出次数加进倍率（而非等级 ×4）
 * - 古老小丑：本回合随机一种花色，该花色每张计分牌 ×1.5（而非花色种类越多越强）
 * - 方尖石塔：乘算倍率随"出非最常牌型"成长，失败重置（而非高阶牌型 ×2）
 * - 仪式匕首：永久加算倍率来自吞掉的小丑售价 ×2（而非"卖出时吞左侧"）
 * - 全息影像：每往牌堆加一张牌 ×0.25（而非每买一个小丑）
 */
class StatefulJokerTest {

    private fun compute(
        id: String,
        played: List<Card>,
        handsPlayedCount: Map<HandType, Int> = emptyMap(),
        ancientSuit: Suit? = null,
        obeliskXMult: Double = 1.0,
        cardsAddedToDeck: Int = 0,
        growthMult: Int = 0,
    ) = Scoring.compute(
        played = played,
        hand = emptyList(),
        jokers = listOf(JokerInst(Jokers.get(id)!!, growthMult = growthMult)),
        levels = emptyMap(),
        discardsLeft = 3,
        handsPlayedCount = handsPlayedCount,
        ancientSuit = ancientSuit,
        obeliskXMult = obeliskXMult,
        cardsAddedToDeck = cardsAddedToDeck,
    )

    private val ace = Card(1, Rank.ACE, Suit.SPADE)

    // ---------------------------------------------------------------- 超新星

    @Test
    fun `超新星把该牌型本局打出次数加进倍率`() {
        // High Card 基础倍率 1；addMult = 本局已打出次数 + 1（含本次）
        assertEquals(2.0, compute("supernova", listOf(ace)).totalMult, 0.001)
        assertEquals(
            5.0,
            compute("supernova", listOf(ace), handsPlayedCount = mapOf(HandType.HIGH_CARD to 3)).totalMult,
            0.001,
        )
        assertEquals(
            11.0,
            compute("supernova", listOf(ace), handsPlayedCount = mapOf(HandType.HIGH_CARD to 9)).totalMult,
            0.001,
        )
    }

    // ---------------------------------------------------------------- 古老小丑

    @Test
    fun `古老小丑只对本回合选中的花色计分牌乘算`() {
        // 5 张黑桃 → 同花顺，5 张都参与计分
        val spades = listOf(
            Card(1, Rank.ACE, Suit.SPADE),
            Card(2, Rank.KING, Suit.SPADE),
            Card(3, Rank.QUEEN, Suit.SPADE),
            Card(4, Rank.JACK, Suit.SPADE),
            Card(5, Rank.TEN, Suit.SPADE),
        )
        val none = compute("ancient", spades).totalMult
        val heart = compute("ancient", spades, ancientSuit = Suit.HEART).totalMult
        val spade = compute("ancient", spades, ancientSuit = Suit.SPADE).totalMult
        // 花色未选 / 选中非黑桃：没有该花色计分牌 → ×1
        assertEquals(none, heart, 0.001)
        // 选中黑桃：5 张各 ×1.5
        assertEquals(none * 1.5 * 1.5 * 1.5 * 1.5 * 1.5, spade, 0.001)
    }

    // ---------------------------------------------------------------- 方尖石塔

    @Test
    fun `方尖石塔乘算倍率直接取引擎传入的成长值`() {
        assertEquals(1.0, compute("obelisk", listOf(ace), obeliskXMult = 1.0).totalMult, 0.001)
        assertEquals(1.6, compute("obelisk", listOf(ace), obeliskXMult = 1.6).totalMult, 0.001)
        assertEquals(3.0, compute("obelisk", listOf(ace), obeliskXMult = 3.0).totalMult, 0.001)
    }

    // ---------------------------------------------------------------- 仪式匕首

    @Test
    fun `仪式匕首把吞掉小丑的成长值加进倍率`() {
        val base = compute("ceremonial", listOf(ace), growthMult = 0)
        val grown = compute("ceremonial", listOf(ace), growthMult = 8)
        assertEquals(base.totalMult + 8, grown.totalMult, 0.001)
    }

    @Test
    fun `多张仪式匕首各自读取自己的成长值`() {
        val a = JokerInst(Jokers.get("ceremonial")!!, growthMult = 3)
        val b = JokerInst(Jokers.get("ceremonial")!!, growthMult = 7)
        val r = Scoring.compute(
            played = listOf(ace),
            hand = emptyList(),
            jokers = listOf(a, b),
            levels = emptyMap(),
            discardsLeft = 3,
        )
        // 两张都只做加算，总加算 = 3 + 7；High Card 基础 1 → 11
        assertEquals(11.0, r.totalMult, 0.001)
    }

    // ---------------------------------------------------------------- 全息影像

    @Test
    fun `全息影像每加一张牌乘 0_25`() {
        // 加 0 张 → ×1；加 4 张 → ×(1 + 4*0.25) = ×2
        assertEquals(1.0, compute("hologram", listOf(ace), cardsAddedToDeck = 0).totalMult, 0.001)
        assertEquals(2.0, compute("hologram", listOf(ace), cardsAddedToDeck = 4).totalMult, 0.001)
        assertEquals(3.5, compute("hologram", listOf(ace), cardsAddedToDeck = 10).totalMult, 0.001)
    }

    // ---------------------------------------------------------------- 引擎级：状态更新与副作用

    @Test
    fun `仪式匕首选盲注时吞掉右侧小丑并加倍率`() {
        val e = GameEngine(1)
        e.startRun(DeckType.RED)
        val dagger = JokerInst(Jokers.get("ceremonial")!!)
        val victim = JokerInst(Jokers.get("jolly")!!, Edition.FOIL)
        e.restore(e.export().copy(phase = Phase.BLIND_SELECT, jokers = listOf(dagger, victim)))
        e.selectBlind()
        val js = e.snapshot().jokers
        assertEquals(1, js.size)
        assertEquals("ceremonial", js[0].id)
        assertEquals(victim.sellPrice * 2, js[0].growthMult)
    }

    @Test
    fun `仪式匕首左侧没有小丑时不动`() {
        val e = GameEngine(1)
        e.startRun(DeckType.RED)
        e.restore(e.export().copy(phase = Phase.BLIND_SELECT, jokers = listOf(JokerInst(Jokers.get("ceremonial")!!))))
        e.selectBlind()
        val js = e.snapshot().jokers
        assertEquals(1, js.size)
        assertEquals(0, js[0].growthMult)
    }

    @Test
    fun `古老小丑选盲注后更换花色`() {
        val e = GameEngine(1)
        e.startRun(DeckType.RED)
        val before = Suit.HEART
        e.restore(e.export().copy(phase = Phase.BLIND_SELECT, ancientSuit = before))
        e.selectBlind()
        // 从其余三种花色里随机，一定不等于上一回合
        assertTrue(e.export().ancientSuit != before)
    }

    @Test
    fun `方尖石塔出非最常牌型时乘算成长`() {
        val e = GameEngine(1)
        e.startRun(DeckType.RED)
        val hand = listOf(Card(0, Rank.ACE, Suit.SPADE), Card(1, Rank.KING, Suit.SPADE))
        val state = e.export().copy(
            phase = Phase.PLAYING,
            master = hand,
            drawPile = emptyList(),
            discardPile = emptyList(),
            hand = listOf(0, 1),
            jokers = listOf(JokerInst(Jokers.get("obelisk")!!)),
            typeCount = mapOf(HandType.HIGH_CARD.name to 3, HandType.PAIR.name to 5),
            obeliskXMult = 1.4,
            handsLeft = 4,
            score = 0,
            target = 999999,
        )
        e.restore(state)
        e.toggle(0)
        e.play()
        // High Card 含本次 4 次，仍少于 Pair 的 5 次 → 非最常 → +0.2
        assertEquals(1.6, e.export().obeliskXMult, 0.001)
    }

    @Test
    fun `大理石每关加牌计数加一`() {
        val e = GameEngine(1)
        e.startRun(DeckType.RED)
        e.restore(e.export().copy(phase = Phase.BLIND_SELECT, jokers = listOf(JokerInst(Jokers.get("marble")!!))))
        e.selectBlind()
        assertEquals(1, e.export().cardsAddedToDeck)
    }
}
