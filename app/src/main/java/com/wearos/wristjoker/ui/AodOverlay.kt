package com.wearos.wristjoker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wearos.wristjoker.game.GameUi
import com.wearos.wristjoker.game.Phase

/**
 * AOD 常显层。
 *
 * 熄屏后系统进入 ambient 模式：屏幕只剩极少数像素发光，OLED 上白色是最亮的。
 * 这里只保留三类信息 —— 分数、目标、剩余出牌 —— 玩家手放下瞥一眼就能知道
 * 「这局打到哪了」，抬腕再恢复完整牌桌。
 *
 * 设计纪律：纯黑底 + 单一米白 + 暗灰次要字，不做任何动画、渐变、彩色功能色。
 * 彩色的语义在熄屏下没用（人眼已切到"瞥一眼"模式），反而白耗电。
 */
@Composable
fun AodOverlay(ui: GameUi) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when (ui.phase) {
            // 游戏进行中：大分数 + 目标 + 剩余出牌/弃牌
            Phase.PLAYING, Phase.BLIND_SELECT, Phase.CASH_OUT, Phase.SHOP, Phase.PACK_OPEN -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T(
                        "第 ${ui.ante} 关 · ${ui.blindKind.cn}",
                        Type.TinyMono.copy(color = AOD_DIM, fontSize = 7.sp),
                    )
                    Spacer(Modifier.height(6.dp))
                    T(
                        fmtScore(ui.score),
                        Type.Big.copy(color = AOD_INK, fontSize = 34.sp),
                    )
                    Spacer(Modifier.height(8.dp))
                    T(
                        "目标 ${fmtScore(ui.target)}",
                        Type.TinyMono.copy(color = AOD_DIM, fontSize = 8.sp),
                    )
                    Spacer(Modifier.height(8.dp))
                    T(
                        "出牌 ${ui.handsLeft} · 弃牌 ${ui.discardsLeft}",
                        Type.TinyMono.copy(color = AOD_DIM, fontSize = 8.sp),
                    )
                }
            }

            // 菜单：标题 + 是否有可继续的局
            Phase.MENU -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T(
                    "腕上小丑",
                    Type.Medium.copy(color = AOD_INK, fontSize = 16.sp),
                )
                Spacer(Modifier.height(4.dp))
                T(
                    if (ui.hasSave) "继续 A${ui.ante} · \$${ui.money}" else "轻触开局",
                    Type.TinyMono.copy(color = AOD_DIM, fontSize = 8.sp),
                )
            }

            // 其余阶段：一句状态
            else -> T(
                when (ui.phase) {
                    Phase.WIN -> "通关 A${ui.ante}"
                    Phase.GAME_OVER -> "止步 A${ui.ante}"
                    Phase.DECK_SELECT -> "选牌组"
                    Phase.CHALLENGE_SELECT -> "挑战模式"
                    else -> "腕上小丑"
                },
                Type.Medium.copy(color = AOD_INK, fontSize = 14.sp),
            )
        }
    }
}

// AOD 专用色：米白 + 暗灰。黑白屏上最省电且可读，不借功能色表意。
private val AOD_INK = Color(0xFFE8E1D1)
private val AOD_DIM = Color(0xFF8A8578)
