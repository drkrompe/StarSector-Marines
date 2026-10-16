"""Bounded 3D exhaust for the retained Second Wind landing scene.

Call apply_thruster_fx(scene) after loading the source blend. Source frames are
12 fps; retime all generated FX fcurves with the rest of the scene if needed.
No simulation cache, image planes or opaque plume surfaces are used.
"""
import bpy

COLLECTION = 'LANDING FX | bounded exhaust'
CONTACT_FRAME = 744
SHUTDOWN_FRAME = 758


def _math(nodes, links, op, a, b=None):
    n = nodes.new('ShaderNodeMath'); n.operation = op
    for idx, value in enumerate((a, b)):
        if value is None: continue
        if hasattr(value, 'node'): links.new(value, n.inputs[idx])
        else: n.inputs[idx].default_value = value
    return n.outputs[0]


def _volume(name, color, density, emission, dust=False):
    mat = bpy.data.materials.new(name); mat.use_nodes = True
    nodes = mat.node_tree.nodes; nodes.clear(); links = mat.node_tree.links
    out = nodes.new('ShaderNodeOutputMaterial')
    tex = nodes.new('ShaderNodeTexCoord')
    # Generated coordinates make the field independent of view and world scale.
    sub = nodes.new('ShaderNodeVectorMath'); sub.operation = 'SUBTRACT'
    links.new(tex.outputs['Generated'], sub.inputs[0]); sub.inputs[1].default_value = (.5,.5,.5)
    sep = nodes.new('ShaderNodeSeparateXYZ'); links.new(sub.outputs[0],sep.inputs[0])
    x2 = _math(nodes,links,'MULTIPLY',sep.outputs['X'],sep.outputs['X'])
    y2 = _math(nodes,links,'MULTIPLY',sep.outputs['Y'],sep.outputs['Y'])
    r2 = _math(nodes,links,'ADD',x2,y2)
    # Ellipsoidal soft bounds eliminate the cube boundary. Jet narrows at its top.
    z2 = _math(nodes,links,'MULTIPLY',sep.outputs['Z'],sep.outputs['Z'])
    ell = _math(nodes,links,'ADD',r2,z2)
    edge = _math(nodes,links,'MAXIMUM',_math(nodes,links,'SUBTRACT',1,_math(nodes,links,'MULTIPLY',ell,4)),0)
    edge = _math(nodes,links,'POWER',edge,2)
    noise = nodes.new('ShaderNodeTexNoise'); noise.noise_dimensions='4D'
    noise.inputs['Scale'].default_value = 6 if dust else 4
    noise.inputs['Detail'].default_value=2; noise.inputs['Roughness'].default_value=.7
    links.new(tex.outputs['Generated'],noise.inputs['Vector'])
    driver=noise.inputs['W'].driver_add('default_value').driver; driver.expression='frame * 0.13'
    ragged = _math(nodes,links,'POWER',noise.outputs['Fac'],2)
    field = _math(nodes,links,'MULTIPLY',edge,ragged)
    strength=nodes.new('ShaderNodeValue'); strength.name='Thrust envelope'
    for f,v in ((680,1),(CONTACT_FRAME,.65),(750,.25),(SHUTDOWN_FRAME,0),(800,0)):
        strength.outputs[0].default_value=v; strength.outputs[0].keyframe_insert('default_value',frame=f)
    field = _math(nodes,links,'MULTIPLY',field,strength.outputs[0])
    vol=nodes.new('ShaderNodeVolumePrincipled')
    vol.inputs['Color'].default_value=(*color,1)
    vol.inputs['Anisotropy'].default_value=.25
    links.new(_math(nodes,links,'MULTIPLY',field,density),vol.inputs['Density'])
    vol.inputs['Emission Color'].default_value=(*color,1)
    links.new(_math(nodes,links,'MULTIPLY',field,emission),vol.inputs['Emission Strength'])
    links.new(vol.outputs[0],out.inputs['Volume'])
    return mat


def _box(collection,name,location,scale,material,parent=None):
    # A closed cube is only a volume container: the material has no surface.
    verts=[(x,y,z) for x in (-1,1) for y in (-1,1) for z in (-1,1)]
    faces=[(0,4,6,2),(1,3,7,5),(0,1,5,4),(2,6,7,3),(0,2,3,1),(4,5,7,6)]
    mesh=bpy.data.meshes.new(name); mesh.from_pydata(verts,[],[tuple(reversed(face)) for face in faces]); mesh.update()
    obj=bpy.data.objects.new(name,mesh); collection.objects.link(obj)
    obj.parent=parent; obj.location=location; obj.scale=scale; obj.data.materials.append(material)
    return obj


def apply_thruster_fx(scene):
    """Replace four solid controls with soft volume exhaust; return FX collection.

    Uses translation constraints so the old animated cone scale cannot pull the
    nozzle off the hull. Original actions/meshes/materials are left intact.
    Dust bounds are deliberately low and local (roughly 14m wide, under 0.7m high).
    """
    old=bpy.data.collections.get(COLLECTION)
    if old:
        for obj in list(old.objects):
            mesh = obj.data if obj.type == 'MESH' else None
            bpy.data.objects.remove(obj,do_unlink=True)
            if mesh and mesh.users == 0: bpy.data.meshes.remove(mesh)
        bpy.data.collections.remove(old)
    for material in list(bpy.data.materials):
        if material.name.startswith('LANDING FX |') and material.users == 0:
            bpy.data.materials.remove(material)
    coll=bpy.data.collections.new(COLLECTION); scene.collection.children.link(coll)
    core=_volume('LANDING FX | pale ionized core',(.66,.83,1),.008,10)
    exhaust=_volume('LANDING FX | transparent hot mixing',(.69,.78,.81),.09,.5)
    dust=_volume('LANDING FX | pad grit',(.46,.39,.28),.32,0,True)
    controls=sorted([o for o in scene.objects if o.name.startswith('LIFT VECTOR | placeholder')],key=lambda o:o.name)
    if len(controls)!=4: raise ValueError('Expected exactly four retained LIFT VECTOR controls')
    for i,control in enumerate(controls):
        control.hide_render=True
        anchor=bpy.data.objects.new('LANDING FX | nozzle anchor %02d'%i,None); coll.objects.link(anchor)
        copy=anchor.constraints.new('COPY_LOCATION'); copy.target=control
        # Source control centre is 3.75m below the fixed nozzle at full thrust.
        _box(coll,'LANDING FX | core %02d'%i,(0,0,1.8),(.45,.45,2.3),core,anchor)
        _box(coll,'LANDING FX | mixing %02d'%i,(0,0,1.0),(2.2,2.2,2.65),exhaust,anchor)
        ground=bpy.data.objects.new('LANDING FX | ground anchor %02d'%i,None); coll.objects.link(ground)
        c=ground.constraints.new('COPY_LOCATION'); c.target=control; c.use_z=False
        ground.location.z=.28
        _box(coll,'LANDING FX | ground wash %02d'%i,(0,0,0),(7,7,.32),dust,ground)
    scene['landing_fx_note']='Bounded procedural volumes; source-frame contact 744, exhaust off 758. No fluid/thermal simulation.'
    return coll
