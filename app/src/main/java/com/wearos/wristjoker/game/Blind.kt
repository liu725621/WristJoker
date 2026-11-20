package com.wearos.wristjoker.game

/** 盲注类型。目标分数 = 基础筹码 × 对应倍率；奖励是击败后必得的基础金钱。 */
enum class BlindKind(val cn: String, val short: String, val multiplier: Double, val reward: Int) {
    SMALL("小盲注", "小盲", 1.0, 3),
    BIG("大盲注", "大盲", 1.5, 4),
    BOSS("Boss 盲注", "BOSS", 2.0, 5),
}

/**
 * Boss 盲注的负面效果。
 *
 * 原版 28 个（含 5 个第 8 关专属的 Showdown），这里**全部收录**。
 * 判据只有一条：效果必须能用引擎里的确定性状态表达出来。
 *
 * 因此三个需要"牌背朝上"的效果（The House / The Mark / The Wheel / The Fish）
 * 被翻译成「这些牌不计分」—— 手表上是单色屏级的小屏，画一个背面图案
 * 和画一张灰掉的牌在信息量上没有区别，但后者不需要第二层翻牌交互。
 *
 * [minAnte] 是原版的"最早出现底注"，早于这个关号不会抽到。
 */
enum class BossModifier(val cn: String, val desc: String, val minAnte: Int = 1) {
    NONE("无", "", 1),

    // ---------------- 花色 / 点数封禁 ----------------
    CLUB_DEBUFF("梅花", "梅花不计分"),
    SPADE_DEBUFF("黑桃", "黑桃不计分"),
    HEART_DEBUFF("红桃", "红桃不计分"),
    DIAMOND_DEBUFF("方块", "方块不计分"),
    PLANT("植物", "人头牌不计分", minAnte = 4),
    MARK("标记", "人头牌不计分", minAnte = 2),

    // ---------------- 资源限制 ----------------
    NEEDLE("针", "只有 1 次出牌", minAnte = 1),
    WATER("水", "本关无法弃牌", minAnte = 1),
    MANACLE("镣铐", "手牌 -1", minAnte = 1),
    SERPENT("蛇", "每次只补 3 张", minAnte = 5),

    // ---------------- 目标分 ----------------
    WALL("墙", "超大盲注", minAnte = 2),
    VIOLET("紫器", "目标 ×6", minAnte = 8),

    // ---------------- 出牌规则 ----------------
    PSYCHIC("通灵", "必须出 5 张", minAnte = 1),
    EYE("眼", "每种牌型限一次", minAnte = 2),
    MOUTH("嘴", "只能出一种牌型", minAnte = 2),
    FLINT("燧石", "基础值减半", minAnte = 2),

    // ---------------- 结算阶段副作用 ----------------
    HOOK("钩", "出牌后弃 2 张", minAnte = 1),
    TOOTH("牙", "每张牌 -\$1", minAnte = 3),
    ARM("臂", "出牌后牌型降级", minAnte = 2),
    OX("牛", "打出最常牌型金钱归零", minAnte = 6),

    // ---------------- 牌面封锁 ----------------
    HOUSE("房子", "开局 2 张不计分", minAnte = 2),
    WHEEL("轮", "开局 3 张不计分", minAnte = 2),
    FISH("鱼", "每手后 1 张不计分", minAnte = 2),
    PILLAR("柱", "本关打过的牌不计分", minAnte = 1),

    // ---------------- Showdown（第 8 关专属） ----------------
    AMBER("琥珀橡果", "小丑效果减半", minAnte = 8),
    CRIMSON("绯红之心", "每手 1 个小丑失效", minAnte = 8),
    VERDANT("翠叶", "卖 1 个小丑前全牌不计分", minAnte = 8),
    ;

    /** 该效果下某张牌是否因为**花色/点数**被封锁。牌面封锁另算（见引擎）。 */
    fun blocks(card: Card): Boolean = when (this) {
        CLUB_DEBUFF -> card.suit == Suit.CLUB
        SPADE_DEBUFF -> card.suit == Suit.SPADE
        HEART_DEBUFF -> card.suit == Suit.HEART
        DIAMOND_DEBUFF -> card.suit == Suit.DIAMOND
        PLANT, MARK -> card.rank.order >= 11
        else -> false
    }

    /** 目标分的额外倍数。 */
    val targetScale: Double
        get() = when (this) {
            // Boss 本身已乘 2，这里再乘 2 → 合计 4 倍基础筹码（原版 The Wall）
            WALL -> 2.0
            // 同样：2 × 3 = 6 倍（原版 Violet Vessel）
            VIOLET -> 3.0
            else -> 1.0
        }

    /** 手牌上限修正。 */
    val handSizeDelta: Int
        get() = when (this) {
            MANACLE -> -1
            else -> 0
        }

    /** 出牌次数修正（窃贼小丑以外的一般规则）。 */
    val handsDelta: Int
        get() = when (this) {
            NEEDLE -> -3 // 4 → 1
            else -> 0
        }

    /** UI 上是否需要额外一行解释（这些效果不是"封一张牌"那么简单）。 */
    val isComplex: Boolean
        get() = this in setOf(PSYCHIC, EYE, MOUTH, OX, ARM, PILLAR, CRIMSON, VERDANT, AMBER, TOOTH)
}

/**
 * 一关（一个盲注）的运行参数。
 */
data class BlindSpec(
    val ante: Int,
    val index: Int,
    val kind: BlindKind,
    val modifier: BossModifier,
    val target: Long,
    val hands: Int,
    val discards: Int,
    val handSize: Int,
) {
    val label: String get() = "第 $ante 关 · ${kind.cn}"
    val reward: Int get() = kind.reward
    val modifierLabel: String
        get() = if (modifier == BossModifier.NONE) "" else "【${modifier.cn}】${modifier.desc}"

    /** 只有小盲和大盲可以跳过；Boss 必须打。 */
    val skippable: Boolean get() = index < 2
}

/**
 * 盲注选择页要展示的一格。
 *
 * 三个盲注同屏列出，`current` 是下一个要打的，`cleared` 表示本关已通过。
 */
data class BlindOption(
    val index: Int,
    val kind: BlindKind,
    val target: Long,
    val reward: Int,
    val modifier: BossModifier,
    val cleared: Boolean,
    val current: Boolean,
    val skippable: Boolean,
)

object Blinds {

    /**
     * 标准赌注（白/红）下 Ante 1..8 的基础筹码。
     *
     * 数值取自 Balatro Wiki「List of Antes」，是原版的真实表格而非拟合公式 ——
     * 原版这一列并非等比数列（800 → 2000 → 5000 → 11000 …），
     * 用公式会算错，所以这里直接查表。
     */
    private val BASE_CHIPS = longArrayOf(
        300L, 800L, 2000L, 5000L, 11000L, 20000L, 35000L, 50000L,
    )

    const val MAX_ANTE = 8

    /** 跳过一个盲注给的标签数量。 */
    const val SKIP_TAGS = 1

    /** 利息：每 5 元 +1，基础上限 5 元（种子基金可提到 10）。 */
    const val INTEREST_CAP = 5

    /** 基础每关出牌 / 弃牌 / 手牌上限。 */
    const val BASE_HANDS = 4
    const val BASE_DISCARDS = 3
    const val BASE_HAND_SIZE = 8

    /** 取第 [ante] 关（1 起）的基础筹码；超出 8 关按原版无尽公式外推。 */
    fun baseChips(ante: Int): Long {
        if (ante <= MAX_ANTE) return BASE_CHIPS[ante.coerceAtLeast(1) - 1]
        // 原版无尽模式：req = 50000 × (1.6 + 0.75(n-8))^(n-8)，n = ante
        var v = BASE_CHIPS.last().toDouble()
        repeat(ante - MAX_ANTE) {
            v *= 1.6 + 0.75 * (ante - MAX_ANTE)
        }
        return v.toLong()
    }

    /**
     * 为整局预生成 8 个 Boss 效果。
     *
     * 做成一整条序列存在引擎里，而不是「用到时按关号算」—— 因为
     * 盲注选择页要**提前展示** Boss 效果和目标分。如果两处各自算一次，
     * 随机源一错位就会出现"选择页显示针、实战是墙"的 bug（上一版踩过）。
     *
     * 抽取规则照原版：只在 [BossModifier.minAnte] ≤ 当前关号的效果里抽，
     * 且前两关不挂效果（给玩家一个没有额外规则的适应期）。
     */
    fun bossSequence(random: kotlin.random.Random, anteCount: Int): List<BossModifier> {
        val out = ArrayList<BossModifier>(anteCount)
        val used = HashSet<BossModifier>()
        for (ante in 1..anteCount) {
            if (ante <= 2) {
                out.add(BossModifier.NONE)
                continue
            }
            // 第 8 关（以及无尽模式每 8 关）固定是 Showdown
            val showdown = ante % MAX_ANTE == 0
            val pool = BossModifier.entries.filter {
                it != BossModifier.NONE &&
                    it.minAnte <= ante &&
                    (it.minAnte >= 8) == showdown &&
                    it !in used
            }.ifEmpty {
                BossModifier.entries.filter { it != BossModifier.NONE && it.minAnte <= ante }
            }
            val pick = pool[random.nextInt(pool.size)]
            used.add(pick)
            out.add(pick)
        }
        return out
    }

    /** 无尽模式追加的 Boss（确定性循环，避免无限增长的存档）。 */
    fun endlessBoss(ante: Int): BossModifier {
        val pool = BossModifier.entries.filter { it != BossModifier.NONE && it.minAnte < 8 }
        return pool[(ante - MAX_ANTE - 1).mod(pool.size)]
    }

    /**
     * 构造第 [ante] 关第 [index]（0=小盲 1=大盲 2=Boss）个盲注。
     */
    fun spec(
        ante: Int,
        index: Int,
        boss: BossModifier = BossModifier.NONE,
        extraHands: Int = 0,
        extraDiscards: Int = 0,
        handSizeBonus: Int = 0,
        /** 赌注 / 挑战带来的目标分倍率（绿注、紫注、双倍或无）。 */
        targetScale: Double = 1.0,
    ): BlindSpec {
        val kind = BlindKind.entries[index.coerceIn(0, 2)]
        val modifier = if (kind == BlindKind.BOSS) boss else BossModifier.NONE
        val mult = kind.multiplier * modifier.targetScale * targetScale
        val baseHands = (BASE_HANDS + modifier.handsDelta).coerceAtLeast(1)
        val baseDiscards = if (modifier == BossModifier.WATER) 0 else BASE_DISCARDS
        return BlindSpec(
            ante = ante,
            index = index.coerceIn(0, 2),
            kind = kind,
            modifier = modifier,
            target = (baseChips(ante) * mult).toLong(),
            hands = baseHands + extraHands,
            discards = baseDiscards + extraDiscards,
            handSize = (BASE_HAND_SIZE + handSizeBonus + modifier.handSizeDelta).coerceAtLeast(1),
        )
    }
}
