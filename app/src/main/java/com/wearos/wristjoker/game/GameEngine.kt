package com.wearos.wristjoker.game

/** 游戏阶段。 */
enum class Phase {
    /** 开始界面：标题 + 继续/开新局。 */
    MENU,

    /** 牌组选择。 */
    DECK_SELECT,

    /** 挑战选择：20 个固定规则预设。 */
    CHALLENGE_SELECT,

    /** 盲注选择：小盲 / 大盲 / Boss，前两个可跳过。 */
    BLIND_SELECT,

    /** 正在打一个盲注。 */
    PLAYING,

    /** 达标后的结算页：基础奖励 + 剩余出牌 + 利息 + 标签。 */
    CASH_OUT,

    /** 商店：商品位 + 补充包位 + 代金券位。 */
    SHOP,

    /** 正在开一个补充包。 */
    PACK_OPEN,

    /** 出牌次数耗尽仍未达标。 */
    GAME_OVER,

    /** 通关 8 关。 */
    WIN,
}

/** 商店里的一格。 */
sealed interface ShopItem {
    val price: Int
    val sold: Boolean
    /** 稳定的 UI key。 */
    val key: String

    data class JokerItem(
        val inst: JokerInst,
        override val price: Int,
        override val sold: Boolean = false,
    ) : ShopItem {
        override val key: String get() = "j:${inst.id}"
    }

    data class ConsumableItem(
        val c: Consumable,
        override val price: Int,
        override val sold: Boolean = false,
    ) : ShopItem {
        override val key: String get() = "c:${c.key}"
    }

    data class BoosterItem(
        val kind: PackKind,
        val size: PackSize,
        override val price: Int,
        override val sold: Boolean = false,
    ) : ShopItem {
        override val key: String get() = "b:${kind.name}:${size.name}"
    }

    data class VoucherItem(
        val v: Voucher,
        override val price: Int,
        override val sold: Boolean = false,
    ) : ShopItem {
        override val key: String get() = "v:${v.key}"
    }
}

/**
 * 商店的四段结构，严格对齐原版：
 *
 * - `cards`：2 个随机商品位（多余库存券可扩到 4），小丑 : 塔罗 : 行星 = 20 : 4 : 4
 * - `boosters`：固定 2 个补充包位，**重掷不刷新**
 * - `vouchers`：**2 个**代金券位，只在击败 Boss 后换新
 *
 * 上一版只有一个券位，是照早期版本做的；原版从 1.0 起就是两个券位并排，
 * 玩家可以在「扩容量」和「降价格」之间做一次取舍 —— 一个位就没有取舍了。
 */
data class ShopState(
    val cards: List<ShopItem> = emptyList(),
    val boosters: List<ShopItem> = emptyList(),
    val vouchers: List<ShopItem> = emptyList(),
) {
    /** 第一张券。保留这个别名，是因为很多地方只关心"有没有券"。 */
    val voucher: ShopItem? get() = vouchers.firstOrNull()

    val all: List<ShopItem> get() = cards + boosters + vouchers
}

/** 开包界面要展示的内容。 */
data class PackView(
    val kind: PackKind,
    val size: PackSize,
    val contents: List<PackContent>,
    val picked: Set<Int>,
    val maxPicks: Int,
) {
    val title: String get() = "${size.cn}${kind.cn}"
}

/**
 * 结算页的逐项明细，对应原版 Cash Out 面板。
 *
 * 钱不在这里加，要等玩家点「收下」才入帐 —— 原版也是这样，
 * 让玩家看清这一关赚在哪。
 */
data class CashOutDetail(
    val ante: Int,
    val blindKind: BlindKind,
    val score: Long,
    val target: Long,
    val baseReward: Int,
    val handsLeft: Int,
    val handBonus: Int,
    val interest: Int,
    val handyBonus: Int,
    val doubleTag: Boolean,
    val walletBonus: Int,
    val total: Int,
) {
    val isBoss: Boolean get() = blindKind == BlindKind.BOSS
    val interestDoubled: Boolean get() = doubleTag
}

/**
 * 暴露给 Compose 的只读快照。
 */
data class GameUi(
    val phase: Phase = Phase.MENU,
    val deckType: DeckType = DeckType.RED,
    val ante: Int = 1,
    val blindIndex: Int = 0,
    val blindKind: BlindKind = BlindKind.SMALL,
    val modifier: BossModifier = BossModifier.NONE,
    val target: Long = 300,
    val score: Long = 0,
    val handsLeft: Int = 4,
    val discardsLeft: Int = 3,
    val handSize: Int = 8,
    val money: Int = 4,
    val hand: List<Card> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val jokers: List<JokerInst> = emptyList(),
    val maxJokers: Int = 5,
    val consumables: List<Consumable> = emptyList(),
    val maxConsumables: Int = 2,
    val preview: ScoreBreakdown? = null,
    val lastResult: ScoreBreakdown? = null,
    val shop: ShopState = ShopState(),
    val pack: PackView? = null,
    val rerollCost: Int = 5,
    val deckLeft: Int = 0,
    val levels: Map<HandType, Int> = emptyMap(),
    val vouchers: List<Voucher> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val playedTypes: Set<HandType> = emptySet(),
    val message: String = "",
    val blindOptions: List<BlindOption> = emptyList(),
    val cashOut: CashOutDetail? = null,
    val handsPlayed: Int = 0,
    val bestHand: Long = 0,
    val blindsCleared: Int = 0,
    val lastGain: Int = 0,
    val endless: Boolean = false,
    val hasSave: Boolean = false,
    /** 当前赌注。 */
    val stake: Stake = Stake.WHITE,
    /** 当前挑战（普通一局为 null）。 */
    val challenge: Challenge? = null,
    /** 本关被封锁（不计分）的牌 id，UI 画成灰底。 */
    val blockedIds: Set<Int> = emptySet(),
    /**
     * 上一次打出的牌。
     *
     * 原版的「出牌区」是打击感的舞台：牌打出去停在桌面中央，逐张弹跳、
     * 飘出 +筹码，而不是直接消失回牌堆。出完牌手牌已经补满，所以这批牌
     * 必须单独留一份给 UI 画。
     */
    val playedCards: List<Card> = emptyList(),
    /**
     * 抽牌堆（还没摸到的牌），按实际抽取顺序排列。
     *
     * 暴露给 UI 是为了「牌堆查看器」——原版点牌堆能看剩余所有牌，
     * 这是判断"还剩几张红桃"的唯一手段。之前只有 [deckLeft] 这个数量，
     * 中后期的同花/顺子听牌判断只能靠记忆。
     */
    val drawPileCards: List<Card> = emptyList(),
    /** 弃牌堆（已弃 / 已打出且未碎裂的牌）。 */
    val discardCards: List<Card> = emptyList(),
) {
    val progress: Float
        get() = if (target <= 0) 1f else (score.toFloat() / target).coerceIn(0f, 1f)

    val isBoss: Boolean get() = blindKind == BlindKind.BOSS

    val canPlay: Boolean get() = phase == Phase.PLAYING && handsLeft > 0 && selected.isNotEmpty()

    val canDiscard: Boolean
        get() = phase == Phase.PLAYING && discardsLeft > 0 && selected.isNotEmpty()

    val canSkip: Boolean
        get() = phase == Phase.BLIND_SELECT && blindIndex < 2
}

/**
 * 一局的完整可持久化状态。
 */
data class RunState(
    val seed: Long,
    val deckType: DeckType,
    val phase: Phase,
    val ante: Int,
    val blindIndex: Int,
    val score: Long,
    val money: Int,
    val handsLeft: Int,
    val discardsLeft: Int,
    val handSize: Int,
    val master: List<Card>,
    val drawPile: List<Int>,
    val discardPile: List<Int>,
    val hand: List<Int>,
    val jokers: List<JokerInst>,
    val consumables: List<String>,
    val levels: Map<HandType, Int>,
    val vouchers: List<String>,
    val bossSeq: List<BossModifier>,
    val pendingTags: List<Tag>,
    val playedTypes: List<HandType>,
    val destroyed: List<Int>,
    val shop: ShopState,
    val rerollCost: Int,
    val cashOut: CashOutDetail?,
    val handsPlayed: Int,
    val bestHand: Long,
    val blindsCleared: Int,
    val lastGain: Int,
    val endless: Boolean,
    val message: String,
    // ---- 2026-11-16 新增 ----
    val rerolls: Int = 0,
    val tarotsUsed: Int = 0,
    val blindsSkipped: Int = 0,
    val blindBlocked: List<Int> = emptyList(),
    val pillarHistory: List<Int> = emptyList(),
    val typeCount: Map<String, Int> = emptyMap(),
    val verdantCleared: Boolean = false,
    val pack: BoosterPack? = null,
    val packPicked: List<Int> = emptyList(),
    val packReturn: Phase = Phase.SHOP,
    // ---- 2026-11-15 新增（传奇成长计数）----
    val destroyedFaces: Int = 0,
    val discardsTotal: Int = 0,
    // ---- 幻灵 / 蜡封 ----
    val handSizePenalty: Int = 0,
    /** 赌注（八档难度）。 */
    val stake: Stake = Stake.WHITE,
    /** 挑战 id；普通一局为空串。 */
    val challenge: String = "",
)

/**
 * 游戏引擎：一局完整的 Roguelike 单局。
 *
 * 设计上刻意保持"纯逻辑"——不引用任何 Android / Compose 类型，
 * 所以整局可以在 JVM 单元测试里跑完。
 *
 * 所有牌都只以 id 存在三个堆里，卡牌实体统一放 [master]；
 * 塔罗改造只改 [master]，三个堆自动跟着变 —— 避免"手牌改了、弃牌堆没改"这类不一致。
 */
/**
 * 手牌排序方式。
 *
 * 原版牌桌右下有两个按钮（按点数 / 按花色），这里做成单键循环切换 ——
 * 1.4 英寸屏上多一个按钮就少一行可用宽度，而排序只有两种意图。
 */
enum class SortMode(val cn: String) {
    RANK("点数"),
    SUIT("花色"),
    ;

    fun next(): SortMode = if (this == RANK) SUIT else RANK
}

class GameEngine(seed: Long = System.currentTimeMillis()) {

    // ---------------------------------------------------------------- 基础

    private var seed = seed
    private var random = kotlin.random.Random(seed)

    var deckType = DeckType.RED
        private set

    /** 当前赌注（八档难度）。 */
    var stake = Stake.WHITE
        private set

    /** 当前挑战；普通一局为 null。 */
    var challenge: Challenge? = null
        private set

    private val master = LinkedHashMap<Int, Card>()
    private var drawPile = mutableListOf<Int>()
    private var discardPile = mutableListOf<Int>()
    private var hand = mutableListOf<Int>()
    private val selected = linkedSetOf<Int>()

    private var ante = 1
    private var blindIndex = 0
    private var phase = Phase.MENU

    private var score = 0L
    private var money = 4
    private var handsLeft = Blinds.BASE_HANDS
    private var discardsLeft = Blinds.BASE_DISCARDS
    private var handSize = Blinds.BASE_HAND_SIZE

    private val jokers = mutableListOf<JokerInst>()
    private val consumables = mutableListOf<Consumable>()
    private val levels = mutableMapOf<HandType, Int>()
    private val vouchers = linkedSetOf<String>()
    private var bossSeq: List<BossModifier> = emptyList()
    private val pendingTags = mutableListOf<Tag>()
    private val playedTypes = linkedSetOf<HandType>()
    private val typeCount = mutableMapOf<HandType, Int>()

    private var shop = ShopState()
    private var rerollCost = 5
    private var cashOut: CashOutDetail? = null

    private var message = ""
    private var lastResult: ScoreBreakdown? = null

    /** 上一次打出的牌，供 UI 画出牌区。 */
    private var lastPlayed: List<Card> = emptyList()

    private var handsPlayed = 0
    private var bestHand = 0L
    private var blindsCleared = 0
    private var lastGain = 0
    private var endless = false

    // ---- 商店 / 卡包的累计计数，供小丑读取 ----
    private var rerolls = 0
    private var tarotsUsed = 0
    private var blindsSkipped = 0

    // ---- 传奇小丑的成长计数 ----
    /** 被塔罗摧毁的人头牌张数（卡尼奥）。 */
    private var destroyedFaces = 0
    /** 累计弃掉的牌张数（约里克，每 23 张 ×1）。 */
    private var discardsTotal = 0
    /** 累计使用的行星牌（星座）。 */
    private var planetsUsed = 0
    /** 累计买入 / 卖出的小丑（全息图 / 篝火）。 */
    private var jokersBought = 0
    private var jokersSold = 0
    /** 累计碎掉的玻璃牌（玻璃小丑）。 */
    private var glassShattered = 0
    /** 「骨头先生」这一局是否已经用掉免死。 */
    private var bonesSaved = false

    // ---- 幻灵 / 蜡封带来的持久状态 ----
    /** 幻灵「通灵板 / 外质」造成的手牌上限永久修正（负值），跨盲注保留。 */
    private var handSizePenalty = 0
    /** 本盲注最后打出的牌型；蓝蜡封在结算时据此生成对应行星牌。 */
    private var lastHandType: HandType? = null

    // ---- 补充包 ----
    private var openPack: BoosterPack? = null
    private val packPicked = linkedSetOf<Int>()
    private var packReturn = Phase.SHOP

    // ---- Boss 状态 ----
    /** 本关因"牌面封锁"型 Boss 而不计分的牌。 */
    private val blindBlocked = linkedSetOf<Int>()

    /** 「柱」：本关已经打出去的牌，之后再出现就不计分。 */
    private val pillarHistory = linkedSetOf<Int>()

    /** 「翠叶」：卖掉一个小丑之前所有牌都不计分。 */
    private var verdantCleared = false

    /** 「绯红之心」：这一手被禁用的小丑下标。 */
    private var disabledJoker = -1

    /** 标准包等生成的新牌 id 从这里往上发，避开 0..51。 */
    private var newCardSeq = 100

    var hasSave = false

    /** 测试 / 调试用：直接塞一张小丑，不经过商店。 */
    internal fun debugAddJoker(j: JokerInst) {
        jokers += j
    }

    /** 出牌后挂起的结局，等 UI 播完结算动画再落地。 */
    private enum class Outcome { CLEAR, FAIL }

    private var pendingOutcome: Outcome? = null

    /** 当前盲注参数。声明在 init 之前，否则 init 里的 resetRun 会读到未初始化字段。 */
    private var spec: BlindSpec = Blinds.spec(1, 0)

    init {
        resetRun(DeckType.RED)
    }

    // ---------------------------------------------------------------- 派生数值

    private fun handSizeBonus(): Int =
        deckType.extraHandSize + (challenge?.handSizeDelta ?: 0) +
            (if (Voucher.PAINTER.key in vouchers) 1 else 0) +
            (if (Voucher.PAINT_BRUSH.key in vouchers) 1 else 0) +
            (if (Voucher.PALETTE.key in vouchers) 1 else 0) + handSizePenalty +
            jokerHandSizeDelta()

    /**
     * 小丑带来的手牌 / 出牌槽位修正。
     *
     * 上一版这些牌只有一行 desc，引擎从未读取 —— 玩家买到「乌龟豆」
     * 发现手牌没变多。这里统一在一处算，[specOf] 与开局发牌共用。
     */
    private fun jokerHandSizeDelta(): Int {
        var d = 0
        if (jokers.any { it.id == "turtle" }) d += 5
        if (jokers.any { it.id == "juggler" }) d += 1
        if (jokers.any { it.id == "troubadour" }) d += 2
        if (jokers.any { it.id == "stuntman" }) d -= 2
        if (jokers.any { it.id == "merry" }) d -= 1
        return d
    }

    private fun jokerHandsDelta(): Int {
        var d = 0
        if (jokers.any { it.id == "burglar" }) d += 3
        if (jokers.any { it.id == "troubadour" }) d -= 1
        return d
    }

    private fun jokerDiscardsDelta(): Int {
        var d = 0
        if (jokers.any { it.id == "drunkard" }) d += 1
        if (jokers.any { it.id == "merry" }) d += 3
        return d
    }

    private val maxJokers: Int
        get() = (5 + deckType.extraJokerSlots +
            (challenge?.jokerSlotsDelta ?: 0) +
            (if (Voucher.JOKER_BOX.key in vouchers) 1 else 0) +
            (if (Voucher.ANTIMATTER.key in vouchers) 1 else 0) +
            jokers.count { it.edition == Edition.NEGATIVE }).coerceAtLeast(0)

    private val maxConsumables: Int
        get() = 2 + (if (Voucher.CRYSTAL_BALL.key in vouchers) 1 else 0)

    /** 商品位：基础 2，多余库存各 +1。 */
    private val shopSlots: Int
        get() = 2 +
            (if (Voucher.OVERSTOCK.key in vouchers) 1 else 0) +
            (if (Voucher.OVERSTOCK_PLUS.key in vouchers) 1 else 0)

    private val interestCap: Int
        get() = when {
            deckType == DeckType.GREEN -> 99
            else -> Blinds.INTEREST_CAP +
                (if (Voucher.SEED_MONEY.key in vouchers) 5 else 0) +
                (if (Voucher.MONEY_TREE.key in vouchers) 5 else 0)
        }

    private val rerollBase: Int
        get() = if (Voucher.RETCON.key in vouchers) 2
        else (stake.rerollBase -
            (if (Voucher.REROLL.key in vouchers) 2 else 0) -
            (if (Voucher.REROLL_GLUT.key in vouchers) 2 else 0)).coerceAtLeast(0)

    /** 全场折扣：清盘 5 折优先于清仓 75 折。 */
    private val discount: Double
        get() = when {
            Voucher.LIQUIDATION.key in vouchers -> 0.5
            Voucher.CLEARANCE.key in vouchers -> 0.75
            else -> 1.0
        }

    private fun bossOf(anteLevel: Int): BossModifier {
        // 挑战的常驻 Boss（美杜莎 / 五张牌 / 定型）优先级最高
        challenge?.let { if (it.forcedBoss != BossModifier.NONE) return it.forcedBoss }
        return if (anteLevel <= bossSeq.size) bossSeq[anteLevel - 1]
        else Blinds.endlessBoss(anteLevel)
    }

    private fun specOf(anteLevel: Int, index: Int): BlindSpec = Blinds.spec(
        ante = anteLevel,
        index = index,
        boss = bossOf(anteLevel),
        extraHands = deckType.extraHands + (challenge?.handsDelta ?: 0) +
            (if (Voucher.GRABBER.key in vouchers) 1 else 0) +
            (if (Voucher.HIEROGLYPH.key in vouchers) 1 else 0) + jokerHandsDelta(),
        extraDiscards = deckType.extraDiscards + (challenge?.discardsDelta ?: 0) +
            stake.discardsDelta +
            (if (Voucher.NACHO.key in vouchers) 1 else 0) +
            (if (Voucher.WASTEFUL.key in vouchers) 1 else 0) + jokerDiscardsDelta(),
        handSizeBonus = handSizeBonus(),
        // 赌注与挑战的目标倍率在此相乘：紫注 × 双倍挑战 = 双重加码
        targetScale = stake.targetScale(anteLevel) * (challenge?.targetScale ?: 1.0),
    )

    /** 本关所有"不计分"的牌 id，UI 会把它们画灰。 */
    private fun effectiveBlocked(): Set<Int> {
        val out = HashSet<Int>(blindBlocked.size + pillarHistory.size + 4)
        out += blindBlocked
        out += pillarHistory
        if (spec.modifier == BossModifier.VERDANT && !verdantCleared) {
            // 翠叶没解除：整副牌都不计分
            out += master.keys
        }
        return out
    }

    // ---------------------------------------------------------------- 流程控制

    fun toDeckSelect() {
        phase = Phase.DECK_SELECT
        message = "选一个牌组开局"
    }

    fun toChallengeSelect() {
        phase = Phase.CHALLENGE_SELECT
        message = "20 个挑战，每个只改一条规则"
    }

    /** 在牌组页切换赌注（未开局时生效）。 */
    fun setStake(s: Stake) {
        if (phase == Phase.DECK_SELECT || phase == Phase.MENU || phase == Phase.CHALLENGE_SELECT) {
            stake = s
        }
    }

    fun startRun(type: DeckType, stakeLevel: Stake = stake) {
        challenge = null
        stake = stakeLevel
        resetRun(type)
        phase = Phase.BLIND_SELECT
        message = "${stakeLevel.cn} · 第 1 关，选一个盲注开打"
    }

    /** 开始一个挑战：固定种子、固定牌组、固定赌注。 */
    fun startChallenge(c: Challenge) {
        challenge = c
        stake = c.stake
        resetRun(c.deck, seedOverride = c.seed)
        phase = Phase.BLIND_SELECT
        message = "${c.cn} · 第 1 关"
    }

    fun selectBlind() {
        if (phase != Phase.BLIND_SELECT) return
        startBlind()
    }

    /**
     * 跳过当前盲注，换一张标签。
     */
    fun skipBlind(): Tag? {
        if (phase != Phase.BLIND_SELECT || blindIndex >= 2) return null
        val tag = Tag.roll(random)
        blindsSkipped++
        advanceBlind()
        val after = if (phase == Phase.WIN) Phase.WIN else Phase.BLIND_SELECT
        phase = after
        packReturn = after
        applyTag(tag)
        if (phase != Phase.PACK_OPEN) message = "跳过，拿到${tag.cn}"
        return tag
    }

    private fun applyTag(tag: Tag) {
        when (tag) {
            Tag.CHARM -> {
                val def = Jokers.rollOne(random, jokers.map { it.id }.toSet())
                if (def != null && jokers.size < maxJokers) {
                    jokers += JokerInst(def, Jokers.randomEdition(random, editionBoost))
                } else money += 4
            }
            Tag.METEOR -> {
                if (consumables.size < maxConsumables) {
                    consumables += Consumables.rollPlanet(random)
                } else money += 3
            }
            Tag.BUFFOON -> openFreePack(PackKind.BUFFOON)
            Tag.ETHEREAL -> openFreePack(PackKind.ARCANA)
            Tag.STANDARD -> openFreePack(PackKind.STANDARD)
            Tag.INVESTMENT -> money += 25
            Tag.VOUCHER -> money += 10
            Tag.TOPUP -> money += 6
            Tag.RARE -> money += 12
            Tag.ORBITAL -> {
                // 随机 3 种牌型各升 1 级
                val pool = HandType.entries.shuffled(random).take(3)
                pool.forEach { levels[it] = (levels[it] ?: 1) + 1 }
                message = "轨道：${pool.joinToString("、") { it.cn }} 升级"
            }
            Tag.HOARD -> {
                if (consumables.isNotEmpty() && consumables.size < maxConsumables) {
                    consumables += consumables[random.nextInt(consumables.size)]
                } else if (consumables.size < maxConsumables) {
                    consumables += Consumables.rollTarot(random)
                } else money += 3
            }
            Tag.NEGATIVE -> {
                val def = Jokers.rollOne(random, jokers.map { it.id }.toSet())
                if (def != null) jokers += JokerInst(def, Edition.NEGATIVE) else money += 5
            }
            Tag.FOIL, Tag.HOLO, Tag.POLY -> {
                val ed = when (tag) {
                    Tag.FOIL -> Edition.FOIL
                    Tag.HOLO -> Edition.HOLO
                    else -> Edition.POLY
                }
                if (jokers.isNotEmpty()) {
                    val i = random.nextInt(jokers.size)
                    jokers[i] = jokers[i].copy(edition = ed)
                } else money += 4
            }
            else -> pendingTags += tag
        }
    }

    /** 结算页 → 收钱 → 商店。 */
    fun confirmCashOut() {
        val c = cashOut ?: return
        money += c.total
        lastGain = c.total
        cashOut = null
        phase = Phase.SHOP
        rerolls = 0
        rollShop()
        rerollCost = rerollBase
        message = "商店开张"
    }

    /** 商店 → 下一个盲注的选择页。 */
    fun leaveShop() {
        if (phase != Phase.SHOP) return
        // 「优惠券」「D6」只在紧随其后的那一个商店生效
        pendingTags.removeAll { it == Tag.COUPON || it == Tag.D6 }
        // 「佩尔克」：离开商店时复制一张消耗品（原版是回合结束时随机复制一张）
        if (jokers.any { it.id == "perkeo" } && consumables.isNotEmpty()) {
            val src = consumables[random.nextInt(consumables.size)]
            if (consumables.size < maxConsumables) consumables += src
            else money += 3
            message = "佩尔克复制了 ${src.name}"
        }
        advanceBlind()
        if (phase == Phase.WIN) {
            message = "八关全部击破"
            return
        }
        phase = Phase.BLIND_SELECT
        message = "第 $ante 关，选一个盲注开打"
    }

    fun toMenu() {
        phase = Phase.MENU
        message = ""
    }

    fun restart() {
        resetRun(deckType)
        phase = Phase.BLIND_SELECT
        message = "第 1 关，选一个盲注开打"
    }

    fun continueEndless() {
        endless = true
        phase = Phase.BLIND_SELECT
        message = "无尽模式 · 第 $ante 关"
    }

    // ---------------------------------------------------------------- 牌桌操作

    fun toggle(cardId: Int) {
        if (phase != Phase.PLAYING) return
        if (cardId !in hand) return
        if (selected.contains(cardId)) selected.remove(cardId)
        else if (selected.size < 5) selected.add(cardId)
    }

    fun clearSelection() {
        selected.clear()
    }

    fun sortHand() {
        hand.sortWith(
            compareByDescending<Int> { master[it]?.rank?.order ?: 0 }
                .thenBy { master[it]?.suit?.ordinal ?: 0 }
        )
    }

    /**
     * 手牌重排：把第 [from] 张拖到第 [to] 位。
     *
     * 原版可以任意拖拽手牌 —— 这不是装饰，因为小丑牌的效果按**摆位**结算，
     * 玩家常把某张牌拖到手牌最左以配合"取最左"的小丑。上一版只能整体排序，
     * 等于把这条策略剪掉了。
     */
    fun moveCard(from: Int, to: Int) {
        if (phase != Phase.PLAYING) return
        if (from !in hand.indices || to !in hand.indices || from == to) return
        val id = hand.removeAt(from)
        hand.add(to.coerceIn(0, hand.size), id)
    }

    /**
     * 一键排序。
     *
     * 原版牌桌右下有两个排序按钮（按点数 / 按花色）。上一版只有单张拖拽，
     * 等于每手开局都要手动理 8 张牌 —— 手表上拖牌是体力活，而"理牌"
     * 从来不是这个游戏的乐趣。
     *
     * 注意：排序**不影响选中集合**（选中按牌 id 记，不按下标），
     * 也不会改变任何牌的 id 或点数——所以计分结果完全不变。
     */
    fun sortHand(mode: SortMode) {
        if (phase != Phase.PLAYING) return
        val sorted = hand.sortedWith(
            when (mode) {
                SortMode.RANK -> compareBy(
                    { master[it]?.rank?.order ?: 0 },
                    { master[it]?.suit?.ordinal ?: 0 },
                )
                SortMode.SUIT -> compareBy(
                    { master[it]?.suit?.ordinal ?: 0 },
                    { master[it]?.rank?.order ?: 0 },
                )
            }
        )
        hand.clear()
        hand.addAll(sorted)
        message = "按${mode.cn}排序"
    }

    /**
     * 出牌。
     *
     * @return 本次计分明细；未满足出牌条件时返回 null 且不改变状态。
     */
    fun play(): ScoreBreakdown? {
        if (phase != Phase.PLAYING || handsLeft <= 0) return null
        val played = hand.filter { it in selected }.mapNotNull { master[it] }
        if (played.isEmpty()) return null

        // Boss 限制：通灵必须正好 5 张
        if (spec.modifier == BossModifier.PSYCHIC && played.size != 5) {
            message = "通灵：必须出满 5 张"
            return null
        }

        val remaining = hand.filter { it !in selected }.mapNotNull { master[it] }
        val blockSet = effectiveBlocked()
        val result = Scoring.compute(
            played = played,
            hand = remaining,
            jokers = jokers.toList(),
            levels = levels.toMap(),
            discardsLeft = discardsLeft,
            handsLeft = handsLeft,
            money = money,
            ante = ante,
            playedTypes = playedTypes.toSet(),
            blockedIds = blockSet,
            flint = spec.modifier == BossModifier.FLINT,
            amber = spec.modifier == BossModifier.AMBER,
            disabledJoker = disabledJoker,
            deckLeft = drawPile.size,
            rerolls = rerolls,
            tarotsUsed = tarotsUsed,
            blindsSkipped = blindsSkipped,
            handsPlayed = handsPlayed,
            jokerSlots = maxJokers,
            steelInDeck = master.values.count { it.enh == Enhancement.STEEL },
            stoneInDeck = master.values.count { it.enh == Enhancement.STONE },
            destroyedFaces = destroyedFaces,
            discardsTotal = discardsTotal,
            planetsUsed = planetsUsed,
            jokersBought = jokersBought,
            jokersSold = jokersSold,
            glassShattered = glassShattered,
            deckSize = master.size,
            handSize = handSize,
            bossBlind = spec.kind == BlindKind.BOSS,
            fourFingers = jokers.any { it.id == "fourfingers" },
            shortcut = jokers.any { it.id == "shortcut" },
            smeared = jokers.any { it.id == "smeared" },
            splash = jokers.any { it.id == "splash" },
            random = random,
        )

        if (spec.modifier == BossModifier.EYE && result.handType in playedTypes) {
            message = "眼：${result.handType.cn} 本关已出过"
            return null
        }
        if (spec.modifier == BossModifier.MOUTH && playedTypes.isNotEmpty() &&
            result.handType !in playedTypes
        ) {
            message = "嘴：本关只能出${playedTypes.first().cn}"
            return null
        }

        // 「牛」：打出本局最常出的牌型，金钱归零 —— 必须在加分之前判断
        val mostType = typeCount.maxByOrNull { it.value }?.key
        val oxTrip = spec.modifier == BossModifier.OX &&
            mostType != null && result.handType == mostType && (typeCount[mostType] ?: 0) >= 2

        score += result.total
        money += result.money
        handsLeft--
        handsPlayed++
        playedTypes += result.handType
        lastHandType = result.handType
        typeCount[result.handType] = (typeCount[result.handType] ?: 0) + 1
        if (result.total > bestHand) bestHand = result.total
        lastResult = result
        lastPlayed = played

        // ---- Boss 副作用 ----
        if (oxTrip) {
            money = 0
            message = "牛：${result.handType.cn} 是最常牌型，金钱归零"
        }
        if (spec.modifier == BossModifier.TOOTH) {
            money -= played.size
        }
        if (spec.modifier == BossModifier.ARM) {
            val cur = levels[result.handType] ?: 1
            levels[result.handType] = (cur - 1).coerceAtLeast(1)
        }
        if (spec.modifier == BossModifier.PILLAR) {
            pillarHistory += played.map { it.id }
        }
        if (spec.modifier == BossModifier.FISH) {
            val candidates = hand.filter { it !in blindBlocked }
            if (candidates.isNotEmpty()) {
                blindBlocked += candidates[random.nextInt(candidates.size)]
            }
        }
        if (spec.modifier == BossModifier.CRIMSON && jokers.isNotEmpty()) {
            disabledJoker = random.nextInt(jokers.size)
        }

        // 「降神」：打出同花顺给 1 张幻灵
        if (jokers.any { it.id == "seance" } &&
            (result.handType == HandType.STRAIGHT_FLUSH || result.handType == HandType.FLUSH_FIVE)
        ) {
            if (consumables.size < maxConsumables) consumables += Consumables.rollSpectral(random)
            else money += 3
        }
        // 「DNA」：只打 1 张时，把它复制一张进手牌
        if (jokers.any { it.id == "dnajoker" } && played.size == 1) {
            val src = played[0]
            val id = nextCardId()
            master[id] = src.copy(id = id)
            hand.add(id)
        }

        // 玻璃碎裂：从牌堆彻底移除
        if (result.shattered.isNotEmpty()) {
            glassShattered += result.shattered.size
            result.shattered.forEach { master.remove(it) }
        }
        val toDiscard = played.filter { it.id !in result.shattered }.map { it.id }
        discardPile.addAll(toDiscard)
        hand.removeAll { it in selected }
        selected.clear()
        refill()

        // Boss「钩」：出牌后随机弃 2 张
        if (spec.modifier == BossModifier.HOOK && hand.size > 2) {
            repeat(2) {
                val i = random.nextInt(hand.size)
                discardPile += hand.removeAt(i)
            }
            refill()
        }

        if (message.isEmpty() || !oxTrip) {
            message = buildString {
                append(result.handType.cn).append(' ').append(result.total)
                if (result.money > 0) append(" +\$").append(result.money)
            }
        }

        if (score >= spec.target) pendingOutcome = Outcome.CLEAR
        else if (handsLeft <= 0) {
            // 「骨头先生」：本局第一次用尽出牌时救一次（原版是给到下一关）
            if (!bonesSaved && jokers.any { it.id == "mrBones" }) {
                bonesSaved = true
                handsLeft = 1
                message = "骨头先生：续 1 次出牌"
            } else {
                pendingOutcome = Outcome.FAIL
            }
        }
        return result
    }

    /**
     * 结算这一手的结局。
     *
     * 出牌后**不立刻**切阶段：UI 还要把逐张结算的动画播完，
     * 一达标就跳走等于让玩家错过"这一手是怎么赢的"。
     */
    fun resolvePending() {
        val o = pendingOutcome ?: return
        pendingOutcome = null
        when (o) {
            Outcome.CLEAR -> clearBlind()
            Outcome.FAIL -> {
                phase = Phase.GAME_OVER
                message = "差 ${spec.target - score} 分，挑战失败"
            }
        }
    }

    /** 弃掉选中的牌并补满。 */
    fun discard(): Boolean {
        if (phase != Phase.PLAYING || discardsLeft <= 0 || selected.isEmpty()) return false
        val gone = hand.filter { it in selected }
        discardPile.addAll(gone)
        hand.removeAll { it in selected }
        selected.clear()
        discardsLeft--
        discardsTotal += gone.size
        // 紫蜡封：被弃掉的牌各生成一张随机塔罗（原版 Purple Seal）
        val purple = gone.count { id -> master[id]?.seal == Seal.PURPLE }
        repeat(purple) {
            if (consumables.size < maxConsumables) consumables += Consumables.rollTarot(random)
            else money += 3
        }
        // 「烧焦」：弃牌时升级当前等级最低的牌型（原版 Burnt Joker）
        if (jokers.any { it.id == "burnt" }) {
            val lowest = HandType.entries.minByOrNull { levels[it] ?: 1 }
            if (lowest != null) levels[lowest] = (levels[lowest] ?: 1) + 1
        }
        refill()
        message = "弃掉 ${gone.size} 张，剩 $discardsLeft 次"
        return true
    }

    // ---------------------------------------------------------------- 消耗品

    fun useConsumable(index: Int): Boolean {
        val c = consumables.getOrNull(index) ?: return false
        if (c.needsCards) {
            if (phase != Phase.PLAYING) {
                message = "要先在手牌上选牌"
                return false
            }
            if (selected.size < c.minSel) {
                message = "至少选 ${c.minSel} 张"
                return false
            }
            if (selected.size > c.maxSel) {
                message = "最多选 ${c.maxSel} 张"
                return false
            }
        }
        val picked = if (c.needsCards) {
            hand.filter { it in selected }.mapNotNull { master[it] }
        } else {
            emptyList()
        }

        val jokerValue = jokers.sumOf { it.sellPrice }
        val res = c.use(ConsumableCtx(picked, money, jokerValue, random))

        if (res.destroyed) {
            // 卡尼奥按"被摧毁的人头牌"成长 —— 原版是每毁一张 +1 倍率
            destroyedFaces += picked.count { it.rank.order >= 11 }
            picked.forEach {
                master.remove(it.id)
                hand.remove(it.id)
                drawPile.remove(it.id)
                discardPile.remove(it.id)
            }
            selected.clear()
        } else if (res.cards.isNotEmpty()) {
            picked.forEachIndexed { i, old ->
                val new = res.cards.getOrNull(i) ?: return@forEachIndexed
                master[old.id] = new
            }
        }
        if (res.moneyDelta != 0) money += res.moneyDelta
        res.levelUp?.let {
            levels[it] = (levels[it] ?: 1) + if (Voucher.OBSERVATORY.key in vouchers) 2 else 1
        }
        if (res.spawnJokers > 0) {
            repeat(res.spawnJokers) {
                val def = Jokers.rollOne(random, jokers.map { it.id }.toSet()) ?: return@repeat
                if (jokers.size < maxJokers) jokers += JokerInst(def, Jokers.randomEdition(random, editionBoost))
                else money += 4
            }
        }
        if (res.spawnLegendary) {
            val def = Jokers.rollLegendary(random, jokers.map { it.id }.toSet())
            if (def == null) {
                money += 20
            } else if (jokers.size < maxJokers) {
                jokers += JokerInst(def, Jokers.randomEdition(random, editionBoost))
            } else {
                // 槽位满了：传奇折价 $20，比普通小丑的 $4 高得多
                money += 20
            }
        }
        res.spawnConsumables.forEach { c2 ->
            if (consumables.size < maxConsumables) consumables += c2 else money += 3
        }
        if (res.upgradeRandomJoker && jokers.isNotEmpty()) {
            val i = random.nextInt(jokers.size)
            val old = jokers[i]
            if (old.edition.isBase) jokers[i] = old.copy(edition = Jokers.randomEdition(random, editionBoost))
        }

        // ---- 幻灵（Spectral）落账：18 张牌的复杂效果统一在这里落地 ----
        if (res.allLevelsUp) {
            HandType.entries.forEach { levels[it] = (levels[it] ?: 1) + 1 }
        }
        if (res.handSizeDelta != 0) {
            handSizePenalty += res.handSizeDelta
            handSize = (handSize + res.handSizeDelta).coerceAtLeast(1)
            // 上限变小：多出来的手牌退回抽牌堆
            while (hand.size > handSize) {
                val id = hand.removeAt(hand.size - 1)
                drawPile.add(id)
                selected.remove(id)
            }
        }
        if (res.destroyRandomHand > 0) {
            val victims = hand.shuffled(random).take(res.destroyRandomHand)
            victims.forEach { id ->
                val c = master[id]
                if (c != null && c.rank.order >= 11) destroyedFaces++
                master.remove(id)
                hand.remove(id)
                drawPile.remove(id)
                discardPile.remove(id)
                selected.remove(id)
            }
        }
        if (res.spawnCards.isNotEmpty()) {
            res.spawnCards.forEach { tpl ->
                val id = nextCardId()
                master[id] = tpl.copy(id = id)
                drawPile.add(id)
            }
            drawPile.shuffle(random)
        }
        if (res.convertHandSuit) {
            val s = Suit.entries[random.nextInt(Suit.entries.size)]
            hand.forEach { id -> master[id]?.let { master[id] = it.copy(suit = s) } }
        }
        if (res.convertHandRank) {
            val r = Rank.entries[random.nextInt(Rank.entries.size)]
            hand.forEach { id -> master[id]?.let { master[id] = it.copy(rank = r) } }
        }
        if (res.spawnRareJoker) {
            val def = Jokers.rollRare(random, jokers.map { it.id }.toSet())
            if (def != null && jokers.size < maxJokers) {
                jokers += JokerInst(def, Jokers.randomEdition(random, editionBoost))
            } else money += 4
        }
        res.editionRandomJoker?.let { ed ->
            if (jokers.isNotEmpty()) {
                val i = random.nextInt(jokers.size)
                jokers[i] = jokers[i].copy(edition = ed)
            }
        }
        if (res.jokerApocalypse > 0 && jokers.isNotEmpty()) {
            val keepIdx = random.nextInt(jokers.size)
            var kept = jokers[keepIdx]
            if (res.jokerApocalypse >= 2) kept = kept.copy(edition = Edition.POLY)
            jokers.clear()
            jokers += kept
            // 生命之符（1）额外复制保留的那一张；咒术（2）只留一张
            if (res.jokerApocalypse == 1 && jokers.size < maxJokers) jokers += kept.copy()
        }
        // 怨灵的"金钱归零"放最后，确保覆盖上面的折价
        if (res.moneyToZero) money = 0

        consumables.removeAt(index)
        // 塔罗计数：算命师 / 红牌这类小丑靠它成长
        if (c.kind == ConsumableKind.TAROT) tarotsUsed++
        if (c.kind == ConsumableKind.PLANET) planetsUsed++
        if (c.needsCards) selected.clear()
        // 手牌上限的变化必须在提示里说出来 —— 否则玩家只看到"某张牌用掉了"，
        // 不知道自己的手牌上限已经变了（这是手表上最容易漏掉的一类反馈）。
        val sizeNote = if (res.handSizeDelta != 0) " · 手牌 $handSize" else ""
        message = "${c.name}：${res.message.ifEmpty { "已生效" }}$sizeNote"
        if (phase == Phase.PLAYING) sortHand()
        return true
    }

    fun discardConsumable(index: Int): Boolean {
        if (consumables.size <= index) return false
        val c = consumables.removeAt(index)
        message = "丢弃 ${c.name}"
        return true
    }

    // ---------------------------------------------------------------- 商店

    /**
     * 生成商店内容，结构照原版：
     * 2 个商品（小丑 : 塔罗 : 行星 = 20 : 4 : 4）、2 个补充包、1 张代金券。
     */
    private fun rollShop() {
        // 「主持人」：商店允许出现已拥有的小丑（原版 Showman）
        val ownedJokers = if (jokers.any { it.id == "showman" }) emptySet()
        else jokers.map { it.id }.toSet()
        val cards = mutableListOf<ShopItem>()

        repeat(shopSlots) {
            // 权重抽类型：20 : 4 : 4（带塔罗商/行星商则翻倍）
            val tarotW = 4 * (if (Voucher.TAROT_MERCHANT.key in vouchers) 2 else 1) *
                (if (Voucher.TAROT_TYCOON.key in vouchers) 2 else 1)
            val planetW = 4 * (if (Voucher.PLANET_MERCHANT.key in vouchers) 2 else 1) *
                (if (Voucher.PLANET_TYCOON.key in vouchers) 2 else 1)
            val r = random.nextInt(20 + tarotW + planetW)
            when {
                r < 20 -> {
                    val def = Jokers.rollOne(random, ownedJokers)
                    if (def != null) {
                        val ed = Jokers.randomEdition(random, editionBoost)
                        val inst = JokerInst(def, ed)
                        cards += ShopItem.JokerItem(inst, jokerPrice(def, ed))
                    } else {
                        val c = Consumables.rollOne(random)
                        cards += ShopItem.ConsumableItem(c, priceOf(c.price))
                    }
                }
                r < 20 + tarotW -> {
                    val c = Consumables.rollTarot(random)
                    cards += ShopItem.ConsumableItem(c, priceOf(c.price))
                }
                else -> {
                    val c = Consumables.rollPlanet(random)
                    cards += ShopItem.ConsumableItem(c, planetPrice(c.price))
                }
            }
        }

        // 「魔术」：商店第一格固定是塔罗
        if (Voucher.MAGIC_TRICK.key in vouchers && cards.isNotEmpty()) {
            val t = Consumables.rollTarot(random)
            cards[0] = ShopItem.ConsumableItem(t, priceOf(t.price))
        }

        // 补充包：固定 2 个位，掷出种类与规格（橙注 / 挑战加价）
        val packExtra = stake.packPriceDelta + (challenge?.priceDelta ?: 0)
        val boosters = Boosters.roll(random, 2).map { (k, s) ->
            ShopItem.BoosterItem(k, s, priceOf(s.price + packExtra))
        }

        // 代金券：两个位，只在击败 Boss 之后换新（与原版一致）
        val voucherItems = Voucher.roll(random, vouchers, 2).map {
            ShopItem.VoucherItem(it, priceOf(it.price))
        }

        shop = ShopState(cards = cards, boosters = boosters, vouchers = voucherItems)
    }

    /** 小丑定价：稀有度区间中位 + 版本加价，再打折。 */
    private fun jokerPrice(def: JokerDef, ed: Edition): Int {
        val raw = def.price + ed.buyExtra + (challenge?.priceDelta ?: 0)
        return (raw * discount).toInt().coerceAtLeast(1)
    }

    /** 「磨刀石 / 发光」带来的版本概率加成（百分点）。 */
    private val editionBoost: Int
        get() = (if (Voucher.HONE.key in vouchers) 12 else 0) +
            (if (Voucher.GLOW_UP.key in vouchers) 12 else 0)

    /** 可透支额度：「信用卡」允许欠到 -$20。 */
    private val creditLine: Int
        get() = if (jokers.any { it.id == "creditcard" }) 20 else 0

    /** 行星牌价格：「天文学家」让行星全部免费。 */
    private fun planetPrice(base: Int): Int =
        if (jokers.any { it.id == "astronomer" }) 0 else priceOf(base)

    private fun priceOf(base: Int): Int =
        ((base + (challenge?.priceDelta ?: 0)) * discount).toInt().coerceAtLeast(1)

    /** 商店：买下第 [index] 个商品位。 */
    fun buy(index: Int): Boolean {
        if (phase != Phase.SHOP) return false
        val item = shop.cards.getOrNull(index) ?: return false
        if (item.sold) return false
        if (money + creditLine < item.price) {
            message = "金钱不足"
            return false
        }
        when (item) {
            is ShopItem.JokerItem -> {
                if (jokers.size >= maxJokers) {
                    message = "小丑位已满（$maxJokers）"
                    return false
                }
                jokers += item.inst
                jokersBought++
                message = "买入 ${item.inst.name}"
            }
            is ShopItem.ConsumableItem -> {
                if (consumables.size >= maxConsumables) {
                    message = "消耗品位已满（$maxConsumables）"
                    return false
                }
                consumables += item.c
                message = "买入 ${item.c.name}"
            }
            else -> return false
        }
        money -= item.price
        shop = shop.copy(
            cards = shop.cards.toMutableList().also { it[index] = markSold(item) }
        )
        return true
    }

    /** 商店：买下第 [index] 个补充包并立刻打开。 */
    fun buyBooster(index: Int): Boolean {
        if (phase != Phase.SHOP) return false
        val item = shop.boosters.getOrNull(index) as? ShopItem.BoosterItem ?: return false
        if (item.sold) return false
        if (money + creditLine < item.price) {
            message = "金钱不足"
            return false
        }
        money -= item.price
        shop = shop.copy(
            boosters = shop.boosters.toMutableList().also { it[index] = markSold(item) }
        )
        openPackNow(item.kind, item.size, Phase.SHOP)
        return true
    }

    /** 商店：买下第 [index] 个代金券位。 */
    fun buyVoucher(index: Int = 0): Boolean {
        if (phase != Phase.SHOP) return false
        val item = shop.vouchers.getOrNull(index) as? ShopItem.VoucherItem ?: return false
        if (item.sold) return false
        if (money + creditLine < item.price) {
            message = "金钱不足"
            return false
        }
        money -= item.price
        vouchers += item.v.key
        shop = shop.copy(
            vouchers = shop.vouchers.toMutableList().also { it[index] = markSold(item) }
        )
        message = if (item.v == Voucher.PAINTER) {
            // 手牌上限的券不会当场改（这一关已经发完牌了），所以必须写清"下关"
            "买入 ${item.v.cn} · 下关手牌 ${Blinds.BASE_HAND_SIZE + handSizeBonus()}"
        } else {
            "买入代金券 ${item.v.cn}"
        }
        return true
    }

    private fun markSold(item: ShopItem): ShopItem = when (item) {
        is ShopItem.JokerItem -> item.copy(sold = true)
        is ShopItem.ConsumableItem -> item.copy(sold = true)
        is ShopItem.BoosterItem -> item.copy(sold = true)
        is ShopItem.VoucherItem -> item.copy(sold = true)
    }

    /** 商店：卖出一个已拥有小丑（[index] 是 jokers 的下标）。 */
    fun sell(index: Int): Boolean {
        if (phase == Phase.MENU || phase == Phase.DECK_SELECT || phase == Phase.CHALLENGE_SELECT) return false
        if (stake.sellLocked) {
            message = "黑注：小丑不可卖出"
            return false
        }
        val j = jokers.getOrNull(index) ?: return false
        val refund = j.sellPrice
        // 「仪式匕首」：卖出时吞掉左侧那张，并把它的售价翻倍加到自己身上
        if (j.id == "ceremonial" && index > 0) {
            val eaten = jokers.removeAt(index - 1)
            money += eaten.sellPrice * 2
            jokers.removeAt(index - 1)
            jokersSold++
            money += refund
            message = "仪式匕首吞掉 ${eaten.name}，+\$${refund + eaten.sellPrice * 2}"
            return true
        }
        jokers.removeAt(index)
        jokersSold++
        money += refund
        // 「健怡可乐」：卖出时给 2 张标签（原版是双倍标签，这里直接给两张）
        if (j.id == "dietcola") {
            repeat(2) { applyTag(Tag.roll(random)) }
        }
        // 「摔角手」：卖出时废掉本关 Boss
        if (j.id == "luchador" && spec.kind == BlindKind.BOSS) {
            spec = spec.copy(modifier = BossModifier.NONE)
            message = "卖出 ${j.name}，+\$$refund · Boss 失效"
            return true
        }
        // 「翠叶」：卖掉一张小丑后全牌恢复计分
        if (spec.modifier == BossModifier.VERDANT && !verdantCleared) {
            verdantCleared = true
            message = "卖出 ${j.name}，+\$$refund · 翠叶解除"
        } else {
            message = "卖出 ${j.name}，+\$$refund"
        }
        return true
    }

    /** 商店：重掷商品位（补充包和代金券不刷新，与原版一致）。 */
    fun reroll(): Boolean {
        if (phase != Phase.SHOP) return false
        val free = rerollCost == 0 ||
            (pendingTags.contains(Tag.D6) && rerolls == 0) ||
            (pendingTags.contains(Tag.RETRY) && rerolls == 0) ||
            (jokers.any { it.id == "chaos" } && rerolls == 0) ||
            (Voucher.DIRECTOR.key in vouchers && rerolls == 0)
        if (!free && money < rerollCost) {
            message = "金钱不足"
            return false
        }
        if (!free) money -= rerollCost
        rerolls++
        rerollCost += 1
        // 只重掷商品位；包与券保留
        val keepBoosters = shop.boosters
        val keepVouchers = shop.vouchers
        rollShop()
        shop = ShopState(
            cards = shop.cards,
            boosters = keepBoosters,
            vouchers = keepVouchers,
        )
        message = if (free) "免费重掷" else "已重掷"
        return true
    }

    // ---------------------------------------------------------------- 补充包

    private fun openFreePack(kind: PackKind) {
        openPackNow(kind, PackSize.NORMAL, packReturn)
    }

    /**
     * 打开一个包。
     *
     * 内容在**买下的那一刻**生成 —— 同一个存档点反复"买-读档-买"结果一致，
     * 不会出现刷包。
     */
    private fun openPackNow(kind: PackKind, size: PackSize, returnTo: Phase) {
        val artisan = if (Voucher.ILLUSION.key in vouchers) 1 else 0
        val contents = Boosters.fill(
            kind = kind,
            size = size,
            random = random,
            ownedJokers = jokers.map { it.id }.toSet(),
            nextCardId = { nextCardId() },
            artisan = artisan,
        )
        openPack = BoosterPack(kind, size, contents)
        packPicked.clear()
        packReturn = returnTo
        phase = Phase.PACK_OPEN
        message = "${size.cn}${kind.cn}：选 ${size.picks} 张"
    }

    private fun nextCardId(): Int {
        while (master.containsKey(newCardSeq)) newCardSeq++
        return newCardSeq++
    }

    /** 在包里点一张：选中 / 取消。超过可选上限时忽略。 */
    fun togglePackPick(index: Int) {
        if (phase != Phase.PACK_OPEN) return
        val p = openPack ?: return
        if (index !in p.contents.indices) return
        if (packPicked.contains(index)) packPicked.remove(index)
        else if (packPicked.size < p.picks) packPicked.add(index)
    }

    /** 拿走选中的内容，关包。 */
    fun confirmPack(): Boolean {
        if (phase != Phase.PACK_OPEN) return false
        val p = openPack ?: return false
        if (packPicked.isEmpty()) {
            message = "至少选 1 张"
            return false
        }
        val picked = packPicked.sorted().mapNotNull { p.contents.getOrNull(it) }
        picked.forEach { takeFromPack(it) }
        closePack("拿到 ${picked.size} 张")
        return true
    }

    /** 跳过（什么都不拿）。 */
    fun skipPack() {
        if (phase != Phase.PACK_OPEN) return
        closePack("跳过卡包")
    }

    private fun closePack(msg: String) {
        openPack = null
        packPicked.clear()
        phase = packReturn
        message = msg
    }

    /**
     * 把包里的一张内容落到实处。
     *
     * 塔罗进槽位而不是立即生效 —— 需要选牌的塔罗在包内没法再套一层选牌界面，
     * 手表上这层嵌套会把 122dp 的宽度彻底用尽。行星不需要选牌，直接升级。
     */
    private fun takeFromPack(c: PackContent) {
        when (c) {
            is PackContent.Tarot -> {
                if (consumables.size < maxConsumables) consumables += c.c
                else money += 3
            }
            is PackContent.Planet -> {
                c.c.handType?.let { levels[it] = (levels[it] ?: 1) + 1 }
            }
            is PackContent.Playing -> {
                master[c.card.id] = c.card
                drawPile.add(c.card.id)
                drawPile.shuffle(random)
            }
            is PackContent.Joker -> {
                if (jokers.size < maxJokers) jokers += JokerInst(c.def, c.edition)
                else money += c.def.price
            }
        }
    }

    // ---------------------------------------------------------------- 内部逻辑

    /** 当前选牌的预估得分，用于 UI 实时提示。 */
    private fun previewOf(): ScoreBreakdown? {
        val played = hand.filter { it in selected }.mapNotNull { master[it] }
        if (played.isEmpty()) return null
        return Scoring.compute(
            played = played,
            hand = hand.filter { it !in selected }.mapNotNull { master[it] },
            jokers = jokers.toList(),
            levels = levels.toMap(),
            discardsLeft = discardsLeft,
            handsLeft = handsLeft,
            money = money,
            ante = ante,
            playedTypes = playedTypes.toSet(),
            blockedIds = effectiveBlocked(),
            flint = spec.modifier == BossModifier.FLINT,
            amber = spec.modifier == BossModifier.AMBER,
            disabledJoker = disabledJoker,
            deckLeft = drawPile.size,
            rerolls = rerolls,
            tarotsUsed = tarotsUsed,
            blindsSkipped = blindsSkipped,
            handsPlayed = handsPlayed,
            jokerSlots = maxJokers,
            steelInDeck = master.values.count { it.enh == Enhancement.STEEL },
            stoneInDeck = master.values.count { it.enh == Enhancement.STONE },
            destroyedFaces = destroyedFaces,
            discardsTotal = discardsTotal,
            planetsUsed = planetsUsed,
            jokersBought = jokersBought,
            jokersSold = jokersSold,
            glassShattered = glassShattered,
            deckSize = master.size,
            handSize = handSize,
            bossBlind = spec.kind == BlindKind.BOSS,
            fourFingers = jokers.any { it.id == "fourfingers" },
            shortcut = jokers.any { it.id == "shortcut" },
            smeared = jokers.any { it.id == "smeared" },
            splash = jokers.any { it.id == "splash" },
            // 预览不能消耗随机数，否则"看一眼"就会改变玻璃牌碎不碎
            random = kotlin.random.Random(0),
        )
    }

    private fun startBlind() {
        spec = specOf(ante, blindIndex)
        score = 0
        handsLeft = spec.hands
        discardsLeft = spec.discards
        handSize = spec.handSize
        if (pendingTags.contains(Tag.SPEED)) handsLeft += 1
        if (pendingTags.contains(Tag.JUGGLE)) handSize += 3
        if (pendingTags.contains(Tag.BOSS_TAG)) spec = spec.copy(modifier = BossModifier.NONE)
        // 「希科」：Boss 效果直接失效。放在这里而不是各处副作用里，
        // 是因为所有 Boss 判定都读 spec.modifier，一处生效就不会漏。
        if (jokers.any { it.id == "chicot" }) spec = spec.copy(modifier = BossModifier.NONE)
        playedTypes.clear()
        lastHandType = null
        pillarHistory.clear()
        blindBlocked.clear()
        verdantCleared = false
        disabledJoker = -1

        // 每个盲注重置牌堆：原版是同一副牌跨关连续，但手表上单局更长、
        // 玩家更难记住跨关弃了哪些牌，重洗反而降低认知负担。
        // 「即兴」：每关开始时送 2 个普通小丑（槽位满则折价）
        if (blindIndex == 0 && jokers.any { it.id == "riff" }) {
            repeat(2) {
                val def = Jokers.rollOne(random, jokers.map { it.id }.toSet()) ?: return@repeat
                if (jokers.size < maxJokers) jokers += JokerInst(def, Jokers.randomEdition(random, editionBoost))
                else money += 4
            }
        }
        // 「大理石 / 证书」按原版"每关开始时"加牌，放在重建抽牌堆之前。
        if (blindIndex == 0) {
            if (jokers.any { it.id == "marble" }) {
                val id = nextCardId()
                master[id] = Card(
                    id,
                    Rank.entries[random.nextInt(Rank.entries.size)],
                    Suit.entries[random.nextInt(Suit.entries.size)],
                    Enhancement.STONE,
                )
            }
            if (jokers.any { it.id == "certificate" }) {
                val id = nextCardId()
                val seals = Seal.entries.filter { !it.isNone }
                master[id] = Card(
                    id,
                    Rank.entries[random.nextInt(Rank.entries.size)],
                    Suit.entries[random.nextInt(Suit.entries.size)],
                    seal = seals[random.nextInt(seals.size)],
                )
            }
        }
        drawPile = master.keys.shuffled(random).toMutableList()
        discardPile = mutableListOf()
        hand = mutableListOf()
        selected.clear()
        lastResult = null
        lastPlayed = emptyList()
        phase = Phase.PLAYING
        refill(full = true)
        sortHand()

        // 牌面封锁型 Boss 在发牌之后才能算
        when (spec.modifier) {
            BossModifier.HOUSE -> blindBlocked += hand.shuffled(random).take(2)
            BossModifier.WHEEL -> blindBlocked += hand.shuffled(random).take(3)
            else -> Unit
        }
        message = "凑够 ${spec.target} 分"
    }

    private fun refill(full: Boolean = false) {
        val cap = if (!full && spec.modifier == BossModifier.SERPENT) {
            (hand.size + 3).coerceAtMost(handSize)
        } else {
            handSize
        }
        var guard = 0
        while (hand.size < cap && guard++ < 60) {
            if (drawPile.isEmpty()) {
                if (discardPile.isEmpty()) break
                drawPile = discardPile.shuffled(random).toMutableList()
                discardPile = mutableListOf()
            }
            hand.add(drawPile.removeAt(drawPile.lastIndex))
        }
    }

    /** 达标：算出结算明细，停在 CASH_OUT 让玩家确认，不直接加钱。 */
    private fun clearBlind() {
        blindsCleared++
        val eco = pendingTags.contains(Tag.ECONOMY)
        val interest = ((money / 5).coerceAtMost(interestCap)) * (if (eco) 2 else 1)
        val handy = if (pendingTags.contains(Tag.HANDY)) handsLeft else 0
        val wallet = if (Voucher.WALLET.key in vouchers) 2 else 0
        val dbl = pendingTags.contains(Tag.DOUBLE)
        val base = if (stake.smallBlindNoReward && spec.kind == BlindKind.SMALL) 0 else spec.reward
        val handBonus = handsLeft
        val subtotal = base + handBonus + interest + handy + wallet
        cashOut = CashOutDetail(
            ante = ante,
            blindKind = spec.kind,
            score = score,
            target = spec.target,
            baseReward = base,
            handsLeft = handsLeft,
            handBonus = handBonus,
            interest = interest,
            handyBonus = handy,
            doubleTag = dbl,
            walletBonus = wallet,
            total = if (dbl) subtotal * 2 else subtotal,
        )
        // 蓝蜡封：回合结束时仍留在手里的牌，各生成"最后打出牌型"的行星牌
        // （原版 1.0.1f 起改为按牌型给行星，不再是随机行星）
        val blueHeld = hand.count { id -> master[id]?.seal == Seal.BLUE }
        val lastType = lastHandType ?: playedTypes.lastOrNull()
        if (blueHeld > 0 && lastType != null) {
            repeat(blueHeld) {
                if (consumables.size < maxConsumables) consumables += Consumables.planetFor(lastType)
                else money += 3
            }
        }
        // 「纸牌师」：每关结束给 1 张塔罗
        if (jokers.any { it.id == "cartomancer" }) {
            if (consumables.size < maxConsumables) consumables += Consumables.rollTarot(random)
            else money += 3
        }
        phase = Phase.CASH_OUT
        message = "达标！$score / ${spec.target}"
    }

    private fun advanceBlind() {
        if (blindIndex >= 2) {
            if (!endless && ante >= Blinds.MAX_ANTE) {
                phase = Phase.WIN
                return
            }
            ante++
            blindIndex = 0
        } else {
            blindIndex++
        }
    }

    /** 复位到「一局都没开」的状态，不动 [phase]。 */
    private fun resetRun(type: DeckType, seedOverride: Long? = null) {
        deckType = type
        if (seedOverride != null) {
            seed = seedOverride
            random = kotlin.random.Random(seed)
        } else {
            random = kotlin.random.Random(seed)
        }
        ante = 1
        blindIndex = 0
        score = 0
        money = challenge?.money ?: (stake.startMoney + type.extraMoney)
        jokers.clear()
        consumables.clear()
        levels.clear()
        vouchers.clear()
        pendingTags.clear()
        playedTypes.clear()
        typeCount.clear()
        master.clear()
        Deck.forType(type, random).forEach { master[it.id] = it }
        drawPile = master.keys.shuffled(random).toMutableList()
        discardPile = mutableListOf()
        hand = mutableListOf()
        selected.clear()
        shop = ShopState()
        openPack = null
        packPicked.clear()
        rerollCost = rerollBase
        cashOut = null
        lastResult = null
        lastPlayed = emptyList()
        handsPlayed = 0
        bestHand = 0
        blindsCleared = 0
        lastGain = 0
        endless = false
        rerolls = 0
        tarotsUsed = 0
        blindsSkipped = 0
        destroyedFaces = 0
        discardsTotal = 0
        planetsUsed = 0
        jokersBought = 0
        jokersSold = 0
        glassShattered = 0
        bonesSaved = false
        handSizePenalty = 0
        lastHandType = null
        blindBlocked.clear()
        pillarHistory.clear()
        verdantCleared = false
        disabledJoker = -1
        newCardSeq = 100
        pendingOutcome = null
        bossSeq = Blinds.bossSequence(random, Blinds.MAX_ANTE)
        grantStarters()
        spec = specOf(1, 0)
        handsLeft = spec.hands
        discardsLeft = spec.discards
        handSize = spec.handSize
    }

    /**
     * 开局赠送：牌组的塔罗/行星/幻灵、挑战的随机小丑。
     *
     * 顺序放在 [master] 与 [bossSeq] 之后、[spec] 之前 —— 因为
     * 「小丑位」与「手牌上限」都受这些东西影响，先给再算才不会少一格。
     */
    private fun grantStarters() {
        repeat(deckType.startTarots) {
            consumables += Consumables.rollTarot(random)
        }
        repeat(deckType.startPlanets) {
            consumables += Consumables.rollPlanet(random)
        }
        repeat(deckType.startSpectrals) {
            consumables += Consumables.rollSpectral(random)
        }
        repeat(challenge?.startJokers ?: 0) {
            val def = Jokers.rollOne(random, jokers.map { it.id }.toSet()) ?: return@repeat
            // 挑战送的小丑无视槽位上限（否则「发射」这种 0 槽位挑战会直接卡住）
            jokers += JokerInst(def, Jokers.randomEdition(random, editionBoost))
        }
    }

    private fun blindOptions(): List<BlindOption> = (0..2).map { i ->
        val s = specOf(ante, i)
        BlindOption(
            index = i,
            kind = s.kind,
            target = s.target,
            reward = s.reward,
            modifier = s.modifier,
            cleared = i < blindIndex,
            current = i == blindIndex,
            skippable = s.skippable,
        )
    }

    // ---------------------------------------------------------------- 快照 / 存档

    fun snapshot(): GameUi = GameUi(
        phase = phase,
        deckType = deckType,
        ante = ante,
        blindIndex = blindIndex,
        blindKind = spec.kind,
        modifier = spec.modifier,
        target = spec.target,
        score = score,
        handsLeft = handsLeft,
        discardsLeft = discardsLeft,
        handSize = handSize,
        money = money,
        hand = hand.mapNotNull { master[it] },
        selected = selected.toSet(),
        jokers = jokers.toList(),
        maxJokers = maxJokers,
        consumables = consumables.toList(),
        maxConsumables = maxConsumables,
        preview = if (phase == Phase.PLAYING) previewOf() else null,
        lastResult = lastResult,
        shop = if (phase == Phase.SHOP) shop else ShopState(),
        pack = openPack?.let {
            PackView(it.kind, it.size, it.contents, packPicked.toSet(), it.picks)
        },
        rerollCost = rerollCost,
        deckLeft = drawPile.size,
        levels = levels.toMap(),
        vouchers = vouchers.mapNotNull { k -> Voucher.entries.firstOrNull { it.key == k } },
        tags = pendingTags.toList(),
        playedTypes = playedTypes.toSet(),
        message = message,
        blindOptions = if (phase == Phase.BLIND_SELECT) blindOptions() else emptyList(),
        cashOut = cashOut,
        handsPlayed = handsPlayed,
        bestHand = bestHand,
        blindsCleared = blindsCleared,
        lastGain = lastGain,
        endless = endless,
        hasSave = hasSave,
        stake = stake,
        challenge = challenge,
        blockedIds = if (phase == Phase.PLAYING) effectiveBlocked() else emptySet(),
        playedCards = lastPlayed,
        drawPileCards = drawPile.mapNotNull { master[it] },
        discardCards = discardPile.mapNotNull { master[it] },
    )

    /** 导出可持久化状态。 */
    fun export(): RunState = RunState(
        seed = seed,
        deckType = deckType,
        phase = phase,
        ante = ante,
        blindIndex = blindIndex,
        score = score,
        money = money,
        handsLeft = handsLeft,
        discardsLeft = discardsLeft,
        handSize = handSize,
        master = master.values.toList(),
        drawPile = drawPile.toList(),
        discardPile = discardPile.toList(),
        hand = hand.toList(),
        jokers = jokers.toList(),
        consumables = consumables.map { it.key },
        levels = levels.toMap(),
        vouchers = vouchers.toList(),
        bossSeq = bossSeq,
        pendingTags = pendingTags.toList(),
        playedTypes = playedTypes.toList(),
        destroyed = emptyList(),
        shop = shop,
        rerollCost = rerollCost,
        cashOut = cashOut,
        handsPlayed = handsPlayed,
        bestHand = bestHand,
        blindsCleared = blindsCleared,
        lastGain = lastGain,
        endless = endless,
        message = message,
        rerolls = rerolls,
        tarotsUsed = tarotsUsed,
        blindsSkipped = blindsSkipped,
        blindBlocked = blindBlocked.toList(),
        pillarHistory = pillarHistory.toList(),
        typeCount = typeCount.mapKeys { it.key.name },
        verdantCleared = verdantCleared,
        pack = openPack,
        packPicked = packPicked.toList(),
        packReturn = packReturn,
        destroyedFaces = destroyedFaces,
        discardsTotal = discardsTotal,
        handSizePenalty = handSizePenalty,
        stake = stake,
        challenge = challenge?.id ?: "",
    )

    /** 从存档恢复。任一字段缺失都由 [SaveCodec] 兜底成安全默认值。 */
    fun restore(s: RunState) {
        seed = s.seed
        random = kotlin.random.Random(s.seed xor 0x5DEECE66DL)
        deckType = s.deckType
        phase = if (s.phase == Phase.MENU || s.phase == Phase.DECK_SELECT) {
            Phase.BLIND_SELECT
        } else {
            s.phase
        }
        ante = s.ante.coerceAtLeast(1)
        blindIndex = s.blindIndex.coerceIn(0, 2)
        score = s.score
        money = s.money
        handsLeft = s.handsLeft
        discardsLeft = s.discardsLeft
        handSize = s.handSize
        master.clear()
        s.master.forEach { master[it.id] = it }
        if (master.isEmpty()) Deck.forType(s.deckType, random).forEach { master[it.id] = it }
        newCardSeq = (master.keys.maxOrNull() ?: 51).let { if (it >= 100) it + 1 else 100 }
        drawPile = s.drawPile.filter { it in master }.toMutableList()
        discardPile = s.discardPile.filter { it in master }.toMutableList()
        hand = s.hand.filter { it in master }.toMutableList()
        selected.clear()
        jokers.clear()
        jokers += s.jokers
        consumables.clear()
        s.consumables.mapNotNull { Consumables.get(it) }.forEach { consumables += it }
        levels.clear()
        levels.putAll(s.levels)
        vouchers.clear()
        vouchers += s.vouchers
        bossSeq = if (s.bossSeq.size >= Blinds.MAX_ANTE) s.bossSeq else Blinds.bossSequence(random, Blinds.MAX_ANTE)
        pendingTags.clear()
        pendingTags += s.pendingTags
        playedTypes.clear()
        playedTypes += s.playedTypes
        typeCount.clear()
        s.typeCount.forEach { (k, v) ->
            HandType.entries.firstOrNull { it.name == k }?.let { typeCount[it] = v }
        }
        shop = s.shop
        openPack = s.pack
        packPicked.clear()
        packPicked += s.packPicked
        packReturn = s.packReturn
        rerollCost = s.rerollCost
        cashOut = s.cashOut
        handsPlayed = s.handsPlayed
        bestHand = s.bestHand
        blindsCleared = s.blindsCleared
        lastGain = s.lastGain
        endless = s.endless
        message = s.message
        rerolls = s.rerolls
        tarotsUsed = s.tarotsUsed
        blindsSkipped = s.blindsSkipped
        destroyedFaces = s.destroyedFaces
        discardsTotal = s.discardsTotal
        handSizePenalty = s.handSizePenalty
        stake = s.stake
        challenge = s.challenge.takeIf { it.isNotEmpty() }
            ?.let { id -> Challenge.entries.firstOrNull { it.id == id } }
        lastHandType = null
        blindBlocked.clear()
        blindBlocked += s.blindBlocked
        pillarHistory.clear()
        pillarHistory += s.pillarHistory
        verdantCleared = s.verdantCleared
        disabledJoker = -1
        lastResult = null
        spec = specOf(ante, blindIndex)
        // 恢复出来的可能是 PLAYING，但手牌为空的边界情况要补牌
        if (phase == Phase.PLAYING && hand.isEmpty()) refill(full = true)
        // 存档点正好落在"这一手已经达标/用尽但动画没播完"的瞬间：
        // 直接补一次结局，避免恢复后卡在 PLAYING 出不了牌也过不了关。
        if (phase == Phase.PLAYING) {
            if (score >= spec.target) pendingOutcome = Outcome.CLEAR
            else if (handsLeft <= 0) pendingOutcome = Outcome.FAIL
            resolvePending()
        }
    }
}
