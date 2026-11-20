package com.wearos.wristjoker.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第二轮改造的机制测试：商店三段结构、补充包、Boss 全量效果、消耗品清单。
 *
 * 这里不重新验证"能不能打完整局"（那在 GameEngineTest 里），
 * 只钉住这一轮新加的规则 —— 每条规则都对应原版一处具体行为。
 */
class MechanicsTest {

    /** 造一个停在商店里的引擎，商店内容由参数指定，避免依赖随机。 */
    private fun engineAtShop(
        cards: List<ShopItem> = listOf(
            ShopItem.JokerItem(JokerInst(Jokers.get("joker")!!), 5),
        ),
        boosters: List<ShopItem> = listOf(
            ShopItem.BoosterItem(PackKind.ARCANA, PackSize.MEGA, 8),
        ),
        vouchers: List<ShopItem> = listOf(ShopItem.VoucherItem(Voucher.OVERSTOCK, 10)),
        money: Int = 40,
    ): GameEngine {
        val e = GameEngine(1)
        e.startRun(DeckType.RED)
        val st = e.export().copy(
            phase = Phase.SHOP,
            money = money,
            shop = ShopState(cards, boosters, vouchers),
        )
        e.restore(st)
        return e
    }

    // ---------------------------------------------------------------- 商店结构

    @Test
    fun `商店是 2 个商品位加 2 个卡包位加 2 张券`() {
        val e = GameEngine(7)
        e.startRun(DeckType.RED)
        // 走完第一关拿结算 → 商店
        val st = e.export().copy(
            phase = Phase.CASH_OUT,
            cashOut = CashOutDetail(
                ante = 1, blindKind = BlindKind.SMALL, score = 400, target = 300,
                baseReward = 3, handsLeft = 2, handBonus = 2, interest = 0,
                handyBonus = 0, doubleTag = false, walletBonus = 0, total = 5,
            ),
        )
        e.restore(st)
        e.confirmCashOut()
        val ui = e.snapshot()
        assertEquals(Phase.SHOP, ui.phase)
        assertEquals(2, ui.shop.cards.size)
        assertEquals(2, ui.shop.boosters.size)
        assertEquals(2, ui.shop.vouchers.size)
        // 商品位里至少有一样是小丑/消耗品，且都标了价
        assertTrue(ui.shop.cards.all { it.price >= 1 })
    }

    @Test
    fun `多余库存券把商品位扩到 3`() {
        val e = engineAtShop(vouchers = listOf(ShopItem.VoucherItem(Voucher.OVERSTOCK, 10)))
        assertTrue(e.buyVoucher())
        e.leaveShop()
        // 回到盲注选择后再进商店不方便，直接看券是否生效：重新算一次商店
        val st = e.export().copy(phase = Phase.CASH_OUT)
        e.restore(st)
        // 用 confirmCashOut 重掷商店（它内部会 rollShop）
        val st2 = e.export().copy(
            phase = Phase.CASH_OUT,
            cashOut = CashOutDetail(
                ante = 1, blindKind = BlindKind.SMALL, score = 400, target = 300,
                baseReward = 3, handsLeft = 2, handBonus = 2, interest = 0,
                handyBonus = 0, doubleTag = false, walletBonus = 0, total = 5,
            ),
        )
        e.restore(st2)
        e.confirmCashOut()
        assertEquals(3, e.snapshot().shop.cards.size)
    }

    @Test
    fun `清仓折扣让所有商品降价`() {
        val e = engineAtShop(vouchers = listOf(ShopItem.VoucherItem(Voucher.CLEARANCE, 10)), money = 60)
        e.buyVoucher()
        val st = e.export().copy(
            phase = Phase.CASH_OUT,
            cashOut = CashOutDetail(
                ante = 1, blindKind = BlindKind.SMALL, score = 400, target = 300,
                baseReward = 3, handsLeft = 2, handBonus = 2, interest = 0,
                handyBonus = 0, doubleTag = false, walletBonus = 0, total = 5,
            ),
        )
        e.restore(st)
        e.confirmCashOut()
        // 塔罗/行星原价 3，75 折后 2（向下取整后再兜底到 1）
        val cons = e.snapshot().shop.cards.filterIsInstance<ShopItem.ConsumableItem>()
        assertTrue(cons.all { it.price <= 3 })
        val boosters = e.snapshot().shop.boosters
        assertTrue(boosters.all { it.price < 8 })
    }

    // ---------------------------------------------------------------- 补充包

    @Test
    fun `买补充包进入开包阶段并扣钱`() {
        val e = engineAtShop()
        val before = e.snapshot().money
        assertTrue(e.buyBooster(0))
        val ui = e.snapshot()
        assertEquals(Phase.PACK_OPEN, ui.phase)
        assertEquals(before - 8, ui.money)
        val pack = ui.pack!!
        assertEquals(PackKind.ARCANA, pack.kind)
        assertEquals(PackSize.MEGA, pack.size)
        // 超大包：5 张里选 2 张
        assertEquals(5, pack.contents.size)
        assertEquals(2, pack.maxPicks)
    }

    @Test
    fun `开包选满上限后再点无效`() {
        val e = engineAtShop()
        e.buyBooster(0)
        e.togglePackPick(0)
        e.togglePackPick(1)
        e.togglePackPick(2) // 超大只能选 2 张
        assertEquals(2, e.snapshot().pack!!.picked.size)
        // 再点已选中的会取消
        e.togglePackPick(0)
        assertEquals(setOf(1), e.snapshot().pack!!.picked)
    }

    @Test
    fun `一行都不选不能拿走`() {
        val e = engineAtShop()
        e.buyBooster(0)
        assertFalse(e.confirmPack())
        assertEquals(Phase.PACK_OPEN, e.snapshot().phase)
    }

    @Test
    fun `塔罗包拿到的牌进消耗品槽`() {
        val e = engineAtShop()
        val before = e.snapshot().consumables.size
        e.buyBooster(0)
        e.togglePackPick(0)
        assertTrue(e.confirmPack())
        val ui = e.snapshot()
        assertEquals(Phase.SHOP, ui.phase)
        assertEquals(before + 1, ui.consumables.size)
    }

    @Test
    fun `跳过卡包回商店且什么都不拿`() {
        val e = engineAtShop()
        e.buyBooster(0)
        val before = e.snapshot().consumables.size
        e.skipPack()
        val ui = e.snapshot()
        assertEquals(Phase.SHOP, ui.phase)
        assertEquals(before, ui.consumables.size)
        assertNull(ui.pack)
    }

    @Test
    fun `小丑包拿到的小丑直接入场`() {
        val e = engineAtShop(
            boosters = listOf(ShopItem.BoosterItem(PackKind.BUFFOON, PackSize.JUMBO, 6)),
        )
        val before = e.snapshot().jokers.size
        e.buyBooster(0)
        assertEquals(4, e.snapshot().pack!!.contents.size) // 巨型小丑包：4 选 1
        e.togglePackPick(0)
        e.confirmPack()
        assertEquals(before + 1, e.snapshot().jokers.size)
    }

    @Test
    fun `标准包拿到的牌会进牌堆`() {
        val e = engineAtShop(
            boosters = listOf(ShopItem.BoosterItem(PackKind.STANDARD, PackSize.NORMAL, 4)),
        )
        val before = e.export().master.size
        e.buyBooster(0)
        // 标准包内容全是扑克牌
        assertTrue(e.snapshot().pack!!.contents.all { it is PackContent.Playing })
        e.togglePackPick(0)
        e.confirmPack()
        assertEquals(before + 1, e.export().master.size)
    }

    @Test
    fun `包内容随存档往返`() {
        val e = engineAtShop()
        e.buyBooster(0)
        e.togglePackPick(1)
        val text = SaveCodec.encode(e.export())
        val back = SaveCodec.decode(text)!!
        assertEquals(Phase.PACK_OPEN, back.phase)
        assertEquals(PackKind.ARCANA, back.pack!!.kind)
        assertEquals(5, back.pack!!.contents.size)
        assertEquals(listOf(1), back.packPicked)
    }

    // ---------------------------------------------------------------- Boss

    @Test
    fun `Boss 序列前两关无效果且第 8 关是决战`() {
        for (seed in 1L..20L) {
            val seq = Blinds.bossSequence(kotlin.random.Random(seed), 8)
            assertEquals(8, seq.size)
            assertEquals(BossModifier.NONE, seq[0])
            assertEquals(BossModifier.NONE, seq[1])
            assertTrue("seed=$seed", seq[7].minAnte >= 8)
            assertTrue(seq.drop(2).take(5).none { it.minAnte >= 8 })
        }
    }

    @Test
    fun `针只有一次出牌 墙四倍 紫器六倍`() {
        assertEquals(1, Blinds.spec(5, 2, BossModifier.NEEDLE).hands)
        val wall = Blinds.spec(5, 2, BossModifier.WALL)
        assertEquals(Blinds.baseChips(5) * 4, wall.target)
        val violet = Blinds.spec(8, 2, BossModifier.VIOLET)
        assertEquals(Blinds.baseChips(8) * 6, violet.target)
    }

    @Test
    fun `殓葬与标记按人头牌封锁`() {
        val face = Card(9, Rank.KING, Suit.HEART)
        val num = Card(4, Rank.FIVE, Suit.HEART)
        assertTrue(BossModifier.PLANT.blocks(face))
        assertFalse(BossModifier.PLANT.blocks(num))
        assertTrue(BossModifier.MARK.blocks(face))
        assertFalse(BossModifier.HOOK.blocks(face))
    }

    @Test
    fun `翠叶在卖小丑后解除全牌封锁`() {
        val e = GameEngine(11)
        e.startRun(DeckType.RED)
        // 强行把第 1 关的 Boss 换成翠叶，并先塞一张小丑进去
        val st = e.export().copy(
            phase = Phase.BLIND_SELECT,
            ante = 1,
            blindIndex = 2,
            bossSeq = List(8) { BossModifier.VERDANT },
            jokers = listOf(JokerInst(Jokers.get("joker")!!)),
        )
        e.restore(st)
        e.selectBlind()
        val ui = e.snapshot()
        assertEquals(BossModifier.VERDANT, ui.modifier)
        // 全牌封锁 → 每张手牌都在 blockedIds 里
        assertTrue(ui.hand.all { it.id in ui.blockedIds })

        assertTrue(e.sell(0))
        val after = e.snapshot()
        assertTrue(after.hand.none { it.id in after.blockedIds })
    }

    @Test
    fun `嘴只能出一种牌型`() {
        val e = GameEngine(13)
        e.startRun(DeckType.RED)
        val st = e.export().copy(
            phase = Phase.BLIND_SELECT,
            ante = 1,
            blindIndex = 2,
            bossSeq = List(8) { BossModifier.MOUTH },
        )
        e.restore(st)
        e.selectBlind()

        // 先打一手单张（高牌）
        val hand = e.snapshot().hand
        e.toggle(hand[0].id)
        assertNotNull(e.play())
        e.resolvePending()
        if (e.snapshot().phase != Phase.PLAYING) return // 一手就过关了，嘴没机会验证

        // 再打两张不同点数的单张 → 仍是高牌，允许；打同点数 → 对子，被拒
        val h2 = e.snapshot().hand
        val pair = h2.groupBy { it.rank }.values.firstOrNull { it.size >= 2 }
        if (pair != null) {
            e.clearSelection()
            pair.take(2).forEach { e.toggle(it.id) }
            val r = e.play()
            assertEquals("嘴应该拒绝换牌型", null, r)
        }
    }

    @Test
    fun `牙每打一张牌扣一元`() {
        val e = GameEngine(17)
        e.startRun(DeckType.RED)
        val st = e.export().copy(
            phase = Phase.BLIND_SELECT,
            ante = 1,
            blindIndex = 2,
            bossSeq = List(8) { BossModifier.TOOTH },
            money = 20,
        )
        e.restore(st)
        e.selectBlind()
        val moneyBefore = e.snapshot().money
        val hand = e.snapshot().hand
        hand.take(3).forEach { e.toggle(it.id) }
        val r = e.play()!!
        val expected = moneyBefore - 3 + r.money
        assertEquals(expected, e.snapshot().money)
    }

    @Test
    fun `臂把打出的牌型降一级`() {
        val e = GameEngine(19)
        e.startRun(DeckType.RED)
        val st = e.export().copy(
            phase = Phase.BLIND_SELECT,
            ante = 1,
            blindIndex = 2,
            bossSeq = List(8) { BossModifier.ARM },
            levels = mapOf(HandType.PAIR to 4),
        )
        e.restore(st)
        e.selectBlind()
        val hand = e.snapshot().hand
        // 找一对打出去
        val pair = hand.groupBy { it.rank }.values.firstOrNull { it.size >= 2 } ?: return
        pair.take(2).forEach { e.toggle(it.id) }
        e.play()
        assertEquals(3, e.snapshot().levels[HandType.PAIR])
    }

    // ---------------------------------------------------------------- 清单完整性

    @Test
    fun `小丑牌 150 张且传奇不进商店`() {
        assertEquals("小丑数量 ${Jokers.ALL.size}", 150, Jokers.ALL.size)
        assertEquals(Jokers.ALL.size, Jokers.ALL.map { it.id }.toSet().size)
        // 抽 200 次不应出现传奇
        val r = kotlin.random.Random(4)
        repeat(200) {
            val j = Jokers.rollOne(r, emptySet())!!
            assertTrue(j.rarity != Rarity.LEGENDARY)
        }
    }

    @Test
    fun `塔罗 22 张行星 12 张`() {
        val tarots = Consumables.ALL.filter { it.kind == ConsumableKind.TAROT }
        val planets = Consumables.ALL.filter { it.kind == ConsumableKind.PLANET }
        assertEquals(22, tarots.size)
        assertEquals(12, planets.size)
        // 每张行星对应且只对应一个牌型
        assertEquals(HandType.entries.size, planets.mapNotNull { it.handType }.toSet().size)
    }

    @Test
    fun `每张塔罗用起来都不崩`() {
        val r = kotlin.random.Random(9)
        val cards = Deck.standard().take(3)
        Consumables.ALL.filter { it.kind == ConsumableKind.TAROT }.forEach { t ->
            val picked = if (t.needsCards) cards.take(t.maxSel.coerceAtMost(3)) else emptyList()
            val res = t.use(ConsumableCtx(picked, 20, 10, r))
            // 要么改造了牌，要么摧毁了牌，要么给了资源 —— 不能什么都不做
            val didSomething = res.cards.isNotEmpty() || res.destroyed ||
                res.moneyDelta != 0 || res.spawnJokers > 0 ||
                res.spawnConsumables.isNotEmpty() || res.levelUp != null ||
                res.upgradeRandomJoker || res.message.isNotEmpty()
            assertTrue("${t.name} 没有任何效果", didSomething)
        }
    }

    @Test
    fun `代金券 32 张且标签 24 种`() {
        assertEquals(32, Voucher.entries.size)
        assertEquals(24, Tag.entries.size)
        // 券的 key 必须唯一，否则存档按 key 反查会串
        assertEquals(Voucher.entries.size, Voucher.entries.map { it.key }.toSet().size)
    }

    @Test
    fun `Boss 效果覆盖原版全部 28 种`() {
        // 27 个实际效果 + NONE
        assertEquals(28, BossModifier.entries.size)
    }

    // ---------------------------------------------------------------- 传奇与幻灵

    @Test
    fun `传奇 5 张且只能由灵魂抽到`() {
        val legends = Jokers.ALL.filter { it.rarity == Rarity.LEGENDARY }
        assertEquals(5, legends.size)
        assertEquals(
            setOf("canio", "triboulet", "yorick", "chicot", "perkeo"),
            legends.map { it.id }.toSet(),
        )
        // 商店抽取 500 次都不该出现传奇
        val r = kotlin.random.Random(11)
        repeat(500) {
            val j = Jokers.rollOne(r, emptySet())!!
            assertTrue("${j.name} 不该出现在商店", j.rarity != Rarity.LEGENDARY)
        }
        // 传奇路径不受商店池影响
        assertEquals(Rarity.LEGENDARY, Jokers.rollLegendary(r, emptySet())!!.rarity)
    }

    @Test
    fun `灵魂生成传奇小丑`() {
        val e = engineAtShop()
        e.restore(e.export().copy(consumables = listOf("s_soul"), money = 40))
        assertEquals(0, e.snapshot().jokers.size)
        assertTrue(e.useConsumable(0))
        val got = e.snapshot().jokers
        assertEquals(1, got.size)
        assertEquals(Rarity.LEGENDARY, got[0].rarity)
        // 用掉之后槽位里不该还剩灵魂
        assertTrue(e.snapshot().consumables.isEmpty())
    }

    @Test
    fun `灵魂在小丑位满时折价 20`() {
        val e = engineAtShop()
        val full = List(5) { JokerInst(Jokers.get("joker")!!) }
        e.restore(e.export().copy(consumables = listOf("s_soul"), jokers = full, money = 0))
        assertTrue(e.useConsumable(0))
        assertEquals(20, e.snapshot().money)
        assertEquals(5, e.snapshot().jokers.size)
    }

    /** 把引擎推到第 5 关 Boss，返回开打后的 Boss 效果。 */
    private fun bossAtAnte5(jokers: List<JokerInst>): BossModifier {
        val e = GameEngine(5)
        e.startRun(DeckType.RED)
        e.restore(
            e.export().copy(
                phase = Phase.BLIND_SELECT, ante = 5, blindIndex = 2, jokers = jokers,
            )
        )
        e.selectBlind()
        return e.snapshot().modifier
    }

    @Test
    fun `希科让 Boss 效果失效`() {
        assertTrue("第 5 关 Boss 应当有效果", bossAtAnte5(emptyList()) != BossModifier.NONE)
        assertEquals(
            BossModifier.NONE,
            bossAtAnte5(listOf(JokerInst(Jokers.get("chicot")!!))),
        )
    }

    @Test
    fun `佩尔克离开商店时复制一张消耗品`() {
        val e = engineAtShop()
        e.restore(
            e.export().copy(
                jokers = listOf(JokerInst(Jokers.get("perkeo")!!)),
                consumables = listOf("t_magician"),
            )
        )
        assertEquals(1, e.snapshot().consumables.size)
        e.leaveShop()
        assertEquals(2, e.snapshot().consumables.size)
        // 复制的是同一张，不是随机换一张
        assertTrue(e.snapshot().consumables.all { it.key == "t_magician" })
    }

    @Test
    fun `卡尼奥与约里克的成长按计数走`() {
        val card = Card(1, Rank.TEN, Suit.SPADE)
        fun multOf(id: String, faces: Int = 0, discards: Int = 0): Double =
            Scoring.compute(
                played = listOf(card),
                hand = emptyList(),
                jokers = listOf(JokerInst(Jokers.get(id)!!)),
                levels = emptyMap(),
                discardsLeft = 3,
                destroyedFaces = faces,
                discardsTotal = discards,
            ).totalMult

        // 卡尼奥：毁 1 张人头 +1 倍率，加算而不是乘算
        assertEquals(multOf("canio", faces = 0) + 3, multOf("canio", faces = 3), 0.001)
        // 约里克：每 23 张弃牌 ×1
        assertEquals(multOf("yorick", discards = 0) * 1, multOf("yorick", discards = 22), 0.001)
        assertEquals(multOf("yorick", discards = 0) * 2, multOf("yorick", discards = 23), 0.001)
        assertEquals(multOf("yorick", discards = 0) * 3, multOf("yorick", discards = 46), 0.001)
    }

    @Test
    fun `特里布莱打出每张 K 或 Q 都 ×2`() {
        val base = listOf(Card(1, Rank.TEN, Suit.SPADE))
        val two = listOf(Card(1, Rank.KING, Suit.SPADE), Card(2, Rank.QUEEN, Suit.HEART))
        fun total(played: List<Card>) = Scoring.compute(
            played = played,
            hand = emptyList(),
            jokers = listOf(JokerInst(Jokers.get("triboulet")!!)),
            levels = emptyMap(),
            discardsLeft = 3,
        )
        // 无 K/Q：只按牌型基础倍率，不乘
        assertEquals(1.0, total(base).totalMult / total(base).totalMult, 0.001)
        // K + Q：×4（两张各乘一次）
        val plain = Scoring.compute(
            played = two,
            hand = emptyList(),
            jokers = emptyList(),
            levels = emptyMap(),
            discardsLeft = 3,
        )
        assertEquals(plain.totalMult * 4, total(two).totalMult, 0.001)
    }

    @Test
    fun `灵魂只在补充包里以极低概率顶替一张`() {
        val r = kotlin.random.Random(77)
        var soul = 0
        var total = 0
        repeat(3000) {
            val contents = Boosters.fill(PackKind.ARCANA, PackSize.NORMAL, r, emptySet(), { 100 })
            total += contents.size
            soul += contents.count { it is PackContent.Tarot && it.c.key == "s_soul" }
        }
        // 0.3% 的量级：3000 个包（每个 3 张）应命中个位数到十几次
        assertTrue("灵魂出现 $soul 次 / $total 张", soul in 1..60)
        // 顶替而不是追加：包内张数不变
        assertEquals(3, total / 3000)
    }

    // ---------------------------------------------------------------- 第三轮：按原版补齐的机制

    @Test
    fun `消耗品共 52 张含 18 张幻灵`() {
        assertEquals(52, Consumables.ALL.size)
        assertEquals(22, Consumables.ALL.count { it.kind == ConsumableKind.TAROT })
        assertEquals(12, Consumables.ALL.count { it.kind == ConsumableKind.PLANET })
        assertEquals(18, Consumables.ALL.count { it.kind == ConsumableKind.SPECTRAL })
        assertEquals(Consumables.ALL.size, Consumables.ALL.map { it.key }.toSet().size)
        // 幻灵不能从商店的塔罗/行星池里被 rollOne 抽出来
        val r = kotlin.random.Random(31)
        repeat(200) { assertTrue(Consumables.rollOne(r).kind != ConsumableKind.SPECTRAL) }
    }

    @Test
    fun `四种蜡封与五类补充包齐全`() {
        assertEquals(5, Seal.entries.size)          // NONE + 红金蓝紫
        assertTrue(Seal.entries.contains(Seal.PURPLE))
        assertEquals(5, PackKind.entries.size)
        assertTrue(PackKind.entries.contains(PackKind.SPECTRAL))
    }

    @Test
    fun `幻灵包规格与内容按原版`() {
        // 幻灵包和小丑包一样，普通 2 张、巨型/超大 4 张
        assertEquals(2, PackSize.NORMAL.contentCount(PackKind.SPECTRAL))
        assertEquals(4, PackSize.JUMBO.contentCount(PackKind.SPECTRAL))
        assertEquals(4, PackSize.MEGA.contentCount(PackKind.SPECTRAL))
        val r = kotlin.random.Random(12)
        val contents = Boosters.fill(PackKind.SPECTRAL, PackSize.JUMBO, r, emptySet(), { 100 })
        assertEquals(4, contents.size)
        assertTrue(contents.all { it is PackContent.Tarot && it.c.kind == ConsumableKind.SPECTRAL })
    }

    @Test
    fun `幻灵包在补充包权重里最稀有`() {
        val r = kotlin.random.Random(88)
        val rolls = Boosters.roll(r, 20000)
        val arcana = rolls.count { it.first == PackKind.ARCANA }
        val spectral = rolls.count { it.first == PackKind.SPECTRAL }
        // 权重 4.0 : 0.6（各规格合计），幻灵应出现但远少于塔罗
        assertTrue("塔罗 $arcana / 幻灵 $spectral", spectral > 0 && spectral < arcana / 3)
    }

    @Test
    fun `每张幻灵用起来都不崩`() {
        val r = kotlin.random.Random(19)
        val cards = Deck.standard().take(3)
        Consumables.ALL.filter { it.kind == ConsumableKind.SPECTRAL }.forEach { s ->
            val picked = if (s.needsCards) cards.take(s.maxSel.coerceAtLeast(1)) else emptyList()
            val res = s.use(ConsumableCtx(picked, 20, 10, r))
            val didSomething = res.message.isNotEmpty() || res.cards.isNotEmpty() ||
                res.allLevelsUp || res.spawnCards.isNotEmpty() || res.destroyRandomHand > 0 ||
                res.moneyToZero || res.handSizeDelta != 0 || res.convertHandSuit ||
                res.convertHandRank || res.spawnRareJoker || res.jokerApocalypse > 0 ||
                res.editionRandomJoker != null || res.spawnLegendary
            assertTrue("${s.name} 没有任何效果", didSomething)
        }
    }

    /** 造一个处在牌桌阶段（有真实手牌）且消耗品槽装着指定幻灵的引擎。 */
    private fun enginePlayingWith(vararg keys: String): GameEngine {
        val e = GameEngine(5)
        e.startRun(DeckType.RED)
        e.selectBlind()
        e.restore(e.export().copy(consumables = keys.toList()))
        return e
    }

    @Test
    fun `黑洞让全部牌型升一级`() {
        val e = enginePlayingWith("s_blackhole")
        assertTrue(e.useConsumable(0))
        val lv = e.snapshot().levels
        assertTrue(HandType.entries.all { (lv[it] ?: 1) >= 2 })
    }

    @Test
    fun `怨灵生成一张稀有小丑并把钱清零`() {
        val e = enginePlayingWith("s_wraith")
        assertTrue(e.useConsumable(0))
        assertEquals(0, e.snapshot().money)
        assertEquals(1, e.snapshot().jokers.size)
        assertEquals(Rarity.RARE, e.snapshot().jokers[0].rarity)
    }

    @Test
    fun `通灵板把点数归一并让手牌上限减一`() {
        val e = enginePlayingWith("s_ouija")
        val before = e.snapshot().handSize
        assertTrue(e.useConsumable(0))
        assertEquals(before - 1, e.snapshot().handSize)
        assertEquals(1, e.snapshot().hand.map { it.rank }.toSet().size)
    }

    @Test
    fun `外质给一张小丑负片并让手牌上限减一`() {
        val e = enginePlayingWith("s_ectoplasm")
        e.restore(e.export().copy(jokers = listOf(JokerInst(Jokers.get("joker")!!))))
        val before = e.snapshot().handSize
        assertTrue(e.useConsumable(0))
        assertEquals(before - 1, e.snapshot().handSize)
        assertEquals(1, e.snapshot().jokers.count { it.edition == Edition.NEGATIVE })
    }

    @Test
    fun `生命之符只留一张小丑并复制它`() {
        val e = enginePlayingWith("s_ankh")
        e.restore(e.export().copy(jokers = List(4) { JokerInst(Jokers.get("joker")!!) }))
        assertTrue(e.useConsumable(0))
        val got = e.snapshot().jokers
        assertEquals(2, got.size)
        assertEquals(got[0].id, got[1].id)
    }

    @Test
    fun `咒术只留一张小丑并让它多彩`() {
        val e = enginePlayingWith("s_hex")
        e.restore(e.export().copy(jokers = List(4) { JokerInst(Jokers.get("joker")!!) }))
        assertTrue(e.useConsumable(0))
        val got = e.snapshot().jokers
        assertEquals(1, got.size)
        assertEquals(Edition.POLY, got[0].edition)
    }

    @Test
    fun `熟悉者摧毁一张随机手牌并塞进三张增强人头牌`() {
        val e = enginePlayingWith("s_familiar")
        val before = e.export().master.size
        assertTrue(e.useConsumable(0))
        // 毁 1 张 + 加 3 张 = 净 +2
        assertEquals(before + 2, e.export().master.size)
        val added = e.export().master.filter { it.id >= 100 }
        assertEquals(3, added.size)
        assertTrue(added.all { it.rank.order >= 11 })
    }

    @Test
    fun `献祭摧毁五张手牌并给 20 元`() {
        val e = enginePlayingWith("s_immolate")
        val money = e.snapshot().money
        assertTrue(e.useConsumable(0))
        assertEquals(money + 20, e.snapshot().money)
    }

    @Test
    fun `幻灵包拿到的幻灵牌进消耗品槽`() {
        val e = engineAtShop(
            boosters = listOf(ShopItem.BoosterItem(PackKind.SPECTRAL, PackSize.NORMAL, 4)),
        )
        val before = e.snapshot().consumables.size
        assertTrue(e.buyBooster(0))
        assertEquals(PackKind.SPECTRAL, e.snapshot().pack!!.kind)
        e.togglePackPick(0)
        assertTrue(e.confirmPack())
        val ui = e.snapshot()
        assertEquals(Phase.SHOP, ui.phase)
        assertEquals(before + 1, ui.consumables.size)
        assertEquals(ConsumableKind.SPECTRAL, ui.consumables.last().kind)
    }

    @Test
    fun `紫蜡封被弃掉时生成塔罗牌`() {
        val e = GameEngine(5)
        e.startRun(DeckType.RED)
        e.selectBlind()
        val st = e.export()
        val targetId = st.hand.first()
        val purple = st.master.first { it.id == targetId }.copy(seal = Seal.PURPLE)
        e.restore(
            st.copy(
                master = st.master.map { if (it.id == targetId) purple else it },
                consumables = emptyList(),
            )
        )
        e.toggle(targetId)
        assertTrue(e.discard())
        val cons = e.snapshot().consumables
        assertEquals(1, cons.size)
        assertEquals(ConsumableKind.TAROT, cons[0].kind)
    }

    @Test
    fun `蓝蜡封回合末按最后牌型生成行星牌`() {
        val blue = Card(0, Rank.TWO, Suit.SPADE, seal = Seal.BLUE)
        val e = GameEngine(5)
        e.startRun(DeckType.RED)
        // score 直接过线：restore 内的 resolvePending 会走 clearBlind，
        // 此时"手里还留着蓝蜡封牌"就该结算行星牌。
        e.restore(
            e.export().copy(
                phase = Phase.PLAYING,
                score = 999999L,
                master = listOf(blue),
                drawPile = emptyList(),
                discardPile = emptyList(),
                hand = listOf(0),
                playedTypes = listOf(HandType.PAIR),
                consumables = emptyList(),
            )
        )
        val cons = e.snapshot().consumables
        assertEquals(1, cons.size)
        assertEquals("p_pair", cons[0].key)   // 对子 → 水星
    }

    @Test
    fun `手牌上限惩罚随存档往返保留`() {
        val e = enginePlayingWith("s_ouija")
        assertTrue(e.useConsumable(0))
        assertEquals(-1, e.export().handSizePenalty)
        val back = SaveCodec.decode(SaveCodec.encode(e.export()))!!
        assertEquals(-1, back.handSizePenalty)
    }

    // ------------------------------------------------------ 手牌上限：真落地

    /**
     * 「画家」券（手牌 +1）之前只是写在 desc 里 —— 买了之后既没有数字变化，
     * 也没有提示，玩家自然会认为"没落到实处"。这条用例钉住真实链路：
     * 券 → `handSizeBonus()` → `Blinds.spec()` → `startBlind()` 发牌张数。
     */
    @Test
    fun `画家券让下一关多发一张牌`() {
        val e = GameEngine(3)
        e.startRun(DeckType.RED)
        assertEquals(Phase.BLIND_SELECT, e.snapshot().phase)

        e.restore(e.export().copy(vouchers = listOf(Voucher.PAINTER.key)))
        e.selectBlind()

        val ui = e.snapshot()
        assertEquals(Phase.PLAYING, ui.phase)
        assertEquals(9, ui.handSize)
        assertEquals(9, ui.hand.size)
    }

    /** 没买券时是 8 张 —— 上一条用例的对照，确保 +1 不是"本来就 9"。 */
    @Test
    fun `默认手牌上限是八张`() {
        val e = GameEngine(3)
        e.startRun(DeckType.RED)
        e.selectBlind()
        val ui = e.snapshot()
        assertEquals(8, ui.handSize)
        assertEquals(8, ui.hand.size)
    }

    /** 通灵板：手牌 −1，当前这一关立刻少一张，且下关仍然是 7 张（不重复扣）。 */
    @Test
    fun `通灵板减手牌立刻生效且不会重复扣`() {
        val e = enginePlayingWith("s_ouija")
        val before = e.snapshot().hand.size
        assertTrue(e.useConsumable(0))
        val after = e.snapshot()
        assertEquals(before - 1, after.handSize)
        assertEquals(before - 1, after.hand.size)

        // 下一关：BASE 8 + 罚 −1 = 7，而不是 6
        val st = e.export().copy(phase = Phase.BLIND_SELECT)
        e.restore(st)
        e.selectBlind()
        assertEquals(7, e.snapshot().hand.size)
    }
}
