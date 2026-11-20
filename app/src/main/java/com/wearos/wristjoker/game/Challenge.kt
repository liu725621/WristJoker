package com.wearos.wristjoker.game

/**
 * 挑战（Challenge）—— 原版 20 个预设关卡。
 *
 * 原版挑战的价值不在"更难"，而在**把某一条规则拧到极限**：
 * 无丑（小丑位 0）、巨石（只有 1 个位）、金针（只有 1 手）、
 * 美杜莎（人头牌全废）。玩家打一遍就知道自己平时依赖的是哪一根拐杖。
 *
 * 手表版全部 20 个都保留，但每条规则都翻译成**引擎已有的确定性参数**
 * （牌组 / 赌注 / 起始钱 / 出牌弃牌 / 手牌 / 小丑位 / 目标倍率 / 常驻 Boss），
 * 不引入"限时""隐藏计数器"这类屏幕上看不见的东西。
 *
 * 种子固定：同一个挑战每次开局的牌序一致，玩家可以复盘，而不是靠运气。
 * 这也是原版挑战的做法。
 */
enum class Challenge(
    val id: String,
    val cn: String,
    val desc: String,
    val deck: DeckType,
    val seed: Long,
    val stake: Stake = Stake.WHITE,
    /** null = 用赌注的默认起始金钱。 */
    val money: Int? = null,
    val handsDelta: Int = 0,
    val discardsDelta: Int = 0,
    val handSizeDelta: Int = 0,
    val jokerSlotsDelta: Int = 0,
    /** 开局直接送的小丑数量（原版"不朽""发射"这类）。 */
    val startJokers: Int = 0,
    /** 目标分固定倍率。 */
    val targetScale: Double = 1.0,
    /** 商店 / 补充包加价。 */
    val priceDelta: Int = 0,
    /** 整局常驻的 Boss 效果（美杜莎 / 五张牌 / 定型）。 */
    val forcedBoss: BossModifier = BossModifier.NONE,
) {
    OMELETTE(
        "omelette", "煎蛋卷", "起始 \$0 · 弃牌 +2",
        DeckType.RED, 0x0E6611L, money = 0, discardsDelta = 2,
    ),
    FIFTEEN(
        "fifteen", "十五分钟", "出牌 -2 · 手牌 +1",
        DeckType.BLUE, 0x51E7E4L, handsDelta = -2, handSizeDelta = 1,
    ),
    RICH(
        "rich", "富者愈富", "起始 \$40 · 目标 ×1.5",
        DeckType.YELLOW, 0x21C40FL, money = 40, targetScale = 1.5,
    ),
    KNIFE(
        "knife", "刀锋边缘", "没有弃牌",
        DeckType.BLACK, 0x4B19E5L, discardsDelta = -3,
    ),
    XRAY(
        "xray", "X 光", "手牌 +3 · 目标 ×1.3",
        DeckType.BLUE, 0x58A11L, handSizeDelta = 3, targetScale = 1.3,
    ),
    MADWORLD(
        "madworld", "疯狂世界", "小丑位 +2 · 起始 \$0",
        DeckType.RANDOM, 0x3AD0FFL, money = 0, jokerSlotsDelta = 2,
    ),
    LUXURY(
        "luxury", "奢侈税", "商店 +$2",
        DeckType.YELLOW, 0x7A2C11L, priceDelta = 2,
    ),
    NONPERISH(
        "nonperish", "不朽", "开局送 1 个小丑",
        DeckType.GREEN, 0x9E7A31L, startJokers = 1,
    ),
    MEDUSA(
        "medusa", "美杜莎", "人头牌不计分",
        DeckType.BLACK, 0x0B5EADL, forcedBoss = BossModifier.PLANT,
    ),
    DOUBLE(
        "double", "双倍或全无", "起始 \$16 · 目标 ×2",
        DeckType.YELLOW, 0x2D0B1EL, money = 16, targetScale = 2.0,
    ),
    TYPECAST(
        "typecast", "定型", "只能出一种牌型",
        DeckType.BLUE, 0x7711AAL, forcedBoss = BossModifier.MOUTH,
    ),
    INFLATION(
        "inflation", "通胀", "商店 +$3 · 起始 \$12",
        DeckType.RANDOM, 0x1F1A7CL, money = 12, priceDelta = 3,
    ),
    BRAM(
        "bram", "布拉姆扑克", "手牌 -2 · 出牌 +1",
        DeckType.BLACK, 0x5A0B11L, handSizeDelta = -2, handsDelta = 1,
    ),
    FRAGILE(
        "fragile", "易碎", "玻璃牌组 · 手牌 -1",
        DeckType.GLASS, 0x6E55E5L, handSizeDelta = -1,
    ),
    MONOLITH(
        "monolith", "巨石", "只有 1 个小丑位",
        DeckType.RED, 0x3C3C3CL, jokerSlotsDelta = -4,
    ),
    BLASTOFF(
        "blastoff", "发射", "小丑位 0 · 开局送 2 个小丑",
        DeckType.BLUE, 0x0FF5E7L, jokerSlotsDelta = -5, startJokers = 2,
    ),
    FIVECARD(
        "fivecard", "五张牌", "只能出满 5 张",
        DeckType.GREEN, 0x5C0DE5L, forcedBoss = BossModifier.PSYCHIC,
    ),
    NEEDLE(
        "needle", "金针", "只有 1 次出牌 · 目标 ×0.4",
        DeckType.YELLOW, 0x11D0BEL, handsDelta = -3, targetScale = 0.4,
    ),
    CRUELTY(
        "cruelty", "残酷", "紫注 · 目标 ×2",
        DeckType.RED, 0x0C7E17L, stake = Stake.PURPLE, targetScale = 2.0,
    ),
    JOKERLESS(
        "jokerless", "无丑", "小丑位 0",
        DeckType.WHITE, 0x0000000L, jokerSlotsDelta = -5,
    ),
    ;

    /** 这一档挑战在列表里显示的一行摘要。 */
    val subtitle: String get() = "${deck.cn} · ${stake.cn}"
}
