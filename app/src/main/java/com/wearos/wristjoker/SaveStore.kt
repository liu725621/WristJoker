package com.wearos.wristjoker

import android.content.Context
import com.wearos.wristjoker.game.Collection
import com.wearos.wristjoker.game.GameEngine
import com.wearos.wristjoker.game.SaveCodec
import com.wearos.wristjoker.game.Stake

/**
 * 自动存档。
 *
 * 三件事：
 * 1. **每次状态变更后落盘**（由 UI 层在一帧结束后调用，带 200ms 防抖）；
 * 2. **onStop 强制落盘**——手表上玩家随时会被抬腕/息屏打断，不能指望"退出时保存"；
 * 3. **冷启动恢复**：`MainActivity` 建引擎时先 `restore()`，主菜单就能显示「继续」。
 *
 * 用 `SharedPreferences` 而不是 DataStore：存档只有一个字符串、写入频率是
 * 人手操作级的（每局几十次），`apply()` 的异步落盘完全够用，而 DataStore
 * 在这里要多引一个协程依赖、还可能因为首帧读延迟导致画面闪一下。
 */
class SaveStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wristjoker_save", Context.MODE_PRIVATE)

    /** 把引擎当前状态写入磁盘。 */
    fun persist(engine: GameEngine) {
        val text = SaveCodec.encode(engine.export())
        prefs.edit().putString(KEY_RUN, text).apply()
    }

    /**
     * 从磁盘恢复引擎状态。
     *
     * @return 是否真的恢复出了一局可继续的游戏。
     */
    fun restore(engine: GameEngine): Boolean {
        val text = prefs.getString(KEY_RUN, null) ?: return false
        val state = SaveCodec.decode(text) ?: return false
        engine.restore(state)
        engine.hasSave = true
        return true
    }

    /** 是否存在可继续的存档。 */
    fun exists(): Boolean {
        val text = prefs.getString(KEY_RUN, null) ?: return false
        return SaveCodec.decode(text) != null
    }

    /**
     * 只读一次存档，不建引擎。
     *
     * 给手表卡片（Tile）用：TileService 是被系统按需绑定的短命服务，
     * 为了显示四行字去建一个完整 [GameEngine]、把 52 张牌和商店都还原一遍
     * 纯属浪费 —— 卡片只读 [SaveCodec.decode] 出来的状态对象。
     */
    fun load(): com.wearos.wristjoker.game.RunState? =
        prefs.getString(KEY_RUN, null)?.let { SaveCodec.decode(it) }

    /** 主动清空（开新局、通关结算后）。 */
    fun wipe() {
        prefs.edit().remove(KEY_RUN).apply()
    }

    // ---------------------------------------------------------------- 长期统计

    var bestAnte: Int
        get() = prefs.getInt(KEY_BEST_ANTE, 0)
        private set(value) = prefs.edit().putInt(KEY_BEST_ANTE, value).apply()

    var bestHand: Long
        get() = prefs.getLong(KEY_BEST_HAND, 0)
        private set(value) = prefs.edit().putLong(KEY_BEST_HAND, value).apply()

    var runs: Int
        get() = prefs.getInt(KEY_RUNS, 0)
        private set(value) = prefs.edit().putInt(KEY_RUNS, value).apply()

    /** 一局结束后更新历史最好成绩。 */
    fun recordRun(ante: Int, hand: Long) {
        if (ante > bestAnte) bestAnte = ante
        if (hand > bestHand) bestHand = hand
        runs += 1
    }

    // ---------------------------------------------------------------- 图鉴

    /**
     * 图鉴里"见过"的条目 id（带前缀，见 [CollectionEntry.id]）。
     *
     * 只增不减，且不参与 wipe() —— 图鉴是跨局的长期记录，清存档不该把它抹掉。
     * `getStringSet` 返回的是 SharedPreferences 内部持有的同一个实例，
     * 直接改它会造成"没提交却已生效"的假象，所以下面一律先 `HashSet(..)` 拷贝。
     */
    fun seen(): Set<String> =
        prefs.getStringSet(KEY_SEEN, emptySet())?.toHashSet() ?: emptySet()

    fun markSeen(ids: Iterable<String>) {
        val cur = seen().toHashSet()
        var dirty = false
        for (id in ids) {
            if (id.isNotEmpty() && cur.add(id)) dirty = true
        }
        if (dirty) prefs.edit().putStringSet(KEY_SEEN, cur).apply()
    }

    /** 已解锁总数（脏 id 不计）。 */
    val seenTotal: Int get() = seen().count { it in Collection.allIds }

    // ---------------------------------------------------------------- 赌注解锁

    /**
     * 已解锁的最高赌注档位（[Stake.ordinal]）。
     *
     * 原版是一档一档打上去的：白注通关才开红注，红注通关才开绿注。手表这里
     * 照做，但**不卡在"必须用最高档通关"** —— 用任意档通关都能解锁下一档，
     * 因为手表一局十几分钟，逼玩家重复刷低档没有意义。
     */
    var maxStake: Int
        get() = prefs.getInt(KEY_MAX_STAKE, 0)
        private set(value) = prefs.edit().putInt(KEY_MAX_STAKE, value).apply()

    fun stakeUnlocked(stake: Stake): Boolean = stake.ordinal <= maxStake

    /** 通关（或进入无尽）后调用：把下一档放出来。 */
    fun unlockNextStake(from: Stake) {
        val next = (from.ordinal + 1).coerceAtMost(Stake.entries.size - 1)
        if (next > maxStake) maxStake = next
    }

    // ---------------------------------------------------------------- 设置

    /** 音效音量档位：0 关 / 1 小 / 2 中 / 3 大。 */
    var sfxLevel: Int
        get() = prefs.getInt(KEY_SFX, 3)
        set(value) = prefs.edit().putInt(KEY_SFX, value.coerceIn(0, 3)).apply()

    var hapticsOn: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    /** CRT 扫描线强度：0 关 / 1 弱 / 2 中 / 3 强。 */
    var crtLevel: Int
        get() = prefs.getInt(KEY_CRT, 2)
        set(value) = prefs.edit().putInt(KEY_CRT, value.coerceIn(0, 3)).apply()

    /** 背景音乐音量档位：0 关 / 1 小 / 2 中 / 3 大。默认「小」—— 表扬声器不适合大声放 bed。 */
    var musicLevel: Int
        get() = prefs.getInt(KEY_MUSIC, 1)
        set(value) = prefs.edit().putInt(KEY_MUSIC, value.coerceIn(0, 3)).apply()

    /** 结算动画速度档位：0 慢 / 1 标准 / 2 快。 */
    var animSpeed: Int
        get() = prefs.getInt(KEY_SPEED, 1)
        set(value) = prefs.edit().putInt(KEY_SPEED, value.coerceIn(0, 2)).apply()

    private companion object {
        const val KEY_RUN = "run"
        const val KEY_BEST_ANTE = "best_ante"
        const val KEY_BEST_HAND = "best_hand"
        const val KEY_RUNS = "runs"
        const val KEY_SEEN = "seen"
        const val KEY_MAX_STAKE = "max_stake"
        const val KEY_SFX = "sfx_level"
        const val KEY_HAPTICS = "haptics_on"
        const val KEY_CRT = "crt_level"
        const val KEY_SPEED = "anim_speed"
        const val KEY_MUSIC = "music_level"
    }
}
