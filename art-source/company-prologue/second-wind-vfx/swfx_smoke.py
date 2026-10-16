"""Smoke and atmosphere: exhaust haze, landing dust, seal wisps, weld smoke.

Everything here is a render-time procedural volume (a container box with a
volume-only material whose density falls to zero well inside the box) or a
geometry-nodes point set, all driven by `frame` and the knobs on the
'SWFX controls' empty. Nothing is simulated or cached; the same file renders
the same frames.

Pieces (all under the 'SWFX | Smoke' collection):

* Exhaust haze, one volume per RUNNING engine, parented to its nozzle anchor
  (+X aft). The thruster module owns the hot plume; this is the large, soft,
  low-density and almost non-emissive haze that hangs downstream of it.
  'salvaged' engines (patched/refit 3 and 4) leave a dirtier grey haze with
  faint soot; 'clean' engines a thin pale one. Vacuum (smk_atmosphere 0):
  barely there and spreading fast. Atmosphere (1): longer, hangs, and bends
  with the wind knobs.
* Landing dust: a shallow ground-wash volume on the ground plane at
  smk_ground_z, following the hull's XY. Its envelope rises as the ship's
  lowest point nears the ground. The Second Wind has no ventral nozzles, so
  this is authored downwash (the lift the shot implies), not a consequence of
  any modelled jet.
* Seal wisps (patched/refit only, spec['seal_wisps']): 2-4 faint intermittent
  leaks at the borrowed hab-door seals and pressure patches the damage layer
  adds. Canon allows no catastrophic venting: these are minor wisps, a faint
  white jet that dies within a few units in vacuum, a slow rising steam wisp
  in atmosphere.
* Welds (add_weld, only on request): arc light with flicker, a glowing arc
  point, a small rising smoke wisp and a deterministic spark shower.
"""
import math
import random

import bpy
from mathutils import Matrix, Vector

import swfx_core
from swfx_core import attach, node_material, units, value_node

GROUP = 'Smoke'
PREFIX = 'SWFX smoke'
FPS = swfx_core.FPS
RUNNING = ('salvaged', 'clean')

# Seal wisp candidates: the damage layer's patched-state objects (joined
# meshes; every loose island is one seal or patch).
# Matched case-insensitively on the name's end (after any '.NNN'), so both
# the scar table's 'Scar <id> borrowed seal gasket' and the older
# 'Damage - Borrowed seal gasket' naming are found.
SEAL_PARTS = ('borrowed seal gasket', 'pressure patch')


def _seal_parts(kind):
    out = []
    for o in bpy.data.objects:
        if o.type != 'MESH' or o.name.startswith('SWFX'):
            continue
        stem = o.name.rsplit('.', 1)[0] if o.name[-4:-3] == '.' and o.name[-3:].isdigit() else o.name
        if stem.lower().endswith(kind):
            out.append(o)
    return sorted(out, key=lambda o: o.name)
HULL_NAMES = ('Main hull - level datum Z0', 'Main hull', 'Hull')

# Dust sizes are ship-relative (the hull is ~100 units long), so they stay in
# hull units; anything physical (speeds, gravity, spark and weld sizes) goes
# through swfx_core.units(metres).
DUST_RADIUS = 85.0      # half extent of the ground-wash box
DUST_HEIGHT = 12.0      # it stays shallow
DUST_REACH = 45.0       # height above ground at which downwash starts to raise dust


# ---------------------------------------------------------------------------
# small node-graph helper (works in shader and geometry-node trees)
# ---------------------------------------------------------------------------
class G:
    def __init__(self, tree):
        self.tree = tree
        self.nodes = tree.nodes
        self.links = tree.links

    def _in(self, sock, value):
        if hasattr(value, 'node'):
            self.links.new(value, sock)
        elif value is not None:
            sock.default_value = value

    def node(self, kind, **props):
        n = self.nodes.new(kind)
        for k, v in props.items():
            setattr(n, k, v)
        return n

    def m(self, op, a, b=None, c=None, clamp=False):
        n = self.node('ShaderNodeMath', operation=op, use_clamp=clamp)
        for i, v in enumerate((a, b, c)):
            self._in(n.inputs[i], v)
        return n.outputs[0]

    def v(self, op, a, b=None, scale=None):
        n = self.node('ShaderNodeVectorMath', operation=op)
        self._in(n.inputs[0], a)
        self._in(n.inputs[1], b)
        if scale is not None:
            self._in(n.inputs['Scale'], scale)
        out = n.outputs['Value'] if op in ('LENGTH', 'DOT_PRODUCT', 'DISTANCE') else n.outputs['Vector']
        return out

    def sep(self, vec):
        n = self.node('ShaderNodeSeparateXYZ')
        self._in(n.inputs[0], vec)
        return n.outputs[0], n.outputs[1], n.outputs[2]

    def comb(self, x, y, z):
        n = self.node('ShaderNodeCombineXYZ')
        for i, v in enumerate((x, y, z)):
            self._in(n.inputs[i], v)
        return n.outputs[0]

    def mr(self, x, a, b, c=0.0, d=1.0, smooth=False):
        """Clamped map range a..b -> c..d (smoothstep if asked)."""
        n = self.node('ShaderNodeMapRange', clamp=True)
        if smooth:
            n.interpolation_type = 'SMOOTHSTEP'
        for name, v in (('Value', x), ('From Min', a), ('From Max', b), ('To Min', c), ('To Max', d)):
            self._in(n.inputs[name], v)
        return n.outputs[0]

    def noise(self, vec, w, scale, detail=3.0, rough=.55, distortion=0.0):
        n = self.node('ShaderNodeTexNoise', noise_dimensions='4D')
        self._in(n.inputs['Vector'], vec)
        self._in(n.inputs['W'], w)
        self._in(n.inputs['Scale'], scale)
        n.inputs['Detail'].default_value = detail
        n.inputs['Roughness'].default_value = rough
        n.inputs['Distortion'].default_value = distortion
        return n.outputs['Fac']

    def gauss(self, x, width, k=1.0):
        """exp(-k (x/width)^2)."""
        q = self.m('DIVIDE', x, width)
        return self.m('EXPONENT', self.m('MULTIPLY', self.m('MULTIPLY', q, q), -k))

    def mix(self, fac, a, b):
        n = self.node('ShaderNodeMix', data_type='RGBA')
        self._in(n.inputs[0], fac)
        self._in(n.inputs[6], a)
        self._in(n.inputs[7], b)
        return n.outputs[2]

    def lerp(self, t, a, b):
        return self.m('MULTIPLY_ADD', t, b - a, a) if not hasattr(a, 'node') and not hasattr(b, 'node') \
            else self.m('ADD', a, self.m('MULTIPLY', t, self.m('SUBTRACT', b, a)))


def volume_output(g, density, color, absorption, anisotropy=.2, emission=None, emission_color=(1, .55, .25, 1)):
    vol = g.node('ShaderNodeVolumePrincipled')
    g._in(vol.inputs['Density'], density)
    g._in(vol.inputs['Color'], color)
    g._in(vol.inputs['Absorption Color'], absorption)
    vol.inputs['Anisotropy'].default_value = anisotropy
    if emission is not None:
        g._in(vol.inputs['Emission Strength'], emission)
        vol.inputs['Emission Color'].default_value = emission_color
    out = g.node('ShaderNodeOutputMaterial')
    g.links.new(vol.outputs[0], out.inputs['Volume'])
    return vol


def box_mesh(name, coll, lo, hi, material):
    """A closed volume container spanning lo..hi in object coordinates."""
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    verts = [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    faces = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.update()
    ob = bpy.data.objects.new(name, me)
    coll.objects.link(ob)
    me.materials.append(material)
    _no_shadow_catch(ob)
    return ob


def _no_shadow_catch(ob):
    # Volumes stay out of glossy reflections of polished paint (cheap, no loss).
    try:
        ob.visible_glossy = False
    except AttributeError:
        pass


def parent_keep_world(child, part, world):
    """Parent child to part (if any) keeping the given world matrix."""
    if part is not None:
        child.parent = part
        child.matrix_parent_inverse = part.matrix_world.inverted()
    child.matrix_basis = world
    return child


def shared_knobs(ctx):
    ctx.prop('thr_throttle', 1.0, 0, 1, 'Master drive throttle (shared by every group)')
    for i in (1, 2, 3, 4):
        ctx.prop(f'thr_throttle_e{i}', 1.0, 0, 1, f'Engine {i} throttle, multiplies thr_throttle')
    ctx.prop('smk_atmosphere', 1.0, 0, 1, '0 vacuum (haze barely exists, disperses fast), 1 in atmosphere (it hangs, drifts, rises)')
    for ax in 'xyz':
        ctx.prop(f'smk_wind_{ax}', 0.0, -1, 1, f'Wind along hull {ax.upper()} in atmosphere; 1 = strong breeze bending the haze ~35% of its length')


def refresh_controls(ctx):
    """Custom props added to the controls empty after a depsgraph evaluation
    (any ray cast evaluates it) are missing from its evaluated copy until the
    ID is tagged, and every driver reading them silently reads 0 / is flagged
    invalid. Tag it, and re-arm the drivers this group made."""
    ctx.controls.update_tag()
    owners = [o for o in _smoke_objects()]
    owners += [o.data for o in owners if o.data is not None]
    owners += [m.node_tree for o in _smoke_objects() if o.type == 'MESH' for m in o.data.materials if m and m.node_tree]
    owners += [md.node_group for o in _smoke_objects() for md in o.modifiers if md.type == 'NODES' and md.node_group]
    for owner in owners:
        ad = getattr(owner, 'animation_data', None)
        for fc in (ad.drivers if ad else ()):
            fc.driver.expression = fc.driver.expression
    bpy.context.view_layer.update()


def time_node(ctx, g, name='t'):
    return value_node(ctx, g.nodes, f'{name} seconds', f'frame/{FPS}')


def hull_object(ctx):
    for n in HULL_NAMES:
        ob = bpy.data.objects.get(n)
        if ob is not None:
            return ob
    return ctx.anchors.hull


# ---------------------------------------------------------------------------
# 1. exhaust haze
# ---------------------------------------------------------------------------
def build_haze(ctx, coll, anchor, mode):
    i, R = anchor.index, anchor.radius
    dirty = mode == 'salvaged'
    mat, nodes, links = node_material(f'{PREFIX} haze e{i}')
    g = G(mat.node_tree)
    props_t = ('thr_throttle', f'thr_throttle_e{i}', 'smk_haze', 'smk_atmosphere')

    # Length and width of the container (and of the shape inside it).
    len_expr = f'{R:.3f}*(7+17*smk_atmosphere)'
    spread_expr = '(0.46-0.24*smk_atmosphere)'
    # Wind, in this nozzle's local frame (hull axes at build time).
    rot = anchor.empty.matrix_world.to_3x3().normalized()
    ly, lz = rot.col[1], rot.col[2]
    wy_expr = f'({ly.x:.4f}*smk_wind_x+{ly.y:.4f}*smk_wind_y+{ly.z:.4f}*smk_wind_z)'
    wz_expr = f'({lz.x:.4f}*smk_wind_x+{lz.y:.4f}*smk_wind_y+{lz.z:.4f}*smk_wind_z)'
    wind_props = ('smk_atmosphere', 'smk_wind_x', 'smk_wind_y', 'smk_wind_z')
    width_expr = (f'({R:.3f}*1.6+{len_expr}*{spread_expr}'
                  f'+0.35*{len_expr}*smk_atmosphere*max(abs({wy_expr}),abs({wz_expr})))')

    L = value_node(ctx, nodes, 'length', len_expr, ('smk_atmosphere',))
    W = value_node(ctx, nodes, 'width', width_expr, wind_props)
    spread = value_node(ctx, nodes, 'spread', spread_expr, ('smk_atmosphere',))
    atm = value_node(ctx, nodes, 'atmosphere', 'smk_atmosphere', ('smk_atmosphere',))
    amount = value_node(ctx, nodes, 'amount',
                        f'thr_throttle*thr_throttle_e{i}*smk_haze*(0.08+0.92*smk_atmosphere)', props_t)
    dy_k = value_node(ctx, nodes, 'drift y', f'0.35*{len_expr}*smk_atmosphere*{wy_expr}', wind_props)
    dz_k = value_node(ctx, nodes, 'drift z', f'0.35*{len_expr}*smk_atmosphere*{wz_expr}', wind_props)
    t = time_node(ctx, g)

    tc = g.node('ShaderNodeTexCoord')
    p = g.v('MULTIPLY', tc.outputs['Object'], g.comb(L, W, W))     # physical, nozzle frame
    x, y, z = g.sep(p)
    u = g.m('DIVIDE', x, L)
    u2 = g.m('MULTIPLY', u, u)
    dy, dz = g.m('MULTIPLY', dy_k, u2), g.m('MULTIPLY', dz_k, u2)
    ry, rz = g.m('SUBTRACT', y, dy), g.m('SUBTRACT', z, dz)
    rr = g.v('LENGTH', g.comb(ry, rz, 0.0))
    radius = g.m('MULTIPLY_ADD', x, spread, R * .85)
    radial = g.gauss(rr, radius, 2.0)
    axial = g.m('MULTIPLY', g.mr(u, 0.0, 0.12, smooth=True),
                g.m('POWER', g.m('SUBTRACT', 1.0, u, clamp=True), 1.5))

    # Flowing breakup: the haze leaves the nozzle at a walking pace and is
    # carried by the wind; large slow billows times a finer tatter.
    flow = g.m('MULTIPLY', t, g.lerp(atm, units(9.0), units(3.3)))
    adv = g.comb(flow, g.m('MULTIPLY', t, g.m('MULTIPLY', dy_k, .12)), g.m('MULTIPLY', t, g.m('MULTIPLY', dz_k, .12)))
    q = g.v('SUBTRACT', p, adv)
    n1 = g.mr(g.noise(q, g.m('MULTIPLY', t, .07), 1.0 / (R * 3.2), 3, .5), .36, .74, smooth=True)
    n2 = g.mr(g.noise(q, g.m('ADD', g.m('MULTIPLY', t, .11), 7.3), 1.0 / (R * 1.1), 4, .6), .25, .8, .35, 1.0)
    base = .45 if dirty else .16
    dens = g.m('MULTIPLY', g.m('MULTIPLY', radial, axial), g.m('MULTIPLY', n1, n2))
    dens = g.m('MULTIPLY', dens, g.m('MULTIPLY', amount, base))

    if dirty:
        soot = g.m('MULTIPLY', g.mr(g.noise(q, g.m('ADD', t, 3.1), 1.0 / (R * 1.8), 2, .5), .52, .78, smooth=True), .55)
        color = g.mix(soot, (.40, .39, .37, 1), (.07, .065, .06, 1))
        absorption = (.42, .38, .34, 1)
    else:
        color, absorption = (.78, .8, .83, 1), (.85, .86, .88, 1)
    # Mostly non-emissive: a faint warm glow only where it leaves the bell.
    glow = g.m('MULTIPLY', g.m('MULTIPLY', dens, g.m('POWER', g.m('SUBTRACT', 1.0, u, clamp=True), 18.0)), .5)
    volume_output(g, dens, color, absorption, .25, glow, (1.0, .52, .22, 1) if dirty else (.75, .82, 1.0, 1))

    ob = swfx_core.box_volume(f'{PREFIX} haze e{i}', coll, 0.0, 1.0, 1.0, 1.0, mat)
    _no_shadow_catch(ob)
    attach(ob, anchor.empty)
    ctx.drive(ob, 'scale', len_expr, ('smk_atmosphere',), index=0)
    for k in (1, 2):
        ctx.drive(ob, 'scale', width_expr, wind_props, index=k)
    return {'engine': i, 'mode': mode, 'object': ob.name, 'base_density': base}


# ---------------------------------------------------------------------------
# 2. landing dust
# ---------------------------------------------------------------------------
def build_dust(ctx, coll):
    hull = hull_object(ctx)
    lo, hi = ctx.anchors.bounds
    centre = (lo + hi) / 2
    hz0 = hull.matrix_world.translation.z if hull else 0.0
    low = lo.z - hz0                       # hull origin -> lowest point
    ctx.prop('smk_dust', 1.0, 0, 1, 'Landing dust amount (authored downwash; the hull has no ventral nozzles)')
    ctx.prop('smk_ground_z', -1000.0, -2000, 2000, 'World Z of the ground under the ship; far below = no ground, no dust')

    mat, nodes, links = node_material(f'{PREFIX} landing dust')
    g = G(mat.node_tree)
    hull_var = {}
    expr_h = f'(hz+{low:.3f}-smk_ground_z)'
    prox_expr = f'pow(max(0,min(1,1-{expr_h}/{DUST_REACH})),1.5)'
    prox = value_node(ctx, nodes, 'proximity', prox_expr, ('smk_ground_z',))
    env = value_node(ctx, nodes, 'envelope', f'smk_dust*(0.2+0.8*thr_throttle)*{prox_expr}',
                     ('smk_dust', 'thr_throttle', 'smk_ground_z'))
    for sock in (prox, env):
        _add_hull_z(sock.node.outputs[0], hull, hz0)
    t = time_node(ctx, g)

    p = g.node('ShaderNodeTexCoord').outputs['Object']
    x, y, z = g.sep(p)
    r = g.v('LENGTH', g.comb(x, y, 0.0))
    ring_pk = g.m('MULTIPLY_ADD', prox, 30.0, 26.0)
    ring_w = g.m('MULTIPLY_ADD', prox, 12.0, 13.0)
    ring = g.gauss(g.m('SUBTRACT', r, ring_pk), ring_w, 1.0)
    core = g.m('MULTIPLY', g.gauss(r, 24.0, 1.0), .3)
    edge = g.mr(r, DUST_RADIUS * .7, DUST_RADIUS * .97, 1.0, 0.0, smooth=True)
    radial = g.m('MULTIPLY', g.m('ADD', ring, core), edge)
    # Shallow: a thin carpet that rolls up a little at the ring front.
    hs = g.m('MULTIPLY_ADD', g.m('MULTIPLY', ring, prox), 4.5, 1.4)
    vert = g.m('MULTIPLY', g.m('EXPONENT', g.m('MULTIPLY', g.m('DIVIDE', z, hs), -1.0)),
               g.mr(z, DUST_HEIGHT * .55, DUST_HEIGHT * .95, 1.0, 0.0, smooth=True))
    # Outward flow: sample the noise at a radius that shrinks with time.
    rs = g.m('MAXIMUM', r, .5)
    f = g.m('DIVIDE', g.m('SUBTRACT', r, g.m('MULTIPLY', t, units(4.5))), rs)
    q = g.comb(g.m('MULTIPLY', x, f), g.m('MULTIPLY', y, f), g.m('MULTIPLY', z, 1.7))
    n1 = g.mr(g.noise(q, g.m('MULTIPLY', t, .09), 1 / 8.0, 4, .55), .36, .72, smooth=True)
    n2 = g.mr(g.noise(q, g.m('ADD', g.m('MULTIPLY', t, .2), 4.0), 1 / 2.4, 3, .6), .28, .78, .4, 1.0)
    dens = g.m('MULTIPLY', g.m('MULTIPLY', radial, vert), g.m('MULTIPLY', n1, n2))
    dens = g.m('MULTIPLY', dens, g.m('MULTIPLY', env, .8))
    # Jangala: warm grey-brown, slightly damp (darker, less bright scatter).
    color = g.mix(g.m('MULTIPLY', n2, .6), (.33, .27, .2, 1), (.42, .36, .28, 1))
    volume_output(g, dens, color, (.30, .24, .17, 1), .3)

    ob = box_mesh(f'{PREFIX} landing dust', coll, (-DUST_RADIUS, -DUST_RADIUS, 0.0),
                  (DUST_RADIUS, DUST_RADIUS, DUST_HEIGHT), mat)
    # The wide, low box otherwise prints a faint rectangular shadow on the
    # ground beyond the visible dust; thin ground dust needs no cast shadow.
    ob.visible_shadow = False
    if hull is not None:
        ob.location = (centre.x - hull.matrix_world.translation.x, centre.y - hull.matrix_world.translation.y, 0)
        c = ob.constraints.new('COPY_LOCATION')
        c.name = 'Follow hull XY'
        c.target = hull
        c.use_z = False
        c.use_offset = True
    else:
        ob.location = (centre.x, centre.y, 0)
    ctx.drive(ob, 'location', 'smk_ground_z', ('smk_ground_z',), index=2)
    return {'object': ob.name, 'follows': hull.name if hull else None, 'lowest_below_hull_origin': round(low, 3),
            'note': 'authored downwash: the hull has no ventral nozzles'}


def _add_hull_z(sock, hull, hz0):
    """Add a 'hz' variable (the hull's world Z) to the driver on sock."""
    fc = sock.id_data.animation_data.drivers.find(sock.path_from_id('default_value'))
    v = fc.driver.variables.new()
    v.name = 'hz'
    if hull is None:
        v.type = 'SINGLE_PROP'
        fc.driver.expression = fc.driver.expression.replace('hz', f'{hz0:.3f}')
        fc.driver.variables.remove(v)
        return
    v.type = 'TRANSFORMS'
    v.targets[0].id = hull
    v.targets[0].transform_type = 'LOC_Z'
    v.targets[0].transform_space = 'WORLD_SPACE'


# ---------------------------------------------------------------------------
# wisp volume (seal leaks and weld smoke)
# ---------------------------------------------------------------------------
def build_wisp(ctx, name, coll, location, normal, part, amount_expr, amount_props, seed=0,
               r0=None, length_vac=None, length_atm=None, color=(.9, .91, .93, 1), absorption=(.95, .95, .96, 1),
               base=18.0, intermittent=True, rise=1.0):
    """A small leak volume at location, along normal (+X of its frame).

    Vacuum: a faint straight jet, gone within length_vac. Atmosphere: a slower
    wisp that bends upward (rise) and lingers out to length_atm. intermittent
    wisps breathe in puffs that travel out of the source."""
    r0 = units(.08) if r0 is None else r0
    length_vac = units(1.6) if length_vac is None else length_vac
    length_atm = units(6.0) if length_atm is None else length_atm
    rng = random.Random(seed)
    mat, nodes, links = node_material(f'{name} material')
    g = G(mat.node_tree)
    frame = swfx_core_frame(normal)
    up_local = frame.transposed() @ Vector((0, 0, 1))
    up_perp = Vector((0, up_local.y, up_local.z))
    atm = value_node(ctx, nodes, 'atmosphere', 'smk_atmosphere', ('smk_atmosphere',))
    amount = value_node(ctx, nodes, 'amount', amount_expr, amount_props)
    t = time_node(ctx, g)

    p = g.node('ShaderNodeTexCoord').outputs['Object']
    x, y, z = g.sep(p)
    L = g.lerp(atm, length_vac, length_atm)
    u = g.m('DIVIDE', x, L)
    vel = g.lerp(atm, units(6.0), units(1.0))   # leak speed, vacuum jet vs drifting steam
    # Bend upward in atmosphere: lateral (local y/z) offset growing as
    # (x/length)^1.6, reaching rise*0.5 of the wisp's length at its end.
    bend = g.m('MULTIPLY', g.m('MULTIPLY', atm, rise * .5 * length_atm),
               g.m('POWER', g.m('MAXIMUM', g.m('DIVIDE', x, length_atm), 0.0), 1.6))
    ry = g.m('SUBTRACT', y, g.m('MULTIPLY', bend, up_perp.y))
    rz = g.m('SUBTRACT', z, g.m('MULTIPLY', bend, up_perp.z))
    rr = g.v('LENGTH', g.comb(ry, rz, 0.0))
    radius = g.m('MULTIPLY_ADD', x, g.lerp(atm, .42, .2), r0)
    radial = g.gauss(rr, radius, 2.2)
    fade = g.m('POWER', g.m('SUBTRACT', 1.0, u, clamp=True), g.lerp(atm, 2.4, 1.3))
    start = g.mr(x, -.02, r0 * 1.5, smooth=True)
    axial = g.m('MULTIPLY', fade, start)
    dens = g.m('MULTIPLY', radial, axial)
    if intermittent:
        tl = g.m('SUBTRACT', t, g.m('DIVIDE', x, vel))
        puff_params = (rng.uniform(.9, 1.5), rng.uniform(0, 6.28), rng.uniform(2.3, 3.4), rng.uniform(0, 6.28))
        a, p1, b, p2 = puff_params
        s = g.m('ADD', g.m('MULTIPLY', g.m('SINE', g.m('MULTIPLY_ADD', tl, a, p1)), .6),
                g.m('MULTIPLY', g.m('SINE', g.m('MULTIPLY_ADD', tl, b, p2)), .4))
        puff = g.mr(s, .05, .75, 0.0, 1.0, smooth=True)
        dens = g.m('MULTIPLY', dens, g.m('MULTIPLY_ADD', puff, .8, .2))
    adv = g.comb(g.m('MULTIPLY', t, vel), 0.0, 0.0)
    q = g.v('SUBTRACT', p, adv)
    n1 = g.mr(g.noise(q, g.m('ADD', g.m('MULTIPLY', t, .4), seed * 3.7), 1 / (r0 * 5 + .35), 4, .6), .3, .75, smooth=True)
    dens = g.m('MULTIPLY', g.m('MULTIPLY', dens, n1), g.m('MULTIPLY', amount, base))
    volume_output(g, dens, color, absorption, .35)

    reach = max(length_vac, length_atm)
    half = r0 + max(length_vac * .42, length_atm * .2) * 1.15 + rise * .5 * length_atm * max(abs(up_perp.y), abs(up_perp.z)) + r0
    ob = box_mesh(name, coll, (-.1, -half, -half), (reach, half, half), mat)
    if intermittent:
        ob['swfx_puff'] = list(puff_params)     # lets a shot find a puff (see puff_at)
    parent_keep_world(ob, part, Matrix.Translation(location) @ frame.to_4x4())
    return ob


def swfx_core_frame(normal):
    import swfx_anchors
    return swfx_anchors.frame(normal)


# ---------------------------------------------------------------------------
# 3. seal wisps
# ---------------------------------------------------------------------------
def _islands(ob):
    me = ob.data
    adj = {i: [] for i in range(len(me.vertices))}
    for e in me.edges:
        a, b = e.vertices
        adj[a].append(b)
        adj[b].append(a)
    seen, out = set(), []
    for s in range(len(me.vertices)):
        if s in seen:
            continue
        stack, comp = [s], []
        seen.add(s)
        while stack:
            v = stack.pop()
            comp.append(v)
            for w in adj[v]:
                if w not in seen:
                    seen.add(w)
                    stack.append(w)
        out.append([ob.matrix_world @ me.vertices[v].co for v in comp])
    return out


def cast(scene, origin, direction, distance=1e6):
    """scene.ray_cast that looks through the kit's own volume boxes."""
    depsgraph = bpy.context.evaluated_depsgraph_get()
    origin, direction = Vector(origin), Vector(direction).normalized()
    for _ in range(16):
        hit, loc, nrm, _i, ob, _m = scene.ray_cast(depsgraph, origin, direction, distance=distance)
        if not hit:
            return False, None, None, None
        if ob is not None and ob.name.startswith('SWFX'):
            origin = loc + direction * .01
            continue
        return True, loc, nrm.normalized(), ob
    return False, None, None, None


def free_view(scene, target, dist, elevation=16.0, prefer=Vector((0, -1, 0))):
    """A camera position dist from target, at the given elevation, along the
    clear line of sight nearest the preferred horizontal direction."""
    best = None
    for k in range(36):
        az = math.atan2(prefer.y, prefer.x) + (1 if k % 2 else -1) * math.radians(10 * ((k + 1) // 2))
        for el in (elevation, elevation + 12, elevation + 26, elevation + 45):
            d = Vector((math.cos(math.radians(el)) * math.cos(az), math.cos(math.radians(el)) * math.sin(az),
                        math.sin(math.radians(el))))
            hit, *_ = cast(scene, target + d * .2, d, dist + 2)
            if not hit:
                return target + d * dist
            best = best or target + d * dist
    return best


def _openness(scene, site, ignore=()):
    """Outward direction at site: the mean of directions with clear sky."""
    depsgraph = bpy.context.evaluated_depsgraph_get()
    golden = math.pi * (3 - math.sqrt(5))
    acc, free = Vector(), 0
    n = 64
    for k in range(n):
        zc = 1 - 2 * (k + .5) / n
        rad = math.sqrt(1 - zc * zc)
        d = Vector((math.cos(golden * k) * rad, math.sin(golden * k) * rad, zc))
        origin = site + d * .35
        clear = True
        for _ in range(6):
            hit, loc, _nrm, _i, ob, _m = scene.ray_cast(depsgraph, origin, d, distance=30.0)
            if not hit:
                break
            if ob is not None and (ob.name.startswith('SWFX') or ob.name in ignore):
                origin = loc + d * .01
                continue
            clear = False
            break
        if clear:
            acc += d
            free += 1
    return acc.normalized() if free else Vector((0, 0, 1)), free / n


def seal_sites(ctx, count=3, seed=7):
    """2-4 leak sites on the borrowed seals / pressure patches: cluster the
    loose islands, spread the choice, put the leak on a seal rim."""
    clusters = []
    for base in SEAL_PARTS:
        for ob in _seal_parts(base):
            for pts in _islands(ob):
                c = sum(pts, Vector()) / len(pts)
                for cl in clusters:
                    if (cl['centre'] - c).length < 2.5:
                        cl['pts'] += pts
                        cl['kinds'].add(base)
                        cl['n'] += 1
                        break
                else:
                    clusters.append({'centre': c, 'pts': list(pts), 'part': ob, 'kinds': {base}, 'n': 1})
    if not clusters:
        return []
    for cl in clusters:
        cl['centre'] = sum(cl['pts'], Vector()) / len(cl['pts'])
    # The borrowed hab-door seals on the access trunks first (the canon
    # "borrowed seals"), then spread over the rest by farthest-point choice.
    clusters.sort(key=lambda cl: (-(cl['n'] >= 4),
                                  cl['centre'].x, cl['centre'].y))
    chosen = [clusters[0]]
    while len(chosen) < min(count, len(clusters)):
        best = max((cl for cl in clusters if cl not in chosen),
                   key=lambda cl: min((cl['centre'] - c['centre']).length for c in chosen))
        chosen.append(best)
    rng = random.Random(seed)
    sites = []
    for k, cl in enumerate(chosen):
        normal, open_frac = _openness(ctx.scene, cl['centre'])
        # A rim point: the vertex farthest along a seeded direction across the seal.
        side = normal.orthogonal().normalized()
        side.rotate(Matrix.Rotation(rng.uniform(0, 2 * math.pi), 3, normal))
        rim = max(cl['pts'], key=lambda p: (p - cl['centre']).dot(side))
        rim_normal, _ = _openness(ctx.scene, rim)
        sites.append({'location': rim + rim_normal * .04, 'normal': rim_normal, 'part': cl['part'],
                      'centre': cl['centre'], 'open': open_frac})
    return sites


def puff_at(ob, frame):
    """0..1: how strongly an intermittent wisp is puffing at its source."""
    a, p1, b, p2 = ob['swfx_puff']
    t = frame / FPS
    s = .6 * math.sin(a * t + p1) + .4 * math.sin(b * t + p2)
    f = min(1, max(0, (s - .05) / .7))
    return f * f * (3 - 2 * f)


def build_seal_wisps(ctx, coll, count=3):
    ctx.prop('smk_seal_wisp', .3, 0, 1, 'Seal-leak wisps (patched/refit): minor and intermittent by canon')
    out = []
    for k, s in enumerate(seal_sites(ctx, count)):
        ob = build_wisp(ctx, f'{PREFIX} seal wisp {k + 1}', coll, s['location'], s['normal'], s['part'],
                        'smk_seal_wisp', ('smk_seal_wisp',), seed=11 + k * 5)
        out.append({'object': ob.name, 'part': s['part'].name, 'at': [round(v, 2) for v in s['location']],
                    'normal': [round(v, 2) for v in s['normal']]})
    return out


# ---------------------------------------------------------------------------
# 4. welds
# ---------------------------------------------------------------------------
def _hash_expr(seed):
    return f'(sin(frame*12.9898+{seed * 78.233:.3f})*43758.5453-floor(sin(frame*12.9898+{seed * 78.233:.3f})*43758.5453))'


def _sock(node, name, out=False):
    socks = node.outputs if out else node.inputs
    return next(s for s in socks if s.name == name and s.enabled)


def _spark_tree(ctx, name, normal, start, end, seed, count=150, life=14.0):
    tree = bpy.data.node_groups.new(name, 'GeometryNodeTree')
    tree.interface.new_socket('Geometry', in_out='INPUT', socket_type='NodeSocketGeometry')
    tree.interface.new_socket('Geometry', in_out='OUTPUT', socket_type='NodeSocketGeometry')
    g = G(tree)
    gin, gout = g.node('NodeGroupInput'), g.node('NodeGroupOutput')

    def rnd(kind, lo, hi, ident, s):
        n = g.node('FunctionNodeRandomValue', data_type=kind)
        g._in(_sock(n, 'Min'), lo)
        g._in(_sock(n, 'Max'), hi)
        g._in(_sock(n, 'ID'), ident)
        _sock(n, 'Seed').default_value = s
        return _sock(n, 'Value', out=True)

    atm = value_node(ctx, g.nodes, 'atmosphere', 'smk_atmosphere', ('smk_atmosphere',))
    amount = value_node(ctx, g.nodes, 'amount', 'smk_weld', ('smk_weld',))
    idx = g.node('GeometryNodeInputIndex').outputs[0]
    T = g.node('GeometryNodeInputSceneTime').outputs['Frame']
    raw = g.m('SUBTRACT', g.m('SUBTRACT', T, float(start)), g.m('MULTIPLY', idx, life / count))
    cyc = g.m('FLOOR', g.m('DIVIDE', raw, life))
    age = g.m('SUBTRACT', raw, g.m('MULTIPLY', cyc, life))
    ident = g.m('ADD', idx, g.m('MULTIPLY', cyc, float(count)))
    life_i = g.m('MULTIPLY', rnd('FLOAT', .35, 1.0, ident, seed + 1), life)
    alive = g.m('MULTIPLY', g.m('MULTIPLY', g.m('GREATER_THAN', raw, -.001), g.m('LESS_THAN', T, float(end))),
                g.m('MULTIPLY', g.m('LESS_THAN', age, life_i), g.m('GREATER_THAN', amount, .001)))
    secs = g.m('DIVIDE', age, float(FPS))
    rv = rnd('FLOAT_VECTOR', (-1, -1, -1), (1, 1, 1), ident, seed)
    direction = g.v('NORMALIZE', g.v('ADD', rv, tuple(Vector(normal).normalized() * 1.25)))
    speed = g.m('MULTIPLY', rnd('FLOAT', units(1.5), units(7.0), ident, seed + 2),
                g.m('POWER', rnd('FLOAT', 0.0, 1.0, ident, seed + 3), .6))
    drag = g.m('MULTIPLY_ADD', atm, 2.4, .05)                     # 1/s
    decay = g.m('EXPONENT', g.m('MULTIPLY', g.m('MULTIPLY', drag, secs), -1.0))
    dist = g.m('DIVIDE', g.m('MULTIPLY', speed, g.m('SUBTRACT', 1.0, decay)), drag)
    grav = g.m('MULTIPLY', atm, -units(9.81))
    pos = g.v('ADD', g.v('SCALE', direction, scale=dist),
              g.comb(0.0, 0.0, g.m('MULTIPLY', g.m('MULTIPLY', grav, .5), g.m('MULTIPLY', secs, secs))))
    vel = g.v('ADD', g.v('SCALE', direction, scale=g.m('MULTIPLY', speed, decay)),
              g.comb(0.0, 0.0, g.m('MULTIPLY', grav, secs)))
    heat = g.m('SUBTRACT', 1.0, g.m('DIVIDE', age, life_i), clamp=True)

    pts = g.node('GeometryNodePoints')
    pts.inputs['Count'].default_value = count
    g.links.new(pos, pts.inputs['Position'])
    geo = pts.outputs[0]
    for attr, kind, value in (('swfx_heat', 'FLOAT', heat), ('swfx_vel', 'FLOAT_VECTOR', vel),
                              ('swfx_alive', 'FLOAT', alive)):
        st = g.node('GeometryNodeStoreNamedAttribute', data_type=kind, domain='POINT')
        g.links.new(geo, st.inputs['Geometry'])
        st.inputs['Name'].default_value = attr
        g.links.new(value, _sock(st, 'Value'))
        geo = st.outputs[0]

    def named(attr, kind):
        n = g.node('GeometryNodeInputNamedAttribute', data_type=kind)
        n.inputs['Name'].default_value = attr
        return _sock(n, 'Attribute', out=True)

    dele = g.node('GeometryNodeDeleteGeometry', domain='POINT')
    g.links.new(geo, dele.inputs['Geometry'])
    g.links.new(g.m('LESS_THAN', named('swfx_alive', 'FLOAT'), .5), dele.inputs['Selection'])
    cube = g.node('GeometryNodeMeshCube')
    cube.inputs['Size'].default_value = (1, 1, 1)
    inst = g.node('GeometryNodeInstanceOnPoints')
    g.links.new(dele.outputs[0], inst.inputs['Points'])
    g.links.new(cube.outputs['Mesh'], inst.inputs['Instance'])
    v = named('swfx_vel', 'FLOAT_VECTOR')
    align = g.node('FunctionNodeAlignRotationToVector', axis='X')
    g.links.new(v, align.inputs['Vector'])
    g.links.new(align.outputs[0], inst.inputs['Rotation'])
    h = named('swfx_heat', 'FLOAT')
    # Streak = about 1.2 frames of travel (stands in for motion blur).
    length = g.m('MULTIPLY_ADD', g.v('LENGTH', v), 2.0 / FPS, units(.02))
    width = g.m('MULTIPLY_ADD', h, units(.005), units(.0025))
    g.links.new(g.comb(length, width, width), inst.inputs['Scale'])
    real = g.node('GeometryNodeRealizeInstances')
    g.links.new(inst.outputs[0], real.inputs['Geometry'])
    sm = g.node('GeometryNodeSetMaterial')
    g.links.new(real.outputs[0], sm.inputs['Geometry'])
    sm.inputs['Material'].default_value = _spark_material(ctx)
    g.links.new(sm.outputs[0], gout.inputs[0])
    return tree


def _spark_material(ctx):
    name = f'{PREFIX} weld spark'
    m = bpy.data.materials.get(name)
    if m:
        return m
    m, nodes, links = node_material(name)
    g = G(m.node_tree)
    attr = g.node('ShaderNodeAttribute', attribute_name='swfx_heat', attribute_type='GEOMETRY')
    heat = attr.outputs['Fac']
    color = g.mix(heat, (1.0, .22, .03, 1), (1.0, .82, .5, 1))
    em = g.node('ShaderNodeEmission')
    g._in(em.inputs['Color'], color)
    amt = value_node(ctx, nodes, 'amount', 'smk_weld', ('smk_weld',))
    g._in(em.inputs['Strength'], g.m('MULTIPLY', g.m('MULTIPLY', g.m('POWER', heat, 1.5), 60.0), amt))
    out = g.node('ShaderNodeOutputMaterial')
    g.links.new(em.outputs[0], out.inputs['Surface'])
    return m


def add_weld(ctx, location, normal, start_frame, end_frame, part=None, seed=0, light_energy=160.0):
    """A repair weld at a world location on the hull: an arc light with
    flicker, a glowing arc point, a small rising smoke wisp and a spark
    shower (ballistic with drag in atmosphere, straight in vacuum).
    Active from start_frame to end_frame; smoke lingers ~2 s after.
    Parented to part (keeping world placement) when given. Returns a record."""
    ctx.prop('smk_weld', 1.0, 0, 1, 'Weld intensity (light, arc, sparks, smoke) for add_weld welds')
    shared_knobs(ctx)
    coll = ctx.collection(GROUP)
    k = sum(1 for o in coll.objects if o.name.startswith(f'{PREFIX} weld ') and o.type == 'EMPTY') + 1
    base = f'{PREFIX} weld {k}'
    location, normal = Vector(location), Vector(normal).normalized()
    s, e = float(start_frame), float(end_frame)
    frame = swfx_core_frame(normal)

    root = swfx_core.empty(base, coll, .5, 'ARROWS')
    parent_keep_world(root, part, Matrix.Translation(location) @ frame.to_4x4())
    on = f'min(1,max(0,frame-{s}+1))*min(1,max(0,{e}-frame))'
    flicker = f'(0.35+0.65*{_hash_expr(seed + 1)})'

    light = swfx_core.point_light(f'{base} arc light', coll, (.72, .82, 1.0), 0.0, units(.03))
    attach(light, root, (units(.12), 0, 0))
    ctx.drive(light.data, 'energy', f'smk_weld*{light_energy}*{on}*{flicker}', ('smk_weld',))
    warm = swfx_core.point_light(f'{base} spark light', coll, (1.0, .55, .2), 0.0, units(.2))
    attach(warm, root, (units(.3), 0, 0))
    ctx.drive(warm.data, 'energy', f'smk_weld*{light_energy * .12}*{on}', ('smk_weld',))

    # Arc point: a small, very bright emitter.
    me = bpy.data.meshes.new(f'{base} arc')
    import bmesh
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=2, radius=units(.022))
    bm.to_mesh(me)
    bm.free()
    arc = bpy.data.objects.new(f'{base} arc', me)
    coll.objects.link(arc)
    attach(arc, root, (units(.02), 0, 0))
    am, nodes, links = node_material(f'{base} arc material')
    g = G(am.node_tree)
    em = g.node('ShaderNodeEmission')
    em.inputs['Color'].default_value = (.8, .88, 1.0, 1)
    g._in(em.inputs['Strength'], value_node(ctx, nodes, 'strength', f'smk_weld*400*{on}*{flicker}', ('smk_weld',)))
    g.links.new(em.outputs[0], g.node('ShaderNodeOutputMaterial').inputs['Surface'])
    me.materials.append(am)
    arc.visible_shadow = False

    smoke = build_wisp(ctx, f'{base} smoke', coll, location + normal * units(.03), normal, part,
                       f'smk_weld*min(1,max(0,(frame-{s})/10))*min(1,max(0,1-(frame-{e})/48))', ('smk_weld',),
                       seed=seed + 31, r0=units(.05), length_vac=units(1.0), length_atm=units(3.5),
                       color=(.42, .41, .4, 1), absorption=(.5, .48, .46, 1), base=9.0, intermittent=False, rise=1.4)

    sm = bpy.data.meshes.new(f'{base} sparks')
    sparks = bpy.data.objects.new(f'{base} sparks', sm)
    coll.objects.link(sparks)
    parent_keep_world(sparks, part, Matrix.Translation(location + normal * units(.02)))
    mod = sparks.modifiers.new('SWFX sparks', 'NODES')
    mod.node_group = _spark_tree(ctx, f'{base} sparks', normal, s, e, seed=seed * 17 + 3)
    sparks.visible_shadow = False
    refresh_controls(ctx)
    return {'weld': base, 'location': [round(v, 3) for v in location], 'normal': [round(v, 3) for v in normal],
            'frames': [s, e], 'part': part.name if part else None,
            'objects': [root.name, light.name, warm.name, arc.name, smoke.name, sparks.name]}


# ---------------------------------------------------------------------------
# module contract
# ---------------------------------------------------------------------------
def build(ctx, haze=True, dust=True, seal_wisps=True, wisp_count=3, welds=(), **options):
    coll = ctx.collection(GROUP)
    shared_knobs(ctx)
    ctx.prop('smk_haze', 1.0, 0, 1, 'Exhaust haze amount behind running engines')
    rec = {'state': ctx.state, 'haze': [], 'dust': None, 'seal_wisps': [], 'welds': []}
    powered = ctx.spec.get('powered', False)
    if haze and powered:
        for a in ctx.anchors.engines:
            mode = ctx.engine_mode(a.index)
            if mode in RUNNING:
                rec['haze'].append(build_haze(ctx, coll, a, mode))
    if dust and powered:
        rec['dust'] = build_dust(ctx, coll)
    if seal_wisps and ctx.spec.get('seal_wisps'):
        rec['seal_wisps'] = build_seal_wisps(ctx, coll, max(2, min(4, wisp_count)))
    for w in welds or ():
        w = dict(w)
        part = w.pop('part', None)
        if isinstance(part, str):
            part = bpy.data.objects.get(part)
        rec['welds'].append(add_weld(ctx, w.pop('location'), w.pop('normal'), w.pop('start_frame', 1),
                                     w.pop('end_frame', 96), part=part, **w))
    refresh_controls(ctx)
    return rec


# ---------------------------------------------------------------------------
# review
# ---------------------------------------------------------------------------
def _daylight(scene, coll, elevation=38.0, azimuth=-60.0, sun_strength=4.0, sky_strength=.12):
    world = swfx_core.dark_world(scene)
    nt = world.node_tree
    bg = nt.nodes.get('Background')
    sky = nt.nodes.get('SWFX smoke sky') or nt.nodes.new('ShaderNodeTexSky')
    sky.name = 'SWFX smoke sky'
    try:
        sky.sky_type = 'MULTIPLE_SCATTERING'
        sky.sun_disc = False
        sky.sun_elevation = math.radians(elevation)
        sky.sun_rotation = math.radians(azimuth)
        sky.altitude = 200
    except Exception:
        pass
    nt.links.new(sky.outputs[0], bg.inputs[0])
    bg.inputs[1].default_value = sky_strength
    sun = bpy.data.objects.get(f'{PREFIX} review sun')
    if sun is None:
        data = bpy.data.lights.new(f'{PREFIX} review sun', 'SUN')
        sun = bpy.data.objects.new(f'{PREFIX} review sun', data)
        coll.objects.link(sun)
    sun.data.energy = sun_strength
    sun.data.angle = math.radians(1.2)
    sun.data.color = (1.0, .95, .88)
    # The sun is seen along d; its light travels along -d (a sun lamp shines down its -Z).
    el, az = math.radians(elevation), math.radians(azimuth)
    d = Vector((math.cos(el) * math.cos(az), math.cos(el) * math.sin(az), math.sin(el)))
    sun.rotation_euler = (-d).to_track_quat('-Z', 'Y').to_euler()
    return sun


def _ground(coll, z, size=1200):
    name = f'{PREFIX} review ground'
    ob = bpy.data.objects.get(name)
    if ob is None:
        me = bpy.data.meshes.new(name)
        s = size / 2
        me.from_pydata([(-s, -s, 0), (s, -s, 0), (s, s, 0), (-s, s, 0)], [], [(0, 1, 2, 3)])
        ob = bpy.data.objects.new(name, me)
        coll.objects.link(ob)
        m, nodes, links = node_material(f'{name} material')
        g = G(m.node_tree)
        tc = g.node('ShaderNodeTexCoord').outputs['Object']
        n = g.noise(tc, 0.0, 1 / 9.0, 6, .6)
        n2 = g.noise(tc, 3.0, 1 / 60.0, 3, .5)
        col = g.mix(g.m('MULTIPLY', n, n2), (.06, .07, .035, 1), (.16, .15, .08, 1))
        col = g.mix(g.mr(n2, .55, .7, smooth=True), col, (.12, .15, .07, 1))  # damp green tinge
        bsdf = g.node('ShaderNodeBsdfPrincipled')
        g._in(bsdf.inputs['Base Color'], col)
        bsdf.inputs['Roughness'].default_value = .85
        g.links.new(bsdf.outputs[0], g.node('ShaderNodeOutputMaterial').inputs['Surface'])
        me.materials.append(m)
    ob.location.z = z
    ob.hide_render = False
    return ob


def _smoke_objects():
    coll = bpy.data.collections.get(f'{swfx_core.ROOT} | {GROUP}')
    return list(coll.objects) if coll else []


def _show_only(prefixes):
    for ob in _smoke_objects():
        ob.hide_render = not any(ob.name.startswith(p) for p in prefixes)


def _render(scene, cam, path):
    scene.camera = cam
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)


def _set(ctx, **knobs):
    for k, v in knobs.items():
        ctx.controls[k] = float(v)
    ctx.controls.update_tag()


def review(ctx, out_dir, quick=False):
    from pathlib import Path
    import shutil
    import time
    import swfx_anchors

    scene = ctx.scene
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    review_dir = Path(__file__).resolve().parent / 'review' / 'smoke'
    if not quick:
        review_dir.mkdir(parents=True, exist_ok=True)
    rc = ctx.collection(f'{GROUP} review')
    swfx_core.hide_reference_lights(scene)
    swfx_core.setup_render(scene, samples=12 if quick else 48)
    scene.cycles.volume_bounces = 1
    sun = _daylight(scene, rc)
    lo, hi = ctx.anchors.bounds
    times = {}

    def still(name, cam):
        t0 = time.time()
        path = out_dir / f'{name}.png'
        _render(scene, cam, path)
        times[name] = round(time.time() - t0, 1)
        print(f'[smoke review] {name}: {times[name]} s')
        if not quick:
            shutil.copy(path, review_dir / f'{name}.png')

    import os
    shots = os.environ.get('SWFX_SMOKE_SHOTS', 'a,b,c,d').split(',')   # iteration aid
    scene.frame_set(36)
    # (a) patched exhaust haze behind the starboard pod, in atmosphere, light crosswind.
    if 'a' in shots and ctx.records.get('smoke', {}).get('haze'):
        _show_only((f'{PREFIX} haze',))
        _set(ctx, smk_atmosphere=1, smk_haze=1, smk_wind_y=-.25, smk_wind_z=.12, smk_ground_z=-1000)
        g = _ground(rc, lo.z - 60)
        cam = swfx_core.look_camera(f'{PREFIX} review cam haze', rc, (-40, -205, 26), (-66, -18, -4), lens=28)
        still('a-haze-starboard-atmosphere', cam)
        _set(ctx, smk_atmosphere=0, smk_wind_y=0, smk_wind_z=0)
        still('a2-haze-starboard-vacuum', cam)
        g.hide_render = True

    # (c) a seal wisp close-up.
    wisps = ctx.records.get('smoke', {}).get('seal_wisps', [])
    if wisps and 'c' in shots:
        _show_only((f'{PREFIX} seal wisp',))
        w = wisps[0]
        at, n = Vector(w['at']), Vector(w['normal'])
        # From outboard and a little above the roof, nearly level, so the
        # wisp rises against the sky and the hull behind it.
        eye = free_view(scene, at + n * .6, 7.0, elevation=14.0, prefer=Vector((0, 1 if at.y >= 0 else -1, 0)))
        cam = swfx_core.look_camera(f'{PREFIX} review cam seal', rc, eye, at + n * 1.3, lens=40)
        wob = bpy.data.objects[w['object']]
        best = max(range(24, 240), key=lambda f: puff_at(wob, f - 18))
        scene.frame_set(best)
        _set(ctx, smk_atmosphere=1, smk_seal_wisp=.3)
        still('c-seal-wisp-atmosphere', cam)
        best = max(range(24, 240), key=lambda f: puff_at(wob, f - 3))
        scene.frame_set(best)
        _set(ctx, smk_atmosphere=0)
        still('c2-seal-wisp-vacuum', cam)
        scene.frame_set(36)

    # (d) a weld close-up on the hull (added here; build() adds none by default).
    hull = hull_object(ctx)
    site = Vector((22.0, -10.5, 0.0))
    hit, loc, nrm, ob = cast(scene, Vector((site.x, site.y, 60)), Vector((0, 0, -1)))
    if hit and 'd' in shots:
        rec = add_weld(ctx, loc, nrm, 1, 400, part=ob, seed=4)
        _show_only((rec['weld'],))
        _set(ctx, smk_atmosphere=1, smk_weld=1)
        side = nrm.cross(Vector((1, 0, 0))).normalized()
        eye = loc + nrm * 3.2 + side * 4.0 + Vector((-3.0, 0, 0))
        cam = swfx_core.look_camera(f'{PREFIX} review cam weld', rc, eye, loc + nrm * .4, lens=45)
        scene.frame_set(40)
        sun.data.energy = 2.0
        still('d-weld-sparks-atmosphere', cam)
        _set(ctx, smk_atmosphere=0)
        still('d2-weld-sparks-vacuum', cam)
        sun.data.energy = 4.5
        ctx.records.setdefault('smoke', {})['review_weld'] = rec

    # (b) landing dust: the ship lowers onto a ground plane, short animation.
    if 'b' in shots and ctx.records.get('smoke', {}).get('dust'):
        _show_only((f'{PREFIX} landing dust', f'{PREFIX} haze'))
        root = swfx_anchors.make_ship_root()
        ground_z = lo.z
        g = _ground(rc, ground_z)
        _set(ctx, smk_atmosphere=1, smk_dust=1, smk_haze=1, smk_ground_z=ground_z, smk_wind_y=-.1)
        n = 12 if quick else 64
        f0, f_contact, f_end = 1, 40, n
        root.location.z = 34
        root.keyframe_insert('location', index=2, frame=f0)
        root.location.z = 0
        root.keyframe_insert('location', index=2, frame=f_contact)
        fc = swfx_core.find_fcurve(root, 'location', 2)
        if fc:
            for kp in fc.keyframe_points:
                kp.interpolation = 'BEZIER'
                kp.easing = 'EASE_OUT'
        ctx.key('thr_throttle', f0, 1.0)
        ctx.key('thr_throttle', f_contact, .9)
        ctx.key('thr_throttle', f_contact + 16, 0.0)
        cam = swfx_core.look_camera(f'{PREFIX} review cam landing', rc, (60, -205, ground_z + 16), (-5, 0, ground_z + 12),
                                    lens=30)
        scene.camera = cam
        fdir = out_dir / 'landing'
        fdir.mkdir(exist_ok=True)
        frames = range(1, n + 1) if not quick else (20, 40, 60)
        t0 = time.time()
        scene.cycles.samples = 12 if quick else 24
        for f in frames:
            scene.frame_set(f)
            scene.render.filepath = str(fdir / f'frame_{f:04d}.png')
            bpy.ops.render.render(write_still=True)
        times['landing per frame'] = round((time.time() - t0) / len(frames), 1)
        if not quick:
            swfx_core.encode_movie(fdir / 'frame_%04d.png', review_dir / 'b-landing-dust.mp4')
            for f, tag in ((20, 'approach'), (f_contact, 'contact'), (f_contact + 20, 'settling')):
                shutil.copy(fdir / f'frame_{f:04d}.png', review_dir / f'b-landing-dust-{tag}.png')
    print('[smoke review] times', times)
    ctx.records.setdefault('smoke', {})['review_times'] = times
