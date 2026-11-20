package com.wearos.wristjoker.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 配色：直接对齐 Balatro 源码里的 `G.C` 常量表
 * （Balatro Wiki · Module:Localization/loadL10n 公开的本地化脚本）。
 *
 * 之所以敢「直接借鉴」：这一层是**功能性的信息编码**，不是美术素材。
 * 蓝=筹码、红=倍率、金=金钱、稀有度四色 —— 玩家靠颜色读数值，
 * 复刻它等于复刻可读性。真正的插画、字体、牌面纹理仍由 `Art.kt`
 * 用 Canvas 逐像素重绘，工程里没有、也不会引入原作的任何二进制资源。
 *
 * 赌桌基调取自 `G.C.BLACK / L_BLACK / GREY`：墨绿灰的 casino felt，
 * 叠 Bicycle 牌背暖红与低透明度旋转多边形，再加 CRT 扫描线与暗角。
 */
object Pal {

    // ---- 赌桌 / 背景（G.C.BLACK=374244, L_BLACK=4f6367, GREY=5f7377）----
    val FeltDark = Color(0xFF16262A)
    val Felt = Color(0xFF374244)
    val FeltLight = Color(0xFF4F6367)
    val FeltHi = Color(0xFF5F7377)
    val WineRed = Color(0xFF8A3030)
    val BossRed = Color(0xFFB44430)       // G.C.BLIND.Boss
    val BlindGreen = Color(0xFF50846E)    // G.C.BLIND.Small / Big
    val Purple = Color(0xFF8867A5)        // G.C.PURPLE

    // ---- 功能色（G.C.MULT / CHIPS / MONEY / GREEN / GOLD）----
    val Chip = Color(0xFF009DFF)
    val Mult = Color(0xFFFE5F55)
    val Money = Color(0xFFF3B958)
    val Gold = Color(0xFFEAC058)
    val Green = Color(0xFF4BC292)
    val PaleGreen = Color(0xFF56A887)
    val Orange = Color(0xFFFDA200)
    val JokerGrey = Color(0xFFBFC7D5)

    // ---- 稀有度（G.C.RARITY 四色，本作只用到前三档）----
    val Common = Color(0xFF009DFF)
    val Uncommon = Color(0xFF4BC292)
    val Rare = Color(0xFFFE5F55)
    val Legendary = Color(0xFFB26CBB)

    // ---- 牌面 ----
    val CardFace = Color(0xFFF4F1E8)
    val CardFaceAlt = Color(0xFFE4E8E1)
    val CardEdge = Color(0xFF374244)
    val CardInner = Color(0xFFCDD9DC)     // G.C.SET.Default
    val CardBack = Color(0xFFB03A3A)      // Bicycle 暖红牌背
    val SuitRed = Color(0xFFFE5F55)       // G.C.SUITS.Hearts / Diamonds
    val SuitBlack = Color(0xFF374649)     // G.C.SUITS.Spades
    val SuitClub = Color(0xFF424E54)      // G.C.SUITS.Clubs

    // ---- 文本 / 面板 ----
    val Cream = Color(0xFFF2EFE6)
    val Dim = Color(0xFF9BB6BD)           // G.C.SECONDARY_SET.Default
    val Ink = Color(0xFF4F6367)           // G.C.UI.TEXT_DARK
    val Bg = FeltDark
    val Surface = Color(0xFF2B3B3E)
    val SurfaceHigh = Color(0xFF3C4E51)
    val Danger = BossRed

    // ---- 兼容旧名的别名（保留，避免遗漏引用）----
    val GoldDim = Color(0xFF8A7338)
    val CardInk = CardEdge
    val CardRed = SuitRed
}

/**
 * 字体层级。
 *
 * 数字一律走等宽（Monospace）—— 原作的分数计数器是机械里程表式，
 * 等宽保证数字跳动时布局不左右晃；标题用无衬线粗体模拟街机高分榜的
 * display face。手表上物理尺寸极小，字号跨度必须拉开才分得清主次。
 */
object Type {
    private val mono = FontFamily.Monospace
    private val display = FontFamily.SansSerif

    val Big = TextStyle(
        fontFamily = mono, fontWeight = FontWeight.Bold,
        fontSize = 18.sp, color = Pal.Cream,
    )
    val Medium = TextStyle(
        fontFamily = mono, fontWeight = FontWeight.Bold,
        fontSize = 14.sp, color = Pal.Cream,
    )
    val Body = TextStyle(fontSize = 10.sp, color = Pal.Cream)
    val Label = TextStyle(fontFamily = mono, fontSize = 9.sp, color = Pal.Dim)
    val Tiny = TextStyle(fontSize = 8.sp, color = Pal.Dim)
    val TinyMono = TextStyle(fontFamily = mono, fontSize = 8.sp, color = Pal.Dim)
    val Button = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, color = Pal.FeltDark,
    )
    val CardRank = TextStyle(
        fontFamily = mono, fontWeight = FontWeight.Bold,
        fontSize = 13.sp, color = Pal.CardEdge,
    )
    val CardRankSmall = TextStyle(
        fontFamily = mono, fontWeight = FontWeight.Bold,
        fontSize = 10.sp, color = Pal.CardEdge,
    )
    val CardSuit = TextStyle(fontSize = 11.sp, color = Pal.CardEdge)
}
