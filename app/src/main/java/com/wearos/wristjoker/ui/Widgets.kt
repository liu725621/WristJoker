package com.wearos.wristjoker.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 统一文本入口：直接用 BasicText，绕开 Material 的字体缩放与默认行高。
 * 手表上 8~11sp 的小字经不起额外行高挤压。
 */
@Composable
fun T(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    maxLines: Int = 1,
    softWrap: Boolean = false,
) {
    BasicText(
        text = text,
        style = if (align != null) style.copy(textAlign = align) else style,
        modifier = modifier,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 原作式按钮：小圆角矩形、深色硬描边、底部 2dp 硬阴影。
 * 不用柔和渐变和投影 —— 层次靠硬边和层间位移建立，CRT 扫描线让背景后退。
 */
@Composable
fun CasinoButton(
    label: String,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    height: Int = 26,
) {
    val bg = when {
        !enabled -> Pal.SurfaceHigh.copy(alpha = .55f)
        filled -> accent
        else -> Pal.Surface
    }
    val fg = when {
        !enabled -> Pal.Dim.copy(alpha = .7f)
        filled -> Pal.FeltDark
        else -> accent
    }
    val edge = when {
        !enabled -> Pal.FeltHi.copy(alpha = .4f)
        else -> Pal.CardEdge
    }
    Box(
        modifier
            .height((height + 2).dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) Pal.CardEdge else Pal.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(bottom = 2.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .border(1.dp, edge, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center,
    ) {
        T(label, Type.Button.copy(color = fg, fontSize = 10.sp))
    }
}

/**
 * 目标进度条：硬边槽 + 金色填充 + 25/50/75 三道刻度。
 *
 * 刻度不是装饰：原版的进度条让玩家一眼估出「还差几次出牌」，
 * 没有刻度就只能在心里做除法。
 */
/**
 * 目标进度条。
 *
 * [preview] 是"这一手打下去会到哪"的第二层 —— 原版左侧的预估分是纯数字，
 * 但手表屏太窄，数字与进度条只能二选一；把预估画成条，两个问题一次说完：
 * 选牌时玩家不用心算，也能看见"打完就够"还是"还差一截"。
 */
@Composable
fun ScoreBar(progress: Float, modifier: Modifier = Modifier, preview: Float = 0f) {
    val shape = RoundedCornerShape(2.dp)
    val p by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(320),
        label = "bar",
    )
    val pv by animateFloatAsState(
        targetValue = preview.coerceIn(0f, 1f),
        animationSpec = tween(200),
        label = "barPreview",
    )
    Box(
        modifier
            .height(5.dp)
            .clip(shape)
            .background(Pal.FeltDark)
            .border(0.8.dp, Pal.CardEdge, shape),
    ) {
        // 预览层先画：它比实际进度长出来的那截才是"这一手能走多远"
        if (pv > p) {
            Box(
                Modifier
                    .fillMaxWidth(pv)
                    .fillMaxHeight()
                    .background(Pal.Green.copy(alpha = .5f)),
            )
        }
        if (p > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(p)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(Pal.GoldDim, Pal.Money, Pal.Money))),
            )
        }
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            for (i in 1..3) {
                val x = size.width * i / 4f
                drawLine(
                    Pal.CardEdge.copy(alpha = .55f),
                    Offset(x, 0f),
                    Offset(x, size.height),
                    0.8.dp.toPx(),
                )
            }
        }
    }
}

/** 硬边小徽章：金钱 / 小丑位这类单值状态。 */
@Composable
fun CountBadge(
    count: String,
    color: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier
            .height(16.dp)
            .clip(shape)
            .background(Pal.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(1.dp, color.copy(alpha = .65f), shape)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(count, Type.TinyMono.copy(color = color, fontSize = 8.sp))
    }
}

/** 小方块标记（不再是圆点），与硬边风格统一。 */
@Composable
fun SuitDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(5.dp).background(color))
}

/**
 * 计分公式行：`牌型 Lv · [蓝 chips] × [红 mult] = [金 total]`。
 * 颜色即标签 —— 蓝=筹码、红=倍率、金=结果，与原作一致。
 */
@Composable
fun ScoreFormula(
    handType: String,
    level: Int,
    chips: Int,
    mult: String,
    total: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(handType + if (level > 1) " Lv$level" else "", Type.TinyMono.copy(color = Pal.Cream, fontSize = 8.sp))
        T("  ", Type.Tiny)
        T("$chips", Type.TinyMono.copy(color = Pal.Chip, fontSize = 9.sp))
        T(" × ", Type.TinyMono.copy(color = Pal.Cream, fontSize = 8.sp))
        T(mult, Type.TinyMono.copy(color = Pal.Mult, fontSize = 9.sp))
        T(" = ", Type.TinyMono.copy(color = Pal.Cream, fontSize = 8.sp))
        T("$total", Type.TinyMono.copy(color = Pal.Money, fontSize = 9.sp))
    }
}

/** 一行两列的紧凑信息条。 */
@Composable
fun InfoRow(left: String, right: String, rightColor: Color = Pal.Cream) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(left, Type.Tiny)
        T(right, Type.Tiny.copy(color = rightColor))
    }
}

/**
 * 结算页的一行条目：左侧项目名、右侧金额。
 *
 * 用点状引导线而不是两段留白 —— 手表上字号只有 8sp，
 * 引导线能让「项 → 值」的对应关系不靠眼睛横移。
 */
@Composable
fun LedgerRow(
    label: String,
    value: String,
    valueColor: Color = Pal.Money,
    strong: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(
            label,
            if (strong) Type.TinyMono.copy(color = Pal.Cream, fontSize = 8.sp)
            else Type.Tiny.copy(color = Pal.Dim, fontSize = 8.sp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .padding(horizontal = 3.dp)
                .background(Pal.FeltHi.copy(alpha = .40f)),
        )
        T(
            value,
            if (strong) Type.TinyMono.copy(color = valueColor, fontSize = 9.sp)
            else Type.TinyMono.copy(color = valueColor, fontSize = 8.sp),
        )
    }
}

/**
 * 已拥有小丑的方形徽章：15dp 头像 + 稀有度色边 + 版本色条 + 右上角「$」卖出标记。
 *
 * 曾经做成「头像 + 名字」的横条，但 5 个 4 字名字并排要 210dp，
 * 而圆屏的安全宽只有 122dp，必然溢出。名字放到小丑面板里看，
 * 牌桌这一行只承担「我有哪些、点一下能看」两个功能。
 */
@Composable
fun JokerBadge(
    id: String,
    accent: Color,
    modifier: Modifier = Modifier,
    edition: com.wearos.wristjoker.game.Edition = com.wearos.wristjoker.game.Edition.BASE,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .size(15.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Pal.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(1.dp, accent.copy(alpha = .85f), RoundedCornerShape(2.dp)),
    ) {
        JokerPortrait(id, Modifier.fillMaxWidth().fillMaxHeight())
        // 版本：顶部 1.5dp 色条
        if (!edition.isBase) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(
                        when (edition) {
                            com.wearos.wristjoker.game.Edition.FOIL -> Pal.Chip
                            com.wearos.wristjoker.game.Edition.HOLO -> Pal.Purple
                            else -> Pal.Money
                        }
                    ),
            )
        }
        // 右下角卖出标记
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(6.dp)
                .background(Pal.FeltDark.copy(alpha = .85f)),
            contentAlignment = Alignment.Center,
        ) {
            T("$", Type.TinyMono.copy(color = Pal.Money, fontSize = 5.sp))
        }
    }
}

/* ------------------------------------------------------------------ *
 * 打击感组件
 * ------------------------------------------------------------------ */

/**
 * 分数格式：4 位以内原样，之后用「万 / 亿」。
 *
 * 原版后期能打到 1e15，等宽字体一格一格写会把整行撑爆；手表上的分数
 * 只需要"量级正确"，精确值放结算页看。
 */
fun fmtScore(v: Long): String = when {
    v < 10_000 -> v.toString()
    v < 100_000_000 -> {
        val w = v / 10_000.0
        if (w >= 100) "${w.toInt()}万" else String.format("%.1f万", w)
    }
    else -> {
        val y = v / 100_000_000.0
        if (y >= 100) "${y.toInt()}亿" else String.format("%.1f亿", y)
    }
}

/**
 * 分数计数器。
 *
 * 不是简单 `animateIntAsState` —— 分数跳变时用 320ms 的补间，
 * 让数字"滚"上去而不是瞬间换掉，眼睛才能跟上这一手加了多少。
 * 小分用点阵（最接近原作的机械里程表），大分退化成等宽字（点阵笔画在
 * 5 位以上会挤成一团）。
 */
@Composable
fun ScoreCounter(
    score: Long,
    modifier: Modifier = Modifier,
    color: Color = Pal.Chip,
    pixel: Dp = 2.0.dp,
) {
    val shown by animateFloatAsState(
        targetValue = score.coerceAtMost(1_000_000_000_000L).toFloat(),
        animationSpec = tween(320),
        label = "score",
    )
    val text = fmtScore(shown.toLong())
    if (shown < 100_000f) {
        PixelText(text, color, modifier = modifier, pixel = pixel)
    } else {
        T(
            text,
            Type.Medium.copy(color = color, fontSize = 17.sp),
            modifier = modifier,
        )
    }
}

/**
 * 屏幕抖动。
 *
 * [trigger] 每次变化抖一次（用乘算小丑时最重）。位移只有 2dp ——
 * 手表屏幕本来就小，抖多了像坏了；2dp 足够让视网膜感到"这一下很重"。
 */
@Composable
fun ShakeBox(
    trigger: Int,
    modifier: Modifier = Modifier,
    magnitude: Float = 2f,
    content: @Composable (Modifier) -> Unit,
) {
    val shift = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        shift.snapTo(magnitude)
        shift.animateTo(
            0f,
            androidx.compose.animation.core.keyframes {
                durationMillis = 260
                magnitude * -.7f at 50
                magnitude * .45f at 110
                magnitude * -.2f at 175
            },
        )
    }
    content(modifier.offset(x = shift.value.dp))
}

/** 带底色的小标签：Boss 效果、标签、稀有度这类"短状态"。 */
@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: Int = 6,
    filled: Boolean = false,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (filled) color.copy(alpha = .9f) else color.copy(alpha = .16f))
            .padding(horizontal = 3.dp, vertical = 0.5.dp),
    ) {
        T(
            text,
            Type.TinyMono.copy(
                color = if (filled) Pal.FeltDark else color,
                fontSize = fontSize.sp,
            ),
        )
    }
}

/** 记账式两列条（结算页 / 结束页共用）。 */
@Composable
fun LedgerLine(
    label: String,
    value: String,
    valueColor: Color = Pal.Money,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        T(label, Type.Tiny.copy(color = Pal.Dim, fontSize = 7.sp))
        Box(
            Modifier.weight(1f).height(1.dp).padding(horizontal = 3.dp)
                .background(Pal.FeltHi.copy(alpha = .35f)),
        )
        T(value, Type.TinyMono.copy(color = valueColor, fontSize = 7.sp))
    }
}
