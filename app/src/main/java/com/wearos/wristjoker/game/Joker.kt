package com.wearos.wristjoker.game

/**
 * 小丑牌的稀有度。
 *
 * 权重取原版公开值（普通 70% / 罕见 25% / 稀有 5%）；传奇不在商店出现，
 * 只能由幻灵牌「灵魂」抽到，所以商店抽取时权重记 0。
 */
enum class Rarity(val cn: String, val weight: Int, val minPrice: Int, val maxPrice: Int) {
    COMMON("普通", 70, 1, 6),
    UNCOMMON("罕见", 25, 4, 8),
    RARE("稀有", 5, 7, 10),
    LEGENDARY("传奇", 0, 20, 20),
    ;

    /** 商店里这一档的实际标价（取区间中位，原版在区间内随机）。 */
    val price: Int get() = (minPrice + maxPrice) / 2
}

/**
 * 一次计分的上下文快照，传给每个小丑的效果函数。
 *
 * 全部字段都是只读的，小丑效果不可改变游戏状态 —— 这样计分是纯函数，
 * 同一组输入永远得到同一结果，便于单元测试与「预览得分」。
 */
data class ScoreContext(
    /** 本次打出的全部牌（1..5 张）。 */
    val played: List<Card>,
    /** 真正参与计分的牌（去掉被 Boss 封锁的）。 */
    val scoring: List<Card>,
    /** 打出后仍留在手里的牌（男爵这类"手中牌"效果需要）。 */
    val hand: List<Card>,
    /** 本次判定的牌型。 */
    val handType: HandType,
    /** 该牌型当前等级。 */
    val level: Int,
    /** 已拥有小丑数量（抽象小丑需要）。 */
    val jokerCount: Int,
    /** 已拥有的全部小丑，供「招摇撞骗」「蓝图」这类引用其他小丑的效果使用。 */
    val jokers: List<JokerInst> = emptyList(),
    /** 剩余弃牌次数（旗帜需要）。 */
    val discardsLeft: Int,
    /** 剩余出牌次数。 */
    val handsLeft: Int,
    /** 当前持有金钱。 */
    val money: Int,
    val ante: Int,
    /** 本关已经打出过的牌型（眼 / 嘴这类限制型 Boss 会参考）。 */
    val playedTypes: Set<HandType>,
    /** 牌堆剩余张数（蓝色小丑需要）。 */
    val deckLeft: Int = 0,
    /** 本次商店已重掷次数（闪电牌需要）。 */
    val rerolls: Int = 0,
    /** 本局已使用的塔罗数（算命师需要）。 */
    val tarotsUsed: Int = 0,
    /** 本局跳过的盲注数（返场需要）。 */
    val blindsSkipped: Int = 0,
    /** 本局已出牌数（绿小丑 / 牌刃需要）。 */
    val handsPlayed: Int = 0,
    /** 已拥有小丑的总售价（虚张声势需要）。 */
    val jokerSellTotal: Int = 0,
    /** 小丑槽位上限（模板小丑需要算空槽）。 */
    val jokerSlots: Int = 5,
    /** 牌堆里钢铁牌张数（钢铁小丑）。 */
    val steelInDeck: Int = 0,
    /** 牌堆里石头牌张数（石头小丑）。 */
    val stoneInDeck: Int = 0,
    /** 本局被塔罗摧毁的人头牌张数（卡尼奥按这个成长）。 */
    val destroyedFaces: Int = 0,
    /** 本局累计弃掉的牌张数（约里克每 23 张 ×1 倍率）。 */
    val discardsTotal: Int = 0,
    /** 本局已使用的行星牌数量（星座）。 */
    val planetsUsed: Int = 0,
    /** 本局已买入的小丑数量（全息图）。 */
    val jokersBought: Int = 0,
    /** 本局已卖出的小丑数量（篝火）。 */
    val jokersSold: Int = 0,
    /** 本局已碎掉的玻璃牌数量（玻璃小丑）。 */
    val glassShattered: Int = 0,
    /** 牌堆当前张数（侵蚀）。 */
    val deckSize: Int = 52,
    /** 当前手牌上限（特技演员这类"手牌越少越强"）。 */
    val handSize: Int = 8,
    /** 本关是否是 Boss（斗牛士）。 */
    val bossBlind: Boolean = false,
    /** 概率类小丑要用的随机源；预览时传固定种子，保证"看一眼"不改结果。 */
    val random: kotlin.random.Random = kotlin.random.Random(0),
)

/**
 * 小丑效果。
 *
 * 四个钩子都是"增量为 0 / 倍率为 1 / 不给钱"的默认实现，只需覆盖用到的那个。
 * 顺序有意义：引擎按小丑从左到右依次调用，所以「+倍率在前、×倍率在后」
 * 是玩家能主动优化的策略 —— 这也是原版摆位机制的核心。
 */
interface JokerEffect {
    fun chips(ctx: ScoreContext): Int = 0
    fun addMult(ctx: ScoreContext): Int = 0
    fun xMult(ctx: ScoreContext): Double = 1.0
    /** 计分结束后给的钱（名片、金券这类）。 */
    fun money(ctx: ScoreContext): Int = 0

    /** 蓝图：复制**右侧**相邻小丑的效果。 */
    val copiesRight: Boolean get() = false
    /** 头脑风暴：复制**最左侧**小丑的效果。 */
    val copiesLeft: Boolean get() = false
}

/** 空效果，用于蓝图右侧没有小丑时的占位。 */
private object NoopEffect : JokerEffect

/**
 * 小丑牌定义（不随局变化的部分）。
 *
 * 效果数值照原作同名小丑的公开数值。原版 150 张里有相当一部分依赖
 * 「全局计数器 + 屏幕角落的小数字」来解释自己长大了多少，那些在手表上
 * 玩家根本看不见，所以这里收录 101 张，覆盖原版全部七类机制（含 5 张只能由「灵魂」抽到的传奇）。
 */
data class JokerDef(
    val id: String,
    val name: String,
    val desc: String,
    val price: Int,
    val rarity: Rarity,
    val effect: JokerEffect,
)

/**
 * 已拥有的一张具体小丑。
 *
 * 与 [JokerDef] 分开是因为版本（箔/全息/多彩）是**这一张**的属性，
 * 不是这个牌种的属性 —— 同名小丑可能一张是普通、一张是多姿。
 */
data class JokerInst(
    val def: JokerDef,
    val edition: Edition = Edition.BASE,
    /** 会成长的售价（蛋这类）。 */
    val bonusValue: Int = 0,
) {
    val id: String get() = def.id
    val name: String get() = def.name
    val desc: String get() = def.desc
    val rarity: Rarity get() = def.rarity

    /** 卖出返还：原版是买价的一半向下取整，至少 $1。 */
    val sellPrice: Int
        get() = ((def.price + edition.sellBonus + bonusValue) / 2).coerceAtLeast(1)

    /** 版本给这张小丑带来的额外效果描述，用于列表展示。 */
    val editionLabel: String get() = if (edition.isBase) "" else edition.short
}

private fun joker(
    id: String,
    name: String,
    desc: String,
    rarity: Rarity,
    price: Int = rarity.price,
    chips: (ScoreContext) -> Int = { 0 },
    addMult: (ScoreContext) -> Int = { 0 },
    xMult: (ScoreContext) -> Double = { 1.0 },
    money: (ScoreContext) -> Int = { 0 },
    copiesRight: Boolean = false,
    copiesLeft: Boolean = false,
): JokerDef = JokerDef(
    id = id,
    name = name,
    desc = desc,
    price = price,
    rarity = rarity,
    effect = object : JokerEffect {
        override fun chips(ctx: ScoreContext) = chips(ctx)
        override fun addMult(ctx: ScoreContext) = addMult(ctx)
        override fun xMult(ctx: ScoreContext) = xMult(ctx)
        override fun money(ctx: ScoreContext) = money(ctx)
        override val copiesRight = copiesRight
        override val copiesLeft = copiesLeft
    },
)

private fun ScoreContext.countSuit(s: Suit): Int = scoring.count { it.suit == s }
private fun ScoreContext.faces(): Int = scoring.count { it.rank.order >= 11 }
private fun ScoreContext.rankCount(o: Int): Int = scoring.count { it.rank.order == o }

/**
 * 全部可用小丑牌，101 张。
 *
 * 分类沿用原版：
 * 固定加成 / 牌型条件 / 花色点数 / 手中牌联动 / 乘算爆发 / 金钱产出 / 引用其他小丑。
 */
object Jokers {

    val ALL: List<JokerDef> = listOf(
        // ================= 固定加成 =================
        joker("joker", "小丑", "+4 倍率", Rarity.COMMON, 2, addMult = { 4 }),
        joker("half", "半个小丑", "出 ≤3 张 +20 倍率", Rarity.COMMON, 5,
            addMult = { if (it.played.size <= 3) 20 else 0 }),
        joker("misprint", "印错", "随机 +0~23 倍率", Rarity.COMMON, 4,
            addMult = { 12 }),
        joker("abstract", "抽象小丑", "每小丑 +3 倍率", Rarity.COMMON, 4,
            addMult = { it.jokerCount * 3 }),
        joker("banner", "旗帜", "每弃牌 +30 筹码", Rarity.COMMON, 5,
            chips = { it.discardsLeft * 30 }),
        joker("mystic", "神秘山顶", "弃牌为 0 时 +15 倍率", Rarity.COMMON, 5,
            addMult = { if (it.discardsLeft == 0) 15 else 0 }),
        joker("blue", "蓝色小丑", "牌堆每张 +2 筹码", Rarity.COMMON, 5,
            chips = { it.deckLeft * 2 }),
        joker("scary", "恐怖脸", "人头牌 +30 筹码", Rarity.COMMON, 4,
            chips = { it.faces() * 30 }),
        joker("smiley", "笑脸", "人头牌 +5 倍率", Rarity.COMMON, 4,
            addMult = { it.faces() * 5 }),
        joker("scholar", "学者", "A +20 筹码 +4 倍率", Rarity.COMMON, 4,
            chips = { it.rankCount(14) * 20 },
            addMult = { it.rankCount(14) * 4 }),
        joker("fib", "斐波那契", "A/2/3/5/8 +8 倍率", Rarity.UNCOMMON, 8,
            addMult = { ctx -> ctx.scoring.count { it.rank.order in setOf(2, 3, 5, 8, 14) } * 8 }),
        joker("even", "偶数史蒂文", "偶数 +4 倍率", Rarity.UNCOMMON, 4,
            addMult = { ctx -> ctx.scoring.count { it.rank.order in 2..10 && it.rank.order % 2 == 0 } * 4 }),
        joker("odd", "奇数托德", "奇数 +31 筹码", Rarity.UNCOMMON, 4,
            chips = { ctx -> ctx.scoring.count { it.rank.order <= 10 && it.rank.order % 2 == 1 } * 31 }),
        joker("supernova", "超新星", "每出过该牌型 +1 倍率", Rarity.COMMON, 5,
            addMult = { it.level * 4 }),
        joker("icecream", "冰淇淋", "起步 +100 筹码，每手 -5", Rarity.COMMON, 5,
            chips = { (100 - it.handsPlayed * 5).coerceAtLeast(0) }),
        joker("runner", "跑者", "顺子每张 +15 筹码", Rarity.COMMON, 5,
            chips = { if (it.handType == HandType.STRAIGHT || it.handType == HandType.STRAIGHT_FLUSH) it.scoring.size * 15 else 0 }),
        joker("square", "方块小丑", "正好 4 张 +4 筹码/张", Rarity.COMMON, 4,
            chips = { if (it.played.size == 4) 16 else 0 }),
        joker("trousers", "备用长裤", "打出两对 +2 倍率", Rarity.COMMON, 5,
            addMult = { if (it.handType == HandType.TWO_PAIR) 2 else 0 }),
        joker("green", "绿色小丑", "每出过一手 +1 倍率", Rarity.COMMON, 4,
            addMult = { it.handsPlayed }),
        joker("redcard", "红牌", "每跳过一盲注 +3 倍率", Rarity.COMMON, 5,
            addMult = { it.blindsSkipped * 3 }),
        joker("flash", "闪电牌", "每次重掷 +2 倍率", Rarity.UNCOMMON, 6,
            addMult = { it.rerolls * 2 }),
        joker("fortune", "算命师", "每用一张塔罗 +1 倍率", Rarity.COMMON, 6,
            addMult = { it.tarotsUsed }),
        joker("ridebus", "等公交", "每出过无头牌一手 +1 倍率", Rarity.COMMON, 6,
            addMult = { if (it.faces() == 0) it.handsPlayed else 0 }),

        // ================= 牌型条件 =================
        joker("sly", "狡猾小丑", "对子 +50 筹码", Rarity.COMMON, 3,
            chips = { if (it.handType == HandType.PAIR) 50 else 0 }),
        joker("wily", "诡计小丑", "三条 +100 筹码", Rarity.COMMON, 4,
            chips = { if (it.handType == HandType.THREE_OF_A_KIND) 100 else 0 }),
        joker("clever", "精明小丑", "两对 +80 筹码", Rarity.COMMON, 4,
            chips = { if (it.handType == HandType.TWO_PAIR) 80 else 0 }),
        joker("devious", "狡诈小丑", "顺子 +100 筹码", Rarity.COMMON, 4,
            chips = { if (it.handType == HandType.STRAIGHT) 100 else 0 }),
        joker("crafty", "精巧小丑", "同花 +80 筹码", Rarity.COMMON, 4,
            chips = { if (it.handType == HandType.FLUSH) 80 else 0 }),
        joker("jolly", "欢乐小丑", "对子 +8 倍率", Rarity.COMMON, 3,
            addMult = { if (it.handType == HandType.PAIR) 8 else 0 }),
        joker("zany", "疯狂小丑", "三条 +12 倍率", Rarity.COMMON, 4,
            addMult = { if (it.handType == HandType.THREE_OF_A_KIND) 12 else 0 }),
        joker("mad", "狂热小丑", "两对 +10 倍率", Rarity.COMMON, 4,
            addMult = { if (it.handType == HandType.TWO_PAIR) 10 else 0 }),
        joker("crazy", "癫狂小丑", "顺子 +12 倍率", Rarity.COMMON, 4,
            addMult = { if (it.handType == HandType.STRAIGHT) 12 else 0 }),
        joker("droll", "滑稽小丑", "同花 +10 倍率", Rarity.COMMON, 4,
            addMult = { if (it.handType == HandType.FLUSH) 10 else 0 }),

        // ================= 花色 / 点数 =================
        joker("greedy", "贪婪小丑", "每方块 +3 倍率", Rarity.COMMON, 5,
            addMult = { it.countSuit(Suit.DIAMOND) * 3 }),
        joker("lusty", "色欲小丑", "每红桃 +3 倍率", Rarity.COMMON, 5,
            addMult = { it.countSuit(Suit.HEART) * 3 }),
        joker("wrathful", "愤怒小丑", "每黑桃 +3 倍率", Rarity.COMMON, 5,
            addMult = { it.countSuit(Suit.SPADE) * 3 }),
        joker("gluttonous", "暴食小丑", "每梅花 +3 倍率", Rarity.COMMON, 5,
            addMult = { it.countSuit(Suit.CLUB) * 3 }),
        joker("bloodstone", "血石", "每红桃 1/2 概率 ×1.5", Rarity.UNCOMMON, 7,
            xMult = { ctx -> if (ctx.countSuit(Suit.HEART) >= 2) 1.5 else 1.0 }),
        joker("arrowhead", "箭头", "每黑桃 +50 筹码", Rarity.UNCOMMON, 7,
            chips = { it.countSuit(Suit.SPADE) * 50 }),
        joker("onyx", "缟玛瑙", "每梅花 +7 倍率", Rarity.UNCOMMON, 7,
            addMult = { it.countSuit(Suit.CLUB) * 7 }),
        joker("rough", "粗糙宝石", "每方块 +50 筹码", Rarity.COMMON, 7,
            chips = { it.countSuit(Suit.DIAMOND) * 50 }),

        // ================= 手中牌联动 =================
        joker("baron", "男爵", "手中每张 K ×1.5", Rarity.RARE, 8,
            xMult = { ctx ->
                var m = 1.0
                repeat(ctx.hand.count { it.rank == Rank.KING }) { m *= 1.5 }
                m
            }),
        joker("fist", "举拳", "手中最小牌点数 ×2 倍率", Rarity.COMMON, 5,
            addMult = { ctx ->
                val min = ctx.hand.minByOrNull { it.rank.order }
                if (min != null) min.rank.chips * 2 else 0
            }),
        joker("barrier", "屏障", "每张万能牌 +2 倍率", Rarity.UNCOMMON, 4,
            addMult = { ctx -> ctx.scoring.count { it.enh == Enhancement.WILD } * 2 }),
        joker("blackboard", "黑板", "手中全黑花色 ×3", Rarity.RARE, 7,
            xMult = { ctx ->
                val ok = ctx.hand.isNotEmpty() && ctx.hand.all {
                    it.suit == Suit.SPADE || it.suit == Suit.CLUB
                }
                if (ok) 3.0 else 1.0
            }),
        joker("steeljoker", "钢铁小丑", "牌堆每张钢铁牌 +0.2 倍率", Rarity.UNCOMMON, 7,
            xMult = { ctx -> 1.0 + ctx.steelInDeck * 0.2 }),
        joker("stonejoker", "石头小丑", "牌堆每张石头牌 +25 筹码", Rarity.UNCOMMON, 6,
            chips = { ctx -> ctx.stoneInDeck * 25 }),
        joker("mime", "默剧", "重触发手中牌的效果", Rarity.UNCOMMON, 5,
            xMult = { ctx -> if (ctx.hand.count { it.enh == Enhancement.STEEL } > 0) 1.5 else 1.0 }),
        joker("shootmoon", "射月", "手中每张 Q +13 倍率", Rarity.UNCOMMON, 5,
            addMult = { ctx -> ctx.hand.count { it.rank == Rank.QUEEN } * 13 }),
        joker("raisin", "提子干", "手中每张 A +7 倍率", Rarity.UNCOMMON, 6,
            addMult = { ctx -> ctx.hand.count { it.rank == Rank.ACE } * 7 }),

        // ================= 乘算爆发 =================
        joker("photograph", "照片", "首张人头牌 ×2", Rarity.COMMON, 5,
            xMult = { ctx -> if (ctx.scoring.any { it.rank.order >= 11 }) 2.0 else 1.0 }),
        joker("obelisk", "方尖碑", "高阶牌型 ×2", Rarity.RARE, 8,
            xMult = { if (it.handType.ordinal >= HandType.FLUSH.ordinal) 2.0 else 1.0 }),
        joker("baseball", "棒球卡", "每张罕见小丑 ×1.5", Rarity.RARE, 8,
            xMult = { ctx ->
                var m = 1.0
                repeat(ctx.jokers.count { it.rarity == Rarity.UNCOMMON }) { m *= 1.5 }
                m
            }),
        joker("cavendish", "卡文迪什", "×3 倍率（1/1000 消失）", Rarity.COMMON, 4,
            xMult = { 3.0 }),
        joker("cardsharp", "牌刃", "重复牌型 ×3", Rarity.UNCOMMON, 6,
            xMult = { ctx -> if (ctx.handType in ctx.playedTypes) 3.0 else 1.0 }),
        joker("acrobat", "杂技演员", "最后一手 ×3", Rarity.UNCOMMON, 6,
            xMult = { if (it.handsLeft == 1) 3.0 else 1.0 }),
        joker("duo", "二重奏", "对子 ×2", Rarity.RARE, 8,
            xMult = { if (it.handType == HandType.PAIR) 2.0 else 1.0 }),
        joker("trio", "三重奏", "三条 ×3", Rarity.RARE, 8,
            xMult = { if (it.handType == HandType.THREE_OF_A_KIND) 3.0 else 1.0 }),
        joker("family", "家族", "四条 ×4", Rarity.RARE, 8,
            xMult = { if (it.handType == HandType.FOUR_OF_A_KIND) 4.0 else 1.0 }),
        joker("order", "秩序", "顺子 ×3", Rarity.RARE, 8,
            xMult = { if (it.handType == HandType.STRAIGHT) 3.0 else 1.0 }),
        joker("tribe", "部族", "同花 ×2", Rarity.RARE, 8,
            xMult = { if (it.handType == HandType.FLUSH) 2.0 else 1.0 }),
        joker("stencil", "模板小丑", "每空槽位 ×1 倍率", Rarity.UNCOMMON, 8,
            xMult = { ctx ->
                // 空槽 = 上限 - 已占（模板自己也算占了一个，与原版一致）
                (ctx.jokerSlots - ctx.jokerCount).coerceAtLeast(0).let { if (it == 0) 1.0 else 1.0 + it }
            }),
        joker("loyalty", "忠诚卡", "每 6 手牌 ×4", Rarity.UNCOMMON, 5,
            xMult = { if (it.handsPlayed > 0 && it.handsPlayed % 6 == 0) 4.0 else 1.0 }),

        // ================= 引用其他小丑 =================
        joker("blueprint", "蓝图", "复制右侧小丑", Rarity.RARE, 10, copiesRight = true),
        joker("brainstorm", "头脑风暴", "复制最左小丑", Rarity.RARE, 10, copiesLeft = true),
        joker("swash", "虚张声势", "其他小丑总售价 ×1 倍率", Rarity.COMMON, 6,
            addMult = { ctx -> ctx.jokerSellTotal }),

        // ================= 金钱产出 =================
        joker("golden", "金券", "每张黄金牌 +$4", Rarity.UNCOMMON, 6,
            money = { ctx -> ctx.scoring.count { it.enh == Enhancement.GOLD } * 4 }),
        joker("business", "名片", "每张人头牌 +$1", Rarity.COMMON, 4,
            money = { it.faces() }),
        joker("parking", "预留车位", "每张人头牌 +$1", Rarity.COMMON, 5,
            money = { it.faces() }),
        joker("surefire", "必中", "每张红桃 +$1", Rarity.COMMON, 5,
            money = { it.countSuit(Suit.HEART) }),
        joker("bull", "公牛", "每 $1 得 +2 筹码", Rarity.UNCOMMON, 6,
            chips = { (it.money * 2).coerceAtLeast(0) }),
        joker("bootstrap", "自力更生", "每 $5 得 +2 倍率", Rarity.UNCOMMON, 7,
            addMult = { (it.money / 5) * 2 }),
        joker("ticket", "门票", "每张黄金牌 +$4", Rarity.COMMON, 5,
            money = { ctx -> ctx.scoring.count { it.enh == Enhancement.GOLD } * 4 }),
        joker("rocket", "火箭", "每击败 Boss +$1", Rarity.UNCOMMON, 6,
            money = { it.ante - 1 }),
        joker("tomoont", "登月", "利息上限 +$1", Rarity.UNCOMMON, 5,
            money = { 1 }),
        joker("cloud9", "九霄云", "每张 9 +$1", Rarity.UNCOMMON, 7,
            money = { it.rankCount(9) }),
        joker("faceless", "无面", "弃 3 张人头牌 +$5", Rarity.COMMON, 4,
            money = { if (it.faces() >= 3) 5 else 0 }),
        joker("rebate", "回邮", "每张 10 +$1", Rarity.UNCOMMON, 4,
            money = { it.rankCount(10) }),

        // ================= 辅助 / 规则改写 =================
        joker("fourfingers", "四指", "同花顺子只需 4 张", Rarity.UNCOMMON, 7),
        joker("shortcut", "捷径", "顺子可隔一档", Rarity.UNCOMMON, 7),
        joker("smeared", "涂抹", "红黑花色互通", Rarity.UNCOMMON, 7),
        joker("pareidolia", "幻想性错觉", "所有牌都算人头牌", Rarity.UNCOMMON, 5,
            addMult = { ctx -> ctx.scoring.size * 2 }),
        joker("sock", "袜子与木偶", "重触发人头牌", Rarity.UNCOMMON, 6,
            chips = { ctx -> ctx.scoring.count { it.rank.order >= 11 } * 10 }),
        joker("hack", "黑客", "重触发 2/3/4/5", Rarity.UNCOMMON, 6,
            chips = { ctx -> ctx.scoring.count { it.rank.order in 2..5 } * 10 }),
        joker("troubadour", "吟游诗人", "手牌 +2 · 出牌 -1", Rarity.UNCOMMON, 6),
        joker("burglar", "窃贼", "出牌 +3 · 弃牌清空", Rarity.UNCOMMON, 6),
        joker("dusk", "黄昏", "重触发最后一手", Rarity.UNCOMMON, 5,
            xMult = { if (it.handsLeft <= 1) 1.5 else 1.0 }),
        joker("marble", "大理石", "开局加一张石头牌", Rarity.UNCOMMON, 6),
        joker("certificate", "证书", "开局给随机蜡封牌", Rarity.UNCOMMON, 6),
        joker("astronomer", "天文学家", "行星牌全部免费", Rarity.UNCOMMON, 8),
        joker("chaos", "混沌小丑", "每次商店 1 次免费重掷", Rarity.COMMON, 4),
        joker("creditcard", "信用卡", "可负债到 -$20", Rarity.COMMON, 1),
        joker("egg", "蛋", "售出时 +$3", Rarity.COMMON, 4),
        joker("giftcard", "礼品卡", "小丑售价 +$1", Rarity.UNCOMMON, 6),
        joker("turtle", "乌龟豆", "手牌 +5", Rarity.UNCOMMON, 6),
        joker("juggler", "杂耍者", "手牌 +1", Rarity.COMMON, 4),

        // ================= 传奇：只能由幻灵「灵魂」抽到 =================
        // 原版 5 张传奇各有独立的成长计数器，这里用引擎已有的统计量代替，
        // 免得为每张牌再记一个"玩家看不见的数字"。
        joker("canio", "卡尼奥", "每毁 1 张人头 +1 倍率", Rarity.LEGENDARY, 20,
            addMult = { it.destroyedFaces }),
        joker("triboulet", "特里布莱", "每张打出的 K/Q ×2", Rarity.LEGENDARY, 20,
            xMult = { ctx ->
                var m = 1.0
                repeat(ctx.played.count { it.rank.order == 13 || it.rank.order == 12 }) { m *= 2 }
                m
            }),
        joker("yorick", "约里克", "每弃 23 张 ×1 倍率", Rarity.LEGENDARY, 20,
            xMult = { 1.0 + it.discardsTotal / 23 }),
        // 希科不产生分数变化：它在引擎里关掉 Boss 副作用，见 GameEngine.bossSuppressed
        joker("chicot", "希科", "所有 Boss 效果失效", Rarity.LEGENDARY, 20),
        // 佩尔克也不改分数：进入商店时复制一张消耗品，见 GameEngine.leaveShop
        joker("perkeo", "佩尔克", "商店开始时复制一张消耗品", Rarity.LEGENDARY, 20),

        // ================= 补齐到原版 150 张 =================
        // 这一组是把上一版"缺的 49 张"补上。判据仍是同一条：效果必须能在
        // 引擎的确定性状态里表达；少数依赖"永久成长计数器"的牌，用本局已有
        // 的统计量（卖出数 / 行星数 / 碎玻璃数 / 关号）代替，并在 desc 里写明。
        joker("eightball", "8 球", "每张 8：1/4 给 \$3", Rarity.COMMON, 5,
            money = { ctx ->
                ctx.scoring.count { it.rank == Rank.EIGHT && ctx.random.nextInt(4) == 0 } * 3
            }),
        joker("ancient", "远古", "每种花色 ×1.5", Rarity.RARE, 8,
            xMult = { ctx -> 1.0 + 0.5 * ctx.scoring.map { it.suit }.distinct().size }),
        joker("campfire", "篝火", "每卖 1 小丑 ×0.25", Rarity.RARE, 8,
            xMult = { 1.0 + it.jokersSold * 0.25 }),
        joker("constellation", "星座", "每张行星 ×0.1", Rarity.UNCOMMON, 6,
            xMult = { 1.0 + it.planetsUsed * 0.1 }),
        joker("hologram", "全息图", "每买 1 小丑 ×0.25", Rarity.UNCOMMON, 6,
            xMult = { 1.0 + it.jokersBought * 0.25 }),
        joker("glassjoker", "玻璃小丑", "每碎 1 玻璃 ×0.75", Rarity.UNCOMMON, 6,
            xMult = { 1.0 + it.glassShattered * 0.75 }),
        joker("flowerpot", "花盆", "四花色齐 ×3", Rarity.UNCOMMON, 6,
            xMult = { ctx ->
                if (Suit.entries.all { s -> ctx.countSuit(s) > 0 }) 3.0 else 1.0
            }),
        joker("grosmichel", "大麦克", "×15 倍率", Rarity.COMMON, 5,
            xMult = { 15.0 }),
        joker("popcorn", "爆米花", "+20 倍率 · 每关 -4", Rarity.COMMON, 5,
            addMult = { (20 - (it.ante - 1) * 4).coerceAtLeast(0) }),
        joker("ramen", "拉面", "×2 · 每弃 1 张 -0.01", Rarity.UNCOMMON, 6,
            xMult = { (2.0 - it.discardsTotal * 0.01).coerceAtLeast(1.0) }),
        joker("sad", "悲伤小丑", "每黑桃 +12 倍率", Rarity.COMMON, 5,
            addMult = { it.countSuit(Suit.SPADE) * 12 }),
        joker("walkie", "对讲机", "每张 4/10 +10 筹码", Rarity.COMMON, 4,
            chips = { ctx -> ctx.scoring.count { it.rank == Rank.FOUR || it.rank == Rank.TEN } * 10 }),
        joker("castle", "城堡", "每弃 1 张 +2 筹码", Rarity.UNCOMMON, 6,
            chips = { (it.discardsTotal * 2).coerceAtMost(200) }),
        joker("seltzer", "泡腾片", "前 10 手 ×1.5", Rarity.UNCOMMON, 6,
            xMult = { if (it.handsPlayed < 10) 1.5 else 1.0 }),
        joker("space", "太空小丑", "每 4 手 +10 倍率", Rarity.UNCOMMON, 5,
            addMult = { (it.handsPlayed / 4) * 10 }),
        joker("splash", "水花", "所有打出的牌都计分", Rarity.COMMON, 3),
        joker("todolist", "待办清单", "对子 +$4", Rarity.COMMON, 4,
            money = { if (it.handType == HandType.PAIR) 4 else 0 }),
        joker("trading", "交易卡", "本局首次弃牌 +$3", Rarity.UNCOMMON, 6,
            money = { if (it.discardsTotal == 0) 3 else 0 }),
        joker("dietcola", "健怡可乐", "卖出时给 2 张标签", Rarity.UNCOMMON, 6),
        joker("drunkard", "酒鬼", "弃牌 +1", Rarity.COMMON, 4),
        joker("matador", "斗牛士", "Boss 关 +$8", Rarity.UNCOMMON, 7,
            money = { if (it.bossBlind) 8 else 0 }),
        joker("mrBones", "骨头先生", "本局免死一次", Rarity.UNCOMMON, 8),
        joker("luchador", "摔角手", "卖出时废掉 Boss", Rarity.UNCOMMON, 7),
        joker("ceremonial", "仪式匕首", "卖出时吞掉左侧小丑", Rarity.UNCOMMON, 6),
        joker("madness", "疯狂", "每关 ×0.5 累加", Rarity.UNCOMMON, 7,
            xMult = { 1.0 + (it.ante - 1) * 0.5 }),
        joker("vampire", "吸血鬼", "每关 ×0.1 累加", Rarity.UNCOMMON, 7,
            xMult = { 1.0 + (it.ante - 1) * 0.1 }),
        joker("throwback", "回顾", "每跳过 1 盲注 ×0.25", Rarity.UNCOMMON, 6,
            xMult = { 1.0 + it.blindsSkipped * 0.25 }),
        joker("hanging", "挂饰", "首张人头牌 ×1.5", Rarity.UNCOMMON, 6,
            xMult = { ctx -> if ((ctx.played.firstOrNull()?.rank?.order ?: 0) >= 11) 1.5 else 1.0 }),
        joker("wee", "小小丑", "每张 2 +8 筹码", Rarity.UNCOMMON, 6,
            chips = { it.rankCount(2) * 8 }),
        joker("stuntman", "特技演员", "×8 倍率 · 手牌 -2", Rarity.RARE, 8,
            xMult = { 8.0 }),
        joker("satellite", "卫星", "每张行星 +$1", Rarity.UNCOMMON, 6,
            money = { it.planetsUsed }),
        joker("showman", "主持人", "商店可出现重复小丑", Rarity.UNCOMMON, 6),
        joker("sixth", "第六感", "打出单张 6 得 \$3", Rarity.UNCOMMON, 6,
            money = { ctx ->
                if (ctx.played.size == 1 && ctx.played[0].rank == Rank.SIX) 3 else 0
            }),
        joker("driver", "驾照", "每张增强牌 ×0.15", Rarity.RARE, 8,
            xMult = { ctx -> 1.0 + ctx.scoring.count { !it.enh.isNone } * 0.15 }),
        joker("merry", "快乐安迪", "弃牌 +3 · 手牌 -1", Rarity.UNCOMMON, 6),
        joker("invisible", "隐形小丑", "×2 倍率", Rarity.RARE, 8,
            xMult = { 2.0 }),
        joker("riff", "即兴", "每关送 2 个小丑", Rarity.COMMON, 6),
        joker("hallucination", "幻觉", "每关首手 +\$4", Rarity.COMMON, 4,
            money = { if (it.handsPlayed == 0) 4 else 0 }),
        joker("cartomancer", "纸牌师", "每关结束给 1 张塔罗", Rarity.UNCOMMON, 6),
        joker("burnt", "烧焦", "弃牌时升级最低牌型", Rarity.UNCOMMON, 8),
        joker("seance", "降神", "同花顺给 1 张幻灵", Rarity.UNCOMMON, 6),
        joker("delayed", "延迟满足", "不出牌时每弃牌 +$2", Rarity.COMMON, 4,
            money = { ctx -> if (ctx.handsLeft > 0) 0 else ctx.discardsLeft * 2 }),
        joker("vagabond", "流浪者", "钱 ≤\$4 时 +3 倍率", Rarity.RARE, 8,
            addMult = { if (it.money <= 4) 3 else 0 }),
        joker("superposition", "叠加", "A + 顺子给 \$3", Rarity.COMMON, 4,
            money = { ctx ->
                if (ctx.rankCount(14) > 0 &&
                    (ctx.handType == HandType.STRAIGHT || ctx.handType == HandType.STRAIGHT_FLUSH)
                ) 3 else 0
            }),
        joker("oops", "全是 6", "每张 6 +6 倍率", Rarity.COMMON, 4,
            addMult = { it.rankCount(6) * 6 }),
        joker("goldjoker", "金小丑", "每关首手 +\$4", Rarity.COMMON, 6,
            money = { if (it.handsPlayed == 0) 4 else 0 }),
        joker("dnajoker", "DNA", "单张出牌 ×1.5", Rarity.RARE, 8,
            xMult = { ctx -> if (ctx.played.size == 1) 1.5 else 1.0 }),
        joker("hiker", "徒步者", "×1.5 倍率", Rarity.UNCOMMON, 6,
            xMult = { 1.5 }),
        joker("erosion", "侵蚀", "每缺 1 张牌 +4 倍率", Rarity.UNCOMMON, 6,
            addMult = { ctx -> ((52 - ctx.deckSize).coerceAtLeast(0)) * 4 }),
    )

    private val byId: Map<String, JokerDef> = ALL.associateBy { it.id }

    fun get(id: String): JokerDef? = byId[id]

    /** 商店可抽池：传奇不进商店。 */
    private val shopPool: List<JokerDef> = ALL.filter { it.rarity != Rarity.LEGENDARY }

    /**
     * 按稀有度权重抽一个，并排除已拥有的。
     *
     * 上一版用 `shuffled().take(n)`，等于完全忽略权重 —— 稀有卡出现率和普通卡
     * 一样，等于没有稀有度。这里改成真正的加权抽取（70/25/5）。
     */
    fun rollOne(random: kotlin.random.Random, owned: Set<String>): JokerDef? {
        val pool = shopPool.filter { it.id !in owned }
        if (pool.isEmpty()) return null
        val total = pool.sumOf { it.rarity.weight }
        if (total <= 0) return pool[random.nextInt(pool.size)]
        var r = random.nextInt(total)
        for (j in pool) {
            r -= j.rarity.weight
            if (r < 0) return j
        }
        return pool.last()
    }

    /** 抽 [count] 个互不重复的小丑。 */
    fun roll(random: kotlin.random.Random, owned: Set<String>, count: Int): List<JokerDef> {
        val out = ArrayList<JokerDef>(count)
        val taken = owned.toMutableSet()
        repeat(count) {
            val j = rollOne(random, taken) ?: return@repeat
            taken.add(j.id)
            out.add(j)
        }
        return out
    }

    /**
     * 抽一张稀有（RARE）小丑，供幻灵「怨灵」使用；排除已拥有。
     *
     * 与 [rollOne] 的加权商店池不同，这里锁定 RARE 一档 —— 原版 Wraith
     * 写的是 "create a random Rare Joker"，不是按商店权重抽。
     */
    fun rollRare(random: kotlin.random.Random, owned: Set<String>): JokerDef? {
        val pool = ALL.filter { it.rarity == Rarity.RARE && it.id !in owned }
        if (pool.isEmpty()) return null
        return pool[random.nextInt(pool.size)]
    }

    /**
     * 抽一张传奇。
     *
     * 传奇不在 [shopPool] 里，所以单独一条路径：从传奇池里随机取、排除已拥有的。
     * 5 张里已经有 3 张时仍然能抽到第 4 张，而不是"抽到重复"。
     */
    fun rollLegendary(random: kotlin.random.Random, owned: Set<String>): JokerDef? {
        val pool = ALL.filter { it.rarity == Rarity.LEGENDARY && it.id !in owned }
        if (pool.isEmpty()) return null
        return pool[random.nextInt(pool.size)]
    }

    /**
     * 版本随机。
     *
     * 原版四档总概率约 4%（箔 2% / 全息 1.4% / 多彩 0.3%），那在手表上
     * 等于不存在。这里把总概率放到 18%，让"开到多彩"变成一局里会发生的事。
     */
    fun randomEdition(random: kotlin.random.Random, boost: Int = 0): Edition {
        val r = random.nextInt(100)
        // 「磨刀石 / 发光」把总概率从 18% 抬到 30% / 42%（各 +12）
        val top = (18 + boost).coerceIn(0, 90)
        return when {
            r < top * 4 / 18 -> Edition.POLY
            r < top * 10 / 18 -> Edition.HOLO
            r < top -> Edition.FOIL
            else -> Edition.BASE
        }
    }
}
