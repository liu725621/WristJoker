package com.wearos.wristjoker.game

/** 一步结算的来源，UI 按来源决定飘字颜色与震感强度。 */
enum class StepSource {
    /** 牌型基础值（含等级）。 */
    HAND,
    /** 逐张计分的牌。 */
    CARD,
    /** 留在手里的牌（钢铁）。 */
    HELD,
    /** 一个小丑。 */
    JOKER,
    /** 版本的额外加成（箔/全息/多彩），单独一步，让"多彩亮了"看得见。 */
    EDITION,
}

/**
 * 单步结算。
 *
 * 这是"打击感"的数据来源：原版之所以爽，是因为它不是一次性弹出最终分数，
 * 而是把「这张牌 +30 筹码 → 那张牌 ×2 倍率 → 小丑 +4 倍率」逐条演出来。
 *
 * [chipsAfter] / [multAfter] 是**执行完这一步之后**的累计值，
 * UI 直接把它们喂给滚动数字。
 */
data class ScoreStep(
    val source: StepSource,
    val cardId: Int = -1,
    /** 牌面文字（"3♠"），UI 在窄条里显示"哪张牌在得分"。 */
    val cardLabel: String = "",
    val jokerIndex: Int = -1,
    val label: String,
    val chipsAfter: Int,
    val multAfter: Double,
    val money: Int = 0,
    /** 是否是乘算（×），UI 用不同的颜色和更重的震感。 */
    val isX: Boolean = false,
)

/**
 * 一次出牌的完整计分明细。
 */
data class ScoreBreakdown(
    val handType: HandType,
    val level: Int,
    val baseChips: Int,
    val cardChips: Int,
    val jokerChips: Int,
    val totalChips: Int,
    val baseMult: Int,
    val jokerAddMult: Int,
    val xMult: Double,
    val totalMult: Double,
    /** 最终得分 = totalChips × totalMult，向下取整。 */
    val total: Long,
    /** 参与计分的牌 id，UI 用高亮。 */
    val scoringIds: Set<Int>,
    /** 逐步结算序列，供 UI 播放动画。 */
    val steps: List<ScoreStep> = emptyList(),
    /** 本手额外产出的金钱。 */
    val money: Int = 0,
    /** 本手被打碎的玻璃牌 id。 */
    val shattered: Set<Int> = emptySet(),
) {
    val summary: String get() = "${handType.cn}  ${totalChips}×${formatMult(totalMult)}"
}

internal fun formatMult(m: Double): String =
    if (m == m.toLong().toDouble()) m.toLong().toString()
    else String.format("%.1f", m)

/** 计分器：把牌型、牌面、增强、版本、蜡封、小丑合成为最终得分。 */
object Scoring {

    /** 玻璃牌打碎概率：1/6（原版 1/4，手表上单局更短，调低一点减少挫败）。 */
    private const val GLASS_BREAK_ODDS = 6

    /** 幸运牌触发概率：1/5。 */
    private const val LUCKY_ODDS = 5

    /** 幸运牌触发时的倍率。 */
    private const val LUCKY_MULT = 20

    /**
     * 解析第 [i] 个小丑实际生效的效果。
     *
     * 蓝图 / 头脑风暴不产生数值，只是把别的小丑的效果搬过来。原版这两个是
     * 构筑的核心组件（"复制右边的男爵"），所以必须支持，否则摆位策略少一半。
     * 用 guard 防自引用死循环。
     */
    private fun resolveEffect(
        i: Int,
        jokers: List<JokerInst>,
    ): Pair<JokerEffect, JokerInst>? {
        var idx = i
        var guard = 0
        while (guard++ < 12) {
            val j = jokers.getOrNull(idx) ?: return null
            val e = j.def.effect
            when {
                e.copiesRight -> idx += 1
                e.copiesLeft -> idx = 0
                else -> return e to j
            }
        }
        return null
    }

    /**
     * 计算得分。
     *
     * 结算顺序严格对齐原版：
     * 1. 牌型基础筹码/倍率（受等级与「燧石」影响）
     * 2. 逐张计分牌：牌面筹码 → 增强 → 版本 →（红蜡封：整套重来一遍）
     * 3. 手中牌的钢铁效果
     * 4. 逐个小丑（按摆位顺序）：筹码 → 加算倍率 → 乘算倍率
     * 5. 小丑自身的版本加成
     */
    fun compute(
        played: List<Card>,
        hand: List<Card>,
        jokers: List<JokerInst>,
        levels: Map<HandType, Int>,
        discardsLeft: Int,
        handsLeft: Int = 4,
        money: Int = 0,
        ante: Int = 1,
        playedTypes: Set<HandType> = emptySet(),
        /** 被 Boss 效果封锁、不参与计分的牌 id。牌型判定仍然算它们。 */
        blockedIds: Set<Int> = emptySet(),
        /** 「燧石」：牌型基础筹码与倍率减半。 */
        flint: Boolean = false,
        /** 「琥珀橡果」：小丑给出的筹码与倍率减半。 */
        amber: Boolean = false,
        /** 「绯红之心」：这一手被禁用的小丑下标。 */
        disabledJoker: Int = -1,
        deckLeft: Int = 0,
        rerolls: Int = 0,
        tarotsUsed: Int = 0,
        blindsSkipped: Int = 0,
        handsPlayed: Int = 0,
        jokerSlots: Int = 5,
        steelInDeck: Int = 0,
        stoneInDeck: Int = 0,
        destroyedFaces: Int = 0,
        discardsTotal: Int = 0,
        planetsUsed: Int = 0,
        jokersBought: Int = 0,
        jokersSold: Int = 0,
        glassShattered: Int = 0,
        deckSize: Int = 52,
        handSize: Int = 8,
        bossBlind: Boolean = false,
        /** 本局各牌型累计打出次数（不含本次，超新星）。 */
        handsPlayedCount: Map<HandType, Int> = emptyMap(),
        /** 本回合古老小丑选中的花色。 */
        ancientSuit: Suit? = null,
        /** 方尖石塔当前乘算倍率。 */
        obeliskXMult: Double = 1.0,
        /** 本局累计添加进牌堆的牌张数（全息影像）。 */
        cardsAddedToDeck: Int = 0,
        /** 「四指」：同花 / 顺子只需 4 张。 */
        fourFingers: Boolean = false,
        /** 「捷径」：顺子可隔一档。 */
        shortcut: Boolean = false,
        /** 「涂抹」：红黑花色互通。 */
        smeared: Boolean = false,
        /** 「水花」：所有打出的牌都参与计分。 */
        splash: Boolean = false,
        random: kotlin.random.Random = kotlin.random.Random(0),
    ): ScoreBreakdown {
        val result = HandEvaluator.evaluate(played, fourFingers, shortcut, smeared)
        val level = levels[result.type] ?: 1

        // Boss 封锁只影响"谁得分"，不影响"是什么牌型" —— 与原版一致
        val effective = (if (splash) played else result.scoringCards)
            .filter { it.id !in blockedIds }

        val steps = ArrayList<ScoreStep>(28)
        val shattered = HashSet<Int>()

        // ---- 1. 牌型基础值 ----
        var chips = if (flint) result.type.chipsAt(level) / 2 else result.type.chipsAt(level)
        var mult = (if (flint) result.type.multAt(level) / 2 else result.type.multAt(level)).toDouble()
        val baseChips = chips
        val baseMult = mult.toInt()
        steps += ScoreStep(
            source = StepSource.HAND,
            label = "${result.type.cn} Lv$level",
            chipsAfter = chips,
            multAfter = mult,
        )

        // ---- 2. 逐张计分牌 ----
        var cardChips = 0
        for (card in effective) {
            // 红蜡封：整套（牌面 + 增强 + 版本）再触发一次
            val triggers = if (card.seal == Seal.RED) 2 else 1
            var gained = 0
            repeat(triggers) {
                val c0 = chips
                val m0 = mult
                chips += card.chips
                gained += card.chips
                chips += card.enh.bonusChips
                gained += card.enh.bonusChips
                mult += card.enh.bonusMult
                if (card.enh.xMult != 1.0) mult *= card.enh.xMult
                if (card.enh == Enhancement.GLASS && random.nextInt(GLASS_BREAK_ODDS) == 0) {
                    shattered += card.id
                }
                if (card.enh == Enhancement.LUCKY && random.nextInt(LUCKY_ODDS) == 0) {
                    mult += LUCKY_MULT
                }
                if (chips != c0 || mult != m0) {
                    steps += ScoreStep(
                        source = StepSource.CARD,
                        cardId = card.id,
                        cardLabel = card.toString(),
                        label = card.enh.short.ifEmpty { "+${card.chips}" },
                        chipsAfter = chips,
                        multAfter = mult,
                        isX = card.enh.xMult != 1.0,
                    )
                }
                // 版本单独一步：原版"箔"的蓝光是会被玩家当成本手很强的信号
                when (card.edition) {
                    Edition.FOIL -> {
                        chips += 50; gained += 50
                        steps += ScoreStep(
                            StepSource.EDITION, card.id, card.toString(), -1,
                            "+50 箔", chips, mult,
                        )
                    }
                    Edition.HOLO -> {
                        mult += 10
                        steps += ScoreStep(
                            StepSource.EDITION, card.id, card.toString(), -1,
                            "+10 全息", chips, mult,
                        )
                    }
                    Edition.POLY -> {
                        mult *= 1.5
                        steps += ScoreStep(
                            StepSource.EDITION, card.id, card.toString(), -1,
                            "×1.5 多彩", chips, mult, isX = true,
                        )
                    }
                    Edition.BASE, Edition.NEGATIVE -> Unit
                }
            }
            cardChips += gained
        }
        var gainMoney = effective.sumOf { c ->
            (if (c.seal == Seal.GOLD) 3 else 0) + (if (c.enh == Enhancement.GOLD) 3 else 0)
        }

        // ---- 3. 手中牌的钢铁 ----
        for (card in hand) {
            if (card.enh != Enhancement.STEEL || card.id in blockedIds) continue
            val triggers = if (card.seal == Seal.RED) 2 else 1
            repeat(triggers) { mult *= 1.5 }
            steps += ScoreStep(
                source = StepSource.HELD,
                cardId = card.id,
                cardLabel = card.toString(),
                label = "×1.5",
                chipsAfter = chips,
                multAfter = mult,
                isX = true,
            )
        }
        // 蓝蜡封不在计分时给钱：它在「回合结束时仍留在手牌里」才结算，
        // 由 GameEngine.confirmCashOut 生成最后打出牌型的行星牌（原版 1.0.1f 行为）。
        // 金蜡封的 +$3 已在上面逐张牌那段算过了。

        // ---- 4. 小丑 ----
        val ctx = ScoreContext(
            played = played,
            scoring = effective,
            hand = hand,
            handType = result.type,
            level = level,
            jokerCount = jokers.size,
            jokers = jokers,
            discardsLeft = discardsLeft,
            handsLeft = handsLeft,
            money = money,
            ante = ante,
            playedTypes = playedTypes,
            deckLeft = deckLeft,
            rerolls = rerolls,
            tarotsUsed = tarotsUsed,
            blindsSkipped = blindsSkipped,
            handsPlayed = handsPlayed,
            jokerSellTotal = jokers.sumOf { it.sellPrice },
            jokerSlots = jokerSlots,
            steelInDeck = steelInDeck,
            stoneInDeck = stoneInDeck,
            destroyedFaces = destroyedFaces,
            discardsTotal = discardsTotal,
            planetsUsed = planetsUsed,
            jokersBought = jokersBought,
            jokersSold = jokersSold,
            glassShattered = glassShattered,
            deckSize = deckSize,
            handSize = handSize,
            bossBlind = bossBlind,
            handsPlayedCount = handsPlayedCount,
            ancientSuit = ancientSuit,
            obeliskXMult = obeliskXMult,
            cardsAddedToDeck = cardsAddedToDeck,
            random = random,
        )
        var jokerChips = 0
        var jokerAddMult = 0
        var xMult = 1.0
        jokers.forEachIndexed { index, j ->
            if (index == disabledJoker) {
                steps += ScoreStep(
                    source = StepSource.JOKER,
                    jokerIndex = index,
                    label = "被禁",
                    chipsAfter = chips,
                    multAfter = mult,
                )
                return@forEachIndexed
            }
            val resolved = resolveEffect(index, jokers) ?: return@forEachIndexed
            val effect = resolved.first
            val jctx = ctx.copy(currentJoker = resolved.second)
            val scale = if (amber) 0.5 else 1.0
            val c = (effect.chips(jctx) * scale).toInt()
            val a = (effect.addMult(jctx) * scale).toInt()
            val x = if (amber) 1.0 + (effect.xMult(jctx) - 1.0) * 0.5 else effect.xMult(jctx)
            val m = effect.money(jctx)
            if (c != 0) { chips += c; jokerChips += c }
            if (a != 0) { mult += a; jokerAddMult += a }
            if (x != 1.0) { mult *= x; xMult *= x }
            if (m != 0) gainMoney += m
            // 小丑自身的版本
            when (j.edition) {
                Edition.FOIL -> { chips += 50; jokerChips += 50 }
                Edition.HOLO -> { mult += 10; jokerAddMult += 10 }
                Edition.POLY -> { mult *= 1.5; xMult *= 1.5 }
                Edition.BASE, Edition.NEGATIVE -> Unit
            }
            val label = buildString {
                if (j.def.effect.copiesRight || j.def.effect.copiesLeft) append("复制→")
                if (c != 0) append("+").append(c)
                if (a != 0) { if (isNotEmpty()) append(" "); append("+").append(a).append("倍") }
                if (x != 1.0) { if (isNotEmpty()) append(" "); append("×").append(formatMult(x)) }
                if (m != 0) { if (isNotEmpty()) append(" "); append("+$").append(m) }
                if (isEmpty()) append("—")
            }
            if (c != 0 || a != 0 || x != 1.0 || m != 0) {
                steps += ScoreStep(
                    source = StepSource.JOKER,
                    jokerIndex = index,
                    label = label,
                    chipsAfter = chips,
                    multAfter = mult,
                    money = m,
                    isX = x != 1.0,
                )
            }
        }

        return ScoreBreakdown(
            handType = result.type,
            level = level,
            baseChips = baseChips,
            cardChips = cardChips,
            jokerChips = jokerChips,
            totalChips = chips,
            baseMult = baseMult,
            jokerAddMult = jokerAddMult,
            xMult = xMult,
            totalMult = mult,
            total = (chips * mult).toLong(),
            scoringIds = effective.map { it.id }.toSet(),
            steps = steps,
            money = gainMoney,
            shattered = shattered,
        )
    }
}
