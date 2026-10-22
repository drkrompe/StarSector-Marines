"""Geometry-first guide for the hive creep material (`colony-breach-nouns.md`, Creep).

Writes a seamless 1024x1024 top-down relief guide: a branching vein network
(raised tubes), clustered nodules and a thin wet film between them. Code owns
the layout; ImageGen reskins the surface. Also writes the guide's own masks
(veins, nodules) so the shipped material keeps channels that register exactly
with the art painted over it.

    python art-source/hive-creep/creep_material_guide.py [out_dir]
"""
import sys, os
import numpy as np
from PIL import Image

N = 1024
rng = np.random.default_rng(0xB100)

def periodic_noise(period, seed):
    r = np.random.default_rng(seed)
    lat = r.random((period, period))
    u = np.arange(N) * period / N
    i0 = np.floor(u).astype(int); f = u - i0
    f = f * f * (3 - 2 * f)
    i1 = (i0 + 1) % period; i0 %= period
    a = lat[np.ix_(i0, i0)]; b = lat[np.ix_(i0, i1)]
    c = lat[np.ix_(i1, i0)]; d = lat[np.ix_(i1, i1)]
    fy = f[:, None]; fx = f[None, :]
    return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy

def fbm(periods, seed):
    out = np.zeros((N, N)); w = 0
    for k, p in enumerate(periods):
        amp = 0.5 ** k
        out += amp * periodic_noise(p, seed + k); w += amp
    return out / w

# Veins: crests of ridged noise, two scales, domain-warped so they meander.
warp_x = fbm([4, 8], 11) - 0.5
warp_y = fbm([4, 8], 21) - 0.5
def warped(field, amount):
    yy, xx = np.mgrid[0:N, 0:N]
    sx = (xx + warp_x * amount).astype(int) % N
    sy = (yy + warp_y * amount).astype(int) % N
    return field[sy, sx]
ridge_big = 1 - np.abs(2 * fbm([5, 10], 31) - 1)
ridge_small = 1 - np.abs(2 * fbm([12, 24], 41) - 1)
ridge_big = warped(ridge_big, 120)
ridge_small = warped(ridge_small, 60)
veins_big = np.clip((ridge_big - 0.90) / 0.08, 0, 1)
veins_small = np.clip((ridge_small - 0.93) / 0.06, 0, 1) * 0.7
veins = np.maximum(veins_big, veins_small)
veins = veins ** 0.7

# Nodules: clustered bumps, larger where the coarse field is high.
cluster = fbm([3, 6], 51)
bumps = fbm([24, 48], 61)
nodules = np.clip((bumps - 0.55) / 0.2, 0, 1) * np.clip((cluster - 0.45) / 0.25, 0, 1)

film = fbm([8, 16, 32], 71)

height = 0.35 + 0.25 * film + 0.55 * nodules + 0.45 * veins
height = np.clip(height, 0, 1)
# Lambert relief, light from the upper left, so the guide reads as 3D growth.
gy, gx = np.gradient(height * 40.0)
gx = np.roll(gx, 0); nx, ny, nz = -gx, -gy, np.ones_like(gx)
ln = np.sqrt(nx * nx + ny * ny + nz * nz)
light = np.array([-0.5, -0.6, 0.62]); light /= np.linalg.norm(light)
shade = np.clip((nx * light[0] + ny * light[1] + nz * light[2]) / ln, 0, 1)
guide = np.clip(0.25 + 0.55 * shade * (0.6 + 0.4 * height) - 0.18 * veins, 0, 1)

args = [a for a in sys.argv[1:] if not a.startswith('--')]
out = args[0] if args else os.path.dirname(os.path.abspath(__file__))
def save(name, a):
    Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), 'L').save(os.path.join(out, name))
save('creep-material-guide.png', guide)
# The masks register with the guide, not with the painted master (it drifts a few texels); kept
# for reference only, so they are written only on request.
if '--masks' in sys.argv:
    save('creep-material-veins.png', veins)
    save('creep-material-nodules.png', nodules)
    save('creep-material-height.png', height)
print('wrote guide and masks to', out)
