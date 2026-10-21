"""Write the Ship Systems tree's SVGs under mod/graphics/ui/refit/.

Usage: python art-source/refit-icons/make_refit_svgs.py <repo root>

Line icons in the officer specialty tree's language: gold strokes on the
console's dark green, a hexagonal node frame instead of the specialty diamond so
the two trees read as siblings rather than the same thing.
"""
import sys
from pathlib import Path

out = Path(sys.argv[1]) / "mod/graphics/ui/refit"
out.mkdir(parents=True, exist_ok=True)

GOLD, PALE, DARK, MUTED, RED, TEAL = "#dec58a", "#f3d98e", "#1d2822", "#738578", "#d88a6c", "#8fd0b8"


def svg(name, body, box=64):
    (out / f"{name}.svg").write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {box} {box}">\n{body}\n</svg>\n', encoding="utf-8", newline="\n")


def line(d, color=PALE, w=3.5):
    return f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{w}" stroke-linecap="round" stroke-linejoin="round"/>'


# Track icons, 48x48 line art.
icons = {
    "power": line("M27 5L13 27H23L20 43L35 20H25Z"),
    "life-support": line("M24 24m-15 0a15 15 0 1 0 30 0a15 15 0 1 0 -30 0") + line("M24 24L24 11M24 24L35 31M24 24L13 31", w=3)
                    + f'<circle cx="24" cy="24" r="3" fill="{PALE}"/>',
    "structure": line("M8 10H40M8 38H40M24 10V38M14 10V38M34 10V38", w=3) + line("M14 10L24 24L34 10M14 38L24 24L34 38", w=2),
    "compute": line("M14 14H34V34H14Z") + line("M19 19H29V29H19Z", w=2)
               + line("M18 14V8M24 14V8M30 14V8M18 34V40M24 34V40M30 34V40M14 18H8M14 24H8M14 30H8M34 18H40M34 24H40M34 30H40", w=2.5),
    "fabrication": line("M30 8a8 8 0 0 0 -8 11L9 32a4 4 0 0 0 6 6L28 25a8 8 0 0 0 11 -8l-5 5l-5 -1l-1 -5z"),
    "mess": line("M14 8V22M10 8V18a4 4 0 0 0 8 0V8M14 22V40") + line("M32 8c-5 3 -5 13 0 15V40"),
    "gym": line("M6 24H42") + line("M11 15V33M17 12V36M31 12V36M37 15V33", w=4),
    "sick-bay": f'<path d="M19 7H29V19H41V29H29V41H19V29H7V19H19Z" fill="none" stroke="{PALE}" stroke-width="3.5" stroke-linejoin="round"/>',
    "rec-lounge": line("M24 6V14M20 10H28") + line("M14 40V26a10 10 0 0 1 20 0V40") + line("M10 40H38"),
    "brig": line("M10 8H38V40H10Z") + line("M17 8V40M24 8V40M31 8V40", w=3),
    "range": line("M24 24m-16 0a16 16 0 1 0 32 0a16 16 0 1 0 -32 0", w=3) + line("M24 24m-8 0a8 8 0 1 0 16 0a8 8 0 1 0 -16 0", w=3)
             + line("M24 4V12M24 36V44M4 24H12M36 24H44", w=2.5),
    "hull": line("M24 5L40 11V23C40 33 33 40 24 43C15 40 8 33 8 23V11Z") + line("M15 20H33M15 28H33", w=2.5),
    "flux": line("M6 24C10 12 14 12 18 24S26 36 30 24S38 12 42 24") + line("M10 38H38M10 10H38", w=2),
    "sensors": line("M8 40A32 32 0 0 1 40 8", w=3) + line("M8 40A21 21 0 0 1 29 19", w=3) + line("M8 40A10 10 0 0 1 18 30", w=3)
               + f'<circle cx="8" cy="40" r="3.5" fill="{PALE}"/>',
    "drive": line("M10 12L22 24L10 36M24 12L36 24L24 36"),
    "hydroponics": line("M24 42V22") + line("M24 30C15 30 9 24 9 14C18 14 24 20 24 30Z", w=3)
                   + line("M24 24C24 15 30 9 39 9C39 18 33 24 24 24Z", w=3) + line("M12 42H36", w=2.5),
    "wardroom": line("M10 22L24 12L38 22") + line("M10 32L24 22L38 32") + line("M10 42L24 32L38 42", w=2.5),
    "briefing": line("M6 34H42") + line("M12 34L10 42M36 34L38 42", w=3)
                + line("M24 28C18 21 17 16 17 13a7 7 0 0 1 14 0C31 16 30 21 24 28Z", w=3)
                + f'<circle cx="24" cy="13" r="2.5" fill="{PALE}"/>',
    "holds": line("M8 16L24 8L40 16V34L24 42L8 34Z") + line("M8 16L24 24L40 16M24 24V42", w=3),
}
for name, body in icons.items():
    svg("track-" + name, body, 48)


def hexagon(r, cx=32, cy=32):
    import math
    pts = [(cx + r * math.cos(math.radians(60 * i - 90)), cy + r * math.sin(math.radians(60 * i - 90))) for i in range(6)]
    return "M" + "L".join(f"{x:.1f} {y:.1f}" for x, y in pts) + "Z"


def pips(n, color):
    xs = {1: [32], 2: [26, 38], 3: [21, 32, 43]}[n]
    return "".join(f'<rect x="{x - 3}" y="28" width="6" height="8" rx="1" fill="{color}"/>' for x in xs)


# Node frames: one per tier (pips 1-3) and state.
for tier in (1, 2, 3):
    svg(f"node-{tier}-owned",
        f'<path d="{hexagon(30)}" fill="none" stroke="{GOLD}" stroke-width="1.5" stroke-opacity="0.5"/>'
        f'<path d="{hexagon(25)}" fill="#3a3220" stroke="{GOLD}" stroke-width="3"/>' + pips(tier, PALE))
    svg(f"node-{tier}-available",
        f'<path d="{hexagon(30)}" fill="none" stroke="{TEAL}" stroke-width="1.5" stroke-opacity="0.55"/>'
        f'<path d="{hexagon(25)}" fill="{DARK}" stroke="{TEAL}" stroke-width="3"/>' + pips(tier, TEAL))
    svg(f"node-{tier}-locked",
        f'<path d="{hexagon(25)}" fill="#141b18" stroke="{MUTED}" stroke-width="2" stroke-dasharray="4 3"/>' + pips(tier, MUTED))

# Root: the hull herself.
svg("root-second-wind",
    f'<path d="{hexagon(30)}" fill="{DARK}" stroke="{GOLD}" stroke-width="2.5"/>'
    f'<path d="{hexagon(22)}" fill="none" stroke="{MUTED}" stroke-width="1.5"/>'
    + line("M32 12L40 24V44L36 52H28L24 44V24Z", GOLD, 2.5) + line("M24 30L16 36V42L24 40M40 30L48 36V42L40 40", GOLD, 2.5))

print(len(list(out.iterdir())), "svgs")
