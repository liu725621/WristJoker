package com.wearos.wristjoker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设置页。
 *
 * 原版的 Options 里有十来项，手表上只留**真正会影响手感**的五项：
 *
 * - **音乐**：4 档。爵士循环是这局游戏的底色，但手表外放时它最容易变成噪音。
 * - **音效**：4 档。手表常在不适合外放的场合（地铁、会议室），0 档必须能一键关。
 * - **震屏**：开关。原作靠屏幕抖动传达"这一手很大"，但震动在手腕上比在显示器上
 *   更"扎"，有些人会直接关掉。
 * - **扫描线**：4 档。CRT 扫描线是这套视觉的骨架，但在 300ppi 的小圆屏上
 *   会显得脏，必须可调。
 * - **结算速度**：3 档。原作的节奏是按 16:9 大屏调的，手表上如果觉得拖，
 *   可以切到「快」；觉得太快看不清楚，切「慢」。
 *
 * 全部即时生效、即时落盘 —— 手表上没有"确定/取消"，改完就应该听到变化。
 */
private val SETTINGS = roundLayout(
    CircleFit.Band(12f, 3f),   // 0 标题
    CircleFit.Band(100f, 3f),  // 1 五行
    CircleFit.Band(18f, 0f),   // 2 返回
)

private val MUSIC_LABELS = listOf("关", "小", "中", "大")
private val SFX_LABELS = listOf("关", "小", "中", "大")
private val CRT_LABELS = listOf("关", "弱", "中", "强")
private val SPEED_LABELS = listOf("慢", "标准", "快")

@Composable
fun SettingsScreen(
    musicLevel: Int,
    sfxLevel: Int,
    hapticsOn: Boolean,
    crtLevel: Int,
    animSpeed: Int,
    onMusic: (Int) -> Unit,
    onSfx: (Int) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onCrt: (Int) -> Unit,
    onSpeed: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RoundPage(SETTINGS, modifier) { L ->
        BandRow(L.w[0], L.h[0]) {
            T("设置", Type.Medium.copy(fontSize = 11.sp, color = Pal.Money))
            T("即时生效", Type.TinyMono.copy(color = Pal.Dim, fontSize = 6.sp))
        }
        BandGap(L.gap[0])

        BandRow(L.w[1], L.h[1], horizontalArrangement = Arrangement.Center) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SettingRow(
                    label = "音乐",
                    hint = "程序化爵士",
                    value = MUSIC_LABELS[musicLevel.coerceIn(0, 3)],
                    accent = Pal.Gold,
                    onClick = { onMusic((musicLevel + 1) % 4) },
                )
                SettingRow(
                    label = "音效",
                    hint = "合成音",
                    value = SFX_LABELS[sfxLevel.coerceIn(0, 3)],
                    accent = Pal.Money,
                    onClick = { onSfx((sfxLevel + 1) % 4) },
                )
                SettingRow(
                    label = "震屏",
                    hint = "触觉层",
                    value = if (hapticsOn) "开" else "关",
                    accent = Pal.Chip,
                    onClick = { onHaptics(!hapticsOn) },
                )
                SettingRow(
                    label = "扫描线",
                    hint = "CRT 强度",
                    value = CRT_LABELS[crtLevel.coerceIn(0, 3)],
                    accent = Pal.Green,
                    onClick = { onCrt((crtLevel + 1) % 4) },
                )
                SettingRow(
                    label = "节奏",
                    hint = "结算动画",
                    value = SPEED_LABELS[animSpeed.coerceIn(0, 2)],
                    accent = Pal.Mult,
                    onClick = { onSpeed((animSpeed + 1) % 3) },
                )
            }
        }
        BandGap(L.gap[1])

        BandRow(L.w[2], L.h[2], horizontalArrangement = Arrangement.Center) {
            CasinoButton(
                label = "返回",
                enabled = true,
                accent = Pal.Chip,
                onClick = onBack,
                modifier = Modifier.width((L.w[2] * .4f).dp),
                filled = false,
                height = 18,
            )
        }
    }
}

/**
 * 一行设置：左名字、中说明、右当前值，整行可点，点一下切下一档。
 *
 * 五项要挤进 100dp，行高只能 18dp —— 上一版"名字 + 说明"两行排版（22dp）
 * 已经放不下了。说明改成同一行的小字，信息没少，高度省了 4dp。
 */
@Composable
private fun SettingRow(
    label: String,
    hint: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(3.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(shape)
            .background(Pal.Surface)
            .border(1.dp, accent.copy(alpha = .45f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(label, Type.TinyMono.copy(color = Pal.Cream, fontSize = 7.5.sp))
        Spacer(Modifier.width(4.dp))
        T(
            hint,
            Type.Tiny.copy(color = Pal.Dim, fontSize = 5.2.sp),
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Spacer(Modifier.width(3.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(accent.copy(alpha = .22f))
                .border(0.8.dp, accent, RoundedCornerShape(2.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            T(value, Type.TinyMono.copy(color = accent, fontSize = 6.5.sp))
        }
    }
}
