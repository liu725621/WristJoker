package com.wearos.wristjoker.game

/**
 * 牌组（Deck）—— 原版 15 种，全部收录。
 *
 * 每一项都只在 [GameEngine.startRun] / [Deck.forType] 里转成具体数值，
 * UI 只读 [desc] 展示 —— 不让界面层再算一遍，避免两处不一致。
 *
 * 前 8 种是上一轮已有的（其中「镀金 / 玻璃 / 随机」是手表版的三个
 * 直观变体）；后 7 种是这一轮补上的原版牌组：标准、魔法、星云、
 * 幽灵、弃牌、棋盘、彩绘。补充的重点不是"数量凑到 15"，而是让
 * **构筑起点**真正分叉：有的牌组一开局就带塔罗，有的整副牌没有人头，
 * 有的只剩两种花色 —— 同一套小丑在这四种牌组里强弱完全不同。
 */
enum class DeckType(
    val cn: String,
    val en: String,
    /** 一行说明，8 个汉字以内，圆屏一行放得下。 */
    val desc: String,
    /** 开局额外金钱。 */
    val extraMoney: Int = 0,
    /** 额外弃牌次数。 */
    val extraDiscards: Int = 0,
    /** 额外出牌次数。 */
    val extraHands: Int = 0,
    /** 额外小丑位。 */
    val extraJokerSlots: Int = 0,
    /** 额外手牌上限。 */
    val extraHandSize: Int = 0,
    /** 开局送的塔罗数量。 */
    val startTarots: Int = 0,
    /** 开局送的行星数量。 */
    val startPlanets: Int = 0,
    /** 开局送的幻灵数量。 */
    val startSpectrals: Int = 0,
    /** 整副牌没有人头（J/Q/K 降为 10）。 */
    val noFaces: Boolean = false,
    /** 整副牌只有黑桃与红桃。 */
    val twoSuits: Boolean = false,
) {
    // ---- 上一轮的 8 种 ----
    RED("红牌组", "Red Deck", "弃牌 +1", extraDiscards = 1),
    BLUE("蓝牌组", "Blue Deck", "出牌 +1", extraHands = 1),
    YELLOW("黄牌组", "Yellow Deck", "开局 +\$10", extraMoney = 10),
    BLACK("黑牌组", "Black Deck", "小丑位 +1 · 手牌 -1", extraJokerSlots = 1, extraHandSize = -1),
    GREEN("绿牌组", "Green Deck", "利息无上限"),
    GOLD("镀金牌组", "Gold Deck", "10 张黄金牌"),
    GLASS("玻璃牌组", "Glass Deck", "8 玻璃 8 钢铁"),
    RANDOM("随机牌组", "Random Deck", "12 张随机增强"),

    // ---- 本轮补齐的 7 种 ----
    WHITE("标准牌组", "White Deck", "无修正 · 基准"),
    MAGIC("魔法牌组", "Magic Deck", "开局 2 张塔罗", startTarots = 2),
    NEBULA("星云牌组", "Nebula Deck", "开局 1 张行星 · 手牌 -1",
        startPlanets = 1, extraHandSize = -1),
    GHOST("幽灵牌组", "Ghost Deck", "开局 1 张幻灵", startSpectrals = 1),
    ABANDONED("弃牌牌组", "Abandoned Deck", "整副没有人头", noFaces = true),
    CHECKERED("棋盘牌组", "Checkered Deck", "只有黑桃红桃", twoSuits = true),
    PAINTED("彩绘牌组", "Painted Deck", "手牌 +2 · 小丑位 -1",
        extraHandSize = 2, extraJokerSlots = -1),
    ;
}
