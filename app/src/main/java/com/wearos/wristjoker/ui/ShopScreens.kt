package com.wearos.wristjoker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wearos.wristjoker.game.Consumable
import com.wearos.wristjoker.game.GameUi
import com.wearos.wristjoker.game.JokerFlavor
import com.wearos.wristjoker.game.JokerInst
import com.wearos.wristjoker.game.PackContent
import com.wearos.wristjoker.game.PackView
import com.wearos.wristjoker.game.Rarity
import com.wearos.wristjoker.game.ShopItem
import kotlinx.coroutines.delay

private const val SAFE = 122

/* ------------------------------------------------------------------ *
 * 圆屏商店：四段同屏
 * ------------------------------------------------------------------ */

/**
 * 商店的六条横带，宽度同样由 [CircleFit] 按弦长算。
 *
 * 上一版把内容塞进 122dp 宽的 `LazyColumn`，然后把「商品 / 卡包 / 券 / 我的」
 * 拆成四个分页 —— 玩家反馈的「商店里少了很多东西，例如卡包」，真正的原因
 * 就是卡包被藏在第二页：它一直存在，但没人知道要点那个标签。
 */
internal val SHOP_BANDS = listOf(
    CircleFit.Band(12f, 2f),   // 0 顶栏
    CircleFit.Band(12f, 2f),   // 1 已拥有（小丑 + 消耗品）
    CircleFit.Band(34f, 3f),   // 2 商品位
    CircleFit.Band(30f, 3f),   // 3 补充包
    CircleFit.Band(26f, 2f),   // 4 代金券 ×2
    CircleFit.Band(20f, 0f),   // 5 重掷 / 继续
)

/** 商店底色：和牌桌同一套圆形毡面，只是更冷一档（"在店里"）。 */
@Composable
private fun ShopBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val r = (CircleFit.RADIUS - 1f).dp.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        drawRect(Color.Black)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Pal.FeltLight.copy(alpha = .30f), Pal.FeltDark),
                center = Offset(c.x, c.y * .75f),
                radius = r * 1.1f,
            ),
            radius = r,
            center = c,
        )
        for (f in listOf(.94f, .70f)) {
            drawCircle(Pal.FeltHi.copy(alpha = .09f), r * f, c, style = Stroke(.7.dp.toPx()))
        }
        drawCircle(Pal.GoldDim.copy(alpha = .45f), r, c, style = Stroke(1.dp.toPx()))
    }
}

/** 已拥有：小丑按稀有度着色 + 消耗品，点一下进详情（可卖 / 可用）。 */
@Composable
private fun OwnedBar(
    ui: GameUi,
    width: Float,
    height: Float,
    onOpenDetail: (DetailTarget) -> Unit,
) {
    Row(
        Modifier.width(width.dp).height(height.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        T(
            "丑${ui.jokers.size}/${ui.maxJokers}",
            Type.TinyMono.copy(
                color = if (ui.jokers.size >= ui.maxJokers) Pal.BossRed else Pal.Money,
                fontSize = 6.sp,
            ),
        )
        ui.jokers.forEachIndexed { i, j ->
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(rarityColorOf(j.rarity).copy(alpha = .88f))
                    .border(.6.dp, Pal.CardFace.copy(alpha = .45f), RoundedCornerShape(1.dp))
                    .clickable { onOpenDetail(DetailTarget.OwnedJoker(i, j)) },
            )
        }
        if (ui.consumables.isNotEmpty()) {
            Spacer(Modifier.width(3.dp))
            Box(Modifier.width(.6.dp).height(8.dp).background(Pal.FeltHi.copy(alpha = .45f)))
            Spacer(Modifier.width(3.dp))
            ui.consumables.forEachIndexed { i, c ->
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Pal.FeltDark)
                        .border(.6.dp, Pal.Purple, RoundedCornerShape(1.dp))
                        .clickable { onOpenDetail(DetailTarget.OwnedConsumable(i, c)) },
                ) {
                    ConsumableArt(c, Modifier.fillMaxSize())
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // 「查看」不再是一行暗字提示，而是一个金色可点的入口：点它等价于
        // 点第一个已拥有的物品，玩家不用先猜到「前面那些色块其实能点」。
        Box(
            Modifier
                .clip(RoundedCornerShape(2.dp))
                .clickable {
                    when {
                        ui.jokers.isNotEmpty() ->
                            onOpenDetail(DetailTarget.OwnedJoker(0, ui.jokers[0]))
                        ui.consumables.isNotEmpty() ->
                            onOpenDetail(DetailTarget.OwnedConsumable(0, ui.consumables[0]))
                    }
                }
                .padding(horizontal = 3.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            T("查看 ›", Type.TinyMono.copy(color = Pal.Money, fontSize = 6.sp))
        }
    }
}

/** 一段商品：左侧单字标签 + 一排并排的格子。 */
@Composable
private fun ShopSection(
    label: String,
    items: List<ShopItem>,
    section: Int,
    width: Float,
    height: Float,
    money: Int,
    empty: String,
    onOpenDetail: (DetailTarget) -> Unit,
    locked: Set<Int> = emptySet(),
) {
    Row(
        Modifier.width(width.dp).height(height.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .width(10.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(Pal.Surface.copy(alpha = .55f))
                .border(.6.dp, Pal.FeltHi.copy(alpha = .45f), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            T(label, Type.TinyMono.copy(color = Pal.Dim, fontSize = 5.8.sp))
        }
        if (items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                T(empty, Type.Tiny.copy(color = Pal.Dim, fontSize = 6.sp))
            }
            return@Row
        }
        // 3 格以内横向排（图标在左、名字在右）；4 格以上改成竖排紧凑格
        val wide = items.size <= 3
        items.forEachIndexed { i, item ->
            ShopTile(
                item = item,
                affordable = money >= item.price,
                wide = wide,
                onClick = { onOpenDetail(DetailTarget.ShopEntry(section, i, item)) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                locked = section == 0 && i in locked,
            )
        }
    }
}

/** 一格商品。价签永远在最上面 —— 对应原版竖卡顶端的 price tag。 */
@Composable
private fun ShopTile(
    item: ShopItem,
    affordable: Boolean,
    wide: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
) {
    val edge = when {
        item.sold -> Pal.FeltHi
        locked -> Pal.Chip
        item is ShopItem.JokerItem -> rarityColorOf(item.inst.rarity)
        item is ShopItem.ConsumableItem -> Pal.Purple
        item is ShopItem.BoosterItem -> Pal.Chip
        else -> Pal.Gold
    }
    val name = when (item) {
        is ShopItem.JokerItem -> item.inst.name
        is ShopItem.ConsumableItem -> item.c.name
        is ShopItem.BoosterItem -> item.kind.cn
        is ShopItem.VoucherItem -> item.v.cn
    }
    val priced = item is ShopItem.BoosterItem || item is ShopItem.VoucherItem

    Box(
        modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (item.sold) Pal.Surface.copy(alpha = .40f) else Pal.Surface)
            .border(1.dp, edge, RoundedCornerShape(3.dp))
            .clickable(enabled = !item.sold, onClick = onClick)
            .padding(1.5.dp),
    ) {
        if (locked) {
            // 锁角标：2dp 的小方块，比画一把锁更能在 6dp 高度里认出来
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(4.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Pal.Chip),
            )
        }
        if (wide) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .border(.7.dp, Pal.CardEdge, RoundedCornerShape(2.dp)),
                ) { ItemArt(item) }
                Spacer(Modifier.width(2.dp))
                Column(Modifier.weight(1f)) {
                    PriceTag(item, affordable)
                    Spacer(Modifier.height(.5.dp))
                    T(
                        name.take(4),
                        Type.Body.copy(
                            fontSize = 6.8.sp,
                            color = if (item.sold) Pal.Dim else Pal.Cream,
                        ),
                    )
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PriceTag(item, affordable)
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(vertical = .5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(15.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .border(.6.dp, Pal.CardEdge, RoundedCornerShape(2.dp)),
                    ) { ItemArt(item) }
                }
                if (!priced) T(name.take(2), Type.Tiny.copy(fontSize = 5.sp, color = Pal.Dim))
            }
        }
    }
}

@Composable
private fun PriceTag(item: ShopItem, affordable: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(
                when {
                    item.sold -> Pal.FeltHi
                    affordable -> Pal.Money
                    else -> Pal.SurfaceHigh
                }
            )
            .border(.5.dp, Pal.FeltDark, RoundedCornerShape(2.dp))
            .padding(horizontal = 2.dp, vertical = .3.dp),
    ) {
        T(
            if (item.sold) "售罄" else "$${item.price}",
            Type.TinyMono.copy(
                color = when {
                    item.sold -> Pal.Dim
                    affordable -> Pal.FeltDark
                    else -> Pal.Mult
                },
                fontSize = 5.6.sp,
            ),
        )
    }
}

@Composable
private fun ItemArt(item: ShopItem) {
    when (item) {
        is ShopItem.JokerItem -> JokerPortrait(item.inst.id, Modifier.fillMaxSize())
        is ShopItem.ConsumableItem -> ConsumableArt(item.c, Modifier.fillMaxSize())
        is ShopItem.BoosterItem -> BoosterArt(item.kind, item.size, Modifier.fillMaxSize())
        is ShopItem.VoucherItem -> VoucherArt(item.v, Modifier.fillMaxSize())
    }
}

/**
 * 详情页要展示的对象。
 *
 * 把「商店里的一格」和「自己已经有的」分成两种 —— 前者底部是"买入"，
 * 后者底部是"卖出"，并且卖出需要二次确认。
 */
sealed interface DetailTarget {
    /** 商店里的一格。[section] 0=商品 1=卡包 2=代金券。 */
    data class ShopEntry(val section: Int, val index: Int, val item: ShopItem) : DetailTarget

    /** 已拥有的小丑。 */
    data class OwnedJoker(val index: Int, val joker: JokerInst) : DetailTarget

    /** 已拥有的消耗品。 */
    data class OwnedConsumable(val index: Int, val c: Consumable) : DetailTarget
}

/* ------------------------------------------------------------------ *
 * 商店
 * ------------------------------------------------------------------ */

/**
 * 商店。
 *
 * 结构照原版一屏摊开：**2 格商品 + 2 个补充包 + 2 张代金券 + 自己的小丑/消耗品**。
 *
 * 前两版分别踩了同一个坑的两面：
 * - 只放 3 格商品、偶尔一张券 —— 内容不全；
 * - 把内容拆成「商品 / 卡包 / 券 / 我的」四个分页 —— 内容全了，但**看不见**。
 *   玩家反馈"商店少了卡包"，其实卡包一直在，只是躲在第二个标签后面。
 *
 * 现在靠 [CircleFit] 的弦长把中间那一带撑到 190dp 以上，四段一次摊开，
 * 每段只是变矮，没有一段需要翻页才能看到。
 *
 * 交互仍旧是**点一下看详情、详情里再买**：一行格子最多写 3 个字，
 * 「这张小丑到底干什么」在原版靠 hover tooltip，手表上没有 hover。
 */
@Composable
fun ShopScreen(
    ui: GameUi,
    onOpenDetail: (DetailTarget) -> Unit,
    onReroll: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bands = SHOP_BANDS
    val w = CircleFit.widths(bands)

    Box(modifier.fillMaxSize()) {
        ShopBackdrop()
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶栏：店名 / 重掷价 / 钱
            Row(
                Modifier.width(w[0].dp).height(bands[0].h.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T("商店", Type.Medium.copy(fontSize = 10.sp, color = Pal.Money))
                // 顶栏中间显示"第几关"，不再重复按钮上的重掷价
                T(
                    "第${ui.ante}关" + if (ui.endless) "∞" else "",
                    Type.TinyMono.copy(color = Pal.Dim, fontSize = 5.8.sp),
                )
                CountBadge("$${ui.money}", Pal.Money)
            }

            Spacer(Modifier.height(bands[0].gap.dp))

            // 已拥有：原版商店顶部固定这一栏，随时能判断还剩几个空位
            OwnedBar(
                ui = ui,
                width = w[1],
                height = bands[1].h,
                onOpenDetail = onOpenDetail,
            )

            Spacer(Modifier.height(bands[1].gap.dp))

            // 商品位（小丑 / 塔罗 / 行星）
            ShopSection(
                label = "商",
                items = ui.shop.cards,
                section = 0,
                width = w[2],
                height = bands[2].h,
                money = ui.money,
                empty = "这一格空了 · 重掷试试",
                onOpenDetail = onOpenDetail,
                locked = ui.shop.locked,
            )

            Spacer(Modifier.height(bands[2].gap.dp))

            // 补充包（5 类 × 3 规格）
            ShopSection(
                label = "包",
                items = ui.shop.boosters,
                section = 1,
                width = w[3],
                height = bands[3].h,
                money = ui.money,
                empty = "包已售罄",
                onOpenDetail = onOpenDetail,
            )

            Spacer(Modifier.height(bands[3].gap.dp))

            // 代金券 ×2
            ShopSection(
                label = "券",
                items = ui.shop.vouchers,
                section = 2,
                width = w[4],
                height = bands[4].h,
                money = ui.money,
                empty = "打完 Boss 补货",
                onOpenDetail = onOpenDetail,
            )

            Spacer(Modifier.height(bands[4].gap.dp))

            Row(
                Modifier.width(w[5].dp).height(bands[5].h.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                CasinoButton(
                    label = if (ui.rerollCost == 0) "重掷 免费" else "重掷 \$${ui.rerollCost}",
                    enabled = ui.money >= ui.rerollCost,
                    accent = Pal.Chip,
                    onClick = onReroll,
                    modifier = Modifier.weight(1f),
                    filled = false,
                    height = 20,
                )
                CasinoButton(
                    label = "继续",
                    enabled = true,
                    accent = Pal.Money,
                    onClick = onLeave,
                    modifier = Modifier.weight(1f),
                    height = 20,
                )
            }
        }
    }
}

/**
 * 原版商店顶部的"已拥有小丑"栏：实心色块按稀有度着色，暗框是空槽，
 * 左侧写 `x/max`。这是原版商店 HUD 最显眼的一块 —— 一屏之内就能看到
 * 自己带了哪几张、还剩几个位置，决定这一轮要不要买新小丑。
 */
@Composable
private fun OwnedJokerBar(
    jokers: List<JokerInst>,
    maxSlots: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.height(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        T(
            "丑 ${jokers.size}/$maxSlots",
            Type.TinyMono.copy(
                color = if (jokers.size >= maxSlots) Pal.BossRed else Pal.Money,
                fontSize = 6.sp,
            ),
        )
        Spacer(Modifier.width(2.dp))
        for (i in 0 until maxSlots.coerceAtMost(8)) {
            val j = jokers.getOrNull(i)
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (j != null) rarityColorOf(j.rarity)
                        else Pal.FeltDark.copy(alpha = .5f)
                    )
                    .border(
                        0.6.dp,
                        if (j != null) Pal.CardFace.copy(alpha = .4f)
                        else Pal.FeltHi.copy(alpha = .35f),
                        RoundedCornerShape(1.dp),
                    ),
            )
        }
    }
}

/** 分页标签。选中时填金色，未选中只有描边。 */
@Composable
private fun TabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .height(13.dp)
            .clip(shape)
            .background(if (selected) Pal.Money else Pal.Surface)
            .border(0.8.dp, if (selected) Pal.Money else Pal.FeltHi, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        T(
            label,
            Type.TinyMono.copy(
                color = if (selected) Pal.FeltDark else Pal.Dim,
                fontSize = 6.sp,
            ),
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    T(
        text,
        Type.Tiny.copy(color = Pal.Dim, fontSize = 6.sp),
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** 一格商品。图标是唯一的类型标识：小丑头像 / 消耗品卡 / 包 / 金色票。 */
@Composable
private fun ShopRow(
    item: ShopItem,
    affordable: Boolean,
    onClick: () -> Unit,
    locked: Boolean = false,
) {
    val shape = RoundedCornerShape(3.dp)
    val edge = when {
        item.sold -> Pal.FeltHi
        locked -> Pal.Chip
        item is ShopItem.JokerItem -> rarityColorOf(item.inst.rarity)
        item is ShopItem.ConsumableItem -> Pal.Purple
        item is ShopItem.BoosterItem -> Pal.Chip
        else -> Pal.Gold
    }
    val name = when (item) {
        is ShopItem.JokerItem -> item.inst.name + item.inst.editionLabel
        is ShopItem.ConsumableItem -> item.c.name
        is ShopItem.BoosterItem -> "${item.size.cn}${item.kind.cn}"
        is ShopItem.VoucherItem -> item.v.cn
    }
    val desc = when (item) {
        is ShopItem.JokerItem -> item.inst.desc
        is ShopItem.ConsumableItem ->
            if (item.c.needsCards) "${item.c.desc} · 需选牌" else item.c.desc
        is ShopItem.BoosterItem ->
            "${item.size.contentCount(item.kind)} 选 ${item.size.picks} · ${item.kind.en}"
        is ShopItem.VoucherItem -> item.v.desc
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(shape)
            .background(if (item.sold) Pal.Surface.copy(alpha = .5f) else Pal.Surface)
            .border(1.1.dp, edge, shape)
            .clickable(enabled = !item.sold, onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 1.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .border(0.8.dp, Pal.CardEdge, RoundedCornerShape(2.dp)),
            ) {
                when (item) {
                    is ShopItem.JokerItem -> JokerPortrait(item.inst.id, Modifier.fillMaxSize())
                    is ShopItem.ConsumableItem -> ConsumableArt(item.c, Modifier.fillMaxSize())
                    is ShopItem.BoosterItem ->
                        BoosterArt(item.kind, item.size, Modifier.fillMaxSize())
                    is ShopItem.VoucherItem -> VoucherArt(item.v, Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(3.dp))
            Column(Modifier.weight(1f)) {
                T(
                    name,
                    Type.Body.copy(fontSize = 8.5.sp, color = if (item.sold) Pal.Dim else Pal.Cream),
                )
                Spacer(Modifier.height(1.dp))
                T(desc, Type.Tiny.copy(color = Pal.Dim, fontSize = 6.sp))
            }
            Spacer(Modifier.width(2.dp))
            // 详情入口的暗示：一个小箭头。手表上没有 hover，必须明示"能点开"
            T("›", Type.TinyMono.copy(color = Pal.FeltHi, fontSize = 9.sp))
            Spacer(Modifier.width(2.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (item.sold) Pal.FeltHi else Pal.Money)
                    // 价签：金色底 + 深色描边，做成"贴在这张商品上的标签"，
                    // 对应原版竖卡片顶部的 price tag（圆屏上折到行尾）。
                    .border(0.6.dp, Pal.FeltDark, RoundedCornerShape(2.dp))
                    .padding(horizontal = 2.dp, vertical = 0.5.dp),
            ) {
                T(
                    if (item.sold) "售罄" else "$${item.price}",
                    Type.TinyMono.copy(
                        color = if (item.sold || !affordable) Pal.FeltDark else Pal.FeltDark,
                        fontSize = 6.2.sp,
                    ),
                )
            }
        }
    }
}

/** 已拥有的小丑行：单行 + 卖价提示，点开才进详情。 */
@Composable
private fun OwnedJokerRow(j: JokerInst, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Pal.Surface.copy(alpha = .65f))
            .border(0.9.dp, rarityColorOf(j.rarity).copy(alpha = .8f), RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(18.dp).clip(RoundedCornerShape(2.dp))
                    .border(0.7.dp, Pal.CardEdge, RoundedCornerShape(2.dp)),
            ) {
                JokerPortrait(j.id, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(3.dp))
            Column {
                T(j.name, Type.Body.copy(fontSize = 7.5.sp, color = Pal.Cream))
                T(j.desc, Type.Tiny.copy(color = Pal.Dim, fontSize = 5.6.sp))
            }
            Spacer(Modifier.weight(1f))
            T("卖 \$${j.sellPrice}", Type.TinyMono.copy(color = Pal.Money, fontSize = 6.sp))
        }
    }
}

/* ------------------------------------------------------------------ *
 * 开包
 * ------------------------------------------------------------------ */

private val PACK = roundLayout(
    CircleFit.Band(12f, 3f),
    CircleFit.Band(78f, 3f),
    CircleFit.Band(22f, 0f),
)

private val DETAIL = roundLayout(
    CircleFit.Band(42f, 2f),   // 0 大图
    CircleFit.Band(12f, 1f),   // 1 标题
    CircleFit.Band(8f, 3f),    // 2 副标
    CircleFit.Band(44f, 4f),   // 3 正文
    CircleFit.Band(26f, 2f),   // 4 动作
    CircleFit.Band(8f, 0f),    // 5 提示
)

/**
 * 开包界面。
 *
 * 原版买下补充包会立刻离开商店、把包里的牌摊开供选择，选满（或跳过）
 * 再回到商店。这里保留这个流程 —— 它是"买东西 → 立刻看到实物"的
 * 那条反馈链，砍掉就变成"买了一堆不知道是什么的东西"。
 *
 * 塔罗需要选牌才能用，包内没法再套一层选牌界面，所以选中的塔罗会
 * **进消耗品槽**；行星不需要选牌，选中即升级。
 */
@Composable
fun PackScreen(
    ui: GameUi,
    onToggle: (Int) -> Unit,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pack = ui.pack ?: return
    RoundPage(PACK, modifier) { L ->
        BandRow(L.w[0], L.h[0]) {
            T(pack.title, Type.Medium.copy(fontSize = 9.5.sp, color = Pal.Money))
            T(
                "已选 ${pack.picked.size}/${pack.maxPicks}",
                Type.TinyMono.copy(
                    color = if (pack.picked.size == pack.maxPicks) Pal.Green else Pal.Dim,
                    fontSize = 6.5.sp,
                ),
            )
        }
        BandGap(L.gap[0])

        BandRow(L.w[1], L.h[1], horizontalArrangement = Arrangement.Center) {
            LazyColumn(
                Modifier.fillMaxWidth().height(L.h[1].dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(pack.contents) { i, c ->
                    PackRow(
                        content = c,
                        picked = i in pack.picked,
                        onClick = { onToggle(i) },
                    )
                }
            }
        }
        BandGap(L.gap[1])

        BandRow(L.w[2], L.h[2], horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CasinoButton(
                label = "跳过",
                enabled = true,
                accent = Pal.Chip,
                onClick = onSkip,
                modifier = Modifier.weight(1f),
                filled = false,
                height = 20,
            )
            CasinoButton(
                label = if (pack.picked.isEmpty()) "选一张" else "拿走 ${pack.picked.size}",
                enabled = pack.picked.isNotEmpty(),
                accent = Pal.Money,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                height = 20,
            )
        }
    }
}

@Composable
private fun PackRow(content: PackContent, picked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(3.dp)
    val accent = when (content) {
        is PackContent.Tarot -> Pal.Purple
        is PackContent.Planet -> Pal.Chip
        is PackContent.Playing -> Pal.CardFace
        is PackContent.Joker -> rarityColorOf(content.def.rarity)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(shape)
            .background(if (picked) Pal.SurfaceHigh else Pal.Surface)
            .border(if (picked) 1.6.dp else 1.dp, if (picked) Pal.Money else accent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp),
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(18.dp).clip(RoundedCornerShape(2.dp))
                    .border(0.8.dp, Pal.CardEdge, RoundedCornerShape(2.dp)),
            ) {
                when (content) {
                    is PackContent.Tarot -> ConsumableArt(content.c, Modifier.fillMaxSize())
                    is PackContent.Planet -> ConsumableArt(content.c, Modifier.fillMaxSize())
                    is PackContent.Playing -> CardFaceArt(content.card, Modifier.fillMaxSize())
                    is PackContent.Joker -> JokerPortrait(content.def.id, Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(3.dp))
            Column(Modifier.weight(1f)) {
                val (title, sub) = when (content) {
                    is PackContent.Tarot -> content.c.name to "塔罗 · ${content.c.desc}"
                    is PackContent.Planet -> content.c.name to "行星 · ${content.c.desc}"
                    is PackContent.Playing ->
                        content.card.toString() to playingSub(content.card)
                    is PackContent.Joker ->
                        (content.def.name + content.edition.short) to content.def.desc
                }
                T(title, Type.Body.copy(fontSize = 8.sp, color = Pal.Cream))
                T(sub, Type.Tiny.copy(color = Pal.Dim, fontSize = 5.6.sp))
            }
            if (picked) {
                T("✓", Type.TinyMono.copy(color = Pal.Money, fontSize = 10.sp))
            }
        }
    }
}

/** 标准包里那张牌的卖点：增强 / 蜡封 / 版本，有什么写什么。 */
private fun playingSub(c: com.wearos.wristjoker.game.Card): String {
    val parts = mutableListOf<String>()
    if (!c.enh.isNone) parts += c.enh.cn + c.enh.short
    if (!c.seal.isNone) parts += c.seal.cn
    if (!c.edition.isBase) parts += c.edition.cn
    return if (parts.isEmpty()) "加入牌堆" else parts.joinToString(" · ")
}

/* ------------------------------------------------------------------ *
 * 详情页
 * ------------------------------------------------------------------ */

/**
 * 通用详情页。
 *
 * 三个来源共用一套版式：商店商品（底部"买入"）、已拥有小丑（底部"卖出"）、
 * 已拥有消耗品（底部"使用"）。
 *
 * **卖出必须二次确认**：上一版点一下徽章就卖掉，手表上手指一蹭就亏一半钱。
 * 现在第一次点变成"再点确认"，2 秒没点就退回 —— 这个交互在原版里不存在
 * （原版用右键/拖拽），但在 40dp 见方的触摸目标上它是必要的。
 */
@Composable
fun DetailOverlay(
    target: DetailTarget,
    money: Int,
    onBuy: (DetailTarget) -> Unit,
    onSell: (DetailTarget) -> Unit,
    onUse: (DetailTarget) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** 商店里被锁住的商品位；锁只对"商品"区（section 0）有意义。 */
    lockedShop: Set<Int> = emptySet(),
    onLock: (Int) -> Unit = {},
    onSellConsumable: (Int) -> Unit = {},
) {
    var confirming by remember(target) { mutableStateOf(false) }
    var tick by remember(target) { mutableIntStateOf(0) }

    LaunchedEffect(confirming, tick) {
        if (confirming) {
            delay(2200)
            confirming = false
        }
    }

    // 不同类型的目标 → 标题 / 副标 / 正文 / 图标
    val kind = when (target) {
        is DetailTarget.ShopEntry -> target.item
        else -> null
    }
    val title: String
    val subtitle: String
    val body: String
    val accent: Color
    /** 小丑的台词：不参与计算，只让这张牌像个人。 */
    val flavor: String?
    when (target) {
        is DetailTarget.ShopEntry -> {
            val it0 = target.item
            title = when (it0) {
                is ShopItem.JokerItem -> it0.inst.name
                is ShopItem.ConsumableItem -> it0.c.name
                is ShopItem.BoosterItem -> "${it0.size.cn}${it0.kind.cn}"
                is ShopItem.VoucherItem -> it0.v.cn
            }
            subtitle = when (it0) {
                is ShopItem.JokerItem ->
                    "${it0.inst.rarity.cn}${if (it0.inst.edition.isBase) "" else " · ${it0.inst.edition.cn}"} · 卖 \$${it0.inst.sellPrice}"
                is ShopItem.ConsumableItem -> "${it0.c.kind.cn} · ${it0.c.en}"
                is ShopItem.BoosterItem -> "${it0.size.contentCount(it0.kind)} 张里选 ${it0.size.picks} 张"
                is ShopItem.VoucherItem -> it0.v.en
            }
            body = when (it0) {
                is ShopItem.JokerItem -> it0.inst.desc
                is ShopItem.ConsumableItem -> it0.c.desc
                is ShopItem.BoosterItem -> boosterBody(it0)
                is ShopItem.VoucherItem -> it0.v.desc
            }
            flavor = (it0 as? ShopItem.JokerItem)?.inst?.id?.let { JokerFlavor.of(it) }
            accent = when (it0) {
                is ShopItem.JokerItem -> rarityColorOf(it0.inst.rarity)
                is ShopItem.ConsumableItem -> Pal.Purple
                is ShopItem.BoosterItem -> Pal.Chip
                is ShopItem.VoucherItem -> Pal.Gold
            }
        }
        is DetailTarget.OwnedJoker -> {
            title = target.joker.name
            subtitle = "${target.joker.rarity.cn} · 卖 \$${target.joker.sellPrice}"
            body = target.joker.desc
            flavor = JokerFlavor.of(target.joker.id)
            accent = rarityColorOf(target.joker.rarity)
        }
        is DetailTarget.OwnedConsumable -> {
            title = target.c.name
            subtitle = "${target.c.kind.cn} · ${target.c.en}"
            body = target.c.desc + if (target.c.needsCards) "（需先在手牌上选牌）" else ""
            flavor = null
            accent = Pal.Purple
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Pal.FeltDark.copy(alpha = .96f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        RoundPage(DETAIL) { L ->
            // 大图
            BandRow(L.w[0], L.h[0], horizontalArrangement = Arrangement.Center) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(4.dp))
                        .background(Pal.Surface)
                        .border(1.4.dp, accent, RoundedCornerShape(4.dp)),
                ) {
                    when (target) {
                        is DetailTarget.ShopEntry -> when (val it0 = target.item) {
                            is ShopItem.JokerItem -> JokerPortrait(it0.inst.id, Modifier.fillMaxSize())
                            is ShopItem.ConsumableItem -> ConsumableArt(it0.c, Modifier.fillMaxSize())
                            is ShopItem.BoosterItem -> BoosterArt(it0.kind, it0.size, Modifier.fillMaxSize())
                            is ShopItem.VoucherItem -> VoucherArt(it0.v, Modifier.fillMaxSize())
                        }
                        is DetailTarget.OwnedJoker -> JokerPortrait(target.joker.id, Modifier.fillMaxSize())
                        is DetailTarget.OwnedConsumable -> ConsumableArt(target.c, Modifier.fillMaxSize())
                    }
                }
            }
            BandGap(L.gap[0])

            BandRow(L.w[1], L.h[1], horizontalArrangement = Arrangement.Center) {
                T(title, Type.Medium.copy(fontSize = 11.sp, color = Pal.Cream))
            }
            BandGap(L.gap[1])

            BandRow(L.w[2], L.h[2], horizontalArrangement = Arrangement.Center) {
                T(subtitle, Type.TinyMono.copy(color = accent, fontSize = 6.5.sp))
            }
            BandGap(L.gap[2])

            // 正文：宽度跟着圆走，最多 4 行。上一版固定在 122dp、只给 3 行，
            // 稍长一点的效果描述（幻灵牌尤甚）会被截断成半句话。
            BandRow(L.w[3], L.h[3], horizontalArrangement = Arrangement.Center) {
                T(
                    body,
                    Type.Tiny.copy(color = Pal.Cream.copy(alpha = .92f), fontSize = 7.5.sp),
                    modifier = Modifier.fillMaxWidth().height(L.h[3].dp),
                    align = TextAlign.Center,
                    maxLines = 4,
                    softWrap = true,
                )
            }
            BandGap(L.gap[3])

            // 底部动作
            BandRow(L.w[4], L.h[4], horizontalArrangement = Arrangement.Center) {
                when (target) {
                    is DetailTarget.ShopEntry -> {
                        val it0 = target.item
                        val canBuy = !it0.sold && money >= it0.price
                        val lockable = target.section == 0 && !it0.sold
                        val locked = target.index in lockedShop
                        Row(
                            Modifier.width(L.w[4].dp).height(24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CasinoButton(
                                label = when {
                                    it0.sold -> "已售出"
                                    money < it0.price -> "差 \$${it0.price - money}"
                                    else -> "买入 \$${it0.price}"
                                },
                                enabled = canBuy,
                                accent = Pal.Money,
                                onClick = { onBuy(target) },
                                modifier = Modifier.width((L.w[4] * if (lockable) .38f else .52f).dp),
                                height = 24,
                            )
                            if (lockable) {
                                Spacer(Modifier.width(5.dp))
                                // 锁：只对商品区开放。锁住之后重掷不会换掉这一格，
                                // 是"攒钱买贵牌"唯一的抓手（原版收费 $1，这里免了）。
                                CasinoButton(
                                    label = if (locked) "解锁" else "锁定",
                                    enabled = true,
                                    accent = Pal.Chip,
                                    onClick = { onLock(target.index) },
                                    modifier = Modifier.width((L.w[4] * .3f).dp),
                                    filled = locked,
                                    height = 24,
                                )
                            }
                        }
                    }
                    is DetailTarget.OwnedJoker -> {
                        CasinoButton(
                            label = if (confirming) "再点确认卖出" else "卖出 +\$${target.joker.sellPrice}",
                            enabled = true,
                            accent = if (confirming) Pal.Mult else Pal.Chip,
                            onClick = {
                                if (confirming) onSell(target) else { confirming = true; tick++ }
                            },
                            modifier = Modifier.width((L.w[4] * .52f).dp),
                            filled = confirming,
                            height = 24,
                        )
                    }
                    is DetailTarget.OwnedConsumable -> {
                        Row(
                            Modifier.width(L.w[4].dp).height(24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CasinoButton(
                                label = "使用",
                                enabled = true,
                                accent = Pal.Purple,
                                onClick = { onUse(target) },
                                modifier = Modifier.width((L.w[4] * .38f).dp),
                                height = 24,
                            )
                            Spacer(Modifier.width(5.dp))
                            // 卖消耗品：手表上塔罗/行星买多了会占满两个槽，
                            // 原版能卖，这里之前只能"用掉或占着"。
                            CasinoButton(
                                label = if (confirming) "确认 +\$1" else "卖掉",
                                enabled = true,
                                accent = Pal.Chip,
                                onClick = {
                                    if (confirming) onSellConsumable(target.index)
                                    else { confirming = true; tick++ }
                                },
                                modifier = Modifier.width((L.w[4] * .3f).dp),
                                filled = false,
                                height = 24,
                            )
                        }
                    }
                }
            }
            BandGap(L.gap[4])

            BandRow(L.w[5], L.h[5], horizontalArrangement = Arrangement.Center) {
                // 有小丑台词就显示台词 —— 原版也是把 flavor text 摆在卡底一行小字。
                // 没有台词（消耗品/卡包/券）才退回操作提示。
                T(
                    flavor ?: "点空白处返回",
                    Type.Tiny.copy(
                        color = if (flavor != null) Pal.Money.copy(alpha = .72f) else Pal.FeltHi,
                        fontSize = 5.5.sp,
                    ),
                )
            }
        }
    }
}

private fun boosterBody(i: ShopItem.BoosterItem): String = when (i.kind) {
    com.wearos.wristjoker.game.PackKind.ARCANA -> "开出塔罗牌，选中后进消耗品槽"
    com.wearos.wristjoker.game.PackKind.CELESTIAL -> "开出行星牌，选中立即升级对应牌型"
    com.wearos.wristjoker.game.PackKind.STANDARD -> "开出扑克牌（常带增强/蜡封/版本），选中加入牌堆"
    com.wearos.wristjoker.game.PackKind.BUFFOON -> "开出小丑牌，选中直接入场"
    com.wearos.wristjoker.game.PackKind.SPECTRAL -> "开出幻灵牌，强力效果可改写手牌/小丑/牌型等级"
}
