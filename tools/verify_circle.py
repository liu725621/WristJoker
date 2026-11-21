#!/usr/bin/env python3.11
"""圆屏版式校验（v5）。

这一版**所有九个界面**都是弦长版式：每一行的高度固定、宽度取
「这一行离圆心更远那条边」的弦长 `2·√(r² − dy²)`，r = 108 − 9 = 99dp。
于是每一行的四个角**恰好**落在半径 99dp 的内容圆上：既不切边，
也不浪费面积。

v4 只改了牌桌和商店，其余七页仍是 122dp 竖带 —— 玩家从牌桌点进
结算页，界面会从贴着圆弧的版式缩回一个矩形。这是"像给圆屏套了
安全区"的直接来源，所以这一版把七页全部接上弦长。

判据：每行的四角半径 ≤ 108 − 4 = 104dp（离玻璃至少 4dp）。
再跑两级容差：真机字体行高比预览高 1~3dp 时，总高变大 → 每行
离圆心更远 → 弦长变小，四角半径仍不会超过 105dp。
"""
import math

SCREEN_R = 108.0        # Watch7 216dp 见方
INSET = 9.0             # 内容圆距玻璃的余量，与 CircleFit.INSET 一致
CONTENT_R = SCREEN_R - INSET
LIMIT = SCREEN_R - 4.0  # 允许的最远四角半径


def chord(dy, inset=INSET):
    r = SCREEN_R - inset
    if abs(dy) >= r:
        return 0.0
    return 2.0 * math.sqrt(r * r - dy * dy)


def bands_layout(bands):
    """bands: [(label, h, gap)] -> (total, [(label, w, cy, h, corner_r)])"""
    total = sum(h for _, h, _ in bands) + sum(g for _, _, g in bands[:-1])
    y = -total / 2.0
    out = []
    for label, h, g in bands:
        cy = y + h / 2.0
        far = abs(cy) + h / 2.0
        w = chord(far)
        corner = math.hypot(w / 2.0, far)
        out.append((label, w, cy, h, corner))
        y += h + g
    return total, out


def check_bands(name, bands, quiet=False):
    total, rows = bands_layout(bands)
    worst = max(r[4] for r in rows)
    ok = worst <= LIMIT
    if not quiet:
        print(f"\n=== {name}（弦长版式）===")
        print(f"总高 {total:.1f}dp  内容圆半径 {CONTENT_R:.0f}dp  玻璃半径 {SCREEN_R:.0f}dp")
        print(f"{'横带':<20}{'宽':>7}{'中心y':>8}{'高':>6}{'四角r':>8}  判定")
        for label, w, cy, h, r in rows:
            flag = "OK " if r <= LIMIT else "贴边"
            print(f"{label:<20}{w:>7.1f}{cy:>8.1f}{h:>6.1f}{r:>8.1f}  {flag}")
        print(f"最远角 {worst:.1f}dp <= {LIMIT:.0f}dp : {'PASS' if ok else 'FAIL'}")
    return ok, worst, total


# ------------------------------------------------------------------ 九个界面
# 每一条都与 Kotlin 里的 xxx_BANDS 一一对应（高度 / 间距）。

PAGES = {
    "主牌桌 PLAYING": [
        ("带0 底注/盲注/手牌", 11, 2),
        ("带1 小丑+消耗品", 13, 2),
        ("带2 出牌区", 22, 2),
        ("带3 筹码x倍率", 15, 1),
        ("带4 进度", 4, 2),
        ("带5 手牌扇形", 40, 8),   # 与按钮拉开 8dp，防出牌时误触手牌
        ("带6 弃/出", 20, 0),
    ],
    "商店 SHOP": [
        ("带0 顶栏", 12, 2),
        ("带1 已拥有", 12, 2),
        ("带2 商品位", 34, 3),
        ("带3 补充包", 30, 3),
        ("带4 代金券x2", 26, 2),
        ("带5 重掷/继续", 20, 0),
    ],
    "开始界面 MENU": [
        ("牌背", 24, 2),
        ("标题", 22, 1),
        ("点阵 JOKER", 9, 2),
        ("存档/最佳", 11, 3),
        ("主按钮", 26, 3),
        ("新局+挑战", 22, 3),
        ("生涯/成就/种子", 20, 2),
        ("图鉴/设置", 20, 0),
    ],
    "牌组选择 DECK_SELECT": [
        ("标题行", 12, 2),
        ("赌注行", 14, 2),
        ("列表视口 15 行", 88, 3),
        ("返回", 22, 0),
    ],
    "挑战选择 CHALLENGE_SELECT": [
        ("标题行", 12, 2),
        ("列表视口 20 行", 94, 3),
        ("返回", 22, 0),
    ],
    "盲注选择 BLIND_SELECT": [
        ("标题行", 14, 3),
        ("小盲卡", 24, 2),
        ("大盲卡", 24, 2),
        ("Boss 卡", 24, 3),
        ("按钮行", 24, 0),
    ],
    "结算页 CASH_OUT": [
        ("标题", 18, 1),
        ("副标", 9, 3),
        ("分数", 20, 4),
        ("账单 6 行", 62, 5),
        ("收下按钮", 26, 0),
    ],
    "结束页 END": [
        ("牌背", 22, 2),
        ("标题", 20, 2),
        ("副标", 9, 4),
        ("账单 3 行", 34, 5),
        ("继续无尽/留白", 24, 3),
        ("按钮行", 24, 0),
    ],
    "开包 PACK_OPEN": [
        ("标题行", 12, 3),
        ("列表视口", 78, 3),
        ("按钮行", 22, 0),
    ],
    "详情页 DETAIL": [
        ("大图", 42, 2),
        ("标题", 12, 1),
        ("副标", 8, 3),
        ("正文 4 行", 44, 4),
        ("动作按钮", 26, 2),
        ("提示", 8, 0),
    ],
    "信息面板 PANEL": [
        ("分页条", 14, 2),
        ("内容视口", 92, 1),
        ("提示", 8, 0),
    ],
    "图鉴 COLLECTION": [
        ("标题+计数", 12, 2),
        ("五类分页", 14, 2),
        ("网格视口 4 列", 98, 2),
        ("详情带", 28, 2),
        ("返回", 20, 0),
    ],
    "设置 SETTINGS": [
        ("标题", 12, 3),
        ("五行开关", 100, 3),
        ("返回", 18, 0),
    ],
    "牌堆查看 DECK_VIEW": [
        ("标题+计数", 12, 2),
        ("两页签+排序", 13, 2),
        ("网格视口 4 列", 84, 2),
        ("返回", 20, 0),
    ],
    "牌型等级 RUN_INFO": [
        ("标题", 12, 2),
        ("六行两列", 84, 2),
        ("提示", 13, 2),
        ("返回", 20, 0),
    ],
    "生涯 CAREER": [
        ("标题", 11, 2),
        ("十二格", 74, 2),
        ("返回", 20, 0),
    ],
    "成就 ACH": [
        ("标题", 11, 2),
        ("列表视口 20 条", 76, 2),
        ("返回", 20, 0),
    ],
    "种子 SEED": [
        ("标题", 11, 2),
        ("六位数字", 30, 2),
        ("说明", 14, 2),
        ("开局/每日/返回", 20, 0),
    ],

}

results = {}
worst_all = 0.0
for name, bands in PAGES.items():
    ok, worst, total = check_bands(name, bands)
    results[name] = ok
    worst_all = max(worst_all, worst)

# ------------------------------------------------------------------ 宽度对比

print("\n=== 从 122dp 竖带换成弦长：每页最宽的那一条 ===")
print(f"{'界面':<24}{'上一版':>7}{'现在':>7}{'拿回':>7}")
for name in ("开始界面 MENU", "牌组选择 DECK_SELECT", "挑战选择 CHALLENGE_SELECT",
             "盲注选择 BLIND_SELECT", "结算页 CASH_OUT", "结束页 END",
             "开包 PACK_OPEN", "详情页 DETAIL", "信息面板 PANEL",
             "图鉴 COLLECTION", "设置 SETTINGS",
             "生涯 CAREER", "成就 ACH", "种子 SEED"):
    _, rows = bands_layout(PAGES[name])
    w = max(r[1] for r in rows)
    print(f"{name:<24}{122:>7}{w:>7.1f}{w - 122:>+7.1f}")

print("\n=== 牌桌七条带：上一版写死宽 vs 现在 ===")
old_play = {0: 110, 1: 132, 2: 154, 3: 146, 4: 150, 5: 154, 6: 106}
_, play_rows = bands_layout(PAGES["主牌桌 PLAYING"])
print(f"{'横带':<20}{'上一版':>7}{'现在':>7}{'拿回':>7}")
for i, (label, w, cy, h, r) in enumerate(play_rows):
    print(f"{label:<20}{old_play[i]:>7}{w:>7.1f}{w - old_play[i]:>+7.1f}")

# ------------------------------------------------------------------ 手牌扇形

print("\n=== 手牌扇形 ===")
hand_w = play_rows[5][1] - 12
cw = 25.0
for n in (5, 8, 10):
    reveal = min(cw * 0.72, (hand_w - cw) / (n - 1))
    span = reveal * (n - 1) + cw
    print(f"  {n} 张：露出 {reveal:.1f}dp，展开 {span:.1f}dp / 可用 {hand_w:.1f}dp "
          f"{'PASS' if span <= hand_w else 'FAIL'}")

# ------------------------------------------------------------------ 容差

print("\n=== 容差模拟（每行行高 +1 / +2 / +3dp）===")
for name in ("主牌桌 PLAYING", "商店 SHOP", "结算页 CASH_OUT", "详情页 DETAIL"):
    for extra in (1, 2, 3):
        g = [(n, h + extra, gap) for n, h, gap in PAGES[name]]
        _, w, t = check_bands("", g, quiet=True)
        print(f"  {name} +{extra}dp/行 -> 总高 {t:.1f}dp，最远角 {w:.1f}dp  "
              f"{'PASS' if w <= 105 else 'FAIL'}")

print("\n" + "=" * 62)
allok = all(results.values())
for k, v in results.items():
    print(f"{k:<26} {'PASS' if v else 'FAIL'}")
print(f"OVERALL = {allok}   最远四角 {worst_all:.1f}dp")
