package com.wearos.wristjoker.game

/**
 * 生涯成绩单 —— 成就判定的唯一输入。
 *
 * 全部是**跨局的累计量**，没有一条依赖"当前这一局"的临时状态。
 * 这样成就只在一个地方判定（落盘后），不需要在牌桌的每次刷新里
 * 反复扫描，也不会出现"退出重进成就没了"的情况。
 *
 * 之所以把这个类放在 game 包而不是存档层：它是纯数据 + 纯函数判定，
 * 可以在 JVM 单测里直接构造，不用碰 SharedPreferences。
 */
data class CareerStats(
    val runs: Int = 0,
    val wins: Int = 0,
    val bestAnte: Int = 0,
    val bestHand: Long = 0,
    /** 单局最高总分。 */
    val bestScore: Long = 0,
    val totalHands: Long = 0,
    val totalDiscards: Long = 0,
    val bestMoney: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val seenJokers: Int = 0,
    val bestStakeWon: Int = 0,
)

/**
 * 成就条目。
 *
 * [tier] 只用来排序与配色（1 铜 / 2 银 / 3 金），不影响判定。
 * 数量定在 20 条：原版 20+ 条，而手表一局十几分钟，条目多了永远打不完，
 * 反而失去"再开一局"的推力。
 */
enum class Ach(
    val id: String,
    val title: String,
    val desc: String,
    val tier: Int,
) {
    FIRST_RUN("first_run", "坐下来", "打完第一局", 1),
    FIRST_WIN("first_win", "开张", "通关一次", 2),
    ANTE5("ante5", "过半", "打到第 5 关", 1),
    ANTE8("ante8", "通关", "打到第 8 关", 3),
    ANTE10("ante10", "不肯走", "无尽模式到第 10 关", 3),
    ANTE12("ante12", "赖着不走", "无尽模式到第 12 关", 3),
    HAND_10K("hand10k", "一万", "单手指 10,000 分", 1),
    HAND_100K("hand100k", "十万", "单手指 100,000 分", 2),
    HAND_1M("hand1m", "百万", "单手指 1,000,000 分", 3),
    HANDS_500("hands500", "手熟", "累计出牌 500 次", 1),
    HANDS_2000("hands2000", "老茧", "累计出牌 2000 次", 2),
    DISCARD_100("discard100", "断舍离", "累计弃牌 100 次", 1),
    DISCARD_500("discard500", "弃如敝屣", "累计弃牌 500 次", 2),
    RICH("rich", "阔佬", "一局结束时持有 $50", 2),
    STREAK_3("streak3", "手气", "连续 3 局通关", 2),
    STREAK_5("streak5", "停不下来", "连续 5 局通关", 3),
    SEEN_50("seen50", "半个图鉴", "见过 50 种小丑", 1),
    SEEN_150("seen150", "全图鉴", "见过全部 150 种小丑", 3),
    RUNS_20("runs20", "常客", "累计开局 20 次", 1),
    WINS_5("wins5", "熟客", "累计通关 5 次", 2),
    ;

    companion object {
        val ALL: List<Ach> = entries.toList()
    }
}

/**
 * 按成绩单算出**全部**已达成项。
 *
 * 刻意返回全集而不是增量：达成集合是幂等的，存档里存一份 `Set<String>`，
 * 每次落盘用 `+=` 合并即可，不需要"哪条是新的"这种脆弱状态。
 * 想知道"刚解锁了什么"，用 `now - before` 求差。
 */
fun achievementsOf(s: CareerStats): Set<String> = buildSet {
    fun on(a: Ach, ok: Boolean) { if (ok) add(a.id) }

    on(Ach.FIRST_RUN, s.runs >= 1)
    on(Ach.FIRST_WIN, s.wins >= 1)
    on(Ach.ANTE5, s.bestAnte >= 5)
    on(Ach.ANTE8, s.bestAnte >= 8)
    on(Ach.ANTE10, s.bestAnte >= 10)
    on(Ach.ANTE12, s.bestAnte >= 12)
    on(Ach.HAND_10K, s.bestHand >= 10_000)
    on(Ach.HAND_100K, s.bestHand >= 100_000)
    on(Ach.HAND_1M, s.bestHand >= 1_000_000)
    on(Ach.HANDS_500, s.totalHands >= 500)
    on(Ach.HANDS_2000, s.totalHands >= 2000)
    on(Ach.DISCARD_100, s.totalDiscards >= 100)
    on(Ach.DISCARD_500, s.totalDiscards >= 500)
    on(Ach.RICH, s.bestMoney >= 50)
    on(Ach.STREAK_3, s.bestStreak >= 3)
    on(Ach.STREAK_5, s.bestStreak >= 5)
    on(Ach.SEEN_50, s.seenJokers >= 50)
    on(Ach.SEEN_150, s.seenJokers >= 150)
    on(Ach.RUNS_20, s.runs >= 20)
    on(Ach.WINS_5, s.wins >= 5)
}

/**
 * 成就 → 它解锁的小丑。
 *
 * 原版是把解锁条件写在每张小丑上的，这里反过来：**成就决定哪一档小丑进池**。
 * 150 张小丑逐条挂条件，改一处要验 150 条，收益却只有"某张牌晚出现"；
 * 反过来按档位放行，玩家在第一局能明显感到"池子在变宽"。
 *
 * 稀有档需要至少通关一次；传说永远不进商店（只能靠「灵魂」）。
 */
object Unlocks {

    /** 这档小丑当前是否允许出现在商店里。 */
    fun rarityAllowed(r: Rarity, stats: CareerStats): Boolean = when (r) {
        Rarity.COMMON, Rarity.UNCOMMON -> true
        Rarity.RARE -> stats.wins >= 1 || stats.bestAnte >= 8
        Rarity.LEGENDARY -> false
    }

    /**
     * 牌组解锁：按最高底注分三档放行。
     *
     * 不额外给 [DeckType] 加字段 —— 15 个构造逐个标注档位，改一处要验 15 条，
     * 而枚举本身的顺序已经是"由常规到特殊"（前 5 个是纯数值修正的牌组），
     * 直接按序号分组的语义与人工标注一致。
     */
    fun deckUnlocked(d: DeckType, bestAnte: Int): Boolean = when {
        d.ordinal < 5 -> true
        d.ordinal < 10 -> bestAnte >= 3
        else -> bestAnte >= 6
    }
}
