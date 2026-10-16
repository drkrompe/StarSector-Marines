"""Jangala as a lit 3D sphere for shot C1, alpha background, one sun.

blender --background --factory-startup --python render_planet.py [-- options]

Options:
  --scale P        resolution percentage of 3840x2160 (default 100; 25 for previews)
  --samples N      Cycles samples (default 128)
  --out PATH       output PNG (default layers/planet.png)
  --frames N       render an N-frame cloud-drift sequence to layers/planet-frames/
                   instead of a still (24 fps; the cloud shell turns slowly over the
                   surface while the surface stays fixed)

Builds three shells from the equirectangular masters in assets/:
  surface  radius 1.000  planet-albedo-v1.png, glossier water from an ocean mask
           derived from the albedo (blue over green)
  clouds   radius 1.006  planet-clouds-v1.png as density -> alpha and bump; Cycles
           casts their soft shadows onto the surface
  air      radius 1.016  thin emissive limb shell weighted by grazing angle and
           by the sun side, so the dark limb does not glow

Layout follows c1-layout.md: a whole distant globe, centre near (1290, 560) and about
760 px across on a 1920x1080 frame (double that on the 3840x2160 render), with the
disc and its glow entirely inside the canvas and everything else fully transparent.
The camera aims at the planet centre and lens shift places the disc, so the limb
projects as an exact circle. Sun from the upper left and somewhat toward camera, so
the lit face is gibbous and a soft terminator crosses the lower right. Equirect
seams are turned to the far side of the planet.

Derived map: assets/planet-clouds-v1-eyefix.png is planet-clouds-v1.png with the
cyclone eye (a ~7 px pure-black disc centred at about (1203, 231) of 1774x887)
painted out by a PIL edit: inside r<9 px the value is raised to at least a radial
fill from 42% of the surrounding eyewall brightness (mean 227) at the centre up to
the wall, feathered to the original over r 9-13 px. No regeneration; everything
else is byte-for-byte the generated map. The eye now reads as a small dim clearing.
"""
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector

BASE = Path(__file__).resolve().parent
ALBEDO = BASE / 'assets' / 'planet-albedo-v1.png'
# The cyclone eye was a hard black dot in the generated map; -eyefix paints it out into a dim
# clearing (PIL edit; see 'Derived map' in this module's docstring).
CLOUDS = BASE / 'assets' / 'planet-clouds-v1-eyefix.png'

# Whole globe on a 1920x1080 frame (pixels, y down): centre and radius of the disc.
FRAME_W, FRAME_H = 1920, 1080
DISC_CENTRE_PX = Vector((1290.0, 560.0))
DISC_RADIUS_PX = 380.0
LENS_MM, SENSOR_MM = 50.0, 36.0
CAM_ELEVATION = math.radians(20)      # above the orbital plane: northern hemisphere tilts toward camera
SURFACE_SPIN = math.radians(150)       # which longitude faces the camera (seam to the back)
CLOUD_SPIN = math.radians(150)
CLOUD_DRIFT_DEG_PER_FRAME = 0.012     # --frames: slow drift of the cloud shell
SUN_IN_CAMERA = Vector((-0.62, 0.50, 0.50))  # toward the light; x right, y up, z to camera
SUN_STRENGTH = 3.2
CLOUD_LEVELS = (0.38, 0.97)           # cloud-map luminance window mapped to alpha
CLOUD_GAMMA = 1.7                     # >1: only cores dense, thin cloud translucent
CLOUD_PEAK_ALPHA = 0.9


def args():
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    opts = {'scale': 100, 'samples': 128, 'out': str(BASE / 'layers' / 'planet.png'), 'frames': 0}
    for i, a in enumerate(argv):
        if a.startswith('--') and a[2:] in opts:
            v = argv[i + 1]
            opts[a[2:]] = v if a == '--out' else int(v)
    return opts


def image(path, colorspace):
    img = bpy.data.images.load(str(path), check_existing=True)
    img.colorspace_settings.name = colorspace
    return img


def spun_coords(nodes, links, spin):
    tex = nodes.new('ShaderNodeTexCoord')
    mapping = nodes.new('ShaderNodeMapping')
    mapping.inputs['Rotation'].default_value = (0, 0, spin)
    links.new(tex.outputs['Object'], mapping.inputs['Vector'])
    return mapping


def sphere(name, radius):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=256, ring_count=128, radius=radius)
    obj = bpy.context.active_object
    obj.name = name
    bpy.ops.object.shade_smooth()
    return obj


def mix_rgb(nodes, links, blend, fac, a, b):
    """Colour mix node; each of fac/a/b is a socket or a constant. Returns the result socket."""
    n = nodes.new('ShaderNodeMix')
    n.data_type = 'RGBA'
    n.blend_type = blend
    for sock, v in ((n.inputs[0], fac), (n.inputs[6], a), (n.inputs[7], b)):
        if isinstance(v, (int, float, tuple)):
            sock.default_value = v
        else:
            links.new(v, sock)
    return n.outputs[2]


def surface_material():
    mat = bpy.data.materials.new('Jangala | surface')
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    bsdf = nodes['Principled BSDF']
    coords = spun_coords(nodes, links, SURFACE_SPIN)
    tex = nodes.new('ShaderNodeTexImage')
    tex.image = image(ALBEDO, 'sRGB')
    tex.projection = 'SPHERE'
    tex.interpolation = 'Cubic'
    links.new(coords.outputs['Vector'], tex.inputs['Vector'])
    # Grade toward the earlier accepted plate: a little less saturated and darker overall,
    # deeper blue water, darker forest with broad low-frequency variation so the land
    # greens are not one uniform tone.
    hsv = nodes.new('ShaderNodeHueSaturation')
    hsv.inputs['Saturation'].default_value = 0.9
    hsv.inputs['Value'].default_value = 0.88
    links.new(tex.outputs['Color'], hsv.inputs['Color'])
    variation = nodes.new('ShaderNodeTexNoise')
    variation.inputs['Scale'].default_value = 7.0
    variation.inputs['Detail'].default_value = 6.0
    links.new(coords.outputs['Vector'], variation.inputs['Vector'])
    forest = nodes.new('ShaderNodeValToRGB')
    forest.color_ramp.elements[0].position = 0.35
    forest.color_ramp.elements[0].color = (0.52, 0.58, 0.40, 1)
    forest.color_ramp.elements[1].position = 0.68
    forest.color_ramp.elements[1].color = (0.86, 0.84, 0.74, 1)
    links.new(variation.outputs['Fac'], forest.inputs['Fac'])
    land_col = mix_rgb(nodes, links, 'MULTIPLY', 1.0, hsv.outputs['Color'], forest.outputs['Color'])
    sea_col = mix_rgb(nodes, links, 'MULTIPLY', 1.0, hsv.outputs['Color'], (0.80, 0.93, 1.12, 1))

    # Ocean mask: blue over green. Deep water ~1, turquoise shelves ~0.5, land/ice ~0.
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(tex.outputs['Color'], sep.inputs['Color'])
    diff = nodes.new('ShaderNodeMath')
    diff.operation = 'SUBTRACT'
    links.new(sep.outputs['Blue'], diff.inputs[0])
    links.new(sep.outputs['Green'], diff.inputs[1])
    mask = nodes.new('ShaderNodeMapRange')
    mask.inputs['From Min'].default_value = 0.0
    mask.inputs['From Max'].default_value = 0.12
    links.new(diff.outputs[0], mask.inputs['Value'])
    base = mix_rgb(nodes, links, 'MIX', mask.outputs['Result'], land_col, sea_col)
    links.new(base, bsdf.inputs['Base Color'])
    rough = nodes.new('ShaderNodeMapRange')
    rough.inputs['To Min'].default_value = 0.88
    rough.inputs['To Max'].default_value = 0.32
    links.new(mask.outputs['Result'], rough.inputs['Value'])
    links.new(rough.outputs['Result'], bsdf.inputs['Roughness'])
    spec = nodes.new('ShaderNodeMapRange')
    spec.inputs['To Min'].default_value = 0.15
    spec.inputs['To Max'].default_value = 0.55
    links.new(mask.outputs['Result'], spec.inputs['Value'])
    links.new(spec.outputs['Result'], bsdf.inputs['Specular IOR Level'])

    # Faint relief on land from albedo luminance (oceans flattened by the mask).
    lum = nodes.new('ShaderNodeRGBToBW')
    links.new(tex.outputs['Color'], lum.inputs['Color'])
    land = nodes.new('ShaderNodeMath')
    land.operation = 'SUBTRACT'
    land.inputs[0].default_value = 1.0
    links.new(mask.outputs['Result'], land.inputs[1])
    height = nodes.new('ShaderNodeMath')
    height.operation = 'MULTIPLY'
    links.new(lum.outputs['Val'], height.inputs[0])
    links.new(land.outputs[0], height.inputs[1])
    bump = nodes.new('ShaderNodeBump')
    bump.inputs['Strength'].default_value = 0.12
    bump.inputs['Distance'].default_value = 0.002
    links.new(height.outputs[0], bump.inputs['Height'])
    links.new(bump.outputs['Normal'], bsdf.inputs['Normal'])
    return mat


def cloud_material():
    mat = bpy.data.materials.new('Jangala | clouds')
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    nodes.remove(nodes['Principled BSDF'])
    out = nodes['Material Output']
    coords = spun_coords(nodes, links, CLOUD_SPIN)
    tex = nodes.new('ShaderNodeTexImage')
    tex.image = image(CLOUDS, 'Non-Color')
    tex.projection = 'SPHERE'
    tex.interpolation = 'Cubic'
    links.new(coords.outputs['Vector'], tex.inputs['Vector'])
    lum = nodes.new('ShaderNodeRGBToBW')
    links.new(tex.outputs['Color'], lum.inputs['Color'])
    # Thin the generated cover hard. Fine noise jitters the threshold so edges feather
    # into wisps instead of ending as clean blobs; a levels window plus a gamma keeps
    # only the cores dense, and the peak stays below 1 so thin cloud stays translucent.
    wisp = nodes.new('ShaderNodeTexNoise')
    wisp.inputs['Scale'].default_value = 55.0
    wisp.inputs['Detail'].default_value = 10.0
    wisp.inputs['Roughness'].default_value = 0.6
    links.new(coords.outputs['Vector'], wisp.inputs['Vector'])
    jitter = nodes.new('ShaderNodeMath')
    jitter.operation = 'MULTIPLY_ADD'
    jitter.inputs[1].default_value = 0.26
    jitter.inputs[2].default_value = -0.13
    links.new(wisp.outputs['Fac'], jitter.inputs[0])
    lum_j = nodes.new('ShaderNodeMath')
    lum_j.operation = 'ADD'
    links.new(lum.outputs['Val'], lum_j.inputs[0])
    links.new(jitter.outputs[0], lum_j.inputs[1])
    levels = nodes.new('ShaderNodeMapRange')
    levels.inputs['From Min'].default_value = CLOUD_LEVELS[0]
    levels.inputs['From Max'].default_value = CLOUD_LEVELS[1]
    levels.inputs['To Max'].default_value = CLOUD_PEAK_ALPHA
    links.new(lum_j.outputs[0], levels.inputs['Value'])
    density = nodes.new('ShaderNodeMath')
    density.operation = 'POWER'
    density.inputs[1].default_value = CLOUD_GAMMA
    links.new(levels.outputs['Result'], density.inputs[0])
    diffuse = nodes.new('ShaderNodeBsdfDiffuse')
    diffuse.inputs['Color'].default_value = (0.80, 0.81, 0.83, 1)
    bump = nodes.new('ShaderNodeBump')
    bump.inputs['Strength'].default_value = 0.15
    bump.inputs['Distance'].default_value = 0.003
    links.new(lum.outputs['Val'], bump.inputs['Height'])
    links.new(bump.outputs['Normal'], diffuse.inputs['Normal'])
    transparent = nodes.new('ShaderNodeBsdfTransparent')
    mix = nodes.new('ShaderNodeMixShader')
    links.new(density.outputs[0], mix.inputs['Fac'])
    links.new(transparent.outputs['BSDF'], mix.inputs[1])
    links.new(diffuse.outputs['BSDF'], mix.inputs[2])
    links.new(mix.outputs['Shader'], out.inputs['Surface'])
    mat.node_tree.nodes.active = out
    return mat


AIR_RADIUS = 1.016
AIR_SCALE_HEIGHT = 0.0045              # glow e-folding height above the limb, planet radii


def air_material(sun_world):
    """Limb glow from the ray's closest approach to the planet centre (impact parameter b).

    Above the limb (b > 1) the glow falls off exponentially with height, so the rim is
    brightest right on the surface and fades into space instead of ending in a hard ring.
    Over the disc (b < 1) a faint haze rises toward the limb. Both fade on the night side.
    """
    mat = bpy.data.materials.new('Jangala | air')
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    nodes.remove(nodes['Principled BSDF'])
    out = nodes['Material Output']

    def math_node(op, a=None, b=None, clamp=False):
        n = nodes.new('ShaderNodeMath')
        n.operation = op
        n.use_clamp = clamp
        for i, v in enumerate((a, b)):
            if v is None:
                continue
            if isinstance(v, (int, float)):
                n.inputs[i].default_value = v
            else:
                links.new(v, n.inputs[i])
        return n.outputs[0]

    geo = nodes.new('ShaderNodeNewGeometry')
    cosine = nodes.new('ShaderNodeVectorMath')
    cosine.operation = 'DOT_PRODUCT'
    links.new(geo.outputs['Normal'], cosine.inputs[0])
    links.new(geo.outputs['Incoming'], cosine.inputs[1])
    c2 = math_node('MULTIPLY', cosine.outputs['Value'], cosine.outputs['Value'])
    sin = math_node('SQRT', math_node('SUBTRACT', 1.0, c2, clamp=True))
    b = math_node('MULTIPLY', sin, AIR_RADIUS)
    height = math_node('SUBTRACT', b, 1.0)
    outer = math_node('EXPONENT', math_node('DIVIDE', math_node('MAXIMUM', height, 0.0), -AIR_SCALE_HEIGHT))
    ramp = nodes.new('ShaderNodeMapRange')
    ramp.interpolation_type = 'SMOOTHSTEP'
    ramp.inputs['From Min'].default_value = 0.94
    ramp.inputs['From Max'].default_value = 1.0
    links.new(b, ramp.inputs['Value'])
    inner = math_node('MULTIPLY', math_node('POWER', ramp.outputs['Result'], 3.0), 0.6)
    above = math_node('GREATER_THAN', height, 0.0)
    glow = math_node('ADD', math_node('MULTIPLY', above, outer),
                     math_node('MULTIPLY', math_node('SUBTRACT', 1.0, above), inner))

    # Day side from object-space position, not the shading normal: Cycles flips a back
    # face's normal toward the ray, so the far side of the shell just past the night limb
    # read as sunlit and drew a hard pale ring around the dark side.
    place = nodes.new('ShaderNodeTexCoord')
    radial = nodes.new('ShaderNodeVectorMath')
    radial.operation = 'NORMALIZE'
    links.new(place.outputs['Object'], radial.inputs[0])
    sun_dot = nodes.new('ShaderNodeVectorMath')
    sun_dot.operation = 'DOT_PRODUCT'
    sun_dot.inputs[1].default_value = tuple(sun_world)
    links.new(radial.outputs['Vector'], sun_dot.inputs[0])
    day = nodes.new('ShaderNodeMapRange')
    day.interpolation_type = 'SMOOTHSTEP'
    day.inputs['From Min'].default_value = 0.0
    day.inputs['From Max'].default_value = 0.65
    links.new(sun_dot.outputs['Value'], day.inputs['Value'])
    fac = math_node('MULTIPLY', glow, day.outputs['Result'], clamp=True)

    emit = nodes.new('ShaderNodeEmission')
    emit.inputs['Color'].default_value = (0.32, 0.56, 1.0, 1)
    emit.inputs['Strength'].default_value = 1.9
    transparent = nodes.new('ShaderNodeBsdfTransparent')
    mix = nodes.new('ShaderNodeMixShader')
    links.new(fac, mix.inputs['Fac'])
    links.new(transparent.outputs['BSDF'], mix.inputs[1])
    links.new(emit.outputs['Emission'], mix.inputs[2])
    links.new(mix.outputs['Shader'], out.inputs['Surface'])
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
    sun.data.color = (1.0, 0.97, 0.9)
    sun.data.angle = math.radians(0.5)
    sun.rotation_euler = sun_world.to_track_quat('Z', 'Y').to_euler()
    scene.collection.objects.link(sun)

    surface = sphere('Jangala | surface', 1.0)
    surface.data.materials.append(surface_material())
    clouds = sphere('Jangala | clouds', 1.006)
    clouds.data.materials.append(cloud_material())
    air = sphere('Jangala | air', AIR_RADIUS)
    air.data.materials.append(air_material(sun_world))
    # Camera rays only: an emissive shell would otherwise light the night side.
    for attr in ('visible_shadow', 'visible_diffuse', 'visible_glossy',
                 'visible_transmission', 'visible_volume_scatter'):
        setattr(air, attr, False)

    scene.render.engine = 'CYCLES'
    scene.cycles.samples = opts['samples']
    scene.cycles.use_denoising = True
    scene.cycles.max_bounces = 6
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
        rot = clouds.material_slots[0].material.node_tree.nodes['Mapping'].inputs['Rotation']
        for f in range(opts['frames']):
            rot.default_value[2] = CLOUD_SPIN + math.radians(CLOUD_DRIFT_DEG_PER_FRAME * f)
            scene.render.filepath = str(BASE / 'layers' / 'planet-frames' / ('planet-%04d.png' % f))
            bpy.ops.render.render(write_still=True)
        print('Wrote', opts['frames'], 'frames to', BASE / 'layers' / 'planet-frames')
        return
    out.parent.mkdir(parents=True, exist_ok=True)
    scene.render.filepath = str(out)
    bpy.ops.render.render(write_still=True)
    print('Wrote', out)


main()
