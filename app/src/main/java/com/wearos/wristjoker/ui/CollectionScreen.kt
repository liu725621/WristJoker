package com.wearos.wristjoker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wearos.wristjoker.game.Collection
import com.wearos.wristjoker.game.CollectionEntry
import com.wearos.wristjoker.game.CollectionTab
import com.wearos.wristjoker.game.Consumables
import com.wearos.wristjoker.game.Voucher

/**
 * 图鉴页。
 *
 * 五类分页（小丑 / 消耗品 / 代金券 / 标签 / 牌组），4 列网格，未解锁画成
 * 暗格加问号。点一张，底部信息带显示名字与效果 —— 手表上没有悬浮 tooltip，
 * 用固定的一行"详情带"比弹窗更稳，也不会遮住刚点的那张。
 *
 * 每格只有 40×38dp，所以名字最多放 4 个字（6sp）。超过的用省略号截断，
 * 完整名字在详情带里 —— 这是刻意的取舍：网格要能一屏看到 12 个，
 * 字号就不能大，宁可名字短。
 */
private val COLLECTION = roundLayout(
    CircleFit.Band(12f, 2f),   // 0 标题 + 计数
    CircleFit.Band(14f, 2f),   // 1 分页
    CircleFit.Band(98f, 2f),   // 2 网格
    CircleFit.Band(28f, 2f),   // 3 详情带
    CircleFit.Band(20f, 0f),   // 4 返回
)

@Composable
fun CollectionScreen(
    seen: Set<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<CollectionEntry?>(null) }
    val cur = CollectionTab.entries[tab]
    val items = Collection.of(cur)

    // 表冠翻页：图鉴是最典型的"一页一页看"的场景，5 个分页用转表冠切
    // 比用手指戳 36dp 的小标签舒服得多（手指会盖住正在看的那一页）。
    RoundPage(
        COLLECTION,
        modifier.onRotaryScrollEvent { e ->
            if (picked != null) return@onRotaryScrollEvent false
            val step = if (e.verticalScrollPixels > 0) 1 else -1
            tab = (tab + step).coerceIn(0, CollectionTab.entries.size - 1)
            true
        },
    ) { L ->
        BandRow(L.w[0], L.h[0]) {
            T("图鉴", Type.Medium.copy(fontSize = 11.sp, color = Pal.Money))
            val done = Collection.seenCount(cur, seen)
            T(
                "$done/${items.size}",
                Type.TinyMono.copy(
                    color = if (done == items.size) Pal.Green else Pal.Dim,
                    fontSize = 6.5.sp,
                ),
            )
        }
        BandGap(L.gap[0])

        // 五个分页：一屏放下，不做横向滚动 —— 5 × 36dp 正好 180dp
        BandRow(L.w[1], L.h[1], horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            CollectionTab.entries.forEachIndexed { i, t ->
                val on = i == tab
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (on) Pal.Money.copy(alpha = .88f) else Pal.Surface)
                        .border(
                            1.dp,
                            if (on) Pal.Money else Pal.CardEdge.copy(alpha = .5f),
                            RoundedCornerShape(3.dp),
                        )
                        .clickable {
                            tab = i
                            picked = null
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    T(
                        t.cn,
                        Type.TinyMono.copy(
                            color = if (on) Pal.FeltDark else Pal.Dim,
                            fontSize = 6.5.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        ),
                    )
                }
            }
        }
        BandGap(L.gap[1])

        BandRow(L.w[2], L.h[2], horizontalArrangement = Arrangement.Center) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().height(L.h[2].dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(items, key = { it.id }) { e ->
                    CollectionCell(
                        entry = e,
                        unlocked = e.id in seen,
                        selected = picked?.id == e.id,
                        onClick = { picked = if (picked?.id == e.id) null else e },
                    )
                }
            }
        }
        BandGap(L.gap[2])

        BandRow(L.w[3], L.h[3], horizontalArrangement = Arrangement.Center) {
            val p = picked
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (p == null) {
                    T(
                        "点一张看效果 · 灰色是还没见过的",
                        Type.Tiny.copy(color = Pal.Dim, fontSize = 6.sp),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(p.name, Type.TinyMono.copy(color = Pal.Cream, fontSize = 8.sp))
                        Spacer(Modifier.width(4.dp))
                        T(p.sub, Type.TinyMono.copy(color = Pal.Money, fontSize = 5.5.sp))
                    }
                    T(
                        if (p.id in seen) p.desc else "还没在商店或卡包里见过",
                        Type.Tiny.copy(color = Pal.Cream.copy(alpha = .85f), fontSize = 6.5.sp),
                        modifier = Modifier.fillMaxWidth(),
                        align = TextAlign.Center,
                        maxLines = 2,
                        softWrap = true,
                    )
                }
            }
        }
        BandGap(L.gap[3])

        BandRow(L.w[4], L.h[4], horizontalArrangement = Arrangement.Center) {
            CasinoButton(
                label = "返回",
                enabled = true,
                accent = Pal.Chip,
                onClick = onBack,
                modifier = Modifier.width((L.w[4] * .4f).dp),
                filled = false,
                height = 20,
            )
        }
    }
}

/** 一格：图 + 名字。未解锁时整体压暗并画问号。 */
@Composable
private fun CollectionCell(
    entry: CollectionEntry,
    unlocked: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(3.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(shape)
            .background(if (selected) Pal.SurfaceHigh else Pal.Surface.copy(alpha = .75f))
            .border(
                if (selected) 1.5.dp else 0.8.dp,
                when {
                    selected -> Pal.Money
                    unlocked -> Pal.CardEdge.copy(alpha = .55f)
                    else -> Pal.Dim.copy(alpha = .25f)
                },
                shape,
            )
            .clickable(onClick = onClick)
            .padding(1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 22.dp, height = 22.dp), contentAlignment = Alignment.Center) {
            if (!unlocked) {
                T("?", Type.Medium.copy(color = Pal.Dim.copy(alpha = .7f), fontSize = 12.sp))
            } else {
                when (entry.tab) {
                    CollectionTab.JOKER -> JokerPortrait(
                        entry.id.removePrefix("j:"), Modifier.fillMaxSize(),
                    )
                    CollectionTab.CONSUMABLE -> Consumables.ALL
                        .firstOrNull { it.key == entry.id.removePrefix("c:") }
                        ?.let { ConsumableArt(it, Modifier.fillMaxSize()) }
                    CollectionTab.VOUCHER -> Voucher.entries
                        .firstOrNull { it.key == entry.id.removePrefix("v:") }
                        ?.let { VoucherArt(it, Modifier.fillMaxSize()) }
                    CollectionTab.TAG -> TagBadge(entry.name.take(1))
                    CollectionTab.DECK -> Box(
                        Modifier.padding(2.dp).clip(RoundedCornerShape(1.5.dp)),
                    ) { CardBackArt(Modifier.fillMaxSize()) }
                }
            }
        }
        T(
            if (unlocked) entry.name.take(4) else "??",
            Type.TinyMono.copy(
                color = if (unlocked) Pal.Cream else Pal.Dim.copy(alpha = .6f),
                fontSize = 5.5.sp,
            ),
            maxLines = 1,
        )
    }
}

/** 标签没有美术资源，用一个带底色的方块 + 首字代替。 */
@Composable
private fun TagBadge(glyph: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Pal.Purple.copy(alpha = .35f))
            .border(0.8.dp, Pal.Purple, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        T(glyph, Type.Medium.copy(color = Pal.Cream, fontSize = 10.sp))
    }
}
