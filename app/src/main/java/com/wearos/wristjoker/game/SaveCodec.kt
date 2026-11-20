package com.wearos.wristjoker.game

/**
 * 存档编解码。
 *
 * 刻意**不用 JSON**：`org.json` 在 JVM 单元测试里是空壳类（调用即抛
 * `Stub!`），一旦用它做序列化，"存档能不能还原"就只能靠真机验证，
 * 而这恰恰是最需要反复测的部分。所以这里手写一个行式格式：
 *
 * ```
 * v|2
 * seed|123456
 * deck|RED
 * master|0~0~0~0,1,2,3
 * ...
 * ```
 *
 * 一行一个字段，`|` 前是键、后是值；值内部用 `,` 分隔元素、`~` 分隔子字段。
 * 解析全程容错：任何一行坏了只丢这一行，其余照常还原，最后用默认值兜底。
 *
 * v2 起：商店拆成商品位 / 卡包位 / 代金券位三段，牌堆允许出现 52 起的
 * 「卡包生成的牌」（这类牌必须额外存点数与花色）。
 */
object SaveCodec {

    const val VERSION = 2

    /** 存档字符串上限，防止读到损坏的超大文件时卡住 UI。 */
    private const val MAX_LEN = 400_000

    /** 标准 52 张之外（卡包生成）的牌从这里开始编号。 */
    private const val STD_DECK = 52

    // ---------------------------------------------------------------- 编码

    fun encode(s: RunState): String = buildString {
        append("v|").append(VERSION).append('\n')
        append("seed|").append(s.seed).append('\n')
        append("deck|").append(s.deckType.name).append('\n')
        append("phase|").append(s.phase.name).append('\n')
        append("ante|").append(s.ante).append('\n')
        append("bi|").append(s.blindIndex).append('\n')
        append("score|").append(s.score).append('\n')
        append("money|").append(s.money).append('\n')
        append("hl|").append(s.handsLeft).append('\n')
        append("dl|").append(s.discardsLeft).append('\n')
        append("hs|").append(s.handSize).append('\n')
        append("master|").append(s.master.joinToString(",") { cardToken(it) }).append('\n')
        append("draw|").append(s.drawPile.joinToString(",")).append('\n')
        append("disc|").append(s.discardPile.joinToString(",")).append('\n')
        append("hand|").append(s.hand.joinToString(",")).append('\n')
        append("jokers|").append(
            s.jokers.joinToString(",") { "${it.id}:${it.edition.ordinal}:${it.bonusValue}" }
        ).append('\n')
        append("cons|").append(s.consumables.joinToString(",")).append('\n')
        append("levels|").append(
            s.levels.entries.joinToString(",") { "${it.key.name}:${it.value}" }
        ).append('\n')
        append("vouchers|").append(s.vouchers.joinToString(",")).append('\n')
        append("boss|").append(s.bossSeq.joinToString(",") { it.name }).append('\n')
        append("tags|").append(s.pendingTags.joinToString(",") { it.name }).append('\n')
        append("ptypes|").append(s.playedTypes.joinToString(",") { it.name }).append('\n')
        append("shopc|").append(s.shop.cards.joinToString(",") { encodeShop(it) }).append('\n')
        append("shopb|").append(s.shop.boosters.joinToString(",") { encodeShop(it) }).append('\n')
        append("shopv|").append(s.shop.vouchers.joinToString(",") { encodeShop(it) }).append('\n')
        append("reroll|").append(s.rerollCost).append('\n')
        append("cash|").append(s.cashOut?.let { encodeCash(it) } ?: "").append('\n')
        append("hp|").append(s.handsPlayed).append('\n')
        append("best|").append(s.bestHand).append('\n')
        append("bc|").append(s.blindsCleared).append('\n')
        append("gain|").append(s.lastGain).append('\n')
        append("endless|").append(if (s.endless) 1 else 0).append('\n')
        // ---- v2 ----
        append("rr|").append(s.rerolls).append('\n')
        append("tu|").append(s.tarotsUsed).append('\n')
        append("bs|").append(s.blindsSkipped).append('\n')
        append("blk|").append(s.blindBlocked.joinToString(",")).append('\n')
        append("pil|").append(s.pillarHistory.joinToString(",")).append('\n')
        append("tc|").append(s.typeCount.entries.joinToString(",") { "${it.key}:${it.value}" }).append('\n')
        append("vc|").append(if (s.verdantCleared) 1 else 0).append('\n')
        // 传奇成长计数（v2 内追加，旧档缺这两行时按 0 处理）
        append("df|").append(s.destroyedFaces).append('\n')
        append("dt|").append(s.discardsTotal).append('\n')
        append("hsp|").append(s.handSizePenalty).append('\n')
        append("stk|").append(s.stake.name).append('\n')
        append("chl|").append(s.challenge).append('\n')
        append("pack|").append(s.pack?.let { "${it.kind.name}~${it.size.name}" } ?: "").append('\n')
        append("packc|").append(s.pack?.contents?.joinToString(",") { encodePack(it) } ?: "").append('\n')
        append("packp|").append(s.packPicked.joinToString(",")).append('\n')
        append("packr|").append(s.packReturn.name).append('\n')
        append("msg|").append(s.message.replace('\n', ' ')).append('\n')
        // v2 内追加（不升版本号）：旧档没这行，解码时补 0，卡片回退到"只显示分数"。
        append("tgt|").append(s.target).append('\n')
    }

    /**
     * 单张牌：标准牌是 `id~enh~seal~ed`，卡包生成的牌是
     * `id~enh~seal~ed~rank~suit` —— 后者不在 0..51 的格子表里，
     * 点数花色没有隐含关系，必须显式存。
     */
    private fun cardToken(c: Card): String = buildString {
        append(c.id).append('~').append(c.enh.ordinal)
        append('~').append(c.seal.ordinal)
        append('~').append(c.edition.ordinal)
        if (c.id >= STD_DECK) {
            append('~').append(c.rank.ordinal).append('~').append(c.suit.ordinal)
        }
    }

    private fun encodeShop(i: ShopItem): String = when (i) {
        is ShopItem.JokerItem ->
            "J:${i.inst.id}:${i.inst.edition.ordinal}:${i.inst.bonusValue}:${i.price}:${if (i.sold) 1 else 0}"
        is ShopItem.ConsumableItem -> "C:${i.c.key}:${i.price}:${if (i.sold) 1 else 0}"
        is ShopItem.BoosterItem ->
            "B:${i.kind.name}:${i.size.name}:${i.price}:${if (i.sold) 1 else 0}"
        is ShopItem.VoucherItem -> "V:${i.v.key}:${i.price}:${if (i.sold) 1 else 0}"
    }

    private fun encodePack(c: PackContent): String = when (c) {
        is PackContent.Tarot -> "T:${c.c.key}"
        is PackContent.Planet -> "P:${c.c.key}"
        is PackContent.Playing -> "W:${cardToken(c.card)}"
        is PackContent.Joker -> "J:${c.def.id}:${c.edition.ordinal}"
    }

    private fun encodeCash(c: CashOutDetail): String = listOf(
        c.ante, c.blindKind.ordinal, c.score, c.target, c.baseReward,
        c.handsLeft, c.handBonus, c.interest, c.handyBonus,
        if (c.doubleTag) 1 else 0, c.walletBonus, c.total,
    ).joinToString("~")

    // ---------------------------------------------------------------- 解码

    /**
     * 解析存档。
     *
     * @return 解析结果；[text] 为空或版本不可识别时返回 null（调用方视为"没有存档"）。
     */
    fun decode(text: String): RunState? {
        if (text.isBlank() || text.length > MAX_LEN) return null
        val map = HashMap<String, String>()
        text.lineSequence().forEach { line ->
            val i = line.indexOf('|')
            if (i <= 0) return@forEach
            map[line.substring(0, i)] = line.substring(i + 1)
        }
        val ver = map["v"]?.toIntOrNull() ?: return null
        if (ver > VERSION) return null

        val deckType = enumOr(map["deck"], DeckType.RED)
        val master = map["master"].orEmpty()
            .split(',')
            .filter { it.isNotBlank() }
            .mapNotNull { decodeCard(it) }
        val masterIds = master.map { it.id }.toHashSet()

        fun ids(key: String): List<Int> = map[key].orEmpty()
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in masterIds }

        val jokers = map["jokers"].orEmpty().split(',')
            .filter { it.isNotBlank() }
            .mapNotNull { decodeJoker(it) }

        val levels = HashMap<HandType, Int>()
        map["levels"].orEmpty().split(',').forEach { seg ->
            val p = seg.split(':')
            if (p.size == 2) {
                val t = HandType.entries.firstOrNull { it.name == p[0] }
                val v = p[1].toIntOrNull()
                if (t != null && v != null) levels[t] = v
            }
        }

        val typeCount = HashMap<String, Int>()
        map["tc"].orEmpty().split(',').forEach { seg ->
            val p = seg.split(':')
            if (p.size == 2) {
                val v = p[1].toIntOrNull()
                if (v != null && HandType.entries.any { it.name == p[0] }) typeCount[p[0]] = v
            }
        }

        val boss = map["boss"].orEmpty().split(',')
            .mapNotNull { name -> BossModifier.entries.firstOrNull { it.name == name } }

        // 卡包：种类 + 规格 + 内容
        val packHead = map["pack"].orEmpty().split('~')
        val pack = if (packHead.size == 2) {
            val kind = PackKind.entries.firstOrNull { it.name == packHead[0] }
            val size = PackSize.entries.firstOrNull { it.name == packHead[1] }
            if (kind != null && size != null) {
                val contents = map["packc"].orEmpty().split(',')
                    .filter { it.isNotBlank() }
                    .mapNotNull { decodePack(it) }
                BoosterPack(kind, size, contents)
            } else null
        } else null

        val shop = ShopState(
            cards = map["shopc"].orEmpty().split(',').filter { it.isNotBlank() }
                .mapNotNull { decodeShop(it) },
            boosters = map["shopb"].orEmpty().split(',').filter { it.isNotBlank() }
                .mapNotNull { decodeShop(it) },
            vouchers = map["shopv"].orEmpty().split(',').filter { it.isNotBlank() }
                .mapNotNull { decodeShop(it) },
        )

        return RunState(
            seed = map["seed"]?.toLongOrNull() ?: System.currentTimeMillis(),
            deckType = deckType,
            phase = enumOr(map["phase"], Phase.BLIND_SELECT),
            ante = (map["ante"]?.toIntOrNull() ?: 1).coerceIn(1, 99),
            blindIndex = (map["bi"]?.toIntOrNull() ?: 0).coerceIn(0, 2),
            score = map["score"]?.toLongOrNull() ?: 0,
            money = map["money"]?.toIntOrNull() ?: 4,
            handsLeft = map["hl"]?.toIntOrNull() ?: Blinds.BASE_HANDS,
            discardsLeft = map["dl"]?.toIntOrNull() ?: Blinds.BASE_DISCARDS,
            handSize = map["hs"]?.toIntOrNull() ?: Blinds.BASE_HAND_SIZE,
            master = master,
            drawPile = ids("draw"),
            discardPile = ids("disc"),
            hand = ids("hand"),
            jokers = jokers,
            consumables = map["cons"].orEmpty().split(',')
                .filter { it.isNotBlank() && Consumables.get(it) != null },
            levels = levels,
            vouchers = map["vouchers"].orEmpty().split(',').filter { it.isNotBlank() },
            bossSeq = boss,
            pendingTags = map["tags"].orEmpty().split(',')
                .mapNotNull { n -> Tag.entries.firstOrNull { it.name == n } },
            playedTypes = map["ptypes"].orEmpty().split(',')
                .mapNotNull { n -> HandType.entries.firstOrNull { it.name == n } },
            destroyed = emptyList(),
            shop = shop,
            rerollCost = map["reroll"]?.toIntOrNull() ?: 5,
            cashOut = map["cash"]?.takeIf { it.isNotBlank() }?.let { decodeCash(it) },
            handsPlayed = map["hp"]?.toIntOrNull() ?: 0,
            destroyedFaces = map["df"]?.toIntOrNull() ?: 0,
            discardsTotal = map["dt"]?.toIntOrNull() ?: 0,
            handSizePenalty = map["hsp"]?.toIntOrNull() ?: 0,
            stake = enumOr(map["stk"], Stake.WHITE),
            challenge = map["chl"].orEmpty(),
            bestHand = map["best"]?.toLongOrNull() ?: 0,
            blindsCleared = map["bc"]?.toIntOrNull() ?: 0,
            lastGain = map["gain"]?.toIntOrNull() ?: 0,
            endless = map["endless"] == "1",
            message = map["msg"].orEmpty(),
            rerolls = map["rr"]?.toIntOrNull() ?: 0,
            tarotsUsed = map["tu"]?.toIntOrNull() ?: 0,
            blindsSkipped = map["bs"]?.toIntOrNull() ?: 0,
            blindBlocked = ids("blk"),
            pillarHistory = ids("pil"),
            typeCount = typeCount,
            verdantCleared = map["vc"] == "1",
            pack = pack,
            packPicked = map["packp"].orEmpty().split(',')
                .mapNotNull { it.trim().toIntOrNull() },
            packReturn = enumOr(map["packr"], Phase.SHOP),
            target = map["tgt"]?.toLongOrNull() ?: 0,
        )
    }

    /** 单张牌：`id` / `id~enh~seal~ed` / `id~enh~seal~ed~rank~suit`。 */
    private fun decodeCard(token: String): Card? {
        val p = token.split('~')
        val id = p.getOrNull(0)?.toIntOrNull() ?: return null
        val enh = Enhancement.entries.getOrNull(p.getOrNull(1)?.toIntOrNull() ?: 0) ?: Enhancement.NONE
        val seal = Seal.entries.getOrNull(p.getOrNull(2)?.toIntOrNull() ?: 0) ?: Seal.NONE
        val ed = Edition.entries.getOrNull(p.getOrNull(3)?.toIntOrNull() ?: 0) ?: Edition.BASE
        return if (id in 0 until STD_DECK) {
            // 标准 52 张：点数花色由 id 反推，不用存
            Card(id, Rank.entries[id % 13], Suit.entries[id / 13], enh, seal, ed)
        } else {
            // 卡包生成的牌：必须显式读点数花色
            val rank = Rank.entries.getOrNull(p.getOrNull(4)?.toIntOrNull() ?: -1) ?: return null
            val suit = Suit.entries.getOrNull(p.getOrNull(5)?.toIntOrNull() ?: -1) ?: return null
            Card(id, rank, suit, enh, seal, ed)
        }
    }

    private fun decodeJoker(seg: String): JokerInst? {
        val p = seg.split(':')
        val def = Jokers.get(p[0]) ?: return null
        val ed = Edition.entries.getOrNull(p.getOrNull(1)?.toIntOrNull() ?: 0) ?: Edition.BASE
        val bonus = p.getOrNull(2)?.toIntOrNull() ?: 0
        return JokerInst(def, ed, bonus)
    }

    private fun decodePack(seg: String): PackContent? {
        val p = seg.split(':')
        return when (p.getOrNull(0)) {
            "T" -> Consumables.get(p.getOrNull(1).orEmpty())?.let { PackContent.Tarot(it) }
            "P" -> Consumables.get(p.getOrNull(1).orEmpty())?.let { PackContent.Planet(it) }
            "W" -> p.getOrNull(1)?.let { decodeCard(it) }?.let { PackContent.Playing(it) }
            "J" -> {
                val def = Jokers.get(p.getOrNull(1).orEmpty()) ?: return null
                val ed = Edition.entries.getOrNull(p.getOrNull(2)?.toIntOrNull() ?: 0) ?: Edition.BASE
                PackContent.Joker(def, ed)
            }
            else -> null
        }
    }

    private fun decodeShop(seg: String): ShopItem? {
        val p = seg.split(':')
        return when (p.getOrNull(0)) {
            "J" -> {
                val def = Jokers.get(p.getOrNull(1).orEmpty()) ?: return null
                val ed = Edition.entries.getOrNull(p.getOrNull(2)?.toIntOrNull() ?: 0) ?: Edition.BASE
                val bonus = p.getOrNull(3)?.toIntOrNull() ?: 0
                ShopItem.JokerItem(
                    JokerInst(def, ed, bonus),
                    p.getOrNull(4)?.toIntOrNull() ?: def.price,
                    p.getOrNull(5) == "1",
                )
            }
            "C" -> {
                val c = Consumables.get(p.getOrNull(1).orEmpty()) ?: return null
                ShopItem.ConsumableItem(c, p.getOrNull(2)?.toIntOrNull() ?: c.price, p.getOrNull(3) == "1")
            }
            "B" -> {
                val k = PackKind.entries.firstOrNull { it.name == p.getOrNull(1) } ?: return null
                val s = PackSize.entries.firstOrNull { it.name == p.getOrNull(2) } ?: return null
                ShopItem.BoosterItem(k, s, p.getOrNull(3)?.toIntOrNull() ?: s.price, p.getOrNull(4) == "1")
            }
            "V" -> {
                val v = Voucher.entries.firstOrNull { it.key == p.getOrNull(1) } ?: return null
                ShopItem.VoucherItem(v, p.getOrNull(2)?.toIntOrNull() ?: v.price, p.getOrNull(3) == "1")
            }
            else -> null
        }
    }

    private fun decodeCash(s: String): CashOutDetail? {
        val n = s.split('~').mapNotNull { it.toIntOrNull() }
        if (n.size < 12) return null
        return CashOutDetail(
            ante = n[0],
            blindKind = BlindKind.entries.getOrNull(n[1]) ?: BlindKind.SMALL,
            score = n[2].toLong(),
            target = n[3].toLong(),
            baseReward = n[4],
            handsLeft = n[5],
            handBonus = n[6],
            interest = n[7],
            handyBonus = n[8],
            doubleTag = n[9] == 1,
            walletBonus = n[10],
            total = n[11],
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}
