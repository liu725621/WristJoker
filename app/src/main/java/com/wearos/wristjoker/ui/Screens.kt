package com.wearos.wristjoker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wearos.wristjoker.game.BlindKind
import com.wearos.wristjoker.game.BlindOption
import com.wearos.wristjoker.game.BossModifier
import com.wearos.wristjoker.game.Challenge
import com.wearos.wristjoker.game.Consumable
import com.wearos.wristjoker.game.DeckType
import com.wearos.wristjoker.game.GameUi
import com.wearos.wristjoker.game.JokerInst
import com.wearos.wristjoker.game.Phase
import com.wearos.wristjoker.game.Rarity
import com.wearos.wristjoker.game.Stake
import com.wearos.wristjoker.game.Tag
import com.wearos.wristjoker.game.Voucher

/**
 * 七个整页界面全部改成**按弦长分带**。
 *
 * 上一轮只改了牌桌和商店，菜单/牌组/盲注/结算/结束/开包/详情仍然是
 * 一条 122dp 的竖带 —— 玩家从牌桌点进结算页，界面会从贴着圆弧的版式
 * 缩回一个矩形。这一轮把这七页全部接上 [CircleFit]，每页的宽度都随
 * 所在高度变化：中间带能到 190dp，上下带自动收到 140dp 左右。
 */

/* ------------------------------------------------------------------ *
 * 开始界面
 * ------------------------------------------------------------------ */

private val MENU = roundLayout(
    CircleFit.Band(24f, 2f),   // 0 牌背
    CircleFit.Band(22f, 1f),   // 1 标题
    CircleFit.Band(9f, 2f),    // 2 JOKER
    CircleFit.Band(11f, 3f),   // 3 存档 / 最佳记录
    CircleFit.Band(26f, 3f),   // 4 主按钮
    CircleFit.Band(24f, 3f),   // 5 次按钮
    CircleFit.Band(22f, 2f),   // 6 挑战
    CircleFit.Band(20f, 0f),   // 7 图鉴 / 设置
)

/**
 * 开始界面。
 *
 * 有存档时把「继续」放成主按钮，并直接把存档里的进度写出来
 * （第几关 / 哪个盲注 / 多少钱 / 什么赌注）—— 手表上玩家很可能隔几小时
 * 才回来，只写一句"继续游戏"他不知道自己上次打到哪。
 *
 * 这一版多了「挑战」入口：20 个固定规则预设，是原版最有教学价值的部分。
 */
@Composable
fun MenuScreen(
    ui: GameUi,
    bestAnte: Int,
    bestHand: Long,
    onContinue: () -> Unit,
    onNewRun: () -> Unit,
    onChallenges: () -> Unit,
    onCollection: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasRun = ui.hasSave && ui.phase != Phase.MENU
    RoundPage(MENU, modifier) { L ->
        BandRow(L.w[0], L.h[0], horizontalArrangement = Arrangement.Center) {
            Box(
                Modifier
                    .size(width = 22.dp, height = 30.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .border(1.5.dp, Pal.Money, RoundedCornerShape(3.dp)),
            ) {
                CardBackArt(Modifier.fillMaxSize())
            }
        }
        BandGap(L.gap[0])

        BandRow(L.w[1], L.h[1], horizontalArrangement = Arrangement.Center) {
            EmbossedTitle(
                text = "腕上小丑",
                color = Pal.SuitRed,
                depthColor = Pal.FeltDark,
                style = Type.Medium.copy(fontSize = 21.sp, fontWeight = FontWeight.Black),
            )
        }
        BandGap(L.gap[1])

        BandRow(L.w[2], L.h[2], horizontalArrangement = Arrangement.Center) {
            PixelText("JOKER", Pal.Money, pixel = 1.8.dp)
        }
        BandGap(L.gap[2])

        BandRow(L.w[3], L.h[3], horizontalArrangement = Arrangement.Center) {
            if (hasRun) {
                T(
                    "A${ui.ante} · ${ui.blindKind.cn} · \$${ui.money} · ${ui.stake.cn}",
                    Type.TinyMono.copy(color = Pal.Cream, fontSize = 7.sp),
                )
            } else {
                T(
                    if (bestAnte > 0) "最高 A$bestAnte · 最强单手 ${fmtScore(bestHand)}" else "8 关 · 5 小丑位 · 8 档赌注",
                    Type.Tiny.copy(color = Pal.Dim, fontSize = 6.5.sp),
                )
            }
        }
        BandGap(L.gap[3])

        BandRow(L.w[4], L.h[4], horizontalArrangement = Arrangement.Center) {
            CasinoButton(
                label = if (hasRun) "继续 A${ui.ante}" else "开始游戏",
                enabled = true,
                accent = Pal.Money,
                onClick = if (hasRun) onContinue else onNewRun,
                modifier = Modifier.width((L.w[4] * .62f).dp),
                height = 24,
            )
        }
        BandGap(L.gap[4])

        BandRow(L.w[5], L.h[5], horizontalArrangement = Arrangement.Center) {
            CasinoButton(
                label = "新的一局",
                enabled = true,
                accent = Pal.Chip,
                onClick = onNewRun,
                modifier = Modifier.width((L.w[5] * .52f).dp),
                filled = false,
                height = 22,
            )
        }
        BandGap(L.gap[5])

        BandRow(L.w[6], L.h[6], horizontalArrangement = Arrangement.Center) {
            CasinoButton(
                label = "挑战 · ${Challenge.entries.size} 个",
                enabled = true,
                accent = Pal.Purple,
                onClick = onChallenges,
                modifier = Modifier.width((L.w[6] * .46f).dp),
                filled = false,
                height = 20,
            )
        }
        BandGap(L.gap[6])

        // 图鉴与设置：两个窄按钮并排。它们都不是"开始一局"，所以放在最下面，
        // 用 outline 样式，避免和上面的主流程抢注意力。
        BandRow(L.w[7], L.h[7], horizontalArrangement = Arrangement.Center) {
            val bw = (L.w[7] * .27f).dp
            CasinoButton(
                label = "图鉴",
                enabled = true,
                accent = Pal.Money,
                onClick = onCollection,
                modifier = Modifier.width(bw),
                filled = false,
                height = 19,
            )
            Spacer(Modifier.width(7.dp))
            CasinoButton(
                label = "设置",
                enabled = true,
                accent = Pal.Chip,
                onClick = onSettings,
                modifier = Modifier.width(bw),
                filled = false,
                height = 19,
            )
        }
    }
}

/* ------------------------------------------------------------------ *
 * 牌组选择
 * ------------------------------------------------------------------ */

private val DECK = roundLayout(
    CircleFit.Band(12f, 2f),   // 0 标题
    CircleFit.Band(14f, 2f),   // 1 赌注
    CircleFit.Band(88f, 3f),   // 2 列表
    CircleFit.Band(22f, 0f),   // 3 返回
)

/**
 * 牌组选择页。
 *
 * 15 个牌组纵向滚动。原版是横向卡片列表，圆屏上横向列表在 y=0 处也
 * 只有 192dp 可用，一次只能看到 2 张，滚动体验很差。
 *
 * 顶部加了一条**赌注**：点一下循环白→金八档。原版是在牌组页下方选，
 * 位置一样，只是压缩成一行 —— 选牌组和选难度本来就该在同一屏。
 */
@Composable
fun DeckSelectScreen(
    stake: Stake,
    maxStake: Int,
    onStake: (Stake) -> Unit,
    onPick: (DeckType) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 只允许在已解锁的档位里循环。原版是"通关才解锁下一档"，
    // 这里同样把未解锁的档位挡在外面，但不会让玩家卡在"选不到"上 ——
    // 超上限时回到白注。
    val top = maxStake.coerceIn(0, Stake.entries.size - 1)
    val next = (stake.ordinal + 1).let { if (it > top) 0 else it }
    RoundPage(DECK, modifier) { L ->
        BandRow(L.w[0], L.h[0]) {
            T("选牌组", Type.Medium.copy(fontSize = 11.sp, color = Pal.Money))
            T("${DeckType.entries.size} 种 · 影响开局", Type.TinyMono.copy(color = Pal.Dim, fontSize = 6.sp))
        }
        BandGap(L.gap[0])

        BandRow(L.w[1], L.h[1]) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Pal.Purple.copy(alpha = .18f))
                    .border(1.dp, Pal.Purple.copy(alpha = .6f), RoundedCornerShape(3.dp))
                    .clickable { onStake(Stake.entries[next]) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T("赌注", Type.TinyMono.copy(color = Pal.Purple, fontSize = 6.sp))
                Spacer(Modifier.width(4.dp))
                T(stake.cn, Type.TinyMono.copy(color = Pal.Cream, fontSize = 7.sp))
                Spacer(Modifier.weight(1f))
                T(
                    if (stake.ordinal >= top && top < Stake.entries.size - 1) {
                        "通关解锁下一档"
                    } else {
                        stake.desc
                    },
                    Type.Tiny.copy(
                        color = if (stake.ordinal >= top && top < Stake.entries.size - 1) {
                            Pal.Money.copy(alpha = .8f)
                        } else {
                            Pal.Dim
                        },
                        fontSize = 6.sp,
                    ),
                )
                Spacer(Modifier.width(3.dp))
                T("▶", Type.TinyMono.copy(color = Pal.Purple, fontSize = 6.sp))
            }
        }
        BandGap(L.gap[1])

        BandRow(L.w[2], L.h[2], horizontalArrangement = Arrangement.Center) {
            LazyColumn(
                Modifier.fillMaxWidth().height(L.h[2].dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(DeckType.entries.toList()) { i, d ->
                    DeckRow(i, d) { onPick(d) }
                }
            }
        }
        BandGap(L.gap[2])

        BandRow(L.w[3], L.h[3], horizontalArrangement = Arrangement.Center) {
            CasinoButton(
                label = "返回",
                enabled = true,
                accent = Pal.Chip,
                onClick = onBack,
                modifier = Modifier.width((L.w[3] * .4f).dp),
                filled = false,
                height = 20,
            )
        }
    }
}

/** 一行牌组：序号 + 名字 + 加成。 */
@Composable
private fun DeckRow(index: Int, deck: DeckType, onClick: () -> Unit) {
    val shape = RoundedCornerShape(3.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(shape)
            .background(Pal.Surface)
            .border(1.dp, Pal.Money.copy(alpha = .45f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(
            "%02d".format(index + 1),
            Type.TinyMono.copy(color = Pal.Dim, fontSize = 5.5.sp),
            modifier = Modifier.width(13.dp),
        )
        Box(
            Modifier
                .size(width = 11.dp, height = 15.dp)
                .clip(RoundedCornerShape(2.dp))
                .border(0.8.dp, Pal.CardEdge, RoundedCornerShape(2.dp)),
        ) {
            CardBackArt(Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(4.dp))
        T(deck.cn, Type.TinyMono.copy(color = Pal.Cream, fontSize = 8.sp))
        Spacer(Modifier.weight(1f))
        T(deck.desc, Type.Tiny.copy(color = Pal.Dim, fontSize = 6.5.sp))
    }
}

/* ------------------------------------------------------------------ *
 * 挑战选择
 * ------------------------------------------------------------------ */

private val CHALLENGE = roundLayout(
    CircleFit.Band(12f, 2f),   // 0 标题
    CircleFit.Band(94f, 3f),   // 1 列表
    CircleFit.Band(22f, 0f),   // 2 返回
)

/**
 * 挑战列表：20 个预设，一屏 4 个，滚动查看。
 *
 * 每个挑战只改一条规则 —— 这是它能当"教学关"的原因：玩家打完「无丑」
 * 才知道自己平时多依赖小丑；打完「金针」才知道出牌次数就是生命线。
 */
@Composable
fun ChallengeScreen(
    onPick: (Challenge) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RoundPage(CHALLENGE, modifier) { L ->
        BandRow(L.w[0], L.h[0]) {
            T("挑战模式", Type.Medium.copy(fontSize = 11.sp, color = Pal.Purple))
            T("${Challenge.entries.size} 个 · 固定牌序", Type.TinyMono.copy(color = Pal.Dim, fontSize = 6.sp))
        }
        BandGap(L.gap[0])

        BandRow(L.w[1], L.h[1], horizontalArrangement = Arrangement.Center) {
            LazyColumn(
                Modifier.fillMaxWidth().height(L.h[1].dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(Challenge.entries.toList()) { i, c ->
                    ChallengeRow(i, c) { onPick(c) }
                }
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
                height = 20,
            )
        }
    }
}

@Composable
private fun ChallengeRow(index: Int, c: Challenge, onClick: () -> Unit) {
    val shape = RoundedCornerShape(3.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(shape)
            .background(Pal.Surface)
            .border(1.dp, Pal.Purple.copy(alpha = .55f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(
            "%02d".format(index + 1),
            Type.TinyMono.copy(color = Pal.Purple, fontSize = 5.5.sp),
            modifier = Modifier.width(13.dp),
        )
        Column(Modifier.weight(1f)) {
            T(c.cn, Type.TinyMono.copy(color = Pal.Cream, fontSize = 7.5.sp))
            T(c.desc, Type.Tiny.copy(color = Pal.Dim, fontSize = 5.8.sp))
        }
        T(c.stake.cn, Type.TinyMono.copy(color = Pal.Gold, fontSize = 5.5.sp))
    }
}

/* ------------------------------------------------------------------ *
 * 盲注选择
 * ------------------------------------------------------------------ */

private val BLIND = roundLayout(
    CircleFit.Band(14f, 3f),
    CircleFit.Band(24f, 2f),
    CircleFit.Band(24f, 2f),
    CircleFit.Band(24f, 3f),
    CircleFit.Band(24f, 0f),
)

private fun blindColor(k: BlindKind): Color = when (k) {
    BlindKind.SMALL -> Pal.BlindGreen
    BlindKind.BIG -> Pal.Green
    BlindKind.BOSS -> Pal.BossRed
}

/**
 * 盲注选择页。
 *
 * 三个盲注同屏列出（原版是纵向滚动 + 跳到下一格，手表一屏正好三格高），
 * 当前要打的那格高亮并展开 Boss 负面效果。底部「跳过」会写明这一跳
 * 换来的是一张标签 —— 不写清楚的话，玩家不会知道"跳过"是有回报的。
 */
@Composable
fun BlindSelectScreen(
    ui: GameUi,
    onSelect: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RoundPage(BLIND, modifier) { L ->
        BandRow(L.w[0], L.h[0]) {
            // 赌注直接拼进标题：这一行最窄（y=±55 处弦长只有 160dp），
            // 再挂一颗胶囊会把"第 3 关"挤成省略号
            T(
                "第 ${ui.ante} 关" + (if (ui.endless) " ∞" else "") +
                    (if (ui.stake == Stake.WHITE) "" else " · ${ui.stake.cn}"),
                Type.Medium.copy(fontSize = 9.5.sp, color = Pal.Money),
            )
            if (ui.tags.isNotEmpty()) {
                Pill(ui.tags.first().cn.substringBefore("标签"), Pal.Purple, fontSize = 5)
            }
            CountBadge("$${ui.money}", Pal.Money)
        }
        BandGap(L.gap[0])

        ui.blindOptions.forEachIndexed { i, option ->
            BandRow(L.w[i + 1], L.h[i + 1]) {
                BlindRow(option, Modifier.fillMaxWidth())
            }
            BandGap(L.gap[i + 1])
        }

        BandRow(L.w[4], L.h[4], horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CasinoButton(
                label = if (ui.canSkip) "跳过 得标签" else "跳过",
                enabled = ui.canSkip,
                accent = Pal.Purple,
                onClick = onSkip,
                modifier = Modifier.weight(1f),
                filled = false,
                height = 22,
            )
            CasinoButton(
                label = if (ui.blindIndex >= 2) "打 Boss" else "开打",
                enabled = true,
                accent = if (ui.blindIndex >= 2) Pal.BossRed else Pal.Money,
                onClick = onSelect,
                modifier = Modifier.weight(1f),
                height = 22,
            )
        }
    }
}

/**
 * 一格盲注：第一行「小盲 · \$3」，第二行目标分与 Boss 效果。
 * 当前格用金色描边 + 亮底，已过格降低对比。
 */
@Composable
private fun BlindRow(option: BlindOption, modifier: Modifier = Modifier) {
    val accent = blindColor(option.kind)
    val shape = RoundedCornerShape(3.dp)
    val dim = !option.current
    Box(
        modifier
            .height(24.dp)
            .clip(shape)
            .background(if (option.current) Pal.Surface else Pal.FeltDark.copy(alpha = .45f))
            .border(
                if (option.current) 1.5.dp else 1.dp,
                if (option.current) Pal.Money else accent.copy(alpha = .55f),
                shape,
            )
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (option.cleared) {
                        T("✓ ", Type.TinyMono.copy(color = Pal.Green, fontSize = 7.sp))
                    }
                    T(
                        option.kind.cn,
                        Type.TinyMono.copy(
                            fontSize = 7.5.sp,
                            color = if (dim) accent.copy(alpha = .55f) else accent,
                        ),
                    )
                }
                T(
                    "+$${option.reward}",
                    Type.TinyMono.copy(
                        fontSize = 6.5.sp,
                        color = if (dim) Pal.Dim.copy(alpha = .6f) else Pal.Money,
                    ),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T(
                    "目标 ${fmtScore(option.target)}",
                    Type.TinyMono.copy(
                        fontSize = 6.5.sp,
                        color = if (dim) Pal.Dim.copy(alpha = .65f) else Pal.Cream,
                    ),
                )
                if (option.kind == BlindKind.BOSS && option.modifier != BossModifier.NONE) {
                    T(
                        option.modifier.desc,
                        Type.Tiny.copy(
                            fontSize = 5.8.sp,
                            color = if (dim) Pal.BossRed.copy(alpha = .6f) else Pal.BossRed,
                        ),
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ *
 * 结算页（Cash Out）
 * ------------------------------------------------------------------ */

private val CASH = roundLayout(
    CircleFit.Band(18f, 1f),
    CircleFit.Band(9f, 3f),
    CircleFit.Band(20f, 4f),
    CircleFit.Band(62f, 5f),
    CircleFit.Band(26f, 0f),
)

/**
 * 结算页。
 *
 * 逐项列出这一关赚在哪。原版把这几项摆成一张账单，玩家能立刻算清
 * 「少用一次出牌值多少钱」——这是它策略感的来源。钱在点「收下」之后才入帐。
 */
@Composable
fun CashOutScreen(
    ui: GameUi,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ui.cashOut ?: return
    val accent = if (c.isBoss) Pal.BossRed else Pal.Money

    RoundPage(CASH, modifier) { L ->
        BandRow(L.w[0], L.h[0], horizontalArrangement = Arrangement.Center) {
            T(
                if (c.isBoss) "BOSS 击破" else "盲注通过",
                Type.Medium.copy(fontSize = 12.sp, color = accent),
            )
        }
        BandGap(L.gap[0])

        BandRow(L.w[1], L.h[1], horizontalArrangement = Arrangement.Center) {
            T(
                "第 ${c.ante} 关 · ${c.blindKind.cn}",
                Type.TinyMono.copy(color = Pal.Dim, fontSize = 6.5.sp),
            )
        }
        BandGap(L.gap[1])

        BandRow(L.w[2], L.h[2], horizontalArrangement = Arrangement.Center) {
            PixelText(fmtScore(c.score), Pal.Chip, pixel = 1.7.dp)
            T(
                " / ${fmtScore(c.target)}",
                Type.TinyMono.copy(color = Pal.Dim, fontSize = 7.sp),
                modifier = Modifier.padding(start = 5.dp),
            )
        }
        BandGap(L.gap[2])

        BandRow(L.w[3], L.h[3], horizontalArrangement = Arrangement.Center) {
            Column(
                Modifier.width((L.w[3] * .72f).dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                LedgerLine("击败奖励", "+\$${c.baseReward}")
                LedgerLine("剩余出牌 ${c.handsLeft}×", "+\$${c.handBonus}")
                LedgerLine(
                    if (c.interestDoubled) "利息(经济×2)" else "利息",
                    "+\$${c.interest}",
                    Pal.Money,
                )
                if (c.handyBonus > 0) LedgerLine("手巧标签", "+\$${c.handyBonus}", Pal.Purple)
                if (c.walletBonus > 0) LedgerLine("钱袋", "+\$${c.walletBonus}", Pal.Gold)

                Spacer(Modifier.height(1.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Pal.FeltHi.copy(alpha = .45f)))
                Spacer(Modifier.height(1.dp))

                LedgerLine("合计", "+\$${c.total}", Pal.Gold)
            }
        }
        BandGap(L.gap[3])

        BandRow(L.w[4], L.h[4], horizontalArrangement = Arrangement.Center) {
            CasinoButton(
                label = "收下 \$${c.total}",
                enabled = true,
                accent = Pal.Money,
                onClick = onConfirm,
                modifier = Modifier.width((L.w[4] * .55f).dp),
                height = 24,
            )
        }
    }
}

/* ------------------------------------------------------------------ *
 * 结束界面
 * ------------------------------------------------------------------ */

private val END = roundLayout(
    CircleFit.Band(22f, 2f),
    CircleFit.Band(20f, 2f),
    CircleFit.Band(9f, 4f),
    CircleFit.Band(34f, 5f),
    CircleFit.Band(24f, 3f),
    CircleFit.Band(24f, 0f),
)

/**
 * 结束界面：区分通关与失败，并给出这一局的三个数字。
 *
 * 失败时把「差多少分」写清楚 —— 这是玩家判断「下一局要不要换个打法」的
 * 唯一依据。通关时换成最高单手分，并给一个"继续无尽"的入口。
 */
@Composable
fun EndScreen(
    ui: GameUi,
    onRestart: () -> Unit,
    onMenu: () -> Unit,
    onEndless: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val win = ui.phase == Phase.WIN
    RoundPage(END, modifier) { L ->
        BandRow(L.w[0], L.h[0], horizontalArrangement = Arrangement.Center) {
            Box(
                Modifier
                    .size(width = 18.dp, height = 24.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .border(1.5.dp, if (win) Pal.Money else Pal.BossRed, RoundedCornerShape(3.dp)),
            ) {
                CardBackArt(Modifier.fillMaxSize())
            }
        }
        BandGap(L.gap[0])

        BandRow(L.w[1], L.h[1], horizontalArrangement = Arrangement.Center) {
            EmbossedTitle(
                text = if (win) "通 关" else "止 步",
                color = if (win) Pal.Money else Pal.BossRed,
                depthColor = Pal.FeltDark,
                style = Type.Medium.copy(fontSize = 17.sp, fontWeight = FontWeight.Black),
            )
        }
        BandGap(L.gap[1])

        BandRow(L.w[2], L.h[2], horizontalArrangement = Arrangement.Center) {
            T(
                if (win) "八关全部击破" else "第 ${ui.ante} 关 · ${ui.blindKind.cn}",
                Type.Tiny.copy(color = Pal.Dim, fontSize = 6.5.sp),
            )
        }
        BandGap(L.gap[2])

        // 通关多一颗"继续无尽"按钮，所以账单只留 2 行；失败时补一行"本关差"——
        // 这是玩家判断"下一局要不要换打法"的唯一依据
        BandRow(L.w[3], L.h[3], horizontalArrangement = Arrangement.Center) {
            Column(
                Modifier.width((L.w[3] * .62f).dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                LedgerLine("最高单手", fmtScore(ui.bestHand), Pal.Chip)
                LedgerLine("已过盲注", "${ui.blindsCleared}", Pal.Green)
                if (!win) {
                    LedgerLine(
                        "本关差",
                        fmtScore((ui.target - ui.score).coerceAtLeast(0)),
                        Pal.Mult,
                    )
                }
            }
        }
        BandGap(L.gap[3])

        if (win) {
            BandRow(L.w[4], L.h[4], horizontalArrangement = Arrangement.Center) {
                CasinoButton(
                    label = "继续无尽",
                    enabled = true,
                    accent = Pal.Money,
                    onClick = onEndless,
                    modifier = Modifier.width((L.w[4] * .45f).dp),
                    height = 22,
                )
            }
            BandGap(L.gap[4])
        } else {
            Spacer(Modifier.height((L.h[4] + L.gap[4]).dp))
        }

        BandRow(L.w[5], L.h[5], horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CasinoButton(
                label = "主菜单",
                enabled = true,
                accent = Pal.Chip,
                onClick = onMenu,
                modifier = Modifier.weight(1f),
                filled = false,
                height = 22,
            )
            CasinoButton(
                label = "再来一局",
                enabled = true,
                accent = if (win) Pal.Money else Pal.SuitRed,
                onClick = onRestart,
                modifier = Modifier.weight(1f),
                height = 22,
            )
        }
    }
}

/* ------------------------------------------------------------------ *
 * 通用小部件
 * ------------------------------------------------------------------ */

/** 消耗品单行（面板里用）。 */
@Composable
internal fun ConsumableRow(c: Consumable, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Pal.Surface)
            .border(0.8.dp, Pal.Purple.copy(alpha = .7f), RoundedCornerShape(3.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .border(0.6.dp, Pal.CardEdge, RoundedCornerShape(2.dp)),
        ) {
            ConsumableArt(c, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(3.dp))
        Column(Modifier.weight(1f)) {
            T(c.name, Type.TinyMono.copy(color = Pal.Cream, fontSize = 7.sp))
            T(
                if (c.needsCards) "${c.desc}（选 ${c.maxSel} 张）" else c.desc,
                Type.Tiny.copy(color = Pal.Dim, fontSize = 5.8.sp),
            )
        }
    }
}

/** 小丑单行（面板里用）。 */
@Composable
internal fun JokerRow(j: JokerInst) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Pal.Surface)
            .border(0.9.dp, rarityColorOf(j.rarity), RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .border(0.6.dp, Pal.CardEdge, RoundedCornerShape(2.dp)),
        ) {
            JokerPortrait(j.id, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(3.dp))
        Column(Modifier.weight(1f)) {
            T(
                j.name + if (j.edition.isBase) "" else " · ${j.edition.cn}",
                Type.TinyMono.copy(color = Pal.Cream, fontSize = 7.sp),
            )
            T(j.desc, Type.Tiny.copy(color = Pal.Dim, fontSize = 5.8.sp))
        }
        T("\$${j.sellPrice}", Type.TinyMono.copy(color = Pal.Money, fontSize = 6.sp))
    }
}

/** 代金券单行。 */
@Composable
internal fun VoucherRow(v: Voucher) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Pal.Surface)
            .border(0.8.dp, Pal.Gold.copy(alpha = .7f), RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .border(0.6.dp, Pal.CardEdge, RoundedCornerShape(2.dp)),
        ) {
            VoucherArt(v, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(3.dp))
        T(v.cn, Type.TinyMono.copy(color = Pal.Cream, fontSize = 7.sp))
        Spacer(Modifier.weight(1f))
        T(v.desc, Type.Tiny.copy(color = Pal.Dim, fontSize = 6.sp))
    }
}

/** 标签单行。 */
@Composable
internal fun TagRow(t: Tag) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Pal.Purple.copy(alpha = .18f))
            .border(0.8.dp, Pal.Purple.copy(alpha = .6f), RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(t.cn, Type.TinyMono.copy(color = Pal.Purple, fontSize = 6.5.sp))
        Spacer(Modifier.weight(1f))
        T(t.desc, Type.Tiny.copy(color = Pal.Dim, fontSize = 5.8.sp))
    }
}
