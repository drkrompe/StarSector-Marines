"""Metre-scale, untextured surface yard for the provisional Second Wind animatic.

+X is bow/flight, +Y port. Four 10 m contact pads meet the ship at Z=5 m.
All inhabited/service volumes sit outside the landing and lift-exhaust envelope.
No Blender work happens on import. The caller owns animation and collection setup.
"""


def build_yard(bpy, helpers):
    """Build static yard plus four retracted clamp wedges; return animation metadata.

    helpers accepts a dict or namespace containing box, cyl, material, materials.
    A clamp translates from its initial location to clamp_engaged_locations only
    after hull contact; it never swings across the side access volume.
    """
    def helper(key):
        return helpers[key] if isinstance(helpers, dict) else getattr(helpers, key)

    box, cyl, material = (helper(k) for k in ('box', 'cyl', 'material'))
    concrete = material('Yard concrete', (0.29, 0.31, 0.30))
    steel = material('Yard cradle steel', (0.21, 0.25, 0.26))
    dark = material('Yard trench graphite', (0.075, 0.09, 0.095))
    light = material('Yard markings chalk', (0.68, 0.68, 0.60))
    caution = material('Yard safety ochre', (0.53, 0.46, 0.25))
    walls = material('Yard service walls', (0.38, 0.40, 0.37))
    people_mat = material('Yard human proxy', (0.64, 0.56, 0.43))
    objects, clamps, engaged, supports, trenches, people = [], [], [], [], [], []

    def block(name, loc, dims, mat, bevel=0):
        obj = box('YARD_' + name, loc, dims, mat, bevel=bevel)
        objects.append(obj)
        return obj

    def cylinder(name, loc, radius, depth, mat):
        obj = cyl('YARD_' + name, loc, radius, depth, mat)
        objects.append(obj)
        return obj

    # Apron top is exactly Z=0. Low paint/inlays are the only flight-lane objects.
    apron = block('apron_240x220m', (0, 0, -0.6), (240, 220, 1.2), concrete)
    block('surrounding_ground', (0, 0, -1.3), (650, 550, 1), walls)
    block('negative_X_approach', (-215, 0, -0.14), (190, 92, 0.25), concrete)
    for x in (-290, -265, -240, -215, -190, -165, -140, -108, -84, -60):
        block('approach_center_%s' % x, (x, 0, 0.02), (12, 0.7, 0.035), light)
    for y in (-69, 69):
        block('landing_boundary_%s' % y, (0, y, 0.025), (215, 0.8, 0.04), caution)
    for x in (-108, 108):
        block('landing_end_%s' % x, (x, 0, 0.025), (0.8, 138, 0.04), caution)
    # Low longitudinal rails stay inboard of nozzle footprints at Y=27..28.5.
    for y in (-15, 15):
        block('cradle_rail_%s' % y, (9.75, y, 0.45), (102, 2, 0.9), steel)
    contact_points = [(x, y, 5.0) for x in (-31.5, 51.0) for y in (-15.0, 15.0)]
    for i, (x, y, z) in enumerate(contact_points):
        supports.append(block('contact_pad_%s' % i, (x, y, 2.5), (10, 10, 5), steel, 0.2))
        block('pad_contact_face_%s' % i, (x, y, 4.95), (9.4, 9.4, 0.1), light)
        sign = 1 if y > 0 else -1
        block('clamp_slide_%s' % i, (x, y + sign * 5.7, 1.0), (6, 5, 2), steel)
        # Wedge points inward. Initial inner edge is Y=+/-19; engagement
        # moves it 1 m inwards beside the pad, with only a 0.6 m retaining lip.
        cy = y + sign * 6.0
        verts = [(-3, -2, 0), (3, -2, 0), (3, 2, 0), (-3, 2, 0),
                 (-3, -2, 3.6), (3, -2, 3.6), (3, 2, 2), (-3, 2, 2)]
        verts = [(vx, vy * sign, vz) for vx, vy, vz in verts]
        mesh = bpy.data.meshes.new('YARD_clamp_wedge_mesh_%s' % i)
        mesh.from_pydata(verts, [], [(0, 3, 2, 1), (0, 1, 5, 4), (1, 2, 6, 5),
                                   (2, 3, 7, 6), (3, 0, 4, 7), (4, 5, 6, 7)])
        mesh.update()
        obj = bpy.data.objects.new('YARD_retaining_wedge_%s' % i, mesh)
        bpy.context.collection.objects.link(obj)
        obj.location = (x, cy, 2)
        obj.data.materials.append(caution)
        obj['engage_after_support'] = True
        obj['slide_distance_m'] = 1.0
        clamps.append(obj)
        objects.append(obj)
        engaged.append((x, cy - sign, 2))
    # Recess-read dark blast inlays: low rim plus cross grating, no raised wall.
    exhaust_points = [(49.5, 27, 0), (49.5, -27, 0), (-25.5, 28.5, 0), (-25.5, -28.5, 0)]
    for i, (x, y, _) in enumerate(exhaust_points):
        trenches.append(block('blast_trench_%s' % i, (x, y, 0.012), (16, 12, 0.02), dark))
        for side in (-1, 1):
            block('trench_rim_%s_%s' % (i, side), (x, y + side * 6, 0.04), (17, 0.4, 0.08), steel)
        for dx in (-5, 0, 5):
            block('trench_grate_%s_%s' % (i, dx), (x + dx, y, 0.035), (0.35, 11.5, 0.04), steel)

    # Port service frontage readable through a side window; no reaching booms.
    for i, (x, y, length, width, height) in enumerate(((-60, 117, 38, 28, 12),
                                                     (8, 124, 44, 30, 15),
                                                     (77, 120, 30, 26, 11),
                                                     (35, -134, 48, 30, 13))):
        block('shed_%s' % i, (x, y, height / 2), (length, width, height), walls, 0.3)
        block('shed_roof_%s' % i, (x, y, height + 0.3), (length + 2, width + 2, 0.6), steel)
        block('shed_door_%s' % i, (x, y - width / 2 - 0.04, 4), (10, 0.12, 8), dark)
    for i, (x, y) in enumerate(((-25, 106), (85, -125))):
        block('crane_base_%s' % i, (x, y, 1), (8, 8, 2), steel)
        block('crane_mast_%s' % i, (x, y, 11), (2, 2, 20), caution)
        block('crane_boom_%s' % i, (x + 8, y, 21), (20, 1.5, 1.5), caution)
        cylinder('crane_cable_%s' % i, (x + 17, y, 14), 0.07, 13, dark)
    truck = block('service_truck_12m', (-48, 84, 1.5), (12, 4, 2.2), walls, 0.25)
    block('truck_cab', (-43.8, 84, 3), (3.2, 3.8, 2), steel, 0.15)
    for x in (-52, -45):
        for y in (81.9, 86.1):
            wheel = cylinder('truck_wheel', (x, y, 0.8), 0.8, 0.55, dark)
            wheel.rotation_euler[0] = 1.57079632679

    # Two protected 1.8 m workers beside the parked truck, beyond Y=69
    # landing boundary. Their separated limbs, sealed helmets and packs read
    # as people without any exposed face or implied breathable atmosphere.
    workwear = material('Yard protected workwear ochre', (0.66, 0.43, 0.18))
    seal = material('Yard environmental seals', (0.12, 0.15, 0.15))
    visor = material('Yard sealed fullface visor', (0.095, 0.17, 0.18))
    outdoor_people = []
    for i, (x, y) in enumerate(((-48.5, 88.5), (-46.8, 88.0))):
        parts = []
        for dx in (-0.135, 0.135):
            parts.append(block('worker_%s_boot' % i, (x + dx, y - 0.04, 0.10),
                               (0.22, 0.36, 0.20), seal, 0.035))
            parts.append(block('worker_%s_leg' % i, (x + dx, y, 0.49),
                               (0.19, 0.23, 0.62), workwear, 0.035))
        parts.append(block('worker_%s_torso' % i, (x, y, 1.08),
                           (0.48, 0.30, 0.62), workwear, 0.06))
        for dx in (-0.34, 0.34):
            parts.append(block('worker_%s_arm' % i, (x + dx, y, 1.02),
                               (0.17, 0.21, 0.62), workwear, 0.035))
            parts.append(block('worker_%s_glove' % i, (x + dx, y - 0.015, 0.69),
                               (0.18, 0.23, 0.16), seal, 0.035))
        parts.append(block('worker_%s_collar' % i, (x, y, 1.43),
                           (0.31, 0.30, 0.11), seal, 0.025))
        parts.append(block('worker_%s_sealed_helmet' % i, (x, y, 1.62),
                           (0.36, 0.35, 0.36), workwear, 0.10))
        parts.append(block('worker_%s_fullface_visor' % i, (x, y - 0.176, 1.61),
                           (0.28, 0.045, 0.23), visor, 0.05))
        parts.append(block('worker_%s_environment_pack' % i, (x, y + 0.23, 1.12),
                           (0.34, 0.22, 0.45), steel, 0.045))
        outdoor_people.extend(parts)
    people.extend(outdoor_people)

    # Closed booth: low wall, uninterrupted glazing band, solid roof. Scale
    # figures can be seen from the port-facing rear/window view at close range.
    booth = block('control_booth_lower', (71, 86, 0.6), (9, 7, 1.2), walls)
    block('control_booth_roof', (71, 86, 3.35), (9.6, 7.6, 0.4), steel)
    glass = material('Yard booth clear glass', (0.32, 0.43, 0.43))
    if hasattr(glass, 'use_nodes'):
        glass.use_nodes = True
        bsdf = glass.node_tree.nodes.get('Principled BSDF')
        if bsdf:
            transmission = bsdf.inputs.get('Transmission Weight') or bsdf.inputs.get('Transmission')
            if transmission is not None:
                transmission.default_value = 0.88
            bsdf.inputs['Roughness'].default_value = 0.12
    for y in (82.55, 89.45):
        block('booth_glazing', (71, y, 2.15), (8.8, 0.1, 1.9), glass)
    for x in (66.55, 75.45):
        block('booth_end_glazing', (x, 86, 2.15), (0.1, 6.8, 1.9), glass)
        for y in (82.55, 89.45):
            block('booth_post', (x, y, 2.15), (0.18, 0.18, 2.0), steel)
    block('booth_console', (71, 84, 1.15), (5, 0.8, 0.7), steel)
    for x in (69.7, 72.2):
        body = cylinder('human_1p8m_body', (x, 85, 1.0), 0.23, 1.25, people_mat)
        head = cylinder('human_1p8m_head', (x, 85, 1.65), 0.16, 0.3, people_mat)
        people.extend((body, head))
        for dx in (-0.12, 0.12):
            people.append(block('human_leg', (x + dx, 85, 0.3), (0.17, 0.21, 0.6), people_mat))
    return {'objects': objects, 'apron': apron, 'clamps': clamps,
            'clamp_engaged_locations': engaged, 'contact_points': contact_points,
            'supports': supports, 'blast_trenches': trenches, 'exhaust_points': exhaust_points,
            'control_booth': booth, 'human_proxies': people, 'outdoor_human_proxies': outdoor_people,
            'service_truck': truck,
            'clear_volume': ((-100, -65, 0), (110, 65, 65)),
            'scale_m_per_unit': 1.0, 'contact_height_m': 5.0,
            'notes': '150 x 110 m provisional ship; four 10 m pads; clamps slide inward only after contact.'}
