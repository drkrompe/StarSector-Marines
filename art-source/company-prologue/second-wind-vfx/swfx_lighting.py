"""Lighting for the Second Wind: rig presets and the ship's own lights.

Two independent halves:

* **Ship lights** (`build(ctx)`, always): the ship's running, beacon, work,
  davit, masthead and drive-warning lamps as real light objects parented to
  their lamp anchors, with the geometry's emissive lamp materials driven from
  the same knobs, so the visible lens and the light it throws never disagree.
  A lamp whose mesh carries the damage layer's 'Dead lamp' material gets no
  light (the patched port pod's warning lamps; everything on the derelict),
  and an unpowered state builds nothing that emits.
* **Rig presets** (`apply_preset(ctx, name)` or `build(ctx, preset=name)`):
  world, sun/sky, haze and environment lights for one look. A preset owns
  everything in 'SWFX | Lighting rig' and the scene world; applying another
  preset replaces it. Presets hide the geometry build's 'Key'/'Fill'.

Coordinates are hull-local world units (+X bow, +Y port, +Z dorsal). Sun
azimuth is measured in the world XY plane from +X (bow) toward +Y (port);
elevation above the XY plane. Engine glow lights belong to the thrusters
module and are not made here.

Knobs (all on 'SWFX controls'; see PRESET_KNOBS and SHIP_KNOBS for ranges):
ship lights   lit_ship_master, lit_running, lit_beacon, lit_beacon_period,
              lit_work, lit_davit, lit_drive_warning, lit_masthead
rig           lit_exposure, lit_sun, lit_sun_elevation, lit_sun_azimuth,
              lit_sky, lit_rim, lit_haze, lit_stars, lit_star_glare,
              lit_berth, lit_berth_warmth, lit_floor_lamps, lit_fog
"""
import math
import os
from pathlib import Path

import bpy
from mathutils import Vector

import swfx_core
from swfx_core import node_material, math_node, units

GROUP = 'Lighting'
RIG = 'Lighting rig'
REVIEW = 'Lighting review'
PREFIX = 'SWFX lighting'
HERE = Path(__file__).resolve().parent
DEAD_LAMP = 'Dead lamp'


def watts(metre_watts):
    """A lamp's real-world power -> Blender energy in this hull-unit scene.
    Blender's falloff counts scene units as metres, so a lamp that should
    light the hull as it would at 0.65 m per unit needs energy / mpu^2."""
    return metre_watts / swfx_core.METRES_PER_UNIT ** 2

# --- ship lamps ----------------------------------------------------------
# role -> (knob, light kind, real power W at knob 1, emitter radius m, spot size deg)
LAMP_LIGHTS = {
    'running-port':      ('lit_running', 'POINT', 13.0, .1, None),
    'running-starboard': ('lit_running', 'POINT', 13.0, .1, None),
    'masthead':          ('lit_masthead', 'POINT', 17.0, .1, None),
    'beacon':            ('lit_beacon', 'POINT', 250.0, .13, None),
    'work':              ('lit_work', 'SPOT', 9000.0, .22, 75.0),
    'davit':             ('lit_davit', 'POINT', 10.0, .08, None),
    'drive-warning':     ('lit_drive_warning', 'POINT', 15.0, .1, None),
}
SHIP_KNOBS = {
    # name: (default, lo, hi, description)
    'lit_ship_master': (1.0, 0.0, 4.0, 'Master multiplier on every ship lamp (lights and lenses)'),
    'lit_running': (1.0, 0.0, 4.0, 'Red/green running and crosstree signal lights'),
    'lit_masthead': (1.0, 0.0, 4.0, 'White masthead light (clean/restored mast only)'),
    'lit_beacon': (1.0, 0.0, 4.0, 'Amber roof beacon peak; it blinks on frame'),
    'lit_beacon_period': (36.0, 6.0, 240.0, 'Beacon flash period in frames (36 = 40 flashes/min at 24 fps)'),
    'lit_work': (1.0, 0.0, 4.0, 'Amber grapple-sponson and bow work floods'),
    'lit_davit': (1.0, 0.0, 4.0, 'Green davit position light'),
    'lit_drive_warning': (1.0, 0.0, 4.0, 'Red drive warning lamps on the engine pods'),
}
# Lens material -> knob that drives its Emission Strength.
LENS_KNOB = {
    'Port running light': 'lit_running',
    'Starboard running light': 'lit_running',
    'Masthead light': 'lit_masthead',
    'Mast beacon': 'lit_beacon',
    'Port arm work light amber': 'lit_work',
    'Davit position light green': 'lit_davit',
    'Drive warning light': 'lit_drive_warning',
}


def beacon_wave(offset=0.0):
    """Deterministic flash on frame: 2-frame rise, ~3 held, 5-frame decay
    (an amber LED strobe with a little thermal tail). Simple-expression safe,
    so it evaluates without auto-run scripts."""
    ph = f'fmod(frame + {offset:g}, max(lit_beacon_period, 1))'
    return f'(smoothstep(0, 2, {ph}) - smoothstep(5, 10, {ph}))'


def lamp_expr(knob, role):
    e = f'{knob} * lit_ship_master'
    if role == 'beacon':
        e += ' * ' + beacon_wave()
    return e


def lamp_props(knob, role):
    p = [knob, 'lit_ship_master']
    if role == 'beacon':
        p.append('lit_beacon_period')
    return p


def facing(part, at, centre):
    """Outward direction of a lamp lens: its thinnest axis (signed away from
    the hull centre), or up for a near-cubic lamp."""
    d = part.dimensions
    dims = [d.x, d.y, d.z]
    i = min(range(3), key=lambda k: dims[k])
    if dims[i] > .6 * max(dims):
        return Vector((0, 0, 1))
    v = Vector((0, 0, 0))
    v[i] = 1.0 if (at[i] - centre[i]) >= 0 else -1.0
    return v


def work_aim(at, out):
    """A work flood looks outboard and ~40 deg down onto the flank; one that
    faces the grapple head (the bow sponson lamp) looks at the jaws."""
    head = bpy.data.objects.get('Grapple head body')
    if head is not None:
        to = head.matrix_world.translation - at
        if to.length < units(14) and to.normalized().dot(out) > .3:
            return to.normalized()
    return (out + Vector((0, 0, -.85))).normalized()


def lamp_material(part):
    return part.material_slots[0].material if part.material_slots and part.material_slots[0].material else None


def build_ship_lights(ctx):
    coll = ctx.collection(GROUP)
    powered = ctx.spec['powered']
    for name, (default, lo, hi, desc) in SHIP_KNOBS.items():
        ctx.prop(name, default, lo, hi, desc)
    lo, hi = ctx.anchors.bounds
    centre = (lo + hi) / 2
    record = {'powered': powered, 'lights': [], 'dark': [], 'lenses': {}}

    # Lens emission, per shared material, from the same knob as the lights.
    for mname, knob in LENS_KNOB.items():
        m = bpy.data.materials.get(mname)
        if m is None or not m.use_nodes:
            continue
        bsdf = next((n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED'), None)
        if bsdf is None:
            continue
        sock = bsdf.inputs['Emission Strength']
        base = m.get('swfx_base_emission', sock.default_value)
        m['swfx_base_emission'] = base
        try:
            sock.driver_remove('default_value')
        except Exception:
            pass
        if not powered:
            sock.default_value = 0.0
            record['lenses'][mname] = 0.0
            continue
        role = 'beacon' if knob == 'lit_beacon' else knob
        ctx.drive(sock, 'default_value', f'{base:g} * ' + lamp_expr(knob, role), lamp_props(knob, role))
        record['lenses'][mname] = f'{base:g} x {knob}'

    for lamp in ctx.anchors.lamps:
        spec = LAMP_LIGHTS.get(lamp.role)
        mat = lamp_material(lamp.part)
        if spec is None or not powered or mat is None or mat.name == DEAD_LAMP:
            record['dark'].append(lamp.name)
            continue
        knob, kind, energy, radius, spot = spec
        energy, radius = watts(energy), units(radius)
        at = lamp.empty.matrix_world.translation.copy()
        out = facing(lamp.part, at, centre)
        thick = min(lamp.part.dimensions) if kind == 'POINT' else min(lamp.part.dimensions)
        offset = out * (thick * .5 + radius + units(.05))
        data = bpy.data.lights.new(f'{PREFIX} {lamp.name}', kind)
        data.color = tuple(lamp.color[:3])
        data.shadow_soft_size = radius
        data.energy = energy
        if kind == 'SPOT':
            data.spot_size = math.radians(spot)
            data.spot_blend = .55
            data.show_cone = True
        ob = bpy.data.objects.new(f'{PREFIX} {lamp.name}', data)
        coll.objects.link(ob)
        ob.visible_camera = False  # the lens mesh is what the camera sees
        rot = (0, 0, 0)
        if kind == 'SPOT':
            aim = work_aim(at, out)
            rot = aim.to_track_quat('-Z', 'Y').to_euler()
        # Anchors carry world orientation at build time (frame(+X)), so a
        # world offset is the anchor-local offset.
        rel = lamp.empty.matrix_world.to_3x3().inverted() @ offset
        swfx_core.attach(ob, lamp.empty, tuple(rel), (0, 0, 0))
        if kind == 'SPOT':
            ob.rotation_euler = (lamp.empty.matrix_world.to_3x3().inverted() @ aim).to_track_quat('-Z', 'Y').to_euler()
        ctx.drive(data, 'energy', f'{energy:g} * ' + lamp_expr(knob, lamp.role), lamp_props(knob, lamp.role))
        record['lights'].append({'lamp': lamp.name, 'role': lamp.role, 'light': ob.name, 'type': kind,
                                 'energy': energy, 'knob': knob})
    return record


# --- presets -------------------------------------------------------------
# Every preset sets these knobs to its own defaults when applied (unless the
# knob is already animated). (default, lo, hi, description)
PRESETS = {
    'deep-space': dict(
        description='Hard warm star key, faint cool rim, near-black space with a sparse starfield.',
        knobs={
            'lit_exposure': (0.3, -6.0, 6.0, 'View exposure (stops)'),
            'lit_sun': (5.0, 0.0, 30.0, 'Star/sun key strength (W/m2)'),
            'lit_sun_elevation': (30.0, -90.0, 90.0, 'Key elevation above the XY plane (deg)'),
            'lit_sun_azimuth': (100.0, -360.0, 360.0, 'Key azimuth from +X (bow) toward +Y (port), deg'),
            'lit_rim': (0.35, 0.0, 5.0, 'Cool rim/back light strength'),
            'lit_sky': (0.0012, 0.0, 0.05, 'Ambient world strength (very low in space)'),
            'lit_stars': (1.0, 0.0, 10.0, 'Starfield brightness (0 hides it)'),
            'lit_star_glare': (1.0, 0.0, 10.0, 'Visible star disc + glare shell (0 hides it)'),
        }),
    'jangala-approach': dict(
        description='Bright tropical daylight high over Jangala: high sun from camera-left, open sky fill, light haze.',
        knobs={
            'lit_exposure': (0.3, -6.0, 6.0, 'View exposure (stops)'),
            'lit_sun': (5.0, 0.0, 30.0, 'Sun strength (W/m2)'),
            'lit_sun_elevation': (55.0, -10.0, 90.0, 'Sun elevation (deg)'),
            'lit_sun_azimuth': (30.0, -360.0, 360.0, 'Sun azimuth from +X (bow) toward +Y (port), deg'),
            'lit_sky': (0.07, 0.0, 5.0, 'Physical sky strength (fill and background)'),
            'lit_haze': (0.0004, 0.0, 0.03, 'Humidity haze density per metre'),
        }),
    'jangala-landing': dict(
        description='Lower afternoon sun near the ground: more sky and ground bounce, thicker humid haze.',
        knobs={
            'lit_exposure': (0.2, -6.0, 6.0, 'View exposure (stops)'),
            'lit_sun': (4.2, 0.0, 30.0, 'Sun strength (W/m2)'),
            'lit_sun_elevation': (30.0, -10.0, 90.0, 'Sun elevation (deg)'),
            'lit_sun_azimuth': (36.0, -360.0, 360.0, 'Sun azimuth from +X (bow) toward +Y (port), deg'),
            'lit_sky': (0.09, 0.0, 5.0, 'Physical sky strength (fill and background)'),
            'lit_haze': (0.0008, 0.0, 0.03, 'Humidity haze density per metre'),
        }),
    'night-berth': dict(
        description='Night berth: blue-grey ambient, high gantry floods, two floor work lamps, optional fog.',
        knobs={
            'lit_exposure': (0.4, -6.0, 6.0, 'View exposure (stops)'),
            'lit_sky': (0.018, 0.0, 1.0, 'Blue-grey night ambient'),
            'lit_berth': (1.0, 0.0, 5.0, 'Gantry flood intensity'),
            'lit_berth_warmth': (0.35, 0.0, 1.0, '0 = cool LED floods, 1 = sodium'),
            'lit_floor_lamps': (1.0, 0.0, 5.0, 'Floor-level work lamp intensity'),
            'lit_fog': (0.0, 0.0, 0.05, 'Berth fog density per metre (light shafts); 0 = off'),
        }),
}


def _set_knobs(ctx, knobs):
    c = ctx.controls
    for name, (default, lo, hi, desc) in knobs.items():
        ctx.prop(name, default, lo, hi, desc)
        if swfx_core.find_fcurve(c, f'["{name}"]') is None:
            c[name] = float(default)


def clear_rig(ctx):
    coll = ctx.collection(RIG)
    for ob in list(coll.objects):
        data = ob.data
        bpy.data.objects.remove(ob, do_unlink=True)
        if data is not None and data.users == 0:
            for store in (bpy.data.meshes, bpy.data.lights):
                if data.name in store and store[data.name] == data:
                    store.remove(data)
                    break
    try:
        ctx.scene.driver_remove('view_settings.exposure')
    except Exception:
        pass
    for w in list(bpy.data.worlds):
        if w.name.startswith(PREFIX) and w.users == 0:
            bpy.data.worlds.remove(w)
    for m in list(bpy.data.materials):
        if m.name.startswith(PREFIX + ' rig') and m.users == 0:
            bpy.data.materials.remove(m)
    return coll


def _new_world(ctx, name):
    old = ctx.scene.world
    w = bpy.data.worlds.new(f'{PREFIX} {name}')
    ctx.scene.world = w
    if old is not None and old.name.startswith(PREFIX) and old.users == 0:
        bpy.data.worlds.remove(old)
    nt = w.node_tree
    nt.nodes.clear()
    out = nt.nodes.new('ShaderNodeOutputWorld')
    return w, nt.nodes, nt.links, out


def _sun(ctx, coll, name, color, angle_deg, strength_expr, props, elev_expr=None, az_expr=None, rot=None):
    data = bpy.data.lights.new(f'{PREFIX} {name}', 'SUN')
    data.color = color
    data.angle = math.radians(angle_deg)
    ob = bpy.data.objects.new(f'{PREFIX} {name}', data)
    coll.objects.link(ob)
    ctx.drive(data, 'energy', strength_expr, props)
    if elev_expr:
        ctx.drive(ob, 'rotation_euler', elev_expr, ['lit_sun_elevation'], index=1)
        ctx.drive(ob, 'rotation_euler', az_expr, ['lit_sun_azimuth'], index=2)
    elif rot:
        ob.rotation_euler = rot
    return ob


def sun_direction(elevation_deg, azimuth_deg):
    """Unit vector toward the sun (the light travels the opposite way)."""
    el, az = math.radians(elevation_deg), math.radians(azimuth_deg)
    return Vector((math.cos(el) * math.cos(az), math.cos(el) * math.sin(az), math.sin(el)))


def _key_sun(ctx, coll, color, angle):
    # A sun lamp's +Z points at the sun: rotate +Z down by (90-el) about Y, then by az about Z.
    return _sun(ctx, coll, 'key sun', color, angle, 'lit_sun', ['lit_sun'],
                'radians(90 - lit_sun_elevation)', 'radians(lit_sun_azimuth)')


def _mesh(name, coll, verts, faces, material):
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.update()
    ob = bpy.data.objects.new(name, me)
    coll.objects.link(ob)
    if material:
        me.materials.append(material)
    return ob


def _box(name, coll, lo, hi, material):
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    verts = [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    faces = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    return _mesh(name, coll, verts, faces, material)


def _plane(name, coll, centre, half, z, material):
    cx, cy = centre
    verts = [(cx - half, cy - half, z), (cx + half, cy - half, z), (cx + half, cy + half, z), (cx - half, cy + half, z)]
    return _mesh(name, coll, verts, [(0, 1, 2, 3)], material)


def _volume_material(ctx, name, knob, color, anisotropy):
    m, nodes, links = node_material(f'{PREFIX} rig {name}')
    vol = nodes.new('ShaderNodeVolumePrincipled')
    vol.inputs['Color'].default_value = (*color, 1)
    vol.inputs['Anisotropy'].default_value = anisotropy
    # Knob is per metre; the volume integrates per scene unit.
    d = swfx_core.value_node(ctx, nodes, knob, f'{knob} * {swfx_core.METRES_PER_UNIT:g}', [knob])
    links.new(d, vol.inputs['Density'])
    out = nodes.new('ShaderNodeOutputMaterial')
    links.new(vol.outputs[0], out.inputs['Volume'])
    return m


def _only_camera(ob, glossy=True):
    ob.visible_diffuse = False
    ob.visible_shadow = False
    ob.visible_transmission = False
    ob.visible_volume_scatter = False
    ob.visible_glossy = glossy


def _deep_space(ctx, coll, options):
    lo, hi = ctx.anchors.bounds
    w, nodes, links, out = _new_world(ctx, 'deep-space')
    # Near-black ambient plus a sparse procedural starfield on the view direction.
    tc = nodes.new('ShaderNodeTexCoord')
    vor = nodes.new('ShaderNodeTexVoronoi')
    vor.voronoi_dimensions = '3D'
    vor.feature = 'F1'
    vor.inputs['Scale'].default_value = float(options.get('star_scale', 320.0))
    vor.inputs['Randomness'].default_value = 1.0
    links.new(tc.outputs['Generated'], vor.inputs['Vector'])
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(vor.outputs['Color'], sep.inputs[0])
    density = float(options.get('star_density', .018))
    keep = math_node(nodes, links, 'GREATER_THAN', sep.outputs[0], 1.0 - density)
    core = math_node(nodes, links, 'SUBTRACT', 1.0, math_node(nodes, links, 'DIVIDE', vor.outputs['Distance'], .16))
    core = math_node(nodes, links, 'MAXIMUM', core, 0.0)
    core = math_node(nodes, links, 'POWER', core, 2.0)
    # Magnitudes: most stars faint, a few bright (power-law on a cell random).
    mag = math_node(nodes, links, 'POWER', sep.outputs[1], 6.0)
    mag = math_node(nodes, links, 'ADD', math_node(nodes, links, 'MULTIPLY', mag, 60.0), 1.5)
    star = math_node(nodes, links, 'MULTIPLY', math_node(nodes, links, 'MULTIPLY', keep, core), mag)
    stars_k = swfx_core.value_node(ctx, nodes, 'lit_stars', 'lit_stars', ['lit_stars'])
    star = math_node(nodes, links, 'MULTIPLY', star, stars_k)
    # Slight colour temperature per star.
    tint = nodes.new('ShaderNodeMix')
    tint.data_type = 'RGBA'
    links.new(sep.outputs[2], tint.inputs['Factor'])
    tint.inputs['A'].default_value = (1.0, .82, .66, 1)
    tint.inputs['B'].default_value = (.72, .82, 1.0, 1)
    star_bg = nodes.new('ShaderNodeBackground')
    links.new(tint.outputs['Result'], star_bg.inputs['Color'])
    links.new(star, star_bg.inputs['Strength'])
    amb = nodes.new('ShaderNodeBackground')
    amb.inputs['Color'].default_value = (.55, .62, .8, 1)
    links.new(swfx_core.value_node(ctx, nodes, 'lit_sky', 'lit_sky', ['lit_sky']), amb.inputs['Strength'])
    add = nodes.new('ShaderNodeAddShader')
    links.new(amb.outputs[0], add.inputs[0])
    links.new(star_bg.outputs[0], add.inputs[1])
    # Stars must not light the ship (only camera rays see them).
    lp = nodes.new('ShaderNodeLightPath')
    mix = nodes.new('ShaderNodeMixShader')
    links.new(lp.outputs['Is Camera Ray'], mix.inputs[0])
    links.new(amb.outputs[0], mix.inputs[1])
    links.new(add.outputs[0], mix.inputs[2])
    links.new(mix.outputs[0], out.inputs['Surface'])

    sun = _key_sun(ctx, coll, (1.0, .95, .87), float(options.get('star_angle', .6)))
    # Rim from behind/below the key, cool; fixed relative to the key is not
    # needed: it is placed opposite the default key.
    rim = _sun(ctx, coll, 'rim', (.62, .74, 1.0), 4.0, 'lit_rim', ['lit_rim'],
               rot=(0, math.radians(90 - float(options.get('rim_elevation', 25.0))),
                    math.radians(float(options.get('rim_azimuth', -125.0)))))
    rim.data.angle = math.radians(4.0)

    # Visible star: a disc and a glare shell parented to the key sun along +Z.
    dist = float(options.get('star_distance', 3000.0))
    angle = math.radians(float(options.get('star_disc_deg', .6)))
    disc_m, n2, l2 = node_material(f'{PREFIX} rig star disc')
    em = n2.new('ShaderNodeEmission')
    em.inputs['Color'].default_value = (1.0, .95, .86, 1)
    links2 = l2
    gk = swfx_core.value_node(ctx, n2, 'lit_star_glare', '400 * lit_star_glare', ['lit_star_glare'])
    links2.new(gk, em.inputs['Strength'])
    o2 = n2.new('ShaderNodeOutputMaterial')
    links2.new(em.outputs[0], o2.inputs['Surface'])
    bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=12, radius=dist * math.tan(angle / 2))
    disc = bpy.context.active_object
    disc.name = f'{PREFIX} star disc'
    for c in list(disc.users_collection):
        c.objects.unlink(disc)
    coll.objects.link(disc)
    disc.data.materials.append(disc_m)
    _only_camera(disc)
    swfx_core.attach(disc, sun, (0, 0, dist))

    glare_m, n3, l3 = node_material(f'{PREFIX} rig star glare')
    lw = n3.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    f = math_node(n3, l3, 'SUBTRACT', 1.0, lw.outputs['Facing'])
    f = math_node(n3, l3, 'POWER', f, 9.0)
    gk3 = swfx_core.value_node(ctx, n3, 'lit_star_glare', '1.2 * lit_star_glare', ['lit_star_glare'])
    f = math_node(n3, l3, 'MULTIPLY', f, gk3)
    em3 = n3.new('ShaderNodeEmission')
    em3.inputs['Color'].default_value = (1.0, .9, .78, 1)
    l3.new(f, em3.inputs['Strength'])
    tr = n3.new('ShaderNodeBsdfTransparent')
    add3 = n3.new('ShaderNodeAddShader')
    l3.new(tr.outputs[0], add3.inputs[0])
    l3.new(em3.outputs[0], add3.inputs[1])
    o3 = n3.new('ShaderNodeOutputMaterial')
    l3.new(add3.outputs[0], o3.inputs['Surface'])
    bpy.ops.mesh.primitive_uv_sphere_add(segments=48, ring_count=24, radius=dist * math.tan(angle / 2) * 14)
    glare = bpy.context.active_object
    glare.name = f'{PREFIX} star glare'
    for c in list(glare.users_collection):
        c.objects.unlink(glare)
    coll.objects.link(glare)
    glare.data.materials.append(glare_m)
    bpy.ops.object.shade_smooth()
    _only_camera(glare, glossy=False)
    swfx_core.attach(glare, sun, (0, 0, dist))
    return {'sun': sun.name, 'rim': rim.name, 'star': disc.name}


def _jangala(ctx, coll, options, landing):
    lo, hi = ctx.anchors.bounds
    centre = (lo + hi) / 2
    w, nodes, links, out = _new_world(ctx, 'jangala-landing' if landing else 'jangala-approach')
    sky = nodes.new('ShaderNodeTexSky')
    sky.sky_type = 'MULTIPLE_SCATTERING'
    sky.sun_disc = False                   # the sun lamp is the sun; no double key
    sky.altitude = 200.0 if landing else 900.0
    sky.air_density = 1.0
    sky.aerosol_density = 2.2 if landing else 1.4   # tropical humidity
    sky.ozone_density = 1.0
    # Sky sun follows the knobs: sun_rotation = 90deg - azimuth (measured).
    fc = ctx.drive(sky, 'sun_elevation', 'radians(lit_sun_elevation)', ['lit_sun_elevation'])
    fc = ctx.drive(sky, 'sun_rotation', 'radians(90 - lit_sun_azimuth)', ['lit_sun_azimuth'])
    bg = nodes.new('ShaderNodeBackground')
    links.new(sky.outputs[0], bg.inputs['Color'])
    links.new(swfx_core.value_node(ctx, nodes, 'lit_sky', 'lit_sky', ['lit_sky']), bg.inputs['Strength'])
    links.new(bg.outputs[0], out.inputs['Surface'])

    sun = _key_sun(ctx, coll, (1.0, .955, .9), 1.2 if landing else .8)

    # Ground bounce: a big canopy-green plane below (camera-invisible by default:
    # it is lighting, not the environment plate).
    ground_z = lo.z - float(options.get('ground_drop', units(9.0 if landing else 270.0)))
    gm, gn, gl = node_material(f'{PREFIX} rig canopy bounce')
    bs = gn.new('ShaderNodeBsdfDiffuse')
    noise = gn.new('ShaderNodeTexNoise')
    noise.inputs['Scale'].default_value = .02
    ramp = gn.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].color = (.035, .07, .025, 1)
    ramp.color_ramp.elements[1].color = (.07, .11, .04, 1)
    gl.new(noise.outputs['Fac'], ramp.inputs['Fac'])
    gl.new(ramp.outputs['Color'], bs.inputs['Color'])
    go = gn.new('ShaderNodeOutputMaterial')
    gl.new(bs.outputs[0], go.inputs['Surface'])
    ground = _plane(f'{PREFIX} ground bounce', coll, (centre.x, centre.y), units(2600.0), ground_z, gm)
    ground.visible_camera = bool(options.get('ground_visible', False))
    ground.visible_shadow = True

    # Humidity haze: a homogeneous, forward-scattering volume around the shot.
    hm = _volume_material(ctx, 'haze', 'lit_haze', (.86, .91, 1.0), .55)
    r = float(options.get('haze_radius', units(1000.0)))
    haze = _box(f'{PREFIX} haze', coll, (centre.x - r, centre.y - r, ground_z + .5),
                (centre.x + r, centre.y + r, ground_z + units(330.0 if landing else 800.0)), hm)
    haze.visible_shadow = False
    return {'sun': sun.name, 'ground_z': round(ground_z, 2), 'haze': haze.name}


def _night_berth(ctx, coll, options):
    lo, hi = ctx.anchors.bounds
    centre = (lo + hi) / 2
    floor_z = lo.z - float(options.get('floor_drop', units(1.0)))
    w, nodes, links, out = _new_world(ctx, 'night-berth')
    # Night sky: blue-grey, a little brighter toward the horizon (city glow).
    tc = nodes.new('ShaderNodeTexCoord')
    sepxyz = nodes.new('ShaderNodeSeparateXYZ')
    links.new(tc.outputs['Generated'], sepxyz.inputs[0])
    ramp = nodes.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].position = .0
    ramp.color_ramp.elements[0].color = (.30, .27, .26, 1)   # sodium-tinted horizon glow
    ramp.color_ramp.elements[1].position = .45
    ramp.color_ramp.elements[1].color = (.10, .13, .2, 1)
    links.new(sepxyz.outputs['Z'], ramp.inputs['Fac'])
    bg = nodes.new('ShaderNodeBackground')
    links.new(ramp.outputs['Color'], bg.inputs['Color'])
    links.new(swfx_core.value_node(ctx, nodes, 'lit_sky', 'lit_sky * 3', ['lit_sky']), bg.inputs['Strength'])
    links.new(bg.outputs[0], out.inputs['Surface'])

    # Floor: wet-ish concrete pad, so the floods pool.
    fm, fn, fl = node_material(f'{PREFIX} rig berth floor')
    p = fn.new('ShaderNodeBsdfPrincipled')
    noise = fn.new('ShaderNodeTexNoise')
    noise.inputs['Scale'].default_value = .08
    noise.inputs['Detail'].default_value = 8.0
    ramp2 = fn.new('ShaderNodeValToRGB')
    ramp2.color_ramp.elements[0].color = (.07, .07, .068, 1)
    ramp2.color_ramp.elements[1].color = (.16, .155, .15, 1)
    fl.new(noise.outputs['Fac'], ramp2.inputs['Fac'])
    fl.new(ramp2.outputs['Color'], p.inputs['Base Color'])
    rr = fn.new('ShaderNodeMapRange')
    rr.inputs['To Min'].default_value = .25
    rr.inputs['To Max'].default_value = .75
    fl.new(noise.outputs['Fac'], rr.inputs['Value'])
    fl.new(rr.outputs['Result'], p.inputs['Roughness'])
    fo = fn.new('ShaderNodeOutputMaterial')
    fl.new(p.outputs[0], fo.inputs['Surface'])
    rec = {'floor_z': round(floor_z, 2), 'gantry': [], 'floor_lamps': []}
    if options.get('floor', True):
        floor = _plane(f'{PREFIX} berth floor', coll, (centre.x, centre.y), units(400.0), floor_z, fm)
        rec['floor'] = floor.name

    steel_m, sn, sl = node_material(f'{PREFIX} rig gantry steel')
    sp = sn.new('ShaderNodeBsdfPrincipled')
    sp.inputs['Base Color'].default_value = (.1, .1, .1, 1)
    sp.inputs['Metallic'].default_value = .6
    sp.inputs['Roughness'].default_value = .55
    so = sn.new('ShaderNodeOutputMaterial')
    sl.new(sp.outputs[0], so.inputs['Surface'])

    def lens_material(tag, knob, expr, color):
        m, ln, ll = node_material(f'{PREFIX} rig {tag} lens')
        em = ln.new('ShaderNodeEmission')
        em.inputs['Color'].default_value = (*color, 1)
        ll.new(swfx_core.value_node(ctx, ln, knob, expr, [knob]), em.inputs['Strength'])
        o = ln.new('ShaderNodeOutputMaterial')
        ll.new(em.outputs[0], o.inputs['Surface'])
        return m

    # Gantry floods: poles around the berth, heads well above the ship.
    mx, my = float(options.get('gantry_margin_x', units(16.0))), float(options.get('gantry_margin_y', units(20.0)))
    top = hi.z + float(options.get('gantry_rise', units(14.0)))
    sites = [
        # Aft corners and beam poles stand clear; the bow pair stands ahead of
        # the bow near the centreline, out of a bow-quarter camera's way.
        (lo.x - mx, hi.y + my), (centre.x, hi.y + my + units(4)), (hi.x + mx, hi.y * .3),
        (lo.x - mx, lo.y - my), (centre.x, lo.y - my - units(4)), (hi.x + mx, lo.y * .3),
    ]
    sodium, led = (1.0, .52, .18), (.82, .9, 1.0)
    lens = lens_material('flood', 'lit_berth', '25 * lit_berth', (1.0, .85, .7))
    for i, (x, y) in enumerate(sites, 1):
        pw = units(.4)
        pole = _box(f'{PREFIX} gantry pole {i}', coll, (x - pw, y - pw, floor_z), (x + pw, y + pw, top), steel_m)
        aim_at = Vector((min(max(x, lo.x + units(10)), hi.x - units(10)), centre.y * .3 + units(4 if y > 0 else -4),
                         lo.z + (hi.z - lo.z) * .35))
        here = Vector((x, y, top + units(.3)))
        d = (aim_at - here).normalized()
        arm = _box(f'{PREFIX} gantry head {i}', coll, (x - units(1.4), y - units(.65), top), (x + units(1.4), y + units(.65), top + units(.8)), steel_m)
        glow = _box(f'{PREFIX} gantry lens {i}', coll, (x - units(1.2), y - units(.5), top - units(.05)), (x + units(1.2), y + units(.5), top + units(.01)), lens)
        _only_camera(glow)
        data = bpy.data.lights.new(f'{PREFIX} gantry flood {i}', 'SPOT')
        data.spot_size = math.radians(85)
        data.spot_blend = .75
        data.shadow_soft_size = units(1.2)
        ob = bpy.data.objects.new(f'{PREFIX} gantry flood {i}', data)
        coll.objects.link(ob)
        ob.location = here - Vector((0, 0, units(.5)))
        ob.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
        ob.visible_camera = False
        ctx.drive(data, 'energy', f'{watts(68000):.0f} * lit_berth', ['lit_berth'])
        for k in range(3):
            ctx.drive(data, 'color', f'{led[k]:g} + ({sodium[k]:g} - {led[k]:g}) * lit_berth_warmth',
                      ['lit_berth_warmth'], index=k)
        rec['gantry'].append(ob.name)

    # Two hard floor-level work lamps uplighting the hull from the deck.
    stands = [
        (Vector((hi.x - units(14), hi.y + units(6), floor_z + units(1.0))),
         Vector((hi.x - units(20), hi.y - units(5), centre.z + units(2.5)))),
        (Vector((lo.x + units(12), lo.y - units(6), floor_z + units(1.0))),
         Vector((lo.x + units(17), lo.y + units(4), centre.z + units(1.3)))),
    ]
    lamp_lens = lens_material('floor lamp', 'lit_floor_lamps', '60 * lit_floor_lamps', (.95, .97, 1.0))
    for i, (at, target) in enumerate(stands, 1):
        d = (target - at).normalized()
        base = _box(f'{PREFIX} floor lamp stand {i}', coll, (at.x - units(.5), at.y - units(.5), floor_z), (at.x + units(.5), at.y + units(.5), at.z - units(.25)), steel_m)
        head = _box(f'{PREFIX} floor lamp lens {i}', coll, (at.x - units(.32), at.y - units(.32), at.z - units(.25)), (at.x + units(.32), at.y + units(.32), at.z), lamp_lens)
        data = bpy.data.lights.new(f'{PREFIX} floor lamp {i}', 'SPOT')
        data.spot_size = math.radians(55)
        data.spot_blend = .3
        data.shadow_soft_size = units(.15)  # hard work-lamp shadows
        data.color = (.95, .97, 1.0)
        ob = bpy.data.objects.new(f'{PREFIX} floor lamp {i}', data)
        coll.objects.link(ob)
        ob.location = at + d * units(.4)
        ob.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
        ob.visible_camera = False
        ctx.drive(data, 'energy', f'{watts(12500):.0f} * lit_floor_lamps', ['lit_floor_lamps'])
        rec['floor_lamps'].append(ob.name)

    # Optional fog for light shafts (density 0 by default renders as nothing,
    # but the box is only built when asked, so it costs nothing otherwise).
    if options.get('fog', True):
        fogm = _volume_material(ctx, 'berth fog', 'lit_fog', (.8, .82, .85), .35)
        fog = _box(f'{PREFIX} berth fog', coll, (lo.x - units(90), lo.y - units(90), floor_z + units(.03)), (hi.x + units(90), hi.y + units(90), top + units(16)), fogm)
        fog.visible_shadow = False
        ctx.drive(fog, 'hide_render', 'lit_fog <= 0', ['lit_fog'])
        rec['fog'] = fog.name
    return rec


def refresh_drivers(ctx):
    """Make drivers read knobs added after the depsgraph last evaluated the
    controls empty. A custom prop set from Python does not re-tag the
    evaluated copy, so a driver built on it can read nothing, flag itself
    invalid and stay at zero (seen on cached ship blends). Tag, re-evaluate,
    and clear the sticky invalid flag on every driver in the file."""
    ctx.controls.update_tag()
    bpy.context.view_layer.update()
    stores = (bpy.data.objects, bpy.data.lights, bpy.data.materials, bpy.data.worlds,
              bpy.data.scenes, bpy.data.node_groups, bpy.data.meshes)
    for store in stores:
        for idb in store:
            for owner in (idb, getattr(idb, 'node_tree', None)):
                ad = getattr(owner, 'animation_data', None) if owner is not None else None
                if ad:
                    for fc in ad.drivers:
                        fc.driver.is_valid = True
    ctx.scene.frame_set(ctx.scene.frame_current)


def apply_preset(ctx, name, **options):
    """Replace the current rig with preset `name` and set its knob defaults.
    Options: deep-space star_scale/star_density/star_distance/star_disc_deg/
    star_angle; jangala ground_drop/ground_visible/haze_radius; night-berth
    floor (bool)/floor_drop/gantry_margin_x/_y/gantry_rise/fog (bool)."""
    if name not in PRESETS:
        raise ValueError(f'unknown lighting preset {name!r}; expected one of {sorted(PRESETS)}')
    coll = clear_rig(ctx)
    swfx_core.hide_reference_lights(ctx.scene)
    _set_knobs(ctx, PRESETS[name]['knobs'])
    ctx.drive(ctx.scene, 'view_settings.exposure', 'lit_exposure', ['lit_exposure'])
    ctx.scene.view_settings.view_transform = 'AgX'
    if name == 'deep-space':
        rec = _deep_space(ctx, coll, options)
    elif name.startswith('jangala'):
        rec = _jangala(ctx, coll, options, landing=name == 'jangala-landing')
    else:
        rec = _night_berth(ctx, coll, options)
    ctx.controls['swfx_lighting_preset'] = name
    rec['preset'] = name
    refresh_drivers(ctx)
    return rec


def build(ctx, preset=None, **options):
    rec = {'ship': build_ship_lights(ctx)}
    if preset:
        rec['rig'] = apply_preset(ctx, preset, **options)
    refresh_drivers(ctx)
    return rec


# --- review --------------------------------------------------------------
def _cams(ctx):
    coll = ctx.collection(REVIEW)
    for ob in list(coll.objects):
        bpy.data.objects.remove(ob, do_unlink=True)
    lo, hi = ctx.anchors.bounds
    c = (lo + hi) / 2
    cams = {
        # One consistent 3/4 from the port bow quarter, slightly above the deck.
        'three-quarter': swfx_core.look_camera(f'{PREFIX} cam three-quarter', coll,
                                               c + Vector((120, 150, 40)), c + Vector((-2, 0, -4)), lens=52),
        'grapple-close': swfx_core.look_camera(f'{PREFIX} cam grapple-close', coll,
                                               Vector((66, 92, 20)), Vector((34, 30, 5)), lens=30),
        'beacon-anim': swfx_core.look_camera(f'{PREFIX} cam beacon-anim', coll,
                                             Vector((85, 95, 48)), Vector((14, 14, 10)), lens=36),
    }
    return cams


def _render(scene, cam, path):
    scene.camera = cam
    scene.render.filepath = str(path)
    bpy.ops.render.render(write_still=True)


def review(ctx, out_dir, quick=False):
    import shutil
    scene = ctx.scene
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    final = HERE / 'review' / 'lighting'
    if not quick:
        final.mkdir(parents=True, exist_ok=True)
    swfx_core.setup_render(scene, samples=12 if quick else 64)
    scene.cycles.volume_bounces = 1
    scene.cycles.max_bounces = 6
    if quick:
        scene.render.resolution_percentage = 50
    cams = _cams(ctx)
    state = ctx.state
    shots = []
    if not ctx.spec['powered']:
        shots.append(('deep-space', 'three-quarter', 1, {}))
    else:
        shots += [('deep-space', 'three-quarter', 1, {}), ('jangala-approach', 'three-quarter', 1, {}),
                  ('jangala-landing', 'three-quarter', 1, {}), ('night-berth', 'three-quarter', 1, {}),
                  ('night-berth', 'grapple-close', 1, {})]
    only = os.environ.get('SWFX_LIGHTING_ONLY')  # iteration filter, e.g. 'deep-space,anim'
    written = []
    for preset, cam, fr, opts in shots:
        if only and preset not in str(only).split(','):
            continue
        apply_preset(ctx, preset, **opts)
        scene.frame_set(fr)
        name = f'{state}-{preset}-{cam}.png'
        _render(scene, cams[cam], out_dir / name)
        written.append(name)
        if not quick:
            shutil.copyfile(out_dir / name, final / name)

    if ctx.spec['powered'] and not (only and 'anim' not in str(only).split(',')):
        apply_preset(ctx, 'night-berth')
        frames = range(1, 13 if quick else 61)
        scene.cycles.samples = 8 if quick else 24
        anim = out_dir / f'{state}-night-berth-beacon'
        anim.mkdir(exist_ok=True)
        for f in frames:
            scene.frame_set(f)
            _render(scene, cams['beacon-anim'], anim / f'f{f:04d}.png')
        if not quick:
            swfx_core.encode_movie(anim / 'f%04d.png', final / f'{state}-night-berth-beacon.mp4')
            written.append(f'{state}-night-berth-beacon.mp4')
    print('[swfx_lighting] review wrote', written)
