#!/usr/bin/env python3.11
"""手表卡片（Tile）预览图。

输出三张 192dp 见方的图：

* `preview/tile-run.png`   —— 牌桌态（有进度条）
* `preview/tile-empty.png` —— 没有进行中的一局
* `app/src/main/res/drawable-nodpi/tile_preview.png` —— 系统「添加卡片」
  列表里的缩略图，内容与 tile-run 相同，但铺满方形（系统自己圆裁）

排版值与 `JokerTileService` 里逐项对齐（字号、间距、色值），
改卡片样式时两份要一起改 —— 否则玩家在"添加卡片"里看到的预览
和真正加上去的样子对不上。
"""
import os

from PIL import Image, ImageDraw, ImageFont

S = 2
W = 192 * S
CX = CY = W / 2

FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
FONT_B = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"

INK900 = (27, 23, 19)
SLOT = (44, 38, 32)
CREAM = (243, 238, 225)
DIM = (156, 146, 127)
CHIP = (94, 155, 176)
BOSS_RED = (168, 71, 58)
PANEL = (49, 42, 35)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def font(dp, bold=False):
    return ImageFont.truetype(FONT_B if bold else FONT, max(6, int(dp * S)))


def tx(d, s, y_dp, size, color, bold=False, tracking=0.0):
    """居中画一行字。tracking 是 em 字距，PIL 没有原生支持，按字符补空格近似。"""
    f = font(size, bold)
    if tracking > 0:
        s = (" " * max(0, round(tracking * size / 4))).join(list(s))
    d.text((CX, CY + y_dp * S), s, font=f, fill=color, anchor="mm")


def card(run: bool) -> Image.Image:
    img = Image.new("RGB", (W, W), INK900)
    d = ImageDraw.Draw(img)

    if run:
        # 顶部压印短线：1999 的印刷母题，顺便压住上面那块空
        d.rectangle([CX - 9 * S, CY - 59 * S, CX + 9 * S, CY - 58 * S], fill=(59, 51, 42))
        tx(d, "A III \u00b7 \u5927\u76f2", -46.5, 10, DIM, tracking=0.08)

        # 圆角面板：152 × 58，半径 10，底色比卡面亮一档
        px0, py0 = CX - 76 * S, CY - 35.5 * S
        px1, py1 = CX + 76 * S, CY + 22.5 * S
        d.rounded_rectangle([px0, py0, px1, py1], radius=10 * S, fill=PANEL)
        tx(d, "12,480", -12, 25, CREAM, bold=True)
        tx(d, "/ 1,800", 12, 10, DIM)

        # 进度条：120 × 4，比例 0.62；右侧百分比
        by = CY + 35.5 * S
        bx0 = CX - 76 * S
        d.rounded_rectangle([bx0, by - 2 * S, bx0 + 120 * S, by + 2 * S], radius=2 * S, fill=SLOT)
        d.rounded_rectangle([bx0, by - 2 * S, bx0 + 74 * S, by + 2 * S], radius=2 * S, fill=CHIP)
        d.text((CX + 58 * S, by), "62%", font=font(9), fill=CHIP, anchor="mm")

        tx(d, "$12 \u00b7 \u624b 3 \u00b7 \u5f03 2", 52, 9, DIM)
    else:
        d.rectangle([CX - 9 * S, CY - 59 * S, CX + 9 * S, CY - 58 * S], fill=(59, 51, 42))
        tx(d, "\u8155\u4e0a\u5c0f\u4e11", -46.5, 10, DIM, tracking=0.08)
        # 空态也用面板，只是里面只有一句提示 —— 保证"加了卡片"与"没加"两种
        # 情况在列表里的缩略图是同一个视觉体系。
        d.rounded_rectangle(
            [CX - 76 * S, CY - 30 * S, CX + 76 * S, CY + 20 * S], radius=10 * S, fill=PANEL
        )
        tx(d, "\u8f7b\u89e6\u5f00\u5c40", -12, 22, CREAM, bold=True)
        tx(d, "\u6ca1\u6709\u8fdb\u884c\u4e2d\u7684\u4e00\u5c40", 12, 10, DIM)

    return img


def rounded(img: Image.Image) -> Image.Image:
    """文档图用：圆裁 + 透明四角，模拟手表上的真实形状。"""
    mask = Image.new("L", (W, W), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, W - 1, W - 1], fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def main():
    prev = os.path.join(ROOT, "preview")
    os.makedirs(prev, exist_ok=True)

    run = card(True)
    empty = card(False)
    rounded(run).save(os.path.join(prev, "tile-run.png"))
    rounded(empty).save(os.path.join(prev, "tile-empty.png"))

    res = os.path.join(ROOT, "app/src/main/res/drawable-nodpi")
    os.makedirs(res, exist_ok=True)
    # 缩略图铺满方形：系统会自己裁成圆形，留透明角反而会露出底色
    run.save(os.path.join(res, "tile_preview.png"))
    print("wrote preview/tile-run.png, preview/tile-empty.png, res/drawable-nodpi/tile_preview.png")


if __name__ == "__main__":
    main()
