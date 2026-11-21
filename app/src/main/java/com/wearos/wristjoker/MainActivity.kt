package com.wearos.wristjoker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wearos.wristjoker.fx.Haptics
import com.wearos.wristjoker.fx.Music
import com.wearos.wristjoker.fx.Sfx
import com.wearos.wristjoker.game.Ach
import com.wearos.wristjoker.game.GameEngine
import com.wearos.wristjoker.game.GameUi
import com.wearos.wristjoker.game.PackContent
import com.wearos.wristjoker.game.Phase
import com.wearos.wristjoker.game.ShopItem
import com.wearos.wristjoker.game.SortMode
import com.wearos.wristjoker.tile.TileRefresh
import com.wearos.wristjoker.ui.BlindSelectScreen
import com.wearos.wristjoker.ui.CareerScreen
import com.wearos.wristjoker.ui.AchBanner
import com.wearos.wristjoker.ui.AchievementsScreen
import com.wearos.wristjoker.ui.SeedScreen
import com.wearos.wristjoker.ui.CashOutScreen
import com.wearos.wristjoker.ui.ChallengeScreen
import com.wearos.wristjoker.ui.CollectionScreen
import com.wearos.wristjoker.ui.CrtOverlay
import com.wearos.wristjoker.ui.DeckSelectScreen
import com.wearos.wristjoker.ui.EndScreen
import com.wearos.wristjoker.ui.FeltBackground
import com.wearos.wristjoker.ui.GameScreen
import com.wearos.wristjoker.ui.InfoPanel
import com.wearos.wristjoker.ui.DeckViewScreen
import com.wearos.wristjoker.ui.RunInfoScreen
import com.wearos.wristjoker.ui.Pal
import com.wearos.wristjoker.ui.DetailOverlay
import com.wearos.wristjoker.ui.DetailTarget
import com.wearos.wristjoker.ui.MenuScreen
import com.wearos.wristjoker.ui.PackScreen
import com.wearos.wristjoker.ui.SettingsScreen
import com.wearos.wristjoker.ui.ShopScreen

/**
 * 单 Activity + 阶段切换。
 *
 * 不给这个小游戏上导航库：界面由 [Phase] 直接分支，比引入
 * navigation-compose 更轻、重建更快。八种界面共用同一层赌桌毡背景。
 *
 * 三层叠放：赌桌毡 → 当前界面 → 信息面板 → CRT 扫描线。扫描线放最上层
 * 但不注册 pointerInput，触摸事件会穿透到下面的按钮和卡牌。
 *
 * **自动存档**在这里落地：引擎不是 Compose 状态，而是 Activity 持有的
 * 长生命周期对象；`onStop` 时直接 `persist(engine)`，所以抬腕息屏、
 * 被系统回收、玩家按返回，都不会丢进度。
 */
class MainActivity : ComponentActivity() {

    private lateinit var store: SaveStore
    private lateinit var engine: GameEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 一局可能打十几分钟，中途息屏会让玩家丢掉节奏
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        Sfx.init(this)
        Haptics.init(this)
        Music.init(this)

        store = SaveStore(this)
        engine = GameEngine()
        // 冷启动先尝试恢复；恢复失败视为没有存档，主菜单会显示"开始游戏"
        val restored = store.restore(engine)
        engine.hasSave = restored

        setContent { WristJokerApp(engine, store) }
    }

    override fun onStart() {
        super.onStart()
        Music.setActive(true)
    }

    override fun onStop() {
        super.onStop()
        Music.setActive(false)
        // 只要不是停在菜单 / 选牌组 / 选挑战 / 结束态，就落盘。
        // 结束态不存 —— 已结束的局没必要"继续"；选牌组/选挑战存下来，
        // 下次启动会落在一个没有手牌的页面上。
        val p = engine.export().phase
        if (p != Phase.MENU && p != Phase.DECK_SELECT && p != Phase.CHALLENGE_SELECT &&
            p != Phase.GAME_OVER && p != Phase.WIN
        ) {
            store.persist(engine)
        }
        // 离场是卡片最容易被看一眼的时刻（抬腕 → 右滑），强制刷一次，
        // 不走 refresh() 的 2 秒节流。
        TileRefresh.nudge(this, force = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        Sfx.release()
        Haptics.release()
        Music.release()
    }
}

@Composable
fun WristJokerApp(engine: GameEngine, store: SaveStore) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var ui by remember { mutableStateOf(engine.snapshot()) }
    /** 0 = 关，1 = 小丑页，2 = 消耗品页，3 = 等级页，4 = 券与标签页 */
    var panel by remember { mutableIntStateOf(0) }
    /** 详情页：非空时盖住当前界面。 */
    var detail by remember { mutableStateOf<DetailTarget?>(null) }
    var recorded by remember { mutableStateOf(false) }
    /** 菜单上的两个覆盖层：图鉴 / 设置。它们不是 Phase，盖在菜单上即可。 */
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    /** 手牌排序模式：只在牌桌用，不落盘（下一局重新按点数排更符合直觉）。 */
    var sortMode by remember { mutableStateOf(SortMode.RANK) }
    var seen by remember { mutableStateOf(store.seen()) }
    var maxStake by remember { mutableIntStateOf(store.maxStake) }
    /** 刚解锁的成就：非空时在顶部压一条横幅，3 秒后自己消失。 */
    var achToast by remember { mutableStateOf<Ach?>(null) }
    /** 生涯成绩单：商店稀有档与牌组解锁读它。 */
    var career by remember { mutableStateOf(store.careerStats(store.seenTotal)) }

    // 引擎开局前就要拿到生涯快照，否则第一局的商店池是"全开"的
    LaunchedEffect(Unit) { engine.career = career }

    LaunchedEffect(achToast) {
        if (achToast != null) {
            kotlinx.coroutines.delay(3200)
            achToast = null
        }
    }

    // 设置项：一轮生命周期内只从磁盘读一次，之后即时生效
    var musicLevel by remember { mutableIntStateOf(store.musicLevel) }
    var sfxLevel by remember { mutableIntStateOf(store.sfxLevel) }
    var hapticsOn by remember { mutableStateOf(store.hapticsOn) }
    var crtLevel by remember { mutableIntStateOf(store.crtLevel) }
    var animSpeed by remember { mutableIntStateOf(store.animSpeed) }

    LaunchedEffect(sfxLevel) {
        Sfx.setMasterVolume(listOf(0f, .35f, .7f, 1f)[sfxLevel.coerceIn(0, 3)])
        Sfx.setEnabled(sfxLevel > 0)
    }
    LaunchedEffect(hapticsOn) { Haptics.setEnabled(hapticsOn) }
    LaunchedEffect(musicLevel) {
        // 音乐比音效更"占耳朵"，同一档位下音量压到三成左右
        Music.setVolume(listOf(0f, .18f, .32f, .5f)[musicLevel.coerceIn(0, 3)])
        Music.setEnabled(musicLevel > 0)
    }
    // 场景切换只改速度：菜单最松弛，Boss 慢下来，结算页轻快一点
    LaunchedEffect(ui.phase, ui.isBoss) {
        val mood = when (ui.phase) {
            Phase.PLAYING, Phase.BLIND_SELECT -> if (ui.isBoss) Music.Mood.BOSS else Music.Mood.PLAY
            Phase.CASH_OUT, Phase.SHOP, Phase.PACK_OPEN -> Music.Mood.CASH
            else -> Music.Mood.MENU
        }
        Music.play(mood)
    }

    fun refresh() {
        ui = engine.snapshot()
        // 每一次有效操作都落盘。SharedPreferences.apply() 是异步写，
        // 一局几十次调用不会造成卡顿，换来的是"随时被打断都不丢进度"。
        // 菜单/选牌组/选挑战/结束态不存：这些不是"进行中的一局"，
        // 存下来会让下次启动的"继续"落在一个没有手牌的页面上。
        val p = ui.phase
        if (p != Phase.MENU && p != Phase.DECK_SELECT && p != Phase.CHALLENGE_SELECT &&
            p != Phase.GAME_OVER && p != Phase.WIN
        ) {
            store.persist(engine)
        }
        // 卡片（Tile）读的是同一份存档，所以阶段一变就该让它跟上。
        // 结束态要强制刷：那一帧 `store.wipe()` 把存档抹了，卡片得立刻
        // 退回"轻触开局"，否则通关后卡片还挂着上一局的分数。
        TileRefresh.nudge(
            context,
            force = p == Phase.GAME_OVER || p == Phase.WIN,
        )
        // 图鉴：把这一帧"出现过"的东西都记成已解锁。商店里摆着的、
        // 手里拿着的、包内开出的都算 —— 玩家见过就不该在图鉴里还是个问号。
        store.markSeen(seenIdsOf(ui))

        if ((ui.phase == Phase.GAME_OVER || ui.phase == Phase.WIN) && !recorded) {
            recorded = true
            store.recordRun(
                ante = ui.ante,
                hand = ui.bestHand,
                won = ui.phase == Phase.WIN,
                score = ui.score,
                money = ui.money,
                hands = ui.handsPlayed.toLong(),
                discards = ui.discardsTotal.toLong(),
                stakeOrdinal = ui.stake.ordinal,
            )
            // 通关（不是失败）才解锁下一档赌注。无尽模式也走 WIN，
            // 所以打通一次就能继续往上，不需要单独再赢一遍。
            if (ui.phase == Phase.WIN) {
                store.unlockNextStake(ui.stake)
                maxStake = store.maxStake
            }
            // 成就：重新判定一遍，把"刚解锁"的那几条弹出来
            val fresh = store.refreshAchievements()
            if (fresh.isNotEmpty()) {
                achToast = Ach.ALL.firstOrNull { it.id == fresh.first() }
            }
            // 成绩单变了 → 商店池与牌组解锁跟着变，引擎拿新快照
            engine.career = store.careerStats()
            store.wipe()
            engine.hasSave = false
        }
        if (ui.phase == Phase.BLIND_SELECT || ui.phase == Phase.PLAYING) recorded = false
    }

    Box(Modifier.fillMaxSize()) {
        FeltBackground(Modifier.fillMaxSize())

        when (ui.phase) {
            Phase.MENU -> MenuScreen(
                ui = ui,
                bestAnte = store.bestAnte,
                bestHand = store.bestHand,
                // 「继续」= 从磁盘把那一局读回来。引擎此时已经被 toMenu() 推到
                // MENU，只有存档里还留着 PLAYING 的那一帧。
                onContinue = {
                    if (store.restore(engine)) {
                        Haptics.tap(2)
                    }
                    refresh()
                },
                onNewRun = {
                    // 开新局 = 放弃旧局，顺手把存档抹掉：否则在选牌组页退出 App，
                    // 下次启动会被旧档"复活"回上一局，玩家会以为点错了按钮。
                    store.wipe()
                    engine.hasSave = false
                    // 生涯可能在上一局结束时变过（解锁了新牌组），重新取一次
                    career = store.careerStats(store.seenTotal)
                    engine.career = career
                    engine.toDeckSelect()
                    refresh()
                },
                onChallenges = {
                    store.wipe()
                    engine.hasSave = false
                    engine.toChallengeSelect()
                    refresh()
                },
                onCollection = {
                    seen = store.seen()
                    overlay = Overlay.COLLECTION
                },
                onSettings = { overlay = Overlay.SETTINGS },
                onCareer = {
                    career = store.careerStats(store.seenTotal)
                    overlay = Overlay.CAREER
                },
                onAchievements = {
                    seen = store.seen()
                    overlay = Overlay.ACH
                },
                onSeed = { overlay = Overlay.SEED },
            )

            Phase.DECK_SELECT -> DeckSelectScreen(
                stake = ui.stake,
                maxStake = maxStake,
                bestAnte = career.bestAnte,
                onStake = { engine.setStake(it); refresh() },
                onPick = { deck ->
                    engine.startRun(deck, ui.stake)
                    Sfx.play(Sfx.Kind.DEAL)
                    Haptics.tap(2)
                    refresh()
                },
                onBack = { engine.toMenu(); refresh() },
            )

            Phase.CHALLENGE_SELECT -> ChallengeScreen(
                onPick = { c ->
                    engine.startChallenge(c)
                    Sfx.play(Sfx.Kind.DEAL)
                    Haptics.tap(2)
                    refresh()
                },
                onBack = { engine.toMenu(); refresh() },
            )

            Phase.BLIND_SELECT -> BlindSelectScreen(
                ui = ui,
                onSelect = {
                    engine.selectBlind()
                    Sfx.play(Sfx.Kind.DEAL)
                    Haptics.tap(1)
                    refresh()
                },
                onSkip = {
                    engine.skipBlind()
                    Sfx.play(Sfx.Kind.BUY)
                    Haptics.tap(2)
                    refresh()
                },
            )

            Phase.PLAYING -> GameScreen(
                ui = ui,
                onToggle = { id -> engine.toggle(id); refresh() },
                onPlay = {
                    val r = engine.play()
                    if (r != null) {
                        Sfx.play(Sfx.Kind.DEAL)
                        Haptics.tap(2)
                        refresh()
                    } else {
                        Haptics.tap(0)
                    }
                },
                onDiscard = {
                    if (engine.discard()) {
                        Sfx.play(Sfx.Kind.SELECT, .6f)
                        Haptics.tap(0)
                        refresh()
                    }
                },
                onOpenJokers = { panel = 1 },
                onUseConsumable = { panel = 2 },
                onResolve = {
                    engine.resolvePending()
                    refresh()
                },
                onMove = { from, to ->
                    engine.moveCard(from, to)
                    Sfx.play(Sfx.Kind.SELECT, .4f)
                    refresh()
                },
                onSort = {
                    val next = sortMode.next()
                    sortMode = next
                    engine.sortHand(next)
                    Sfx.play(Sfx.Kind.SELECT, .6f)
                    Haptics.tap(0)
                    refresh()
                },
                onOpenDeck = { overlay = Overlay.DECK },
                onOpenInfo = { overlay = Overlay.INFO },
                onExit = {
                    // 先落盘再改 phase：toMenu() 之后 refresh() 就不再有
                    // "进行中的一局"可写，顺序反了会丢这一局。
                    store.persist(engine)
                    engine.toMenu()
                    Haptics.tap(1)
                    refresh()
                },
                sortMode = sortMode,
                speed = when (animSpeed) {
                    0 -> .7f
                    2 -> 1.5f
                    else -> 1f
                },
            )

            Phase.CASH_OUT -> CashOutScreen(
                ui = ui,
                onConfirm = {
                    engine.confirmCashOut()
                    Sfx.play(Sfx.Kind.COIN)
                    Haptics.tap(2)
                    refresh()
                },
            )

            Phase.SHOP -> ShopScreen(
                ui = ui,
                onOpenDetail = { detail = it },
                onReroll = {
                    if (engine.reroll()) {
                        Sfx.play(Sfx.Kind.DEAL)
                        Haptics.tap(1)
                    }
                    refresh()
                },
                onLeave = {
                    engine.leaveShop()
                    Haptics.tap(1)
                    refresh()
                },
            )

            Phase.PACK_OPEN -> PackScreen(
                ui = ui,
                onToggle = { i ->
                    engine.togglePackPick(i)
                    Sfx.play(Sfx.Kind.SELECT, .55f)
                    Haptics.tap(0)
                    refresh()
                },
                onConfirm = {
                    if (engine.confirmPack()) {
                        Sfx.play(Sfx.Kind.BUY, .8f)
                        Haptics.tap(2)
                    } else {
                        Haptics.tap(0)
                    }
                    refresh()
                },
                onSkip = {
                    engine.skipPack()
                    Haptics.tap(1)
                    refresh()
                },
            )

            Phase.GAME_OVER, Phase.WIN -> EndScreen(
                ui = ui,
                onRestart = {
                    engine.restart()
                    Haptics.tap(2)
                    refresh()
                },
                onMenu = { engine.toMenu(); refresh() },
                onEndless = {
                    engine.continueEndless()
                    Haptics.tap(2)
                    refresh()
                },
            )
        }

        // ---- 详情页：盖在一切之上（面板除外）----
        detail?.let { d ->
            DetailOverlay(
                target = d,
                money = ui.money,
                onBuy = { t ->
                    val ok = when (t) {
                        is DetailTarget.ShopEntry -> when (t.section) {
                            0 -> engine.buy(t.index)
                            1 -> engine.buyBooster(t.index)
                            else -> engine.buyVoucher(t.index)
                        }
                        else -> false
                    }
                    if (ok) {
                        Sfx.play(Sfx.Kind.BUY)
                        Haptics.tap(1)
                    } else {
                        Haptics.tap(0)
                    }
                    detail = null
                    refresh()
                },
                onSell = { t ->
                    if (t is DetailTarget.OwnedJoker) {
                        if (engine.sell(t.index)) {
                            Sfx.play(Sfx.Kind.SELL, .7f)
                            Haptics.tap(1)
                        }
                    }
                    detail = null
                    refresh()
                },
                onUse = { t ->
                    if (t is DetailTarget.OwnedConsumable) {
                        if (engine.useConsumable(t.index)) {
                            Sfx.play(Sfx.Kind.BUY, .7f)
                            Haptics.tap(2)
                        } else {
                            Haptics.tap(0)
                        }
                    }
                    detail = null
                    refresh()
                },
                onDismiss = { detail = null },
                lockedShop = ui.shop.locked,
                onLock = { i ->
                    if (engine.toggleLock(i)) {
                        Sfx.play(Sfx.Kind.SELECT, .7f)
                        Haptics.tap(1)
                    }
                    refresh()
                },
                onSellConsumable = { i ->
                    if (engine.sellConsumable(i)) {
                        Sfx.play(Sfx.Kind.SELL, .7f)
                        Haptics.tap(1)
                    }
                    detail = null
                    refresh()
                },
            )
        }

        // ---- 覆盖层：图鉴 / 设置 / 牌堆 / 牌型等级 ----
        //
        // 加一层近乎不透明的底：这四个页面都是"整页替换"，不是浮窗。
        // 之前直接叠在菜单上，菜单的字会从下面透出来（菜单元素少所以没被发现，
        // 盖在牌桌上就明显了）。
        if (overlay != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Pal.Bg.copy(alpha = .97f)),
            ) {
                when (overlay) {
                    Overlay.COLLECTION -> CollectionScreen(
                        seen = seen,
                        onBack = { overlay = null },
                    )
                    Overlay.SETTINGS -> SettingsScreen(
                        musicLevel = musicLevel,
                        sfxLevel = sfxLevel,
                        hapticsOn = hapticsOn,
                        crtLevel = crtLevel,
                        animSpeed = animSpeed,
                        onMusic = { musicLevel = it; store.musicLevel = it },
                        onSfx = { sfxLevel = it; store.sfxLevel = it; Sfx.play(Sfx.Kind.SELECT) },
                        onHaptics = { hapticsOn = it; store.hapticsOn = it; Haptics.tap(1) },
                        onCrt = { crtLevel = it; store.crtLevel = it },
                        onSpeed = { animSpeed = it; store.animSpeed = it },
                        onBack = { overlay = null },
                    )
                    Overlay.DECK -> DeckViewScreen(
                        drawPile = ui.drawPileCards,
                        discard = ui.discardCards,
                        sortMode = sortMode,
                        onSort = {
                            val next = sortMode.next()
                            sortMode = next
                            engine.sortHand(next)
                            Sfx.play(Sfx.Kind.SELECT, .6f)
                            refresh()
                        },
                        onBack = { overlay = null },
                    )
                    Overlay.INFO -> RunInfoScreen(
                        levels = ui.levels,
                        playedTypes = ui.playedTypes,
                        onBack = { overlay = null },
                    )
                    Overlay.CAREER -> CareerScreen(
                        stats = career,
                        onBack = { overlay = null },
                    )
                    Overlay.ACH -> AchievementsScreen(
                        earned = store.achievements(),
                        onBack = { overlay = null },
                    )
                    Overlay.SEED -> SeedScreen(
                        seed = ui.seed,
                        dailyKey = dailyKey(),
                        onStart = { s ->
                            val deck = ui.deckType
                            store.wipe()
                            engine.hasSave = false
                            engine.startRun(deck, ui.stake, seedOverride = s)
                            overlay = null
                            Sfx.play(Sfx.Kind.DEAL)
                            Haptics.tap(2)
                            refresh()
                        },
                        onDaily = {
                            val deck = ui.deckType
                            store.wipe()
                            engine.hasSave = false
                            engine.startDaily(deck, dailyKey())
                            overlay = null
                            Sfx.play(Sfx.Kind.DEAL)
                            Haptics.tap(2)
                            refresh()
                        },
                        onBack = { overlay = null },
                    )
                    null -> Unit
                }
            }
        }

        // ---- 信息面板：小丑 / 消耗品 / 等级 / 券 ----
        if (panel != 0) {
            InfoPanel(
                ui = ui,
                initialTab = panel - 1,
                onUseConsumable = { i ->
                    if (engine.useConsumable(i)) {
                        Sfx.play(Sfx.Kind.BUY, .7f)
                        Haptics.tap(2)
                    } else {
                        Haptics.tap(0)
                    }
                    // 用牌后手牌会变，面板留在原页方便连续操作
                    refresh()
                },
                onDiscardConsumable = { i ->
                    engine.discardConsumable(i)
                    refresh()
                },
                onDismiss = { panel = 0 },
            )
        }

        CrtOverlay(Modifier.fillMaxSize(), crtLevel)

        // 成就横幅压在最上层（含 CRT 之上）：它是"刚刚发生"的通知，
        // 不该被扫描线削弱对比度，也不该被任何面板盖住。
        achToast?.let { a ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                AchBanner(a)
            }
        }
    }
}

/** 菜单上的两个全屏覆盖层。 */
private enum class Overlay { COLLECTION, SETTINGS, DECK, INFO, CAREER, ACH, SEED }

/**
 * 每日挑战的日期键：`20261121`。
 *
 * 用本地时区而不是 UTC —— 玩家眼里的"今天"就是手表上的今天，
 * 若按 UTC 算，东八区的人在早上八点前会看到昨天的种子，
 * 这种"每天不一样但不是我以为的那天"比不统一更难受。
 */
private fun dailyKey(): Int {
    val c = java.util.Calendar.getInstance()
    return c.get(java.util.Calendar.YEAR) * 10000 +
        (c.get(java.util.Calendar.MONTH) + 1) * 100 +
        c.get(java.util.Calendar.DAY_OF_MONTH)
}

/**
 * 把一帧 UI 快照里"出现过"的图鉴 id 全部收集起来。
 *
 * 商店的 [ShopItem.key] 与图鉴 id 用的是同一套前缀（`j:` / `c:` / `v:`），
 * 所以第一段可以直接复用；补充包的 `b:` 前缀不在图鉴里，跳过。
 */
private fun seenIdsOf(ui: GameUi): List<String> {
    val out = ArrayList<String>(24)
    ui.jokers.forEach { out += "j:${it.id}" }
    ui.consumables.forEach { out += "c:${it.key}" }
    ui.vouchers.forEach { out += "v:${it.key}" }
    ui.tags.forEach { out += "t:${it.key}" }
    out += "d:${ui.deckType.name}"

    ui.shop.all.forEach { item ->
        when (item) {
            is ShopItem.JokerItem -> out += item.key
            is ShopItem.ConsumableItem -> out += item.key
            is ShopItem.VoucherItem -> out += item.key
            is ShopItem.BoosterItem -> Unit
        }
    }

    ui.pack?.contents?.forEach { c ->
        when (c) {
            is PackContent.Joker -> out += "j:${c.def.id}"
            is PackContent.Tarot -> out += "c:${c.c.key}"
            is PackContent.Planet -> out += "c:${c.c.key}"
            is PackContent.Playing -> Unit
            else -> Unit
        }
    }
    return out
}
