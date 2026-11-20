package com.wearos.wristjoker.game

/**
 * 底注的罗马数字写法。
 *
 * 牌桌上的底注显示成 `AIII/8`，卡片（Tile）上也照抄 —— 同一个数字在两处
 * 长得不一样，玩家会以为是两个值。1..8 是正经关卡，无尽模式超出去就退回
 * 阿拉伯数字，免得出现 `AXXXVII/8` 这种占掉半行宽度的东西。
 */
fun roman(n: Int): String = when (n) {
    1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"
    5 -> "V"; 6 -> "VI"; 7 -> "VII"; 8 -> "VIII"
    else -> n.toString()
}
