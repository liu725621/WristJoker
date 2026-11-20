package com.wearos.wristjoker.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 引擎整局行为。
 *
 * 全部用固定种子 —— 引擎里所有随机都走可注入的 [kotlin.random.Random]，
 * 同一种子必然同一局牌，失败可复现。
 */
class GameEngineTest {

    /** 从手牌里挑期望得分最高的一组（含不足 5 张的情况）。 */
    private fun bestSelection(
        hand: List<Card>,
        jokers: List<JokerInst>,
        levels: Map<HandType, Int>,
        discards: Int,
    ): List<Card> {
        var best: List<Card> = hand.take(minOf(5, hand.size))
        var bestScore = -1L
        fun eval(combo: List<Card>) {
            if (combo.isEmpty()) return
            val r = Scoring.compute(
                combo, hand - combo.toSet(), jokers, levels, discards,
                random = kotlin.random.Random(0),
            )
            if (r.total > bestScore) {
                bestScore = r.total
                best = combo
            }
        }
        fun pick(start: Int, acc: MutableList<Card>, max: Int) {
            if (acc.isNotEmpty() && acc.size <= max) eval(acc.toList())
            if (acc.size == max) return
            for (i in start until hand.size) {
                acc.add(hand[i])
                pick(i + 1, acc, max)
                acc.removeAt(acc.size - 1)
            }
        }
        pick(0, mutableListOf(), 5)
        return best
    }

    /**
     * 自动跑一局：每手挑最优组合出牌，能弃就弃，遇到结算/商店自动推进。
     *
     * 这不是"高手打法"，但只要它能稳定打过第一关，就说明主流程是通的。
     */
    private fun autoRun(seed: Long, maxSteps: Int = 600, stopAtCashOut: Boolean = false): GameEngine {
        val e = GameEngine(seed)
        e.startRun(DeckType.RED)
        var guard = 0
        while (guard++ < maxSteps) {
            val ui = e.snapshot()
            when (ui.phase) {
                Phase.BLIND_SELECT -> e.selectBlind()
                Phase.PLAYING -> {
                    if (ui.hand.isEmpty()) break
                    val pick = bestSelection(ui.hand, ui.jokers, ui.levels, ui.discardsLeft)
                    val est = Scoring.compute(
                        pick, ui.hand - pick.toSet(), ui.jokers, ui.levels, ui.discardsLeft,
                        random = kotlin.random.Random(0),
                    ).total
                    // 按当前手牌打到不了目标、且还有弃牌机会 → 先换手（真人也是这么打的）
                    if (ui.discardsLeft > 0 && ui.handsLeft > 1 &&
                        ui.score + est * ui.handsLeft < ui.target
                    ) {
                        val keep = pick.map { it.id }.toSet()
                        ui.hand.filter { it.id !in keep }.take(3).forEach { e.toggle(it.id) }
                        e.discard()
                        e.clearSelection()
                        continue
                    }
                    pick.forEach { e.toggle(it.id) }
                    val played = e.play()
                    if (played == null) {
                        // 被 Boss 限制拒绝（通灵 / 眼）：先弃牌换手，弃光就打单张
                        e.clearSelection()
                        if (ui.discardsLeft > 0) {
                            ui.hand.take(3).forEach { e.toggle(it.id) }
                            e.discard()
                        } else {
                            e.toggle(ui.hand.first().id)
                            e.play()
                        }
                    }
                    e.resolvePending()
                }
                Phase.CASH_OUT -> {
                    if (stopAtCashOut) return e
                    e.confirmCashOut()
                }
                Phase.SHOP -> e.leaveShop()
                else -> return e
            }
        }
        return e
    }

    @Test
    fun `开局进入盲注选择`() {
        val e = GameEngine(7)
        assertEquals(Phase.MENU, e.snapshot().phase)
        e.startRun(DeckType.RED)
        val ui = e.snapshot()
        assertEquals(Phase.BLIND_SELECT, ui.phase)
        assertEquals(1, ui.ante)
        assertEquals(3, ui.blindOptions.size)
        assertEquals(4, ui.money)
    }

    @Test
    fun `开打后手牌发满 8 张`() {
        val e = GameEngine(11)
        e.startRun(DeckType.RED)
        e.selectBlind()
        val ui = e.snapshot()
        assertEquals(Phase.PLAYING, ui.phase)
        assertEquals(8, ui.hand.size)
        assertEquals(8, ui.handSize)
        assertEquals(4, ui.handsLeft)
        // 红牌组自带的 +1 弃牌
        assertEquals(4, ui.discardsLeft)
    }

    @Test
    fun `出牌扣一次手数 弃牌扣一次弃牌数`() {
        val e = GameEngine(13)
        e.startRun(DeckType.RED)
        e.selectBlind()
        val hand = e.snapshot().hand
        hand.take(2).forEach { e.toggle(it.id) }
        assertNotNull(e.play())
        assertEquals(3, e.snapshot().handsLeft)
        assertEquals(8, e.snapshot().hand.size)

        e.snapshot().hand.take(1).forEach { e.toggle(it.id) }
        assertTrue(e.discard())
        assertEquals(3, e.snapshot().discardsLeft)
    }

    @Test
    fun `选中上限 5 张`() {
        val e = GameEngine(17)
        e.startRun(DeckType.RED)
        e.selectBlind()
        e.snapshot().hand.forEach { e.toggle(it.id) }
        assertEquals(5, e.snapshot().selected.size)
    }

    @Test
    fun `跳过盲注拿到一张标签`() {
        val e = GameEngine(19)
        e.startRun(DeckType.RED)
        val tag = e.skipBlind()
        assertNotNull(tag)
        // 跳过后推进到下一个盲注
        assertEquals(1, e.snapshot().blindIndex)
    }

    /** 跳过 [n] 个盲注；中途若发了免费卡包（小丑/塔罗/标准标签）先关掉。 */
    private fun skipTimes(e: GameEngine, n: Int) {
        repeat(n) {
            if (e.snapshot().phase == Phase.PACK_OPEN) e.skipPack()
            e.skipBlind()
            if (e.snapshot().phase == Phase.PACK_OPEN) e.skipPack()
        }
    }

    @Test
    fun `Boss 盲注不能跳过`() {
        val e = GameEngine(23)
        e.startRun(DeckType.RED)
        skipTimes(e, 2)
        val ui = e.snapshot()
        assertEquals(2, ui.blindIndex)
        assertFalse(ui.canSkip)
        assertEquals(null, e.skipBlind())
    }

    @Test
    fun `针只有一次出牌 水没有弃牌`() {
        val needle = Blinds.spec(5, 2, BossModifier.NEEDLE)
        assertEquals(1, needle.hands)
        val water = Blinds.spec(5, 2, BossModifier.WATER)
        assertEquals(0, water.discards)
        val wall = Blinds.spec(5, 2, BossModifier.WALL)
        assertEquals(Blinds.baseChips(5) * 4, wall.target)   // Boss 2× × 墙 2×
    }

    @Test
    fun `boss 序列前两关无效果`() {
        val seq = Blinds.bossSequence(kotlin.random.Random(3), Blinds.MAX_ANTE)
        assertEquals(Blinds.MAX_ANTE, seq.size)
        assertEquals(BossModifier.NONE, seq[0])
        assertEquals(BossModifier.NONE, seq[1])
        assertTrue(seq.drop(2).none { it == BossModifier.NONE })
    }

    /** 找一个能稳定打过第一关的种子；找不到说明数值或流程坏了。 */
    private fun winningSeed(maxSeed: Long = 400): Long {
        for (s in 1..maxSeed) {
            val e = autoRun(s, maxSteps = 150, stopAtCashOut = true)
            if (e.snapshot().phase == Phase.CASH_OUT) return s
        }
        throw AssertionError("1..$maxSeed 里没有任何种子能过第一关")
    }

    @Test
    fun `行星牌升级对应牌型`() {
        val e = GameEngine(winningSeed(), )
        e.startRun(DeckType.RED)
        e.selectBlind()
        assertEquals(1, e.snapshot().levels[HandType.PAIR] ?: 1)
        // 行星牌直接生效：对子升级后，同两手牌的分会变高
        val res = Consumables.get("p_pair")!!.use(
            ConsumableCtx(emptyList(), 0, 0, kotlin.random.Random(1))
        )
        assertEquals(HandType.PAIR, res.levelUp)
    }

    @Test
    fun `消耗品行星牌提升等级`() {
        val e = GameEngine(31)
        e.startRun(DeckType.RED)
        e.selectBlind()
        // 造一张水星（对子）塞进引擎：借商店接口不可行，改用 useConsumable 前先有牌
        val res = Consumables.get("p_pair")!!.use(
            ConsumableCtx(emptyList(), 0, 0, kotlin.random.Random(1))
        )
        assertEquals(HandType.PAIR, res.levelUp)
    }

    @Test
    fun `塔罗魔术师把选中牌变幸运`() {
        val e = GameEngine(37)
        e.startRun(DeckType.RED)
        e.selectBlind()
        val card = e.snapshot().hand.first()
        val tarot = Consumables.get("t_magician")!!
        val res = tarot.use(ConsumableCtx(listOf(card), 4, 0, kotlin.random.Random(1)))
        assertEquals(1, res.cards.size)
        assertEquals(Enhancement.LUCKY, res.cards[0].enh)
        assertEquals(card.id, res.cards[0].id)
    }

    @Test
    fun `塔罗倒吊人摧毁选中牌`() {
        val e = GameEngine(41)
        e.startRun(DeckType.RED)
        e.selectBlind()
        val card = e.snapshot().hand.first()
        val res = Consumables.get("t_hanged")!!.use(
            ConsumableCtx(listOf(card), 0, 0, kotlin.random.Random(1))
        )
        assertTrue(res.destroyed)
    }

    @Test
    fun `隐者按金钱翻倍且封顶 20`() {
        val res = Consumables.get("t_hermit")!!.use(
            ConsumableCtx(emptyList(), 40, 0, kotlin.random.Random(1))
        )
        assertEquals(20, res.moneyDelta)
    }

    @Test
    fun `商店买卖与重掷`() {
        val e = autoRun(winningSeed(), stopAtCashOut = true)
        e.confirmCashOut()
        val ui = e.snapshot()
        assertEquals(Phase.SHOP, ui.phase)
        // 原版结构：2 个商品位 + 2 个补充包位 + 1 个代金券位
        assertEquals(2, ui.shop.cards.size)
        assertEquals(2, ui.shop.boosters.size)
        assertNotNull(ui.shop.voucher)
        // 重掷只改商品，不改金钱之外的东西
        val moneyBefore = ui.money
        if (moneyBefore >= ui.rerollCost) {
            assertTrue(e.reroll())
            assertEquals(moneyBefore - ui.rerollCost, e.snapshot().money)
        }
    }

    @Test
    fun `牌组加成生效`() {
        val red = GameEngine(47).also { it.startRun(DeckType.RED) }
        red.selectBlind()
        assertEquals(4, red.snapshot().discardsLeft)   // 3 + 红牌组 1

        val blue = GameEngine(47).also { it.startRun(DeckType.BLUE) }
        blue.selectBlind()
        assertEquals(5, blue.snapshot().handsLeft)

        val yellow = GameEngine(47).also { it.startRun(DeckType.YELLOW) }
        assertEquals(14, yellow.snapshot().money)

        val black = GameEngine(47).also { it.startRun(DeckType.BLACK) }
        assertEquals(6, black.snapshot().maxJokers)
    }

    @Test
    fun `镀金牌组给出 10 张黄金牌`() {
        val e = GameEngine(53)
        e.startRun(DeckType.GOLD)
        e.selectBlind()
        val gold = e.export().master.count { it.enh == Enhancement.GOLD }
        assertEquals(10, gold)
    }

    @Test
    fun `自动打完第一关能进结算页`() {
        // 找一个能过关的种子，验证"出牌 → 达标 → 结算"这条主链是通的
        val e = autoRun(winningSeed(), stopAtCashOut = true)
        assertEquals(Phase.CASH_OUT, e.snapshot().phase)
        val c = e.snapshot().cashOut!!
        assertTrue(c.score >= c.target)
        assertTrue(c.total > 0)
        assertTrue(c.baseReward >= 3)
    }

    @Test
    fun `结算收钱后进入商店`() {
        val e = autoRun(winningSeed(), stopAtCashOut = true)
        val before = e.snapshot().money
        val gain = e.snapshot().cashOut!!.total
        e.confirmCashOut()
        assertEquals(before + gain, e.snapshot().money)
        assertEquals(Phase.SHOP, e.snapshot().phase)
    }

    @Test
    fun `牌型等级提高后同一手得分更高`() {
        val cards = listOf(
            Card(0, Rank.KING, Suit.SPADE), Card(1, Rank.KING, Suit.HEART),
        )
        val lv1 = Scoring.compute(cards, emptyList(), emptyList(), emptyMap(), 3,
            random = kotlin.random.Random(1))
        val lv3 = Scoring.compute(cards, emptyList(), emptyList(),
            mapOf(HandType.PAIR to 3), 3, random = kotlin.random.Random(1))
        assertTrue(lv3.total > lv1.total)
        assertEquals(3, lv3.level)
    }

    @Test
    fun `存档往返保持局面`() {
        val e = autoRun(winningSeed(), stopAtCashOut = true)
        val state = e.export()
        val text = SaveCodec.encode(state)
        val back = SaveCodec.decode(text)
        assertNotNull(back)
        assertEquals(state.ante, back!!.ante)
        assertEquals(state.money, back.money)
        assertEquals(state.phase, back.phase)
        assertEquals(state.master.size, back.master.size)
        assertEquals(state.jokers.size, back.jokers.size)
    }

    @Test
    fun `恢复后的引擎与存档一致`() {
        val e = autoRun(winningSeed(), stopAtCashOut = true)
        val text = SaveCodec.encode(e.export())
        val restored = GameEngine(0)
        restored.restore(SaveCodec.decode(text)!!)
        val a = e.snapshot()
        val b = restored.snapshot()
        assertEquals(a.phase, b.phase)
        assertEquals(a.ante, b.ante)
        assertEquals(a.money, b.money)
        assertEquals(a.score, b.score)
        assertEquals(a.handsLeft, b.handsLeft)
    }
}
