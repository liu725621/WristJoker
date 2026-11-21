package com.wearos.wristjoker.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 存档格式的容错性。这是"自动存档"能不能信的唯一保障。 */
class SaveCodecTest {

    private fun sample(): RunState {
        val master = Deck.standard()
        return RunState(
            seed = 123456789L,
            deckType = DeckType.GLASS,
            phase = Phase.PLAYING,
            ante = 4,
            blindIndex = 2,
            score = 8213,
            money = 17,
            handsLeft = 2,
            discardsLeft = 1,
            handSize = 8,
            master = master.mapIndexed { i, c ->
                when {
                    i == 0 -> c.copy(enh = Enhancement.GLASS, seal = Seal.RED, edition = Edition.POLY)
                    i == 1 -> c.copy(enh = Enhancement.STEEL)
                    else -> c
                }
            },
            drawPile = listOf(5, 6, 7),
            discardPile = listOf(8, 9),
            hand = listOf(2, 3, 4),
            jokers = listOf(
                JokerInst(Jokers.get("joker")!!, Edition.FOIL),
                JokerInst(Jokers.get("baron")!!),
                JokerInst(Jokers.get("ceremonial")!!, growthMult = 9),
            ),
            consumables = listOf("p_pair", "t_hermit"),
            levels = mapOf(HandType.PAIR to 3, HandType.FLUSH to 2),
            vouchers = listOf("painter", "reroll"),
            bossSeq = Blinds.bossSequence(kotlin.random.Random(5), 8),
            pendingTags = listOf(Tag.SPEED, Tag.ECONOMY),
            playedTypes = listOf(HandType.PAIR),
            destroyed = emptyList(),
            shop = ShopState(
                cards = listOf(
                    ShopItem.JokerItem(JokerInst(Jokers.get("jolly")!!), 5),
                    ShopItem.ConsumableItem(Consumables.get("p_flush")!!, 3, sold = true),
                ),
                boosters = listOf(
                    ShopItem.BoosterItem(PackKind.ARCANA, PackSize.JUMBO, 6),
                    ShopItem.BoosterItem(PackKind.BUFFOON, PackSize.NORMAL, 4, sold = true),
                ),
                vouchers = listOf(
                    ShopItem.VoucherItem(Voucher.PAINTER, 10),
                    ShopItem.VoucherItem(Voucher.OVERSTOCK, 10, sold = true),
                ),
            ),
            rerollCost = 7,
            cashOut = null,
            handsPlayed = 9,
            bestHand = 4410,
            blindsCleared = 8,
            lastGain = 12,
            endless = false,
            message = "凑够 5000 分",
            target = 5000,
            obeliskXMult = 2.4,
            cardsAddedToDeck = 6,
            ancientSuit = Suit.CLUB,
        )
    }

    @Test
    fun `编码再解码后字段一致`() {
        val s = sample()
        val back = SaveCodec.decode(SaveCodec.encode(s))
        assertNotNull(back)
        back!!
        assertEquals(s.seed, back.seed)
        assertEquals(s.deckType, back.deckType)
        assertEquals(s.phase, back.phase)
        assertEquals(s.ante, back.ante)
        assertEquals(s.blindIndex, back.blindIndex)
        assertEquals(s.score, back.score)
        assertEquals(s.money, back.money)
        assertEquals(s.handsLeft, back.handsLeft)
        assertEquals(s.discardsLeft, back.discardsLeft)
        assertEquals(s.handSize, back.handSize)
        assertEquals(s.master, back.master)
        assertEquals(s.drawPile, back.drawPile)
        assertEquals(s.discardPile, back.discardPile)
        assertEquals(s.hand, back.hand)
        assertEquals(s.jokers, back.jokers)
        assertEquals(s.consumables, back.consumables)
        assertEquals(s.levels, back.levels)
        assertEquals(s.vouchers, back.vouchers)
        assertEquals(s.bossSeq, back.bossSeq)
        assertEquals(s.pendingTags, back.pendingTags)
        assertEquals(s.playedTypes, back.playedTypes)
        assertEquals(s.shop, back.shop)
        assertEquals(s.rerollCost, back.rerollCost)
        assertEquals(s.handsPlayed, back.handsPlayed)
        assertEquals(s.bestHand, back.bestHand)
        assertEquals(s.blindsCleared, back.blindsCleared)
        assertEquals(s.endless, back.endless)
        assertEquals(s.message, back.message)
        // 卡片（Tile）靠这一行显示进度：旧档缺它时解码为 0，卡片会退化成只显示分数
        assertEquals(s.target, back.target)
        // 有状态小丑的跨局状态（方尖石塔 / 全息影像 / 古老小丑 / 仪式匕首成长值）
        assertEquals(s.obeliskXMult, back.obeliskXMult, 0.001)
        assertEquals(s.cardsAddedToDeck, back.cardsAddedToDeck)
        assertEquals(s.ancientSuit, back.ancientSuit)
    }

    @Test
    fun `牌的增强蜡封版本能原样还原`() {
        val s = sample()
        val back = SaveCodec.decode(SaveCodec.encode(s))!!
        val c0 = back.master.first { it.id == 0 }
        assertEquals(Enhancement.GLASS, c0.enh)
        assertEquals(Seal.RED, c0.seal)
        assertEquals(Edition.POLY, c0.edition)
    }

    @Test
    fun `空串与坏数据返回 null`() {
        assertNull(SaveCodec.decode(""))
        assertNull(SaveCodec.decode("   "))
        assertNull(SaveCodec.decode("这不是存档"))
        // 没有版本号行
        assertNull(SaveCodec.decode("seed|1\ndeck|RED"))
    }

    @Test
    fun `未来版本拒绝加载`() {
        val text = SaveCodec.encode(sample()).replaceFirst("v|2", "v|99")
        assertNull(SaveCodec.decode(text))
    }

    @Test
    fun `缺字段时用安全默认值`() {
        val text = "v|1\nseed|42\ndeck|BLUE\nphase|PLAYING\n"
        val s = SaveCodec.decode(text)
        assertNotNull(s)
        assertEquals(1, s!!.ante)
        assertEquals(DeckType.BLUE, s.deckType)
        assertEquals(Phase.PLAYING, s.phase)
        assertTrue(s.master.isEmpty())
        assertEquals(Blinds.BASE_HANDS, s.handsLeft)
        assertEquals(4, s.money)
    }

    @Test
    fun `单行损坏不影响其他字段`() {
        val text = SaveCodec.encode(sample()) + "junk line without separator\n"
        val s = SaveCodec.decode(text)
        assertNotNull(s)
        assertEquals(4, s!!.ante)
    }

    @Test
    fun `主表缺失的牌 id 会被丢掉`() {
        // hand 里写了 999，主表没有这张牌 → 恢复时不应带进手牌
        val s = sample().copy(hand = listOf(2, 999))
        val back = SaveCodec.decode(SaveCodec.encode(s))!!
        assertEquals(listOf(2), back.hand)
    }
}
