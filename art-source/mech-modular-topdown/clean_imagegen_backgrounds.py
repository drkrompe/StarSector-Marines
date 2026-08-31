"""Remove ImageGen's baked transparency checker from retained mech masters.

The built-in generator may preview transparency by painting a lightly noisy
white/gray checker into an RGB image.  The checker is connected to the canvas
edge while the chassis is enclosed by its dark outline, so an edge-connected
neutral-light flood removes it without keying light faction paint inside the
silhouette.
"""

from argparse import ArgumentParser
from collections import deque
from pathlib import Path

from PIL import Image


HERE = Path(__file__).resolve().parent
DEFAULT_ROOT = HERE / "sources" / "faction-chassis"


def is_checker(pixel: tuple[int, int, int, int]) -> bool:
    red, green, blue, _ = pixel
    return min(red, green, blue) >= 190 and max(red, green, blue) - min(red, green, blue) <= 28


def edge_connected_checker(image: Image.Image) -> bytearray:
    width, height = image.size
    pixels = image.load()
    background = bytearray(width * height)
    pending: deque[tuple[int, int]] = deque()

    def offer(x: int, y: int) -> None:
        index = y * width + x
        if background[index] or not is_checker(pixels[x, y]):
            return
        background[index] = 1
        pending.append((x, y))

    for x in range(width):
        offer(x, 0)
        offer(x, height - 1)
    for y in range(height):
        offer(0, y)
        offer(width - 1, y)

    while pending:
        x, y = pending.popleft()
        if x > 0:
            offer(x - 1, y)
        if x + 1 < width:
            offer(x + 1, y)
        if y > 0:
            offer(x, y - 1)
        if y + 1 < height:
            offer(x, y + 1)
    return background


def clear_border_foreground(image: Image.Image) -> bool:
    """Remove isolated generator debris touching the padded canvas edge."""
    width, height = image.size
    pixels = image.load()
    pending: deque[tuple[int, int]] = deque()
    visited = bytearray(width * height)

    def offer(x: int, y: int) -> None:
        index = y * width + x
        if visited[index] or pixels[x, y][3] == 0:
            return
        visited[index] = 1
        pending.append((x, y))

    for x in range(width):
        offer(x, 0)
        offer(x, height - 1)
    for y in range(height):
        offer(0, y)
        offer(width - 1, y)

    while pending:
        x, y = pending.popleft()
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < width and 0 <= ny < height:
                offer(nx, ny)

    changed = any(visited)
    if changed:
        for index, value in enumerate(visited):
            if value:
                x, y = index % width, index // width
                pixels[x, y] = (0, 0, 0, 0)
    return changed


def clean(path: Path) -> bool:
    image = Image.open(path).convert("RGBA")
    width, height = image.size
    if image.getchannel("A").getextrema()[0] < 255:
        changed = clear_border_foreground(image)
        corners = (
            image.getpixel((0, 0))[3],
            image.getpixel((width - 1, 0))[3],
            image.getpixel((0, height - 1))[3],
            image.getpixel((width - 1, height - 1))[3],
        )
        if corners == (0, 0, 0, 0):
            if changed:
                image.save(path)
            return changed

    corners = (
        image.getpixel((0, 0))[3],
        image.getpixel((width - 1, 0))[3],
        image.getpixel((0, height - 1))[3],
        image.getpixel((width - 1, height - 1))[3],
    )
    if corners == (0, 0, 0, 0):
        return False

    background = edge_connected_checker(image)
    if not any(background):
        raise ValueError(f"{path}: no transparent alpha or edge-connected checker found")

    pixels = image.load()
    background_luma = sum(
        sum(pixels[index % width, index // width][:3]) / 3
        for index, value in enumerate(background)
        if value
    ) / sum(background)

    adjacent = bytearray(width * height)
    for index, value in enumerate(background):
        if not value:
            continue
        x, y = index % width, index // width
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < width and 0 <= ny < height:
                adjacent[ny * width + nx] = 1

    for index, value in enumerate(background):
        x, y = index % width, index // width
        if value:
            pixels[x, y] = (0, 0, 0, 0)
            continue
        if not adjacent[index]:
            continue
        red, green, blue, _ = pixels[x, y]
        if max(red, green, blue) - min(red, green, blue) > 28:
            continue
        luma = (red + green + blue) / 3
        alpha = round(255 * max(0.0, min(1.0, 1.0 - luma / background_luma)))
        if alpha >= 250:
            continue
        if alpha <= 0:
            pixels[x, y] = (0, 0, 0, 0)
            continue
        fraction = alpha / 255
        channels = tuple(
            max(0, min(255, round((channel - (1 - fraction) * background_luma) / fraction)))
            for channel in (red, green, blue)
        )
        pixels[x, y] = (*channels, alpha)

    clear_border_foreground(image)
    image.save(path)
    return True


def main() -> None:
    parser = ArgumentParser()
    parser.add_argument("root", nargs="?", type=Path, default=DEFAULT_ROOT)
    args = parser.parse_args()
    paths = sorted(args.root.rglob("*.png"))
    changed = sum(clean(path) for path in paths)
    print(f"cleaned {changed} of {len(paths)} faction chassis masters")


if __name__ == "__main__":
    main()
