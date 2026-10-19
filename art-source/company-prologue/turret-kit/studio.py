"""The review studio: Cycles, AgX, the grey world and three suns.

catalog.py renders the turrets under it and calibrate_paints.py solves each
sprite paint's base colour under it, so the two cannot drift apart: a paint
calibrated here is the paint the review sheet shows.
"""
import math
import bpy


def material(name, color, metallic=0.0, roughness=.6):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = (*color, 1)
    b.inputs['Metallic'].default_value = metallic
    b.inputs['Roughness'].default_value = roughness
    return m


def hull_mats():
    """Stand-in hull materials (the XIV Legion's), as a hull driver passes them."""
    return {'armour': material('XIV orange armour', (.62, .19, .055), .25, .55),
            'plate': material('XIV grey hull plating', (.30, .27, .25), .35, .6),
            'steel': material('Gunmetal machinery', (.16, .16, .17), .7, .5),
            'dark': material('Recess and machinery', (.035, .035, .04), .5),
            'glass': material('Sight glass', (.03, .05, .06), .2, .1)}


def setup(scene, samples=32):
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = samples
    scene.cycles.use_denoising = True
    scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage = 1920, 1080, 100
    scene.view_settings.view_transform = 'AgX'
    world = bpy.data.worlds.new('w')
    scene.world = world
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs[0].default_value = (.74, .76, .78, 1)
    world.node_tree.nodes['Background'].inputs[1].default_value = .55
    for name, rot, energy in (('Key', (50, 0, -35), 4.0), ('Fill', (65, 0, 140), 1.2), ('Rim', (20, 0, 200), 2.0)):
        d = bpy.data.lights.new(name, 'SUN')
        d.energy = energy
        d.angle = math.radians(3)
        ob = bpy.data.objects.new(name, d)
        scene.collection.objects.link(ob)
        ob.rotation_euler = [math.radians(a) for a in rot]
