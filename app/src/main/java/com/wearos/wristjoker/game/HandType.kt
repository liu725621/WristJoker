package com.wearos.wristjoker.game

/**
 * 13 种牌型（含 3 种"隐藏牌型"）。
 *
 * 基础值与每级成长为 Balatro 官方数值，已用三个来源交叉核对：
 * 高牌 5/1、对子 10/2、两对 20/2、三条 30/3、顺子 30/4、同花 35/4、
 * 葫芦 40/4、四条 60/7、同花顺 100/8、五条 120/12、同花葫芦 140/14、同花五条 160/16。
 *
 * 枚举声明顺序 = 由弱到强，判定时反向遍历即可取到最强牌型。
 */
enum class HandType(
    val cn: String,
    val en: String,
    val baseChips: Int,
    val baseMult: Int,
    /** 每升一级增加的筹码。 */
    val chipsPerLevel: Int,
    /** 每升一级增加的倍率。 */
    val multPerLevel: Int,
) {
    HIGH_CARD("高牌", "High Card", 5, 1, 10, 1),
    PAIR("对子", "Pair", 10, 2, 15, 1),
    TWO_PAIR("两对", "Two Pair", 20, 2, 20, 1),
    THREE_OF_A_KIND("三条", "Three of a Kind", 30, 3, 20, 2),
    STRAIGHT("顺子", "Straight", 30, 4, 30, 3),
    FLUSH("同花", "Flush", 35, 4, 15, 2),
    FULL_HOUSE("葫芦", "Full House", 40, 4, 25, 2),
    FOUR_OF_A_KIND("四条", "Four of a Kind", 60, 7, 30, 3),
    STRAIGHT_FLUSH("同花顺", "Straight Flush", 100, 8, 40, 4),
    FIVE_OF_A_KIND("五条", "Five of a Kind", 120, 12, 35, 3),
    FLUSH_HOUSE("同花葫芦", "Flush House", 140, 14, 40, 4),
    FLUSH_FIVE("同花五条", "Flush Five", 160, 16, 50, 3),
    ;

    /** 该牌型在指定等级下的基础筹码。等级从 1 起。 */
    fun chipsAt(level: Int): Int = baseChips + chipsPerLevel * (level - 1)

    /** 该牌型在指定等级下的基础倍率。 */
    fun multAt(level: Int): Int = baseMult + multPerLevel * (level - 1)
}
