package com.wearos.wristjoker.fx

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * 背景音乐：一段**用代码生成的爵士循环**。
 *
 * 上一版整局是静的 —— 只有音效，没有底色。这是"没有原版感觉"最直接的原因：
 * 原作的氛围有一半来自那支循环播放的爵士小乐队，牌桌的静默会被它填满，
 * 玩家不用一直盯着屏幕也知道"这局还在进行"。
 *
 * 和音效一样**不打包音频文件**：20 秒 22kHz 单声道 WAV 约 900KB，用代码生成
 * 反而更小、可调，还能顺手把速度做成场景参数（Boss 慢下来、结算快一点）。
 *
 * 编曲（8 小节，92 BPM，摇摆八分）：
 * - 贝斯：根音 + 五度 + 半音趋近，走路低音；
 * - 电钢：Dm9 → G13 → Cmaj9 → Am9 的 ii-V-I-vi，反拍切分；
 * - 吊镲：每个摇摆八分一记短噪声，重拍稍响；
 * - 黑胶：极低的底噪 + 偶发爆点，把数字合成声"做旧"。
 *
 * 生成在后台线程做一次，落到 cacheDir；之后用 MediaPlayer 循环。
 */
object Music {

    /** 场景。只改播放速度，不重新生成 —— 换调会破坏循环的连贯性。 */
    enum class Mood(val speed: Float) {
        MENU(1.0f),
        PLAY(1.0f),
        BOSS(0.94f),
        CASH(1.06f),
    }

    private const val SR = 22050
    private const val BPM = 92.0
    private const val BEAT = 60.0 / BPM
    private const val BAR = BEAT * 4
    private const val BARS = 8

    private var player: MediaPlayer? = null
    private var file: File? = null
    private var loading = false
    private var enabled = true
    private var volume = .35f
    private var mood = Mood.MENU
    /** 界面是否要求"该有音乐"（菜单/牌桌为 true，结束页也为 true）。 */
    private var wantPlay = false

    private val main = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        if (loading || file != null) return
        loading = true
        val app = context.applicationContext
        Thread {
            val dir = File(app.cacheDir, "bgm").apply { mkdirs() }
            val f = File(dir, "loop_v1.wav")
            if (!f.exists() || f.length() < 44) {
                runCatching { f.writeBytes(wav(renderLoop())) }
            }
            file = f
            loading = false
            main.post { if (wantPlay) startInternal() }
        }.apply { name = "bgm-render"; priority = Thread.MIN_PRIORITY }.start()
    }

    fun setEnabled(v: Boolean) {
        enabled = v
        if (!v) pauseInternal() else if (wantPlay) { startInternal(); applyMood() }
    }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        runCatching { player?.setVolume(volume, volume) }
    }

    /** 切换场景：只调整播放速度，不打断循环。 */
    fun play(m: Mood) {
        mood = m
        applyMood()
        if (wantPlay) startInternal()
    }

    /** 界面可见时才播；[on] 为 false 时暂停（不释放，回来能接上）。 */
    fun setActive(on: Boolean) {
        wantPlay = on
        if (on) { startInternal(); applyMood() } else pauseInternal()
    }

    fun release() {
        runCatching { player?.release() }
        player = null
    }

    // ---------------------------------------------------------------- 播放

    private fun startInternal() {
        if (!enabled) return
        val p = player
        if (p != null) {
            if (!p.isPlaying) runCatching { p.start() }
            applyMood()
            return
        }
        val f = file ?: return
        if (!f.exists() || f.length() < 44) return
        runCatching {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(f.absolutePath)
            mp.isLooping = true
            mp.setVolume(volume, volume)
            mp.prepare()
            mp.start()
            player = mp
            applyMood()
        }
    }

    private fun pauseInternal() {
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
    }

    private fun applyMood() {
        val p = player ?: return
        runCatching {
            p.playbackParams = p.playbackParams.setSpeed(mood.speed)
        }.onFailure {
            // 少数机型在未 start 时拒绝改速；下一次 play() 会再试一次
        }
    }

    // ---------------------------------------------------------------- 合成

    /**
     * 返回 16bit 单声道 PCM，长度正好 8 小节。
     *
     * 尾巴不能直接剪掉：最后一小节的和弦要响 2 秒，硬切会在循环点"咔"一声。
     * 所以多渲染 1.2 小节，再把这截尾巴**折回开头**叠加 —— 循环点听不出接缝，
     * 这是磁带循环的老办法。
     */
    private fun renderLoop(): ShortArray {
        val total = (BAR * BARS * SR).toInt()
        val pad = (BAR * 1.2 * SR).toInt()
        val buf = DoubleArray(total + pad)
        val rnd = Random(20240220)

        // 和声：ii - V - I - vi 走两遍，第二遍把 vi 换成 III7 增加一点"拐弯"
        val chords = listOf(
            intArrayOf(50, 57, 60, 65), // Dm9   D3 A3 C4 F4
            intArrayOf(43, 53, 59, 65), // G13   G2 F3 B3 F4
            intArrayOf(48, 55, 59, 64), // Cmaj9 C3 G3 B3 E4
            intArrayOf(45, 52, 55, 60), // Am9   A2 E3 G3 C4
            intArrayOf(50, 57, 60, 65),
            intArrayOf(43, 53, 59, 63), // G7b9
            intArrayOf(40, 47, 52, 59), // E7    E2 B2 E3 B3
            intArrayOf(45, 52, 55, 61), // A7    A2 E3 G3 C#4
        )

        for (bar in 0 until BARS) {
            val t0 = bar * BAR
            val ch = chords[bar]
            val nextRoot = chords[(bar + 1) % BARS][0]

            // --- 贝斯：1 拍根音（长）、2.5 拍五度、3.5 拍趋近音
            bass(buf, t0, ch[0], BEAT * 1.4, .26)
            bass(buf, t0 + BEAT * 1.5, ch[0] + 7, BEAT * .8, .22)
            val approach = if (nextRoot > ch[0]) nextRoot - 1 else nextRoot + 1
            bass(buf, t0 + BEAT * 2.5, nextRoot, BEAT * .5, .18)
            bass(buf, t0 + BEAT * 3.5, approach, BEAT * .45, .20)

            // --- 电钢：第 2、4 拍反拍切分，三音省略根音（贝斯已经在弹）
            comp(buf, t0 + BEAT * 1.0, ch, .088, rnd)
            comp(buf, t0 + BEAT * 3.0, ch, .078, rnd)

            // --- 鼓刷：只有 4 个正拍 + 第 4 拍后的一个弱"搭"，别把 20 秒塞满
            //     （每拍都打会变成沙锤铺底，听久了比静音更累）
            for (b in 0 until 4) {
                brush(buf, t0 + b * BEAT, if (b == 1 || b == 3) .013 else .006, rnd)
            }
            brush(buf, t0 + BEAT * 3.66, .005, rnd)
        }

        // --- 旋律：只在第 3、7 小节进来，避免 20 秒里一直有人说话
        val motif = listOf(
            Triple(2, 0.0, 74), Triple(2, 0.75, 77), Triple(2, 1.5, 79), Triple(2, 2.5, 81),
            Triple(3, 0.0, 79), Triple(3, 1.5, 77), Triple(3, 2.5, 74),
            Triple(6, 0.0, 71), Triple(6, 1.0, 74), Triple(6, 2.0, 76),
            Triple(7, 0.0, 74), Triple(7, 2.0, 69),
        )
        for ((bar, beat, midi) in motif) {
            mel(buf, bar * BAR + beat * BEAT, midi, BEAT * 1.1, .095)
        }

        // --- 黑胶底噪 + 偶发爆点
        var noiseLp = 0.0
        for (i in buf.indices) {
            noiseLp = noiseLp * .92 + (rnd.nextDouble() * 2 - 1) * .08
            buf[i] += noiseLp * .006
        }
        repeat(14) {
            val pos = rnd.nextInt(total - 400)
            val amp = .02 + rnd.nextDouble() * .03
            for (k in 0 until 90) buf[pos + k] += (rnd.nextDouble() * 2 - 1) * amp * exp(-k / 18.0)
        }

        // --- 单极低通：把合成器的高频毛刺磨掉，听感接近老唱片
        val cutoff = 0.55
        var lp = 0.0
        for (i in buf.indices) {
            lp += cutoff * (buf[i] - lp)
            buf[i] = lp
        }

        // --- 尾巴折回开头，抹掉循环接缝
        for (i in 0 until pad) buf[i] += buf[total + i]

        // --- 软限幅 + 归一
        var peak = 1e-9
        for (i in 0 until total) peak = maxOf(peak, kotlin.math.abs(buf[i]))
        val norm = .82 / peak
        val out = ShortArray(total)
        for (i in 0 until total) {
            val v = tanh(buf[i] * norm * 1.1) * .9
            out[i] = (v * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** 走路贝斯：三角波偏正弦，短促起音，长衰减。 */
    private fun bass(buf: DoubleArray, at: Double, midi: Int, dur: Double, gain: Double) {
        val f = mtof(midi)
        val start = (at * SR).toInt()
        val len = (dur * SR).toInt()
        for (i in 0 until len) {
            val idx = start + i
            if (idx !in buf.indices) break
            val t = i.toDouble() / SR
            val env = exp(-3.2 * t) * (t / .008).coerceAtMost(1.0)
            val ph = 2 * PI * f * t
            val v = sin(ph) * .82 + sin(ph * 2) * .18
            buf[idx] += v * env * gain
        }
    }

    /** 电钢：FM 音色（调制指数快速衰减），三个音错开 14ms 做扫弦感。 */
    private fun comp(buf: DoubleArray, at: Double, chord: IntArray, gain: Double, rnd: Random) {
        val notes = chord.drop(1).take(3)
        notes.forEachIndexed { k, midi ->
            val f = mtof(midi)
            val start = ((at + k * .014) * SR).toInt()
            val len = (BEAT * 1.9 * SR).toInt()
            for (i in 0 until len) {
                val idx = start + i
                if (idx !in buf.indices) break
                val t = i.toDouble() / SR
                val env = exp(-2.4 * t) * (t / .006).coerceAtMost(1.0)
                val mod = sin(2 * PI * f * 2 * t) * 1.3 * exp(-7.0 * t)
                val v = sin(2 * PI * f * t + mod)
                buf[idx] += v * env * gain
            }
        }
    }

    /** 鼓刷：一小段高通噪声，快速衰减。 */
    private fun brush(buf: DoubleArray, at: Double, gain: Double, rnd: Random) {
        val start = (at * SR).toInt()
        val len = (.045 * SR).toInt()
        var prev = 0.0
        for (i in 0 until len) {
            val idx = start + i
            if (idx !in buf.indices) break
            val t = i.toDouble() / SR
            val n = rnd.nextDouble() * 2 - 1
            val hp = (n - prev) * .7 + prev * .3
            prev = n
            buf[idx] += hp * exp(-110.0 * t) * gain
        }
    }

    /** 旋律：带 4.5Hz 轻颤的音，起音慢一点，像吹奏。 */
    private fun mel(buf: DoubleArray, at: Double, midi: Int, dur: Double, gain: Double) {
        val f = mtof(midi)
        val start = (at * SR).toInt()
        val len = (dur * SR).toInt()
        for (i in 0 until len) {
            val idx = start + i
            if (idx !in buf.indices) break
            val t = i.toDouble() / SR
            val env = (t / .05).coerceAtMost(1.0) * exp(-1.6 * t)
            val vib = 1.0 + sin(2 * PI * 4.5 * t) * .004
            buf[idx] += sin(2 * PI * f * vib * t) * env * gain
        }
    }

    private fun mtof(midi: Int): Double = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    /** 16bit 单声道 WAV。 */
    private fun wav(pcm: ShortArray): ByteArray {
        val data = pcm.size * 2
        val out = ByteArray(44 + data)
        fun i32(v: Int, o: Int) {
            out[o] = (v and 0xff).toByte()
            out[o + 1] = (v shr 8 and 0xff).toByte()
            out[o + 2] = (v shr 16 and 0xff).toByte()
            out[o + 3] = (v shr 24 and 0xff).toByte()
        }
        fun i16(v: Int, o: Int) {
            out[o] = (v and 0xff).toByte()
            out[o + 1] = (v shr 8 and 0xff).toByte()
        }
        "RIFF".forEachIndexed { i, c -> out[i] = c.code.toByte() }
        i32(36 + data, 4)
        "WAVE".forEachIndexed { i, c -> out[8 + i] = c.code.toByte() }
        "fmt ".forEachIndexed { i, c -> out[12 + i] = c.code.toByte() }
        i32(16, 16)
        i16(1, 20)          // PCM
        i16(1, 22)          // 单声道
        i32(SR, 24)
        i32(SR * 2, 28)     // byte rate
        i16(2, 32)          // block align
        i16(16, 34)         // bits
        "data".forEachIndexed { i, c -> out[36 + i] = c.code.toByte() }
        i32(data, 40)
        var o = 44
        for (s in pcm) { i16(s.toInt(), o); o += 2 }
        return out
    }
}
