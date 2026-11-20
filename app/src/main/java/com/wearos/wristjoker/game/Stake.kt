package com.wearos.wristjoker.game

/**
 * 赌注（Stake）—— 原版的八档难度。
 *
 * 原版的难度不是"敌人血更厚"这种一维加码，而是**每一档拿走一样东西**：
 * 先是小盲的钱，再是分数增长曲线，然后是小丑的退出机制（不能卖），
 * 最后是弃牌、目标、包价、重掷价。玩家升一档，等于被迫换一种打法。
 *
 * 手表版把八档全部做出来，但每档只保留**一条**可感知的规则 —— 216dp 的
 * 圆屏上没法展示"本档同时改了四个隐藏系数"，一条规则一句话说得清，
 * 玩家才知道自己为什么变难了。
 *
 * 叠加方式照原版：选金注等于同时承受白→金的全部修正。
 */
enum class Stake(
    val cn: String,
    val en: String,
    /** 一句话说明这一档**新增**了什么，圆屏一行放得下。 */
    val desc: String,
) {
    WHITE("白注", "White Stake", "标准规则"),
    RED("红注", "Red Stake", "小盲注不给钱"),
    GREEN("绿注", "Green Stake", "目标分数增长更快"),
    BLACK("黑注", "Black Stake", "小丑不可卖出"),
    BLUE("蓝注", "Blue Stake", "每关弃牌 -1"),
    PURPLE("紫注", "Purple Stake", "目标分数再快一档"),
    ORANGE("橙注", "Orange Stake", "补充包 +$1"),
    GOLD("金注", "Gold Stake", "重掷起步 \$10"),
    ;

    /** 是否已经跨过 [other] 这一档（含本档）。 */
    fun atLeast(other: Stake): Boolean = ordinal >= other.ordinal

    /** 起始金钱。黑注起小丑无法变现，补 $1 免得开局就卡死。 */
    val startMoney: Int
        get() = 4 + if (atLeast(BLACK)) 1 else 0

    /** 每关弃牌修正。 */
    val discardsDelta: Int
        get() = if (atLeast(BLUE)) -1 else 0

    /**
     * 目标分数倍率。
     *
     * 原版的"分数增长更快"是改整条底注表的曲线，这里用按关号累乘表达：
     * 绿注每关 +5%，紫注每关 +10%。到第 8 关分别约为 ×1.41 / ×1.95。
     */
    fun targetScale(ante: Int): Double {
        val per = when {
            atLeast(PURPLE) -> 0.10
            atLeast(GREEN) -> 0.05
            else -> 0.0
        }
        if (per == 0.0) return 1.0
        var s = 1.0
        repeat((ante - 1).coerceAtLeast(0)) { s *= 1.0 + per }
        return s
    }

    /** 重掷起步价。 */
    val rerollBase: Int
        get() = if (atLeast(GOLD)) 10 else 5

    /** 补充包加价。 */
    val packPriceDelta: Int
        get() = if (atLeast(ORANGE)) 1 else 0

    /** 小丑是否锁定（只能买、不能卖）。 */
    val sellLocked: Boolean
        get() = atLeast(BLACK)

    /** 小盲注是否没有击败奖励。 */
    val smallBlindNoReward: Boolean
        get() = atLeast(RED)
}
