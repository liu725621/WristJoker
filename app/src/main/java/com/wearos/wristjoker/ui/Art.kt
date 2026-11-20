package com.wearos.wristjoker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wearos.wristjoker.game.Card
import com.wearos.wristjoker.game.PackKind
import com.wearos.wristjoker.game.PackSize
import com.wearos.wristjoker.game.Consumable
import com.wearos.wristjoker.game.ConsumableKind
import com.wearos.wristjoker.game.Enhancement
import com.wearos.wristjoker.game.Edition
import com.wearos.wristjoker.game.HandType
import com.wearos.wristjoker.game.Rank
import com.wearos.wristjoker.game.Seal
import com.wearos.wristjoker.game.Suit
import com.wearos.wristjoker.game.Voucher
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * 全部图形在这里用 Canvas 逐块重绘：5×7 点阵字符、四种花色、牌面、牌背、
 * 18 个小丑像素头像、赌桌毡、CRT 扫描线、四角光标。
 *
 * 牌面从上一版的位图切图改回纯 Canvas 绘制。原因很实际：手表手牌槽位是
 * 38×26dp，任何 88×124 的竖版位图塞进去都要裁掉大半，J/Q/K 的人像被截断，
 * 缩小后点数还会被重采样糊掉。改成矢量绘制后点数按点阵逐格画，在 26dp 高度
 * 里仍有 10dp 高的字号，且不依赖任何外部素材，APK 里没有一张牌面 PNG。
 */

/* ------------------------------------------------------------------ *
 * 1. 5×7 点阵字符
 * ------------------------------------------------------------------ */

/** 5 宽 × 7 高。数字、字母、运算符全在一张表里，牌面点数和分数共用同一套字形。 */
private val GLYPHS: Map<Char, List<String>> = mapOf(
    '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
    '3' to listOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
    '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
    '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
    '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
    '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
    '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
    '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
    'b' to listOf("10000", "10000", "11110", "10001", "10001", "10001", "11110"),
    'c' to listOf("00000", "01111", "10000", "10000", "10000", "01111", "00000"),
    'd' to listOf("00001", "00001", "01111", "10001", "10001", "10001", "01111"),
    'e' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    'f' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
    'g' to listOf("01111", "10000", "10000", "10111", "10001", "10001", "01111"),
    'h' to listOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
    'i' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "11111"),
    'l' to listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    'm' to listOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
    'n' to listOf("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
    'o' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    'p' to listOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
    'r' to listOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
    's' to listOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
    'u' to listOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
    'v' to listOf("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
    'w' to listOf("10001", "10001", "10001", "10101", "10101", "11011", "10001"),
    'y' to listOf("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
    'a' to listOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
    'j' to listOf("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
    'q' to listOf("00000", "01110", "10001", "10001", "10101", "01110", "00001"),
    'k' to listOf("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
    't' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
    'x' to listOf("00000", "10001", "01010", "00100", "01010", "10001", "00000"),
    '+' to listOf("00000", "00100", "00100", "11111", "00100", "00100", "00000"),
    '-' to listOf("00000", "00000", "00000", "11111", "00000", "00000", "00000"),
    '=' to listOf("00000", "11111", "00000", "11111", "00000", "00000", "00000"),
    '/' to listOf("00001", "00001", "00010", "00100", "01000", "10000", "10000"),
    '.' to listOf("00000", "00000", "00000", "00000", "00000", "01100", "01100"),
    ':' to listOf("00000", "01100", "01100", "00000", "01100", "01100", "00000"),
    '$' to listOf("00100", "01111", "10100", "01110", "00101", "11110", "00100"),
    '%' to listOf("10001", "00010", "00100", "00100", "01000", "10001", "00000"),
    '!' to listOf("00100", "00100", "00100", "00100", "00100", "00000", "00100"),
    ' ' to listOf("00000", "00000", "00000", "00000", "00000", "00000", "00000"),
    '?' to listOf("01110", "10001", "00001", "00010", "00100", "00000", "00100"),
)

private const val GLYPH_COLS = 5
private const val GLYPH_ROWS = 7
private const val GLYPH_ADVANCE = 6

/** 字符在 [px] 像素格下的宽度（含字间 1 格）。 */
private fun glyphWidth(text: String, px: Float): Float =
    (text.length * GLYPH_ADVANCE - 1).coerceAtLeast(1) * px

/**
 * 在 DrawScope 里画点阵串。
 *
 * 牌面点数、分数计数、提示文字都走这一条路径 —— 同一套字形，同一套锯齿，
 * 视觉上才是一块屏幕上的东西，而不是两种风格拼在一起。
 */
private fun DrawScope.drawPixelText(
    text: String,
    color: Color,
    x: Float,
    y: Float,
    px: Float,
) {
    text.forEachIndexed { i, ch ->
        val g = GLYPHS[ch.lowercaseChar()] ?: GLYPHS.getValue('?')
        for (r in 0 until GLYPH_ROWS) {
            val row = g[r]
            for (c in 0 until GLYPH_COLS) {
                if (row[c] == '1') {
                    drawRect(
                        color,
                        Offset(x + (i * GLYPH_ADVANCE + c) * px, y + r * px),
                        Size(px, px),
                    )
                }
            }
        }
    }
}

/** 像素数字。每个方格就是一个 [pixel]，天然带锯齿，与 CRT 扫描线同一套视觉。 */
@Composable
fun PixelText(
    text: String,
    color: Color,
    pixel: Dp = 2.dp,
    modifier: Modifier = Modifier,
) {
    val cols = (text.length * GLYPH_ADVANCE - 1).coerceAtLeast(1)
    Canvas(modifier.width(pixel * cols).height(pixel * GLYPH_ROWS)) {
        drawPixelText(text, color, 0f, 0f, pixel.toPx())
    }
}

/* ------------------------------------------------------------------ *
 * 2. 四种花色（纯 Path / Circle，不依赖系统字体字形）
 * ------------------------------------------------------------------ */

/** 把花色画进 [left],[top] 起点、[w]×[h] 的矩形里。 */
private fun DrawScope.drawSuitShape(
    suit: Suit,
    color: Color,
    left: Float,
    top: Float,
    w: Float,
    h: Float,
) {
    fun x(t: Float) = left + t * w
    fun y(t: Float) = top + t * h
    when (suit) {
        Suit.DIAMOND -> {
            val p = Path().apply {
                moveTo(x(.5f), y(0f)); lineTo(x(1f), y(.5f))
                lineTo(x(.5f), y(1f)); lineTo(x(0f), y(.5f)); close()
            }
            drawPath(p, color)
        }
        Suit.HEART -> {
            val p = Path().apply {
                moveTo(x(.5f), y(.98f))
                cubicTo(x(.10f), y(.62f), x(.02f), y(.42f), x(.02f), y(.28f))
                cubicTo(x(.02f), y(.10f), x(.16f), y(.02f), x(.30f), y(.02f))
                cubicTo(x(.42f), y(.02f), x(.50f), y(.14f), x(.50f), y(.22f))
                cubicTo(x(.50f), y(.14f), x(.58f), y(.02f), x(.70f), y(.02f))
                cubicTo(x(.84f), y(.02f), x(.98f), y(.10f), x(.98f), y(.28f))
                cubicTo(x(.98f), y(.42f), x(.90f), y(.62f), x(.50f), y(.98f))
                close()
            }
            drawPath(p, color)
        }
        Suit.SPADE -> {
            val p = Path().apply {
                moveTo(x(.5f), y(.02f))
                cubicTo(x(.62f), y(.18f), x(.98f), y(.34f), x(.98f), y(.58f))
                cubicTo(x(.98f), y(.78f), x(.82f), y(.88f), x(.66f), y(.88f))
                cubicTo(x(.58f), y(.88f), x(.53f), y(.84f), x(.50f), y(.79f))
                cubicTo(x(.47f), y(.84f), x(.42f), y(.88f), x(.34f), y(.88f))
                cubicTo(x(.18f), y(.88f), x(.02f), y(.78f), x(.02f), y(.58f))
                cubicTo(x(.02f), y(.34f), x(.38f), y(.18f), x(.50f), y(.02f))
                close()
                moveTo(x(.44f), y(.70f)); lineTo(x(.37f), y(1f))
                lineTo(x(.63f), y(1f)); lineTo(x(.56f), y(.70f)); close()
            }
            drawPath(p, color)
        }
        Suit.CLUB -> {
            val r = w * .23f
            drawCircle(color, r, Offset(x(.5f), y(.30f)))
            drawCircle(color, r, Offset(x(.27f), y(.58f)))
            drawCircle(color, r, Offset(x(.73f), y(.58f)))
            val stem = Path().apply {
                moveTo(x(.44f), y(.58f)); lineTo(x(.37f), y(1f))
                lineTo(x(.63f), y(1f)); lineTo(x(.56f), y(.58f)); close()
            }
            drawPath(stem, color)
        }
    }
}

@Composable
fun SuitGlyph(suit: Suit, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawSuitShape(suit, color, 0f, 0f, size.width, size.height) }
}

/* ------------------------------------------------------------------ *
 * 3. 牌面 / 牌背
 * ------------------------------------------------------------------ */

/** 点阵点数字符：A→a，10→t，J/Q/K→j/q/k。 */
private fun rankGlyph(rank: Rank): String = when (rank) {
    Rank.ACE -> "a"
    Rank.TEN -> "t"
    Rank.JACK -> "j"
    Rank.QUEEN -> "q"
    Rank.KING -> "k"
    else -> rank.label
}

/**
 * 一张牌的正脸（不含外框与点击层，由 [PlayingCard] 叠加）。
 *
 * 版面按 38×26dp 的实际比例排：
 * - 左上角点阵点数，7 行像素高约 10dp —— 这是整张牌最重要的信息；
 * - 点数正下方一枚 4dp 小花色，与点数构成标准扑克牌的「索引块」；
 * - 中央 13dp 大花色，J/Q/K 换成点阵字母，因为人像在这个尺寸下没有辨识度；
 * - 细内框 + 极淡纸纹，让白底不至于糊成一块。
 */
@Composable
fun CardFaceArt(card: Card, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val ink = if (card.suit.isRed) Pal.SuitRed else Pal.SuitBlack

        // 底色按增强变化 —— 20dp 高的牌面上，**颜色**是唯一还能被眼睛
        // 分辨的增强标记，任何花纹/图标都会糊成噪点。
        val faceColor = when (card.enh) {
            Enhancement.GOLD -> Pal.Gold
            Enhancement.STONE -> Color(0xFFB4B9B6)
            Enhancement.STEEL -> Color(0xFFDCE3E5)
            Enhancement.GLASS -> Pal.CardFaceAlt
            Enhancement.LUCKY -> Color(0xFFEFF4E4)
            else -> Pal.CardFace
        }
        drawRect(faceColor)

        // 纸纹：四道极淡横线 + 边缘老化。
        //
        // 试过网点（halftone），但在 25dp 高的牌面上点阵会糊成一层灰，
        // 反而像脏了。这个尺寸下唯一读得出来的"纸"是**不均匀**：
        // 中间干净、四角发暗，所以改用径向暗角，横线只留极淡的几道。
        var ty = h * .20f
        while (ty < h) {
            drawLine(Pal.CardInner.copy(alpha = .16f), Offset(0f, ty), Offset(w, ty), .5f)
            ty += h * .30f
        }
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color.Transparent, Color(0xFF7A6A50).copy(alpha = .16f)),
                center = Offset(w * .5f, h * .42f),
                radius = w * .78f,
            )
        )

        // 内框：双细线压印（1999 的装订边），外线略深、内线极淡
        val inset = (w * .055f).coerceAtLeast(1f)
        drawRect(
            if (card.enh == Enhancement.STEEL) Pal.FeltHi else Pal.CardInner.copy(alpha = .85f),
            Offset(inset, inset),
            Size(w - inset * 2, h - inset * 2),
            style = Stroke((w * .022f).coerceAtLeast(.7f)),
        )
        val inset2 = inset + (w * .042f).coerceAtLeast(.7f)
        if (w - inset2 * 2 > 1f) {
            drawRect(
                Pal.CardInner.copy(alpha = .38f),
                Offset(inset2, inset2),
                Size(w - inset2 * 2, h - inset2 * 2),
                style = Stroke((w * .010f).coerceAtLeast(.3f)),
            )
        }

        // ---- 顶部双层通栏色带：上层 = 版本，下层 = 增强 ----
        //
        // 上一版把版本画成 1.2dp 顶带、把增强画成右上角 2dp 的小方块。
        // 这两个位置**恰好都落在手牌被相邻牌盖住的右段**，手牌一摊成扇形
        // 就全被吃掉 —— 玩家反馈的「特殊手牌标识不明显」正是这个原因。
        //
        // 现在两条都改成通栏横带：扇形只盖右边，每张牌左侧那 18dp 永远露着，
        // 所以颜色一定看得见。颜色本身就是原作的编码（蓝=+筹码、红=+倍率、
        // 四色=万能、青=玻璃、银=钢铁、金=黄金、绿=幸运）。
        val edH = (h * .085f).coerceAtLeast(1.6f)
        when (card.edition) {
            Edition.FOIL -> drawRect(Pal.Chip, Offset(0f, 0f), Size(w, edH))
            Edition.HOLO -> drawRect(Pal.Purple, Offset(0f, 0f), Size(w, edH))
            Edition.POLY -> {
                val seg = w / 4f
                listOf(Pal.SuitRed, Pal.Money, Pal.Green, Pal.Chip).forEachIndexed { i, c ->
                    drawRect(c, Offset(i * seg, 0f), Size(seg + .5f, edH))
                }
            }
            Edition.NEGATIVE -> drawRect(Pal.Legendary, Offset(0f, 0f), Size(w, edH))
            Edition.BASE -> Unit
        }
        val enhH = (h * .075f).coerceAtLeast(1.3f)
        val enhTop = if (card.edition == Edition.BASE) 0f else edH
        when (card.enh) {
            Enhancement.BONUS -> drawRect(Pal.Chip, Offset(0f, enhTop), Size(w, enhH))
            Enhancement.MULT -> drawRect(Pal.Mult, Offset(0f, enhTop), Size(w, enhH))
            Enhancement.WILD -> {
                val q = w / 4f
                listOf(Pal.SuitRed, Pal.Money, Pal.Chip, Pal.Green).forEachIndexed { i, c ->
                    drawRect(c, Offset(i * q, enhTop), Size(q + .5f, enhH))
                }
            }
            Enhancement.LUCKY -> drawRect(Pal.Green, Offset(0f, enhTop), Size(w, enhH))
            Enhancement.GLASS -> drawRect(Color(0xFF9FC7DA), Offset(0f, enhTop), Size(w, enhH))
            Enhancement.STEEL -> drawRect(Color(0xFF9AA7AB), Offset(0f, enhTop), Size(w, enhH))
            Enhancement.GOLD -> drawRect(Pal.Gold, Offset(0f, enhTop), Size(w, enhH))
            Enhancement.STONE -> drawRect(Color(0xFF8A908D), Offset(0f, enhTop), Size(w, enhH))
            Enhancement.NONE -> Unit
        }

        // 石头牌：没有点数没有花色，只画一个石块轮廓
        if (card.enh.stone) {
            val r = (h * .30f).coerceAtLeast(3f)
            val cx = w / 2f
            val cy = h / 2f
            val p = Path().apply {
                moveTo(cx - r, cy + r * .6f)
                lineTo(cx - r * .7f, cy - r * .5f)
                lineTo(cx + r * .2f, cy - r * .9f)
                lineTo(cx + r, cy + r * .1f)
                lineTo(cx + r * .6f, cy + r * .7f)
                close()
            }
            drawPath(p, Color(0xFF8A908D))
            drawPath(p, Pal.CardEdge, style = Stroke((w * .03f).coerceAtLeast(.8f)))
            return@Canvas
        }

        // 左上角索引块：点数 + 花色。整体下移到色带之下，
        // 否则点数的第一行会被增强带切掉。
        val px = (h * .055f).coerceAtLeast(.68f)
        val g = rankGlyph(card.rank)
        val bandBottom = enhTop + if (card.enh == Enhancement.NONE) 0f else enhH
        val gx = w * .11f
        val gy = bandBottom + h * .035f
        drawPixelText(g, ink, gx, gy, px)
        val suitTop = gy + GLYPH_ROWS * px + h * .04f
        val ss = (h * .21f).coerceAtLeast(2.6f)
        drawSuitShape(card.suit, ink, gx, suitTop, ss, ss)

        // 中央：一律画大花色。
        // 曾经在 J/Q/K 中央放点阵字母，但 25dp 高的牌面上 5×7 的 K、Q
        // 只剩一团像素，认出「这是张 K」靠的其实是左上角的索引块。
        // 所以中央统一用花色，宫牌改用花色上方的三齿冠冕做区分。
        val court = card.rank == Rank.JACK ||
            card.rank == Rank.QUEEN || card.rank == Rank.KING
        val cs = (h * .62f).coerceAtLeast(5f)
        val csx = w * .58f - cs / 2f
        val csy = h * .24f
        if (court) {
            // 冠冕：三个小方块 + 一条底边
            val u = (w * .045f).coerceAtLeast(.8f)
            val crownY = h * .16f
            val crownX = w * .58f - u * 2.5f
            drawRect(ink, Offset(crownX, crownY + u), Size(u * 5, u * .8f))
            for (i in 0 until 3) {
                drawRect(ink, Offset(crownX + i * u * 2f, crownY - u * .6f), Size(u, u * 1.6f))
            }
            drawSuitShape(card.suit, ink, csx, csy + h * .06f, cs * .86f, cs * .86f)
        } else {
            drawSuitShape(card.suit, ink, csx, csy, cs, cs)
        }

        // ---- 蜡封：左下角蜡点 ----
        //
        // 位置从右下改到左下：扇形手牌盖住的是右段，蜡封放在右边等于看不见。
        // 外加一圈深色描边 + 一圈高光，白牌面、金卡面、银卡面上都能读出来。
        if (!card.seal.isNone) {
            val r = (h * .085f).coerceAtLeast(1.6f)
            val c = when (card.seal) {
                Seal.RED -> Pal.SuitRed
                Seal.GOLD -> Pal.Money
                Seal.BLUE -> Pal.Chip
                Seal.PURPLE -> Pal.Purple
                Seal.NONE -> Pal.CardEdge
            }
            val cx = w * .11f + r * .6f
            val cy = h - r - (h * .035f)
            drawCircle(Pal.FeltDark.copy(alpha = .55f), r * 1.55f, Offset(cx, cy))
            drawCircle(c, r, Offset(cx, cy))
            drawCircle(Pal.CardFace.copy(alpha = .55f), r * .34f, Offset(cx - r * .3f, cy - r * .3f))
        }
    }
}


/**
 * 牌背：1999 的**深咖 + 烫金细线 + 四角星**，不是 Bicycle 斜网。
 *
 * 牌背在牌桌上是"背面朝上"的常驻元素，占的视觉面积比任何一张牌都大 ——
 * 它决定整块牌桌的年代感。这里用装订边（双线）+ 徽记（四角星）+ 雨滴斜纹
 * 三件套替换原来的红底斜网。
 *
 * 上半部分会被手牌槽位裁掉，所以徽记压在正中偏上。
 */
@Composable
fun CardBackArt(modifier: Modifier = Modifier, base: Color = Pal.CardBack) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(base)

        // 雨滴斜纹：疏、细、低透明度 —— 密了会变成灰糊
        val step = (w / 6.0f).coerceAtLeast(1.4f)
        var k = -h
        while (k < w + h) {
            drawLine(
                Pal.Rain.copy(alpha = .15f),
                Offset(k, 0f), Offset(k - h * .32f, h), .45f,
            )
            k += step
        }

        // 烫金双线框（外粗内细，模拟压印）
        val inset = w * .075f
        drawRect(
            Pal.Amber400.copy(alpha = .58f),
            Offset(inset, inset),
            Size(w - inset * 2, h - inset * 2),
            style = Stroke((w * .018f).coerceAtLeast(.5f)),
        )
        val inset2 = inset * 1.62f
        drawRect(
            Pal.Amber400.copy(alpha = .22f),
            Offset(inset2, inset2),
            Size(w - inset2 * 2, h - inset2 * 2),
            style = Stroke((w * .011f).coerceAtLeast(.32f)),
        )

        // 中心四角星：八点折线（四尖 + 四凹），比曲线更利落，也更像印刷符号
        val cx = w / 2f
        val cy = h * .46f
        val r = (w * .23f).coerceAtLeast(2.4f)
        val inner = r * .26f
        val star = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + inner, cy - inner)
            lineTo(cx + r, cy)
            lineTo(cx + inner, cy + inner)
            lineTo(cx, cy + r)
            lineTo(cx - inner, cy + inner)
            lineTo(cx - r, cy)
            lineTo(cx - inner, cy - inner)
            close()
        }
        drawPath(star, Pal.Amber400.copy(alpha = .88f))
        drawCircle(base, r * .21f, Offset(cx, cy))
        drawCircle(Pal.Paper100.copy(alpha = .92f), r * .075f, Offset(cx, cy))
    }
}

/* ------------------------------------------------------------------ *
 * 4. 18 个小丑：8×8 像素头像，每个一张不同的脸
 * ------------------------------------------------------------------ */

private fun DrawScope.pixelGrid(draw: DrawScope.(px: (Int, Int, Color, Int, Int) -> Unit) -> Unit) {
    val u = size.minDimension / 8f
    val ox = (size.width - u * 8) / 2f
    val oy = (size.height - u * 8) / 2f
    drawRect(Pal.SurfaceHigh)
    val px: (Int, Int, Color, Int, Int) -> Unit = { x, y, c, w, h ->
        drawRect(c, Offset(ox + x * u, oy + y * u), Size(u * w, u * h))
    }
    this.draw(px)
}

@Composable
fun JokerPortrait(id: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val K = Pal.CardEdge
        val W = Pal.CardFace
        val R = Pal.SuitRed
        val B = Pal.Chip
        val G = Pal.Green
        val Y = Pal.Money
        val P = Pal.Purple
        val S = Pal.JokerGrey
        val D = Pal.WineRed

        pixelGrid { px ->
            when (id) {
                "joker" -> { // 小丑帽 + 笑脸
                    px(2, 0, R, 4, 1); px(1, 1, R, 6, 1); px(3, 0, Y, 1, 1)
                    px(1, 2, W, 6, 4); px(2, 3, K, 1, 1); px(5, 3, K, 1, 1)
                    px(3, 5, R, 2, 1); px(1, 6, K, 6, 1)
                }
                "sly" -> { // 斜眼 + 歪帽
                    px(1, 1, B, 6, 1); px(5, 0, B, 2, 1)
                    px(1, 2, W, 6, 4); px(2, 3, K, 2, 1); px(5, 3, K, 2, 1)
                    px(3, 5, D, 3, 1); px(1, 6, K, 6, 1)
                }
                "jolly" -> { // 大笑
                    px(1, 1, G, 6, 5); px(2, 2, K, 1, 1); px(5, 2, K, 1, 1)
                    px(2, 4, K, 4, 2); px(3, 4, W, 2, 1); px(1, 6, K, 6, 1)
                }
                "zany" -> { // 螺旋眼
                    px(1, 1, P, 6, 5); px(2, 2, W, 2, 2); px(5, 2, W, 2, 2)
                    px(2, 2, K, 1, 1); px(5, 3, K, 1, 1); px(3, 5, Y, 2, 1)
                }
                "mad" -> { // 火焰
                    px(3, 0, R, 2, 2); px(2, 1, Y, 4, 2); px(1, 3, R, 6, 3)
                    px(2, 4, Y, 4, 2); px(3, 6, W, 2, 1)
                }
                "clever" -> { // 眼镜
                    px(1, 1, W, 6, 5); px(1, 2, K, 3, 2); px(4, 2, K, 3, 2)
                    px(2, 3, B, 1, 1); px(5, 3, B, 1, 1); px(3, 3, K, 2, 1)
                    px(3, 5, D, 2, 1); px(1, 6, K, 6, 1)
                }
                "greedy" -> { // 方块
                    px(3, 1, R, 2, 1); px(2, 2, R, 4, 1); px(1, 3, R, 6, 2)
                    px(2, 5, R, 4, 1); px(3, 6, R, 2, 1); px(3, 3, Y, 2, 2)
                }
                "lusty" -> { // 红桃
                    px(1, 1, R, 3, 2); px(4, 1, R, 3, 2); px(1, 3, R, 6, 2)
                    px(2, 5, R, 4, 1); px(3, 6, R, 2, 1); px(3, 2, W, 2, 1)
                }
                "wrathful" -> { // 黑桃
                    px(3, 0, K, 2, 2); px(2, 2, K, 4, 2); px(1, 4, K, 6, 2)
                    px(3, 6, K, 2, 2); px(3, 3, R, 2, 1)
                }
                "gluttonous" -> { // 梅花
                    px(3, 0, K, 2, 3); px(1, 2, K, 3, 3); px(4, 2, K, 3, 3)
                    px(3, 5, K, 2, 3); px(3, 3, G, 2, 1)
                }
                "even" -> { // 偶数方块 2 4 6
                    px(1, 1, B, 6, 6); px(2, 2, W, 2, 1); px(4, 2, W, 2, 1)
                    px(2, 4, W, 2, 1); px(4, 4, W, 2, 1); px(2, 6, Y, 4, 1)
                }
                "odd" -> { // 奇数 1 3 5
                    px(1, 1, D, 6, 6); px(2, 2, W, 1, 4); px(4, 2, W, 3, 1)
                    px(4, 4, W, 3, 1); px(4, 6, W, 3, 1); px(2, 6, Y, 1, 1)
                }
                "abstract" -> { // 几何拼贴
                    px(0, 0, R, 4, 4); px(4, 0, B, 4, 4); px(0, 4, Y, 4, 4)
                    px(4, 4, G, 4, 4); px(3, 3, W, 2, 2); px(2, 2, K, 1, 1)
                }
                "banner" -> { // 旗帜
                    px(1, 0, K, 1, 8); px(2, 1, G, 5, 3); px(2, 4, W, 5, 1)
                    px(2, 5, R, 5, 2); px(1, 7, K, 3, 1)
                }
                "photograph" -> { // 相框里的风景
                    px(1, 1, W, 6, 6); px(2, 2, B, 4, 3); px(2, 5, G, 4, 1)
                    px(3, 3, Y, 1, 1); px(0, 0, K, 8, 1); px(0, 7, K, 8, 1)
                }
                "blackboard" -> { // 黑板 + x3
                    px(1, 1, K, 6, 6); px(2, 2, G, 4, 1); px(2, 4, W, 4, 1)
                    px(3, 3, R, 2, 1); px(2, 6, Y, 4, 1)
                }
                "baron" -> { // 皇冠 + K
                    px(1, 1, Y, 6, 2); px(1, 0, Y, 1, 2); px(3, 0, Y, 2, 2)
                    px(6, 0, Y, 1, 2); px(2, 3, R, 4, 4); px(3, 4, W, 2, 2)
                }
                "obelisk" -> { // 方尖碑
                    px(3, 0, Y, 2, 2); px(2, 2, P, 4, 5); px(1, 7, K, 6, 1)
                    px(3, 3, W, 2, 1); px(3, 5, W, 2, 1)
                }
                else -> {
                    // 新增的小丑没有逐个手绘头像，这里用 id 的哈希生成一张
                    // **确定性**的脸：同一个小丑永远同一张，不同小丑之间颜色、
                    // 帽型、眼型、嘴型都不同 —— 缩到 13dp 也能看出"这是另一张牌"。
                    // 手绘 37 张 8×8 至少 200 行，收益比不上把空间留给界面布局。
                    val hs = id.hashCode()
                    val skins = listOf(W, Y, S, Color(0xFFE8C9A0))
                    val hats = listOf(R, B, G, P, D)
                    val skin = skins[(hs ushr 3).mod(skins.size)]
                    val hat = hats[(hs ushr 7).mod(hats.size)]
                    when ((hs ushr 11).mod(5)) {
                        0 -> { px(2, 0, hat, 4, 1); px(1, 1, hat, 6, 1) }
                        1 -> { px(3, 0, hat, 2, 1); px(2, 1, hat, 4, 1) }
                        2 -> { px(1, 0, hat, 2, 1); px(5, 0, hat, 2, 1); px(1, 1, hat, 6, 1) }
                        3 -> px(2, 1, hat, 4, 1)
                        else -> Unit
                    }
                    px(1, 2, skin, 6, 4)
                    when ((hs ushr 13).mod(4)) {
                        0 -> { px(2, 3, K, 1, 1); px(5, 3, K, 1, 1) }
                        1 -> { px(2, 3, K, 2, 1); px(4, 3, K, 2, 1) }
                        2 -> px(3, 3, K, 2, 1)
                        else -> { px(2, 3, R, 1, 1); px(5, 3, R, 1, 1) }
                    }
                    when ((hs ushr 17).mod(4)) {
                        0 -> px(3, 5, R, 2, 1)
                        1 -> px(2, 5, R, 4, 1)
                        2 -> { px(2, 5, R, 1, 1); px(5, 5, R, 1, 1) }
                        else -> px(3, 4, R, 2, 2)
                    }
                    px(1, 6, K, 6, 1)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 5. 赌桌毡 + CRT 扫描线 + 光标
 * ------------------------------------------------------------------ */

/**
 * 背景：1999 的**深咖低长调**，不是赌桌毡。
 *
 * 结构照 `DESIGN_LANGUAGE.md` §3.3 的「低长调」——整体压暗，只留一处极亮高光；
 * 材质照 §3.4 的「油墨 + 纸张」——纤维短划、极细雨滴斜线、外圈压印双线。
 *
 * 上一版是墨绿毡 + 三个缓慢旋转的六边形，属于"赌场"语义；现在换成
 * "档案 / 老照片"语义：底色是纸背，雨是世界观母题，双线是装订边。
 * 全部确定性绘制（用质数取模代替随机），重组不会闪。
 */
@Composable
fun FeltBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // 低长调底：中心偏上一点最亮（像一盏顶灯打在纸上），边缘落黑
        drawRect(
            Brush.radialGradient(
                listOf(Pal.Ink800, Pal.Ink900, Color(0xFF110E0B)),
                center = Offset(cx, cy * .86f),
                radius = w * .80f,
            )
        )

        // 暴雨母题：极细斜线，只落在画布外围（8:2 里的那 2 成）
        var i = 0
        while (i < 30) {
            val t = ((i * 7919) % 101) / 101f
            val ph = ((i * 104729) % 97) / 97f
            val x0 = -w * .12f + t * w * 1.24f
            val y0 = ph * h
            val len = h * (.09f + (i % 4) * .035f)
            drawLine(
                Pal.Rain.copy(alpha = .055f),
                Offset(x0, y0),
                Offset(x0 - len * .32f, y0 + len),
                .55f,
            )
            i++
        }

        // 纸张纤维：短横划，比圆点更像纸（圆点在小屏上会读成脏污）
        for (k in 0 until 210) {
            val x = ((k * 7919) % 211) / 211f * w
            val y = ((k * 104729) % 223) / 223f * h
            val l = 1.1f + ((k * 31) % 5) * .7f
            drawLine(Pal.Paper100.copy(alpha = .020f), Offset(x, y), Offset(x + l, y), .6f)
        }

        // 装订边：两圈极淡的压印线，贴着圆屏边缘
        val rOuter = w * .5f - 1.5.dp.toPx()
        drawCircle(
            Pal.Amber400.copy(alpha = .10f), rOuter, Offset(cx, cy),
            style = Stroke(.8f),
        )
        drawCircle(
            Pal.Amber400.copy(alpha = .055f), rOuter - 3.dp.toPx(), Offset(cx, cy),
            style = Stroke(.6f),
        )

        // 暗角
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .55f)),
                center = Offset(cx, cy),
                radius = w * .73f,
            )
        )
    }
}

/** 全局 CRT 扫描线。放在最上层，不注册任何 pointerInput，触摸会穿透到下面。 */
@Composable
fun CrtOverlay(modifier: Modifier = Modifier, level: Int = 2) {
    // level：0 关 / 1 弱 / 2 中 / 3 强。原来写死 0.18，设置页要能调，
    // 就把强度提成一个乘子 —— 关掉时直接不画，省掉整层 Canvas 的开销。
    if (level <= 0) return
    val k = when (level) {
        1 -> 0.45f
        3 -> 1.7f
        else -> 1f
    }
    Canvas(modifier.fillMaxSize()) {
        val step = 3.dp.toPx()
        val sw = 0.7.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(Color.Black.copy(alpha = (.18f * k).coerceAtMost(.55f)),
                Offset(0f, y), Offset(size.width, y), sw)
            y += step
        }
        drawRect(
            Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = (.30f * k).coerceAtMost(.6f)),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = (.30f * k).coerceAtMost(.6f)),
                )
            )
        )
    }
}

/**
 * 表冠光标：**四角星**，替代上一版的四角 L 线。
 *
 * 四角星是 1999 的点击态核心元素（`DESIGN_LANGUAGE.md` §3.6「四角星（点击态核心元素）」、
 * §3.7「点击态由中心向外圆形扩散，中心是闪光四角星」）。用四个尖角标记选中，
 * 方向感比线段强，在 4dp 尺度上也不糊。
 */
@Composable
fun CursorCorners(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = (minOf(w, h) * .14f).coerceIn(1.3f, 4.0.dp.toPx())
        val inner = r * .26f
        val pad = r * .92f
        fun star(cx: Float, cy: Float) {
            val p = Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + inner, cy - inner)
                lineTo(cx + r, cy)
                lineTo(cx + inner, cy + inner)
                lineTo(cx, cy + r)
                lineTo(cx - inner, cy + inner)
                lineTo(cx - r, cy)
                lineTo(cx - inner, cy - inner)
                close()
            }
            drawPath(p, color)
        }
        star(pad, pad)
        star(w - pad, pad)
        star(pad, h - pad)
        star(w - pad, h - pad)
    }
}

/**
 * 独立的四角星，供菜单分隔、结算标记、空槽位复用。
 *
 * 和 [CursorCorners] 的区别：这个画在容器正中，尺寸由容器决定；
 * 里面没有内圆，是纯实心星（1999 的星星很少画装饰内圈）。
 */
@Composable
fun FourPointStar(color: Color, modifier: Modifier = Modifier, shape: Float = 1f) {
    Canvas(modifier) {
        val r = minOf(size.width, size.height) * .5f * shape.coerceIn(0f, 1f)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val inner = r * .24f
        val p = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + inner, cy - inner)
            lineTo(cx + r, cy)
            lineTo(cx + inner, cy + inner)
            lineTo(cx, cy + r)
            lineTo(cx - inner, cy + inner)
            lineTo(cx - r, cy)
            lineTo(cx - inner, cy - inner)
            close()
        }
        drawPath(p, color)
    }
}

/** 标题用的立体字：两层同字叠放，底层偏移 1.5dp 做厚度。 */
@Composable
fun EmbossedTitle(
    text: String,
    color: Color,
    depthColor: Color,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        T(
            text,
            style.copy(color = depthColor),
            modifier = Modifier.offset(1.5.dp, 1.5.dp),
            align = androidx.compose.ui.text.style.TextAlign.Center,
        )
        T(
            text,
            style.copy(color = color),
            align = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/* ------------------------------------------------------------------ *
 * 7. 消耗品 / 代金券图标
 * ------------------------------------------------------------------ */

/** 行星牌按牌型给一个稳定色调，让玩家"看颜色就知道是哪张行星"。 */
private fun planetTint(t: HandType?): Color = when (t) {
    HandType.HIGH_CARD -> Color(0xFF9AA7B4)
    HandType.PAIR -> Color(0xFF6FB1E0)
    HandType.TWO_PAIR -> Color(0xFF7ED0B0)
    HandType.THREE_OF_A_KIND -> Color(0xFFE0B76F)
    HandType.STRAIGHT -> Color(0xFFD98CC0)
    HandType.FLUSH -> Color(0xFF5FA8D8)
    HandType.FULL_HOUSE -> Color(0xFF89C36A)
    HandType.FOUR_OF_A_KIND -> Color(0xFFE0685C)
    HandType.STRAIGHT_FLUSH -> Color(0xFFB98CE0)
    HandType.FIVE_OF_A_KIND -> Color(0xFFE0A45C)
    HandType.FLUSH_HOUSE -> Color(0xFF63C7C7)
    HandType.FLUSH_FIVE -> Color(0xFFE07A9A)
    null -> Color(0xFF9AA7B4)
}

/**
 * 消耗品图标（13~24dp）。
 *
 * 塔罗 = 深紫卡背 + 一个按 key 派生的白色符号；
 * 行星 = 深蓝天体 + 一颗按牌型着色的行星 + 一道斜向轨道。
 * 两者都是 3~4 个图元，微尺寸下反而比精细插画更清楚。
 */
@Composable
fun ConsumableArt(c: Consumable, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val u = minOf(w, h)

        if (c.kind == ConsumableKind.SPECTRAL) {
            // 幻灵：近黑紫底 + 一颗发光的魂体。原版幻灵牌是紫底白描边，
            // 但在 20dp 里描边会糊成一团，所以改成"暗底 + 亮体"的高反差画法。
            drawRect(Color(0xFF140A20))
            val cx = w / 2f
            val cy = h * .54f
            val r = u * .19f
            drawCircle(Pal.Purple.copy(alpha = .26f), r * 2.2f, Offset(cx, cy))
            drawCircle(Pal.Purple.copy(alpha = .16f), r * 3.0f, Offset(cx, cy))
            val ink = Color(0xFFEDE2FA)
            // 泪滴：上方一个尖，下方一个圆
            val flame = Path().apply {
                moveTo(cx, cy - r * 2.3f)
                lineTo(cx - r * 1.05f, cy - r * .1f)
                lineTo(cx + r * 1.05f, cy - r * .1f)
                close()
            }
            drawPath(flame, ink)
            drawCircle(ink, r, Offset(cx, cy))
            // 两个眼洞 —— 有洞才像"魂"，没洞像一颗水滴
            drawCircle(Color(0xFF140A20), r * .26f, Offset(cx - r * .40f, cy))
            drawCircle(Color(0xFF140A20), r * .26f, Offset(cx + r * .40f, cy))
        } else if (c.kind == ConsumableKind.PLANET) {
            drawRect(Color(0xFF101E36))
            // 星点：四角各一颗小方点，确定性分布
            val dot = (u * .05f).coerceAtLeast(.6f)
            drawRect(Color(0xFF3E5C86), Offset(w * .12f, h * .16f), Size(dot, dot))
            drawRect(Color(0xFF3E5C86), Offset(w * .78f, h * .22f), Size(dot, dot))
            drawRect(Color(0xFF3E5C86), Offset(w * .22f, h * .80f), Size(dot, dot))
            drawRect(Color(0xFF3E5C86), Offset(w * .70f, h * .74f), Size(dot, dot))

            val cx = w / 2f
            val cy = h / 2f
            val r = u * .23f
            val tint = planetTint(c.handType)
            // 轨道：一道穿过圆心的斜线，两端超出球体
            drawLine(
                tint.copy(alpha = .45f),
                Offset(cx - r * 2.0f, cy + r * .9f),
                Offset(cx + r * 2.0f, cy - r * .9f),
                (u * .07f).coerceAtLeast(1f),
            )
            drawCircle(tint, r, Offset(cx, cy))
            drawCircle(tint.copy(alpha = .45f), r * .55f, Offset(cx - r * .32f, cy - r * .30f))
        } else {
            drawRect(Color(0xFF2A1B38))
            val inset = (u * .07f).coerceAtLeast(.7f)
            drawRect(
                Pal.Purple.copy(alpha = .85f),
                Offset(inset, inset),
                Size(w - inset * 2, h - inset * 2),
                style = Stroke((u * .035f).coerceAtLeast(.7f)),
            )
            val cx = w / 2f
            val cy = h * .46f
            val r = u * .24f
            val ink = Color(0xFFEFE6F4)
            when (c.key.hashCode().mod(6)) {
                0 -> { // 星
                    drawLine(ink, Offset(cx - r, cy), Offset(cx + r, cy), (u * .06f).coerceAtLeast(.8f))
                    drawLine(ink, Offset(cx, cy - r), Offset(cx, cy + r), (u * .06f).coerceAtLeast(.8f))
                    drawLine(
                        ink.copy(alpha = .6f),
                        Offset(cx - r * .7f, cy - r * .7f),
                        Offset(cx + r * .7f, cy + r * .7f),
                        (u * .04f).coerceAtLeast(.6f),
                    )
                }
                1 -> { // 月
                    drawCircle(ink, r, Offset(cx, cy))
                    drawCircle(Color(0xFF2A1B38), r * .78f, Offset(cx + r * .42f, cy - r * .18f))
                }
                2 -> { // 眼
                    drawCircle(ink.copy(alpha = .9f), r * .95f, Offset(cx, cy), style = Stroke((u * .05f).coerceAtLeast(.7f)))
                    drawCircle(ink, r * .30f, Offset(cx, cy))
                }
                3 -> { // 塔
                    drawRect(ink, Offset(cx - r * .45f, cy - r * .5f), Size(r * .9f, r * 1.5f))
                    drawRect(ink, Offset(cx - r * .75f, cy - r * .95f), Size(r * 1.5f, r * .45f))
                }
                4 -> { // 轮
                    drawCircle(ink, r, Offset(cx, cy), style = Stroke((u * .05f).coerceAtLeast(.7f)))
                    drawCircle(ink, r * .22f, Offset(cx, cy))
                    drawLine(ink, Offset(cx - r, cy), Offset(cx + r, cy), (u * .04f).coerceAtLeast(.6f))
                    drawLine(ink, Offset(cx, cy - r), Offset(cx, cy + r), (u * .04f).coerceAtLeast(.6f))
                }
                else -> { // 剑
                    drawLine(ink, Offset(cx, cy - r), Offset(cx, cy + r), (u * .07f).coerceAtLeast(.8f))
                    drawLine(
                        ink,
                        Offset(cx - r * .6f, cy + r * .25f),
                        Offset(cx + r * .6f, cy + r * .25f),
                        (u * .05f).coerceAtLeast(.7f),
                    )
                }
            }
        }
    }
}

/** 代金券图标：金色票券 + 左右锯齿 + 中央一个按 key 派生的小符号。 */
@Composable
fun VoucherArt(v: Voucher, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val u = minOf(w, h)
        drawRect(Pal.GoldDim)
        drawRect(Pal.Gold, Offset(0f, 0f), Size(w, h * .18f))
        // 左右锯齿
        val tooth = (u * .10f).coerceAtLeast(.9f)
        var y = 0f
        val bg = Pal.FeltDark
        while (y < h) {
            drawRect(bg, Offset(0f, y), Size(tooth, tooth))
            drawRect(bg, Offset(w - tooth, y + tooth * .5f), Size(tooth, tooth))
            y += tooth * 2f
        }
        val cx = w / 2f
        val cy = h / 2f
        val r = u * .22f
        when (v.key.hashCode().mod(4)) {
            0 -> drawRect(Pal.FeltDark, Offset(cx - r, cy - r), Size(r * 2, r * 2))
            1 -> drawCircle(Pal.FeltDark, r, Offset(cx, cy))
            2 -> {
                drawRect(Pal.FeltDark, Offset(cx - r, cy - r * .4f), Size(r * 2, r * .8f))
                drawRect(Pal.FeltDark, Offset(cx - r * .4f, cy - r), Size(r * .8f, r * 2))
            }
            else -> {
                drawLine(Pal.FeltDark, Offset(cx - r, cy + r), Offset(cx, cy - r), (u * .10f).coerceAtLeast(.9f))
                drawLine(Pal.FeltDark, Offset(cx + r, cy + r), Offset(cx, cy - r), (u * .10f).coerceAtLeast(.9f))
            }
        }
    }
}


/* ------------------------------------------------------------------ *
 * 8. 补充包图标
 * ------------------------------------------------------------------ */

/**
 * 补充包图标。
 *
 * 原版每种包有自己的封面插画（塔罗包画愚者、行星包画木星…）。手表上
 * 15~22dp 见方的位置塞不下插画，所以退回"色块 + 类型符号 + 封条"：
 * 塔罗紫、行星蓝、标准白、小丑红，右边一条金封条表示"未拆"。
 * 这个编码方式与原版一致 —— 玩家在原版里也是靠颜色区分包的。
 */
@Composable
fun BoosterArt(kind: PackKind, packSize: PackSize, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // 注意：参数名不能叫 size —— 会把 DrawScope.size 遮蔽掉
        val w = this.size.width
        val h = this.size.height
        val u = minOf(w, h)
        val body = when (kind) {
            PackKind.ARCANA -> Color(0xFF3A2350)
            PackKind.CELESTIAL -> Color(0xFF122A44)
            PackKind.STANDARD -> Color(0xFF2E3F42)
            PackKind.BUFFOON -> Color(0xFF4A2222)
            PackKind.SPECTRAL -> Color(0xFF241436)
        }
        val ink = when (kind) {
            PackKind.ARCANA -> Pal.Purple
            PackKind.CELESTIAL -> Pal.Chip
            PackKind.STANDARD -> Pal.CardFace
            PackKind.BUFFOON -> Pal.SuitRed
            PackKind.SPECTRAL -> Pal.Legendary
        }
        drawRect(body)
        // 外框
        val inset = (u * .06f).coerceAtLeast(.6f)
        drawRect(
            ink.copy(alpha = .8f),
            Offset(inset, inset),
            Size(w - inset * 2, h - inset * 2),
            style = Stroke((u * .05f).coerceAtLeast(.7f)),
        )
        // 封条：右侧一条金带 + 一颗铆钉
        val bandW = (w * .16f).coerceAtLeast(1.2f)
        drawRect(Pal.Gold.copy(alpha = .9f), Offset(w - bandW - inset, inset + .5f),
            Size(bandW, h - inset * 2 - 1f))
        // 中心符号
        val cx = w * .44f
        val cy = h * .5f
        val r = u * .17f
        when (kind) {
            PackKind.ARCANA -> { // 三角 + 横线（塔罗的"眼"）
                drawLine(ink, Offset(cx - r, cy + r * .8f), Offset(cx, cy - r), (u * .07f).coerceAtLeast(.8f))
                drawLine(ink, Offset(cx, cy - r), Offset(cx + r, cy + r * .8f), (u * .07f).coerceAtLeast(.8f))
                drawLine(ink, Offset(cx - r * .7f, cy + r * .8f), Offset(cx + r * .7f, cy + r * .8f),
                    (u * .06f).coerceAtLeast(.7f))
            }
            PackKind.CELESTIAL -> { // 带环行星
                drawCircle(ink, r * .7f, Offset(cx, cy))
                drawLine(ink.copy(alpha = .55f), Offset(cx - r * 1.4f, cy + r * .5f),
                    Offset(cx + r * 1.4f, cy - r * .5f), (u * .06f).coerceAtLeast(.7f))
            }
            PackKind.STANDARD -> { // 两张叠牌
                drawRect(ink, Offset(cx - r * .8f, cy - r), Size(r * 1.2f, r * 2))
                drawRect(ink.copy(alpha = .5f), Offset(cx - r * .2f, cy - r * .8f), Size(r * 1.2f, r * 2))
            }
            PackKind.BUFFOON -> { // 小丑帽
                drawRect(ink, Offset(cx - r, cy - r * .2f), Size(r * 2, r * .5f))
                drawRect(ink, Offset(cx - r * .5f, cy - r), Size(r, r * .8f))
            }
            PackKind.SPECTRAL -> { // 魂体：菱形轮廓 + 中心核 + 四角星芒
                drawLine(ink, Offset(cx, cy - r), Offset(cx + r * .8f, cy), (u * .06f).coerceAtLeast(.7f))
                drawLine(ink, Offset(cx + r * .8f, cy), Offset(cx, cy + r), (u * .06f).coerceAtLeast(.7f))
                drawLine(ink, Offset(cx, cy + r), Offset(cx - r * .8f, cy), (u * .06f).coerceAtLeast(.7f))
                drawLine(ink, Offset(cx - r * .8f, cy), Offset(cx, cy - r), (u * .06f).coerceAtLeast(.7f))
                drawCircle(ink, r * .3f, Offset(cx, cy))
                drawCircle(Pal.CardFace, r * .12f, Offset(cx, cy))
            }
        }
        // 规格角标：巨型/超大在左下角加一道/两道短杠
        val bars = when (packSize) {
            PackSize.NORMAL -> 0
            PackSize.JUMBO -> 1
            PackSize.MEGA -> 2
        }
        for (i in 0 until bars) {
            val bw = (u * .07f).coerceAtLeast(.7f)
            drawRect(Pal.Money, Offset(w * .12f, h - inset - bw * (i + 1) - i * bw * .3f), Size(bw * 2.2f, bw))
        }
    }
}
