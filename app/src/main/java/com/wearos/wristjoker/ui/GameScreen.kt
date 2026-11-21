package com.wearos.wristjoker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wearos.wristjoker.fx.Haptics
import com.wearos.wristjoker.fx.Sfx
import com.wearos.wristjoker.game.BlindKind
import com.wearos.wristjoker.game.BossModifier
import com.wearos.wristjoker.game.Card
import com.wearos.wristjoker.game.GameUi
import com.wearos.wristjoker.game.HandType
import com.wearos.wristjoker.game.ScoreBreakdown
import com.wearos.wristjoker.game.ScoreStep
import com.wearos.wristjoker.game.SortMode
import com.wearos.wristjoker.game.StepSource
import com.wearos.wristjoker.game.roman
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/* ------------------------------------------------------------------ *
 * 节奏
 * ------------------------------------------------------------------ */

/**
 * 结算节奏。
 *
 * 上一版是 78ms / 145ms 一步 —— 5 张牌 + 2 个小丑一共 0.7 秒就播完了。
 * 这个速度在 PC 上叫"干脆"，在手表上叫"没看见"：玩家的视线要从出牌区
 * 移到计分区再回到手牌，一次横移就要 200ms 以上，78ms 一步等于跳帧。
 *
 * 现在的节奏参照原作（一手牌的完整弹跳大约 1.5~2 秒）：
 * 落桌停 260 → 每张牌 170 → 乘算 300 → 收尾 320 → 总分停留 700。
 * 一手 5 张牌 + 2 个小丑 ≈ 2.6 秒，比原来慢 3.5 倍。
 */
private const val T_DEAL = 260L
private const val T_STEP = 170L
private const val T_XSTEP = 300L
private const val T_FINALE = 320L
private const val T_HOLD = 700L

/* ------------------------------------------------------------------ *
 * 圆弦分带
 * ------------------------------------------------------------------ */

/**
 * 牌桌的七条横带。**宽度不再写死**，由 [CircleFit] 按各自在圆内的高度
 * 取弦长 —— 所以越靠上/下的行越窄，中间（出牌区、筹码行）最宽，
 * 整块界面是贴着弧长出来的，而不是被弧切掉。
 */
internal val PLAY_BANDS = listOf(
    CircleFit.Band(11f, 2f),   // 0 底注 · 盲注 · 手牌数 · 钱 · 退出
    CircleFit.Band(13f, 2f),   // 1 小丑 + 消耗品
    CircleFit.Band(22f, 2f),   // 2 出牌区
    CircleFit.Band(15f, 1f),   // 3 筹码 × 倍率
    CircleFit.Band(4f, 2f),    // 4 目标进度
    CircleFit.Band(40f, 8f),   // 5 手牌扇形（与按钮拉开，防误触）
    CircleFit.Band(20f, 0f),   // 6 弃牌 / 出牌
)

private val PLAY_W = CircleFit.widths(PLAY_BANDS)
private val PLAY_CY = CircleFit.centers(PLAY_BANDS)

/** 盲注简称。 */
private fun shortBlind(k: BlindKind): String = k.short

// 底注罗马数字的实现搬到 game/Roman.kt —— 卡片（Tile）也要用同一份，
// 两处各写一份迟早会有一边忘了改（尤其无尽模式回退阿拉伯数字那条）。

/**
 * 主牌桌。
 *
 * 分区仍是原版的六块（左上信息 / 顶部小丑 / 中央出牌 / 右上筹码倍率 /
 * 底部手牌 / 底部按钮），但**排布方式换成了圆屏的排布方式**：
 * 七条横带各自取弦长，中间宽、两端窄，视觉上"长在圆里"。
 *
 * 手牌从 2×4 网格改成**单排扇形**（原作的样子）：牌沿一段浅弧摊开，
 * 每张带 1.6° 的递进倾角，边缘 5dp 的下沉；被选中的牌抬起 8dp 并盖在
 * 相邻牌之上。露出的是每张牌的**左段**，所以点数与蜡封/增强标记都画在
 * 左侧可见区，不会互相遮挡。
 */
@Composable
fun GameScreen(
    ui: GameUi,
    onToggle: (Int) -> Unit,
    onPlay: () -> Unit,
    onDiscard: () -> Unit,
    onOpenJokers: () -> Unit,
    onUseConsumable: (Int) -> Unit,
    onResolve: () -> Unit,
    onMove: (Int, Int) -> Unit = { _, _ -> },
    /** 一键排序：切换「按点数 / 按花色」并立即重排。 */
    onSort: () -> Unit = {},
    /** 打开牌堆查看器。 */
    onOpenDeck: () -> Unit = {},
    /** 打开牌型等级总览。 */
    onOpenInfo: () -> Unit = {},
    /** 退出到主菜单（进度已落盘，可从菜单的「继续」回来）。 */
    onExit: () -> Unit = {},
    /** 当前排序模式，只用于按钮上的文字。 */
    sortMode: SortMode = SortMode.RANK,
    /** 结算节奏倍率：设置页的「慢 / 标准 / 快」映射成 0.7 / 1 / 1.5。 */
    speed: Float = 1f,
    modifier: Modifier = Modifier,
) {
    var cursor by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    /** 退出确认层：局内唯一的"离开"入口，误触代价是一局，所以要点两次。 */
    var confirmExit by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val blockedIds = ui.blockedIds

    // ---- 结算动画状态：-1 未播放；0 牌刚落桌；1..n 播第 i 步；n+1 播完 ----
    var step by remember { mutableIntStateOf(-1) }
    var shake by remember { mutableIntStateOf(0) }
    /** 出牌 / 弃牌时手牌带下沉一次的计数。 */
    var handKick by remember { mutableIntStateOf(0) }
    LaunchedEffect(ui.handsPlayed, ui.discardsLeft) { handKick++ }
    var shakeAmount by remember { mutableIntStateOf(2) }
    /** 达标闪白的触发计数（每次自增触发一帧白光）。 */
    var flash by remember { mutableIntStateOf(0) }
    val result = ui.lastResult
    val total = result?.steps?.size ?: 0

    LaunchedEffect(ui.handsPlayed) {
        val r = ui.lastResult ?: return@LaunchedEffect
        if (r.steps.isEmpty()) return@LaunchedEffect
        // 0 = 牌已落桌但还没开始计分：先给眼睛一拍，看清"我出了哪几张"
        step = 0
        Sfx.play(Sfx.Kind.DEAL, .55f)
        Haptics.tap(1)
        delay((T_DEAL / speed).toLong())
        for (i in r.steps.indices) {
            step = i + 1
            val s = r.steps[i]
            // 音高逐张上行：同一手 5 张牌听起来是 C-D-E-F-G，而不是同一个音敲 5 次
            val rate = Sfx.scaleRate(i)
            when (s.source) {
                StepSource.HAND -> Sfx.play(Sfx.Kind.DEAL, .5f)
                StepSource.CARD -> {
                    Sfx.play(Sfx.Kind.CHIP, .7f, rate)
                    Haptics.tap(1)
                }
                StepSource.HELD -> {
                    Sfx.play(Sfx.Kind.MULT, .6f, rate)
                    Haptics.tap(1)
                }
                StepSource.EDITION -> {
                    Sfx.play(Sfx.Kind.XMULT, .55f, rate)
                    Sfx.play(Sfx.Kind.KACHING, .45f, rate)
                    Haptics.tap(1)
                }
                StepSource.JOKER -> {
                    if (s.isX) {
                        Sfx.play(Sfx.Kind.XMULT, .85f, rate)
                        Sfx.play(Sfx.Kind.KACHING, .6f, rate)
                        Haptics.tap(2)
                        // 抖幅随得分量级涨：小分抖 1dp，十万分抖 4dp
                        shakeAmount = magnitudeOf(s.chipsAfter * s.multAfter)
                        shake++
                    } else {
                        Sfx.play(Sfx.Kind.MULT, .65f, rate)
                        Haptics.tap(1)
                    }
                }
            }
            if (s.money > 0) Sfx.play(Sfx.Kind.COIN, .5f, 1f + i * .02f)
            delay(((if (s.isX) T_XSTEP else T_STEP) / speed).toLong())
        }
        step = r.steps.size + 1
        delay((T_FINALE / speed).toLong())
        Haptics.tap(3)
        Sfx.play(Sfx.Kind.XMULT, .9f, 1.3f)
        // 达标高潮：bass drop + 全屏闪白（原版 juice stack 第 3 / 第 5 层）
        Sfx.play(Sfx.Kind.BASS, .8f)
        flash++
        if (r.shattered.isNotEmpty()) Sfx.play(Sfx.Kind.SHATTER, .7f)
        if (r.money > 0) Sfx.play(Sfx.Kind.COIN)
        delay((T_HOLD / speed).toLong())
        step = -1
        onResolve()
    }

    val playing = step in 0..total

    Box(modifier.fillMaxSize()) {
        // 全屏氛围层：圆形毡面 + 同心刻度 + CRT 扫描线 + 牌型色脉冲 + 达标闪白。
        // 叠在最底层，不占任何行高。
        JuiceBackdrop(
            playing = playing,
            pulseColor = handTypeColor(result?.handType),
            flash = flash,
        )

        Column(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .focusable()
                .onRotaryScrollEvent { event ->
                    val last = (ui.hand.size - 1).coerceAtLeast(0)
                    val s = if (event.verticalScrollPixels > 0) 1 else -1
                    cursor = (cursor + s).coerceIn(0, last)
                    true
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            /* ---- 带 0：底注 · 盲注 · 手牌上限 · 钱 --------------------- */
            Row(
                Modifier.width(PLAY_W[0].dp).height(PLAY_BANDS[0].h.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 底注 + 盲注合并成一段：顶栏要腾出位置给「退出」，
                // 原来两者之间那条 SpaceBetween 的缝比字还宽。点击仍去牌型等级总览。
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .clickable { onOpenInfo() }
                        .padding(horizontal = 1.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ui.isBoss) SuitDot(Pal.BossRed, Modifier.padding(end = 1.dp))
                        T(
                            "A${roman(ui.ante)}${if (ui.endless) "∞" else "/8"}·${shortBlind(ui.blindKind)}",
                            Type.TinyMono.copy(
                                fontSize = 6.5.sp,
                                color = if (ui.isBoss) Pal.BossRed else Pal.Money,
                            ),
                        )
                    }
                    Box(
                        Modifier
                            .width(20.dp)
                            .height(.5.dp)
                            .background(Pal.Dim.copy(alpha = .45f)),
                    )
                }
                // 手牌上限必须看得见 —— 玩家买了「画家」、吃了「通灵板」之后
                // 唯一能验证的方式就是这里的数字变了，以及扇形里的牌数变了。
                // 这一块同时是**牌堆查看器的入口**：点它就能看待抽的牌。
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .clickable { onOpenDeck() },
                ) {
                    T(
                        "手${ui.hand.size}/${ui.handSize}",
                        Type.TinyMono.copy(
                            color = if (ui.handSize > 8) Pal.Green else Pal.Dim,
                            fontSize = 6.sp,
                        ),
                    )
                    // 可点提示：一条 0.5dp 的下划线，比加图标省宽度
                    Box(
                        Modifier
                            .width(16.dp)
                            .height(.5.dp)
                            .background(Pal.Dim.copy(alpha = .45f)),
                    )
                }
                CountBadge("$${ui.money}", Pal.Money)
                // 退出：局内唯一的"离开"入口。顶栏只放得下 14dp，
                // 所以点开后还有一层确认 —— 这个小按钮本身不该承担"一键退出"。
                Box(
                    Modifier
                        .width(15.dp)
                        .height(11.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Pal.Ink700.copy(alpha = .55f))
                        .clickable { confirmExit = true },
                    contentAlignment = Alignment.Center,
                ) {
                    T("×", Type.TinyMono.copy(fontSize = 8.sp, color = Pal.Dim))
                }
            }

            Spacer(Modifier.height(PLAY_BANDS[0].gap.dp))

            /* ---- 带 1：小丑横排 + 消耗品 ------------------------------ */
            Row(
                Modifier.width(PLAY_W[1].dp).height(PLAY_BANDS[1].h.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JokerStrip(
                    jokers = ui.jokers,
                    maxSlots = ui.maxJokers,
                    onClick = onOpenJokers,
                    // 正在触发计分的那个小丑单独放大（原版"协同可视化"）
                    pulseIndex = if (playing && step > 0) {
                        result?.steps?.getOrNull(step - 1)?.jokerIndex ?: -1
                    } else -1,
                )
                if (ui.consumables.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier.width(0.8.dp).height(12.dp)
                            .background(Pal.FeltHi.copy(alpha = .45f)),
                    )
                    Spacer(Modifier.width(4.dp))
                    ConsumableStrip(items = ui.consumables, onClick = onUseConsumable)
                }
            }

            Spacer(Modifier.height(PLAY_BANDS[1].gap.dp))

            /* ---- 带 2：出牌区 ---------------------------------------- */
            ShakeBox(trigger = shake, magnitude = shakeAmount.toFloat()) { m ->
                PlayedArea(
                    ui = ui,
                    result = result,
                    step = step,
                    modifier = m.width(PLAY_W[2].dp).height(PLAY_BANDS[2].h.dp),
                )
            }

            Spacer(Modifier.height(PLAY_BANDS[2].gap.dp))

            /* ---- 带 3：筹码 × 倍率（两个独立盒子，互不吃字）----------- */
            ScoreBoard(
                ui = ui,
                result = result,
                step = step,
                modifier = Modifier.width(PLAY_W[3].dp).height(PLAY_BANDS[3].h.dp),
            )

            Spacer(Modifier.height(PLAY_BANDS[3].gap.dp))

            /* ---- 带 4：目标进度 -------------------------------------- */
            // 选牌时把"这一手能打多远"画成第二层进度（绿），并在右侧给出
            // 结论：够 → ✓ 打完后总分；不够 → +这手的分。原版这两件事
            // 分别由左侧面板和进度条承担，手表上并为一行。
            run {
                val pv = ui.preview
                val est = if (pv != null) {
                    (pv.totalChips.toDouble() * pv.totalMult).toLong().coerceAtLeast(0L)
                } else 0L
                val after = ui.score + est
                val clear = pv != null && after >= ui.target && ui.target > 0
                Row(
                    Modifier.width(PLAY_W[4].dp).height(PLAY_BANDS[4].h.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ScoreBar(
                        progress = ui.progress,
                        modifier = Modifier.weight(1f).height(4.dp),
                        preview = if (pv != null && ui.target > 0) {
                            after.toFloat() / ui.target.toFloat()
                        } else 0f,
                    )
                    T(
                        when {
                            pv == null -> " ${fmtScore(ui.score)}/${fmtScore(ui.target)}"
                            clear -> " ✓${fmtScore(after)}"
                            else -> " +${fmtScore(est)}"
                        },
                        Type.TinyMono.copy(
                            color = if (clear) Pal.Green else Pal.Dim,
                            fontSize = 5.5.sp,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(PLAY_BANDS[4].gap.dp))

            /* ---- 带 5：手牌扇形 -------------------------------------- */
            HandFan(
                hand = ui.hand,
                selected = ui.selected,
                blockedIds = blockedIds,
                cursor = cursor,
                maxWidth = PLAY_W[5] - 12f,
                // 扇形会把牌角扬到行框外面，所以底部再收 9dp
                cardHeight = PLAY_BANDS[5].h - 9f,
                onToggle = { id ->
                    ui.hand.indexOfFirst { it.id == id }.let { if (it >= 0) cursor = it }
                    Sfx.play(Sfx.Kind.SELECT, .5f)
                    Haptics.tap(0)
                    onToggle(id)
                },
                onMove = onMove,
                kick = handKick,
                modifier = Modifier.width(PLAY_W[5].dp).height(PLAY_BANDS[5].h.dp),
            )

            Spacer(Modifier.height(PLAY_BANDS[5].gap.dp))

            /* ---- 带 6：弃牌 / 出牌（原版左弃右出）+ 排序 --------------- */
            Row(
                Modifier.width(PLAY_W[6].dp).height(20.dp),
                // 6dp 而不是 4dp：出牌和弃牌是两种不可撤销的操作，
                // 手指落在两键之间时，宁可两边都不响，也不能点"出"却弃了牌。
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 排序：单键切换「按点数 / 按花色」，按钮上只放一个字。
                // 原版是两个按钮，手表上多一个就少一截按钮宽度 —— 而排序
                // 只有两种意图，循环切换足够。
                Box(
                    Modifier
                        .width(22.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Pal.Amber500.copy(alpha = .16f))
                        .border(
                            1.dp,
                            Pal.Amber500.copy(alpha = .58f),
                            RoundedCornerShape(3.dp),
                        )
                        .clickable { onSort() },
                    contentAlignment = Alignment.Center,
                ) {
                    T(
                        if (sortMode == SortMode.RANK) "点" else "花",
                        Type.TinyMono.copy(color = Pal.Amber400, fontSize = 7.sp),
                    )
                }
                CasinoButton(
                    label = "弃 ${ui.discardsLeft}",
                    enabled = ui.canDiscard && !playing,
                    accent = Pal.Chip,
                    onClick = onDiscard,
                    modifier = Modifier.weight(1f),
                    filled = true,
                    height = 20,
                )
                CasinoButton(
                    label = "出 ${ui.handsLeft}",
                    enabled = ui.canPlay && !playing,
                    accent = Pal.Money,
                    onClick = onPlay,
                    modifier = Modifier.weight(1f),
                    height = 20,
                )
            }
        }

        // ---- 退出确认：盖住整张牌桌 ----
        if (confirmExit) {
            ExitConfirm(
                onStay = { confirmExit = false },
                onLeave = {
                    confirmExit = false
                    onExit()
                },
            )
        }
    }
}

/**
 * 「退出本局？」确认层。
 *
 * 两个按钮的默认落点是「继续玩」：真要退出的人会多花半秒去找，
 * 误触的人则直接落在安全选项上。文案不写"保存"，写"进度会保留" ——
 * 玩家关心的是"会不会白打"，不是存储介质。
 */
@Composable
private fun ExitConfirm(onStay: () -> Unit, onLeave: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Pal.Bg.copy(alpha = .88f))
            // 吞掉所有触摸：底下的牌桌此时不该再响应
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(150.dp)
                // 面板自己有一层实色：全屏遮罩只压暗背景，靠它来保证
                // 按钮与文字的对比度（手表在户外，虚焦的背景很吃对比）
                .clip(RoundedCornerShape(6.dp))
                .background(Pal.Ink800)
                .border(1.dp, Pal.Ink700, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T(
                "退出本局？",
                Type.Title.copy(fontSize = 13.sp, color = Pal.Cream, letterSpacing = 1.2.sp),
            )
            Spacer(Modifier.height(5.dp))
            T(
                "进度会保留，可从主菜单继续",
                Type.Tiny.copy(fontSize = 7.sp, color = Pal.Dim),
            )
            Spacer(Modifier.height(11.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                CasinoButton(
                    label = "继续玩",
                    enabled = true,
                    accent = Pal.Chip,
                    onClick = onStay,
                    modifier = Modifier.weight(1f),
                    height = 22,
                )
                CasinoButton(
                    label = "退出",
                    enabled = true,
                    accent = Pal.Amber400,
                    onClick = onLeave,
                    modifier = Modifier.weight(1f),
                    filled = false,
                    height = 22,
                )
            }
        }
    }
}

/** 抖幅：按得分的量级给 1~5dp。原版也是"分越大抖越狠"。 */
private fun magnitudeOf(v: Double): Int = when {
    v < 500 -> 1
    v < 5_000 -> 2
    v < 50_000 -> 3
    v < 500_000 -> 4
    else -> 5
}

/* ------------------------------------------------------------------ *
 * 出牌区
 * ------------------------------------------------------------------ */

/**
 * 出牌区：原版里最"有戏"的一块。
 *
 * 打出的牌停在这里，逐张弹起、计分完的变暗，正在计分的放大 + 金色描边。
 * 没有出牌时这一格退回"信息位"：Boss 效果 / 当前牌型基础值 / 构筑摘要 ——
 * 永远不空着。
 */
@Composable
private fun PlayedArea(
    ui: GameUi,
    result: ScoreBreakdown?,
    step: Int,
    modifier: Modifier = Modifier,
) {
    val cards = ui.playedCards
    val activeId = if (step > 0 && result != null) {
        result.steps.getOrNull(step - 1)?.cardId ?: -1
    } else -1
    val doneIds = remember(result, step) {
        if (result == null || step <= 0) emptySet()
        else result.steps.take(step - 1).map { it.cardId }.filter { it >= 0 }.toSet()
    }
    val activeStep = if (step > 0 && result != null) result.steps.getOrNull(step - 1) else null

    Box(modifier, contentAlignment = Alignment.Center) {
        if (cards.isEmpty()) {
            // 空态：Boss 效果优先，其次是牌型预览，最后是构筑摘要
            if (ui.isBoss && ui.modifier != BossModifier.NONE) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T(
                        "【${ui.modifier.cn}】",
                        Type.TinyMono.copy(color = Pal.BossRed, fontSize = 6.5.sp),
                    )
                    T(
                        ui.modifier.desc,
                        Type.Tiny.copy(color = Pal.BossRed.copy(alpha = .85f), fontSize = 6.sp),
                    )
                    // 眼 / 嘴：这两关"能出什么牌型"本身就是规则的一部分，
                    // 出错了直接 0 分。原版在左侧常驻一张牌型表，手表上
                    // 没这面墙，就把"本关已出过"压在 Boss 描述下面 ——
                    // 它只在出牌区为空时可见，而那正是做决策的时刻。
                    if (ui.modifier == BossModifier.EYE || ui.modifier == BossModifier.MOUTH) {
                        T(
                            if (ui.playedTypes.isEmpty()) "本关还没出过牌型"
                            else "已出 " + ui.playedTypes.joinToString("·") { it.cn },
                            Type.Tiny.copy(
                                color = Pal.BossRed.copy(alpha = .75f),
                                fontSize = 5.5.sp,
                            ),
                        )
                    }
                }
            } else if (ui.preview != null) {
                val p = ui.preview
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T(
                        "${p.handType.cn} · ${p.level} 级",
                        Type.TinyMono.copy(color = Pal.Chip, fontSize = 6.5.sp),
                    )
                    T(
                        "基础 ${p.baseChips} × ${p.baseMult}",
                        Type.Tiny.copy(color = Pal.FeltHi, fontSize = 6.sp),
                    )
                }
            } else {
                T(
                    "余 ${ui.deckLeft} 张",
                    Type.Tiny.copy(color = Pal.FeltHi, fontSize = 6.5.sp),
                )
            }
        } else {
            // 筹码粒子（底层）：从正在计分的牌迸发、拖尾飞向计分区（原版 LAYER 4）
            activeStep?.let { s ->
                ChipBurst(
                    seed = step,
                    color = if (s.isX) Pal.Mult else Pal.Chip,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                cards.forEachIndexed { i, c ->
                    PlayedCard(
                        card = c,
                        active = c.id == activeId,
                        done = c.id in doneIds,
                        index = i,
                    )
                }
            }
            // 飘字改到整条带子的右端，做成一个不上浮的胶囊 —— 见 [GainPill]
            activeStep?.let { s ->
                val label = gainLabel(s)
                if (label.isNotEmpty()) {
                    GainPill(
                        label,
                        if (s.isX) Pal.Mult else Pal.Money,
                        Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}

/** 这一步对外行话：+筹码 / +倍率 / ×倍率 / 小丑号。 */
private fun gainLabel(s: ScoreStep): String = when (s.source) {
    StepSource.HAND -> ""
    StepSource.CARD, StepSource.HELD, StepSource.EDITION -> s.label
    StepSource.JOKER -> if (s.isX) "丑${s.jokerIndex + 1} ${s.label}" else "丑${s.jokerIndex + 1} ${s.label}"
}

/**
 * 一张已打出的牌。
 *
 * 弹跳用 `spring(0.42)` —— 阻尼比 1 是临界阻尼（不过冲），0.42 会过冲一次
 * 再回弹，这就是原版卡牌"啪"一下弹起来的手感。
 */
@Composable
private fun PlayedCard(
    card: Card,
    active: Boolean,
    done: Boolean,
    /** 在出牌区里的序号，用于入场错峰。 */
    index: Int = 0,
    modifier: Modifier = Modifier,
) {
    val bounce by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 1100f),
        label = "bounce",
    )
    val dim by animateFloatAsState(
        targetValue = if (done && !active) 1f else 0f,
        animationSpec = tween(200),
        label = "dim",
    )
    // 入场：从手牌带的方向（下方 56dp）、±14° 倾斜、0.7 缩放、全透明开始，
    // 按 index 错峰 36ms 飞入并回正。
    //
    // 位移从上一版的 12dp 加到 56dp，是因为 12dp 在 1.4 英寸屏上只是
    // "抖了一下" —— 眼睛读到的仍是"瞬移"。56dp 差不多是手牌带到出牌区的
    // 实际距离，牌看起来是从手里被抽出来的；同时保留一个由 id 决定的
    // 起手倾角，五张牌飞进来的轨迹不会完全平行。
    val enter = remember(card.id) { Animatable(0f) }
    LaunchedEffect(card.id) {
        enter.snapTo(0f)
        delay((index * 36L).coerceAtMost(220L))
        enter.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }
    Box(modifier.size(28.dp, 20.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val e = enter.value
                    val s = (1f + .16f * bounce) * (.7f + .3f * e)
                    scaleX = s
                    scaleY = s
                    translationY = -3.dp.toPx() * bounce + (1f - e) * 56.dp.toPx()
                    alpha = e
                    // 落定时回正到 ±3°：原版每张打出的牌带一点随机倾斜，
                    // 避免"阅兵式"的死板排布；用 id 做稳定伪随机。
                    val tilt = (((card.id * 31) % 7) - 3).toFloat()
                    val from = if (card.id % 2 == 0) 14f else -14f
                    rotationZ = tilt + (1f - e) * from
                }
                .clip(RoundedCornerShape(2.dp))
                .background(Pal.CardFace)
                .border(
                    if (active) 1.4.dp else .8.dp,
                    if (active) Pal.Money else Pal.CardEdge,
                    RoundedCornerShape(2.dp),
                ),
        ) {
            CardFaceArt(card, Modifier.fillMaxSize())
        }
        if (dim > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = dim }
                    .background(Pal.FeltDark.copy(alpha = .42f)),
            )
        }
    }
}

/**
 * 这一步给了多少 —— 做成**贴在带子右端的胶囊**，而不是从牌上方飘出去。
 *
 * 上一版是 `translationY = -9dp * v` + `padding(top = 18dp)`：文字起点被
 * 推到出牌区下沿，上浮的整个过程都压在筹码/倍率那一行上 —— 也就是
 * 玩家反馈的"数字有遮挡"。牌桌上每一行之间只有 1~2dp 的间距，任何
 * 纵向位移都会串行。
 *
 * 所以改成横向解决：胶囊固定在带子右侧 34dp 的空位里，只做「放大 → 淡出」，
 * 不做位移。信息一样清楚，且永远不会盖住别的数字。
 */
@Composable
private fun GainPill(text: String, color: Color, modifier: Modifier = Modifier) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(text) {
        p.snapTo(0f)
        p.animateTo(1f, tween(700))
    }
    val v = p.value
    if (v <= 0f || v >= 1f) return
    val pop = (1f - (1f - v) * (1f - v)).coerceIn(0f, 1f)
    Box(
        modifier
            .padding(end = 2.dp)
            .graphicsLayer {
                alpha = if (v < .55f) 1f else 1f - (v - .55f) / .45f
                scaleX = .82f + .18f * pop
                scaleY = .82f + .18f * pop
            }
            .clip(RoundedCornerShape(3.dp))
            .background(Pal.FeltDark.copy(alpha = .88f))
            .border(1.dp, color.copy(alpha = .85f), RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    ) {
        T(text, Type.TinyMono.copy(color = color, fontSize = 6.5.sp))
    }
}

/* ------------------------------------------------------------------ *
 * 筹码 × 倍率
 * ------------------------------------------------------------------ */

/**
 * 筹码 × 倍率。
 *
 * 这是整块界面里唯一必须"一眼读准"的两个数，所以各自住进一个**有底色的
 * 盒子**：蓝盒装筹码、红盒装倍率，中间一个 ×。原版就是这个排法。
 *
 * 上一版把「= 总分」也塞进同一行，于是 `1,234 × 12.5 =15,425` 在 146dp
 * 里挤成一串，末位会贴到进度条上。现在总分交给带 4 的进度条与结算页，
 * 这一行只留两个操作数 —— 宽度一下就宽裕了，字号也能放大。
 */
@Composable
private fun ScoreBoard(
    ui: GameUi,
    result: ScoreBreakdown?,
    step: Int,
    modifier: Modifier = Modifier,
) {
    val playing = step in 0..(result?.steps?.size ?: 0)
    val s = if (playing && step > 0) result?.steps?.getOrNull(step - 1) else null
    val chips: Int
    val mult: Double
    if (s != null) {
        chips = s.chipsAfter
        mult = s.multAfter
    } else if (playing && step == 0 && result != null) {
        // 落桌那一拍：先显示牌型基础值，让"起点"也有交代
        chips = result.baseChips
        mult = result.baseMult.toDouble()
    } else {
        chips = ui.preview?.totalChips ?: 0
        mult = ui.preview?.totalMult ?: 0.0
    }
    val hasValue = chips > 0

    // 数字滚动：跟着 T_STEP 放慢到 240ms，眼睛才跟得上
    val shownChips by animateFloatAsState(
        targetValue = chips.toFloat().coerceAtMost(1e9f),
        animationSpec = tween(240),
        label = "chips",
    )
    val shownMult by animateFloatAsState(
        targetValue = mult.toFloat().coerceAtMost(9999f),
        animationSpec = tween(240),
        label = "mult",
    )

    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!hasValue) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                T(
                    if (ui.preview != null) "选牌" else "—",
                    Type.Tiny.copy(color = Pal.FeltHi, fontSize = 7.sp),
                )
            }
            return@Row
        }
        // 蓝盒：筹码。点阵数字，最接近原作的机械里程表
        Box(
            Modifier
                .weight(1.25f)
                .fillMaxSize()
                .clip(RoundedCornerShape(3.dp))
                .background(Pal.Chip.copy(alpha = .14f))
                .border(1.dp, Pal.Chip.copy(alpha = .55f), RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val txt = fmtScore(shownChips.toLong())
            if (txt.length <= 6) {
                // 逐位 stagger 的里程表滚动（原版 LAYER 2）
                RollingDigits(txt, Pal.Chip, step, 1.5.dp)
            } else {
                T(txt, Type.Medium.copy(color = Pal.Chip, fontSize = 12.sp))
            }
        }
        T("×", Type.Medium.copy(color = Pal.Dim, fontSize = 12.sp))
        // 红盒：倍率
        Box(
            Modifier
                .weight(0.75f)
                .fillMaxSize()
                .clip(RoundedCornerShape(3.dp))
                .background(Pal.Mult.copy(alpha = .14f))
                .border(1.dp, Pal.Mult.copy(alpha = .55f), RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val txt = fmtMultShort(shownMult.toDouble())
            T(txt, Type.Medium.copy(color = Pal.Mult, fontSize = if (txt.length > 4) 12.sp else 15.sp))
        }
    }
}

private fun fmtMultShort(m: Double): String =
    if (abs(m - m.toLong().toDouble()) < 0.05) m.toLong().toString()
    else String.format("%.1f", m)

/* ------------------------------------------------------------------ *
 * 打击感氛围层（原版 juice stack 第 3 层：屏幕效果）
 * ------------------------------------------------------------------ */

/** 牌型色：越高级的牌型，背景脉冲越偏暖（金 / 红），弱牌型偏冷（蓝）。 */
private fun handTypeColor(ht: HandType?): Color = when (ht) {
    null -> Pal.Purple
    HandType.FLUSH_FIVE, HandType.FLUSH_HOUSE, HandType.FIVE_OF_A_KIND -> Pal.Money
    HandType.STRAIGHT_FLUSH, HandType.FOUR_OF_A_KIND -> Pal.Mult
    HandType.FULL_HOUSE, HandType.FLUSH, HandType.STRAIGHT -> Pal.Purple
    else -> Pal.Chip
}

/**
 * 全屏氛围层：**圆形毡面 + 表盘同心圈 + CRT 扫描线 + 牌型色脉冲 + 达标闪白**。
 *
 * 上一版背景还是一整块方形渐变，只是内容缩在中间 —— 这正是"像给圆屏加了个
 * 安全区"的观感来源：方形底 + 方形容器，圆只体现在最外层被裁掉。
 *
 * 现在底子是**一个真正的圆**：半径 108dp 的毡面、外面是纯黑（表壳），
 * 再叠 3 圈同心刻度。所有纹理都用 `clipPath(圆)` 裁过，所以任何抖动、
 * 闪白、扫描线都不会溢出到圆外，圆边界始终是硬的。
 */
@Composable
private fun JuiceBackdrop(playing: Boolean, pulseColor: Color, flash: Int) {
    val pulse by animateFloatAsState(
        targetValue = if (playing) 0.15f else 0f,
        animationSpec = tween(200),
        label = "bgPulse",
    )
    val flashP = remember { Animatable(0f) }
    LaunchedEffect(flash) {
        if (flash <= 0) return@LaunchedEffect
        flashP.snapTo(1f)
        flashP.animateTo(0f, tween(300))
    }
    Canvas(Modifier.fillMaxSize()) {
        val r = (CircleFit.RADIUS - 1f).dp.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        val disc = Path().apply { addOval(Rect(c, r)) }

        // 表壳：圆外一律纯黑，把"这是圆屏"这件事说死
        drawRect(Color.Black)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Pal.Felt.copy(alpha = .55f), Pal.FeltDark),
                center = Offset(c.x, c.y * .72f),
                radius = r * 1.15f,
            ),
            radius = r,
            center = c,
        )

        clipPath(disc) {
            // 表盘同心圈：三圈极淡的刻线，提供"这是圆的"的视觉锚点
            for (f in listOf(.94f, .74f, .52f)) {
                drawCircle(
                    Pal.FeltHi.copy(alpha = if (playing) .16f else .09f),
                    radius = r * f,
                    center = c,
                    style = Stroke(.7.dp.toPx()),
                )
            }
            // 背景色按牌型脉冲
            if (pulse > 0.001f) drawRect(pulseColor.copy(alpha = pulse))
            // CRT 扫描线：常驻极淡，结算时瞬态增强
            val scan = if (playing) 0.10f else 0.030f
            val line = Color.Black.copy(alpha = scan)
            val gapPx = 3.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                y += gapPx
            }
            // 达标闪白（bass drop 同步）
            val f = flashP.value
            if (f > 0.001f) drawRect(Color.White.copy(alpha = f * 0.38f))
        }
        // 圆边界硬描边：一圈 1dp 的暗金，把毡面"扣"在表壳里
        drawCircle(
            Pal.GoldDim.copy(alpha = .55f),
            radius = r,
            center = c,
            style = Stroke(1.dp.toPx()),
        )
    }
}

/* ------------------------------------------------------------------ *
 * 逐位滚动数字 + 筹码粒子（原版 LAYER 2 / LAYER 4）
 * ------------------------------------------------------------------ */

/**
 * 逐位 stagger 的里程表数字：每一位比前一位晚一点滑到位，像老虎机卷轴
 * 逐格停住，而不是整串数字一起淡入（原版 LAYER 2 Chip Counter）。
 */
@Composable
private fun RollingDigits(text: String, color: Color, step: Int, pixel: Dp) {
    val p = remember { Animatable(1f) }
    LaunchedEffect(step) {
        p.snapTo(0f)
        p.animateTo(1f, tween(300))
    }
    val v = p.value
    Row(verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            val local = ((v * text.length - i * 0.55f) / 0.55f).coerceIn(0f, 1f)
            PixelText(
                ch.toString(), color, pixel,
                Modifier.graphicsLayer {
                    alpha = 0.3f + 0.7f * local
                    translationY = -(1f - local) * 4.dp.toPx()
                },
            )
        }
    }
}

/**
 * 筹码粒子爆发：9 颗圆点从中心按角度放射、带一小段拖尾，边飞边淡出。
 * 颜色随当前计分步（乘算红、普通蓝），对应原版"筹码飞向总分"。
 */
@Composable
private fun ChipBurst(seed: Int, color: Color, modifier: Modifier) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(seed) {
        p.snapTo(0f)
        p.animateTo(1f, tween(520))
    }
    val v = p.value
    if (v >= 1f) return
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val fade = (1f - v).coerceIn(0f, 1f)
        for (i in 0 until 9) {
            val a = (i * 40f + seed * 7f) * PI.toFloat() / 180f
            val r = (5f + (i % 3) * 4f) * v
            val x = cx + cos(a) * r * 2.4f
            val y = cy - sin(a) * r
            drawCircle(
                color.copy(alpha = fade * 0.9f),
                (1.5f - v).coerceAtLeast(0.4f).dp.toPx(),
                Offset(x, y),
            )
            drawLine(
                color.copy(alpha = fade * 0.35f),
                Offset(x, y),
                Offset(x - cos(a) * 4f, y + sin(a) * 4f),
                1.dp.toPx(),
            )
        }
    }
}
