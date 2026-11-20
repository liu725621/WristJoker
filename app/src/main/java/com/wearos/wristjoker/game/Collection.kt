package com.wearos.wristjoker.game

/**
 * 图鉴（Collection）。
 *
 * 原版有一个「Collection」页，把 150 张小丑、52 张消耗品、32 张代金券、
 * 24 个标签、15 个牌组全部列出来，没解锁的显示成黑影。它的作用不是"好看"，
 * 而是**让玩家知道自己还差什么** —— 没有图鉴，150 张小丑在玩家眼里就是一锅
 * 随机数，既不知道总量也不知道该往哪个方向构筑。
 *
 * 手表上的实现做了两点让步：
 *
 * 1. **解锁判据是"见过"而不是"拥有过"** —— 手表屏幕小、单局短，要求玩家
 *    真的买到过某张小丑才解锁，会让图鉴长期停在 20/150。这里只要它在你
 *    的商店里出现过、包内开出过、或者你持有过，就算解锁。
 * 2. **不做成就/进度条** —— 原版图鉴还挂着"用某张牌赢一局"这类成就，手表上
 *    没有足够的空间和耐心承载，硬塞只会变成噪音。
 *
 * 数据全部从既有枚举里导出，不新写一份清单，避免两处不一致。
 */
enum class CollectionTab(val cn: String, val glyph: String) {
    JOKER("小丑", "丑"),
    CONSUMABLE("消耗品", "耗"),
    VOUCHER("代金券", "券"),
    TAG("标签", "签"),
    DECK("牌组", "组"),
    ;
}

/**
 * 图鉴里的一条。
 *
 * [id] 带前缀（`j:` / `c:` / `v:` / `t:` / `d:`），因为不同表里的 key 会撞车
 * —— 代金券有 `rare`，标签也有 `rare`。存档只存这一串 id，"见过"的判定
 * 才不会串。
 */
data class CollectionEntry(
    val id: String,
    val name: String,
    /** 副标题：小丑是稀有度，消耗品是种类，券/标签是价格或触发时机。 */
    val sub: String,
    val desc: String,
    val tab: CollectionTab,
)

object Collection {

    private val jokers: List<CollectionEntry> = Jokers.ALL.map {
        CollectionEntry(
            id = "j:${it.id}",
            name = it.name,
            sub = it.rarity.cn,
            desc = it.desc,
            tab = CollectionTab.JOKER,
        )
    }

    private val consumables: List<CollectionEntry> = Consumables.ALL.map {
        CollectionEntry(
            id = "c:${it.key}",
            name = it.name,
            sub = it.kind.cn,
            desc = it.desc,
            tab = CollectionTab.CONSUMABLE,
        )
    }

    private val vouchers: List<CollectionEntry> = Voucher.entries.map {
        CollectionEntry(
            id = "v:${it.key}",
            name = it.cn,
            sub = "券 · \$${it.price}",
            desc = it.desc,
            tab = CollectionTab.VOUCHER,
        )
    }

    private val tags: List<CollectionEntry> = Tag.entries.map {
        CollectionEntry(
            id = "t:${it.key}",
            name = it.cn,
            // 标签分「立刻生效」和「下一关生效」两类，这一条信息比价格有用
            sub = if (it.needsNext) "下关生效" else "立刻生效",
            desc = it.desc,
            tab = CollectionTab.TAG,
        )
    }

    private val decks: List<CollectionEntry> = DeckType.entries.map {
        CollectionEntry(
            id = "d:${it.name}",
            name = it.cn,
            sub = it.en,
            desc = it.desc,
            tab = CollectionTab.DECK,
        )
    }

    fun of(tab: CollectionTab): List<CollectionEntry> = when (tab) {
        CollectionTab.JOKER -> jokers
        CollectionTab.CONSUMABLE -> consumables
        CollectionTab.VOUCHER -> vouchers
        CollectionTab.TAG -> tags
        CollectionTab.DECK -> decks
    }

    fun total(tab: CollectionTab): Int = of(tab).size

    fun all(): List<CollectionEntry> = jokers + consumables + vouchers + tags + decks

    /** 全部条目 id，供存档侧做"这条算不算合法的已解锁 id"校验。 */
    val allIds: Set<String> by lazy { all().map { it.id }.toSet() }

    fun find(id: String): CollectionEntry? = all().firstOrNull { it.id == id }

    /** 已解锁数量（[seen] 里不在图鉴中的脏数据不计入）。 */
    fun seenCount(tab: CollectionTab, seen: Set<String>): Int =
        of(tab).count { it.id in seen }
}
