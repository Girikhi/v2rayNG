from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "V2rayNG" / "app" / "src" / "main" / "res"
MASTER = Path(__file__).with_name("v2rayng-auto-icon-v1-master.png")
BACKGROUND = "#F5F7F8"
RESAMPLE = Image.Resampling.LANCZOS

LEGACY_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

FOREGROUND_SIZES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}


def cropped_master() -> Image.Image:
    image = Image.open(MASTER).convert("RGBA")
    bounds = image.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError("Icon master has no visible pixels")
    return image.crop(bounds)


def fitted_symbol(source: Image.Image, size: tuple[int, int], fill: float) -> Image.Image:
    canvas = Image.new("RGBA", size, (0, 0, 0, 0))
    symbol = source.copy()
    max_size = (round(size[0] * fill), round(size[1] * fill))
    symbol.thumbnail(max_size, RESAMPLE)
    offset = ((size[0] - symbol.width) // 2, (size[1] - symbol.height) // 2)
    canvas.alpha_composite(symbol, offset)
    return canvas


def legacy_icon(source: Image.Image, size: int, round_icon: bool) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    inset = max(1, round(size * 0.035))
    bounds = (inset, inset, size - inset - 1, size - inset - 1)
    if round_icon:
        draw.ellipse(bounds, fill=BACKGROUND)
    else:
        draw.rounded_rectangle(bounds, radius=round(size * 0.22), fill=BACKGROUND)
    canvas.alpha_composite(fitted_symbol(source, (size, size), 0.76))
    return canvas


def banner_images(source: Image.Image) -> tuple[Image.Image, Image.Image]:
    size = (320, 180)
    foreground = fitted_symbol(source, size, 0.64)
    legacy = Image.new("RGBA", size, BACKGROUND)
    legacy.alpha_composite(foreground)
    return legacy, foreground


def save(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True)


def main() -> None:
    source = cropped_master()
    for density, legacy_size in LEGACY_SIZES.items():
        directory = RES / f"mipmap-{density}"
        save(legacy_icon(source, legacy_size, False), directory / "ic_launcher.png")
        save(legacy_icon(source, legacy_size, True), directory / "ic_launcher_round.png")

    for density, foreground_size in FOREGROUND_SIZES.items():
        foreground = fitted_symbol(source, (foreground_size, foreground_size), 0.62)
        save(foreground, RES / f"mipmap-{density}" / "ic_launcher_foreground.png")

    banner, banner_foreground = banner_images(source)
    save(banner, RES / "mipmap-xhdpi" / "ic_banner.png")
    save(banner_foreground, RES / "mipmap-xhdpi" / "ic_banner_foreground.png")


if __name__ == "__main__":
    main()
