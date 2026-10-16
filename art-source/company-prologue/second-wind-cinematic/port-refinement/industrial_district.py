"""Geometry-owned working port. Source-world metres; deliberately no ground overlays.

build_district(scene, world) returns JSON-compatible ground, road and traffic
contracts. The caller owns one continuous ground surface using those footprints.
"""
from pathlib import Path
import math
import json
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
ATLAS = HERE.parent / 'port-approach-realism/assets/industrial-materials-v1.png'
GROUND_Z = .4
SECONDARY_ATLAS = HERE / 'assets/secondary-materials-v1.png'


def build_district(scene, world):
    created = []
    footprints = []
    # The inherited sheds are placeholders, including all of their old fittings.
    hidden = []
    for obj in list(scene.objects):
        if obj.name.startswith(('Port district shed', 'Port district roof', 'PORT DETAIL |',
                                'Port communications', 'Port tower', 'Port district freight road')):
            obj.hide_render = True
            obj.hide_set(True)
            hidden.append(obj.name)

    def material(name, color, cell=None, period=16, metallic=.15, atlas=ATLAS):
        mat = bpy.data.materials.new('DISTRICT | ' + name)
        mat.use_nodes = True
        nodes, links = mat.node_tree.nodes, mat.node_tree.links
        bsdf = nodes.get('Principled BSDF')
        bsdf.inputs['Base Color'].default_value = (*color, 1)
        bsdf.inputs['Roughness'].default_value = .78
        bsdf.inputs['Metallic'].default_value = metallic
        if cell is not None and Path(atlas).exists():
            uv = nodes.new('ShaderNodeTexCoord')
            wrap = nodes.new('ShaderNodeVectorMath'); wrap.operation = 'FRACTION'
            links.new(uv.outputs['UV'], wrap.inputs[0])
            mapping = nodes.new('ShaderNodeVectorMath'); mapping.operation = 'MULTIPLY_ADD'
            mapping.inputs[1].default_value = (.46, .46, 1)
            mapping.inputs[2].default_value = (cell[0]*.5+.02, cell[1]*.5+.02, 0)
            links.new(wrap.outputs[0], mapping.inputs[0])
            tex = nodes.new('ShaderNodeTexImage')
            tex.image = bpy.data.images.load(str(atlas), check_existing=True)
            tex.extension = 'EXTEND'; links.new(mapping.outputs[0], tex.inputs[0])
            links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
        mat['surface_period_metres'] = period
        return mat

    cream = material('weathered warm cream cladding', (.62,.60,.53), (1,1), 22)
    roof = material('standing seam steel roof', (.38,.43,.43), (0,1), 18)
    oxide = material('oxide load bearing steel', (.40,.18,.075), (1,0), 14)
    metal = material('galvanized structural steel', (.46,.50,.50), (0,1), 10, .4)
    dark = material('deep loading apertures', (.045,.065,.07), metallic=.05)
    blue = material('faded blue cargo', (.22,.34,.39))
    ochre = material('faded ochre cargo', (.49,.36,.18))
    green = material('faded green cargo', (.27,.36,.29))
    slate = material('dark slate roof metal',(.20,.25,.27),(0,1),28,atlas=SECONDARY_ATLAS)
    cast = material('warm cast concrete',(.54,.53,.47),(1,1),30,metallic=.0,atlas=SECONDARY_ATLAS)
    teal = material('faded teal tank paint',(.22,.37,.37),(0,0),35,atlas=SECONDARY_ATLAS)
    tan = material('warm tan repair panels',(.52,.43,.29),(1,0),28,atlas=SECONDARY_ATLAS)
    glass = material('control room smoked glass', (.12,.23,.27), metallic=.45)

    def mesh(name, verts, faces, mat):
        data = bpy.data.meshes.new(name)
        data.from_pydata(verts, [], faces); data.update()
        data.materials.append(mat)
        uv = data.uv_layers.new(name='face scale metres')
        period = mat.get('surface_period_metres',16)
        for face in data.polygons:
            axis = max(range(3), key=lambda i: abs(face.normal[i]))
            axes = [i for i in range(3) if i != axis]
            for li in face.loop_indices:
                v = data.vertices[data.loops[li].vertex_index].co
                uv.data[li].uv = (v[axes[0]]/period, v[axes[1]]/period)
        obj = bpy.data.objects.new('DISTRICT | '+name,data)
        scene.collection.objects.link(obj); obj.parent = world; obj.pass_index=12
        created.append(obj)
        return obj

    def box(name, pos, size, mat):
        x,y,z=pos; a,b,c=(v/2 for v in size)
        return mesh(name,[(x-a,y-b,z-c),(x+a,y-b,z-c),(x+a,y+b,z-c),(x-a,y+b,z-c),
                         (x-a,y-b,z+c),(x+a,y-b,z+c),(x+a,y+b,z+c),(x-a,y+b,z+c)],
                    [(0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)],mat)

    def beam(name,a,b,width,mat):
        av,bv=Vector(a),Vector(b);mid=(av+bv)*.5
        # Local mesh around origin keeps proper axial rotation.
        obj=box(name,(0,0,0),(width,width,(bv-av).length),mat)
        obj.location=mid;obj.rotation_euler=(bv-av).to_track_quat('Z','Y').to_euler()
        return obj

    def cylinder(name,x,y,bottom,radius,height,mat,vertices=24):
        verts=[]
        for z in (bottom,bottom+height):
            verts += [(x+radius*math.cos(i*math.tau/vertices),y+radius*math.sin(i*math.tau/vertices),z) for i in range(vertices)]
        faces=[tuple(reversed(range(vertices))),tuple(range(vertices,vertices*2))]
        faces += [(i,(i+1)%vertices,(i+1)%vertices+vertices,i+vertices) for i in range(vertices)]
        obj=mesh(name,verts,faces,mat)
        # Cylindrical unwrap keeps panel width/height consistent around curved walls.
        uv=obj.data.uv_layers.active;period=mat.get('surface_period_metres',16)
        for poly in obj.data.polygons:
            for corner,li in enumerate(poly.loop_indices):
                vi=obj.data.loops[li].vertex_index
                if poly.index<2:
                    v=obj.data.vertices[vi].co
                    uv.data[li].uv=((v.x-x)/period,(v.y-y)/period)
                else:
                    segment=poly.index-2
                    u=(segment+(1 if corner in (1,2) else 0))*math.tau*radius/vertices/period
                    uv.data[li].uv=(u,(height if corner in (2,3) else 0)/period)
            if poly.index>=2:poly.use_smooth=True
        return obj

    def dome(name,x,y,z,r,mat):
        count=32;verts=[]
        for ratio,dz in ((1,0),(.86,2.1),(.49,3.8),(0.04,4.5)):
            verts.extend((x+r*ratio*math.cos(i*math.tau/count),y+r*ratio*math.sin(i*math.tau/count),z+dz) for i in range(count))
        faces=[]
        for ring in range(3):
            for i in range(count):faces.append((ring*count+i,ring*count+(i+1)%count,(ring+1)*count+(i+1)%count,(ring+1)*count+i))
        faces.append(tuple(range(3*count,4*count)))
        obj=mesh(name,verts,faces,mat)
        for face in obj.data.polygons:face.use_smooth=True
        return obj

    def footprint(name, bounds, use):
        footprints.append(dict(name=name,bounds=list(bounds),use=use))

    def portal(name,x,y,width,height):
        box(name+' recess',(x,y,GROUND_Z+height/2),(width,.8,height),dark)
        for side in (-1,1):
            box(name+' jamb',(x+side*(width/2+1),y-1,GROUND_Z+height/2),(1.5,2,height+1),oxide)
        box(name+' canopy',(x,y-5,GROUND_Z+height+1),(width+4,12,1.3),roof)

    # Angular high-span repair hangar: supported chamfered roof, deep bays,
    # buttresses, clerestory and offset machine annex instead of a repeated cube.
    x,y=-1320,231
    section=[(-120,0),(-120,34),(-103,48),(-78,61),(78,61),(103,48),(120,34),(120,0)]
    verts=[(x+dx,y+dy,GROUND_Z+z) for dy in (-78,78) for dx,z in section]
    faces=[tuple(reversed(range(8))),tuple(range(8,16))]+[(i,(i+1)%8,(i+1)%8+8,i+8) for i in range(8)]
    hangar=mesh('repair hangar chamfered envelope',verts,faces,tan)
    hangar.data.materials.append(slate)
    for poly in hangar.data.polygons:
        if poly.normal.z>.25:poly.material_index=1
    for dx in (-68,0,68):portal('repair bay',x+dx,y-78-1,52,31)
    for dx in (-116,-78,78,116):
        for dy in (-78,78):beam('hangar external load buttress',(x+dx,y+dy*1.10,GROUND_Z),(x+dx*.94,y+dy,46),3,oxide)
    box('hangar clerestory',(x,y,63),(130,30,5),roof)
    for dx in range(-54,55,18):box('clerestory glass',(x+dx,y-15.2,63),(12,.5,2.6),glass)
    box('repair machine annex',(-1514,247,15.4),(104,111,30),cream)
    box('annex offset roof',(-1514,247,31),(112,117,2),roof)
    footprint('repair hangar and machine annex',(-1580,-1190,135,322),'repair')

    # Long transfer hall with distinct repeated sawtooth roof cross-section.
    x,y=-675,254;length=340;depth=108
    box('freight transfer hall',(x,y,14.4),(length,depth,28),tan)
    for k in range(5):
        xx=x-length/2+k*68
        verts=[(xx,y-depth/2,28.4),(xx+68,y-depth/2,28.4),(xx+53,y-depth/2,42.4),
               (xx,y+depth/2,28.4),(xx+68,y+depth/2,28.4),(xx+53,y+depth/2,42.4)]
        mesh('freight sawtooth roof %02d'%k,verts,[(0,2,1),(3,4,5),(0,1,4,3),(1,2,5,4),(2,0,3,5)],slate)
        box('freight roof light %02d'%k,(xx+55,y,36),(1.1,88,9),glass)
    for dx in range(-140,141,40):portal('freight dock',x+dx,y-depth/2-.5,25,16)
    for dx in (-110,-30,50,130):box('freight roof extractor',(x+dx,y+20,45),(12,12,5),metal)
    footprint('freight transfer hall',(-861,-489,178,321),'freight')

    # Low-front service lots bring working industry into former empty turf.
    for name,x,y,w,d,h in [('electrical workshop',-1450,-18,164,78,20),('repair stores',-1195,-18,150,68,15),('dispatch garage',-730,-15,210,72,18)]:
        box(name,(x,y,GROUND_Z+h/2),(w,d,h),teal if name=='electrical workshop' else tan)
        box(name+' projecting roof',(x,y,GROUND_Z+h+1),(w+8,d+8,2),roof)
        for dx in (-w*.28,w*.28):portal(name+' door',x+dx,y-d/2-.5,23,12)
        footprint(name,(x-w/2-10,x+w/2+10,y-d/2-15,y+d/2+10),'service')

    # Cargo yard: irregular bays and differing stacks, with two open-legged cranes.
    for row,yy in enumerate((420,449,486,531)):
        for col in range(8 if row!=2 else 6):
            xx=-1645+col*43+(15 if row%2 else 0)
            levels=1+(col+row)%3
            for level in range(levels):
                mat=(blue,ochre,green,cream)[(col+row+level)%4]
                box('container r%d c%d level%d'%(row,col,level),(xx,yy,GROUND_Z+3+level*6.2),(33,13,6),mat)
                for dd in (-13,0,13):box('container end stiffener',(xx+dd,yy-6.6,GROUND_Z+3+level*6.2),(.45,.4,5.9),metal)
    for xx,yy in [(-1510,474),(-1245,498)]:
        for side in (-1,1):
            beam('cargo crane portal leg',(xx+side*47,yy-11,.4),(xx+side*42,yy,62),3.4,oxide)
            beam('cargo crane back leg',(xx+side*47,yy+11,.4),(xx+side*42,yy,62),3.4,oxide)
        beam('cargo crane main girder',(xx-67,yy,62),(xx+67,yy,62),5,oxide)
        box('cargo crane machinery',(xx-15,yy,59),(16,12,8),cream)
        for dx in (-5,5):beam('cargo crane cables',(xx+dx,yy,55),(xx+dx,yy,23),.3,dark)
        box('cargo crane spreader',(xx,yy,22),(26,9,2),metal)
    footprint('cargo handling yard',(-1710,-1160,387,570),'cargo yard')

    # Tank farm provides round silhouettes and connecting elevated utility pipes.
    for i,(xx,yy,r,h) in enumerate([(-793,465,28,34),(-718,465,24,43),(-640,505,32,29),(-805,550,20,27)]):
        cylinder('utility tank %d'%i,xx,yy,.4,r,h,teal)
        cylinder('tank rim %d'%i,xx,yy,h-.6,r+1.2,1.5,metal)
        dome('tank shallow domed cap %d'%i,xx,yy,h+.4,r,metal)
        cylinder('tank service valve %d'%i,xx+4,yy,h+6,1.3,3,oxide)
        for zz in (h*.28,h*.71):cylinder('tank circumferential band',xx,yy,zz,r+.22,.65,oxide)
        beam('tank inspection ladder',(xx-r-1,yy,.4),(xx-r-1,yy,h+2),.8,metal)
    for xx in (-875,-775,-675,-575):beam('pipe rack column',(xx,394,.4),(xx,394,15),1.4,metal)
    for dy in (-3,3):beam('tank connecting pipe',(-885,394+dy,16),(-560,394+dy,16),1.5,oxide)
    box('utilities pump house',(-530,475,9.4),(78,68,18),cast)
    footprint('tank and utilities farm',(-910,-475,380,592),'utilities')

    # Offset stacked customs/admin buildings and cantilever control room.
    box('customs administration base',(-343,214,13.4),(154,110,26),cast)
    box('customs second floor',(-318,228,33.4),(103,82,14),cast)
    box('admin roof overhang',(-318,228,41.4),(111,90,2),roof)
    for xx in (-370,-340,-310,-280):box('admin recessed window',(xx,186.7,33),(20,.7,5),glass)
    box('control tower pedestal',(-420,282,31.4),(20,22,62),cream)
    box('control room cantilever',(-416,278,63.4),(52,43,13),glass)
    box('control room heavy roof',(-416,278,71.4),(59,50,3),cream)
    beam('control antenna',(-416,278,73),(-416,278,91),.9,metal)
    footprint('customs administration and tower',(-455,-245,145,315),'administration')

    # Service spine is open underneath. It crosses secondary access with clearance.
    for xx in (-1100,-930,-760,-590,-420):
        beam('service pipe bridge pier',(xx,370,.4),(xx,370,19),1.5,metal)
    for yy in (369,372):beam('service spine pipe',(-1120,yy,20),(-390,yy,20),1.4,oxide)
    for xx,yy in [(-1090,160),(-915,280),(-465,415),(-1730,250)]:
        beam('yard light mast',(xx,yy,.4),(xx,yy,32),.8,metal)
        beam('yard light arm',(xx,yy,32),(xx+8,yy,32),.6,metal)
        box('yard light head',(xx+8,yy,31.5),(5,2,1),cream)

    # Working rear precincts replace the broad turf buffer. Staggered yards,
    # drainage strips and long access spurs maintain a legible large-scale plan.
    for name,x,y,w,d,h in [('heavy overhaul hall',-1710,1000,355,205,53),
                          ('bulk transfer depot',-585,1010,445,165,36),
                          ('port cold stores',-1140,1510,288,175,42),
                          ('rear maintenance shops',-1830,1530,245,125,26)]:
        # A sloped polygonal roof is real silhouette geometry at rear-camera depth.
        box(name+' walls',(x,y,GROUND_Z+h/2),(w,d,h),tan if 'overhaul' in name else cast)
        verts=[(x-w/2,y-d/2,h+.4),(x+w/2,y-d/2,h+.4),(x+w/2,y+d/2,h+.4),(x-w/2,y+d/2,h+.4),
               (x-w/2+22,y-d/2,h+15),(x+w/2-22,y-d/2,h+15),(x+w/2-22,y+d/2,h+15),(x-w/2+22,y+d/2,h+15)]
        mesh(name+' raised roof',verts,[(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7),(4,5,6,7)],slate)
        for dx in (-w*.30,0,w*.30):portal(name+' loading',x+dx,y-d/2-.5,42,26)
        for dx in (-w*.43,w*.43):
            beam(name+' end buttress',(x+dx,y-d/2-8,.4),(x+dx,y-d/2,h),3,oxide)
        footprint(name,(x-w/2-30,x+w/2+30,y-d/2-40,y+d/2+25),'rear repair and transfer')
    # East-side bulk storage has tanks of mixed height rather than shed repetition.
    for i,(xx,yy,r,h) in enumerate([(-130,1410,43,63),(-10,1440,38,48),(105,1395,48,57),
                                   (-150,1580,32,44),(-30,1620,50,38),(130,1580,30,71)]):
        cylinder('rear bulk tank %d'%i,xx,yy,.4,r,h,teal)
        cylinder('rear tank roof rim',xx,yy,h-.2,r+1.5,1.8,oxide)
        dome('rear tank shallow dome',xx,yy,h+.4,r,metal)
        cylinder('rear tank small valve',xx+3,yy,h+6,1.4,3,oxide)
        for zz in (h*.25,h*.72):cylinder('rear tank band',xx,yy,zz,r+.4,.8,metal)
    box('rear utility station',(315,1515,17.4),(150,180,34),cast)
    footprint('rear bulk storage',(-230,425,1310,1740),'bulk liquids and power')
    # A second cargo yard: larger gaps leave maneuvering space around real stacks.
    for row,yy in enumerate((820,875,940,1065,1120)):
        for col in range(5):
            xx=-1320+col*61
            if row==2 and col in (1,2):continue
            for level in range(1+(col+row)%3):
                box('rear freight container',(xx,yy,3.4+level*6.3),(40,16,6),(ochre,blue,green)[(row+col)%3])
    for xx,yy in [(-1220,925),(-1065,1085),(-390,1550)]:
        for dx in (-62,62):
            beam('rear handling gantry leg',(xx+dx,yy,.4),(xx+dx,yy,76),4,oxide)
            beam('rear gantry ground brace',(xx+dx,yy-18,.4),(xx+dx,yy,48),2,metal)
        beam('rear handling gantry girder',(xx-78,yy,76),(xx+78,yy,76),6,oxide)
        box('rear gantry machinery',(xx-20,yy,72),(22,17,8),cream)
        beam('rear crane cable',(xx-20,yy,68),(xx-20,yy,29),.5,dark)
    footprint('rear container interchange',(-1400,-1015,790,1160),'container handling')
    # Smaller dispatch structures and long horizontal pipe racks break the gaps.
    for xx,yy in [(-2060,945),(-760,1580)]:
        box('rear dispatch shelter',(xx,yy,9.4),(90,52,18),cream)
        box('rear shelter roof',(xx,yy,19.4),(98,60,2),roof)
    for xx in range(-790,211,200):
        beam('rear utility spine pier',(xx,1290,.4),(xx,1290,24),2,metal)
    for yy in (1287,1293):beam('rear utility spine pipe',(-810,yy,25),(300,yy,25),2,oxide)

    # Visible communications/control station at the east service edge.
    box('east communications equipment base',(100,595,10.4),(68,49,20),cast)
    box('east communications second step',(112,598,27.4),(39,32,14),tan)
    box('east control glass room',(111,595,40.4),(53,39,12),glass)
    box('east control heavy canopy',(111,595,47.4),(63,49,2),slate)
    for xx in (86,136):
        beam('east control angled support',(xx,574,.4),(111+(xx-111)*.64,587,35),2.8,oxide)
    beam('east communications antenna',(111,595,49),(111,595,94),1.4,metal)
    for zz,reach in ((65,19),(79,12),(89,7)):
        beam('east communications antenna crossarm',(111-reach,595,zz),(111+reach,595,zz),.8,metal)
        for side in (-1,1):cylinder('east communications aerial terminal',111+side*reach,595,zz+2,1.5,4,cream)
    footprint('east communications station',(55,158,560,634),'control and communications')

    roads=[dict(name='main freight artery',bounds=[-1780,-160,78,102],axis='X',z=GROUND_Z),
           dict(name='rear cargo artery',bounds=[-1740,-210,345,365],axis='X',z=GROUND_Z),
           dict(name='cross district service street',bounds=[-991,-969,-80,1800],axis='Y',z=GROUND_Z),
           dict(name='rear interchange road',bounds=[-2200,500,745,775],axis='X',z=GROUND_Z),
           dict(name='rear industrial road',bounds=[-2200,500,1210,1240],axis='X',z=GROUND_Z),
           dict(name='east tank access',bounds=[-275,-245,760,1800],axis='Y',z=GROUND_Z),
           dict(name='west repair access',bounds=[-2160,-2130,760,1800],axis='Y',z=GROUND_Z)]
    lanes=[dict(name='main east',start=[-1760,84,GROUND_Z],end=[-180,84,GROUND_Z],width=8),
           dict(name='main west',start=[-180,96,GROUND_Z],end=[-1760,96,GROUND_Z],width=8),
           dict(name='cargo east',start=[-1720,350,GROUND_Z],end=[-230,350,GROUND_Z],width=7)]
    result=dict(objects=[o.name for o in created],hidden_placeholders=hidden,footprints=footprints,
                roads=roads,lanes=lanes,developed_bounds=[-2200,500,-80,1800],ground_z=GROUND_Z,
                reserved_ship_aprons=[dict(name='Venture',bounds=[-200,100,830,1120],z=GROUND_Z),dict(name='Hound',bounds=[140,280,840,1020],z=GROUND_Z)],
                ground_regions=[[-1780,-160,-80,650],[-2200,500,745,1180],[-2200,500,1240,1800],[-1030,-930,620,1800]],
                drainage_strips=[[-2200,500,655,735],[-2200,500,1180,1205]],
                atlas=str(ATLAS),surface_contract='Caller creates one opaque ground owner; no district road or pad overlays.',
                silhouette_families=['chamfered repair hangar','sawtooth transfer hall','cylindrical utility farm','cantilever control tower','open cargo gantries'])
    out=HERE/'build/district';out.mkdir(parents=True,exist_ok=True)
    (out/'layout.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    return result
