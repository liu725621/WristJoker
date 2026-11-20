package com.wearos.wristjoker.tile

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.em
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.wearos.wristjoker.MainActivity
import com.wearos.wristjoker.SaveStore

/**
 * 手表「卡片」（三星叫卡片，Wear OS 叫 Tile）：抬手右滑就能看到这一局的进度。
 *
 * 为什么值得做：这是一局十几分钟的游戏，而手表的交互是"看一眼就走"。
 * 之前想知道「我还差多少分、还剩几次出牌」必须重新打开 App —— 冷启动、
 * 读存档、进牌桌，三秒起步，还容易被别的事情打断。卡片把这三秒压到零。
 *
 * 三条设计约束：
 *
 * 1. **不建引擎**。TileService 是系统按需绑定的短命服务，只为画四行字
 *    去还原 52 张牌和整个商店没有意义；直接读 [SaveStore.load] 的结果。
 * 2. **不做交互**。卡片只有整块可点（回到游戏）。Wear OS 的卡片点击区域
 *    会被系统放大到 48dp 以上，放两个小按钮只会互相误触；真要出牌还是得
 *    进牌桌，那是 4 个按钮 + 手牌的一整套手势。
 * 3. **颜色只用语义色**。分数进度用筹码蓝（`#5E9BB0`），Boss 标签用砖红；
 *    卡片上是 "刚才看了一眼" 的场景，装饰越少越好读。
 */
class JokerTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val store = SaveStore(this)
        val snap = TileSnapshot.of(store.load(), store.bestAnte)

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root(snap)))
            .build()
        return immediate(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        // 卡片全部用系统字体与色块绘制，不引图片资源，返回空表即可。
        immediate(ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build())

    // ---------------------------------------------------------------- 布局

    private fun root(snap: TileSnapshot): LayoutElementBuilders.LayoutElement {
        val content = Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            // 顶部一条 18×1 的压印短线：1999 的印刷母题，也顺手压住上面的空
            .addContent(
                Box.Builder()
                    .setWidth(dp(18f))
                    .setHeight(dp(1f))
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setBackground(
                                ModifiersBuilders.Background.Builder()
                                    .setColor(argb(Ink700))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(gap(6f))
            .addContent(label(snap.kicker, 10f, if (snap.boss) BossRed else Dim, tracking = .08f))
            .addContent(gap(3f))
            .addContent(label(snap.big, 25f, Cream, bold = true, tracking = .02f))
            .addContent(gap(4f))
            .addContent(label(snap.sub, 10f, Dim))

        // 只有牌桌态有"进度"这个概念：商店的金钱没有分母，硬画一条
        // 满格进度条会让人以为"快赢了"。
        if (snap.ratio >= 0f) {
            content.addContent(gap(8f))
            content.addContent(bar(snap.ratio))
        }
        if (snap.meta.isNotEmpty()) {
            content.addContent(gap(8f))
            content.addContent(label(snap.meta, 9f, Dim))
        }

        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(Ink900))
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(dp(14f))
                            .setEnd(dp(14f))
                            .build()
                    )
                    // 整块可点：回牌桌继续。不设 minimumClickable 尺寸 ——
                    // 卡片本身就是 192dp 见方，系统会自行放大命中区。
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("open")
                            .setOnClick(
                                ActionBuilders.launchAction(
                                    ComponentName(
                                        this,
                                        MainActivity::class.java,
                                    )
                                )
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(content.build())
            .build()
    }

    /** 一行文字。字号单位是 sp，[tracking] 是字距（em）。 */
    private fun label(
        text: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
        tracking: Float = 0f,
    ): LayoutElementBuilders.LayoutElement {
        if (text.isEmpty()) return gap(0f)
        val style = FontStyle.Builder()
            .setSize(sp(size))
            .setColor(argb(color))
            .apply {
                if (bold) setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                if (tracking > 0f) setLetterSpacing(em(tracking))
            }
            .build()
        return Text.Builder()
            .setText(text)
            .setFontStyle(style)
            .setMaxLines(1)
            .build()
    }

    private fun gap(h: Float): LayoutElementBuilders.LayoutElement =
        Spacer.Builder().setHeight(dp(h)).build()

    /**
     * 进度条：底槽 + 实心填充。
     *
     * [ratio] 至少给 4dp 宽 —— 手气差的时候分数是 0，但一条完全没有的
     * 进度条会被误读成"布局没渲染出来"；留一小段反而说明"这局刚开始"。
     */
    private fun bar(ratio: Float): LayoutElementBuilders.LayoutElement {
        val full = 150f
        val w = (full * ratio.coerceIn(0f, 1f)).coerceAtLeast(4f)
        return Box.Builder()
            .setWidth(dp(full))
            .setHeight(dp(4f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(Slot))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(2f)).build())
                            .build()
                    )
                    .build()
            )
            .addContent(
                Box.Builder()
                    .setWidth(dp(w))
                    .setHeight(expand())
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setBackground(
                                ModifiersBuilders.Background.Builder()
                                    .setColor(argb(Chip))
                                    .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(2f)).build())
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private companion object {
        const val RES_VERSION = "v1"
        const val FRESHNESS_MS = 60_000L

        // 与 ui/Palette.kt 同一套色值。卡片不能引用 Compose 的 Color
        // （protolayout 只吃 ARGB int），所以这里重抄一遍 —— 改配色时
        // 两处要一起改，tools/render_preview.py 里也有第三份。
        const val Ink900 = 0xFF1B1713.toInt()
        const val Ink700 = 0xFF3B332A.toInt()
        const val Slot = 0xFF2C2620.toInt()
        const val Cream = 0xFFF3EEE1.toInt()
        const val Dim = 0xFF9C927F.toInt()
        const val Chip = 0xFF5E9BB0.toInt()
        const val BossRed = 0xFFA8473A.toInt()

        fun <T> immediate(v: T): ListenableFuture<T> =
            ResolvableFuture.create<T>().also { it.set(v) }
    }
}

/**
 * 让 App 内的状态变化立刻反映到卡片上。
 *
 * 系统也会按 [JokerTileService] 的 freshness 定时重取，但那个粒度是分钟级；
 * 玩家刚打完一手牌就抬手看卡片，看到的是上一手的分数，会以为没记上。
 *
 * 节流 2 秒：一次出牌会连着触发「选中 → 出牌 → 结算 → 补牌」好几次
 * [refresh]，每次都跨进程 bind 一次 TileService 既费电又没有必要。
 */
object TileRefresh {

    private const val MIN_INTERVAL_MS = 2_000L
    private var lastAt = 0L

    fun nudge(context: Context, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastAt < MIN_INTERVAL_MS) return
        lastAt = now
        runCatching {
            TileService.getUpdater(context).requestUpdate(JokerTileService::class.java)
        }
    }
}
