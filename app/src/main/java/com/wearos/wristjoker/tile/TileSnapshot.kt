package com.wearos.wristjoker.tile

import com.wearos.wristjoker.game.BlindKind
import com.wearos.wristjoker.game.Phase
import com.wearos.wristjoker.game.RunState
import com.wearos.wristjoker.game.roman
import java.util.Locale

/**
 * 手表卡片上要显示的那几行字。
 *
 * 抽成一个纯数据类、只吃 [RunState]，是因为**卡片跑在另一个进程里**：
 * TileService 被系统按需拉起，拿不到正在运行的引擎，只能读存档。
 * 把"存档 → 文案"这段逻辑从绘制里剥出来，就能在 JVM 里直接单测
 * 「不同阶段显示什么」，不用真机反复抬手。
 *
 * 排版只有五行：kicker（阶段）/ big（主数字）/ sub（主数字的说明）/
 * 进度条 / meta（钱包与剩余资源）。192dp 的圆屏放不下第二个大数字，
 * 所以商店态把主数字让给金钱，牌桌态让给分数 —— 都是"当下最想瞟一眼"的那个。
 */
data class TileSnapshot(
    val running: Boolean,
    /** 顶部小标签：`A III · 大盲`。 */
    val kicker: String,
    /** 主数字：分数、金钱或提示语。 */
    val big: String,
    /** 主数字下方的说明：`/ 1,800`、`可支配`。 */
    val sub: String,
    /** 进度 0..1；负数表示这一态不画进度条。 */
    val ratio: Float,
    /** 底行：钱包 + 剩余资源。 */
    val meta: String,
    /** Boss 关：kicker 换成砖红，提醒这是硬关。 */
    val boss: Boolean,
) {
    companion object {

        /** 没有可继续的一局。 */
        fun idle(bestAnte: Int): TileSnapshot = TileSnapshot(
            running = false,
            kicker = "腕上小丑",
            big = "轻触开局",
            sub = "没有进行中的一局",
            ratio = -1f,
            meta = if (bestAnte > 0) "最高 A ${roman(bestAnte)}" else "",
            boss = false,
        )

        fun of(state: RunState?, bestAnte: Int): TileSnapshot {
            // 结束态与菜单态都不算"进行中"：引擎在这些阶段不会落盘，
            // 能读到它们只可能是玩家刚通关、存档还没被抹掉的瞬间。
            if (state == null || state.phase == Phase.MENU ||
                state.phase == Phase.GAME_OVER || state.phase == Phase.WIN
            ) {
                return idle(bestAnte)
            }

            // 牌桌上的底注是紧凑的 `AIII/8`（横向只有一格），卡片上宽度富余，
            // 加一个空格更好认 —— 且和下面 `最高 A III` 的写法统一。
            val ante = "A ${roman(state.ante)}"
            val isBoss = state.blindIndex == 2
            return when (state.phase) {
                Phase.PLAYING -> {
                    val target = state.target.coerceAtLeast(1)
                    TileSnapshot(
                        running = true,
                        kicker = "$ante · ${if (isBoss) "BOSS" else BlindKind.entries[state.blindIndex].short}",
                        big = compact(state.score),
                        sub = "/ ${compact(target)}",
                        ratio = (state.score.toFloat() / target).coerceIn(0f, 1f),
                        meta = "\$${state.money} · 手 ${state.handsLeft} · 弃 ${state.discardsLeft}",
                        boss = isBoss,
                    )
                }

                Phase.BLIND_SELECT -> TileSnapshot(
                    running = true,
                    kicker = "$ante · 选盲注",
                    big = compact(state.target.coerceAtLeast(1)),
                    sub = "下一个盲注目标",
                    ratio = -1f,
                    meta = "\$${state.money} · 已过 ${state.blindsCleared} 关",
                    boss = isBoss,
                )

                Phase.CASH_OUT -> TileSnapshot(
                    running = true,
                    kicker = "$ante · 结算",
                    big = "+${compact(state.cashOut?.total?.toLong() ?: state.lastGain.toLong())}",
                    sub = "本关收入",
                    ratio = -1f,
                    meta = "\$${state.money} 在手",
                    boss = false,
                )

                Phase.SHOP -> TileSnapshot(
                    running = true,
                    kicker = "$ante · 商店",
                    big = "\$${state.money}",
                    sub = "可支配",
                    ratio = -1f,
                    meta = "小丑 ${state.jokers.size} · 消耗 ${state.consumables.size}",
                    boss = false,
                )

                Phase.PACK_OPEN -> TileSnapshot(
                    running = true,
                    kicker = "$ante · 开包",
                    big = "选包中",
                    sub = "${state.pack?.contents?.size ?: 0} 张可选",
                    ratio = -1f,
                    meta = "\$${state.money} · 小丑 ${state.jokers.size}",
                    boss = false,
                )

                // DECK_SELECT / CHALLENGE_SELECT 不会落盘（见 MainActivity.onStop），
                // 走到这里只可能是手动改存档；当作"没有一局"处理更诚实。
                else -> idle(bestAnte)
            }
        }

        /**
         * 大数字缩写：卡片主数字只有一行，无尽模式几十万分的 `1,240,000`
         * 会顶到圆边。
         *
         * 阈值定在 10 万而不是 1 万：底注 1–6 的目标分与常见得分都在六位
         * 以内，`12,480` 这种精确值玩家读得懂、也愿意读；只有进了无尽模式
         * 才开始用 `248K` / `1.2M`。25sp 下一行能放约 8 个字符，
         * `999,999` 正好卡在边上，所以 10 万以上必须缩写。
         */
        fun compact(n: Long): String = when {
            n < 100_000 -> String.format(Locale.US, "%,d", n)
            n < 1_000_000 -> String.format(Locale.US, "%.0fK", n / 1000.0)
            else -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        }
    }
}
