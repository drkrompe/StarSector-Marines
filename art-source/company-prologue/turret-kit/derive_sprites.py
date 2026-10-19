"""Measure the game's turret sprites into sprite_outlines.json for turret_kit.

  python derive_sprites.py [--game "C:/Program Files (x86)/Fractal Softworks/Starsector"]

Reads each catalogue weapon's .wpn and its layered turret sprites from the
read-only install (nothing is copied into the repo) and records shapes in
sprite pixels about the sprite centre: one pixel is one hull unit, +x toward
the muzzle, +y to port. Polygons are traced pixel outlines simplified to
straight edges (so a staircase becomes a chamfer), counter-clockwise.

  body      the base sprite's outline with prongs under 3 px opened away
  bodies    every part of that opened outline at least a fifth of the largest
            (two halves where a slot splits the sprite down its line of fire)
  under     the under sprite's outline, where the weapon has one (Squall)
  prongs    what the opening removed: sight posts, rails, thin arms
  slots     dark detail inside the body (vents, grilles, embrasures, ladders)
  panels    patches of a second paint (the flak's red box, Hephaestus brass)
  barrels   each part of the gun (recoil) sprite: a lathe profile (x, radius)
            and dark band rows when it is long and round, else an outline
  colours   the base's main paint, its accent, the gun's metal (linear RGB)
  offsets   turretOffsets from the .wpn ([x, y] per muzzle), barrel mode,
            visual recoil and whether the gun is drawn below the base

The kit builds a turret's plan from these, so its top view is the sprite's.
Heights are the kit's judgement.
"""
import argparse
import colorsys
import json
import os
import re

from PIL import Image

WEAPONS = {   # kit id -> .wpn id
    'vulcan': 'vulcan', 'lightac': 'lightac', 'lightmg': 'lightmg',
    'heavyac': 'heavyac', 'flak': 'flak', 'dualflak': 'dualflak',
    'heavymauler': 'heavymauler', 'hveldriver': 'hveldriver', 'devastator': 'devastator',
    'mark9': 'mark9', 'hellbore': 'hellbore', 'hephaestus': 'hephag', 'needler': 'heavyneedler',
    'squall': 'squall', 'hurricane': 'hurricane',
    # Tri-Tachyon (the Paragon's Elite fit): beams have no gun sprite, the reaper is drawn loaded.
    'tachyonlance': 'tachyonlance', 'gravitonbeam': 'gravitonbeam', 'taclaser': 'taclaser', 'reaper': 'reaper',
}
ALPHA = 40
# Second paints, sRGB, read off each base sprite; MAIN_HINT is its body paint
# where that is needed to tell the two apart.
ACCENT = {'flak': (200, 60, 55), 'dualflak': (225, 160, 60), 'devastator': (190, 120, 70),
          'hephaestus': (215, 180, 95), 'hveldriver': (200, 185, 120)}
MAIN_HINT = {'flak': (150, 120, 100), 'dualflak': (150, 115, 105), 'devastator': (205, 205, 215),
             'hephaestus': (160, 150, 140), 'hveldriver': (175, 175, 180)}


def lin(c):
    c = c/255
    return round(c/12.92 if c <= .04045 else ((c+.055)/1.055)**2.4, 4)


def grid(im, test):
    px = im.load()
    return [[test(px[j, i]) for j in range(im.width)] for i in range(im.height)]


def morph(m, keep, k=1):
    h, w = len(m), len(m[0])
    return [[keep(m[ii][jj] if 0 <= ii < h and 0 <= jj < w else False
                  for ii in range(i-k, i+k+1) for jj in range(j-k, j+k+1))
             for j in range(w)] for i in range(h)]


def components(m, min_px=1):
    h, w = len(m), len(m[0])
    seen, out = set(), []
    for i in range(h):
        for j in range(w):
            if m[i][j] and (i, j) not in seen:
                stack, comp = [(i, j)], []
                seen.add((i, j))
                while stack:
                    a, b = stack.pop()
                    comp.append((a, b))
                    for da, db in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        p = (a+da, b+db)
                        if 0 <= p[0] < h and 0 <= p[1] < w and m[p[0]][p[1]] and p not in seen:
                            seen.add(p)
                            stack.append(p)
                if len(comp) >= min_px:
                    out.append(comp)
    return out


def outline(cells):
    """Outer boundary of a 4-connected pixel set as grid corners (i, j)."""
    return max(loops_of(cells), key=lambda lp: abs(_area(lp)))


def _area(lp):
    return sum(a[1]*b[0]-b[1]*a[0] for a, b in zip(lp, lp[1:]+lp[:1]))/2


def outer_loops(cells, share=.2):
    """Every outer boundary loop at least `share` of the largest: a pixel set that
    pinches to one pixel corner (the tachyon lance either side of its beam slot)
    traces as two loops, of which outline() keeps only the larger."""
    loops = pinched_loops(cells)
    big = max(loops, key=lambda lp: abs(_area(lp)))
    sign = _area(big) > 0
    return [lp for lp in loops if (_area(lp) > 0) == sign and abs(_area(lp)) >= share*abs(_area(big))]


def pinched_loops(cells):
    """Every boundary loop, split where the set pinches to a shared pixel corner.
    loops_of keys edges by their start corner, so at a pinch one edge overwrites
    the other and a loop jumps across; here a corner keeps every edge leaving it,
    and the walk turns left first, hugging the pixel it is going round."""
    cs = set(cells)
    out = {}
    for i, j in cs:
        for a, b, open_ in (((i, j+1), (i, j), (i-1, j)), ((i+1, j), (i+1, j+1), (i+1, j)),
                            ((i, j), (i+1, j), (i, j-1)), ((i+1, j+1), (i, j+1), (i, j+1))):
            if open_ not in cs:
                out.setdefault(a, []).append(b)
    loops = []
    while out:
        start = next(iter(out))
        prev, cur = start, out[start].pop()
        if not out[start]:
            del out[start]
        loop = [start]
        while cur != start:
            loop.append(cur)
            ends = out[cur]
            din = (cur[0]-prev[0], cur[1]-prev[1])
            # left of travel with interior on the left: (di, dj) -> (-dj, di) in (row, col)
            def rank(e, din=din, cur=cur):
                d = (e[0]-cur[0], e[1]-cur[1])
                return 0 if d == (-din[1], din[0]) else 1 if d == din else 2
            nxt = min(ends, key=rank)
            ends.remove(nxt)
            if not ends:
                del out[cur]
            prev, cur = cur, nxt
        loops.append(loop)
    return loops


def loops_of(cells):
    """Every boundary loop of a pixel set as grid corners (i, j), interior on the left."""
    cs = set(cells)
    edges = {}
    for i, j in cs:          # directed boundary edges, interior on the left
        if (i-1, j) not in cs:
            edges[(i, j+1)] = (i, j)
        if (i+1, j) not in cs:
            edges[(i+1, j)] = (i+1, j+1)
        if (i, j-1) not in cs:
            edges[(i, j)] = (i+1, j)
        if (i, j+1) not in cs:
            edges[(i+1, j+1)] = (i, j+1)
    loops = []
    while edges:
        start, nxt = edges.popitem()
        loop = [start]
        while nxt != start and nxt in edges:
            loop.append(nxt)
            nxt = edges.pop(nxt)
        loops.append(loop)
    return loops


def simplify(pts, tol):
    """Douglas-Peucker on a closed loop."""
    def dp(seg):
        if len(seg) < 3:
            return seg
        (x0, y0), (x1, y1) = seg[0], seg[-1]
        dx, dy = x1-x0, y1-y0
        n = (dx*dx+dy*dy)**.5 or 1e-9
        d = [abs(dy*(x-x0)-dx*(y-y0))/n for x, y in seg[1:-1]]
        k = max(range(len(d)), key=d.__getitem__)
        if d[k] <= tol:
            return [seg[0], seg[-1]]
        return dp(seg[:k+2])[:-1]+dp(seg[k+1:])
    far = max(range(len(pts)), key=lambda k: (pts[k][0]-pts[0][0])**2+(pts[k][1]-pts[0][1])**2)
    a = dp(pts[:far+1])
    b = dp(pts[far:]+pts[:1])
    return a[:-1]+b[:-1]


def poly(cells, cx, cy, tol=.75, loop=None):
    lp = simplify(loop or outline(cells), tol)
    pts = [(round(cy-i, 2), round(cx-j, 2)) for i, j in lp]          # (x forward, y port)
    s = sum(a[0]*b[1]-b[0]*a[1] for a, b in zip(pts, pts[1:]+pts[:1]))
    return pts if s > 0 else pts[::-1]


def lum(p):
    return .3*p[0]+.59*p[1]+.11*p[2]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--game', default='C:/Program Files (x86)/Fractal Softworks/Starsector')
    args = ap.parse_args()
    core = os.path.join(args.game, 'starsector-core')
    out = {}
    for kid, wid in WEAPONS.items():
        t = open(os.path.join(core, 'data', 'weapons', wid+'.wpn'), encoding='utf-8').read()

        def get(key):
            m = re.search(r'^\s*"%s"\s*:\s*"([^"]+)"' % key, t, re.M)
            return m and m.group(1)
        offs = re.search(r'"turretOffsets"\s*:\s*\[([^\]]*)\]', t)
        nums = [float(v) for v in re.findall(r'-?\d+(?:\.\d+)?', offs.group(1))] if offs else []
        hints = re.search(r'^\s*"renderHints"\s*:\s*\[([^\]]*)\]', t, re.M)
        base = Image.open(os.path.join(core, get('turretSprite'))).convert('RGBA')
        cx, cy = base.width/2, base.height/2
        px = base.load()
        solid = grid(base, lambda p: p[3] > ALPHA)
        body_m = morph(morph(solid, all), any)
        comps = components(body_m)
        body_c = max(comps, key=len)
        # A sprite split down its line of fire (the tachyon lance's open beam slot)
        # comes apart into halves: every outer loop at least a fifth of the largest.
        bodies = [poly(None, cx, cy, loop=lp) for c in comps if len(c) >= .2*len(body_c) for lp in outer_loops(c)]
        body_set = set(body_c)
        inner = morph([[(i, j) in body_set for j in range(base.width)] for i in range(base.height)], all)
        vals = [px[j, i] for i, j in body_c if inner[i][j]]
        top = max(lum(p) for p in vals)
        # Paints: the accent is named per weapon (ACCENT, read off the sprite by
        # eye: automatic clustering misses the flak's red box and invents one on
        # the Vulcan). Panels are the lit pixels nearer the accent than the main.
        lit = [p[:3] for p in vals if lum(p) > .35*top]

        def med(ps):
            ps = sorted(ps, key=lum)
            p = ps[len(ps)//2]
            return [lin(p[0]), lin(p[1]), lin(p[2])]

        def dist(a, b):
            return sum((a[c]-b[c])**2 for c in range(3))**.5
        hint = ACCENT.get(kid)
        is_acc = (lambda p: dist(p, hint) < 70 and dist(p, hint) < .8*dist(p, MAIN_HINT.get(kid, p))) if hint else             (lambda p: False)
        colours = {'main': med([p for p in lit if not is_acc(p)] or lit)}
        panels = []
        if hint:
            am = [[False]*base.width for _ in range(base.height)]
            accp = []
            for i, j in body_c:
                p = px[j, i][:3]
                if lum(p) > .25*top and is_acc(p):
                    am[i][j] = True
                    accp.append(p)
            if accp:
                colours['accent'] = med(accp)
            panels = [poly(c, cx, cy, .6) for c in components(am, 3)]
        dm = [[False]*base.width for _ in range(base.height)]
        for i, j in body_c:
            if inner[i][j] and lum(px[j, i]) < .3*top:
                dm[i][j] = True
        slots = [poly(c, cx, cy, .5) for c in components(dm, 2)]
        prong_m = [[solid[i][j] and not body_m[i][j] for j in range(base.width)] for i in range(base.height)]
        prongs = [poly(c, cx, cy, .5) for c in components(prong_m, 3)]
        barrels, metal = [], None
        gun = get('turretGunSprite')
        if gun:
            gi = Image.open(os.path.join(core, gun)).convert('RGBA')
            gcx, gcy = gi.width/2, gi.height/2
            gp = gi.load()
            gm = grid(gi, lambda p: p[3] > ALPHA)
            allpx = []
            for comp in components(gm, 4):
                rows = {}
                for a, b in comp:
                    rows.setdefault(a, []).append(b)
                ys = [b for _, b in comp]
                length, width = max(rows)-min(rows)+1, max(ys)-min(ys)+1
                g_top = max(lum(gp[b, a]) for a, b in comp)
                entry = {'x_back': round(gcy-max(rows)-1, 2), 'x_front': round(gcy-min(rows), 2),
                         'y': round(gcx-(min(ys)+max(ys)+1)/2, 2), 'half_width': width/2}
                if length >= 2*width:
                    entry['kind'] = 'lathe'
                    entry['profile'] = [[round(gcy-a-.5, 2), (max(bs)-min(bs)+1)/2] for a, bs in sorted(rows.items(), reverse=True)]
                    entry['bands'] = [round(gcy-a-.5, 2) for a, bs in sorted(rows.items(), reverse=True)
                                      if sum(lum(gp[b, a]) for b in bs)/len(bs) < .42*g_top]
                else:
                    entry['kind'] = 'block'
                    entry['outline'] = poly(comp, gcx, gcy, .6)
                barrels.append(entry)
                allpx += [gp[b, a] for a, b in comp if lum(gp[b, a]) > .45*g_top]
            barrels.sort(key=lambda b: -b['y'])
            if allpx:
                metal = med(allpx)
        if metal:
            colours['metal'] = metal
        under = []
        if get('turretUnderSprite'):
            ui = Image.open(os.path.join(core, get('turretUnderSprite'))).convert('RGBA')
            um = morph(morph(grid(ui, lambda p: p[3] > ALPHA), all), any)
            under = poly(max(components(um), key=len), ui.width/2, ui.height/2)
        out[kid] = {'wpn': wid, 'under': under, 'size': re.search(r'"size"\s*:\s*"(\w+)"', t).group(1),
                    'sprite_px': [base.width, base.height],
                    'body': poly(body_c, cx, cy), 'bodies': bodies, 'prongs': prongs, 'slots': slots, 'panels': panels,
                    'barrels': barrels, 'colours': colours,
                    'offsets': [nums[k:k+2] for k in range(0, len(nums)-1, 2)],
                    'barrel_mode': (re.search(r'"barrelMode"\s*:\s*"(\w+)"', t) or [None, None])[1],
                    'visual_recoil': float((re.search(r'"visualRecoil"\s*:\s*([\d.]+)', t) or [0, 0])[1]),
                    'barrel_below': bool(hints and 'RENDER_BARREL_BELOW' in hints.group(1))}
        print(f"{kid:12s} {out[kid]['size']:6s} body {len(out[kid]['body']):3d} pts  prongs {len(prongs)}  "
              f"slots {len(slots)}  panels {len(panels)}  barrels {[b['kind'][0] for b in barrels]}  "
              f"below {out[kid]['barrel_below']}  accent {'accent' in colours}")
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'sprite_outlines.json')
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(out, fh, indent=1)
    print('wrote', path)


if __name__ == '__main__':
    main()
