package com.wearos.wristjoker.game

/**
 * 消耗品（Consumable）：塔罗牌与行星牌。
 *
 * 这是原版「成长」的唯一来源，也是本作之前最大的缺口：
 * 没有塔罗就没有牌面改造，没有行星就没有牌型升级，玩家在商店里只买小丑，
 * 每一手的期望值几乎恒定 —— 那就不构成 Roguelike 的构筑曲线。
 *
 * 设计约束：
 * - 效果函数是**纯函数**，输入一组选中牌 + 上下文，返回改造结果，
 *   不直接碰引擎状态。这样单测能穷举，UI 的"预览用牌结果"也能复用。
 * - 需要选牌的塔罗由 UI 先选牌再用牌，选牌张数用 [minSel]/[maxSel] 约束；
 *   不需要选牌的直接生效。
 */
enum class ConsumableKind(val cn: String) {
    TAROT("塔罗"),
    PLANET("行星"),

    /**
     * 幻灵（Spectral）：原版 18 张，仅能从 Spectral Pack（第 5 类补充包）开出。
     *
     * 大多是"摧毁手牌换收益 / 改写牌面 / 改写小丑"的高风险效果；手表上延续
     * 原版语义，靠 [ConsumableResult] 的扩展字段在引擎里统一落账。
     */
    SPECTRAL("幻灵"),
}

/** 用牌时的上下文。 */
data class ConsumableCtx(
    /** 玩家选中的牌（1..3 张）。 */
    val selected: List<Card>,
    /** 当前持有金钱，供「隐者」这类按比例生效的牌使用。 */
    val money: Int,
    /** 已拥有小丑的总售价，供「节制」使用。 */
    val jokerValue: Int,
    val random: kotlin.random.Random,
)

/**
 * 用牌结果。
 *
 * 一次性返回所有可能的状态变更，由引擎统一落账 ——
 * 避免效果函数里出现"改了一半失败"的中间态。
 */
data class ConsumableResult(
    /** 改造后的选中牌，顺序与原选中一致；[destroyed] 为 true 时忽略。 */
    val cards: List<Card> = emptyList(),
    /** 选中牌被摧毁（移出牌堆）。 */
    val destroyed: Boolean = false,
    val moneyDelta: Int = 0,
    /** 生成几张随机小丑。 */
    val spawnJokers: Int = 0,
    /**
     * 生成**一张传奇**小丑（灵魂专用）。
     *
     * 与 [spawnJokers] 分开是因为传奇不在商店池里，抽取路径不同；而且
     * 传奇槽位满了要退回 $20 而不是 $4 —— 它是这一局最贵的一张牌。
     */
    val spawnLegendary: Boolean = false,
    /** 生成几张消耗品（塞进槽位；槽位满了折算成钱）。 */
    val spawnConsumables: List<Consumable> = emptyList(),
    /** 给一张已有小丑加随机版本。 */
    val upgradeRandomJoker: Boolean = false,
    /** 升级哪个牌型。 */
    val levelUp: HandType? = null,
    /** 全部牌型各升一级（幻灵「黑洞」）。 */
    val allLevelsUp: Boolean = false,
    /**
     * 生成的新牌模板（id 会被引擎用 nextCardId() 重新分配）。
     * 用于「熟悉者 / 冷酷 / 咒语 / 神秘生物」这类往牌堆塞牌的幻灵。
     */
    val spawnCards: List<Card> = emptyList(),
    /** 金钱清零（幻灵「怨灵」）。 */
    val moneyToZero: Boolean = false,
    /** 手牌上限变化（幻灵「通灵板 / 外质」都是 -1）。 */
    val handSizeDelta: Int = 0,
    /** 随机摧毁几张手牌（幻灵「献祭」5 张、其余三张 1 张）。 */
    val destroyRandomHand: Int = 0,
    /** 把手牌全部转成同一种花色 / 点数（幻灵「印记 / 通灵板」）。 */
    val convertHandSuit: Boolean = false,
    val convertHandRank: Boolean = false,
    /** 生成一张稀有小丑（幻灵「怨灵」）。 */
    val spawnRareJoker: Boolean = false,
    /**
     * 「生命之符 / 咒术」：摧毁除随机一张外的全部小丑。
     * 1 = 生命之符，额外复制被保留的那张；2 = 咒术，把被保留的那张改多彩且不复制。
     */
    val jokerApocalypse: Int = 0,
    /** 给一张随机小丑加指定版本（幻灵「咒术」多彩 /「外质」负片）。 */
    val editionRandomJoker: Edition? = null,
    val message: String = "",
)

/**
 * 一张消耗品定义。
 *
 * [desc] 控制在 6 个汉字以内 —— 手表上消耗品面板一行只有约 88dp 文本宽度。
 */
data class Consumable(
    val key: String,
    val name: String,
    val en: String,
    val kind: ConsumableKind,
    val price: Int,
    val minSel: Int = 0,
    val maxSel: Int = 0,
    val handType: HandType? = null,
    val desc: String,
    private val effect: (ConsumableCtx) -> ConsumableResult,
) {
    /** 是否需要先选牌。 */
    val needsCards: Boolean get() = maxSel > 0

    fun use(ctx: ConsumableCtx): ConsumableResult = effect(ctx)
}

private fun tarot(
    key: String,
    name: String,
    en: String,
    desc: String,
    minSel: Int = 0,
    maxSel: Int = 0,
    effect: (ConsumableCtx) -> ConsumableResult,
) = Consumable(
    key = "t_$key", name = name, en = en,
    kind = ConsumableKind.TAROT, price = 3,
    minSel = minSel, maxSel = maxSel, desc = desc, effect = effect,
)

private fun planet(ht: HandType, name: String, en: String) = Consumable(
    key = "p_${ht.name.lowercase()}",
    name = name,
    en = en,
    kind = ConsumableKind.PLANET,
    price = 3,
    handType = ht,
    desc = "升级${ht.cn}",
    effect = { ConsumableResult(levelUp = ht, message = "${ht.cn} 升一级") },
)

/** 把选中的每张牌替换成 [f] 的结果。 */
private inline fun recolor(ctx: ConsumableCtx, crossinline f: (Card) -> Card): ConsumableResult =
    ConsumableResult(cards = ctx.selected.map { f(it) })

/** 幻灵牌的构造器：统一 $4、属于 [ConsumableKind.SPECTRAL]。 */
private fun spectral(
    key: String,
    name: String,
    en: String,
    desc: String,
    minSel: Int = 0,
    maxSel: Int = 0,
    effect: (ConsumableCtx) -> ConsumableResult,
) = Consumable(
    key = "s_$key", name = name, en = en,
    kind = ConsumableKind.SPECTRAL, price = 4,
    minSel = minSel, maxSel = maxSel, desc = desc, effect = effect,
)

private val FACE_RANKS = listOf(Rank.JACK, Rank.QUEEN, Rank.KING)
private val NUMBER_RANKS = Rank.entries.filter { it.order in 2..10 }

/** 幻灵往牌堆里塞的牌：随机点数花色 + 随机增强，id 交给引擎重分配。 */
private fun newCard(r: kotlin.random.Random, ranks: List<Rank>): Card {
    val pool = Enhancement.entries.filter { it != Enhancement.NONE }
    val enh = pool[r.nextInt(pool.size)]
    return Card(
        id = -1,
        rank = ranks[r.nextInt(ranks.size)],
        suit = Suit.entries[r.nextInt(4)],
        enh = enh,
    )
}

object Consumables {

    /**
     * 22 张大阿卡纳，与原版一一对应。
     *
     * 「愚者」「女祭司」「皇帝」是生成型的牌 —— 原版里它们负责把别的牌塞进
     * 消耗品槽，是双槽位下唯一能把槽位打满的途径，缺了会让"塔罗流"完全走不通。
     * 「死神」取选中第一张作模板，因为手表上没法做"左变右"的拖拽指示。
     */
    private val TAROTS: List<Consumable> = listOf(
        tarot("magician", "魔术师", "The Magician", "2 张变幸运", 1, 2) {
            recolor(it) { c -> c.copy(enh = Enhancement.LUCKY) }
        },
        tarot("empress", "女皇", "The Empress", "2 张变倍率", 1, 2) {
            recolor(it) { c -> c.copy(enh = Enhancement.MULT) }
        },
        tarot("hierophant", "教皇", "The Hierophant", "2 张变奖励", 1, 2) {
            recolor(it) { c -> c.copy(enh = Enhancement.BONUS) }
        },
        tarot("lovers", "恋人", "The Lovers", "1 张变万能", 1, 1) {
            recolor(it) { c -> c.copy(enh = Enhancement.WILD) }
        },
        tarot("chariot", "战车", "The Chariot", "1 张变钢铁", 1, 1) {
            recolor(it) { c -> c.copy(enh = Enhancement.STEEL) }
        },
        tarot("justice", "正义", "The Justice", "1 张变玻璃", 1, 1) {
            recolor(it) { c -> c.copy(enh = Enhancement.GLASS) }
        },
        tarot("strength", "力量", "The Strength", "2 张点数 +1", 1, 2) {
            recolor(it) { c -> c.copy(rank = c.rank.up()) }
        },
        tarot("hanged", "倒吊人", "The Hanged Man", "摧毁 2 张", 1, 2) {
            ConsumableResult(destroyed = true, message = "摧毁 ${it.selected.size} 张")
        },
        tarot("tower", "高塔", "The Tower", "1 张变石头", 1, 1) {
            recolor(it) { c -> c.copy(enh = Enhancement.STONE) }
        },
        tarot("star", "星星", "The Star", "3 张变方块", 1, 3) {
            recolor(it) { c -> c.copy(suit = Suit.DIAMOND) }
        },
        tarot("hermit", "隐者", "The Hermit", "金钱 ×2 上限 20") {
            val gain = it.money.coerceAtMost(20)
            ConsumableResult(moneyDelta = gain, message = "+\$$gain")
        },
        tarot("temperance", "节制", "The Temperance", "给小丑总价") {
            val gain = it.jokerValue.coerceAtMost(50)
            ConsumableResult(moneyDelta = gain, message = "+\$$gain")
        },
        tarot("judgement", "审判", "The Judgement", "随机一张小丑") {
            ConsumableResult(spawnJokers = 1, message = "召唤小丑")
        },
        tarot("fool", "愚者", "The Fool", "随机 1 张行星") {
            ConsumableResult(spawnConsumables = listOf(rollPlanet(it.random)), message = "行星 +1")
        },
        tarot("priestess", "女祭司", "The High Priestess", "随机 2 张行星") { ctx ->
            ConsumableResult(
                spawnConsumables = List(2) { rollPlanet(ctx.random) },
                message = "行星 +2",
            )
        },
        tarot("emperor", "皇帝", "The Emperor", "随机 2 张塔罗") { ctx ->
            ConsumableResult(
                spawnConsumables = List(2) { rollTarot(ctx.random) },
                message = "塔罗 +2",
            )
        },
        tarot("death", "死神", "Death", "2 张变第一张", 1, 2) {
            val tpl = it.selected.firstOrNull()
                ?: return@tarot ConsumableResult(message = "没选牌")
            ConsumableResult(
                cards = it.selected.map { c -> c.copy(rank = tpl.rank, suit = tpl.suit) },
                message = "同化为 ${tpl.rank.label}${tpl.suit.symbol}",
            )
        },
        tarot("devil", "恶魔", "The Devil", "1 张变黄金", 1, 1) {
            recolor(it) { c -> c.copy(enh = Enhancement.GOLD) }
        },
        tarot("moon", "月亮", "The Moon", "3 张变梅花", 1, 3) {
            recolor(it) { c -> c.copy(suit = Suit.CLUB) }
        },
        tarot("sun", "太阳", "The Sun", "3 张变红桃", 1, 3) {
            recolor(it) { c -> c.copy(suit = Suit.HEART) }
        },
        tarot("world", "世界", "The World", "3 张变黑桃", 1, 3) {
            recolor(it) { c -> c.copy(suit = Suit.SPADE) }
        },
        tarot("wheel", "命运之轮", "The Wheel of Fortune", "小丑随机版本") {
            // 原版是 1/4 概率，失败不给任何东西 —— 保留这个"赌"的手感
            if (it.random.nextInt(4) == 0) {
                ConsumableResult(upgradeRandomJoker = true, message = "版本变更！")
            } else {
                ConsumableResult(message = "什么也没发生")
            }
        },
    )

    /** 12 张行星，覆盖全部牌型。 */
    private val PLANETS: List<Consumable> = listOf(
        planet(HandType.HIGH_CARD, "冥王星", "Pluto"),
        planet(HandType.PAIR, "水星", "Mercury"),
        planet(HandType.TWO_PAIR, "天王星", "Uranus"),
        planet(HandType.THREE_OF_A_KIND, "金星", "Venus"),
        planet(HandType.STRAIGHT, "土星", "Saturn"),
        planet(HandType.FLUSH, "木星", "Jupiter"),
        planet(HandType.FULL_HOUSE, "地球", "Earth"),
        planet(HandType.FOUR_OF_A_KIND, "火星", "Mars"),
        planet(HandType.STRAIGHT_FLUSH, "海王星", "Neptune"),
        planet(HandType.FIVE_OF_A_KIND, "行星 X", "Planet X"),
        planet(HandType.FLUSH_HOUSE, "谷神星", "Ceres"),
        planet(HandType.FLUSH_FIVE, "厄里斯", "Eris"),
    )

    /**
     * 18 张幻灵，与原版 Collection 页的 18 项一一对应。
     *
     * 原版幻灵主要来自 Spectral Pack（第 5 类补充包），其中「灵魂」还能
     * 以极低概率出现在塔罗包、「黑洞」出现在行星包 —— 见 [Boosters.fill]。
     *
     * 需要选牌的幻灵与塔罗共用同一套「先在手牌区选牌、再使用」流程；
     * 原版开包时会额外发一手牌供选牌，手表上没有第二层选牌界面，
     * 所以拿走后先进消耗品槽，在牌桌阶段使用（与商店里买消耗品后留着用一致）。
     */
    private val SPECTRALS: List<Consumable> = listOf(
        spectral("familiar", "熟悉者", "Familiar", "毁1张·加3张增强人头") { ctx ->
            ConsumableResult(
                destroyRandomHand = 1,
                spawnCards = List(3) { newCard(ctx.random, FACE_RANKS) },
                message = "代价换来了 3 张人头牌",
            )
        },
        spectral("grim", "冷酷", "Grim", "毁1张·加2张增强A") { ctx ->
            ConsumableResult(
                destroyRandomHand = 1,
                spawnCards = List(2) { newCard(ctx.random, listOf(Rank.ACE)) },
                message = "代价换来了 2 张 A",
            )
        },
        spectral("incantation", "咒语", "Incantation", "毁1张·加4张增强数字") { ctx ->
            ConsumableResult(
                destroyRandomHand = 1,
                spawnCards = List(4) { newCard(ctx.random, NUMBER_RANKS) },
                message = "代价换来了 4 张数字牌",
            )
        },
        spectral("talisman", "护符", "Talisman", "1张加金蜡", 1, 1) {
            recolor(it) { c -> c.copy(seal = Seal.GOLD) }
        },
        spectral("aura", "光环", "Aura", "1张加随机版本", 1, 1) {
            val ed = listOf(Edition.FOIL, Edition.HOLO, Edition.POLY)[it.random.nextInt(3)]
            recolor(it) { c -> c.copy(edition = ed) }
        },
        spectral("wraith", "怨灵", "Wraith", "稀有小丑·金钱归零") {
            ConsumableResult(
                spawnRareJoker = true, moneyToZero = true,
                message = "钱没了，但来了一张稀有小丑",
            )
        },
        spectral("sigil", "印记", "Sigil", "手牌转同一花色") {
            ConsumableResult(convertHandSuit = true, message = "手牌花色归一")
        },
        spectral("ouija", "通灵板", "Ouija", "手牌转同点数·手牌-1") {
            ConsumableResult(
                convertHandRank = true, handSizeDelta = -1,
                message = "点数归一，手牌上限 -1",
            )
        },
        spectral("ectoplasm", "外质", "Ectoplasm", "小丑负片·手牌-1") {
            ConsumableResult(
                editionRandomJoker = Edition.NEGATIVE, handSizeDelta = -1,
                message = "一张小丑负片化，手牌上限 -1",
            )
        },
        spectral("immolate", "献祭", "Immolate", "毁5张·+\$20") {
            ConsumableResult(destroyRandomHand = 5, moneyDelta = 20, message = "烧掉 5 张，+\$20")
        },
        spectral("ankh", "生命之符", "Ankh", "复制1小丑·毁其余") {
            ConsumableResult(jokerApocalypse = 1, message = "只留下一张，再复制它")
        },
        spectral("dejavu", "既视感", "Deja Vu", "1张加红蜡", 1, 1) {
            recolor(it) { c -> c.copy(seal = Seal.RED) }
        },
        spectral("hex", "咒术", "Hex", "1小丑多彩·毁其余") {
            ConsumableResult(jokerApocalypse = 2, message = "只留一张，并让它多彩")
        },
        spectral("trance", "恍惚", "Trance", "1张加蓝蜡", 1, 1) {
            recolor(it) { c -> c.copy(seal = Seal.BLUE) }
        },
        spectral("medium", "灵媒", "Medium", "1张加紫蜡", 1, 1) {
            recolor(it) { c -> c.copy(seal = Seal.PURPLE) }
        },
        spectral("cryptid", "神秘生物", "Cryptid", "复制1张为2张", 1, 1) {
            ConsumableResult(
                spawnCards = it.selected.take(1).flatMap { c -> listOf(c.copy(id = -1), c.copy(id = -1)) },
                message = "复制了 2 张",
            )
        },
        spectral("soul", "灵魂", "The Soul", "生成一张传奇小丑") {
            ConsumableResult(spawnLegendary = true, message = "传奇降临")
        },
        spectral("blackhole", "黑洞", "Black Hole", "全部牌型 +1 级") {
            ConsumableResult(allLevelsUp = true, message = "所有牌型升一级")
        },
    )

    /** 全部消耗品：22 塔罗 + 12 行星 + 18 幻灵 = 52，与原版 Collection 一致。 */
    val ALL: List<Consumable> = TAROTS + PLANETS + SPECTRALS

    /** 灵魂单例，供 [Boosters.fill] 的跨包彩蛋与单测使用。 */
    val SOUL: Consumable get() = get("s_soul")!!

    /** 黑洞单例，供行星包的跨包彩蛋使用。 */
    val BLACK_HOLE: Consumable get() = get("s_blackhole")!!

    private val byKey: Map<String, Consumable> = ALL.associateBy { it.key }

    fun get(key: String): Consumable? = byKey[key]

    /**
     * 抽一张消耗品。
     *
     * 原版商店权重是 小丑 20 / 塔罗 4 / 行星 4，也就是塔罗与行星等概率；
     * 这里保持 1:1，但行星略高（5:4），因为不升级牌型就打不动后面的关，
     * 行星在手表上的单局里更"刚需"。
     */
    fun rollOne(random: kotlin.random.Random): Consumable =
        if (random.nextInt(9) < 5) rollPlanet(random) else rollTarot(random)

    fun rollTarot(random: kotlin.random.Random): Consumable = TAROTS.random(random)

    fun rollPlanet(random: kotlin.random.Random): Consumable = PLANETS.random(random)

    /**
     * 牌型 → 对应行星牌（蓝蜡封结算用）。
     *
     * 原版 1.0.1f 之后，蓝蜡封在回合末生成"最后打出牌型"对应的行星牌；
     * 12 张行星覆盖全部 [HandType]，所以这里必然命中。
     */
    fun planetFor(ht: HandType): Consumable = get("p_${ht.name.lowercase()}")!!

    /** 幻灵只能从 Spectral Pack 抽，不进商店的塔罗/行星随机池。 */
    fun rollSpectral(random: kotlin.random.Random): Consumable = SPECTRALS.random(random)

    fun roll(random: kotlin.random.Random, count: Int): List<Consumable> =
        List(count) { rollOne(random) }
}
