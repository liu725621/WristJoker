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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wearos.wristjoker.game.Consumable
import com.wearos.wristjoker.game.GameUi
import com.wearos.wristjoker.game.HandType
import com.wearos.wristjoker.game.JokerInst

private val PANEL = roundLayout(
    CircleFit.Band(14f, 2f),
    CircleFit.Band(92f, 1f),
    CircleFit.Band(8f, 0f),
)

/**
 * 信息面板：小丑 / 消耗品 / 等级 / 券与标签，四页共用一层覆盖。
 *
 * 原版这四样东西分别在四个按钮后面；手表只有一块 216dp 的圆，
 * 做四个入口就等于四个都要挤进牌桌那一行。合成一个面板 + 顶部四个
 * 小块做分页，点击成本一样，但牌桌能省下三行的位置。
 */
@Composable
fun InfoPanel(
    ui: GameUi,
    initialTab: Int = 0,
    onUseConsumable: (Int) -> Unit,
    onDiscardConsumable: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
    val tabs = listOf("小丑", "消耗", "等级", "券")

    Box(
        modifier
            .fillMaxSize()
            .background(Pal.FeltDark.copy(alpha = .97f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(PANEL.w[1].dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- 分页条 ----
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                tabs.forEachIndexed { i, name ->
                    val on = i == tab
                    Box(
                        Modifier
                            .weight(1f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (on) Pal.Money else Pal.Surface)
                            .clickable { tab = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        T(
                            name,
                            Type.TinyMono.copy(
                                color = if (on) Pal.FeltDark else Pal.Dim,
                                fontSize = 6.5.sp,
                            ),
                        )
                    }
                }
            }

            // ---- 内容 ----
            val listH = PANEL.h[1].dp
            when (tab) {
                0 -> JokerTab(ui.jokers, ui.maxJokers, listH)
                1 -> ConsumableTab(
                    ui.consumables, ui.maxConsumables, listH,
                    onUse = onUseConsumable,
                    onDiscard = onDiscardConsumable,
                )
                2 -> LevelTab(ui, listH)
                else -> VoucherTab(ui, listH)
            }

            Spacer(Modifier.height(1.dp))
            T(
                if (tab == 1) "点「用」使用 · 塔罗需先在手牌选牌" else "点任意处关闭",
                Type.Tiny.copy(fontSize = 5.5.sp, color = Pal.Dim),
            )
        }
    }
}

@Composable
private fun JokerTab(jokers: List<JokerInst>, max: Int, height: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxWidth()) {
        T("小丑 ${jokers.size}/$max", Type.TinyMono.copy(color = Pal.Money, fontSize = 7.sp))
        Spacer(Modifier.height(2.dp))
        if (jokers.isEmpty()) {
            T("还没有小丑牌", Type.Tiny.copy(fontSize = 6.5.sp))
            T("过关后去商店买", Type.Tiny.copy(fontSize = 6.5.sp, color = Pal.Dim))
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().height(height),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(jokers) { JokerRow(it) }
            }
        }
    }
}

@Composable
private fun ConsumableTab(
    items: List<Consumable>,
    max: Int,
    height: androidx.compose.ui.unit.Dp,
    onUse: (Int) -> Unit,
    onDiscard: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        T("消耗品 ${items.size}/$max", Type.TinyMono.copy(color = Pal.Purple, fontSize = 7.sp))
        Spacer(Modifier.height(2.dp))
        if (items.isEmpty()) {
            T("没有消耗品", Type.Tiny.copy(fontSize = 6.5.sp))
            T("商店买行星牌升级牌型", Type.Tiny.copy(fontSize = 6.5.sp, color = Pal.Dim))
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().height(height),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexedConsumer(items) { i, c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { ConsumableRow(c) }
                        Spacer(Modifier.width(2.dp))
                        PanelAction("用", Pal.Money) { onUse(i) }
                        Spacer(Modifier.width(1.dp))
                        PanelAction("丢", Pal.BossRed) { onDiscard(i) }
                    }
                }
            }
        }
    }
}

/** `LazyColumn.items` 需要稳定的 key，消耗品可能重名，用下标。 */
private fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexedConsumer(
    list: List<T>,
    content: @Composable (Int, T) -> Unit,
) {
    items(list.size) { i -> content(i, list[i]) }
}

@Composable
private fun PanelAction(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 13.dp, height = 13.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = .9f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        T(text, Type.TinyMono.copy(color = Pal.FeltDark, fontSize = 6.sp))
    }
}

/**
 * 牌型等级页。
 *
 * 原版这个信息藏在「Run Info」里，但手表上它是**构筑的唯一刻度** ——
 * 玩家必须知道自己把哪一手升到了几级，才能判断下一张行星牌该买什么。
 * 所以直接放成常驻页签：12 行，每行「牌型 · Lv · 当前 筹码×倍率」。
 */
@Composable
private fun LevelTab(ui: GameUi, height: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxWidth()) {
        T("牌型等级", Type.TinyMono.copy(color = Pal.Chip, fontSize = 7.sp))
        Spacer(Modifier.height(2.dp))
        LazyColumn(
            Modifier.fillMaxWidth().height(height),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            items(HandType.entries.toList().reversed()) { t ->
                val lv = ui.levels[t] ?: 1
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (lv > 1) Pal.Surface else Pal.FeltDark.copy(alpha = .5f))
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(
                        t.cn,
                        Type.Tiny.copy(
                            color = if (lv > 1) Pal.Cream else Pal.Dim,
                            fontSize = 6.sp,
                        ),
                        modifier = Modifier.width(34.dp),
                    )
                    T(
                        "Lv$lv",
                        Type.TinyMono.copy(
                            color = if (lv > 1) Pal.Money else Pal.FeltHi,
                            fontSize = 6.sp,
                        ),
                        modifier = Modifier.width(18.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    T(
                        "${t.chipsAt(lv)}×${t.multAt(lv)}",
                        Type.TinyMono.copy(color = Pal.Chip, fontSize = 5.8.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VoucherTab(ui: GameUi, height: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxWidth()) {
        T(
            "代金券 ${ui.vouchers.size} · 标签 ${ui.tags.size}",
            Type.TinyMono.copy(color = Pal.Gold, fontSize = 7.sp),
        )
        Spacer(Modifier.height(2.dp))
        LazyColumn(
            Modifier.fillMaxWidth().height(height),
            verticalArrangement = Arrangement.spacedBy(1.5.dp),
        ) {
            if (ui.tags.isNotEmpty()) {
                item {
                    T("本关标签", Type.Tiny.copy(color = Pal.Purple, fontSize = 6.sp))
                }
                items(ui.tags.toList()) { TagRow(it) }
            }
            if (ui.vouchers.isNotEmpty()) {
                item {
                    T("已购代金券", Type.Tiny.copy(color = Pal.Gold, fontSize = 6.sp))
                }
                items(ui.vouchers.toList()) { VoucherRow(it) }
            }
            if (ui.vouchers.isEmpty() && ui.tags.isEmpty()) {
                item {
                    T("还没有代金券", Type.Tiny.copy(fontSize = 6.5.sp))
                }
                item {
                    T("第 2/4/6/8 关的商店有卖", Type.Tiny.copy(fontSize = 6.sp, color = Pal.Dim))
                }
            }
        }
    }
}
