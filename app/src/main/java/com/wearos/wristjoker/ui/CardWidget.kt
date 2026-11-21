package com.wearos.wristjoker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.wearos.wristjoker.game.Card

/**
 * 一张扑克牌。
 *
 * 牌面由 [CardFaceArt] 纯 Canvas 绘制：左上角点阵点数 + 小花色构成索引块，
 * 中央大花色，J/Q/K 换成三齿冠冕。增强/蜡封/版本都由牌面自身表达
 * （底色、右上角标、右下蜡点、顶部色带），外框只负责「选中」和「被封锁」。
 *
 * 尺寸 34×20dp 是算出来的：手牌区 2×4 布局要放进圆屏 y=+6..+50 的竖带，
 * 宽上限 154dp、高上限 44dp —— 4 张等宽 + 3 个 4dp 间隙 = 每张 35.5dp。
 * 高度取 20dp 后，点阵点数还有 7dp 高，是这个小尺寸下能保住的可读性。
 */
@Composable
fun PlayingCard(
    card: Card,
    selected: Boolean,
    blocked: Boolean,
    scoring: Boolean,
    /**
     * 点按回调。传 null 表示"这张牌自己不接手势"——
     * 手牌扇形用的是整条带子上的最近中心判定，卡片再挂 clickable
     * 会在 up 时消费事件，把容器的 tap 抢掉。
     */
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** 表冠光标。 */
    cursor: Boolean = false,
) {
    val shape = RoundedCornerShape(3.dp)
    val lift by animateDpAsState(if (selected) (-2).dp else 0.dp, tween(120), label = "lift")
    // 计分中的牌放大 12% —— 这是"这一张正在结算"的唯一提示，
    // 比描边更明显，又不改变布局
    val pop by animateFloatAsState(if (scoring) 1.12f else 1f, tween(110), label = "pop")
    val edge = when {
        selected -> Pal.Money
        blocked -> Pal.BossRed
        scoring -> Pal.Cream
        else -> Pal.CardEdge
    }

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = pop; scaleY = pop }
            .offset(y = lift)
            .clip(shape)
            .background(Pal.CardFace)
            .border(if (selected || scoring) 1.6.dp else 1.dp, edge, shape)
            // 无障碍：牌是纯 Canvas 画的，不声明语义的话 TalkBack 只会读出
            // 一个没有名字的按钮。花色 + 点数 + 状态一次说完，选中与否
            // 走 stateDescription（读屏器在切换时会念"已选中 / 未选中"）。
            .semantics {
                contentDescription = buildString {
                    append(card.suit.cn).append(' ').append(card.rank.label)
                    if (blocked) append("，本关封锁")
                    if (scoring) append("，计分中")
                }
                // 参数名和语义属性同名，直接写 `selected = selected` 会被
                // 当成给参数赋值；用局部变量绕开这个歧义。
                val sel = selected
                this.selected = sel
                stateDescription = when {
                    blocked -> "封锁"
                    sel -> "已选中"
                    else -> "未选中"
                }
                if (onClick != null) role = Role.Button
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        CardFaceArt(card, Modifier.fillMaxSize())

        if (blocked) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(
                    Pal.BossRed.copy(alpha = .85f),
                    Offset(0f, size.height),
                    Offset(size.width, 0f),
                    1.4.dp.toPx(),
                )
            }
        }
        if (scoring) {
            // 结算高亮：一圈半透明金色内发光
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Pal.Money.copy(alpha = .18f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                )
            }
        }
        if (cursor) CursorCorners(Pal.Money, Modifier.fillMaxSize())
    }
}

/**
 * 手牌网格：固定 2 行 × 4 张。
 *
 * 不用横向滚动列表：手表上横向滑动与系统「返回手势」冲突，
 * 且滚动时看不到全部手牌，玩家无法做全局判断。2×4 一屏全见。
 */
@Composable
fun HandGrid(
    hand: List<Card>,
    selected: Set<Int>,
    blockedIds: Set<Int>,
    scoringIds: Set<Int>,
    cursor: Int,
    rowHeight: Int,
    gap: Int,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cols = 4
    val rows = 2
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap.dp)) {
        for (r in 0 until rows) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap.dp),
            ) {
                for (c in 0 until cols) {
                    val index = r * cols + c
                    if (index < hand.size) {
                        val card = hand[index]
                        PlayingCard(
                            card = card,
                            selected = card.id in selected,
                            blocked = card.id in blockedIds,
                            scoring = card.id in scoringIds,
                            cursor = index == cursor,
                            onClick = { onToggle(card.id) },
                            modifier = Modifier.weight(1f).height(rowHeight.dp),
                        )
                    } else {
                        // 空槽位：虚线感的暗框，保持网格节奏不塌陷
                        Box(
                            Modifier
                                .weight(1f)
                                .height(rowHeight.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Pal.FeltDark.copy(alpha = .35f))
                                .border(1.dp, Pal.FeltHi.copy(alpha = .28f), RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 手牌扇形 —— 原作的手牌摆法。
 *
 * 原作的手牌不是网格，是**一手捏着的牌**：沿一段浅弧摊开、每张略微倾斜、
 * 两端的牌往下沉一点，选中的那张抬起来并压到相邻牌上面。这个摆法本身
 * 就是信息：一眼能数出「我手里有几张」，也能立刻看出「哪张被我选了」。
 *
 * 2×4 网格丢掉的就是这两点 —— 8 个等大方框既不像手牌，也让"选中"只能靠描边。
 *
 * 摊开规则：每张露出 `cardWidth × [revealRatio]`（约 18dp），重叠部分在右侧。
 * 因为后画的牌盖住前一张的右段，**露出来的是每张牌的左段** —— 所以
 * 点数索引、蜡封、增强标记全部画在左侧（见 `CardFaceArt`），永远不被吃掉。
 *
 * @param maxWidth 这一带可用弦长；牌太多时优先压缩露出的宽度，而不是溢出圆弧。
 */
@Composable
fun HandFan(
    hand: List<Card>,
    selected: Set<Int>,
    blockedIds: Set<Int>,
    cursor: Int,
    maxWidth: Float,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Float = 25f,
    cardHeight: Float = 33f,
    /** 长按拖动重排：把第 from 张移到第 to 位（原版手牌可拖拽）。 */
    onMove: (Int, Int) -> Unit = { _, _ -> },
    /**
     * 递增计数：出牌 / 弃牌时 +1，手牌带整体下沉一下。
     *
     * 真做"牌从手里飞出去"需要延迟引擎的补牌（先让旧牌飞完再补），
     * 而那会让出牌与结算的时序耦合。用整条带子下沉 8dp + 压暗 45%
     * 表达同一个意思：这一批牌离手了。
     */
    kick: Int = 0,
) {
    // 摊开参数必须在 Box 之外算：点击是按"离哪张牌的中心最近"判定的，
    // 手势要挂在容器上，而容器的 modifier 在 Box 之前就要就绪。
    val n = hand.size
    val revealGeo = if (n <= 1) 0f else minOf(
        cardWidth * .72f,
        (maxWidth - cardWidth) / (n - 1),
    ).coerceAtLeast(6.5f)
    val spanGeo = revealGeo * (n - 1).coerceAtLeast(0)

    val sink = remember { Animatable(0f) }
    LaunchedEffect(kick) {
        if (kick == 0) return@LaunchedEffect
        sink.snapTo(1f)
        sink.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = sink.value * 8.dp.toPx()
                alpha = 1f - sink.value * .45f
            }
            .pointerInput(hand, revealGeo, spanGeo) {
            if (n == 0) return@pointerInput
            // 一张牌时整条带子都是它
            if (n == 1) {
                detectTapGestures { onToggle(hand[0].id) }
                return@pointerInput
            }
            // 关键：**不是在每张牌上各挂一个 clickable**。
            //
            // 牌是重叠的（25dp 宽、只露 15~18dp），每张牌各自可点意味着
            // 重叠的那 7~10dp 归最上面那张 —— 点相邻两张的交界处，选中
            // 哪张取决于绘制顺序，手指又比这个缝宽得多，于是"想选红桃 7，
            // 选中的是方块 9"。改成整条带子一个手势，按点击位置离哪张牌的
            // 中心最近来判定：边界正好落在两张牌的正中间，与视觉一致。
            detectTapGestures { pos ->
                val half = size.width / 2f
                val rel = pos.x - half
                val spanPx = spanGeo.dp.toPx()
                val revPx = revealGeo.dp.toPx()
                if (revPx <= 0f) return@detectTapGestures
                val idx = ((rel + spanPx / 2f) / revPx).roundToInt().coerceIn(0, n - 1)
                val center = -spanPx / 2f + idx * revPx
                // 落在最外两张牌的实际牌面之外就不算（点在带子两端的空白上）
                if (abs(rel - center) <= cardWidth.dp.toPx() / 2f) {
                    onToggle(hand[idx].id)
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (hand.isEmpty()) {
            T("手牌空了", Type.Tiny.copy(color = Pal.FeltHi, fontSize = 6.5.sp))
            return@Box
        }
        val reveal = revealGeo
        val span = spanGeo
        val mid = (n - 1) / 2f

        hand.forEachIndexed { i, card ->
            // 拖拽过程中 i 会变（引擎重排后重绘），用 rememberUpdatedState 读最新值，
            // 否则闭包里的 i 是按下那一刻的位置，第二次拖动就会错位。
            val liveIndex = rememberUpdatedState(i)
            var acc by remember(card.id) { mutableFloatStateOf(0f) }
            val off = i - mid
            val sel = card.id in selected
            val dx = -span / 2f + i * reveal
            // 倾角：每张差 1.7°，两端最多 ±13°
            val ang = (off * 1.7f).coerceIn(-13f, 13f)
            // 弧：两端下沉，最多 4dp
            val dip = off * off * (1.4f / (mid + .8f)).coerceAtMost(4f)
            // 发牌：上一版手牌是"瞬间出现"，出牌后补牌也没有过程 —— 这是整局里
            // 最缺"手感"的一处。新牌从下方 20dp 带 9° 倾角落下，按位置错峰 42ms，
            // 五张牌就是 0.17 秒的"唰唰唰"，和音效对得上。
            val appear = remember(card.id) { Animatable(0f) }
            LaunchedEffect(card.id) {
                if (appear.value < 1f) {
                    delay((i * 42L).coerceAtMost(320L))
                    appear.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
                }
            }
            Box(
                Modifier
                    .zIndex(if (sel) 60f else i.toFloat())
                    .offset(
                        x = dx.dp,
                        y = (dip - if (sel) 5f else 0f).dp,
                    )
                    .pointerInput(card.id, reveal) {
                        val stepPx = reveal.dp.toPx() * .9f
                        detectDragGesturesAfterLongPress(
                            onDragStart = { acc = 0f },
                            onDrag = { change, drag ->
                                change.consume()
                                acc += drag.x
                                if (acc > stepPx || acc < -stepPx) {
                                    val cur = liveIndex.value
                                    val to = (cur + if (acc > 0) 1 else -1).coerceIn(0, n - 1)
                                    if (to != cur) {
                                        onMove(cur, to)
                                        acc = 0f
                                    }
                                }
                            },
                            onDragEnd = { acc = 0f },
                            onDragCancel = { acc = 0f },
                        )
                    },
            ) {
                PlayingCard(
                    card = card,
                    selected = sel,
                    blocked = card.id in blockedIds,
                    scoring = false,
                    cursor = i == cursor,
                    // 点按交给外层整条手牌带判定（见 HandFan 的 pointerInput）
                    onClick = null,
                    modifier = Modifier
                        .width(cardWidth.dp)
                        .height(cardHeight.dp)
                        .graphicsLayer {
                            val v = appear.value
                            rotationZ = ang + (1f - v) * 9f
                            alpha = v
                            translationY = (1f - v) * 20.dp.toPx()
                            scaleX = .94f + .06f * v
                            scaleY = .94f + .06f * v
                        },
                )
            }
        }
    }
}

/**
 * 一行小丑槽位（顶部）。
 *
 * 原版把 5 张小丑竖排在牌桌左侧；手表圆屏的左右两侧是最窄的地方
 * （y=0 处也只有 192dp，但左侧被弧切掉），竖排 5 张要占 5×20dp=100dp 高、
 * 且会把牌桌挤成一条线。所以改成**顶部横排**：5 个 15dp 方徽章并排只占
 * 83dp，正好塞进 y=-58 那一档的 130dp 弦长里，语义仍是"我有哪些小丑"。
 */
@Composable
fun JokerStrip(
    jokers: List<com.wearos.wristjoker.game.JokerInst>,
    maxSlots: Int,
    onClick: () -> Unit,
    /** 当前正在触发计分的小丑下标；-1 表示无。对应原版"从左到右依次 pulse"。 */
    pulseIndex: Int = -1,
    modifier: Modifier = Modifier,
) {
    // 新买入的小丑用一次"弹进来"代替直接出现 —— 原版是从商品卡飞到槽位，
    // 手表上做不到共享坐标（商店和牌桌是两屏），但至少让"多了一张"这件事
    // 有个时间上的落点。
    var prevCount by remember { mutableIntStateOf(jokers.size) }
    var freshIdx by remember { mutableIntStateOf(-1) }
    LaunchedEffect(jokers.size) {
        freshIdx = if (jokers.size > prevCount) jokers.size - 1 else -1
        prevCount = jokers.size
    }
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 挑战「发射」是 0 槽位开送 2 个小丑；此时必须把已有的画出来，
        // 否则玩家开局就有两张牌，界面上却一个都看不到。
        for (i in 0 until maxOf(maxSlots, jokers.size)) {
            val j = jokers.getOrNull(i)
            if (j == null) {
                Box(
                    Modifier
                        .size(15.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Pal.FeltDark.copy(alpha = .5f))
                        .border(1.dp, Pal.FeltHi.copy(alpha = .35f), RoundedCornerShape(2.dp)),
                )
            } else {
                // 原版「协同可视化」：每个 Joker 触发时单独放大一下，玩家先看清
                // "这一下是谁给的"，随后总分才滚动，而不是一次糊上去。
                val pulse by animateFloatAsState(
                    targetValue = if (i == pulseIndex) 1.18f else 1f,
                    animationSpec = spring(dampingRatio = 0.4f, stiffness = 900f),
                    label = "jokerPulse",
                )
                val entrance = remember(j.id) { Animatable(1f) }
                LaunchedEffect(j.id, freshIdx) {
                    if (i != freshIdx) return@LaunchedEffect
                    entrance.snapTo(.35f)
                    entrance.animateTo(1f, spring(dampingRatio = .45f, stiffness = 620f))
                }
                Box(
                    Modifier.graphicsLayer {
                        scaleX = pulse * entrance.value
                        scaleY = pulse * entrance.value
                        alpha = .4f + .6f * entrance.value.coerceIn(0f, 1f)
                    }
                ) {
                    JokerBadge(
                        id = j.id,
                        accent = rarityColorOf(j.rarity),
                        edition = j.edition,
                        onClick = onClick,
                    )
                }
            }
        }
    }
}

/**
 * 消耗品槽位（顶部，小丑行右侧）。
 *
 * 未使用时不画空框 —— 122dp 宽的行里两个空框纯属浪费，
 * 只在有牌时显示，玩家反而更容易注意到"我买了东西没用"。
 */
@Composable
fun ConsumableStrip(
    items: List<com.wearos.wristjoker.game.Consumable>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, c ->
            Box(
                Modifier
                    .size(15.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Pal.FeltDark)
                    .border(1.dp, Pal.Purple.copy(alpha = .9f), RoundedCornerShape(2.dp))
                    .clickable { onClick(i) },
            ) {
                ConsumableArt(c, Modifier.fillMaxSize())
            }
        }
    }
}

/** 小丑稀有度 → 颜色。抽出来给多个界面共用。 */
fun rarityColorOf(r: com.wearos.wristjoker.game.Rarity): Color = when (r) {
    com.wearos.wristjoker.game.Rarity.COMMON -> Pal.Common
    com.wearos.wristjoker.game.Rarity.UNCOMMON -> Pal.Uncommon
    com.wearos.wristjoker.game.Rarity.RARE -> Pal.Rare
    com.wearos.wristjoker.game.Rarity.LEGENDARY -> Pal.Legendary
}
