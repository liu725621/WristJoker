package com.wearos.wristjoker.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 配色：**《重返未来：1999》式的复古印刷底 + Balatro 的功能色编码**。
 *
 * 这不是「把 Balatro 涂成咖啡色」，而是一次有取舍的迁移（方法论见
 * `reverse1999-design/DESIGN_LANGUAGE.md` §5：迁移时只换少数维度，锁死其余）：
 *
 * | 维度 | 处理 |
 * |---|---|
 * | 底色 / 影调 | 换成 1999 的深咖低长调（`ink-900`），赌桌的墨绿退到辅助色 |
 * | 材质 | 纸张纤维噪点、油墨黑描边、压印细线 |
 * | 字体 | 大号数字与标题走衬线（Didot 气质），小字仍用无衬线保可读 |
 * | 装饰 | 四角星（点击态）、罗马数字（底注）、雨滴斜线、票根齿孔 |
 * | **功能色** | **语义锁死**：蓝=筹码、红=倍率、金=金钱、绿=正向；只降饱和、统一泛黄 |
 *
 * 最后一行是纪律。1999 的 80% 低饱和规则不能套到数值色上 —— 玩家靠颜色一眼读
 * 「这是筹码还是倍率」，把它们一起灰掉，等于用风格换掉了可玩性。
 *
 * 色值来源：`DESIGN_LANGUAGE.md` §4 的 token 表（由「咖啡基底 + 略微泛黄 + 低饱和」
 * 推导的建议值，非官方取色）。
 */
object Pal {

    // ---- 1999 基础层：深咖（全屏界面主色 60%）----
    val Ink900 = Color(0xFF1B1713)   // 页面底
    val Ink800 = Color(0xFF2C2620)   // 卡片
    val Ink700 = Color(0xFF3B332A)   // 分隔 / 描边

    // ---- 辅助层 30%：灰绿 + 灰橙 ----
    val Moss500 = Color(0xFF5C6455)
    val Moss400 = Color(0xFF6E735E)
    val Rust500 = Color(0xFF9A6A43)
    val Rust400 = Color(0xFFB07B4E)

    // ---- 纸张层 ----
    val Paper100 = Color(0xFFF3EEE1) // 极亮高光（低长调的「长」）
    val Paper300 = Color(0xFFD9CFBA)
    val Sheet100 = Color(0xFFE8E1D1) // 浅黄灰纸张（牌面 / 弹窗）
    val SheetLine = Color(0xFFB9AE97)
    val InkStamp = Color(0xFF241E18) // 油墨黑

    // ---- 重点层 10%：琥珀 ----
    val Amber500 = Color(0xFFD98E4A)
    val Amber400 = Color(0xFFE0A35C)

    // ---- 暴雨（冷色，仅少量）----
    val Rain = Color(0xFF7D8C86)

    // ---- 兼容旧名的别名（全部改指 1999 基底）----
    val FeltDark = Ink900
    val Felt = Ink800
    val FeltLight = Ink700
    val FeltHi = Moss500
    val WineRed = Color(0xFF8A4638)
    val BossRed = Color(0xFFA8473A)
    val BlindGreen = Color(0xFF5F7360)
    val Purple = Color(0xFF9A7590)

    // ---- 功能色（语义不变，饱和度各降一档）----
    val Chip = Color(0xFF5E9BB0)     // 筹码：灰蓝
    val Mult = Color(0xFFC9604F)     // 倍率：砖红
    val Money = Color(0xFFDCA85E)    // 金钱：琥珀
    val Gold = Amber400
    val Green = Color(0xFF7FA184)    // 正向：灰绿
    val PaleGreen = Color(0xFF94A88F)
    val Orange = Amber500
    val JokerGrey = Color(0xFFB8B2A4)

    // ---- 稀有度（保留四档的语义差，色相靠向印刷油墨）----
    val Common = Color(0xFF6E93A3)   // 普通：灰蓝
    val Uncommon = Color(0xFF7FA184) // 罕见：灰绿
    val Rare = Color(0xFFC9604F)     // 稀有：砖红
    val Legendary = Color(0xFF9A7590) // 传说：灰紫

    // ---- 牌面 ----
    val CardFace = Sheet100
    val CardFaceAlt = Paper300
    val CardEdge = InkStamp
    val CardInner = Color(0xFFCFC5B0)
    /** 牌背：1999 的深咖底 + 金细线，不用 Bicycle 红。 */
    val CardBack = Color(0xFF2C2620)
    val SuitRed = Color(0xFFB04A3E)
    val SuitBlack = InkStamp
    val SuitClub = Color(0xFF332D26)

    // ---- 文本 / 面板 ----
    val Cream = Paper100
    val Dim = Color(0xFF9C927F)      // 暖灰，替代原来的冷灰蓝
    val Ink = Ink700
    val Bg = Ink900
    val Surface = Ink800
    val SurfaceHigh = Ink700
    val Danger = BossRed

    // ---- 兼容别名 ----
    val GoldDim = Color(0xFF8A6E42)
    val CardInk = CardEdge
    val CardRed = SuitRed
}

/**
 * 字体层级。
 *
 * 1999 的排版是「大号衬线标题 + 小号无衬线功能字」的强对比（DESIGN_LANGUAGE §3.5）。
 * 手表上没有 Didot，用系统 `Serif` 承接衬线气质 —— 只给**大号数字与标题**，
 * 小字仍走 sans / mono：小屏上衬线细笔画会糊，可读性优先。
 *
 * 数字仍保留等宽档：分数计数器跳动时不能左右晃。
 */
object Type {
    private val mono = FontFamily.Monospace
    private val display = FontFamily.SansSerif
    private val serif = FontFamily.Serif

    /** 大号数字：衬线 + 粗体，1999 的 Didot 气质。 */
    val Big = TextStyle(
        fontFamily = serif, fontWeight = FontWeight.Bold,
        fontSize = 18.sp, color = Pal.Cream,
    )
    /** 中号数字：仍用等宽，保证计分时数字不抖。 */
    val Medium = TextStyle(
        fontFamily = mono, fontWeight = FontWeight.Bold,
        fontSize = 14.sp, color = Pal.Cream,
    )
    val Body = TextStyle(fontSize = 10.sp, color = Pal.Cream)
    val Label = TextStyle(fontFamily = mono, fontSize = 9.sp, color = Pal.Dim)
    val Tiny = TextStyle(fontSize = 8.sp, color = Pal.Dim)
    val TinyMono = TextStyle(fontFamily = mono, fontSize = 8.sp, color = Pal.Dim)

    /** 标题：衬线 + 宽字距，模拟印刷品标题的疏朗。 */
    val Title = TextStyle(
        fontFamily = serif, fontWeight = FontWeight.Bold,
        fontSize = 15.sp, color = Pal.Cream, letterSpacing = 1.6.sp,
    )

    /** 小号大写英文标签：1999 大量使用（`REVERSE · MCMXCIX` 那种）。 */
    val Caption = TextStyle(
        fontFamily = mono, fontSize = 5.5.sp,
        color = Pal.Moss400, letterSpacing = 1.1.sp,
    )

    val Button = TextStyle(
        fontFamily = display, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, color = Pal.Ink900,
    )
    val CardRank = TextStyle(
        fontFamily = serif, fontWeight = FontWeight.Bold,
        fontSize = 13.sp, color = Pal.CardEdge,
    )
    val CardRankSmall = TextStyle(
        fontFamily = serif, fontWeight = FontWeight.Bold,
        fontSize = 10.sp, color = Pal.CardEdge,
    )
    val CardSuit = TextStyle(fontSize = 11.sp, color = Pal.CardEdge)

    /** 倾斜批注字：用于票根、档案卡上的"小字"。 */
    val Annotation = TextStyle(
        fontFamily = mono, fontSize = 5.5.sp,
        color = Pal.Dim, fontStyle = FontStyle.Italic,
    )
}
