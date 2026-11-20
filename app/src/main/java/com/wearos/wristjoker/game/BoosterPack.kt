package com.wearos.wristjoker.game

/**
 * 补充包（Booster Pack）。
 *
 * 这是上一版商店最明显的缺口：原版每个商店固定 2 个商品位 + **2 个补充包位** +
 * 1 个代金券位，而补充包是玩家拿到「塔罗 / 行星 / 扑克牌 / 小丑」的第二条通道，
 * 尤其是标准包 —— 原版里唯一能把改造过的扑克牌塞进牌堆的方式。
 *
 * 数值取原版公开表：
 *
 * | 类型 | 普通 $4 | 巨型 $6 | 超大 $8 |
 * |---|---|---|---|
 * | 塔罗 / 行星 / 标准 | 3 选 1 | 5 选 1 | 5 选 2 |
 * | 小丑 | 2 选 1 | 4 选 1 | 4 选 2 |
 */
enum class PackKind(val cn: String, val en: String, val short: String) {
    ARCANA("塔罗包", "Arcana", "塔"),
    CELESTIAL("行星包", "Celestial", "行"),
    STANDARD("标准包", "Standard", "牌"),
    BUFFOON("小丑包", "Buffoon", "丑"),
    /** 原版第 5 类：幻灵包。上一版完全没有，幻灵只能靠彩蛋出现。 */
    SPECTRAL("幻灵包", "Spectral", "幻"),
}

/** 包的规格。价格与可选张数是原版数值。 */
enum class PackSize(val cn: String, val picks: Int, val price: Int) {
    NORMAL("普通", 1, 4),
    JUMBO("巨型", 1, 6),
    MEGA("超大", 2, 8),
    ;

    /** 包里一共有几张可看。小丑包比别的少一张，与原版一致。 */
    fun contentCount(kind: PackKind): Int = when (kind) {
        // 小丑包与幻灵包比别的少一张，与原版 Booster Packs 表一致
        PackKind.BUFFOON, PackKind.SPECTRAL -> if (this == NORMAL) 2 else 4
        else -> if (this == NORMAL) 3 else 5
    }
}

/** 包里的一张内容。 */
sealed interface PackContent {
    /** 塔罗：选中后进消耗品槽（需要选牌，包内没法再套一层选牌界面）。 */
    data class Tarot(val c: Consumable) : PackContent

    /** 行星：选中后立即升级对应牌型。 */
    data class Planet(val c: Consumable) : PackContent

    /** 扑克牌：选中后加入牌堆主表。 */
    data class Playing(val card: Card) : PackContent

    /** 小丑：选中后直接入槽。 */
    data class Joker(val def: JokerDef, val edition: Edition) : PackContent

    val label: String
        get() = when (this) {
            is Tarot -> c.name
            is Planet -> c.name
            is Playing -> card.toString()
            is Joker -> def.name
        }
}

/**
 * 一个已开出的包。
 *
 * [contents] 在买下时一次性生成 —— 原版同一个种子的同种包内容固定，
 * 这里也走引擎的 [kotlin.random.Random]，所以同一存档点反复"买-读档-买"
 * 结果一致，不会出现刷包。
 */
data class BoosterPack(
    val kind: PackKind,
    val size: PackSize,
    val contents: List<PackContent>,
) {
    val picks: Int get() = size.picks
}

object Boosters {

    /**
     * 抽包权重，逐格取原版 Booster Packs 表（balatrowiki / fandom 的权重表）：
     *
     * | 类型 | 普通 | 巨型 | 超大 |
     * |---|---|---|---|
     * | 塔罗 / 行星 / 标准 | 4 | 2 | 0.5 |
     * | 小丑 | 1.2 | 0.6 | 0.15 |
     * | 幻灵 | 0.6 | 0.3 | 0.07 |
     *
     * 也就是说幻灵包大约是塔罗包的 1/6~1/7 常见 —— 它是稀有包，不是没有。
     */
    private val WEIGHT: List<Triple<PackKind, PackSize, Double>> = buildList {
        fun put(k: PackKind, n: Double, j: Double, m: Double) {
            add(Triple(k, PackSize.NORMAL, n))
            add(Triple(k, PackSize.JUMBO, j))
            add(Triple(k, PackSize.MEGA, m))
        }
        put(PackKind.ARCANA, 4.0, 2.0, 0.5)
        put(PackKind.CELESTIAL, 4.0, 2.0, 0.5)
        put(PackKind.STANDARD, 4.0, 2.0, 0.5)
        put(PackKind.BUFFOON, 1.2, 0.6, 0.15)
        put(PackKind.SPECTRAL, 0.6, 0.3, 0.07)
    }

    /** 按权重抽 [count] 个包规格（种类可以重复，原版也会出现两个同种包）。 */
    fun roll(random: kotlin.random.Random, count: Int): List<Pair<PackKind, PackSize>> {
        val total = WEIGHT.sumOf { it.third }
        val out = ArrayList<Pair<PackKind, PackSize>>(count)
        repeat(count) {
            var r = random.nextDouble(total)
            var picked = WEIGHT.first().let { it.first to it.second }
            for (e in WEIGHT) {
                r -= e.third
                if (r <= 0) { picked = e.first to e.second; break }
            }
            out.add(picked)
        }
        return out
    }

    /**
     * 生成包内容。
     *
     * [deckSeed] 用于标准包生成扑克牌 id —— 必须避开已有牌堆的 0..51，
     * 所以新牌从 100 起编号（见 [nextCardId]）。
     */
    fun fill(
        kind: PackKind,
        size: PackSize,
        random: kotlin.random.Random,
        ownedJokers: Set<String>,
        nextCardId: () -> Int,
        artisan: Int = 0,
    ): List<PackContent> {
        val n = size.contentCount(kind)
        val contents = when (kind) {
            PackKind.ARCANA -> List(n) { PackContent.Tarot(Consumables.rollTarot(random)) }
            PackKind.CELESTIAL -> List(n) { PackContent.Planet(Consumables.rollPlanet(random)) }
            PackKind.BUFFOON -> {
                val picked = Jokers.roll(random, ownedJokers, n)
                picked.map { PackContent.Joker(it, Jokers.randomEdition(random, 0)) }
            }
            PackKind.STANDARD -> List(n) {
                PackContent.Playing(randomPlayingCard(random, nextCardId, artisan))
            }
            PackKind.SPECTRAL -> List(n) { PackContent.Tarot(Consumables.rollSpectral(random)) }
        }
        // 原版两张幻灵会**跨包**出现：「灵魂」进塔罗包、「黑洞」进行星包，
        // 概率极低（0.3%）。这里按包的类型分别判定，而不是任何包都可能冒出灵魂。
        val cameo: Consumable? = when {
            kind == PackKind.ARCANA && random.nextInt(1000) < 3 -> Consumables.SOUL
            kind == PackKind.CELESTIAL && random.nextInt(1000) < 3 -> Consumables.BLACK_HOLE
            else -> null
        }
        if (cameo != null) return listOf(PackContent.Tarot(cameo)) + contents.drop(1)
        return contents
    }

    /**
     * 标准包里的一张牌。
     *
     * 原版概率：增强 40%、蜡封 20%、版本 20%（受 Illusion 券影响）。
     * 这里给「增强 40% / 蜡封 20% / 版本 10%」，让标准包真的能改牌堆构成。
     */
    private fun randomPlayingCard(
        random: kotlin.random.Random,
        nextCardId: () -> Int,
        artisan: Int,
    ): Card {
        val suit = Suit.entries[random.nextInt(4)]
        val rank = Rank.entries[random.nextInt(13)]
        val enhPool = Enhancement.entries.filter { it != Enhancement.NONE }
        val enh = if (random.nextInt(100) < 40 + artisan * 15) {
            enhPool[random.nextInt(enhPool.size)]
        } else Enhancement.NONE
        val seal = if (random.nextInt(100) < 20 + artisan * 10) {
            // 4 种蜡封与原版一致（红重触发 / 金计分给钱 / 蓝回合末行星 / 紫弃牌塔罗）
            val pool = listOf(Seal.RED, Seal.GOLD, Seal.BLUE, Seal.PURPLE)
            pool[random.nextInt(pool.size)]
        } else Seal.NONE
        val ed = if (random.nextInt(100) < 10 + artisan * 5) {
            val pool = listOf(Edition.FOIL, Edition.HOLO, Edition.POLY)
            pool[random.nextInt(pool.size)]
        } else Edition.BASE
        return Card(nextCardId(), rank, suit, enh, seal, ed)
    }
}
