import os
import zipfile
from PIL import Image, ImageDraw

WIDTH = 1024
HEIGHT = 600
FPS = 30

PART0_FRAMES = 60
PART1_FRAMES = 30

BG_COLOR = (0, 0, 0, 255)
C_LIGHT_BLUE = (129, 196, 255, 255)
C_DARK_BLUE = (0, 102, 177, 255)
C_RED = (231, 34, 46, 255)
C_WHITE = (242, 244, 247, 255)


def draw_badge_frame(progress: float) -> Image.Image:
    img = Image.new("RGBA", (WIDTH, HEIGHT), BG_COLOR)
    badge = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    draw = ImageDraw.Draw(badge)

    badge_h = int(HEIGHT * 0.24)
    badge_w = int(badge_h * (240.0 / 96.0))
    ox = (WIDTH - badge_w) // 2
    oy = (HEIGHT - badge_h) // 2

    def pt(vx: float, vy: float):
        return (ox + (vx / 240.0) * badge_w, oy + (vy / 96.0) * badge_h)

    draw.polygon([pt(24, 84), pt(54, 12), pt(78, 12), pt(48, 84)], fill=C_LIGHT_BLUE)
    draw.polygon([pt(54, 84), pt(84, 12), pt(108, 12), pt(78, 84)], fill=C_DARK_BLUE)
    draw.polygon([pt(84, 84), pt(114, 12), pt(138, 12), pt(108, 84)], fill=C_RED)
    m_points = [
        pt(114, 84), pt(144, 12), pt(164, 12), pt(170, 50),
        pt(198, 12), pt(218, 12), pt(188, 84), pt(170, 84),
        pt(190, 36), pt(168, 68), pt(158, 68), pt(154, 36),
        pt(132, 84)
    ]
    draw.polygon(m_points, fill=C_WHITE)

    mask = Image.new("L", (WIDTH, HEIGHT), 0)
    mask_draw = ImageDraw.Draw(mask)
    slant = badge_h * (30.0 / 72.0)
    span = badge_w + slant
    cur_top_x = ox + span * max(0.0, min(1.0, progress))

    mask_draw.polygon(
        [
            (ox - slant, oy),
            (cur_top_x, oy),
            (cur_top_x - slant, oy + badge_h),
            (ox - slant, oy + badge_h),
        ],
        fill=255,
    )

    img.paste(badge, (0, 0), mask)
    return img.convert("RGB")


def build_bootanimation():
    os.makedirs("build_boot/part0", exist_ok=True)
    os.makedirs("build_boot/part1", exist_ok=True)

    for i in range(PART0_FRAMES):
        t = (i + 1) / float(PART0_FRAMES)
        eased = 1.0 - (1.0 - t) ** 2
        frame = draw_badge_frame(eased)
        frame.save(f"build_boot/part0/{i:04d}.png", "PNG", optimize=True)

    full_frame = draw_badge_frame(1.0)
    for i in range(PART1_FRAMES):
        full_frame.save(f"build_boot/part1/{i:04d}.png", "PNG", optimize=True)

    desc_content = f"{WIDTH} {HEIGHT} {FPS}\np 1 0 part0\np 0 0 part1\n\n"
    with open("build_boot/desc.txt", "wb") as f:
        f.write(desc_content.encode("ascii"))

    with zipfile.ZipFile("bootanimation.zip", "w", compression=zipfile.ZIP_STORED) as zf:
        zf.write("build_boot/desc.txt", arcname="desc.txt")
        for part, count in [("part0", PART0_FRAMES), ("part1", PART1_FRAMES)]:
            for i in range(count):
                filename = f"{i:04d}.png"
                zf.write(f"build_boot/{part}/{filename}", arcname=f"{part}/{filename}")

    print(f"Created bootanimation.zip ({WIDTH}x{HEIGHT} @ {FPS}fps, ZIP_STORED).")


if __name__ == "__main__":
    build_bootanimation()
