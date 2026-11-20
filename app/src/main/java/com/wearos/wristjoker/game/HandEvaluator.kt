package com.wearos.wristjoker.game

/**
 * 一次出牌判定的结果。
 *
 * [scoringCards] 只包含"真正参与计分"的牌 —— 与 Balatro 一致：
 * 高牌只算最大的一张，对子只算那两张，顺子/同花/葫芦/五条算全部打出的牌。
 * 其余牌视为"被带出但不得分"，UI 需要用不同样式区分。
 */
data class HandResult(
    val type: HandType,
    val scoringCards: List<Card>,
    /** 参与牌型判定的牌（已剔除石头牌）。 */
    val realCards: List<Card> = emptyList(),
) {
    /** 皇家同花顺是 10 以上的同花顺，显示时给玩家更明确的反馈。 */
    val isRoyal: Boolean
        get() = type == HandType.STRAIGHT_FLUSH &&
            scoringCards.size == 5 &&
            scoringCards.all { it.rank.order >= 10 }

    val displayName: String
        get() = if (isRoyal) "皇家同花顺" else type.cn

    /** 本次判定牌型提供的筹码（不含牌型基础值与等级）。 */
    val chips: Int get() = scoringCards.sumOf { it.chips }
}

/**
 * 牌型判定器。
 *
 * 纯函数、无状态，便于在 JVM 单元测试中穷举验证。
 *
 * 两个原版规则在这里落地：
 * - **石头牌（Stone）没有点数也没有花色**，不参与牌型判定，但总是计分并给 50 筹码；
 * - **万能牌（Wild）**在「同花」判定里可以当任意花色。
 */
object HandEvaluator {

    /**
     * 判定 [cards]（1..5 张）构成的最强牌型。
     *
     * 判定顺序严格由强到弱，先命中先返回 —— 这与 Balatro 的
     * "高等级牌型优先于低等级牌型"一致：例如 K K K K 2 永远是四条而不是同花。
     *
     * 三个规则改写小丑在这里落地（默认关闭，保持纯标准判定）：
     * - [fourFingers]「四指」：同花 / 顺子只需 4 张；
     * - [shortcut]「捷径」：顺子允许隔一档（2 4 6 8 10）；
     * - [smeared]「涂抹」：红黑花色互通（红桃=方块、黑桃=梅花）。
     *
     * 这三个不是装饰：没有它们，"四指 + 同花牌型"这条经典构筑根本不存在，
     * 玩家买到四指会发现毫无变化 —— 上一版就是如此。
     */
    fun evaluate(
        cards: List<Card>,
        fourFingers: Boolean = false,
        shortcut: Boolean = false,
        smeared: Boolean = false,
    ): HandResult {
        if (cards.isEmpty()) return HandResult(HandType.HIGH_CARD, emptyList(), emptyList())

        val stones = cards.filter { it.enh.stone }
        val real = cards.filter { !it.enh.stone }
        // 全是石头牌：没有点数，只能算高牌，但每张都计分
        if (real.isEmpty()) return HandResult(HandType.HIGH_CARD, stones, real)

        fun r(type: HandType, scoring: List<Card>) =
            HandResult(type, scoring + stones, real)

        val n = real.size
        val byRank = real.groupBy { it.rank }.toList()
            .sortedWith(
                compareByDescending<Pair<Rank, List<Card>>> { it.second.size }
                    .thenByDescending { it.first.order }
            )
        val counts = byRank.map { it.second.size }
        val flush = if (fourFingers) n in 4..5 && isFlush(real, smeared)
        else n == 5 && isFlush(real, smeared)
        val straight = if (fourFingers) n in 4..5 && isStraight(real, shortcut)
        else n == 5 && isStraight(real, shortcut)

        // 1) 同花五条：5 张同点数且同花色
        if (n == 5 && counts[0] == 5 && flush) return r(HandType.FLUSH_FIVE, real)
        // 2) 同花葫芦：同花色下的 3+2
        if (n == 5 && flush && counts.firstOrNull() == 3 && counts.getOrNull(1) == 2) {
            return r(HandType.FLUSH_HOUSE, real)
        }
        // 3) 五条：5 张同点数（花色不限）
        if (n == 5 && counts[0] == 5) return r(HandType.FIVE_OF_A_KIND, real)
        // 4) 同花顺（含皇家同花顺，不单独作为牌型）
        if (flush && straight) return r(HandType.STRAIGHT_FLUSH, real)
        // 5) 四条
        if (counts[0] == 4) return r(HandType.FOUR_OF_A_KIND, byRank[0].second)
        // 6) 葫芦：3+2，且两个点数不同
        if (counts.firstOrNull() == 3 && counts.getOrNull(1) == 2) {
            return r(HandType.FULL_HOUSE, real)
        }
        // 7) 同花
        if (flush) return r(HandType.FLUSH, real)
        // 8) 顺子
        if (straight) return r(HandType.STRAIGHT, real)
        // 8.5) 「四指」：5 张里挑 4 张成同花 / 顺子
        if (fourFingers && n == 5) {
            flushSubset(real, 4, smeared)?.let { return r(HandType.FLUSH, it) }
            straightSubset(real, shortcut)?.let { return r(HandType.STRAIGHT, it) }
        }
        // 9) 三条
        if (counts[0] == 3) return r(HandType.THREE_OF_A_KIND, byRank[0].second)
        // 10) 两对（取最大的两对，多余的牌不得分）
        if (counts.count { it == 2 } >= 2) {
            val pairs = byRank.filter { it.second.size == 2 }.take(2)
            return r(HandType.TWO_PAIR, pairs.flatMap { it.second })
        }
        // 11) 对子
        if (counts[0] == 2) return r(HandType.PAIR, byRank[0].second)
        // 12) 高牌：只算最大的一张（A 最大）
        val highest = real.maxByOrNull { it.rank.order }!!
        return r(HandType.HIGH_CARD, listOf(highest))
    }

    /**
     * 是否同花。万能牌可当任意花色 —— 只要「非万能牌」的花色一致即成立；
     * 全是万能牌时直接算同花。
     *
     * [smeared]（涂抹）时改按颜色判定：红桃/方块同色，黑桃/梅花同色。
     */
    fun isFlush(cards: List<Card>, smeared: Boolean = false): Boolean {
        if (cards.size < 4) return false
        val fixed = cards.mapNotNull { it.effectiveSuit }
        if (fixed.isEmpty()) return true
        return if (smeared) fixed.all { it.isRed == fixed.first().isRed }
        else fixed.all { it == fixed.first() }
    }

    /**
     * 判断牌是否构成顺子。
     *
     * 支持 A 当小（A 2 3 4 5）和 A 当大（10 J Q K A），但不能同时当两头，
     * 因此 A K Q J 2 这类"绕圈"手牌不算顺子。
     *
     * [shortcut]（捷径）允许隔一档：2 4 6 8 10 算顺子。
     */
    fun isStraight(cards: List<Card>, shortcut: Boolean = false): Boolean {
        if (cards.size < 4) return false
        val orders = cards.map { it.rank.order }.sorted()
        val distinct = orders.distinct()
        if (distinct.size != cards.size) return false
        if (!shortcut) {
            if (distinct.last() - distinct.first() == cards.size - 1) return true
            return cards.size == 5 && distinct == listOf(2, 3, 4, 5, 14)
        }
        // 捷径：相邻差值 1~2，A 可当小
        fun okAsc(list: List<Int>): Boolean =
            list.zipWithNext().all { (a, b) -> b - a in 1..2 }
        if (okAsc(distinct)) return true
        return cards.size == 5 && distinct.takeLast(4) == listOf(2, 3, 4, 5) && distinct.last() == 14
    }

    /** 从 5 张里挑出 4 张同花（含万能牌补位）。 */
    private fun flushSubset(cards: List<Card>, need: Int, smeared: Boolean): List<Card>? {
        if (cards.size < need) return null
        if (cards.size == need && isFlush(cards, smeared)) return cards
        val groups = cards.filter { it.effectiveSuit != null }
            .groupBy { if (smeared) it.effectiveSuit!!.isRed else it.effectiveSuit!!.ordinal }
        val wilds = cards.filter { it.effectiveSuit == null }
        val best = groups.values.maxByOrNull { it.size } ?: return null
        val picked = best + wilds
        return if (picked.size >= need) picked.take(need) else null
    }

    /** 从 5 张里挑出 4 张顺子。 */
    private fun straightSubset(cards: List<Card>, shortcut: Boolean): List<Card>? {
        if (cards.size != 5) {
            val s = cards.sortedBy { it.rank.order }
            return if (isStraight(s, shortcut)) s else null
        }
        // C(5,4) = 5 种，直接枚举
        for (drop in cards.indices) {
            val sub = cards.filterIndexed { i, _ -> i != drop }
            if (isStraight(sub, shortcut)) return sub
        }
        return null
    }
}
