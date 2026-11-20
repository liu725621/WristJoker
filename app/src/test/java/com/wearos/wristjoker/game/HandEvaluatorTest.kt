package com.wearos.wristjoker.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 牌型判定的边界覆盖。构造牌时用 rank/suit 反推固定 id，保证断言可读。 */
class HandEvaluatorTest {

    private fun c(rank: Rank, suit: Suit) = Card(rank.ordinal * 4 + suit.ordinal, rank, suit)

    private fun hand(vararg pairs: Pair<Rank, Suit>) = pairs.map { c(it.first, it.second) }

    @Test
    fun `高牌只算最大的一张`() {
        val r = HandEvaluator.evaluate(
            hand(Rank.KING to Suit.SPADE, Rank.NINE to Suit.HEART,
                Rank.SEVEN to Suit.DIAMOND, Rank.FOUR to Suit.CLUB, Rank.TWO to Suit.HEART)
        )
        assertEquals(HandType.HIGH_CARD, r.type)
        assertEquals(1, r.scoringCards.size)
        assertEquals(Rank.KING, r.scoringCards[0].rank)
    }

    @Test
    fun `对子只算两张`() {
        val r = HandEvaluator.evaluate(
            hand(Rank.KING to Suit.SPADE, Rank.KING to Suit.HEART,
                Rank.NINE to Suit.HEART, Rank.FOUR to Suit.CLUB, Rank.TWO to Suit.HEART)
        )
        assertEquals(HandType.PAIR, r.type)
        assertEquals(2, r.scoringCards.size)
    }

    @Test
    fun `两对取最大的两对且只算四张`() {
        val r = HandEvaluator.evaluate(
            hand(Rank.ACE to Suit.SPADE, Rank.ACE to Suit.HEART,
                Rank.KING to Suit.DIAMOND, Rank.KING to Suit.CLUB, Rank.TWO to Suit.HEART)
        )
        assertEquals(HandType.TWO_PAIR, r.type)
        assertEquals(4, r.scoringCards.size)
        assertTrue(r.scoringCards.none { it.rank == Rank.TWO })
    }

    @Test
    fun `三条`() {
        val r = HandEvaluator.evaluate(
            hand(Rank.QUEEN to Suit.SPADE, Rank.QUEEN to Suit.HEART, Rank.QUEEN to Suit.DIAMOND,
                Rank.SEVEN to Suit.CLUB, Rank.TWO to Suit.HEART)
        )
        assertEquals(HandType.THREE_OF_A_KIND, r.type)
        assertEquals(3, r.scoringCards.size)
    }

    @Test
    fun `顺子算全部五张`() {
        val r = HandEvaluator.evaluate(
            hand(Rank.NINE to Suit.SPADE, Rank.EIGHT to Suit.HEART, Rank.SEVEN to Suit.DIAMOND,
                Rank.SIX to Suit.CLUB, Rank.FIVE to Suit.HEART)
        )
        assertEquals(HandType.STRAIGHT, r.type)
        assertEquals(5, r.scoringCards.size)
    }

    @Test
    fun `A 可以当小构成 A2345 顺子`() {
        val r = HandEvaluator.evaluate(
            hand(Rank.ACE to Suit.SPADE, Rank.TWO to Suit.HEART, Rank.THREE to Suit.DIAMOND,
                Rank.FOUR to Suit.CLUB, Rank.FIVE to Suit.HEART)
        )
        assertEquals(HandType.STRAIGHT, r.type)
    }

    @Test
    fun `A 不能同时当两头 AKQJ2 不是顺子`() {
        val r = HandEvaluator.evaluate(
            hand(Rank.ACE to Suit.SPADE, Rank.KING to Suit.HEART, Rank.QUEEN to Suit.DIAMOND,
                Rank.JACK to Suit.CLUB, Rank.TWO to Suit.HEART)
        )
        assertEquals(HandType.HIGH_CARD, r.type)
        assertFalse(HandEvaluator.isStraight(hand(
            Rank.ACE to Suit.SPADE, Rank.KING to Suit.HEART, Rank.QUEEN to Suit.DIAMOND,
            Rank.JACK to Suit.CLUB, Rank.TWO to Suit.HEART)))
    }

    @Test
    fun `同花要求五张同花色`() {
        val flush = HandEvaluator.evaluate(
            hand(Rank.KING to Suit.SPADE, Rank.JACK to Suit.SPADE, Rank.NINE to Suit.SPADE,
                Rank.FIVE to Suit.SPADE, Rank.TWO to Suit.SPADE)
        )
        assertEquals(HandType.FLUSH, flush.type)
        // 只有四张同花色就不成立
        val notFlush = HandEvaluator.evaluate(
            hand(Rank.KING to Suit.SPADE, Rank.JACK to Suit.SPADE, Rank.NINE to Suit.SPADE,
                Rank.FIVE to Suit.SPADE, Rank.TWO to Suit.HEART)
        )
        assertEquals(HandType.HIGH_CARD, notFlush.type)
    }

    @Test
    fun `葫芦算全部五张`() {
        val r = HandEvaluator.evaluate(
            hand(Rank.KING to Suit.SPADE, Rank.KING to Suit.HEART, Rank.KING to Suit.DIAMOND,
                Rank.SEVEN to Suit.CLUB, Rank.SEVEN to Suit.HEART)
        )
        assertEquals(HandType.FULL_HOUSE, r.type)
        assertEquals(5, r.scoringCards.size)
    }

    @Test
    fun `四条优先于同花 KKKK2 全是黑桃仍是四条`() {
        val r = HandEvaluator.evaluate(
            hand(Rank.KING to Suit.SPADE, Rank.KING to Suit.SPADE, Rank.KING to Suit.SPADE,
                Rank.KING to Suit.SPADE, Rank.TWO to Suit.SPADE)
        )
        // 4 张 K + 1 张 2，同花色但不构成同花五条（点数不同）→ 四条
        assertEquals(HandType.FOUR_OF_A_KIND, r.type)
        assertEquals(4, r.scoringCards.size)
    }

    @Test
    fun `同花顺与皇家同花顺`() {
        val sf = HandEvaluator.evaluate(
            hand(Rank.NINE to Suit.HEART, Rank.EIGHT to Suit.HEART, Rank.SEVEN to Suit.HEART,
                Rank.SIX to Suit.HEART, Rank.FIVE to Suit.HEART)
        )
        assertEquals(HandType.STRAIGHT_FLUSH, sf.type)
        assertFalse(sf.isRoyal)

        val royal = HandEvaluator.evaluate(
            hand(Rank.ACE to Suit.HEART, Rank.KING to Suit.HEART, Rank.QUEEN to Suit.HEART,
                Rank.JACK to Suit.HEART, Rank.TEN to Suit.HEART)
        )
        assertEquals(HandType.STRAIGHT_FLUSH, royal.type)
        assertTrue(royal.isRoyal)
        assertEquals("皇家同花顺", royal.displayName)
    }

    @Test
    fun `隐藏牌型 五条 同花葫芦 同花五条`() {
        val five = HandEvaluator.evaluate(
            hand(Rank.NINE to Suit.SPADE, Rank.NINE to Suit.HEART, Rank.NINE to Suit.DIAMOND,
                Rank.NINE to Suit.CLUB, Rank.NINE to Suit.SPADE)
        )
        assertEquals(HandType.FIVE_OF_A_KIND, five.type)

        val fh = HandEvaluator.evaluate(
            hand(Rank.KING to Suit.CLUB, Rank.KING to Suit.CLUB, Rank.KING to Suit.CLUB,
                Rank.SEVEN to Suit.CLUB, Rank.SEVEN to Suit.CLUB)
        )
        assertEquals(HandType.FLUSH_HOUSE, fh.type)

        val ff = HandEvaluator.evaluate(
            hand(Rank.KING to Suit.CLUB, Rank.KING to Suit.CLUB, Rank.KING to Suit.CLUB,
                Rank.KING to Suit.CLUB, Rank.KING to Suit.CLUB)
        )
        assertEquals(HandType.FLUSH_FIVE, ff.type)
    }

    @Test
    fun `单张出牌判为高牌`() {
        val r = HandEvaluator.evaluate(hand(Rank.ACE to Suit.SPADE))
        assertEquals(HandType.HIGH_CARD, r.type)
        assertEquals(11, r.chips)
    }
}
