package com.wearos.wristjoker.game

/**
 * 代金券（Voucher）。
 *
 * 原版 32 张、成对升级（买了一张才会出现它的升级版）。手表上不做层级，
 * 而是把同一效果的强弱两档都摊平 —— 这样"再买一张"永远是可见的收益，
 * 玩家不需要记住自己站在哪条升级链的第几级。
 *
 * 收录 18 张，全部**真正生效**（每张都在 GameEngine 里有一处读取点，
 * 不是只写在 desc 里）。原版里依赖"商店刷新次数""卡包内容"的券略过。
 *
 * 每张券的数值直接写在 [desc] 里，UI 不再二次措辞。
 */
enum class Voucher(
    val key: String,
    val cn: String,
    val en: String,
    val desc: String,
    val price: Int,
) {
    // ---- 商店容量 ----
    OVERSTOCK("overstock", "多余库存", "Overstock", "商品位 3→4", 10),
    OVERSTOCK_PLUS("overstock2", "补货上架", "Overstock+", "商品位 4→5", 10),

    // ---- 折扣 ----
    CLEARANCE("clearance", "清仓折扣", "Clearance Sale", "全场 75 折", 10),
    LIQUIDATION("liquidation", "清盘甩卖", "Liquidation", "全场 5 折", 10),

    // ---- 重掷 ----
    REROLL("reroll", "重掷折扣", "Reroll Surplus", "重掷 -\$2", 10),
    REROLL_GLUT("reroll2", "重掷过剩", "Reroll Glut", "重掷再 -\$2", 10),

    // ---- 手牌 / 资源 ----
    PAINTER("painter", "画家", "Painter", "手牌 +1", 10),
    JOKER_BOX("joker_box", "小丑箱", "Joker Box", "小丑位 +1", 10),
    GRABBER("grabber", "抓手", "Grabber", "每关出牌 +1", 10),
    NACHO("nacho", "玉米片", "Nacho Stand", "每关弃牌 +1", 10),

    // ---- 利息 ----
    SEED_MONEY("seed_money", "种子基金", "Seed Money", "利息上限 +5", 10),
    MONEY_TREE("money_tree", "摇钱树", "Money Tree", "利息上限再 +5", 10),

    // ---- 商店构成 ----
    TAROT_MERCHANT("tarot_merchant", "塔罗商", "Tarot Merchant", "塔罗权重 ×2", 10),
    PLANET_MERCHANT("planet_merchant", "行星商", "Planet Merchant", "行星权重 ×2", 10),
    TELESCOPE("telescope", "望远镜", "Telescope", "行星包权重 ×2", 10),
    ILLUSION("illusion", "幻象", "Illusion", "标准包牌必带增强", 10),

    // ---- 现金 ----
    WALLET("wallet", "钱袋", "Tight Wad", "每次过关 +\$2", 10),

    // ---- 补齐到原版 32 张 ----
    // 这一组全部是**叠加型**：不与已有券互斥，买到就是纯增益，且每张都
    // 在引擎里有一处读取点。原版里靠"商店刷新次数""卡包内容"的券仍然略过
    // （手表上无法把这些数字展示出来，买了也感知不到）。
    HONE("hone", "磨刀石", "Hone", "版本出现率 18%→30%", 10),
    GLOW_UP("glow", "发光", "Glow Up", "版本出现率再 +12%", 10),
    CRYSTAL_BALL("crystal", "水晶球", "Crystal Ball", "消耗品位 +1", 10),
    TAROT_TYCOON("tarot_tycoon", "塔罗大亨", "Tarot Tycoon", "塔罗权重 ×4", 10),
    PLANET_TYCOON("planet_tycoon", "行星大亨", "Planet Tycoon", "行星权重 ×4", 10),
    MAGIC_TRICK("magic_trick", "魔术", "Magic Trick", "商店必有 1 张塔罗", 10),
    OBSERVATORY("observatory", "天文台", "Observatory", "行星牌升级 +2 级", 10),
    BLANK("blank", "空白", "Blank", "没有效果 · 便宜", 4),
    DIRECTOR("director", "导演剪辑", "Director's Cut", "每关首次重掷免费", 10),
    RETCON("retcon", "重来", "Retcon", "重掷价固定 \$2", 10),
    PAINT_BRUSH("paint_brush", "画笔", "Paint Brush", "手牌 +1", 10),
    PALETTE("palette", "调色板", "Palette", "手牌再 +1", 10),
    ANTIMATTER("antimatter", "反物质", "Antimatter", "小丑位 +1", 10),
    HIEROGLYPH("hieroglyph", "象形文字", "Hieroglyph", "每关出牌 +1", 10),
    WASTEFUL("wasteful", "挥霍", "Wasteful", "每关弃牌 +1", 10),
    ;

    companion object {
        /** 抽 [count] 张尚未拥有的券。 */
        fun roll(random: kotlin.random.Random, owned: Set<String>, count: Int): List<Voucher> {
            val pool = entries.filter { it.key !in owned }
            if (pool.isEmpty()) return emptyList()
            return pool.shuffled(random).take(count)
        }
    }
}

/**
 * 标签（Tag）。
 *
 * 原版的 Tag 是"跳过盲注"的补偿 ——  非常精巧的设计：跳过省下了打这一关的时间，
 * 但放弃了这一关的钱、利息和一次商店机会，换来一张有明确预期的补偿。
 *
 * 收录 14 种，全部即时结算（不引入"待生效队列"：122dp 宽的界面上
 * 没法展示"我身上挂着什么"，队列在这里等于让玩家失忆）。
 *
 * [needsNext] 为 true 的表示"在下一关生效"，由 [GameEngine] 的 pendingTags 持有。
 */
enum class Tag(
    val key: String,
    val cn: String,
    val en: String,
    val desc: String,
    val needsNext: Boolean = false,
) {
    CHARM("charm", "魅力", "Charm Tag", "免费随机小丑"),
    METEOR("meteor", "陨石", "Meteor Tag", "免费随机行星"),
    BUFFOON("buffoon", "小丑", "Buffoon Tag", "免费小丑包"),
    ETHEREAL("ethereal", "虚空", "Ethereal Tag", "免费塔罗包"),
    STANDARD("standard", "标准", "Standard Tag", "免费标准包"),
    INVESTMENT("investment", "投资", "Investment Tag", "立刻 +\$25"),
    SPEED("speed", "速度", "Speed Tag", "本关出牌 +1", needsNext = true),
    ECONOMY("economy", "经济", "Economy Tag", "本关利息 ×2", needsNext = true),
    DOUBLE("double", "双倍", "Double Tag", "本关金钱 ×2", needsNext = true),
    HANDY("handy", "手巧", "Handy Tag", "每剩 1 出牌 +\$1", needsNext = true),
    JUGGLE("juggle", "杂耍", "Juggle Tag", "本关手牌 +3", needsNext = true),
    COUPON("coupon", "优惠券", "Coupon Tag", "下次商店 5 折", needsNext = true),
    BOSS_TAG("boss", "头目", "Boss Tag", "下一个 Boss 失效", needsNext = true),
    D6("d6", "D6", "D6 Tag", "下次商店重掷免费", needsNext = true),

    // ---- 补齐到原版 24 张 ----
    VOUCHER("voucher", "代金券", "Voucher Tag", "立刻 +\$10"),
    TOPUP("topup", "补充", "Top-up Tag", "立刻 +\$6"),
    RARE("rare", "稀有", "Rare Tag", "立刻 +\$12"),
    ORBITAL("orbital", "轨道", "Orbital Tag", "随机 3 种牌型升级"),
    HOARD("hoard", "囤积", "Hoard Tag", "复制一张消耗品"),
    NEGATIVE("negative", "负片", "Negative Tag", "送一张负片小丑"),
    FOIL("foil", "箔", "Foil Tag", "随机小丑变箔"),
    HOLO("holo", "全息", "Holo Tag", "随机小丑变全息"),
    POLY("poly", "多彩", "Poly Tag", "随机小丑变多彩"),
    RETRY("retry", "重试", "Retry Tag", "下次商店重掷免费", needsNext = true),
    ;

    companion object {
        fun roll(random: kotlin.random.Random): Tag = entries.random(random)
    }
}
