"""Depth-separated, untextured landscape blocking for approach shots03–06.

Import is inert. build_layers operates only on the loaded derived scene.
Silhouette ribbons have buried skirts and rounded terrain cross-sections;
their extended ends taper under the retained ground, never into visible cards.
"""
import math
import random


def build_layers(scene):
    import bpy
    from mathutils import Vector
    from mathutils.bvhtree import BVHTree

    hidden = []
    for name in ("Jangala | interlocking forest canopy", "Jangala | port approach canopy",
                 "Jangala | distant folded ridgeline", "Jangala | distant storm shelf proxies"):
        obj = bpy.data.objects.get(name)
        if obj:
            obj.hide_render = True
            hidden.append(name)
    land = bpy.data.objects.get("Jangala | continuous landform")
    graph = bpy.context.evaluated_depsgraph_get()
    ground = BVHTree.FromObject(land, graph) if land else None

    def elevation(x, y):
        if ground:
            point, normal, index, distance = ground.ray_cast(Vector((x, y, 5000)), Vector((0, 0, -1)), 10000)
            if point is not None:
                return point.z
        return -50.0

    parent = bpy.data.collections.new("LAYER BLOCK | approach landscape")
    scene.collection.children.link(parent)
    parent["scope"] = "Shots03 managed land,04 port window,05–06 exterior"
    groups = []

    def group(name, role, depth, mask, color):
        collection = bpy.data.collections.new("LAYER BLOCK | " + name)
        parent.children.link(collection)
        collection["future_paint_role"] = role
        collection["nominal_port_depth_m"] = depth
        collection["mask_id"] = mask
        mat = bpy.data.materials.new("LAYER BLOCK | " + name + " color")
        mat.diffuse_color = (*color, 1)
        mat.use_nodes = True
        shader = mat.node_tree.nodes.get("Principled BSDF")
        shader.inputs["Base Color"].default_value = (*color, 1)
        shader.inputs["Roughness"].default_value = .94
        item = {"collection": collection.name, "role": role, "nominal_depth_m": depth,
                "mask_id": mask, "objects": [], "representatives": []}
        groups.append(item)
        return collection, mat, item

    def mesh(name, vertices, faces, collection, mat, info):
        data = bpy.data.meshes.new(name)
        data.from_pydata(vertices, [], faces)
        data.materials.append(mat)
        data.update()
        obj = bpy.data.objects.new(name, data)
        collection.objects.link(obj)
        obj.pass_index = info["mask_id"]
        obj["future_paint_role"] = info["role"]
        obj["nominal_port_depth_m"] = info["nominal_depth_m"]
        info["objects"].append(name)
        return obj

    def ribbon(name, x0, x1, depth, width, height, seed, bundle, river_safe=False, step=130):
        collection, mat, info = bundle
        rng = random.Random(seed)
        # Gaussian hill shoulders with unequal spacing produce broad natural
        # masses; finer irregularities articulate forest without individual trees.
        peaks = [(rng.uniform(x0, x1), rng.uniform(500, 1700), rng.uniform(.3, 1.0))
                 for _ in range(max(5, int((x1 - x0) / 1900)))]
        count = max(8, int((x1 - x0) / step))
        vertices, faces = [], []
        for i in range(count + 1):
            x = x0 + (x1 - x0) * i / count
            y = depth + 90 * math.sin(x / 1200 + seed) + 65 * math.sin(x / 470 + seed * .7)
            if river_safe:
                river = 2850 + 560 * math.sin(x / 3200) + 230 * math.sin(x / 1250)
                y = max(y, river + width * .5 + 500)
            silhouette = .35 + sum(a * math.exp(-((x - cx) / size) ** 2) for cx, size, a in peaks)
            silhouette += .075 * math.sin(x / 113 + seed) + .04 * math.sin(x / 57 + seed * 2)
            endfade = min(1.0, (x - x0) / 1800, (x1 - x) / 1800)
            for side, profile in ((-.5, -.18), (-.3, .46), (0, 1), (.3, .66), (.5, -.18)):
                yy = y + side * width
                zz = elevation(x, yy) - 30 + max(0, profile) * (30 + height * silhouette) * endfade
                vertices.append((x, yy, zz))
            if i:
                a = (i - 1) * 5
                for j in range(4):
                    faces.append((a + j, a + 5 + j, a + 6 + j, a + 1 + j))
        obj = mesh("LAYER BLOCK | " + name, vertices, faces, collection, mat, info)
        centre_index = (count // 2) * 5 + 2
        info["representatives"].append({"object": obj.name, "local_point": list(vertices[centre_index]),
                                         "purpose": "Track stable crest point across source camera frames"})
        return obj

    hills = group("far hills", "far painted hill silhouette / retained shallow relief", 11000, 31, (.28, .36, .35))
    ribbon("far asymmetric hills", -23500, 13000, 11000, 2600, 280, 401, hills, step=220)
    far = group("far forest", "far continuous forest plate / silhouette", 6500, 32, (.19, .29, .235))
    ribbon("far forest belt", -22000, 11000, 6500, 1200, 120, 503, far)
    middle = group("middle woodland", "middle woodland paint relief behind river", 4200, 33, (.16, .265, .18))
    ribbon("middle woodland continuous belt", -19000, 11000, 4250, 650, 70, 607, middle, river_safe=True)
    ribbon("middle woodland outer shoulder", -17000, 12000, 4900, 500, 95, 611, middle, river_safe=True)
    near = group("near hedgerows", "near geometric hedges / later alpha foliage strips", 700, 34, (.17, .26, .145))
    # Field edge bands sit outside managed crop rectangles (y305..1815),
    # retaining their regular clearings and cross-roads in camera03.
    ribbon("managed land outer hedgerow", -7100, -1400, 2090, 180, 24, 701, near, step=90)
    ribbon("managed land near broken verge", -7600, -1800, 390, 95, 15, 709, near, step=85)
    # Low woodland behind the port district preserves the foreground buildings.
    ribbon("port district backdrop trees", -3600, 2400, 760, 250, 32, 719, near, step=85)

    exterior_hills = group("exterior far hills", "exterior south-facing painted hill relief", -5500, 37, (.28, .35, .33))
    ribbon("exterior asymmetric far hills", -17000, 13000, -5500, 1800, 180, 901, exterior_hills, step=210)
    exterior_canopy = group("exterior canopy", "exterior woodland behind working yard", -2500, 38, (.18, .28, .20))
    ribbon("exterior continuous woodland", -12500, 11000, -2500, 1000, 85, 911, exterior_canopy)
    district = group("exterior working district", "real perspective industrial sheds tanks and open crane frames", -460, 39, (.34, .39, .385))

    def district_box(name, centre, dimensions):
        x, y, z = centre
        a, b, c = (v / 2 for v in dimensions)
        verts = [(x-a,y-b,z-c),(x+a,y-b,z-c),(x+a,y+b,z-c),(x-a,y+b,z-c),
                 (x-a,y-b,z+c),(x+a,y-b,z+c),(x+a,y+b,z+c),(x-a,y+b,z+c)]
        faces = [(0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)]
        return mesh("LAYER BLOCK | district " + name, verts, faces, *district)

    # Widely separated, low masses leave the ship and cradle silhouette clear.
    # All new district footprints lie south of y=-300, outside the flight lane.
    for i, (x, y, w, d, h) in enumerate(((-560,-390,110,72,23), (-355,-570,130,90,30),
                                       (-110,-470,95,65,19), (185,-590,125,78,27),
                                       (455,-415,120,80,25), (650,-610,85,65,20))):
        ground_z = elevation(x, y)
        district_box("shed %02d" % i, (x,y,ground_z+h/2), (w,d,h))
        # Shallow pitched roof, retaining a useful silhouette from high camera05.
        verts = [(x-w*.54,y-d*.55,ground_z+h),(x+w*.54,y-d*.55,ground_z+h),
                 (x+w*.54,y,ground_z+h+5),(x-w*.54,y,ground_z+h+5),
                 (x-w*.54,y+d*.55,ground_z+h),(x+w*.54,y+d*.55,ground_z+h)]
        mesh("LAYER BLOCK | district shed roof %02d" % i, verts,
             [(0,1,2,3),(3,2,5,4),(0,3,4),(1,5,2)], *district)
    for i, (x,y,r,h) in enumerate(((-640,-600,24,32),(-580,-610,24,32),(300,-430,29,36))):
        base_z = elevation(x,y)
        verts = [(x+math.cos(k*math.tau/12)*r,y+math.sin(k*math.tau/12)*r,base_z+z)
                 for z in (0,h) for k in range(12)]
        faces = [(k,(k+1)%12,(k+1)%12+12,k+12) for k in range(12)]
        faces += [tuple(reversed(range(12))),tuple(range(12,24))]
        mesh("LAYER BLOCK | district tank %02d" % i, verts, faces, *district)
    for i, (x,y) in enumerate(((-420,-350),(565,-520))):
        base_z = elevation(x,y)
        for dx in (-42,42):
            district_box("gantry %d upright %d" % (i,dx), (x+dx,y,base_z+27), (5,7,54))
        district_box("gantry %d open crossbeam" % i, (x,y,base_z+54), (94,9,6))
        district_box("gantry %d trolley" % i, (x-12,y,base_z+47), (12,12,7))
    district[2]["representatives"].append({"object": "LAYER BLOCK | district shed 02",
                                           "local_point": [-110,-470,elevation(-110,-470)+19],
                                           "purpose": "Exterior working-yard background perspective"})

    clouds = group("independent clouds", "independent translucent painted cloud bank", 13000, 35, (.50, .565, .58))
    vertices, faces = [], []
    rng = random.Random(821)
    for i in range(13):
        cx = -12500 + i * 1200
        cy, cz = 13000 + rng.uniform(-300, 300), 1900 + rng.uniform(-100, 330)
        rx, ry, rz = rng.uniform(900, 1400), 550, rng.uniform(300, 520)
        start = len(vertices)
        for j in range(7):
            phi = math.pi * j / 6
            for k in range(10):
                theta = math.tau * k / 10
                vertices.append((cx + rx * math.sin(phi) * math.cos(theta),
                                 cy + ry * math.sin(phi) * math.sin(theta), cz + rz * math.cos(phi)))
        for j in range(6):
            for k in range(10):
                a, b = start + j * 10 + k, start + j * 10 + (k + 1) % 10
                faces.append((a, b, b + 10, a + 10))
    cloud_obj = mesh("LAYER BLOCK | drifting cloud bank", vertices, faces, *clouds)
    for frame in (217, 624):
        cloud_obj.location.x = (frame - 420) / 12 * 9
        cloud_obj.keyframe_insert(data_path="location", frame=frame)

    traffic = group("distant freight", "independent distant industrial transit geometry", 8500, 36, (.31, .36, .37))
    # A deliberately small distant body, not a canonical hull silhouette.
    vertices = [(-110,-32,-20),(110,-32,-20),(110,32,-20),(-110,32,-20),
                (-85,-28,20),(75,-28,20),(75,28,20),(-85,28,20),
                (140,-16,-8),(140,16,-8)]
    faces = [(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),
             (1,2,9,8),(1,8,5),(5,8,9,6),(2,6,9)]
    freight = mesh("LAYER BLOCK | distant freight transit proxy", vertices, faces, *traffic)
    for frame, x in ((217, -6200), (624, 3300)):
        freight.location = (x, 8500, 800)
        freight.keyframe_insert(data_path="location", frame=frame)
    for obj in (cloud_obj, freight):
        action = obj.animation_data.action
        for layer in action.layers:
            for strip in layer.strips:
                for bag in getattr(strip, "channelbags", ()):
                    for curve in bag.fcurves:
                        for key in curve.keyframe_points:
                            key.interpolation = "LINEAR"
    scene.frame_set(scene.frame_current, subframe=scene.frame_subframe)
    return {"collection": parent.name, "groups": groups, "hidden_old_objects": hidden,
            "coordinate_system": "+X flight,+Y port,+Z up; all dimensions metres",
            "clear_final_corridor": [-700, 150, -80, 80],
            "retained": ["original ground", "river", "plantation clearings/crops/roads", "port structures", "yard"],
            "mask_method": "object pass_index31..39, also stored on grouping collections",
            "render_objects_added": sum(len(g["objects"]) for g in groups),
            "scope": "Untextured geometry and flat-color layer blocking; future painting roles only, no final artwork."}
