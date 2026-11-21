package com.wearos.wristjoker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wearos.wristjoker.game.Ach
import com.wearos.wristjoker.game.CareerStats
import java.util.Locale

/*
 * 三个"长线"页面：统计、成就、种子。
 *
 * 它们都不属于一局之内，因此不是 Phase，而是盖在菜单上的覆盖层 ——
 * 和设置、图鉴同一层。共用弦长骨架，不引入第二种版式。
 */

/* ------------------------------------------------------------------ *
 * 1. 生涯统计
 * ------------------------------------------------------------------ */

private val CAREER_BANDS = roundLayout(
    CircleFit.Band(11f, 2f),   // 0 标题
    CircleFit.Band(74f, 2f),   // 1 十二格
    CircleFit.Band(20f, 0f),   // 2 返回
)

/** 一格：上面数字，下面标签。 */
@Composable
private fun StatCell(value: String, label: String, accent: androidx.compose.ui.graphics.Color) {
    // 17dp 而不是 22dp：外层 Row 只有 18dp，超出的部分会被裁掉上半截。
    // 数字 9sp + 标签 5.5sp 是这一格里能同时保住可读与不重叠的上限。
    Column(
        Modifier.fillMaxWidth().height(17.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        T(
            value,
            Type.Medium.copy(color = accent, fontSize = 9.sp),
        )
        T(label, Type.Tiny.copy(fontSize = 5.5.sp))
    }
}

@Composable
fun CareerScreen(stats: CareerStats, onBack: () -> Unit) {
    val L = CAREER_BANDS
    RoundPage(L) { l ->
        BandRow(l.w[0], l.h[0]) {
            T("生涯", Type.Title.copy(fontSize = 12.sp))
            T("${stats.runs} 局", Type.TinyMono.copy(color = Pal.Dim))
        }
        BandGap(l.gap[0])

        Column(Modifier.width(l.w[1].dp).height(l.h[1].dp)) {
            val rows = listOf(
                listOf(StatCell("${stats.wins}", "通关", Pal.Green), StatCell("${stats.bestAnte}", "最高底注", Pal.Money), StatCell("${stats.bestStreak}", "最长连胜", Pal.Amber400)),
                listOf(StatCell(fmt(stats.bestScore), "单局最高", Pal.Cream), StatCell(fmt(stats.bestHand), "单手指最高", Pal.Chip), StatCell("${stats.bestMoney}", "最高持币", Pal.Money)),
                listOf(StatCell("${stats.totalHands}", "累计出牌", Pal.Dim), StatCell("${stats.totalDiscards}", "累计弃牌", Pal.Dim), StatCell("${stats.seenJokers}", "见过小丑", Pal.Purple)),
                listOf(
                    StatCell(
                        if (stats.runs > 0) "${stats.wins * 100 / stats.runs}%" else "—",
                        "通关率", Pal.Green,
                    ),
                    StatCell(
                        if (stats.runs > 0) "${stats.totalHands / stats.runs}" else "—",
                        "场均出牌", Pal.Dim,
                    ),
                    StatCell(
                        if (stats.runs > 0) "${stats.totalDiscards / stats.runs}" else "—",
                        "场均弃牌", Pal.Dim,
                    ),
                ),
            )
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().height(18.dp)) {
                    row.forEach { c -> Box(Modifier.weight(1f)) { c } }
                }
            }
        }

        BandGap(l.gap[1])
        BandRow(l.w[2], l.h[2], horizontalArrangement = Arrangement.Center) {
            CasinoButton("返回", true, Pal.Amber400, onBack, Modifier.width(96.dp), height = 20)
        }
    }
}

/* ------------------------------------------------------------------ *
 * 2. 成就
 * ------------------------------------------------------------------ */

private val ACH_BANDS = roundLayout(
    CircleFit.Band(11f, 2f),
    CircleFit.Band(76f, 2f),
    CircleFit.Band(20f, 0f),
)

@Composable
fun AchievementsScreen(earned: Set<String>, onBack: () -> Unit) {
    val L = ACH_BANDS
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    RoundPage(
        L,
        // 20 条在圆屏上放不下；表冠滚动比手指在 76dp 里蹭更精准，
        // 也不会把正在看的那一条盖住。
        Modifier.onRotaryScrollEvent { e ->
            scope.launch { scroll.scrollBy(e.verticalScrollPixels) }
            true
        },
    ) { l ->
        BandRow(l.w[0], l.h[0]) {
            T("成就", Type.Title.copy(fontSize = 12.sp))
            T("${earned.size}/${Ach.ALL.size}", Type.TinyMono.copy(color = Pal.Money))
        }
        BandGap(l.gap[0])

        Column(
            Modifier
                .width(l.w[1].dp)
                .height(l.h[1].dp)
                .verticalScroll(scroll),
        ) {
            Ach.ALL.forEach { a ->
                val got = a.id in earned
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(17.dp)
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 达成标记：一个 5dp 的方块，比打勾在小尺寸下更清楚
                    Box(
                        Modifier
                            .width(5.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (got) tierColor(a.tier) else Pal.SurfaceHigh),
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        T(
                            a.title,
                            Type.Body.copy(
                                fontSize = 7.5.sp,
                                color = if (got) Pal.Cream else Pal.Dim.copy(alpha = .75f),
                                fontWeight = if (got) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                        T(
                            a.desc,
                            Type.Tiny.copy(
                                fontSize = 5.5.sp,
                                color = if (got) Pal.Dim else Pal.Dim.copy(alpha = .5f),
                            ),
                        )
                    }
                }
            }
        }

        BandGap(l.gap[1])
        BandRow(l.w[2], l.h[2], horizontalArrangement = Arrangement.Center) {
            CasinoButton("返回", true, Pal.Amber400, onBack, Modifier.width(96.dp), height = 20)
        }
    }
}

private fun tierColor(t: Int) = when (t) {
    3 -> Pal.Money
    2 -> Pal.Cream
    else -> Pal.Amber400
}

/* ------------------------------------------------------------------ *
 * 3. 种子 / 每日
 * ------------------------------------------------------------------ */

private val SEED_BANDS = roundLayout(
    CircleFit.Band(11f, 2f),   // 0 标题
    CircleFit.Band(30f, 2f),   // 1 六位数字
    CircleFit.Band(14f, 2f),   // 2 说明
    CircleFit.Band(20f, 0f),   // 3 按钮
)

/**
 * 种子页。
 *
 * 手表没有键盘，"输入种子"只能是**逐位调**：点一位 +1，0–9 循环。
 * 六位十进制足够 —— 一百万种牌序，同一个种子必然开出同一局，
 * 玩家记下 / 分享的也就是这六个数字。
 */
@Composable
fun SeedScreen(
    seed: Long,
    dailyKey: Int,
    onStart: (Long) -> Unit,
    onDaily: () -> Unit,
    onBack: () -> Unit,
) {
    // 六位数组是本地状态：不进引擎，直到点「开局」才落成种子
    var digits by remember {
        val base = (seed % 1_000_000L).toInt().coerceAtLeast(0)
        mutableIntStateOf(base)
    }
    var cursor by remember { mutableIntStateOf(5) }
    val L = SEED_BANDS

    RoundPage(
        L,
        // 表冠改"光标所在的那一位"：向下转是减，向上转是加。
        // 六位数字逐位调，比点十次同一格快得多。
        Modifier.onRotaryScrollEvent { e ->
            val signed = if (e.verticalScrollPixels > 0) -1 else 1
            val place = pow10(5 - cursor)
            digits = ((digits + signed * place) % 1_000_000 + 1_000_000) % 1_000_000
            true
        },
    ) { l ->
        BandRow(l.w[0], l.h[0]) {
            T("种子局", Type.Title.copy(fontSize = 12.sp))
            T("每日 $dailyKey", Type.TinyMono.copy(color = Pal.Dim, fontSize = 6.sp))
        }
        BandGap(l.gap[0])

        // 六格：点哪一位，哪一位 +1
        Row(
            Modifier.width(l.w[1].dp).height(l.h[1].dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val s = String.format(Locale.US, "%06d", digits % 1_000_000)
            s.forEachIndexed { i, ch ->
                Box(
                    Modifier
                        .padding(horizontal = 1.5.dp)
                        .width(22.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (cursor == i) Pal.SurfaceHigh else Pal.Surface)
                        .border(
                            if (cursor == i) 1.4.dp else 1.dp,
                            if (cursor == i) Pal.Amber400 else Pal.SurfaceHigh,
                            RoundedCornerShape(3.dp),
                        )
                        .clickable {
                            if (cursor == i) {
                                val place = pow10(5 - i)
                                digits = (digits + place) % 1_000_000
                            } else {
                                cursor = i
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    T(
                        ch.toString(),
                        Type.Medium.copy(fontSize = 15.sp, color = Pal.Cream),
                    )
                }
            }
        }

        BandGap(l.gap[1])
        BandRow(l.w[2], l.h[2], horizontalArrangement = Arrangement.Center) {
            T("转表冠改位 · 点数字加一", Type.Tiny.copy(fontSize = 6.sp))
        }

        BandGap(l.gap[2])
        BandRow(l.w[3], l.h[3], horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            // 三个等权按钮，间距 5dp：固定宽度在 152dp 里排不下三个，
            // 而"返回"如果单独给宽，另外两个会被压到只剩 50dp。
            CasinoButton(
                "开局", true, Pal.Amber400,
                { onStart(digits.toLong()) },
                Modifier.weight(1.15f), height = 20,
            )
            CasinoButton(
                "每日", true, Pal.Chip,
                onDaily,
                Modifier.weight(1f), filled = false, height = 20,
            )
            CasinoButton(
                "返回", true, Pal.Dim,
                onBack,
                Modifier.weight(1f), filled = false, height = 20,
            )
        }
    }
}

private fun pow10(n: Int): Int {
    var v = 1
    repeat(n) { v *= 10 }
    return v
}

/** 大数字缩写：统计页一格只有 22dp 宽，六位数会溢出。 */
private fun fmt(n: Long): String = when {
    n < 10_000 -> n.toString()
    n < 1_000_000 -> String.format(Locale.US, "%.1fK", n / 1000.0)
    else -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
}

/* ------------------------------------------------------------------ *
 * 4. 解锁横幅
 * ------------------------------------------------------------------ */

/**
 * 刚解锁一条成就时，从顶部压下来的一条横幅。
 *
 * 不用 toast：Wear OS 的系统 toast 会盖住整个圆屏、且带系统样式，
 * 和这套深咖/烫金的界面是两种语言。这里只用一条 24dp 高的胶囊，
 * 3 秒后自己消失，不打断任何操作。
 */
@Composable
fun AchBanner(a: Ach, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(top = 6.dp)
            .width(132.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Pal.Ink800)
            .border(1.dp, Pal.Money.copy(alpha = .8f), RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(5.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(tierColor(a.tier)),
        )
        Spacer(Modifier.width(6.dp))
        Column {
            T("成就解锁", Type.Tiny.copy(fontSize = 5.5.sp, color = Pal.Dim))
            T(a.title, Type.Body.copy(fontSize = 8.sp, color = Pal.Cream, fontWeight = FontWeight.Bold))
        }
    }
}
