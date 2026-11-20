package com.wearos.wristjoker.game

/**
 * 四种花色。
 *
 * [isRed] 只影响显示颜色，不参与任何计分规则。
 */
enum class Suit(val symbol: String, val cn: String, val isRed: Boolean) {
    SPADE("\u2660", "黑桃", false),
    HEART("\u2665", "红桃", true),
    DIAMOND("\u2666", "方块", true),
    CLUB("\u2663", "梅花", false),
}

/**
 * 13 个点数。
 *
 * [chips] 是这张牌计分时提供的筹码值：2..10 按点数，J/Q/K 记 10，A 记 11
 * （与 Balatro 的牌面筹码一致）。
 * [order] 用于顺子判定，A 既可当最大（A K Q J 10）也可当最小（A 2 3 4 5）。
 */
enum class Rank(val label: String, val chips: Int, val order: Int) {
    TWO("2", 2, 2),
    THREE("3", 3, 3),
    FOUR("4", 4, 4),
    FIVE("5", 5, 5),
    SIX("6", 6, 6),
    SEVEN("7", 7, 7),
    EIGHT("8", 8, 8),
    NINE("9", 9, 9),
    TEN("10", 10, 10),
    JACK("J", 10, 11),
    QUEEN("Q", 10, 12),
    KING("K", 10, 13),
    ACE("A", 11, 14),
    ;

    /** 点数 +1，用于塔罗「力量」；A 封顶不变。 */
    fun up(): Rank = if (this == ACE) ACE else entries[ordinal + 1]
}

/**
 * 卡牌增强（Enhancement）。
 *
 * 对应原版的「增强牌」：改的是**这张牌自身**的性质，跟着牌走、跨关保留。
 * 数值全部取原作同名增强的公开数值。
 *
 * [order] 决定显示优先级——一张牌同时只会有一种增强，这里只是给 UI 一个稳定序号。
 */
enum class Enhancement(
    val cn: String,
    val short: String,
    /** 计分时额外给的筹码。 */
    val bonusChips: Int = 0,
    /** 计分时额外加的倍率。 */
    val bonusMult: Int = 0,
    /** 计分时的乘算倍率。 */
    val xMult: Double = 1.0,
    /** 当成任意花色参与同花判定。 */
    val wild: Boolean = false,
    /** 无点数无花色，只提供固定筹码。 */
    val stone: Boolean = false,
) {
    NONE("无", "", ),
    BONUS("奖励", "+30", bonusChips = 30),
    MULT("倍率", "+4", bonusMult = 4),
    WILD("万能", "万能", wild = true),
    GLASS("玻璃", "×2", xMult = 2.0),
    STEEL("钢铁", "留手×1.5"),
    STONE("石头", "+50", bonusChips = 50, stone = true),
    GOLD("黄金", "打出+\$3"),
    LUCKY("幸运", "1/5 +20"),
    ;

    val isNone: Boolean get() = this == NONE
}

/**
 * 蜡封（Seal）。
 *
 * 原版四种，本作保留三种可被"看一眼就懂"的：红蜡封重触发、金蜡封给钱、
 * 蓝蜡封给经验（折算成现金）。紫蜡封要生成塔罗牌，在双槽位下容易卡住流程，未收录。
 */
/**
 * 蜡封（Seal）。原版 4 种，效果逐条对齐 balatrowiki 的 Card modifiers 页：
 * 红 = 重触发、金 = 计分时 +\$3、蓝 = 回合结束时留在手牌里就生成行星牌、
 * 紫 = 被弃掉时生成塔罗牌。
 *
 * 蓝蜡封在 1.0.1f 之后生成的是「这一关最后打出的牌型」对应的行星，
 * 不是随机行星 —— 这样玩家才能稳定养自己想打的牌型。
 */
enum class Seal(val cn: String, val glyph: String) {
    NONE("无", ""),
    RED("红蜡", "R"),
    GOLD("金蜡", "G"),
    BLUE("蓝蜡", "B"),
    PURPLE("紫蜡", "P"),
    ;

    val isNone: Boolean get() = this == NONE
}

/**
 * 版本（Edition）。
 *
 * 与小丑共用一套版本概念：箔（+50 筹码）、全息（+10 倍率）、多彩（×1.5）。
 * 负片（Negative）在原版里让牌不占槽位，本作收录为「小丑专用」——
 * 买到负片小丑时槽位立即 +1，效果等价但不需要额外的槽位账本。
 *
 * [buyExtra] 是商店加价，[sellBonus] 是卖出时计入的半价基数，两者都取原作数值。
 */
enum class Edition(val cn: String, val short: String, val buyExtra: Int, val sellBonus: Int) {
    BASE("普通", "", 0, 0),
    FOIL("箔", "箔", 2, 2),
    HOLO("全息", "全", 3, 3),
    POLY("多彩", "彩", 5, 5),
    NEGATIVE("负片", "负", 5, 5),
    ;

    val isBase: Boolean get() = this == BASE
}

/**
 * 一张扑克牌。
 *
 * [id] 在整副牌里唯一（0..51），用于 UI 的稳定 key 与存档的最低成本序列化 ——
 * 同一张牌在不同时刻处于手牌、弃牌堆、抽牌堆时都保持同一身份，
 * 所以增强/蜡封/版本只需要在牌堆主表里存一份。
 */
data class Card(
    val id: Int,
    val rank: Rank,
    val suit: Suit,
    val enh: Enhancement = Enhancement.NONE,
    val seal: Seal = Seal.NONE,
    val edition: Edition = Edition.BASE,
) {
    /** 石头牌没有点数，不贡献牌面筹码。 */
    val chips: Int get() = if (enh.stone) 0 else rank.chips

    /** 这张牌在「同花」判定里算哪个花色；万能牌返回 null 表示"任意花色"。 */
    val effectiveSuit: Suit? get() = if (enh.wild) null else suit

    /** 形如 "A♠"，用于日志与测试断言。 */
    override fun toString(): String = rank.label + suit.symbol

    /** 存档用的紧凑 token：`id` 或 `id~E~S~Ed`。 */
    fun token(): String =
        if (enh.isNone && seal.isNone && edition.isBase) "$id"
        else "$id~${enh.ordinal}~${seal.ordinal}~${edition.ordinal}"

    companion object {
        /** 反序列化 [token]；格式非法时返回 null，由调用方兜底。 */
        fun fromToken(deck: List<Card>, token: String): Card? {
            val parts = token.split('~')
            val id = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val base = deck.getOrNull(id) ?: return null
            if (parts.size < 4) return base
            val e = Enhancement.entries.getOrNull(parts[1].toIntOrNull() ?: 0) ?: Enhancement.NONE
            val s = Seal.entries.getOrNull(parts[2].toIntOrNull() ?: 0) ?: Seal.NONE
            val ed = Edition.entries.getOrNull(parts[3].toIntOrNull() ?: 0) ?: Edition.BASE
            return base.copy(enh = e, seal = s, edition = ed)
        }
    }
}

/**
 * 一副 52 张标准牌。
 *
 * 洗牌用可注入种子的 [kotlin.random.Random]，便于测试复现与"同一种子同一局"。
 */
object Deck {

    /** 生成一副完整的 52 张牌（未洗、无增强）。 */
    fun standard(): List<Card> {
        val cards = ArrayList<Card>(52)
        var id = 0
        for (suit in Suit.entries) {
            for (rank in Rank.entries) {
                cards.add(Card(id++, rank, suit))
            }
        }
        return cards
    }

    /** 返回洗好的牌堆。 */
    fun shuffled(random: kotlin.random.Random): MutableList<Card> =
        standard().shuffled(random).toMutableList()

    /**
     * 按 [DeckType] 生成初始牌堆。
     *
     * 牌组差异只体现在「初始牌面改造」与「资源加成」上，不改变 52 张的构成——
     * 这一点与原版一致：牌组是起手条件的差异，不是规则差异。
     */
    fun forType(type: DeckType, random: kotlin.random.Random): List<Card> {
        val base = standard()
        return when (type) {
            DeckType.RED, DeckType.BLUE, DeckType.YELLOW, DeckType.GREEN,
            DeckType.WHITE -> base
            DeckType.BLACK -> base
            // 镀金牌组：前 10 张变成黄金牌，直接给出「打牌赚钱」的开局种子
            DeckType.GOLD -> base.mapIndexed { i, c ->
                if (i < 10) c.copy(enh = Enhancement.GOLD) else c
            }
            // 玻璃牌组：8 张玻璃 + 8 张钢铁，高风险高回报
            DeckType.GLASS -> base.mapIndexed { i, c ->
                when {
                    i < 8 -> c.copy(enh = Enhancement.GLASS)
                    i < 16 -> c.copy(enh = Enhancement.STEEL)
                    else -> c
                }
            }
            // 随机牌组：12 张随机增强
            DeckType.RANDOM -> base.mapIndexed { i, c ->
                if (i < 12) {
                    val pool = Enhancement.entries.filter { it != Enhancement.NONE }
                    c.copy(enh = pool[random.nextInt(pool.size)])
                } else c
            }
            // 魔法 / 星云 / 幽灵：牌面不变，开局送消耗品（见 GameEngine.startRun）
            DeckType.MAGIC, DeckType.NEBULA, DeckType.GHOST -> base
            // 弃牌牌组：没有人头。J/Q/K 就地降成 2/3/4 —— 保留 52 个稳定 id，
            // 存档与"同一种子同一局"都不受影响，牌面效果等同原版的 40 张。
            DeckType.ABANDONED -> base.map { c ->
                when (c.rank) {
                    Rank.JACK -> c.copy(rank = Rank.TWO)
                    Rank.QUEEN -> c.copy(rank = Rank.THREE)
                    Rank.KING -> c.copy(rank = Rank.FOUR)
                    else -> c
                }
            }
            // 棋盘牌组：只有黑桃与红桃。方块→黑桃、梅花→红桃，构成 26 + 26。
            DeckType.CHECKERED -> base.map { c ->
                when (c.suit) {
                    Suit.DIAMOND -> c.copy(suit = Suit.SPADE)
                    Suit.CLUB -> c.copy(suit = Suit.HEART)
                    else -> c
                }
            }
            // 彩绘：牌面不变，手牌 +2 / 小丑位 -1（资源在枚举上）
            DeckType.PAINTED -> base
        }
    }
}
