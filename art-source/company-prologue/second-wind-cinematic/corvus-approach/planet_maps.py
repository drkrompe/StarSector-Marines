"""Derived Jangala maps for render_planet.py, from the two generated masters.

python planet_maps.py

Every map here is derived from `assets/planet-albedo-v2.png` or
`assets/planet-clouds-v2.png` by plain numpy/PIL arithmetic, so all of them are
pixel-registered to the albedo. A separately generated height or night-lights map would
not be registered: ImageGen does not reproduce a reference's coastline exactly, and a
city light half a degree off the coast is a light in the sea.

Writes, each with a `.provenance.json` (inputs, their hashes, this script, parameters):
  planet-ocean-v2.png    L8   1 = open water and shelf (specular, glint), 0 = land and ice
  planet-height-v2.png   I16  land relief for the bump: interior rise from the coast,
                              rugged highlands from the albedo's own ridge texture, flat
                              sea and flat desert pans
  planet-lights-v2.png   L8   night lights at twice the albedo's resolution: towns
                              clustered on coasts, deltas and the agricultural lowlands
  planet-clouds-v2-eyefix.png  the cloud master with the cyclone eye's hard black dot
                              raised to a dim, partly filled clearing
"""
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image

BASE = Path(__file__).resolve().parent
ASSETS = BASE / "assets"
ALBEDO = ASSETS / "planet-albedo-v2.png"
CLOUDS = ASSETS / "planet-clouds-v2.png"
SEED = 1729


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def blur(a, radius):
    """Gaussian blur that wraps east-west (the map is a cylinder) and clamps at the poles."""
    a = np.asarray(a, np.float64)
    h, w = a.shape
    pad = int(radius * 3) + 2
    a = np.pad(a, ((pad, pad), (0, 0)), mode="edge")
    fy = np.fft.fftfreq(a.shape[0])[:, None]
    fx = np.fft.fftfreq(w)[None, :]
    kernel = np.exp(-2 * (np.pi * radius) ** 2 * (fx * fx + fy * fy))   # FFT wraps x: the cylinder
    out = np.real(np.fft.ifft2(np.fft.fft2(a) * kernel))
    return out[pad:pad + h].astype(np.float32)


def save_l8(a, path):
    Image.fromarray(np.clip(a * 255 + 0.5, 0, 255).astype(np.uint8), "L").save(path)


def save_l16(a, path):
    Image.fromarray(np.clip(a * 65535 + 0.5, 0, 65535).astype(np.uint16)).save(path)


def provenance(path, inputs, params, note):
    rec = {
        "generator": "planet_maps.py (derived, no image generation)",
        "inputs": {str(p.relative_to(BASE)): sha(p) for p in inputs},
        "script": "planet_maps.py",
        "scriptSha256": sha(__file__),
        "parameters": params,
        "note": note,
        "output": {"path": str(path.relative_to(BASE)), "sha256": sha(path),
                   "dimensions": list(Image.open(path).size), "mode": Image.open(path).mode},
    }
    Path(str(path) + ".provenance.json").write_text(json.dumps(rec, indent=2) + "\n")


def main():
    rgb = np.asarray(Image.open(ALBEDO).convert("RGB"), np.float32) / 255.0
    h, w, _ = rgb.shape
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
    lat = (0.5 - (np.arange(h) + 0.5) / h)[:, None] * np.pi          # +pi/2 top row
    coslat = np.cos(lat)

    # Water: blue clearly above red, and not bright (ice is bright and nearly neutral).
    ice = smoothstep(0.55, 0.72, lum) * smoothstep(0.10, 0.03, np.abs(b - r))
    water = smoothstep(0.03, 0.10, b - r) * (1 - smoothstep(0.5, 0.7, lum))
    water = np.clip(blur(water, 0.7), 0, 1)
    out = ASSETS / "planet-ocean-v2.png"
    save_l8(water, out)
    provenance(out, [ALBEDO], {"water": "smoothstep(0.03,0.10,B-R)*(1-smoothstep(0.5,0.7,lum))", "blur": 0.7},
               "Ocean and shelf mask for specular/sunglint; land and ice are 0.")

    land = 1 - water
    # Interior rise: land blurred at growing radii is low at the coast, high inland.
    interior = sum(wt * blur(land, rad) for rad, wt in ((3, 0.2), (8, 0.3), (20, 0.3), (45, 0.2)))
    interior = smoothstep(0.5, 1.0, interior) * land
    # Highlands: brown/grey (red at or above green), mid luminance, and rugged: the albedo's
    # painted ridges give high local contrast. Desert pans are tan too but smooth.
    hp = lum - blur(lum, 2.0)
    rugged = np.sqrt(blur(hp * hp, 3.0))
    sat = rgb.max(-1) - rgb.min(-1)
    brownish = smoothstep(0.0, 0.05, r - g) + 0.7 * smoothstep(0.12, 0.05, sat) * smoothstep(0.25, 0.4, lum)
    highland = np.clip(blur(np.clip(brownish, 0, 1) * smoothstep(0.03, 0.06, rugged), 4.0) * 1.8, 0, 1)         * land * (1 - ice)
    ridges = np.clip(0.5 + hp * 6.0, 0, 1)                           # the painted ridge pattern itself
    height = land * (1 - ice) * (0.22 * interior + 0.55 * highland + 0.23 * highland * ridges)
    height = np.clip(height / max(height.max(), 1e-6), 0, 1)
    out = ASSETS / "planet-height-v2.png"
    save_l16(height, out)
    provenance(out, [ALBEDO], {"interior_radii": [3, 8, 20, 45], "highland": "brown & rugged", "ridge_gain": 6.0},
               "Relief for the surface bump. Sea is 0; deserts stay low; ridges follow the painted ones.")

    # Night lights. Population: coasts and deltas first, then the pale agricultural
    # lowlands; never highlands, desert, ice or the high latitudes.
    coast = land * smoothstep(0.05, 0.4, blur(water, 3.0))
    agri = land * smoothstep(0.30, 0.42, lum) * smoothstep(0.50, 0.40, lum) * smoothstep(-0.02, 0.04, g - r) \
        * (1 - highland)
    desert = smoothstep(0.5, 0.62, lum) * smoothstep(0.0, 0.08, r - b)
    pop = (1.0 * coast + 0.55 * blur(agri, 2.0)) * (1 - highland) * (1 - desert) * (1 - ice)
    pop *= smoothstep(0.30, 0.65, coslat)                             # nobody at the poles
    pop = np.clip(blur(pop, 1.2), 0, None)

    rng = np.random.default_rng(SEED)
    W2, H2 = w * 2, h * 2
    lights = np.zeros((H2, W2), np.float32)
    p = (pop ** 2).ravel()
    p /= p.sum()
    n_towns = 1400
    idx = rng.choice(p.size, n_towns, p=p)
    ty, tx = np.divmod(idx, w)
    yy, xx = np.mgrid[-9:10, -9:10].astype(np.float32)
    for y0, x0 in zip(ty, tx):
        size = min(rng.pareto(1.6) + 0.5, 6.0)                       # few cities, many towns
        cy = (y0 + rng.random()) * 2
        cx = (x0 + rng.random()) * 2
        stretch = 1.0 / max(np.cos((0.5 - cy / H2) * np.pi), 0.2)    # equirect: wider toward the poles
        sig = 0.55 + 0.45 * size
        blob = np.exp(-((xx / (sig * stretch)) ** 2 + (yy / sig) ** 2)) * (0.25 + 0.75 * size / 6.0)
        # a city sprawls: a few satellite clusters around it
        for _ in range(int(size)):
            ox, oy = rng.normal(0, 2.2 * sig, 2)
            blob += 0.35 * np.exp(-(((xx - ox) / (0.7 * sig * stretch)) ** 2 + ((yy - oy) / (0.7 * sig)) ** 2))
        iy, ix = int(cy), int(cx)
        ys = np.clip(np.arange(iy - 9, iy + 10), 0, H2 - 1)
        xs = np.arange(ix - 9, ix + 10) % W2
        lights[np.ix_(ys, xs)] += blob
    # faint scattered rural light wherever people live
    pop2 = np.asarray(Image.fromarray(pop.astype(np.float32), "F").resize((W2, H2), Image.BILINEAR))
    rural = (rng.random((H2, W2)) < 0.035 * pop2 / max(pop2.max(), 1e-6)) * rng.uniform(0.05, 0.25, (H2, W2))
    lights += rural
    land2 = np.asarray(Image.fromarray(land.astype(np.float32), "F").resize((W2, H2), Image.BILINEAR))
    lights *= smoothstep(0.3, 0.7, land2)                             # no lights at sea
    lights = 1 - np.exp(-1.6 * lights)                                # soft cap, cities saturate gently
    out = ASSETS / "planet-lights-v2.png"
    save_l8(lights, out)
    provenance(out, [ALBEDO], {"seed": SEED, "towns": n_towns, "sizes": "pareto(1.6)+0.5, cap 6",
                               "resolution": [W2, H2]},
               "Procedural night lights placed by a population field from the albedo: coasts, deltas and "
               "agricultural lowlands; none on highlands, desert, ice, sea or above ~60 degrees latitude.")

    # Cloud eye: the master draws the cyclone's eye as a hard black dot.
    cl = np.asarray(Image.open(CLOUDS).convert("L"), np.float32) / 255.0
    cy0, cx0 = 243, 1127
    win = cl[cy0 - 20:cy0 + 21, cx0 - 20:cx0 + 21]
    my, mx = np.unravel_index(np.argmin(win), win.shape)
    ey, ex = cy0 - 20 + my, cx0 - 20 + mx
    yy, xx = np.mgrid[:cl.shape[0], :cl.shape[1]]
    d = np.hypot(yy - ey, xx - ex)
    ring = (d > 9) & (d < 14)
    wall = float(cl[ring].mean())
    fill = wall * (0.40 + 0.6 * smoothstep(0, 9, d))
    feather = smoothstep(13, 8, d)
    fixed = np.where(d < 14, np.maximum(cl, fill) * feather + cl * (1 - feather), cl)
    out = ASSETS / "planet-clouds-v2-eyefix.png"
    save_l8(fixed, out)
    provenance(out, [CLOUDS], {"eye_centre_px": [int(ex), int(ey)], "eyewall_mean": round(wall, 3),
                               "fill": "40% of eyewall at centre rising to the wall by r=9 px, feather r 8-13"},
               "Cloud master with the cyclone eye's black dot raised to a dim clearing; otherwise unchanged.")
    print("eye at", ex, ey, "wall", round(wall, 3))
    print("water fraction", round(float(water.mean()), 3), "height mean on land",
          round(float(height[land > 0.5].mean()), 3), "lit fraction", round(float((lights > 0.1).mean()), 4))


if __name__ == "__main__":
    main()
