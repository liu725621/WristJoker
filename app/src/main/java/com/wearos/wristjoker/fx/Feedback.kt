package com.wearos.wristjoker.fx

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * 音效 + 震动。
 *
 * **不打包任何音频文件**：十几个 wav 至少几十 KB，还会让 APK 多一个
 * assets 目录；而这些音效本来就是"合成的机械声"，直接用代码生成
 * PCM 更可控 —— 音高、包络、时长都能按手感调，改一个常数就行。
 *
 * 生成过程：合成 `ShortArray` → 套 44 字节 WAV 头 → 写进 cacheDir →
 * 交给 `SoundPool` 播放。`SoundPool` 自带按 stream 混音和低延迟播放，
 * 连点时不会互相打断，正好符合"每张牌响一声"的需求。
 */
object Sfx {

    const val SR = 44100

    enum class Kind { SELECT, DEAL, CHIP, MULT, XMULT, KACHING, BASS, COIN, SHATTER, BUY, SELL, PACK, WIN, LOSE }

    private var pool: SoundPool? = null
    private val ids = HashMap<Kind, Int>()
    private var loaded = 0
    private var ready = false
    var enabled = true
        private set

    /**
     * 全局音量乘子（0..1）。
     *
     * 设置页把它映射成 4 档（关 / 小 / 中 / 大）。之所以乘在 [play] 的入参上
     * 而不是调用 `setVolume`，是因为 SoundPool 没有全局音量接口 —— 每个
     * stream 的音量只能在 play 时给。
     */
    var master: Float = 1f
        private set

    fun setMasterVolume(v: Float) {
        master = v.coerceIn(0f, 1f)
    }

    fun setEnabled(v: Boolean) {
        enabled = v
    }

    /** 在 Activity 创建时调用一次；重复调用会直接返回。 */
    fun init(context: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        pool = p
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        var pending = 0
        for (kind in Kind.entries) {
            val f = File(dir, "${kind.name.lowercase()}.wav")
            if (!f.exists() || f.length() < 44) runCatching { f.writeBytes(wav(render(kind))) }
            val id = runCatching { p.load(f.absolutePath, 1) }.getOrDefault(0)
            if (id != 0) {
                ids[kind] = id
                pending++
            }
        }
        p.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                loaded++
                if (loaded >= pending) ready = true
            } else {
                // 有一个加载失败也要放行，否则整局都没声音
                if (++loaded >= pending) ready = true
            }
        }
    }

    /**
     * 播放一个音效。
     *
     * [rate] 是播放速率（0.5..2.0）。这是原版打击感的关键一层：
     * 同一手牌里每张计分牌的 [rate] 依次升高（C-D-E-F-G 的音阶感），
     * 所以"打了 5 张同花"听起来是往上走的，而不是同一个音敲 5 次。
     */
    fun play(kind: Kind, volume: Float = 0.8f, rate: Float = 1f) {
        if (!enabled || !ready) return
        val id = ids[kind] ?: return
        val v = (volume * master).coerceIn(0f, 1f)
        if (v <= 0f) return
        pool?.play(id, v, v, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    /**
     * 音阶：第 [i] 个计分步对应的播放速率。
     *
     * 每步上移两个半音（2^(2/12)≈1.122），十个步之后到 1.9 倍 ——
     * 正好卡在 SoundPool 的上限内，听起来像"越打越急"。
     */
    fun scaleRate(i: Int): Float = (1.0 * Math.pow(2.0, 2.0 * i / 12.0)).toFloat().coerceIn(0.5f, 2f)

    fun release() {
        pool?.release()
        pool = null
        ids.clear()
        ready = false
        loaded = 0
    }

    // ---------------------------------------------------------------- 合成

    /** 生成某个音效的 PCM。全部是 16bit 单声道。 */
    private fun render(kind: Kind): ShortArray = when (kind) {
        // 选牌：极短的方波点击，只负责给"按下去了"的确认
        Kind.SELECT -> tone(1500.0, 0.028, wave = Wave.SQUARE, decay = 60.0, gain = 0.22)

        // 发牌：白噪声 + 快速衰减，模拟纸牌摩擦
        Kind.DEAL -> noise(0.045, decay = 70.0, gain = 0.16)

        // 筹码：基频 + 二次泛音，短促饱满
        Kind.CHIP -> mix(
            tone(880.0, 0.075, decay = 45.0, gain = 0.30),
            tone(1320.0, 0.055, decay = 60.0, gain = 0.16),
        )

        // 加算倍率：上行滑音，暗示"在涨"
        Kind.MULT -> sweep(520.0, 1040.0, 0.10, gain = 0.28)

        // 乘算倍率：双高频 + 轻微颤音，比加算更"亮"
        Kind.XMULT -> tremolo(
            mix(
                tone(1760.0, 0.16, decay = 14.0, gain = 0.30),
                tone(2640.0, 0.14, decay = 18.0, gain = 0.18),
            ),
            18.0, 0.35,
        )

        // 乘算触发的 "ka-ching"：金属双音 + 一记高泛音，比 MULT 更"叮"，
        // 对应原版 ×倍率弹出时的收银机声。
        Kind.KACHING -> concat(
            mix(
                tone(2093.0, 0.09, wave = Wave.TRIANGLE, decay = 22.0, gain = 0.26),
                tone(3136.0, 0.07, decay = 28.0, gain = 0.16),
            ),
            tone(4186.0, 0.06, decay = 40.0, gain = 0.14),
        )

        // 达标 bass drop：低频下坠 + 一层噪声冲击，配合结算全屏闪白。
        Kind.BASS -> mix(
            sweep(180.0, 42.0, 0.36, gain = 0.34),
            noise(0.12, decay = 26.0, gain = 0.12),
        )

        // 金币：两声上行的短音
        Kind.COIN -> concat(
            tone(1400.0, 0.05, wave = Wave.SQUARE, decay = 40.0, gain = 0.20),
            tone(2100.0, 0.07, wave = Wave.SQUARE, decay = 40.0, gain = 0.18),
        )

        // 碎裂：噪声 + 高频偏移，配合玻璃牌碎掉的画面
        Kind.SHATTER -> noise(0.16, decay = 22.0, gain = 0.26, hp = true)

        // 购买：一短一长的确认音
        Kind.BUY -> concat(
            tone(1000.0, 0.05, decay = 40.0, gain = 0.22),
            tone(1500.0, 0.10, decay = 25.0, gain = 0.22),
        )

        // 卖出：两声下行，和购买的"上行"区分开
        Kind.SELL -> concat(
            tone(1200.0, 0.05, decay = 40.0, gain = 0.20),
            tone(700.0, 0.09, decay = 30.0, gain = 0.20),
        )

        // 开包：撕裂噪声 + 一声亮音
        Kind.PACK -> mix(
            noise(0.20, decay = 12.0, gain = 0.18),
            tone(1046.5, 0.18, decay = 9.0, gain = 0.22),
        )

        // 通关：C-E-G 三音上行
        Kind.WIN -> concat(
            tone(523.25, 0.13, decay = 8.0, gain = 0.26),
            tone(659.25, 0.13, decay = 8.0, gain = 0.26),
            tone(783.99, 0.30, decay = 6.0, gain = 0.28),
        )

        // 失败：低频下滑
        Kind.LOSE -> sweep(300.0, 120.0, 0.42, gain = 0.26)
    }

    private enum class Wave { SINE, SQUARE, TRIANGLE }

    private fun n(dur: Double): Int = (dur * SR).toInt().coerceAtLeast(8)

    private fun tone(
        freq: Double,
        dur: Double,
        wave: Wave = Wave.SINE,
        decay: Double,
        gain: Double,
    ): ShortArray {
        val len = n(dur)
        val out = ShortArray(len)
        for (i in 0 until len) {
            val t = i.toDouble() / SR
            val phase = 2.0 * PI * freq * t
            val v = when (wave) {
                Wave.SINE -> sin(phase)
                Wave.SQUARE -> if (sin(phase) >= 0) 1.0 else -1.0
                Wave.TRIANGLE -> 2.0 / PI * kotlin.math.asin(sin(phase))
            }
            // 攻击 3ms 淡入，避免爆音
            val attack = (t / 0.003).coerceAtMost(1.0)
            val env = exp(-decay * t) * attack
            out[i] = (v * env * gain * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun sweep(from: Double, to: Double, dur: Double, gain: Double): ShortArray {
        val len = n(dur)
        val out = ShortArray(len)
        var phase = 0.0
        for (i in 0 until len) {
            val p = i.toDouble() / len
            val f = from + (to - from) * p
            phase += 2.0 * PI * f / SR
            val attack = (i.toDouble() / (0.004 * SR)).coerceAtMost(1.0)
            val env = exp(-3.0 * p) * attack
            out[i] = (sin(phase) * env * gain * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun noise(dur: Double, decay: Double, gain: Double, hp: Boolean = false): ShortArray {
        val len = n(dur)
        val out = ShortArray(len)
        var prev = 0.0
        val rnd = kotlin.random.Random(7)
        for (i in 0 until len) {
            val t = i.toDouble() / SR
            val white = rnd.nextDouble(-1.0, 1.0)
            val v = if (hp) white - prev * 0.85 else white
            prev = white
            val attack = (t / 0.002).coerceAtMost(1.0)
            out[i] = (v * exp(-decay * t) * attack * gain * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun mix(vararg parts: ShortArray): ShortArray {
        val len = parts.maxOf { it.size }
        val out = ShortArray(len)
        for (i in 0 until len) {
            var s = 0
            for (p in parts) if (i < p.size) s += p[i].toInt()
            out[i] = s.coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun concat(vararg parts: ShortArray): ShortArray {
        val total = parts.sumOf { it.size }
        val out = ShortArray(total)
        var pos = 0
        for (p in parts) {
            p.copyInto(out, pos)
            pos += p.size
        }
        return out
    }

    /** 轻微颤音：让高频音听起来像金属而不是蜂鸣器。 */
    private fun tremolo(src: ShortArray, hz: Double, depth: Double): ShortArray {
        val out = ShortArray(src.size)
        for (i in src.indices) {
            val t = i.toDouble() / SR
            val m = 1.0 - depth + depth * (0.5 + 0.5 * sin(2.0 * PI * hz * t))
            out[i] = (src[i] * m).toInt().toShort()
        }
        return out
    }

    /** 套 44 字节标准 WAV 头。 */
    private fun wav(pcm: ShortArray): ByteArray {
        val dataLen = pcm.size * 2
        val out = ByteArray(44 + dataLen)
        fun putInt(off: Int, v: Int) {
            out[off] = (v and 0xFF).toByte()
            out[off + 1] = ((v shr 8) and 0xFF).toByte()
            out[off + 2] = ((v shr 16) and 0xFF).toByte()
            out[off + 3] = ((v shr 24) and 0xFF).toByte()
        }
        fun putShort(off: Int, v: Int) {
            out[off] = (v and 0xFF).toByte()
            out[off + 1] = ((v shr 8) and 0xFF).toByte()
        }
        "RIFF".forEachIndexed { i, c -> out[i] = c.code.toByte() }
        putInt(4, 36 + dataLen)
        "WAVE".forEachIndexed { i, c -> out[8 + i] = c.code.toByte() }
        "fmt ".forEachIndexed { i, c -> out[12 + i] = c.code.toByte() }
        putInt(16, 16)          // fmt chunk size
        putShort(20, 1)         // PCM
        putShort(22, 1)         // mono
        putInt(24, SR)
        putInt(28, SR * 2)      // byte rate
        putShort(32, 2)         // block align
        putShort(34, 16)        // bits
        "data".forEachIndexed { i, c -> out[36 + i] = c.code.toByte() }
        putInt(40, dataLen)
        for (i in pcm.indices) {
            val v = pcm[i].toInt()
            out[44 + i * 2] = (v and 0xFF).toByte()
            out[44 + i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }
}

/**
 * 分级震动。
 *
 * 强度是"打击感"的一半：手指能感觉到"这一手打得很重"，
 * 比任何视觉反馈都直接。分四档，与结算序列里的事件强度对应。
 */
object Haptics {

    private var vibrator: Vibrator? = null
    var enabled = true
        private set

    fun init(context: Context) {
        if (vibrator != null) return
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun setEnabled(v: Boolean) {
        enabled = v
    }

    /** [strength] 0..3。 */
    fun tap(strength: Int) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val ms = when (strength.coerceIn(0, 3)) {
            0 -> 8L
            1 -> 14L
            2 -> 22L
            else -> 38L
        }
        val amp = when (strength.coerceIn(0, 3)) {
            0 -> 55
            1 -> 110
            2 -> 175
            else -> 255
        }
        runCatching {
            v.vibrate(VibrationEffect.createOneShot(ms, amp.coerceIn(1, 255)))
        }
    }

    fun release() {
        vibrator = null
    }
}

/** 浮点振幅转短期最小可闻强度，供 UI 层按动画进度调用。 */
internal fun Float.toAmplitude(): Int = (abs(this).coerceIn(0f, 1f) * 255).toInt()
