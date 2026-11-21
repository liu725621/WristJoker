package com.wearos.wristjoker.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import com.wearos.wristjoker.MainActivity
import com.wearos.wristjoker.SaveStore
import com.wearos.wristjoker.game.Phase
import com.wearos.wristjoker.game.roman

/**
 * 表盘小部件：把"当前底注 + 分数/目标"放到表盘上。
 *
 * 和 Tile（右滑的卡片）是两种不同的入口，解决的却是同一个问题：
 * 一局十几分钟，玩家会反复抬腕看"还差多少"。Tile 需要一次滑动，
 * Complication 只需要抬手 —— 在表盘上直接就有。
 *
 * 数据源与 Tile 完全一致（都读 [SaveStore.load]），不建引擎：
 * Complication 的刷新由系统按 30 分钟 / 或由表盘自己驱动，
 * 是个更短命的进程，为一个数字还原 52 张牌毫无意义。
 */
class JokerComplicationService : ComplicationDataSourceService() {

    /**
     * 系统来要数据。
     *
     * 这个版本是同步回调（`SuspendingComplicationDataSourceService` 在
     * 1.2.1 里已经不在 data-source 包里）。读一份 SharedPreferences 是
     * 微秒级，不值得为它开一个协程再切回主线程。
     */
    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        val snap = TileSnapshotReader.read(SaveStore(this).load())
        val data = when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> shortText(snap)
            ComplicationType.RANGED_VALUE -> ranged(snap)
            else -> null
        }
        listener.onComplicationData(data)
    }

    /**
     * 表盘编辑器里的预览。
     *
     * 必须给：没有预览数据的 Complication 在选择器里显示成空白方块，
     * 玩家不知道那是什么，也就不会选它。
     */
    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val snap = TileSnapshotReader.preview()
        return when (type) {
            ComplicationType.SHORT_TEXT -> shortText(snap)
            ComplicationType.RANGED_VALUE -> ranged(snap)
            else -> null
        }
    }

    private fun shortText(s: CompSnap): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(s.top).build(),
            contentDescription = PlainComplicationText.Builder(s.talk).build(),
        )
            .setTitle(PlainComplicationText.Builder(s.kicker).build())
            .setTapAction(openApp())
            .build()

    /**
     * 有进度就用 RANGED_VALUE：表盘会把圆环填到对应比例，
     * "打到哪了"一眼可读，比两个数字更快。
     */
    private fun ranged(s: CompSnap): ComplicationData =
        RangedValueComplicationData.Builder(
            value = s.value,
            min = 0f,
            max = s.max,
            contentDescription = PlainComplicationText.Builder(s.talk).build(),
        )
            .setText(PlainComplicationText.Builder(s.top).build())
            .setTitle(PlainComplicationText.Builder(s.kicker).build())
            .setTapAction(openApp())
            .build()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** 表盘只需要四个量，单独一个瘦结构，避免把整份存档带进 UI 层。 */
data class CompSnap(
    val kicker: String,
    val top: String,
    val talk: String,
    val value: Float,
    val max: Float,
)

/**
 * 存档 → 四个量。
 *
 * 与 `TileSnapshot` 平行但不复用它：卡片要五行文案（含资源行与进度条），
 * 表盘只放得下一行标题 + 一行数字，硬套会把卡片的排版约束带过来。
 */
object TileSnapshotReader {

    fun preview(): CompSnap = CompSnap(
        kicker = "腕上小丑",
        top = "A III",
        talk = "腕上小丑，第 3 关",
        value = 0.62f,
        max = 1f,
    )

    fun read(s: com.wearos.wristjoker.game.RunState?): CompSnap {
        if (s == null) return CompSnap("腕上小丑", "轻触开局", "腕上小丑，轻触开局", 0f, 1f)

        val ante = "A ${roman(s.ante)}"
        return when (s.phase) {
            Phase.PLAYING, Phase.BLIND_SELECT -> {
                val target = if (s.target > 0) s.target else 1L
                val r = (s.score.toFloat() / target).coerceIn(0f, 1f)
                CompSnap(
                    kicker = ante,
                    top = compact(s.score),
                    talk = "第 ${s.ante} 关，${compact(s.score)} / ${compact(target)}，${(r * 100).toInt()}%",
                    value = r,
                    max = 1f,
                )
            }
            Phase.SHOP -> CompSnap(
                ante, "商店", "商店，持有 ${s.money} 元", 0f, 1f,
            )
            Phase.CASH_OUT -> CompSnap(
                ante, "结算", "结算，本关 ${compact(s.score)}", 1f, 1f,
            )
            Phase.GAME_OVER -> CompSnap("结束", "${compact(s.score)}", "本局结束，${compact(s.score)}", 0f, 1f)
            Phase.WIN -> CompSnap("通关", "${compact(s.score)}", "通关，${compact(s.score)}", 1f, 1f)
            else -> CompSnap("腕上小丑", "继续", "腕上小丑，继续上一局", 0f, 1f)
        }
    }

    /** 表盘一格只放得下 5~7 个字符，与卡片用同一套缩写规则。 */
    private fun compact(n: Long): String = when {
        n < 10_000 -> n.toString()
        n < 1_000_000 -> String.format(java.util.Locale.US, "%.1fK", n / 1000.0)
        else -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0)
    }
}
