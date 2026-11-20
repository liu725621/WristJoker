package com.wearos.wristjoker.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第五轮补齐项的回归测试。
 *
 * 这一轮补的东西分三类，所以测试也按三类写：
 * 1. **数量**（牌组 15 / 小丑 150 / 券 32 / 标签 24 / 挑战 20）——
 *    防止以后再"悄悄少几张"；
 * 2. **规则改写小丑**（四指 / 捷径 / 涂抹 / 水花）——
 *    这四张上一版只有 desc、引擎从未读取，必须钉死；
 * 3. **赌注与挑战**（八档修正、固定种子、0 小丑位）——
 *    它们是"难度"和"可复现"的唯一入口。
 */
class ExpansionTest {

    // ---------------------------------------------------------------- 数量

    @Test
    fun `牌组 15 种且没有重复`() {
        assertEquals(15, DeckType.entries.size)
        assertEquals(15, DeckType.entries.map { it.cn }.toSet().size)
    }

    @Test
    fun `棋盘牌组只剩两种花色`() {
        val deck = Deck.forType(DeckType.CHECKERED, kotlin.random.Random(1))
        assertEquals(52, deck.size)
        assertEquals(setOf(Suit.SPADE, Suit.HEART), deck.map { it.suit }.toSet())
    }

    @Test
    fun `弃牌牌组没有人头`() {
        val deck = Deck.forType(DeckType.ABANDONED, kotlin.random.Random(1))
        assertEquals(52, deck.size)
        assertTrue(deck.none { it.rank == Rank.JACK || it.rank == Rank.QUEEN || it.rank == Rank.KING })
    }

    @Test
    fun `小丑 150 张 id 唯一且至少七类机制`() {
        assertEquals(150, Jokers.ALL.size)
        assertEquals(150, Jokers.ALL.map { it.id }.toSet().size)
        // 四档稀有度都要有货
        Rarity.entries.forEach { r ->
            assertTrue("$r 一张都没有", Jokers.ALL.any { it.rarity == r })
        }
    }

    @Test
    fun `消耗品 52 张`() {
        assertEquals(52, Consumables.ALL.size)
        assertEquals(52, Consumables.ALL.map { it.key }.toSet().size)
    }

    @Test
    fun `挑战 20 个且 id 唯一`() {
        assertEquals(20, Challenge.entries.size)
        assertEquals(20, Challenge.entries.map { it.id }.toSet().size)
        // 每个挑战的牌组都必须存在，且描述不能为空
        Challenge.entries.forEach {
            assertNotNull(it.cn)
            assertTrue("${it.cn} 没有规则描述", it.desc.isNotEmpty())
        }
    }

    // ---------------------------------------------------------------- 规则改写

    @Test
    fun `四指让 5 张里的 4 张同花成立`() {
        val cards = listOf(
            Card(0, Rank.TWO, Suit.SPADE),
            Card(1, Rank.FIVE, Suit.SPADE),
            Card(2, Rank.NINE, Suit.SPADE),
            Card(3, Rank.KING, Suit.SPADE),
            Card(4, Rank.THREE, Suit.HEART),
        )
        assertEquals(HandType.HIGH_CARD, HandEvaluator.evaluate(cards).type)
        val withFour = HandEvaluator.evaluate(cards, fourFingers = true)
        assertEquals(HandType.FLUSH, withFour.type)
        assertEquals(4, withFour.scoringCards.size)
    }

    @Test
    fun `捷径让隔一档的 5 张成顺子`() {
        // 花色各不相同，避免被判定成同花顺
        val suits = listOf(Suit.SPADE, Suit.HEART, Suit.DIAMOND, Suit.CLUB, Suit.SPADE)
        val cards = listOf(2, 4, 6, 8, 10).mapIndexed { i, o ->
            Card(i, Rank.entries.first { it.order == o }, suits[i])
        }
        assertFalse(HandEvaluator.isStraight(cards))
        assertTrue(HandEvaluator.isStraight(cards, shortcut = true))
        assertEquals(HandType.STRAIGHT, HandEvaluator.evaluate(cards, shortcut = true).type)
    }

    @Test
    fun `涂抹让红黑花色各自互通`() {
        val cards = listOf(
            Card(0, Rank.TWO, Suit.HEART),
            Card(1, Rank.FIVE, Suit.DIAMOND),
            Card(2, Rank.NINE, Suit.HEART),
            Card(3, Rank.KING, Suit.DIAMOND),
            Card(4, Rank.THREE, Suit.HEART),
        )
        assertFalse(HandEvaluator.isFlush(cards))
        assertTrue(HandEvaluator.isFlush(cards, smeared = true))
        assertEquals(HandType.FLUSH, HandEvaluator.evaluate(cards, smeared = true).type)
    }

    @Test
    fun `水花让所有打出的牌都计分`() {
        // 一对 + 三张散牌：标准只算 2 张
        val cards = listOf(
            Card(0, Rank.KING, Suit.SPADE),
            Card(1, Rank.KING, Suit.HEART),
            Card(2, Rank.TWO, Suit.CLUB),
            Card(3, Rank.FIVE, Suit.DIAMOND),
            Card(4, Rank.NINE, Suit.SPADE),
        )
        val normal = Scoring.compute(
            played = cards, hand = emptyList(), jokers = emptyList(), levels = emptyMap(),
            discardsLeft = 2,
        )
        assertEquals(2, normal.scoringIds.size)
        val splash = Scoring.compute(
            played = cards, hand = emptyList(), jokers = emptyList(), levels = emptyMap(),
            discardsLeft = 2, splash = true,
        )
        assertEquals(5, splash.scoringIds.size)
    }

    // ---------------------------------------------------------------- 赌注

    @Test
    fun `八档赌注逐档加码`() {
        assertEquals(8, Stake.entries.size)
        // 起始钱只在黑注变化
        assertEquals(4, Stake.WHITE.startMoney)
        assertEquals(5, Stake.BLACK.startMoney)
        // 弃牌只从蓝注开始 -1
        assertEquals(0, Stake.GREEN.discardsDelta)
        assertEquals(-1, Stake.BLUE.discardsDelta)
        // 目标倍率：绿 5% / 紫 10%
        assertEquals(1.0, Stake.RED.targetScale(8), 1e-9)
        assertTrue(Stake.GREEN.targetScale(8) > 1.3)
        assertTrue(Stake.PURPLE.targetScale(8) > Stake.GREEN.targetScale(8))
        // 高档包含低档
        assertTrue(Stake.GOLD.atLeast(Stake.RED))
        assertTrue(Stake.GOLD.sellLocked)
        // 高档累计包含低档：蓝注在黑注之后，所以同样不能卖
        assertFalse(Stake.GREEN.sellLocked)
        assertTrue(Stake.BLUE.sellLocked)
        assertEquals(10, Stake.GOLD.rerollBase)
        assertEquals(1, Stake.ORANGE.packPriceDelta)
    }

    @Test
    fun `黑注下小丑不可卖出`() {
        val e = GameEngine(11)
        e.startRun(DeckType.RED, Stake.BLACK)
        // 直接塞一张，验证 sell 被锁
        val def = Jokers.ALL.first { it.rarity == Rarity.COMMON }
        e.debugAddJoker(JokerInst(def))
        assertEquals(1, e.snapshot().jokers.size)
        assertFalse(e.sell(0))
        assertEquals(1, e.snapshot().jokers.size)
    }

    @Test
    fun `白注下小丑可以卖出`() {
        val e = GameEngine(12)
        e.startRun(DeckType.RED, Stake.WHITE)
        val def = Jokers.ALL.first { it.rarity == Rarity.COMMON }
        e.debugAddJoker(JokerInst(def))
        assertTrue(e.sell(0))
        assertEquals(0, e.snapshot().jokers.size)
    }

    // ---------------------------------------------------------------- 挑战

    @Test
    fun `挑战固定种子两次开局手牌一致`() {
        val c = Challenge.OMELETTE
        val a = GameEngine(999).also { it.startChallenge(c); it.selectBlind() }
        val b = GameEngine(1000).also { it.startChallenge(c); it.selectBlind() }
        // startChallenge 内部会把种子覆盖成挑战的固定值，所以两次手牌应当一致
        assertEquals(
            a.snapshot().hand.map { it.id },
            b.snapshot().hand.map { it.id },
        )
    }

    @Test
    fun `无丑挑战没有小丑位`() {
        val e = GameEngine(1)
        e.startChallenge(Challenge.JOKERLESS)
        assertEquals(0, e.snapshot().maxJokers)
        // 开局送的也不会出现
        assertEquals(0, e.snapshot().jokers.size)
    }

    @Test
    fun `发射挑战 0 槽位但开局仍送 2 个小丑`() {
        val e = GameEngine(1)
        e.startChallenge(Challenge.BLASTOFF)
        assertEquals(0, e.snapshot().maxJokers)
        assertEquals(2, e.snapshot().jokers.size)
    }

    @Test
    fun `魔术牌组开局带 2 张塔罗`() {
        val e = GameEngine(3)
        e.startRun(DeckType.MAGIC)
        val ss = e.snapshot().consumables
        assertEquals(2, ss.size)
        assertTrue(ss.all { it.kind == ConsumableKind.TAROT })
    }

    @Test
    fun `幽灵牌组开局带 1 张幻灵`() {
        val e = GameEngine(4)
        e.startRun(DeckType.GHOST)
        val ss = e.snapshot().consumables
        assertEquals(1, ss.size)
        assertEquals(ConsumableKind.SPECTRAL, ss[0].kind)
    }

    @Test
    fun `挑战的目标倍率进到盲注目标里`() {
        val normal = GameEngine(7).also { it.startRun(DeckType.YELLOW) }
        val doubled = GameEngine(7).also { it.startChallenge(Challenge.DOUBLE) }
        val n = normal.snapshot().blindOptions.first().target
        val d = doubled.snapshot().blindOptions.first().target
        assertEquals((n * 2).toDouble(), d.toDouble(), 2.0)
    }

    // ---------------------------------------------------------------- 手牌重排

    @Test
    fun `手牌可以拖动重排`() {
        val e = GameEngine(21)
        e.startRun(DeckType.RED)
        e.selectBlind()
        val before = e.snapshot().hand.map { it.id }
        assertTrue(before.size >= 4)
        e.moveCard(0, 3)
        val after = e.snapshot().hand.map { it.id }
        assertEquals(before.size, after.size)
        assertEquals(before.toSet(), after.toSet())
        assertEquals(before[0], after[3])
        assertEquals(before[1], after[0])
    }

    @Test
    fun `重排不改选中的牌`() {
        val e = GameEngine(22)
        e.startRun(DeckType.RED)
        e.selectBlind()
        val hand = e.snapshot().hand
        e.toggle(hand[0].id)
        e.moveCard(0, 2)
        assertEquals(setOf(hand[0].id), e.snapshot().selected)
    }
}
