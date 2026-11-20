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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wearos.wristjoker.game.Card
import com.wearos.wristjoker.game.HandType
import com.wearos.wristjoker.game.SortMode

/*
 * 两个只读的信息页：
 *   1. 牌堆查看（待抽 / 弃牌）—— 原版点牌堆就能看，本作之前完全没有入口；
 *   2. 牌型等级总览 —— 原版左侧常驻的 Run Info，本作只在结算那一瞬显示。
 *
 * 都用弦长骨架，和其余 13 个界面同一套版式，不引入第二种排布。
 */

/* ------------------------------------------------------------------ *
 * 1. 牌堆查看
 * ------------------------------------------------------------------ */

private val DECK_BANDS = roundLayout(
    CircleFit.Band(12f, 2f),   // 0 标题 + 计数
    CircleFit.Band(13f, 2f),   // 1 分页 + 排序
    CircleFit.Band(84f, 2f),   // 2 网格
    CircleFit.Band(20f, 0f),   // 3 返回
)

/**
 * 牌堆查看器。
 *
 * 「待抽」页的顺序就是接下来的摸牌顺序 —— 这让它不只是"看一眼"，
 * 而是能回答"下一张是不是我要的红桃"。原版同样把牌堆按抽取顺序摊开。
 *
 * 排序按钮只作用于待抽页：弃牌堆的顺序没有战术意义。
 */
@Composable
fun DeckViewScreen(
    drawPile: List<Card>,
    discard: List<Card>,
    sortMode: SortMode,
    onSort: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableIntStateOf(0) }
    val cards = if (tab == 0) drawPile else discard

    RoundPage(DECK_BANDS, modifier) { L ->
        BandRow(L.w[0], L.h[0]) {
            T("牌堆", Type.Medium.copy(fontSize = 11.sp, color = Pal.Money))
            T(
                if (tab == 0) "${drawPile.size} 待抽" else "${discard.size} 已弃",
                Type.TinyMono.copy(color = Pal.Dim, fontSize = 6.sp),
            )
        }
        BandGap(L.gap[0])

        BandRow(L.w[1], L.h[1], horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TabChip("待抽", tab == 0, Modifier.weight(1f)) { tab = 0 }
            TabChip("弃牌", tab == 1, Modifier.weight(1f)) { tab = 1 }
            Box(
                Modifier
                    .width(40.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (tab == 0) Pal.Amber500.copy(alpha = .20f)
                        else Pal.Surface.copy(alpha = .45f)
                    )
                    .border(
                        1.dp,
                        if (tab == 0) Pal.Amber500.copy(alpha = .60f)
                        else Pal.CardEdge.copy(alpha = .35f),
                        RoundedCornerShape(3.dp),
                    )
                    .clickable(enabled = tab == 0) { onSort() },
                contentAlignment = Alignment.Center,
            ) {
                T(
                    "按${sortMode.cn}",
                    Type.TinyMono.copy(
                        color = if (tab == 0) Pal.Amber400 else Pal.Dim.copy(alpha = .55f),
                        fontSize = 5.8.sp,
                    ),
                )
            }
        }
        BandGap(L.gap[1])

        BandRow(L.w[2], L.h[2], horizontalArrangement = Arrangement.Center) {
            if (cards.isEmpty()) {
                T("这里还空着", Type.Tiny.copy(color = Pal.Dim, fontSize = 6.5.sp))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().height(L.h[2].dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(cards, key = { it.id }) { c ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Pal.CardFace)
                                .border(.6.dp, Pal.CardEdge.copy(alpha = .45f), RoundedCornerShape(2.dp)),
                        ) {
                            CardFaceArt(c, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        BandGap(L.gap[2])

        BandRow(L.w[3], L.h[3], horizontalArrangement = Arrangement.Center) {
            CasinoButton(
                label = "返回",
                enabled = true,
                accent = Pal.Chip,
                onClick = onBack,
                modifier = Modifier.width((L.w[3] * .34f).dp),
                filled = false,
                height = 18,
            )
        }
    }
}

@Composable
private fun TabChip(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(3.dp))
            .background(if (on) Pal.Chip.copy(alpha = .85f) else Pal.Surface)
            .border(
                1.dp,
                if (on) Pal.Chip else Pal.CardEdge.copy(alpha = .45f),
                RoundedCornerShape(3.dp),
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        T(
            label,
            Type.TinyMono.copy(
                color = if (on) Pal.Ink900 else Pal.Dim,
                fontSize = 6.5.sp,
            ),
        )
    }
}

/* ------------------------------------------------------------------ *
 * 2. 牌型等级总览
 * ------------------------------------------------------------------ */

private val RUNINFO_BANDS = roundLayout(
    CircleFit.Band(12f, 2f),   // 0 标题
    CircleFit.Band(84f, 2f),   // 1 六行两列
    CircleFit.Band(13f, 2f),   // 2 提示
    CircleFit.Band(20f, 0f),   // 3 返回
)

/**
 * 牌型等级总览（原版的 Run Info）。
 *
 * 12 种牌型排成 6 行 × 2 列，每格显示「名称 + 等级」与「筹码 × 倍率」。
 * 本局用过的牌型加亮 —— 玩家能一眼看出"我还剩哪些类型没升级"，
 * 也就知道行星牌该买哪个。
 */
@Composable
fun RunInfoScreen(
    levels: Map<HandType, Int>,
    playedTypes: Set<HandType>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RoundPage(RUNINFO_BANDS, modifier) { L ->
        BandRow(L.w[0], L.h[0]) {
            T("牌型", Type.Medium.copy(fontSize = 11.sp, color = Pal.Money))
            val extra = HandType.entries.sumOf { (levels[it] ?: 1) - 1 }
            T(
                if (extra > 0) "+$extra 级" else "都是 1 级",
                Type.TinyMono.copy(color = if (extra > 0) Pal.Green else Pal.Dim, fontSize = 6.sp),
            )
        }
        BandGap(L.gap[0])

        BandRow(L.w[1], L.h[1], horizontalArrangement = Arrangement.Center) {
            val rowGap = 2.dp
            val rowH = ((L.h[1] - 10f) / 6f).coerceIn(10f, 13f).dp
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                HandType.entries.chunked(2).forEach { pair ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        pair.forEach { h ->
                            HandChip(
                                h = h,
                                level = levels[h] ?: 1,
                                used = h in playedTypes,
                                modifier = Modifier.weight(1f).height(rowH),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        BandGap(L.gap[1])

        BandRow(L.w[2], L.h[2], horizontalArrangement = Arrangement.Center) {
            T(
                "行星牌升级对应牌型 · 亮色是本局出过的",
                Type.Tiny.copy(color = Pal.Dim, fontSize = 5.5.sp),
            )
        }
        BandGap(L.gap[2])

        BandRow(L.w[3], L.h[3], horizontalArrangement = Arrangement.Center) {
            CasinoButton(
                label = "返回",
                enabled = true,
                accent = Pal.Chip,
                onClick = onBack,
                modifier = Modifier.width((L.w[3] * .34f).dp),
                filled = false,
                height = 18,
            )
        }
    }
}

@Composable
private fun HandChip(h: HandType, level: Int, used: Boolean, modifier: Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (used) Pal.SurfaceHigh else Pal.Surface.copy(alpha = .55f))
            .border(
                .6.dp,
                if (used) Pal.Amber500.copy(alpha = .48f) else Pal.CardEdge.copy(alpha = .28f),
                RoundedCornerShape(2.dp),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(
            "${h.cn} ${level}",
            Type.TinyMono.copy(
                color = if (used) Pal.Cream else Pal.Dim,
                fontSize = 5.6.sp,
            ),
            modifier = Modifier.weight(1f),
        )
        T(
            "${h.chipsAt(level)}×${h.multAt(level)}",
            Type.TinyMono.copy(
                color = if (used) Pal.Chip else Pal.Dim.copy(alpha = .62f),
                fontSize = 5.0.sp,
            ),
        )
    }
}
