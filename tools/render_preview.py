#!/usr/bin/env python3.11
"""圆屏界面预览渲染。

按 Compose 的实际排版参数（dp）逐块绘制，输出 216dp @2x = 432px 的圆屏图。
用途是**检查布局与可读性**，不是像素级还原 Compose 输出：
字体会差一点（Compose 用系统 sans，这里是 Noto Sans CJK），
点阵字用矢量近似，但每个元素的宽/高/间距都与源码里的 dp 值一致。

所以这张图能回答"会不会切边、字会不会糊、有没有挤在一起"，
不能回答"真机上滚动手感如何"。
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont

S = 2                     # 1dp = 2px（Watch7 432px / 216dp）
W = 216 * S
R_SAFE = 96 * S           # 可用半径

FONT_PATH = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
FONT_BOLD = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"

# ---- 调色板（对齐 Palette.kt 的 G.C 值）----
# 1999 design tokens（与 Palette.kt 一一对应）
INK900 = (27, 23, 19)
INK800 = (44, 38, 32)
INK700 = (59, 51, 42)
AMBER = (224, 163, 92)
AMBER500 = (217, 142, 74)
RAIN = (125, 140, 134)

FELT_DARK = INK900
FELT = INK800
FELT_HI = (92, 100, 85)
SURFACE = INK800
SURFACE_HI = INK700
CHIP = (94, 155, 176)
MULT = (201, 96, 79)
MONEY = (220, 168, 94)
GOLD = AMBER
GREEN = (127, 161, 132)
BLIND_GREEN = (95, 115, 96)
BOSS_RED = (168, 71, 58)
PURPLE = (154, 117, 144)
CREAM = (243, 238, 225)
DIM = (156, 146, 127)
CARD_FACE = (232, 225, 209)
CARD_EDGE = (36, 30, 24)
SUIT_RED = (176, 74, 62)
SUIT_BLACK = (36, 30, 24)
GOLD_DIM = (138, 110, 66)


def fill_circle_mask():
    m = Image.new("L", (W, W), 0)
    ImageDraw.Draw(m).ellipse([0, 0, W - 1, W - 1], fill=255)
    return m


def font(dp, bold=False):
    # dp → px，字号按 1dp ≈ 1px 的 Compose sp 近似
    return ImageFont.truetype(FONT_BOLD if bold else FONT_PATH, max(6, int(dp * S)))


def xy(x_dp, y_dp):
    """dp 坐标（圆心为原点）→ 像素坐标。"""
    return (W / 2 + x_dp * S, W / 2 + y_dp * S)


def px(v):
    return v * S


def rrect(d, cx, cy, w, h, r, fill=None, outline=None, width=1):
    box = [cx - px(w) / 2, cy - px(h) / 2, cx + px(w) / 2, cy + px(h) / 2]
    d.rounded_rectangle(box, radius=px(r), fill=fill, outline=outline, width=width)


def text(d, cx, cy, s, size, color, anchor="mm", bold=False):
    d.text((cx, cy), s, font=font(size, bold), fill=color, anchor=anchor)


def draw_felt(img):
    """1999 深咖低长调：径向渐变 + 雨滴斜线 + 压印双线。

    与 Compose 的 `FeltBackground` 同步：中心略亮（像顶灯打在纸上），
    四周落黑；雨滴只在边缘，作为世界观母题的一点暗示。
    """
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W, W], fill=INK900)
    for n in range(70, 0, -1):
        rad = px(112) * n / 70
        t = n / 70
        c = tuple(int(INK900[k] + (INK800[k] - INK900[k]) * (1 - t) ** 1.4) for k in range(3))
        d.ellipse(
            [W / 2 - rad, W / 2 - rad - px(8), W / 2 + rad, W / 2 + rad - px(8)],
            fill=c,
        )
    # 雨滴：稀疏细斜线，色值是 RAIN 以 ~10% 混入底色后的结果
    rain = tuple(int(INK900[k] + (RAIN[k] - INK900[k]) * .10) for k in range(3))
    for n in range(30):
        t = ((n * 7919) % 101) / 101
        ph = ((n * 104729) % 97) / 97
        x0 = -px(24) + t * px(240)
        y0 = ph * W
        ln = W * (.09 + (n % 4) * .035)
        d.line([(x0, y0), (x0 - ln * .32, y0 + ln)], fill=rain, width=1)
    # 压印双线
    d.ellipse([px(1.5), px(1.5), W - px(1.5), W - px(1.5)], outline=(52, 43, 33), width=1)
    d.ellipse([px(4.5), px(4.5), W - px(4.5), W - px(4.5)], outline=(41, 35, 28), width=1)
    return img


# 数字牌 pips 排列（与 Art.kt 的 pipLayout 同步）：牌面归一化坐标 (x, y)。
PIP_LAYOUT = {
    "2": [(.56, .26), (.56, .77)],
    "3": [(.56, .26), (.56, .515), (.56, .77)],
    "4": [(.42, .26), (.70, .26), (.42, .77), (.70, .77)],
    "5": [(.42, .26), (.70, .26), (.56, .515), (.42, .77), (.70, .77)],
    "6": [(.42, .26), (.70, .26), (.42, .43), (.70, .43), (.42, .77), (.70, .77)],
    "7": [(.56, .20), (.42, .26), (.70, .26), (.42, .43), (.70, .43), (.42, .77), (.70, .77)],
    "8": [(.42, .26), (.70, .26), (.42, .43), (.70, .43), (.42, .60), (.70, .60), (.42, .77), (.70, .77)],
    "9": [(.42, .26), (.70, .26), (.42, .43), (.70, .43), (.56, .515),
          (.42, .60), (.70, .60), (.42, .77), (.70, .77)],
    "10": [(.42, .26), (.70, .26), (.56, .34), (.42, .43), (.70, .43),
           (.42, .60), (.70, .60), (.56, .69), (.42, .77), (.70, .77)],
}

# J/Q/K 像素半身像（10×14，与 Art.kt 的 courtSprite 同步）。
COURT_SPRITES = {
    "K": [
        ".C......C.",
        ".CCCCCCCC.",
        "..SSSSSS..",
        "..SKSSKS..",
        "..SSSSSS..",
        "...SSSS...",
        ".WWWWWWWW.",
        ".WWWWWWWW.",
        "..PPPPPP..",
        ".PPFFFFPP.",
        ".PPPPPPPP.",
        ".PP....PP.",
        ".PP....PP.",
        "..........",
    ],
    "Q": [
        ".C.CCCC.C.",
        ".CCCCCCCC.",
        "..SSSSSS..",
        "..SKSSKS..",
        "..SSSSSS..",
        "...SRRS...",
        ".RRRRRRRR.",
        ".RRFFFFRR.",
        ".RRRRRRRR.",
        ".RRRRRRRR.",
        ".RRRRRRRR.",
        ".RR....RR.",
        "..R....R..",
        "..........",
    ],
    "J": [
        "....B.....",
        "...BBB....",
        "..BBBBB...",
        "..SSSSS...",
        "..SKSSKS..",
        "..SSSSS...",
        "...SSS....",
        "..BBBBB...",
        ".BBFFFFBB.",
        ".BBBBBBB..",
        ".BBBBBBB..",
        ".BB...BB..",
        ".B.....B..",
        "..........",
    ],
}

COURT_COLORS = {
    "C": MONEY,
    "S": (232, 201, 160),
    "K": CARD_EDGE,
    "W": (242, 238, 227),
    "P": PURPLE,
    "R": SUIT_RED,
    "B": CHIP,
}


def draw_cardface(d, cx, cy, w, h, rank, suit, enh=None, seal=None, edition=None,
                  blocked=False, selected=False, scoring=False):
    """一张牌。与 CardFaceArt 的版面一致：左上索引块 + 中央 pips/人像/大花色。"""
    base = CARD_FACE
    if enh == "GOLD":
        base = MONEY
    elif enh == "STONE":
        base = (180, 185, 182)
    elif enh == "STEEL":
        base = (220, 227, 229)
    elif enh == "GLASS":
        base = (228, 232, 225)
    elif enh == "LUCKY":
        base = (239, 244, 228)

    edge = CARD_EDGE
    if selected:
        edge = AMBER
    elif blocked:
        edge = BOSS_RED
    elif scoring:
        edge = CREAM

    rrect(d, cx, cy, w, h, 3, fill=base, outline=edge,
          width=4 if selected else (3 if scoring else 2))
    if selected:
        # 选中内发光：向内收缩一圈亮边，重叠扇形里一眼可数选了几张
        rrect(d, cx, cy, w - 5, h - 5, 2, fill=None, outline=AMBER, width=1)

    x0 = cx - px(w) / 2
    y0 = cy - px(h) / 2

    if enh == "STONE":
        r = px(h) * 0.30
        d.polygon(
            [(cx - r, cy + r * .6), (cx - r * .7, cy - r * .5),
             (cx + r * .2, cy - r * .9), (cx + r, cy + r * .1), (cx + r * .6, cy + r * .7)],
            fill=(138, 144, 141), outline=CARD_EDGE,
        )
    else:
        # 左上索引块（下移到色带之下、缩小让位给中央 pips，与 CardFaceArt 一致）
        ink = SUIT_RED if suit in "♥♦" else SUIT_BLACK
        text(d, x0 + px(w) * .16, y0 + px(h) * .13, rank, h * 0.28, ink, bold=True)
        text(d, x0 + px(w) * .16, y0 + px(h) * .28, suit, h * 0.17, ink, bold=True)
        # 中央：A 大花色 / JQK 像素人像 / 数字 pips 排列（与 CardFaceArt 一致）
        if rank == "A":
            text(d, cx + px(w) * .04, cy + px(h) * .02, suit, h * 0.52, ink, bold=True)
        elif rank in ("J", "Q", "K"):
            sprite = COURT_SPRITES[rank]
            cols, rows = 10, len(sprite)
            cw = px(w) * .76 / cols
            chh = px(h) * .70 / rows
            ox = x0 + px(w) * .12
            oy = y0 + px(h) * .16
            for r, line in enumerate(sprite):
                for c, ch in enumerate(line):
                    col = ink if ch == "F" else COURT_COLORS.get(ch)
                    if col is None:
                        continue
                    d.rectangle([ox + c * cw, oy + r * chh,
                                 ox + (c + 1) * cw, oy + (r + 1) * chh], fill=col)
        else:
            pip = max(3.2, w * .15)
            for (pxc, pyc) in PIP_LAYOUT.get(rank, []):
                text(d, x0 + px(w) * pxc, y0 + px(h) * pyc, suit, pip, ink, bold=True)

    # 顶部双层通栏色带：上层版本、下层增强（与 CardFaceArt 一致）
    edh = max(3, px(h) * .085)
    if edition:
        cols = {"FOIL": CHIP, "HOLO": PURPLE}
        if edition in cols:
            d.rectangle([x0, y0, x0 + px(w), y0 + edh], fill=cols[edition])
        else:
            seg = px(w) / 4
            for i, c in enumerate([SUIT_RED, MONEY, GREEN, CHIP]):
                d.rectangle([x0 + i * seg, y0, x0 + (i + 1) * seg + 1, y0 + edh], fill=c)
    enhtop = y0 + (edh if edition else 0)
    enhh = max(2, px(h) * .075)
    enhc = {"BONUS": CHIP, "MULT": MULT, "LUCKY": GREEN,
            "GLASS": (159, 199, 218), "STEEL": (154, 167, 171),
            "GOLD": GOLD, "STONE": (138, 144, 141)}
    if enh in enhc:
        d.rectangle([x0, enhtop, x0 + px(w), enhtop + enhh], fill=enhc[enh])
    elif enh == "WILD":
        q = px(w) / 4
        for i, c in enumerate([SUIT_RED, MONEY, CHIP, GREEN]):
            d.rectangle([x0 + i * q, enhtop, x0 + (i + 1) * q + 1, enhtop + enhh], fill=c)

    # 蜡封：左下角蜡点 + 深色光圈
    if seal:
        sc = {"RED": SUIT_RED, "GOLD": MONEY, "BLUE": CHIP, "PURPLE": PURPLE}[seal]
        r = max(2, px(h) * .085)
        scx = x0 + px(w) * .11 + r * .6
        scy = y0 + px(h) - r - px(h) * .035
        d.ellipse([scx - r * 1.55, scy - r * 1.55, scx + r * 1.55, scy + r * 1.55],
                  fill=(22, 38, 42))
        d.ellipse([scx - r, scy - r, scx + r, scy + r], fill=sc)

    if blocked:
        d.line([x0, y0 + px(h), x0 + px(w), y0], fill=BOSS_RED, width=3)


def clip_line(x0, y0, x1, y1, box):
    """Cyrus-Beck 矩形裁剪：牌背的斜网必须画在牌内，否则会糊满整块圆屏。"""
    l, t, r, b = box
    dx, dy = x1 - x0, y1 - y0
    p = [-dx, dx, -dy, dy]
    q = [x0 - l, r - x0, y0 - t, b - y0]
    u0, u1 = 0.0, 1.0
    for pi, qi in zip(p, q):
        if pi == 0:
            if qi < 0:
                return None
        else:
            u = qi / pi
            if pi < 0:
                u0 = max(u0, u)
            else:
                u1 = min(u1, u)
    if u0 > u1:
        return None
    return (x0 + u0 * dx, y0 + u0 * dy, x0 + u1 * dx, y0 + u1 * dy)


def draw_cardback(d, cx, cy, w, h, base=INK800):
    """牌背：深咖 + 烫金双线 + 四角星 + 雨滴斜纹。

    与 Compose 的 `CardBackArt` 同步。牌背在牌桌上占比最大，
    它决定整块牌桌的年代感 —— 所以不用红底斜网，用装订边 + 徽记。
    """
    rrect(d, cx, cy, w, h, 3, fill=base, outline=CARD_EDGE, width=2)
    x0, y0 = cx - px(w) / 2, cy - px(h) / 2
    x1, y1 = x0 + px(w), y0 + px(h)
    # 雨滴斜纹
    step = px(w) / 6
    k = x0 - px(h)
    while k < x1 + px(h):
        d.line([(k, y0 + 2), (k - px(h) * .32, y1 - 2)], fill=(52, 45, 37), width=1)
        k += step
    # 烫金双框
    ix, iy = px(w) * .075, px(h) * .075
    d.rectangle([x0 + ix, y0 + iy, x1 - ix, y1 - iy], outline=AMBER, width=2)
    ix2, iy2 = ix * 1.62, iy * 1.62
    d.rectangle([x0 + ix2, y0 + iy2, x1 - ix2, y1 - iy2], outline=(110, 84, 52), width=1)
    # 四角星
    r = px(w) * .23
    cy2 = cy - px(h) * .04
    inner = r * .26
    d.polygon([
        (cx, cy2 - r), (cx + inner, cy2 - inner), (cx + r, cy2), (cx + inner, cy2 + inner),
        (cx, cy2 + r), (cx - inner, cy2 + inner), (cx - r, cy2), (cx - inner, cy2 - inner),
    ], fill=AMBER)
    d.ellipse([cx - r * .21, cy2 - r * .21, cx + r * .21, cy2 + r * .21], fill=base)
    d.ellipse([cx - r * .075, cy2 - r * .075, cx + r * .075, cy2 + r * .075], fill=CREAM)


def draw_joker_badge(d, cx, cy, size, color, seed=0):
    """矢量徽记小丑头像。

    与 Compose 的 `JokerPortrait` 同构：按小丑「在干什么」挑一个稳定的矢量符号，
    取代原来的 8×8 哈希假脸 —— 加钱的画硬币、加倍的画叉、涨筹码的画筹码堆。
    seed 在这里只用来轮换示例类别，让一张预览图里能同时看到几种徽记。
    """
    rrect(d, cx, cy, size, size, 2, fill=SURFACE, outline=color, width=2)
    s = px(size) * 0.72
    x0 = cx - s / 2
    y0 = cy - s / 2
    u = s / 8

    def X(t):
        return x0 + t * u

    def Y(t):
        return y0 + t * u

    glyph = seed % 9
    if glyph == 0:      # 筹码：三根横条
        for i in range(3):
            yy = Y(2.4 + i * 1.7)
            d.rounded_rectangle([X(1.4), yy, X(6.6), yy + u * 1.3], radius=u * 0.5, fill=CHIP)
    elif glyph == 1:    # 加号
        d.rectangle([X(3.55), Y(1.9), X(4.45), Y(6.1)], fill=MULT)
        d.rectangle([X(1.9), Y(3.55), X(6.1), Y(4.45)], fill=MULT)
    elif glyph == 2:    # 叉
        d.line([X(1.6), Y(1.6), X(6.4), Y(6.4)], fill=MULT, width=max(1, int(u * 0.85)))
        d.line([X(6.4), Y(1.6), X(1.6), Y(6.4)], fill=MULT, width=max(1, int(u * 0.85)))
    elif glyph == 3:    # 硬币
        d.ellipse([X(1.3), Y(1.3), X(6.7), Y(6.7)], fill=MONEY)
        d.ellipse([X(2.5), Y(2.5), X(5.5), Y(5.5)], fill=SURFACE)
        d.rectangle([X(3.55), Y(3.1), X(4.45), Y(4.9)], fill=MONEY)
    elif glyph == 4:    # 三叶草
        d.ellipse([X(2.85), Y(1.55), X(5.15), Y(3.85)], fill=GREEN)
        d.ellipse([X(1.45), Y(2.95), X(3.75), Y(5.25)], fill=GREEN)
        d.ellipse([X(4.25), Y(2.95), X(6.55), Y(5.25)], fill=GREEN)
        d.rectangle([X(3.7), Y(4.5), X(4.3), Y(6.8)], fill=GREEN)
    elif glyph == 5:    # 一张牌（牌型）
        d.rounded_rectangle([X(2), Y(1.5), X(6), Y(6.5)], radius=u * 0.5, fill=CREAM)
        d.polygon([(X(4), Y(2.7)), (X(5), Y(4)), (X(4), Y(5.3)), (X(3), Y(4))], fill=SUIT_RED)
    elif glyph == 6:    # 红桃（花色）
        d.ellipse([X(2.2), Y(1.6), X(4.1), Y(3.5)], fill=SUIT_RED)
        d.ellipse([X(3.9), Y(1.6), X(5.8), Y(3.5)], fill=SUIT_RED)
        d.polygon([(X(2.2), Y(3.0)), (X(5.8), Y(3.0)), (X(4), Y(6.6))], fill=SUIT_RED)
    elif glyph == 7:    # 弃牌：牌 + 箭头
        d.rounded_rectangle([X(1.4), Y(2), X(5), Y(6)], radius=u * 0.5, fill=CARD_EDGE)
        d.line([X(5.2), Y(4), X(6.5), Y(4)], fill=BOSS_RED, width=max(1, int(u * 0.55)))
        d.polygon([(X(6.5), Y(3.4)), (X(7.1), Y(4)), (X(6.5), Y(4.6))], fill=BOSS_RED)
    else:               # 皇冠（传奇）
        d.polygon([(X(1.6), Y(6.1)), (X(1.6), Y(3.1)), (X(3), Y(4.6)), (X(4), Y(2.1)),
                   (X(5), Y(4.6)), (X(6.4), Y(3.1)), (X(6.4), Y(6.1))], fill=MONEY)
        d.rectangle([X(1.6), Y(6.1), X(6.4), Y(6.9)], fill=MONEY)


def draw_button(d, cx, cy, w, h, label, accent, filled=True, enabled=True):
    bg = accent if (enabled and filled) else SURFACE
    fg = FELT_DARK if (enabled and filled) else (accent if enabled else DIM)
    rrect(d, cx, cy + px(1), w, h, 3, fill=CARD_EDGE if enabled else FELT_HI)
    rrect(d, cx, cy, w, h, 3, fill=bg, outline=CARD_EDGE, width=1)
    text(d, cx, cy, label, 9, fg, bold=True)


def draw_progress(d, cx, cy, w, h, p, color=MONEY):
    rrect(d, cx, cy, w, h, 2, fill=FELT_DARK, outline=CARD_EDGE, width=1)
    if p > 0:
        x0 = cx - px(w) / 2
        d.rounded_rectangle(
            [x0, cy - px(h) / 2, x0 + px(w) * min(1, p), cy + px(h) / 2],
            radius=px(2), fill=color,
        )
    for i in (1, 2, 3):
        x = cx - px(w) / 2 + px(w) * i / 4
        d.line([x, cy - px(h) / 2, x, cy + px(h) / 2], fill=FELT_HI, width=1)


def layout(blocks):
    """按 Compose 的 Column(Center) 语义堆叠，返回的 cy 已换算成像素。"""
    total = sum(h for _, _, h in blocks)
    y = -total / 2
    out = []
    for name, w, h in blocks:
        out.append((name, w, W / 2 + (y + h / 2) * S, h))
        y += h
    return out, total


RADIUS = 108.0
INSET = 9.0
CONTENT_R = RADIUS - INSET


def chord(dy, inset=INSET):
    r = RADIUS - inset
    return 0.0 if abs(dy) >= r else 2.0 * math.sqrt(r * r - dy * dy)


def layout_bands(bands):
    """弦长版式：bands = [(name, h, gap)]，宽度取这一行远边的弦长。

    与 CircleFit.widths() 完全同构 —— 所以这张预览图能当几何证明用。
    """
    total = sum(h for _, h, _ in bands) + sum(g for _, _, g in bands[:-1])
    y = -total / 2.0
    out = []
    for name, h, g in bands:
        cy = y + h / 2.0
        w = chord(abs(cy) + h / 2.0)
        out.append((name, w, W / 2 + cy * S, h))
        y += h + g
    return out, total


def paste_card(img, ccx, ccy, cw, ch, rank, suit, enh=None, seal=None,
               edition=None, selected=False, angle=0.0):
    """把一张牌画到临时层，旋转后贴回主图 —— 手牌扇形要靠这个才有倾角。"""
    pad = 6
    tmp = Image.new("RGBA", (int(px(cw)) + pad * 2, int(px(ch)) + pad * 2), (0, 0, 0, 0))
    td = ImageDraw.Draw(tmp)
    draw_cardface(td, tmp.width / 2, tmp.height / 2, cw, ch, rank, suit,
                  enh=enh, seal=seal, edition=edition, selected=selected)
    if abs(angle) > 0.1:
        tmp = tmp.rotate(angle, expand=True, resample=Image.BICUBIC)
    img.paste(tmp, (int(ccx - tmp.width / 2), int(ccy - tmp.height / 2)), tmp)


# ---------------------------------------------------------------- 各界面

def screen_menu(img):
    d = ImageDraw.Draw(img)
    rows, _ = layout_bands([
        ("back", 24, 2), ("title", 22, 1), ("pixel", 9, 2),
        ("hint", 11, 3), ("btn", 26, 3), ("pair", 22, 3), ("long", 20, 2),
        ("tools", 20, 0),
    ])
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "back":
            draw_cardback(d, cx, cy, 22, 30)
        elif name == "title":
            text(d, cx + 1, cy + 1, "腕上小丑", 21, INK700, bold=True)
            text(d, cx, cy, "腕上小丑", 21, CREAM, bold=True)
        elif name == "pixel":
            text(d, cx, cy, "J O K E R", 7.5, AMBER)
        elif name == "hint":
            text(d, cx, cy, "A3 · 小盲注 · $17 · 红注", 7, CREAM)
        elif name == "btn":
            draw_button(d, cx, cy, w * 0.62, 24, "继续 A3", MONEY)
        elif name == "pair":
            # 新局与挑战并排：两个都是"开一局"，分两行会把主按钮挤小
            bw = w * 0.34
            draw_button(d, cx - px(bw) / 2 - px(3), cy, bw, 22, "新的一局", CHIP, filled=False)
            draw_button(d, cx + px(bw) / 2 + px(3), cy, bw, 22, "挑战 20", PURPLE, filled=False)
        elif name == "long":
            bw = w * 0.26
            draw_button(d, cx - px(bw + 5), cy, bw, 19, "生涯", MONEY, filled=False)
            draw_button(d, cx, cy, bw, 19, "成就", AMBER, filled=False)
            draw_button(d, cx + px(bw + 5), cy, bw, 19, "种子", GREEN, filled=False)
        elif name == "tools":
            bw = w * 0.27
            draw_button(d, cx - px(bw) / 2 - px(3.5), cy, bw, 19, "图鉴", MONEY, filled=False)
            draw_button(d, cx + px(bw) / 2 + px(3.5), cy, bw, 19, "设置", CHIP, filled=False)


def screen_deck(img):
    d = ImageDraw.Draw(img)
    rows, _ = layout_bands([
        ("head", 12, 2), ("stake", 14, 2), ("list", 88, 3), ("back", 22, 0),
    ])
    decks = [
        ("红牌组", "弃牌 +1"), ("蓝牌组", "出牌 +1"), ("黄牌组", "开局 +$10"),
        ("黑牌组", "小丑位 +1 · 手牌 -1"),
    ]
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "head":
            text(d, cx - px(w) / 2, cy, "选牌组", 11, MONEY, anchor="lm", bold=True)
            text(d, cx + px(w) / 2, cy, "15 种 · 影响开局", 6, DIM, anchor="rm")
        elif name == "stake":
            rrect(d, cx, cy, w, 14, 3, fill=(48, 30, 62), outline=PURPLE, width=1)
            text(d, cx - px(w) / 2 + px(4), cy, "赌注", 5.5, PURPLE, anchor="lm", bold=True)
            text(d, cx - px(w) / 2 + px(17), cy, "红注", 6.5, CREAM, anchor="lm", bold=True)
            text(d, cx + px(w) / 2 - px(4), cy, "小盲注不给钱 ▶", 5.5, DIM, anchor="rm")
        elif name == "list":
            y0 = cy - px(h) / 2 + px(1)
            for i, (n, de) in enumerate(decks):
                ry = y0 + px(22) * i + px(10)
                rrect(d, cx, ry, w, 20, 3, fill=SURFACE, outline=MONEY, width=1)
                text(d, cx - px(w) / 2 + px(4), ry, f"{i+1:02d}", 5.5, DIM, anchor="lm")
                draw_cardback(d, cx - px(w) / 2 + px(17), ry, 11, 15)
                text(d, cx - px(w) / 2 + px(26), ry, n, 8, CREAM, anchor="lm", bold=True)
                text(d, cx + px(w) / 2 - px(4), ry, de, 6.5, DIM, anchor="rm")
        elif name == "back":
            draw_button(d, cx, cy, w * 0.4, 20, "返回", CHIP, filled=False)


def screen_challenge(img):
    d = ImageDraw.Draw(img)
    rows, _ = layout_bands([("head", 12, 2), ("list", 94, 3), ("back", 22, 0)])
    items = [
        ("01", "煎蛋卷", "起始 $0 · 弃牌 +2", "红注"),
        ("02", "十五分钟", "出牌 -2 · 手牌 +1", "白注"),
        ("03", "富者愈富", "起始 $40 · 目标 ×1.5", "白注"),
        ("04", "刀锋边缘", "没有弃牌", "白注"),
    ]
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "head":
            text(d, cx - px(w) / 2, cy, "挑战模式", 11, PURPLE, anchor="lm", bold=True)
            text(d, cx + px(w) / 2, cy, "20 个 · 固定牌序", 6, DIM, anchor="rm")
        elif name == "list":
            y0 = cy - px(h) / 2 + px(1)
            for i, (no, n, de, st) in enumerate(items):
                ry = y0 + px(24) * i + px(11)
                rrect(d, cx, ry, w, 22, 3, fill=SURFACE, outline=PURPLE, width=1)
                text(d, cx - px(w) / 2 + px(4), ry, no, 5.5, PURPLE, anchor="lm")
                text(d, cx - px(w) / 2 + px(16), ry - px(4), n, 7.5, CREAM, anchor="lm", bold=True)
                text(d, cx - px(w) / 2 + px(16), ry + px(4), de, 6, DIM, anchor="lm")
                text(d, cx + px(w) / 2 - px(4), ry, st, 5.5, GOLD, anchor="rm")
        elif name == "back":
            draw_button(d, cx, cy, w * 0.4, 20, "返回", CHIP, filled=False)


def screen_blind(img):
    d = ImageDraw.Draw(img)
    rows, _ = layout_bands([
        ("head", 14, 3), ("b1", 24, 2), ("b2", 24, 2), ("b3", 24, 3), ("btns", 24, 0),
    ])
    opts = [("小盲注", "+$3", "目标 300", BLIND_GREEN, False),
            ("大盲注", "+$4", "目标 450", GREEN, False),
            ("Boss 盲注", "+$5", "目标 1200 · 超大盲注", BOSS_RED, True)]
    idx = 0
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "head":
            text(d, cx - px(w) / 2, cy, "第 3 关 · 红注", 9, MONEY, anchor="lm", bold=True)
            rrect(d, cx + px(w) / 2 - px(22), cy, 44, 16, 2, fill=SURFACE, outline=MONEY, width=1)
            text(d, cx + px(w) / 2 - px(22), cy, "$17", 8, MONEY)
        elif name in ("b1", "b2", "b3"):
            n, rw, tg, col, cur = opts[idx]
            idx += 1
            rrect(d, cx, cy, w, h, 3, fill=SURFACE if cur else FELT_DARK,
                  outline=MONEY if cur else col, width=3 if cur else 2)
            text(d, cx - px(w) / 2 + px(4), cy - px(6), n, 7, col, anchor="lm", bold=True)
            text(d, cx + px(w) / 2 - px(4), cy - px(6), rw, 7, MONEY, anchor="rm")
            text(d, cx - px(w) / 2 + px(4), cy + px(6), tg, 6, CREAM if cur else DIM, anchor="lm")
        elif name == "btns":
            bw = (w - 8) / 2
            draw_button(d, cx - px(bw) / 2 - px(4), cy, bw, 22, "跳过 得标签", PURPLE, filled=False)
            draw_button(d, cx + px(bw) / 2 + px(4), cy, bw, 22, "打 Boss", BOSS_RED)


def screen_playing(img):
    """主牌桌：出牌区正在结算第 3 张牌。"""
    d = ImageDraw.Draw(img)
    # 全屏 CRT 扫描线（JuiceBackdrop 常驻层）：每 3dp 一条极淡暗线
    for yy in range(0, img.height, max(1, int(px(3)))):
        d.line([0, yy, img.width, yy], fill=(22, 19, 15), width=1)
    rows, _ = layout_bands([
        ("a", 11, 2),
        ("b", 13, 2),
        ("c", 22, 2),
        ("d", 15, 1),
        ("e", 4, 2),
        ("f", 40, 8),   # 与按钮行拉开 8dp
        ("g2", 20, 0),
    ])
    # 已经打出去的 5 张牌（停在出牌区）
    played = [("K", "♠", None), ("K", "♥", "MULT"), ("9", "♦", None),
              ("7", "♣", "STEEL"), ("A", "♠", "GLASS")]
    hand = [("K", "♠", None), ("K", "♥", "MULT"), ("9", "♦", None), ("7", "♣", "STEEL"),
            ("A", "♠", "GLASS"), ("Q", "♥", None), ("3", "♦", "GOLD"), ("J", "♣", None)]
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "a":
            # 底注与盲注合并成一段（顶栏要给「退出」腾位置），下划线提示可点
            x0 = cx - px(w) / 2
            text(d, x0 + px(2), cy - px(1.5), "AIII/8·小盲", 6.5, MONEY, anchor="lm")
            d.line([x0 + px(2), cy + px(5), x0 + px(22), cy + px(5)], fill=(120, 112, 96), width=1)
            # 牌堆查看器入口：迷你牌背 + 手牌数胶囊（与 GameScreen 徽章一致）
            rrect(d, cx + px(8), cy, 24, 10, 2, fill=(36, 30, 24), outline=(92, 100, 85), width=1)
            draw_cardback(d, cx + px(1), cy, 6, 8)
            text(d, cx + px(9), cy, "8/8", 5.5, DIM)
            rrect(d, x0 + px(w) - px(30), cy, 24, 10, 2, fill=SURFACE, outline=MONEY, width=1)
            text(d, x0 + px(w) - px(30), cy, "$17", 6.5, MONEY)
            # 退出：14dp 的小方块 + ×
            rrect(d, x0 + px(w) - px(8), cy, 14, 9, 2, fill=(59, 51, 42))
            text(d, x0 + px(w) - px(8), cy, "×", 7.5, DIM)
        elif name == "b":
            total = 5 * 15 + 4 * 2 + 4 + 2 + 2 * 15 + 2
            x = cx - px(total) / 2
            rar = [CHIP, GREEN, MULT, CHIP, PURPLE]
            for i in range(5):
                # 正在触发计分的小丑单独放大（原版协同可视化 / joker pulse）
                draw_joker_badge(d, x + px(7.5), cy, 17 if i == 2 else 14, rar[i], i)
                x += px(16)
            x += px(4)
            d.rectangle([x, cy - px(6), x + 1.5, cy + px(6)], fill=FELT_HI)
            x += px(6)
            for i in range(2):
                rrect(d, x + px(7.5), cy, 14, 14, 2, fill=FELT_DARK, outline=PURPLE, width=2)
                text(d, x + px(7.5), cy, "★" if i == 0 else "◐", 7, CREAM)
                x += px(16)
        elif name == "c":
            # 出牌区：5 张牌，第 3 张正在计分（放大 + 金边）
            n = len(played)
            total = n * 28 + (n - 1) * 2
            x = cx - px(total) / 2 + px(14)
            for i, (rk, su, en) in enumerate(played):
                active = (i == 2)
                done = i < 2
                pw, ph = (30, 21) if active else (28, 20)
                py = cy - px(3) if active else cy
                draw_cardface(d, x, py, pw, ph, rk, su, enh=en, scoring=active)
                if done:
                    d.rectangle([x - px(pw) / 2, py - px(ph) / 2,
                                 x + px(pw) / 2, py + px(ph) / 2],
                                fill=(22, 34, 38))
                    # 变暗但仍能看出牌面
                    draw_cardface(d, x, py, pw, ph, rk, su, enh=en)
                    ov = Image.new("RGBA", (int(px(pw)), int(px(ph))), (16, 30, 34, 110))
                    img.paste(ov, (int(x - px(pw) / 2), int(py - px(ph) / 2)), ov)
                x += px(30)
            # 飘字改成贴在带子右端的胶囊（GainPill）：不做纵向位移，
            # 所以永远不会压到下面的筹码/倍率行
            gx = cx + px(total) / 2 + px(4)
            if w / 2 - total / 2 > 12:
                rrect(d, gx + px(9), cy, 20, 13, 3,
                      fill=(34, 28, 22), outline=MULT, width=2)
                text(d, gx + px(9), cy, "+9", 6.5, MULT, bold=True)
            # 筹码粒子：从正在计分的牌迸发、带拖尾（原版 LAYER 4）
            import math
            for k in range(7):
                a = k * 40 * math.pi / 180
                rr = px(5 + (k % 3) * 3)
                qx = cx + px(4) + math.cos(a) * rr * 1.8
                qy = cy - math.sin(a) * rr
                d.ellipse([qx - px(1), qy - px(1), qx + px(1), qy + px(1)], fill=CHIP)
                d.line([qx, qy, qx - math.cos(a) * px(3), qy + math.sin(a) * px(3)],
                       fill=(58, 96, 118), width=1)
        elif name == "d":
            # 筹码 × 倍率：两个独立盒子，互不吃字
            bw1, bw2 = w * 0.55, w * 0.33
            rrect(d, cx - px(w) / 2 + px(bw1) / 2, cy, bw1, h, 3,
                  fill=(20, 38, 46), outline=CHIP, width=2)
            text(d, cx - px(w) / 2 + px(bw1) / 2, cy, "1,420", 11, CHIP, bold=True)
            text(d, cx - px(w) / 2 + px(bw1) + px(4), cy, "×", 8, DIM)
            rrect(d, cx + px(w) / 2 - px(bw2) / 2, cy, bw2, h, 3,
                  fill=(58, 20, 20), outline=MULT, width=2)
            text(d, cx + px(w) / 2 - px(bw2) / 2, cy, "12.5", 11, MULT, bold=True)
        elif name == "e":
            barw = w - 62
            x0 = cx - px(30)
            # 先铺预估层（0 → 62%），再用当前进度（0 → 36%）盖在上面 ——
            # 与 Compose 里的叠放顺序一致：绿色在下、金色在上，
            # 金色结束之后露出来的那截绿才是"这一手还能走多远"。
            d.rounded_rectangle(
                [x0, cy - px(2), x0 + px(barw * 0.62), cy + px(2)],
                radius=px(2), fill=(90, 120, 96))
            draw_progress(d, x0, cy, barw, h, 0.36)
            # 预估够不够过：够就 ✓ 打完后总分，不够就 + 这一手的分
            text(d, cx + px(w) / 2, cy, "\u2713 3.1\u4e07", 5, GREEN, anchor="rm")
        elif name == "f":
            # 手牌：单排扇形（原作摆法），选中的抬起 5dp 并压到相邻牌之上
            cw, chh = 25.0, 31.0
            n = len(hand)
            maxw = w - 12
            reveal = min(cw * .72, (maxw - cw) / (n - 1)) if n > 1 else 0.0
            span = reveal * (n - 1)
            mid = (n - 1) / 2.0
            for i, (rk, su, en) in enumerate(hand):
                off = i - mid
                sel = i in (0, 1)
                ang = max(-13.0, min(13.0, off * 1.7))
                dip = min(4.0, off * off * (1.4 / (mid + .8)))
                ccx = cx + px(-span / 2 + i * reveal)
                ccy = cy + px(dip - (5 if sel else 0))
                paste_card(img, ccx, ccy, cw, chh, rk, su, enh=en,
                           seal="PURPLE" if i == 5 else None,
                           edition="FOIL" if i == 6 else None,
                           selected=sel, angle=ang)
        elif name == "g2":
            # 22dp 排序键 + 两个 weight 按钮，间距各 6dp（与 Compose 一致）
            sb = 22
            x0 = cx - px(w) / 2
            rrect(d, x0 + px(sb) / 2, cy, sb, 20, 3,
                  fill=(60, 43, 26), outline=AMBER, width=1)
            text(d, x0 + px(sb) / 2, cy, "点", 7, AMBER)
            bw = (w - sb - 12) / 2
            bx = x0 + px(sb) + px(6)
            draw_button(d, bx + px(bw) / 2, cy, bw, 20, "弃 2", CHIP, filled=True)
            draw_button(d, bx + px(bw) + px(6) + px(bw) / 2, cy, bw, 20, "出 3", MONEY)


def screen_exit(img):
    """局内退出确认：整张牌桌压暗 + 一颗安全按钮、一颗退出按钮。"""
    screen_playing(img)
    ov = Image.new("RGBA", (W, W), (27, 23, 19, 224))
    img.paste(ov, (0, 0), ov)
    d = ImageDraw.Draw(img)
    cx = W / 2
    cy = W / 2
    # 面板：168 × 84，实色底 + 1dp 边
    rrect(d, cx, cy, 150, 84, 6, fill=INK800, outline=INK700, width=2)
    text(d, cx, cy - px(22), "退出本局？", 13, CREAM, bold=True)
    text(d, cx, cy - px(6), "进度会保留，可从主菜单继续", 7, DIM)
    bw, bh, gap = 63.5, 22, 7
    draw_button(d, cx - px(bw + gap) / 2, cy + px(18), bw, bh, "继续玩", CHIP)
    draw_button(d, cx + px(bw + gap) / 2, cy + px(18), bw, bh, "退出", AMBER, filled=False)


def screen_cash(img):
    d = ImageDraw.Draw(img)
    rows, _ = layout_bands([
        ("t", 18, 1), ("s", 9, 3), ("score", 20, 4),
        ("ledger", 62, 5), ("btn", 26, 0),
    ])
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "t":
            text(d, cx, cy, "BOSS 击破", 12, BOSS_RED, bold=True)
        elif name == "s":
            text(d, cx, cy, "第 3 关 · Boss 盲注", 6, DIM)
        elif name == "score":
            text(d, cx - px(16), cy, "1.4万", 13, CHIP, bold=True)
            text(d, cx + px(20), cy, "/1.2万", 7, DIM, anchor="lm")
        elif name == "ledger":
            bw = w * 0.72
            lines = [("击败奖励", "+$5"), ("剩余出牌 2×", "+$2"), ("利息", "+$3"),
                     ("手巧标签", "+$2"), ("钱袋", "+$2"), ("合计", "+$14")]
            y0 = cy - px(h) / 2
            for i, (l, v) in enumerate(lines):
                ly = y0 + px(10.5) * i + px(4)
                strong = i == len(lines) - 1
                if strong:
                    d.line([cx - px(bw) / 2, ly - px(6), cx + px(bw) / 2, ly - px(6)],
                           fill=(120, 140, 145), width=1)
                text(d, cx - px(bw) / 2, ly, l, 7, CREAM if strong else DIM, anchor="lm")
                text(d, cx + px(bw) / 2, ly, v, 7, GOLD if strong else MONEY, anchor="rm")
                if not strong:
                    d.line([cx - px(bw) / 2 + px(40), ly + px(1), cx + px(bw) / 2 - px(22), ly + px(1)],
                           fill=(74, 64, 52), width=1)
        elif name == "btn":
            draw_button(d, cx, cy, w * 0.55, 24, "收下 $14", MONEY)


def screen_shop(img):
    """商店：四段同屏（商品 / 卡包 / 代金券 / 已拥有），弦长版式。"""
    d = ImageDraw.Draw(img)
    for yy in range(0, img.height, max(1, int(px(3)))):
        d.line([0, yy, img.width, yy], fill=(20, 34, 38), width=1)
    rows, _ = layout_bands([
        ("head", 12, 2),
        ("owned", 12, 2),
        ("cards", 34, 3),
        ("packs", 30, 3),
        ("vouch", 26, 2),
        ("btns", 20, 0),
    ])

    def pack_icon(cx, cy, size, body, ink):
        rrect(d, cx, cy, size, size * 1.15, 1, fill=body, outline=CARD_EDGE, width=1)
        d.rectangle([cx - px(size) / 2, cy - px(size) * .3,
                     cx + px(size) / 2, cy - px(size) * .3 + 2], fill=GOLD)
        text(d, cx, cy + px(size) * .12, ink, size * .5, CREAM)

    def tile(cx, cy, tw, th, col, price, label, icon):
        rrect(d, cx, cy, tw, th, 3, fill=SURFACE, outline=col, width=2)
        # 价签贴在顶上（原版 price tag 在卡片上方）
        rrect(d, cx - px(tw) / 2 + px(11), cy - px(th) / 2 + px(6), 19, 9, 2, fill=MONEY)
        text(d, cx - px(tw) / 2 + px(11), cy - px(th) / 2 + px(6), price, 5.5, FELT_DARK)
        icon(cx - px(tw) / 2 + px(11), cy + px(6))
        text(d, cx - px(tw) / 2 + px(24), cy + px(6), label, 6.8, CREAM, anchor="lm", bold=True)

    def label_chip(cx, cy, th, glyph):
        rrect(d, cx, cy, 9, th, 2, fill=SURFACE, outline=FELT_HI, width=1)
        text(d, cx, cy, glyph, 5.5, DIM)

    for name, w, cy, h in rows:
        cx = W / 2
        left = cx - px(w) / 2
        if name == "head":
            text(d, left, cy, "商店", 10, MONEY, anchor="lm", bold=True)
            text(d, cx, cy, "第2关", 5.8, DIM)
            rrect(d, cx + px(w) / 2 - px(19), cy, 38, 11, 2, fill=SURFACE, outline=MONEY, width=1)
            text(d, cx + px(w) / 2 - px(19), cy, "$31", 7, MONEY)
        elif name == "owned":
            text(d, left, cy, "丑3/5", 6, MONEY, anchor="lm")
            bx = left + px(24)
            for i, col in enumerate((MULT, PURPLE, CHIP)):
                d.rectangle([bx - px(3), cy - px(3), bx + px(3), cy + px(3)],
                            fill=col, outline=CARD_EDGE, width=1)
                bx += px(8)
            bx += px(2)
            for i in range(2):
                rrect(d, bx, cy, 6, 6, 1, fill=FELT_DARK, outline=PURPLE, width=1)
                bx += px(8)
            rrect(d, cx + px(w) / 2 - px(13), cy, 26, 11, 3, fill=MONEY, outline=MONEY)
            text(d, cx + px(w) / 2 - px(13), cy, "查看", 7, FELT_DARK, anchor="mm")
        elif name == "cards":
            label_chip(left + px(5), cy, h, "商")
            tw = (w - 13) / 2
            x0 = left + px(12) + px(tw) / 2
            tile(x0, cy, tw, h, MULT, "$10", "男爵",
                 lambda ix, iy: draw_joker_badge(d, ix, iy, 16, MULT, 1))
            tile(x0 + px(tw + 3), cy, tw, h, PURPLE, "$3", "水星",
                 lambda ix, iy: pack_icon(ix, iy, 14, (16, 30, 54), "★"))
        elif name == "packs":
            label_chip(left + px(5), cy, h, "包")
            tw = (w - 13) / 2
            x0 = left + px(12) + px(tw) / 2
            tile(x0, cy, tw, h, CHIP, "$6", "巨塔罗",
                 lambda ix, iy: pack_icon(ix, iy, 15, (58, 35, 80), "★"))
            tile(x0 + px(tw + 3), cy, tw, h, CHIP, "$4", "小丑包",
                 lambda ix, iy: pack_icon(ix, iy, 15, (74, 34, 34), "丑"))
        elif name == "vouch":
            label_chip(left + px(5), cy, h, "券")
            tw = (w - 13) / 2
            x0 = left + px(12) + px(tw) / 2
            for k, (nm, pr) in enumerate((("多余库存", "$10"), ("清仓折扣", "$10"))):
                xx = x0 + k * px(tw + 3)
                rrect(d, xx, cy, tw, h, 3, fill=SURFACE, outline=GOLD, width=2)
                rrect(d, xx - px(tw) / 2 + px(11), cy - px(h) / 2 + px(5), 19, 9, 2, fill=MONEY)
                text(d, xx - px(tw) / 2 + px(11), cy - px(h) / 2 + px(5), pr, 5.5, FELT_DARK)
                d.rectangle([xx - px(tw) / 2 + px(4), cy + px(4),
                             xx - px(tw) / 2 + px(18), cy + px(10)], fill=GOLD_DIM, outline=CARD_EDGE)
                text(d, xx - px(tw) / 2 + px(24), cy + px(5), nm, 6.4, CREAM, anchor="lm", bold=True)
        elif name == "btns":
            bw = (w - 5) / 2
            draw_button(d, cx - px(bw) / 2 - px(2.5), cy, bw, 20, "重掷 $7", CHIP, filled=False)
            draw_button(d, cx + px(bw) / 2 + px(2.5), cy, bw, 20, "继续", MONEY)


def screen_pack(img):
    """开包：5 张里选了 2 张。"""
    d = ImageDraw.Draw(img)
    rows, _ = layout_bands([("head", 12, 3), ("list", 78, 3), ("btns", 22, 0)])
    items = [
        ("死神", "2 张变第一张", PURPLE, True),
        ("愚者", "随机 1 张行星", PURPLE, False),
        ("恶魔", "1 张变黄金", PURPLE, True),
        ("女祭司", "随机 2 张行星", PURPLE, False),
        ("世界", "3 张变黑桃", PURPLE, False),
    ]
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "head":
            text(d, cx - px(w) / 2, cy, "超大塔罗包", 9, MONEY, anchor="lm", bold=True)
            text(d, cx + px(w) / 2, cy, "已选 2/2", 6, GREEN, anchor="rm")
        elif name == "list":
            y0 = cy - px(h) / 2
            for i, (n, de, col, picked) in enumerate(items):
                ry = y0 + px(13) + px(26) * i
                if ry + px(4) > y0 + px(h):
                    break
                rrect(d, cx, ry, w, 24, 3,
                      fill=SURFACE_HI if picked else SURFACE,
                      outline=MONEY if picked else col, width=3 if picked else 2)
                ix = cx - px(w) / 2 + px(12)
                d.rectangle([ix - px(8), ry - px(8), ix + px(8), ry + px(8)], fill=(42, 27, 56))
                d.rectangle([ix - px(6), ry - px(6), ix + px(6), ry + px(6)], outline=(136, 103, 165), width=1)
                text(d, ix, ry, "★", 7, CREAM)
                text(d, cx - px(w) / 2 + px(24), ry - px(4), n, 8, CREAM, anchor="lm", bold=True)
                text(d, cx - px(w) / 2 + px(24), ry + px(4), de, 5.5, DIM, anchor="lm")
                if picked:
                    text(d, cx + px(w) / 2 - px(8), ry, "✓", 9, MONEY, anchor="rm")
        elif name == "btns":
            bw = (w - 6) / 2
            draw_button(d, cx - px(bw) / 2 - px(3), cy, bw, 20, "跳过", CHIP, filled=False)
            draw_button(d, cx + px(bw) / 2 + px(3), cy, bw, 20, "拿走 2", MONEY)


def screen_detail(img):
    """详情页：已拥有小丑，卖出按钮处于「再点确认」状态。"""
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W, W], fill=INK900)
    rows, _ = layout_bands([
        ("icon", 42, 2), ("t", 12, 1), ("s", 8, 3),
        ("body", 44, 4), ("btn", 26, 2), ("hint", 8, 0),
    ])
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "icon":
            rrect(d, cx, cy, 38, 38, 4, fill=SURFACE, outline=MULT, width=3)
            draw_joker_badge(d, cx, cy, 30, MULT, 2)
        elif name == "t":
            text(d, cx, cy, "男爵", 11, CREAM, bold=True)
        elif name == "s":
            text(d, cx, cy, "稀有 · 卖 $4", 6.5, MULT)
        elif name == "body":
            text(d, cx, cy - px(8), "手中每张 K ×1.5 倍率", 7.5, CREAM)
            text(d, cx, cy + px(3), "与「钢铁」「K 牌组」是核心乘算组合", 7.5, CREAM)
        elif name == "btn":
            draw_button(d, cx, cy, w * 0.52, 24, "再点确认卖出", MULT, filled=True)
        elif name == "hint":
            # 有小丑台词时，底部那行显示 flavor 而不是操作提示
            text(d, cx, cy, "留一张国王，加一次倍率。", 5.5, GOLD)


def screen_end(img):
    d = ImageDraw.Draw(img)
    rows, _ = layout_bands([
        ("back", 22, 2), ("t", 20, 2), ("s", 9, 4),
        ("ledger", 34, 5), ("ledger2", 24, 3), ("btns", 24, 0),
    ])
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "back":
            draw_cardback(d, cx, cy, 18, 24, base=(120, 50, 50))
        elif name == "t":
            text(d, cx + 1, cy + 1, "止 步", 17, FELT_DARK, bold=True)
            text(d, cx, cy, "止 步", 17, BOSS_RED, bold=True)
        elif name == "s":
            text(d, cx, cy, "第 5 关 · Boss 盲注", 6, DIM)
        elif name == "ledger":
            bw = w * 0.62
            lines = [("最高单手", "8.2万", CHIP), ("已过盲注", "13", GREEN)]
            y0 = cy - px(h) / 2
            for i, (l, v, c) in enumerate(lines):
                ly = y0 + px(10) * i + px(4)
                text(d, cx - px(bw) / 2, ly, l, 7, DIM, anchor="lm")
                text(d, cx + px(bw) / 2, ly, v, 7, c, anchor="rm")
        elif name == "ledger2":
            bw = w * 0.62
            text(d, cx - px(bw) / 2, cy, "本关差", 7, DIM, anchor="lm")
            text(d, cx + px(bw) / 2, cy, "3.1万", 7, MULT, anchor="rm")
        elif name == "btns":
            bw = (w - 8) / 2
            draw_button(d, cx - px(bw) / 2 - px(4), cy, bw, 22, "主菜单", CHIP, filled=False)
            draw_button(d, cx + px(bw) / 2 + px(4), cy, bw, 22, "再来一局", SUIT_RED)


def screen_levels(img):
    """信息面板 · 等级页。"""
    d = ImageDraw.Draw(img)
    rows, _ = layout_bands([
        ("tabs", 14, 3), ("list", 110, 3), ("hint", 8, 0),
    ])
    types = [("同花五条", "Lv1", "160×16"), ("同花葫芦", "Lv1", "140×14"),
             ("五条", "Lv1", "120×12"), ("同花顺", "Lv2", "140×12"),
             ("四条", "Lv3", "120×13"), ("葫芦", "Lv2", "65×6"),
             ("同花", "Lv4", "80×8"), ("顺子", "Lv2", "60×7"),
             ("三条", "Lv1", "30×3")]
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "tabs":
            for i, lb in enumerate(["小丑", "消耗", "等级", "券"]):
                tw = (w - 3 * 2) / 4
                tx = cx - px(w) / 2 + px(tw) / 2 + px(tw + 2) * i
                on = i == 2
                rrect(d, tx, cy, tw, 14, 2, fill=MONEY if on else SURFACE)
                text(d, tx, cy, lb, 6, FELT_DARK if on else DIM)
        elif name == "list":
            y0 = cy - px(h) / 2
            text(d, cx - px(w) / 2, y0 + px(4), "牌型等级", 7, CHIP, anchor="lm")
            for i, (n, lv, v) in enumerate(types):
                ry = y0 + px(14) + px(13) * i
                if ry + px(6) > y0 + px(h):
                    break
                up = lv != "Lv1"
                rrect(d, cx, ry, w, 12, 1, fill=SURFACE if up else FELT_DARK)
                text(d, cx - px(w) / 2 + px(3), ry, n, 6, CREAM if up else DIM, anchor="lm")
                text(d, cx - px(w) / 2 + px(40), ry, lv, 6, MONEY if up else FELT_HI, anchor="lm")
                text(d, cx + px(w) / 2 - px(3), ry, v, 6, CHIP, anchor="rm")
        elif name == "hint":
            text(d, cx, cy, "点任意处关闭", 6, DIM)


def screen_collection(img):
    """图鉴：小丑分页，4 列网格，已解锁 8/12，选中第 3 格。"""
    d = ImageDraw.Draw(img)
    rows, _ = layout_bands([
        ("head", 12, 2), ("tabs", 14, 2), ("grid", 98, 2),
        ("info", 28, 2), ("back", 20, 0),
    ])
    tabs = ["小丑", "消耗品", "代金券", "标签", "牌组"]
    # (名字, 颜色, 是否解锁)
    cells = [
        ("小丑", SUIT_RED, True), ("半个", MULT, True), ("印错", CHIP, True),
        ("抽象", MONEY, True), ("旗帜", GREEN, True), ("半身", MULT, True),
        ("魔鬼", BOSS_RED, True), ("骑士", CHIP, True), ("??", DIM, False),
        ("??", DIM, False), ("??", DIM, False), ("??", DIM, False),
    ]
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "head":
            text(d, cx - px(w) / 2, cy, "图鉴", 11, MONEY, anchor="lm", bold=True)
            text(d, cx + px(w) / 2, cy, "8/150", 6.5, DIM, anchor="rm")
        elif name == "tabs":
            tw = w / 5
            for i, t in enumerate(tabs):
                tx = cx - px(w) / 2 + px(tw) * i + px(tw) / 2
                on = i == 0
                rrect(d, tx, cy, tw - px(1), 12, 3,
                      fill=MONEY if on else SURFACE,
                      outline=MONEY if on else (56, 48, 39), width=1)
                text(d, tx, cy, t, 6, FELT_DARK if on else DIM, bold=on)
        elif name == "grid":
            y0 = cy - px(h) / 2
            cw2, ch2 = w / 4, 32
            for i, (n, col, un) in enumerate(cells):
                col_i, row_i = i % 4, i // 4
                gx = cx - px(w) / 2 + px(cw2) * col_i + px(cw2) / 2
                gy = y0 + px(ch2) * row_i + px(ch2) / 2
                sel = i == 2
                rrect(d, gx, gy, cw2 - 1.5, 30, 3,
                      fill=SURFACE_HI if sel else SURFACE,
                      outline=MONEY if sel else ((56, 48, 39) if un else (38, 33, 27)),
                      width=2 if sel else 1)
                if un:
                    draw_joker_badge(d, gx, gy - px(2.5), 15, col, 2)
                else:
                    text(d, gx, gy - px(2), "?", 11, DIM)
                text(d, gx, gy + px(9.5), n, 5, CREAM if un else DIM)
        elif name == "info":
            text(d, cx, cy - px(5), "印错  普通", 7.5, CREAM, bold=True)
            text(d, cx, cy + px(4), "随机 +0~23 倍率（本作固定 +12）", 6, CREAM)
        elif name == "back":
            draw_button(d, cx, cy, w * 0.4, 19, "返回", CHIP, filled=False)


def screen_settings(img):
    """设置：四行开关，全部即时生效。"""
    d = ImageDraw.Draw(img)
    rows, _ = layout_bands([("head", 12, 3), ("list", 100, 3), ("back", 18, 0)])
    items = [
        ("音乐", "程序化爵士", "小", GOLD),
        ("音效", "合成音", "中", MONEY),
        ("震屏", "触觉层", "开", CHIP),
        ("扫描线", "CRT 强度", "中", GREEN),
        ("节奏", "结算动画", "标准", MULT),
    ]
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "head":
            text(d, cx - px(w) / 2, cy, "设置", 11, MONEY, anchor="lm", bold=True)
            text(d, cx + px(w) / 2, cy, "即时生效", 6, DIM, anchor="rm")
        elif name == "list":
            y0 = cy - px(h) / 2 + px(1)
            for i, (l, hint, val, col) in enumerate(items):
                ry = y0 + px(10) + px(20) * i
                rrect(d, cx, ry, w, 18, 3, fill=SURFACE, outline=col, width=1)
                text(d, cx - px(w) / 2 + px(5), ry, l, 7.5, CREAM, anchor="lm", bold=True)
                vx = cx + px(w) / 2 - px(13)
                text(d, vx - px(11), ry, hint, 5.2, DIM, anchor="rm")
                rrect(d, vx, ry, 20, 12, 2, fill=(44, 36, 28), outline=col, width=1)
                text(d, vx, ry, val, 6.5, col)
        elif name == "back":
            draw_button(d, cx, cy, w * 0.4, 18, "返回", CHIP, filled=False)


def screen_deck(img):
    """牌堆查看：4 列小牌，待抽 / 弃牌两页，右侧排序按钮。"""
    d = ImageDraw.Draw(img)
    rows, _ = layout_bands([
        ("head", 12, 2), ("tabs", 13, 2), ("grid", 84, 2), ("back", 20, 0),
    ])
    cards = [
        ("A", "\u2660"), ("K", "\u2665"), ("7", "\u2666"), ("Q", "\u2663"),
        ("3", "\u2660"), ("9", "\u2665"), ("J", "\u2666"), ("5", "\u2663"),
        ("2", "\u2665"), ("10", "\u2660"), ("6", "\u2666"), ("K", "\u2663"),
        ("8", "\u2665"), ("4", "\u2660"), ("Q", "\u2666"), ("A", "\u2663"),
    ]
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "head":
            text(d, cx - px(w) / 2, cy, "牌堆", 11, MONEY, anchor="lm", bold=True)
            text(d, cx + px(w) / 2, cy, "32 待抽", 6, DIM, anchor="rm")
        elif name == "tabs":
            tw = (w - 46) / 2
            for i, t in enumerate(["待抽", "弃牌"]):
                tx = cx - px(w) / 2 + px(tw) * i + px(tw) / 2
                on = i == 0
                rrect(d, tx, cy, tw, 12, 3,
                      fill=CHIP if on else SURFACE,
                      outline=CHIP if on else (56, 48, 39), width=1)
                text(d, tx, cy, t, 6.5, INK900 if on else DIM, bold=on)
            rrect(d, cx + px(w) / 2 - px(20), cy, 40, 12, 3,
                  fill=(60, 43, 26), outline=AMBER, width=1)
            text(d, cx + px(w) / 2 - px(20), cy, "按点数", 5.6, AMBER)
        elif name == "grid":
            y0 = cy - px(h) / 2
            cw2, ch2 = w / 4, 22
            for i, (r, su) in enumerate(cards):
                col_i, row_i = i % 4, i // 4
                gx = cx - px(w) / 2 + px(cw2) * col_i + px(cw2) / 2
                gy = y0 + px(ch2) * row_i + px(ch2) / 2
                draw_cardface(d, gx, gy, cw2 - 1.5, 19, r, su)
        elif name == "back":
            draw_button(d, cx, cy, w * 0.34, 18, "返回", CHIP, filled=False)


def screen_runinfo(img):
    """牌型等级总览：12 种牌型排 6 行 2 列，出过的加亮。"""
    d = ImageDraw.Draw(img)
    rows, _ = layout_bands([
        ("head", 12, 2), ("list", 84, 2), ("hint", 13, 2), ("back", 20, 0),
    ])
    # (名称, 等级, 筹码×倍率, 本局是否出过)
    types = [
        ("高牌", 1, "5×1", False), ("对子", 3, "40×4", True),
        ("两对", 2, "40×4", True), ("三条", 1, "30×3", False),
        ("顺子", 1, "30×4", False), ("同花", 2, "50×6", True),
        ("葫芦", 1, "40×4", False), ("四条", 1, "60×7", False),
        ("同花顺", 1, "100×8", False), ("五条", 1, "120×12", False),
        ("同花葫芦", 1, "140×14", False), ("同花五条", 1, "160×16", False),
    ]
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "head":
            text(d, cx - px(w) / 2, cy, "牌型", 11, MONEY, anchor="lm", bold=True)
            text(d, cx + px(w) / 2, cy, "+6 级", 6, GREEN, anchor="rm")
        elif name == "list":
            y0 = cy - px(h) / 2
            cw2, ch2 = w / 2, h / 6
            for i, (n, lv, val, used) in enumerate(types):
                col_i, row_i = i % 2, i // 2
                gx = cx - px(w) / 2 + px(cw2) * col_i + px(cw2) / 2
                gy = y0 + px(ch2) * row_i + px(ch2) / 2
                rrect(d, gx, gy, cw2 - 3.2, ch2 - 2, 2,
                      fill=SURFACE_HI if used else (38, 33, 27),
                      outline=AMBER if used else (48, 41, 34), width=1)
                text(d, gx - px(cw2) / 2 + px(3), gy, f"{n} {lv}", 5.4,
                     CREAM if used else DIM, anchor="lm")
                text(d, gx + px(cw2) / 2 - px(3.4), gy, val, 4.8,
                     CHIP if used else (100, 94, 82), anchor="rm")
        elif name == "hint":
            text(d, cx, cy, "行星牌升级对应牌型 · 亮色是本局出过的", 5.5, DIM)
        elif name == "back":
            draw_button(d, cx, cy, w * 0.34, 18, "返回", CHIP, filled=False)


def screen_career(img):
    """生涯统计：3 × 4 十二格。"""
    d = ImageDraw.Draw(img)
    rows, _ = layout_bands([("head", 11, 2), ("grid", 74, 2), ("back", 20, 0)])
    cells = [
        ("7", "\u901a\u5173", GREEN), ("8", "\u6700\u9ad8\u5e95\u6ce8", MONEY), ("3", "\u6700\u957f\u8fde\u80dc", GOLD),
        ("248K", "\u5355\u5c40\u6700\u9ad8", CREAM), ("12,480", "\u5355\u624b\u6307\u6700\u9ad8", CHIP), ("$52", "\u6700\u9ad8\u6301\u5e01", MONEY),
        ("2,140", "\u7d2f\u8ba1\u51fa\u724c", DIM), ("486", "\u7d2f\u8ba1\u5f03\u724c", DIM), ("63", "\u89c1\u8fc7\u5c0f\u4e11", PURPLE),
        ("35%", "\u901a\u5173\u7387", GREEN), ("306", "\u573a\u5747\u51fa\u724c", DIM), ("69", "\u573a\u5747\u5f03\u724c", DIM),
    ]
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "head":
            text(d, cx - px(w) / 2, cy, "\u751f\u6daf", 11, MONEY, anchor="lm", bold=True)
            text(d, cx + px(w) / 2, cy, "20 \u5c40", 6.5, DIM, anchor="rm")
        elif name == "grid":
            for i, (v, l, col) in enumerate(cells):
                gx = cx + px(w) * ((i % 3) - 1) / 3
                gy = cy - px(h) / 2 + px(9) + px(18) * (i // 3)
                # 数字偏上、标签偏下：9sp 与 5.5sp 两个字形挤在 18dp 里，
                # 中心对中心必然压字，所以各自让开 2dp。
                text(d, gx, gy - px(2), v, 9, col, bold=True)
                text(d, gx, gy + px(5), l, 5.5, DIM)
        elif name == "back":
            draw_button(d, cx, cy, w * 0.5, 20, "\u8fd4\u56de", CHIP, filled=False)


def screen_ach(img):
    """\u6210\u5c31\uff1a20 \u6761\u5217\u8868\uff0c\u5df2\u8fbe\u6210\u7684\u4eae\u8272\u3002"""
    d = ImageDraw.Draw(img)
    rows, _ = layout_bands([("head", 11, 2), ("list", 76, 2), ("back", 20, 0)])
    items = [
        ("\u5750\u4e0b\u6765", "\u6253\u5b8c\u7b2c\u4e00\u5c40", True, GOLD),
        ("\u5f00\u5f20", "\u901a\u5173\u4e00\u6b21", True, CREAM),
        ("\u8fc7\u534a", "\u6253\u5230\u7b2c 5 \u5173", True, GOLD),
        ("\u4e07", "\u5355\u624b\u6307 10,000 \u5206", True, GOLD),
        ("\u5341\u4e07", "\u5355\u624b\u6307 100,000 \u5206", False, CREAM),
        ("\u767e\u4e07", "\u5355\u624b\u6307 1,000,000 \u5206", False, MONEY),
        ("\u901a\u5173", "\u6253\u5230\u7b2c 8 \u5173", False, MONEY),
    ]
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "head":
            text(d, cx - px(w) / 2, cy, "\u6210\u5c31", 11, MONEY, anchor="lm", bold=True)
            text(d, cx + px(w) / 2, cy, "4/20", 6.5, MONEY, anchor="rm")
        elif name == "list":
            y0 = cy - px(h) / 2 + px(1)
            # 视口 76dp 只放得下 4 项半；真实界面可滚动，预览只画放得下的部分
            for i, (t, desc, got, col) in enumerate(items[:4]):
                ry = y0 + px(8) + px(17) * i
                rrect(d, cx - px(w) / 2 + px(2.5), ry, 5, 5, 1,
                      fill=col if got else (58, 51, 42))
                text(d, cx - px(w) / 2 + px(8), ry - px(3), t, 7.5,
                     CREAM if got else DIM, anchor="lm", bold=got)
                text(d, cx - px(w) / 2 + px(8), ry + px(4), desc, 5.5,
                     DIM if got else (120, 112, 96), anchor="lm")
        elif name == "back":
            draw_button(d, cx, cy, w * 0.5, 20, "\u8fd4\u56de", CHIP, filled=False)


def screen_seed(img):
    """\u79cd\u5b50\u5c40\uff1a\u516d\u4f4d\u6570\u5b57\uff0c\u5f53\u524d\u4f4d\u9ad8\u4eae\u3002"""
    d = ImageDraw.Draw(img)
    rows, _ = layout_bands([("head", 11, 2), ("digits", 30, 2), ("hint", 14, 2), ("btns", 20, 0)])
    for name, w, cy, h in rows:
        cx = W / 2
        if name == "head":
            text(d, cx - px(w) / 2, cy, "\u79cd\u5b50\u5c40", 11, MONEY, anchor="lm", bold=True)
            text(d, cx + px(w) / 2, cy, "\u6bcf\u65e5 20261121", 6, DIM, anchor="rm")
        elif name == "digits":
            digs = "202611"
            for i, ch in enumerate(digs):
                gx = cx + px(22 + 3) * (i - 2.5)
                on = i == 5
                rrect(d, gx, cy, 22, 28, 3,
                      fill=(58, 51, 42) if on else SURFACE,
                      outline=(224, 163, 92) if on else (59, 51, 42),
                      width=2 if on else 1)
                text(d, gx, cy, ch, 15, CREAM, bold=True)
        elif name == "hint":
            text(d, cx, cy, "\u8f6c\u8868\u51a0\u6539\u4f4d \u00b7 \u70b9\u6570\u5b57\u52a0\u4e00", 6, DIM)
        elif name == "btns":
            # 152 宽三等分：60 + 5 + 42 + 5 + 40
            draw_button(d, cx - px(46), cy, 60, 20, "\u5f00\u5c40", MONEY)
            draw_button(d, cx + px(10), cy, 42, 20, "\u6bcf\u65e5", CHIP, filled=False)
            draw_button(d, cx + px(56), cy, 40, 20, "\u8fd4\u56de", DIM, filled=False)



SCREENS = [
    ("menu", "开始界面", screen_menu),
    ("deck", "牌组选择（弦长+赌注）", screen_deck),
    ("challenge", "挑战·20 个预设", screen_challenge),
    ("blind", "盲注选择", screen_blind),
    ("playing", "牌桌·弦长版式+扇形手牌", screen_playing),
    ("cash", "结算页·弦长版式", screen_cash),
    ("shop", "商店·四段同屏（含卡包/双券）", screen_shop),
    ("pack", "开包·弦长版式", screen_pack),
    ("detail", "详情·卖出确认（弦长）", screen_detail),
    ("end", "结束页·弦长版式", screen_end),
    ("levels", "面板·等级", screen_levels),
    ("deckview", "牌堆查看·待抽/弃牌/排序", screen_deck),
    ("runinfo", "牌型等级·Run Info", screen_runinfo),
    ("collection", "图鉴·5 类分页", screen_collection),
    ("settings", "设置·五项即时生效", screen_settings),
    ("career", "生涯·十二格统计", screen_career),
    ("ach", "成就·20 条", screen_ach),
    ("seed", "种子·六位可调", screen_seed),
]


def render(fn, out):
    img = Image.new("RGB", (W, W), FELT_DARK)
    draw_felt(img)
    fn(img)
    mask = fill_circle_mask()
    out_img = Image.new("RGB", (W, W), (12, 10, 8))
    out_img.paste(img, (0, 0), mask)
    out_img.save(out)
    return out_img


def main():
    os.makedirs("preview", exist_ok=True)
    tiles = []
    for key, label, fn in SCREENS:
        p = f"preview/screen-{key}.png"
        im = render(fn, p)
        tiles.append((label, im))
        print("wrote", p)

    # 对照图：4 列，行数按实际张数算（13 张 → 4 行，否则第 13 张会被画到画布外）
    cols = 4
    rows = (len(tiles) + cols - 1) // cols
    pad = 16
    tw = W + pad
    canvas = Image.new("RGB", (tw * cols + pad, (W + 26 + pad) * rows + pad), (16, 13, 10))
    d = ImageDraw.Draw(canvas)
    f = font(13, True)
    for i, (label, im) in enumerate(tiles):
        c = i % cols
        r = i // cols
        x = pad + c * tw
        y = pad + r * (W + 26 + pad)
        canvas.paste(im, (x, y))
        d.text((x + W / 2, y + W + 12), label, font=f, fill=(200, 210, 214), anchor="mm")
    canvas.save("preview/screens-all.png")
    print("wrote preview/screens-all.png", canvas.size)

    # 退出确认层单独出一张：它盖在整张牌桌上，不属于弦长版式的 15 个界面
    render(screen_exit, "preview/screen-exit.png")
    print("wrote preview/screen-exit.png")


if __name__ == "__main__":
    main()
