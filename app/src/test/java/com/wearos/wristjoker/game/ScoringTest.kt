package com.wearos.wristjoker.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 计分公式、增强、蜡封、版本、小丑摆位的数值验证，基准值对照 Balatro 官方牌型表。 */
class ScoringTest {

    private fun c(rank: Rank, suit: Suit) = Card(rank.ordinal * 4 + suit.ordinal, rank, suit)

    private fun hand(vararg pairs: Pair<Rank, Suit>) = pairs.map { c(it.first, it.second) }

    private fun j(id: String) = JokerInst(Jokers.get(id)!!)

    private fun score(
        played: List<Card>,
        rest: List<Card> = emptyList(),
        jokers: List<JokerInst> = emptyList(),
        discardsLeft: Int = 3,
        blocked: Set<Int> = emptySet(),
        flint: Boolean = false,
    ) = Scoring.compute(
        played = played, hand = rest, jokers = jokers,
        levels = emptyMap(), discardsLeft = discardsLeft, blockedIds = blocked,
        flint = flint, random = kotlin.random.Random(1),
    )

    // ---------------------------------------------------------------- 基础

    @Test
    fun `对子 K K 得分 60`() {
        // 基础 10 筹码 ×2 倍率，两张 K 各 10 筹码 → (10+20)×2 = 60
        val r = score(hand(Rank.KING to Suit.SPADE, Rank.KING to Suit.HEART))
        assertEquals(HandType.PAIR, r.handType)
        assertEquals(30, r.totalChips)
        assertEquals(2.0, r.totalMult, 0.001)
        assertEquals(60L, r.total)
    }

    @Test
    fun `同花顺 A高 得分 1208`() {
        // 100 + (11+10+10+10+10) = 151 筹码，×8 倍率 = 1208
        val r = score(
            hand(
                Rank.ACE to Suit.HEART, Rank.KING to Suit.HEART, Rank.QUEEN to Suit.HEART,
                Rank.JACK to Suit.HEART, Rank.TEN to Suit.HEART,
            )
        )
        assertEquals(HandType.STRAIGHT_FLUSH, r.handType)
        assertEquals(151, r.totalChips)
        assertEquals(8.0, r.totalMult, 0.001)
        assertEquals(1208L, r.total)
    }

    @Test
    fun `燧石把基础值与倍率减半`() {
        val plain = score(hand(Rank.KING to Suit.SPADE, Rank.KING to Suit.HEART))
        val flint = score(hand(Rank.KING to Suit.SPADE, Rank.KING to Suit.HEART), flint = true)
        assertEquals(5, flint.baseChips)     // 10 / 2
        assertEquals(1, flint.baseMult)      // 2 / 2
        assertEquals(25, flint.totalChips)   // 5 + 20
        assertEquals(25L, flint.total)       // 25 × 1
        assertTrue(flint.total < plain.total)
    }

    // ---------------------------------------------------------------- 小丑

    @Test
    fun `小丑加算倍率叠加在牌型倍率之后`() {
        // 对子 KK：30 筹码 × (2+4) 倍率 = 180
        val r = score(
            hand(Rank.KING to Suit.SPADE, Rank.KING to Suit.HEART),
            jokers = listOf(j("joker")),
        )
        assertEquals(180L, r.total)
        assertEquals(4, r.jokerAddMult)
    }

    @Test
    fun `小丑摆位决定结果 加算在前乘算在后更高`() {
        // 两张 K（都是人头牌）：30 筹码
        // 摆位 A：[+4倍率, ×2] → (2+4)×2 = 12  → 360
        // 摆位 B：[×2, +4倍率] → 2×2+4 = 8    → 240
        val a = score(
            hand(Rank.KING to Suit.SPADE, Rank.KING to Suit.HEART),
            jokers = listOf(j("joker"), j("photograph")),
        )
        val b = score(
            hand(Rank.KING to Suit.SPADE, Rank.KING to Suit.HEART),
            jokers = listOf(j("photograph"), j("joker")),
        )
        assertEquals(12.0, a.totalMult, 0.001)
        assertEquals(8.0, b.totalMult, 0.001)
        assertTrue(a.total > b.total)
    }

    @Test
    fun `旗帜按剩余弃牌加筹码`() {
        val r = score(
            hand(Rank.KING to Suit.SPADE, Rank.KING to Suit.HEART),
            jokers = listOf(j("banner")),
            discardsLeft = 3,
        )
        assertEquals(30 + 90, r.totalChips)
    }

    @Test
    fun `男爵按手中 K 的数量乘算`() {
        val rest = hand(
            Rank.KING to Suit.CLUB, Rank.KING to Suit.DIAMOND, Rank.TWO to Suit.CLUB,
        )
        val r = score(
            hand(Rank.KING to Suit.SPADE, Rank.KING to Suit.HEART),
            rest = rest,
            jokers = listOf(j("baron")),
        )
        // 牌型基础 2 倍率 × 手中两张 K 的 1.5 × 1.5
        assertEquals(2.0 * 2.25, r.totalMult, 0.001)
    }

    // ---------------------------------------------------------------- 增强

    @Test
    fun `奖励牌每张多 30 筹码`() {
        val cards = listOf(
            c(Rank.KING, Suit.SPADE).copy(enh = Enhancement.BONUS),
            c(Rank.KING, Suit.HEART),
        )
        val r = score(cards)
        assertEquals(10 + 10 + 10 + 30, r.totalChips)
    }

    @Test
    fun `倍率牌每张多 4 倍率`() {
        val cards = listOf(
            c(Rank.KING, Suit.SPADE).copy(enh = Enhancement.MULT),
            c(Rank.KING, Suit.HEART),
        )
        val r = score(cards)
        assertEquals(6.0, r.totalMult, 0.001)
    }

    @Test
    fun `玻璃牌乘算 2 倍`() {
        val cards = listOf(
            c(Rank.KING, Suit.SPADE).copy(enh = Enhancement.GLASS),
            c(Rank.KING, Suit.HEART),
        )
        val r = score(cards)
        assertEquals(4.0, r.totalMult, 0.001)
    }

    @Test
    fun `钢铁牌留在手里乘算一点五`() {
        val rest = listOf(c(Rank.TWO, Suit.CLUB).copy(enh = Enhancement.STEEL))
        val r = score(hand(Rank.KING to Suit.SPADE, Rank.KING to Suit.HEART), rest = rest)
        assertEquals(3.0, r.totalMult, 0.001)
    }

    @Test
    fun `石头牌不参与牌型但仍计分`() {
        // 一张 K + 一张石头牌：牌型仍是高牌，筹码 = 5 + 10 + 50 = 65
        val cards = listOf(
            c(Rank.KING, Suit.SPADE),
            c(Rank.TWO, Suit.CLUB).copy(enh = Enhancement.STONE),
        )
        val r = score(cards)
        assertEquals(HandType.HIGH_CARD, r.handType)
        assertEquals(5 + 10 + 50, r.totalChips)
    }

    @Test
    fun `万能牌可以补同花`() {
        val cards = listOf(
            c(Rank.TWO, Suit.HEART).copy(enh = Enhancement.WILD),
            c(Rank.FIVE, Suit.HEART),
            c(Rank.EIGHT, Suit.HEART),
            c(Rank.JACK, Suit.HEART),
            c(Rank.KING, Suit.CLUB).copy(enh = Enhancement.WILD),
        )
        val r = score(cards)
        assertEquals(HandType.FLUSH, r.handType)
    }

    // ---------------------------------------------------------------- 蜡封 / 版本

    @Test
    fun `红蜡封让整张牌触发两次`() {
        val normal = score(hand(Rank.KING to Suit.SPADE, Rank.KING to Suit.HEART))
        val sealed = score(
            listOf(
                c(Rank.KING, Suit.SPADE).copy(seal = Seal.RED),
                c(Rank.KING, Suit.HEART),
            )
        )
        assertEquals(normal.totalChips + 10, sealed.totalChips)
    }

    @Test
    fun `箔版本加 50 筹码 全息加 10 倍率`() {
        val foil = score(
            listOf(c(Rank.KING, Suit.SPADE).copy(edition = Edition.FOIL), c(Rank.KING, Suit.HEART))
        )
        assertEquals(30 + 50, foil.totalChips)
        val holo = score(
            listOf(c(Rank.KING, Suit.SPADE).copy(edition = Edition.HOLO), c(Rank.KING, Suit.HEART))
        )
        assertEquals(12.0, holo.totalMult, 0.001)
    }

    @Test
    fun `黄金牌与金蜡封计分时产钱`() {
        val cards = listOf(
            c(Rank.KING, Suit.SPADE).copy(enh = Enhancement.GOLD),
            c(Rank.KING, Suit.HEART).copy(seal = Seal.GOLD),
        )
        val r = score(cards)
        assertEquals(6, r.money)
    }

    // ---------------------------------------------------------------- Boss 封锁

    @Test
    fun `被封锁的牌不贡献筹码但仍参与牌型判定`() {
        val cards = hand(Rank.KING to Suit.SPADE, Rank.KING to Suit.HEART)
        val blocked = setOf(cards[0].id)
        val r = score(cards, blocked = blocked)
        assertEquals(HandType.PAIR, r.handType)
        assertEquals(10 + 10, r.totalChips) // 只有第二张 K 的 10
        assertEquals(setOf(cards[1].id), r.scoringIds)
    }

    // ---------------------------------------------------------------- 结算序列

    @Test
    fun `结算序列的最终值等于总分`() {
        val r = score(
            hand(Rank.KING to Suit.SPADE, Rank.KING to Suit.HEART, Rank.KING to Suit.CLUB),
            jokers = listOf(j("joker"), j("greedy")),
        )
        assertTrue(r.steps.isNotEmpty())
        val last = r.steps.last()
        assertEquals(r.totalChips, last.chipsAfter)
        assertEquals(r.totalMult, last.multAfter, 0.001)
        assertEquals(r.total, (last.chipsAfter * last.multAfter).toLong())
    }

    @Test
    fun `每张计分牌都有自己的结算步`() {
        val cards = hand(
            Rank.ACE to Suit.SPADE, Rank.ACE to Suit.HEART, Rank.ACE to Suit.CLUB,
        )
        val r = score(cards)
        val cardSteps = r.steps.count { it.source == StepSource.CARD }
        assertEquals(3, cardSteps)
        assertTrue(r.steps.filter { it.source == StepSource.CARD }.all { it.cardLabel.isNotEmpty() })
    }
}
