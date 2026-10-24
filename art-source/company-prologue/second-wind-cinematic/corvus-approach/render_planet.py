"""Jangala as a lit 3D sphere for shot C1, alpha background, one sun.

blender --background --factory-startup --python render_planet.py [-- options]

Options:
  --scale P        resolution percentage of 3840x2160 (default 100; 25 for previews)
  --samples N      Cycles samples (default 256)
  --out PATH       output PNG (default layers/planet.png)
  --frames N       render an N-frame cloud-drift sequence to layers/planet-frames/
                   instead of a still (24 fps; the cloud shell turns slowly over the
                   surface while the surface stays fixed)

Run `python planet_maps.py` first if the derived maps are missing.

Maps (assets/, each with a .provenance.json):
  planet-albedo-v2.png          ImageGen: cloud-free true-colour mosaic (Blue Marble manner)
  planet-clouds-v2-eyefix.png   ImageGen cloud master, cyclone eye's black dot filled
  planet-ocean-v2.png           derived: water mask (specular, sunglint)
  planet-height-v2.png          derived: land relief for the bump
  planet-lights-v2.png          derived: night lights on coasts, deltas and farmland
`planet_maps.py` derives the last four from the first two, so every map is
pixel-registered to the albedo.

Shells:
  surface  radius 1.000  graded albedo; forest darkened and desaturated, ocean near-black
           navy. Water is a smooth dielectric (IOR 1.33, wind-roughened), so the sunglint
           is a real reflection of the sun and sits where the half vector meets the
           sphere. Relief from the height map as a bump; it shows mainly near the
           terminator, as relief does from orbit. Night lights are emission gated by the
           geometric sun angle, so they appear only past the terminator.
  clouds   radius 1.005  cloud density -> alpha with a cloud-top bump; Cycles casts their
           shadows onto the surface (offset grows toward the terminator).
  air      radius 1.030  a physical Rayleigh volume: scattering coefficient proportional
           to 1/lambda^4 (R:G:B 0.175:0.41:1) with density falling off exponentially
           (scale height AIR_SCALE_HEIGHT) and Blender's Rayleigh phase function. It makes
           the blue limb, the blue veil over the dark ocean, and the reddened light on
           the terminator (sunlight there crosses a long air path that strips the blue),
           all from the one sun. A thin forward-scattering haze adds the brighter
           sunward limb.

Texture lookup: explicit equirectangular coordinates (longitude from atan2, latitude from
asin) on a FLAT image lookup. The image node's SPHERE projection remaps its input as
(co - 0.5) * 2, so it needs 0..1 coordinates; fed object coordinates (-1..1) it put the
projection pole at (0.5, 0.5, 0.5), on the camera-facing side, and every map converged
radially there.

Layout follows c1-layout.md: a whole distant globe, centre (1290, 560) and 760 px across
on a 1920x1080 frame (double on the 3840x2160 render), disc and glow entirely inside the
canvas, everything else transparent. The camera aims at the planet centre and lens shift
places the disc, so the limb projects as an exact circle. The key light is SUN_IN_CAMERA,
identical to c1_core.SUN_IN_CAMERA: upper left and somewhat toward camera, so the lit face
is gibbous and the terminator crosses the lower right. Equirect seams face away.
"""
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector

BASE = Path(__file__).resolve().parent
ASSETS = BASE / 'assets'
ALBEDO = ASSETS / 'planet-albedo-v2.png'
CLOUDS = ASSETS / 'planet-clouds-v2-eyefix.png'
OCEAN = ASSETS / 'planet-ocean-v2.png'
HEIGHT = ASSETS / 'planet-height-v2.png'
LIGHTS = ASSETS / 'planet-lights-v2.png'

# Whole globe on a 1920x1080 frame (pixels, y down): centre and radius of the disc.
FRAME_W, FRAME_H = 1920, 1080
DISC_CENTRE_PX = Vector((1290.0, 560.0))
DISC_RADIUS_PX = 380.0
LENS_MM, SENSOR_MM = 50.0, 36.0
CAM_ELEVATION = math.radians(20)      # above the orbital plane: northern hemisphere tilts toward camera
# Albedo column (0..1) facing the camera; the seam is behind. 0.42 puts the jungle
# continents on the lit side and open ocean under the sunglint, which for this sun falls
# about 0.077 of the map west of the facing column at latitude 36 N (screen ~(1147, 445)).
FACING_U = 0.42
CLOUD_FACING_U = 0.62                 # cloud map column facing the camera (cyclone centre right)
CLOUD_DRIFT_DEG_PER_FRAME = 0.012     # --frames: slow drift of the cloud shell
SUN_IN_CAMERA = Vector((-0.62, 0.50, 0.50))  # toward the light; x right, y up, z to camera (= c1_core)
SUN_STRENGTH = 4.6
SUN_COLOUR = (1.0, 0.97, 0.9)

# Surface grade.
LAND_SATURATION = 0.72
LAND_VALUE = 0.80
OCEAN_VALUE = 0.70
WATER_ROUGHNESS = 0.30                # wind-roughened sea: a broad glint, not a mirror point
RELIEF_DISTANCE = 0.016               # height 1.0 -> this many planet radii of bump displacement
LIGHTS_STRENGTH = 1.3
LIGHTS_COLOUR = (1.0, 0.62, 0.28, 1)  # sodium-warm

CLOUD_RADIUS = 1.005
CLOUD_LEVELS = (0.10, 0.85)           # cloud-map luminance window mapped to alpha
CLOUD_GAMMA = 1.35                    # >1: only cores dense, thin cloud translucent
CLOUD_PEAK_ALPHA = 0.96
CLOUD_ALBEDO = (0.86, 0.87, 0.88, 1)
CLOUD_CELL_SCALE = 160.0              # cumulus cell frequency on the unit sphere
CLOUD_BREAKUP = 0.85                  # how much of mid-density cloud the cells may clear

AIR_RADIUS = 1.03
AIR_SCALE_HEIGHT = 0.0035             # planet radii (~2.8x Earth's, so the limb reads at 760 px)
RAYLEIGH_RGB = (0.175, 0.41, 1.0)     # 1/lambda^4 at 680, 550, 440 nm, normalised to blue
RAYLEIGH_TAU_BLUE = 0.28              # vertical optical depth in blue (Earth ~0.25-0.36)
HAZE_TAU = 0.03                       # thin aerosol haze, forward-scattering
HAZE_SCALE_HEIGHT = 0.003


def args():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    opts = {'scale': 100, 'samples': 256, 'out': str(BASE / 'layers' / 'planet.png'), 'frames': 0}
    for i, a in enumerate(argv):
        if a.startswith('--') and a[2:] in opts:
            v = argv[i + 1]
            opts[a[2:]] = v if a == '--out' else int(v)
    return opts


def image(path, colorspace):
    if not path.exists():
        raise FileNotFoundError(f'{path} missing; run python planet_maps.py')
    img = bpy.data.images.load(str(path), check_existing=True)
    img.colorspace_settings.name = colorspace
    return img


class Graph:
    """Small helper over a material node tree: constants or sockets wherever a value goes."""

    def __init__(self, mat):
        self.nodes, self.links = mat.node_tree.nodes, mat.node_tree.links

    def wire(self, sock, v):
        if v is None:
            return
        if isinstance(v, (int, float, tuple, list, Vector)):
            sock.default_value = tuple(v) if isinstance(v, (tuple, list, Vector)) else v
        else:
            self.links.new(v, sock)

    def math(self, op, a=None, b=None, c=None, clamp=False):
        n = self.nodes.new('ShaderNodeMath')
        n.operation = op
        n.use_clamp = clamp
        for i, v in enumerate((a, b, c)):
            self.wire(n.inputs[i], v)
        return n.outputs[0]

    def vmath(self, op, a=None, b=None, out='Vector'):
        n = self.nodes.new('ShaderNodeVectorMath')
        n.operation = op
        self.wire(n.inputs[0], a)
        self.wire(n.inputs[1], b)
        return n.outputs[out]

    def smooth(self, value, lo, hi):
        n = self.nodes.new('ShaderNodeMapRange')
        n.interpolation_type = 'SMOOTHSTEP'
        n.inputs['From Min'].default_value = lo
        n.inputs['From Max'].default_value = hi
        self.wire(n.inputs['Value'], value)
        return n.outputs['Result']

    def mix(self, blend, fac, a, b):
        n = self.nodes.new('ShaderNodeMix')
        n.data_type = 'RGBA'
        n.blend_type = blend
        for sock, v in ((n.inputs[0], fac), (n.inputs[6], a), (n.inputs[7], b)):
            self.wire(sock, v)
        return n.outputs[2]

    def position(self):
        """Unit direction from the planet centre (object space; the spheres are not rotated)."""
        tex = self.nodes.new('ShaderNodeTexCoord')
        return tex.outputs['Object']

    def equirect(self, facing_u, name):
        """(u, v, 0) for an equirectangular map with column `facing_u` toward the camera.

        The camera looks along +Y at longitude -90 deg (object -Y). u grows eastward (to the
        right on screen), v from the south pole (0) to the north pole (1). `facing_u` lives in
        a Value node named `name` so --frames can turn the shell.
        """
        d = self.vmath('NORMALIZE', self.position())
        sep = self.nodes.new('ShaderNodeSeparateXYZ')
        self.links.new(d, sep.inputs[0])
        lon = self.math('ARCTAN2', sep.outputs['Y'], sep.outputs['X'])
        spin = self.nodes.new('ShaderNodeValue')
        spin.name = name
        spin.outputs[0].default_value = facing_u - 0.25
        u = self.math('FRACT', self.math('ADD', self.math('MULTIPLY_ADD', lon, 1 / (2 * math.pi), 0.5),
                                         spin.outputs[0]))
        v = self.math('MULTIPLY_ADD', self.math('ARCSINE', sep.outputs['Z']), 1 / math.pi, 0.5)
        comb = self.nodes.new('ShaderNodeCombineXYZ')
        self.links.new(u, comb.inputs['X'])
        self.links.new(v, comb.inputs['Y'])
        return comb.outputs[0]

    def tex(self, path, colorspace, uv, interpolation='Cubic'):
        n = self.nodes.new('ShaderNodeTexImage')
        n.image = image(path, colorspace)
        n.projection = 'FLAT'
        n.extension = 'REPEAT'
        n.interpolation = interpolation
        self.links.new(uv, n.inputs['Vector'])
        return n

    def sun_cosine(self, sun_world):
        """cos(angle between the local vertical and the sun), from position, not the shading
        normal (Cycles flips back-face normals toward the ray)."""
        return self.vmath('DOT_PRODUCT', self.vmath('NORMALIZE', self.position()), tuple(sun_world), out='Value')


def sphere(name, radius):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=256, ring_count=128, radius=radius)
    obj = bpy.context.active_object
    obj.name = name
    bpy.ops.object.shade_smooth()
    return obj


def surface_material(sun_world):
    mat = bpy.data.materials.new('Jangala | surface')
    g = Graph(mat)
    bsdf = g.nodes['Principled BSDF']
    uv = g.equirect(FACING_U, 'surface spin')
    albedo = g.tex(ALBEDO, 'sRGB', uv).outputs['Color']
    water = g.tex(OCEAN, 'Non-Color', uv).outputs['Color']
    water = g.math('MAXIMUM', g.vmath('DOT_PRODUCT', water, (1 / 3, 1 / 3, 1 / 3), out='Value'), 0.0)
    height = g.tex(HEIGHT, 'Non-Color', uv).outputs['Color']
    height = g.vmath('DOT_PRODUCT', height, (1 / 3, 1 / 3, 1 / 3), out='Value')

    # Land: real forest from orbit is dark and greyed, nowhere near a map's emerald.
    land_hsv = g.nodes.new('ShaderNodeHueSaturation')
    land_hsv.inputs['Saturation'].default_value = LAND_SATURATION
    land_hsv.inputs['Value'].default_value = LAND_VALUE
    g.links.new(albedo, land_hsv.inputs['Color'])
    gamma = g.nodes.new('ShaderNodeGamma')       # deepen the forest more than the pale tracts
    gamma.inputs['Gamma'].default_value = 1.12
    g.links.new(land_hsv.outputs['Color'], gamma.inputs['Color'])
    # Broad, low-frequency variation (soil moisture, canopy type) so forest is not one tone.
    var = g.nodes.new('ShaderNodeTexNoise')
    var.inputs['Scale'].default_value = 6.0
    var.inputs['Detail'].default_value = 5.0
    g.links.new(g.position(), var.inputs['Vector'])
    var_col = g.nodes.new('ShaderNodeValToRGB')
    var_col.color_ramp.elements[0].position = 0.35
    var_col.color_ramp.elements[0].color = (0.78, 0.80, 0.70, 1)
    var_col.color_ramp.elements[1].position = 0.68
    var_col.color_ramp.elements[1].color = (1.0, 0.98, 0.94, 1)
    g.links.new(var.outputs['Fac'], var_col.inputs['Fac'])
    land_col = g.mix('MULTIPLY', 1.0, gamma.outputs['Color'], var_col.outputs['Color'])

    # Ocean: near-black navy as the sea photographs from space; the air above makes it
    # blue. Large slow variation (plankton, depth, sediment) so it is not a flat fill.
    sea_hsv = g.nodes.new('ShaderNodeHueSaturation')
    sea_hsv.inputs['Value'].default_value = OCEAN_VALUE
    sea_hsv.inputs['Saturation'].default_value = 0.9
    g.links.new(albedo, sea_hsv.inputs['Color'])
    sea_var = g.nodes.new('ShaderNodeTexNoise')
    sea_var.inputs['Scale'].default_value = 3.5
    sea_var.inputs['Detail'].default_value = 4.0
    g.links.new(g.position(), sea_var.inputs['Vector'])
    sea_ramp = g.nodes.new('ShaderNodeValToRGB')
    sea_ramp.color_ramp.elements[0].position = 0.3
    sea_ramp.color_ramp.elements[0].color = (0.80, 0.86, 0.95, 1)
    sea_ramp.color_ramp.elements[1].position = 0.7
    sea_ramp.color_ramp.elements[1].color = (1.08, 1.06, 1.0, 1)
    g.links.new(sea_var.outputs['Fac'], sea_ramp.inputs['Fac'])
    sea_col = g.mix('MULTIPLY', 1.0, sea_hsv.outputs['Color'], sea_ramp.outputs['Color'])

    g.links.new(g.mix('MIX', water, land_col, sea_col), bsdf.inputs['Base Color'])
    bsdf.inputs['IOR'].default_value = 1.333
    g.links.new(g.math('MULTIPLY_ADD', water, WATER_ROUGHNESS - 0.92, 0.92), bsdf.inputs['Roughness'])
    g.links.new(g.math('MULTIPLY_ADD', water, 0.5 - 0.2, 0.2), bsdf.inputs['Specular IOR Level'])

    # Relief: the derived height, plus fine 3D noise on rugged ground only so ridges carry
    # detail below a texel. The water mask flattens the sea.
    fine = g.nodes.new('ShaderNodeTexNoise')
    fine.noise_type = 'RIDGED_MULTIFRACTAL'
    fine.inputs['Scale'].default_value = 90.0
    fine.inputs['Detail'].default_value = 6.0
    g.links.new(g.position(), fine.inputs['Vector'])
    relief = g.math('MULTIPLY_ADD', g.math('MULTIPLY', fine.outputs['Fac'], height), 0.12, height)
    relief = g.math('MULTIPLY', relief, g.math('SUBTRACT', 1.0, water))
    bump = g.nodes.new('ShaderNodeBump')
    bump.inputs['Strength'].default_value = 1.0
    bump.inputs['Distance'].default_value = RELIEF_DISTANCE
    g.links.new(relief, bump.inputs['Height'])
    g.links.new(bump.outputs['Normal'], bsdf.inputs['Normal'])

    # Night lights: only past the terminator (geometric sun angle), never on the day side.
    lights = g.tex(LIGHTS, 'Non-Color', uv, interpolation='Linear').outputs['Color']
    night = g.smooth(g.sun_cosine(sun_world), 0.04, -0.10)
    # Toward the limb a city is seen through a long, low air path and is foreshortened to a
    # streak; fade it with the viewing cosine so the night limb does not draw a bright arc.
    geo = g.nodes.new('ShaderNodeNewGeometry')
    view = g.vmath('DOT_PRODUCT', geo.outputs['True Normal'], geo.outputs['Incoming'], out='Value')
    night = g.math('MULTIPLY', night, g.smooth(view, 0.05, 0.45))
    glow = g.math('MULTIPLY', g.vmath('DOT_PRODUCT', lights, (1 / 3, 1 / 3, 1 / 3), out='Value'), night)
    g.links.new(g.mix('MIX', glow, (0, 0, 0, 1), LIGHTS_COLOUR), bsdf.inputs['Emission Color'])
    bsdf.inputs['Emission Strength'].default_value = LIGHTS_STRENGTH
    return mat


def cloud_material():
    mat = bpy.data.materials.new('Jangala | clouds')
    g = Graph(mat)
    g.nodes.remove(g.nodes['Principled BSDF'])
    out = g.nodes['Material Output']
    uv = g.equirect(CLOUD_FACING_U, 'cloud spin')
    tex = g.tex(CLOUDS, 'Non-Color', uv).outputs['Color']
    lum = g.vmath('DOT_PRODUCT', tex, (1 / 3, 1 / 3, 1 / 3), out='Value')
    # Fine noise jitters the threshold so edges fray into fibres instead of ending as
    # clean blobs; a levels window plus a gamma keeps only the cores dense, and the peak
    # stays below 1 so thin cloud stays translucent.
    wisp = g.nodes.new('ShaderNodeTexNoise')
    wisp.inputs['Scale'].default_value = 70.0
    wisp.inputs['Detail'].default_value = 10.0
    wisp.inputs['Roughness'].default_value = 0.6
    g.links.new(g.position(), wisp.inputs['Vector'])
    lum_j = g.math('ADD', lum, g.math('MULTIPLY_ADD', wisp.outputs['Fac'], 0.16, -0.08))
    levels = g.nodes.new('ShaderNodeMapRange')
    levels.inputs['From Min'].default_value = CLOUD_LEVELS[0]
    levels.inputs['From Max'].default_value = CLOUD_LEVELS[1]
    levels.inputs['To Max'].default_value = CLOUD_PEAK_ALPHA
    g.links.new(lum_j, levels.inputs['Value'])
    density = g.math('POWER', levels.outputs['Result'], CLOUD_GAMMA)
    # The master's cumulus fields are smooth puffs at this scale: cotton balls on a globe.
    # Real fields are cells and streets below a pixel or two, so mid-density cloud is
    # broken by fine cellular + fBm texture; thick cores (cyclone, anvils) stay solid.
    cells = g.nodes.new('ShaderNodeTexVoronoi')
    cells.feature = 'SMOOTH_F1'
    cells.inputs['Scale'].default_value = CLOUD_CELL_SCALE
    g.links.new(g.position(), cells.inputs['Vector'])
    grain = g.nodes.new('ShaderNodeTexNoise')
    grain.inputs['Scale'].default_value = CLOUD_CELL_SCALE * 0.6
    grain.inputs['Detail'].default_value = 8.0
    grain.inputs['Roughness'].default_value = 0.65
    g.links.new(g.position(), grain.inputs['Vector'])
    texture = g.math('MULTIPLY_ADD', g.math('SUBTRACT', 1.0, cells.outputs['Distance']), 0.5,
                     g.math('MULTIPLY', grain.outputs['Fac'], 0.5))
    texture = g.smooth(texture, 0.25, 0.75)
    breakup = g.math('MULTIPLY', g.smooth(lum, 0.99, 0.5), CLOUD_BREAKUP)
    density = g.math('MULTIPLY', density,
                     g.math('SUBTRACT', 1.0, g.math('MULTIPLY', breakup, g.math('SUBTRACT', 1.0, texture))))
    diffuse = g.nodes.new('ShaderNodeBsdfDiffuse')
    diffuse.inputs['Color'].default_value = CLOUD_ALBEDO
    bump = g.nodes.new('ShaderNodeBump')
    bump.inputs['Strength'].default_value = 0.35
    bump.inputs['Distance'].default_value = 0.004
    g.links.new(lum, bump.inputs['Height'])
    g.links.new(bump.outputs['Normal'], diffuse.inputs['Normal'])
    transparent = g.nodes.new('ShaderNodeBsdfTransparent')
    mix = g.nodes.new('ShaderNodeMixShader')
    g.links.new(density, mix.inputs['Fac'])
    g.links.new(transparent.outputs['BSDF'], mix.inputs[1])
    g.links.new(diffuse.outputs['BSDF'], mix.inputs[2])
    g.links.new(mix.outputs['Shader'], out.inputs['Surface'])
    return mat


def air_material(length_scale=1.0):
    """Rayleigh scattering volume with an exponential density profile, plus thin haze.

    The profile is written in planet radii, but Cycles integrates density per world unit, so a
    sphere scaled by `length_scale` must pass it here or its optical depth grows by that factor:
    C1 v2's Jangala, scaled by thousands, rendered as an opaque grey ball that hid the surface.
    """
    mat = bpy.data.materials.new('Jangala | air')
    g = Graph(mat)
    g.nodes.remove(g.nodes['Principled BSDF'])
    out = g.nodes['Material Output']
    r = g.vmath('LENGTH', g.position(), out='Value')
    alt = g.math('MAXIMUM', g.math('SUBTRACT', r, 1.0), 0.0)

    def scatter(phase, colour, tau, scale_height, anisotropy=0.0):
        dens = g.math('MULTIPLY', g.math('EXPONENT', g.math('DIVIDE', alt, -scale_height)),
                      tau / scale_height / length_scale)
        n = g.nodes.new('ShaderNodeVolumeScatter')
        n.phase = phase
        n.inputs['Color'].default_value = (*colour, 1)
        if phase == 'HENYEY_GREENSTEIN':
            n.inputs['Anisotropy'].default_value = anisotropy
        g.links.new(dens, n.inputs['Density'])
        return n.outputs[0]

    rayleigh = scatter('RAYLEIGH', RAYLEIGH_RGB, RAYLEIGH_TAU_BLUE, AIR_SCALE_HEIGHT)
    haze = scatter('HENYEY_GREENSTEIN', (1.0, 1.0, 1.0), HAZE_TAU, HAZE_SCALE_HEIGHT, 0.7)
    add = g.nodes.new('ShaderNodeAddShader')
    g.links.new(rayleigh, add.inputs[0])
    g.links.new(haze, add.inputs[1])
    g.links.new(add.outputs[0], out.inputs['Volume'])
    return mat


def main():
    if not bpy.app.background:
        raise RuntimeError('Use background Blender')
    opts = args()
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.world = bpy.data.worlds.new('C1 | space')
    scene.world.use_nodes = True
    scene.world.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.0

    # Camera: perspective, aimed at the planet centre; lens shift places the disc.
    tan_alpha = DISC_RADIUS_PX * SENSOR_MM / (LENS_MM * FRAME_W)
    distance = 1.0 / math.sin(math.atan(tan_alpha))
    cam = bpy.data.objects.new('C1 | camera', bpy.data.cameras.new('C1 | camera'))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.lens = LENS_MM
    cam.data.sensor_width = SENSOR_MM
    cam.data.sensor_fit = 'HORIZONTAL'
    cam.data.clip_start = 0.1
    cam.data.clip_end = 100
    centre = DISC_CENTRE_PX
    cam.data.shift_x = -(centre.x - FRAME_W / 2) / FRAME_W
    cam.data.shift_y = (centre.y - FRAME_H / 2) / FRAME_W
    cam.location = Vector((0, -math.cos(CAM_ELEVATION), math.sin(CAM_ELEVATION))) * distance
    cam.rotation_euler = (-cam.location).to_track_quat('-Z', 'Y').to_euler()
    bpy.context.view_layer.update()
    print('disc centre px', tuple(centre), 'radius px', DISC_RADIUS_PX, 'distance', round(distance, 3),
          'shift', round(cam.data.shift_x, 4), round(cam.data.shift_y, 4))

    sun_world = (cam.matrix_world.to_3x3() @ SUN_IN_CAMERA).normalized()
    sun = bpy.data.objects.new('C1 | Corvus', bpy.data.lights.new('C1 | Corvus', 'SUN'))
    sun.data.energy = SUN_STRENGTH
    sun.data.color = SUN_COLOUR
    sun.data.angle = math.radians(0.5)
    sun.rotation_euler = sun_world.to_track_quat('Z', 'Y').to_euler()
    scene.collection.objects.link(sun)

    surface = sphere('Jangala | surface', 1.0)
    surface.data.materials.append(surface_material(sun_world))
    clouds = sphere('Jangala | clouds', CLOUD_RADIUS)
    clouds.data.materials.append(cloud_material())
    air = sphere('Jangala | air', AIR_RADIUS)
    air.data.materials.append(air_material())

    scene.render.engine = 'CYCLES'
    scene.cycles.samples = opts['samples']
    scene.cycles.use_denoising = True
    scene.cycles.max_bounces = 6
    scene.cycles.volume_bounces = 1
    scene.cycles.transparent_max_bounces = 16
    prefs = bpy.context.preferences.addons['cycles'].preferences
    prefs.compute_device_type = 'OPTIX'
    prefs.get_devices()
    if not [d for d in prefs.devices if d.type == 'OPTIX']:
        raise RuntimeError('OPTIX GPU required; no silent CPU fallback')
    for device in prefs.devices:
        device.use = device.type == 'OPTIX'
    scene.cycles.device = 'GPU'
    scene.render.resolution_x = 3840
    scene.render.resolution_y = 2160
    scene.render.resolution_percentage = opts['scale']
    scene.render.film_transparent = True
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGBA'
    scene.render.image_settings.color_depth = '8'
    scene.render.use_compositing = False
    scene.view_settings.view_transform = 'AgX'
    scene.view_settings.look = 'AgX - Base Contrast'

    out = Path(opts['out'])
    if opts['frames']:
        spin = clouds.material_slots[0].material.node_tree.nodes['cloud spin'].outputs[0]
        for f in range(opts['frames']):
            spin.default_value = CLOUD_FACING_U - 0.25 - CLOUD_DRIFT_DEG_PER_FRAME * f / 360.0
            scene.render.filepath = str(BASE / 'layers' / 'planet-frames' / ('planet-%04d.png' % f))
            bpy.ops.render.render(write_still=True)
        print('Wrote', opts['frames'], 'frames to', BASE / 'layers' / 'planet-frames')
        return
    out.parent.mkdir(parents=True, exist_ok=True)
    scene.render.filepath = str(out)
    bpy.ops.render.render(write_still=True)
    print('Wrote', out)


if __name__ == '__main__':
    main()
