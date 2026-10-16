"""Aft command bridge on the engine-room plateau, and the fore pressure casing.

A squat armoured tug/freighter bridge stands aft on the dorsal centreline
(x -25..-4), looking forward along the spine over the waist machinery: a
chamfered machinery casing grounded on the hull, a wheelhouse with a raked,
wraparound viewport band set back behind mullions under a heavy visor brow,
short bridge wings, an aft sensor mast, and risers tying it into the waist
service trunks. Wings stay inside |y| < 8.5, well inboard of turrets 1 and 2
(-11,+/-20). The forebody keeps only its plain pressure casing.
"""
import math
import sw_kit
from sw_hull import WAIST_DECK


def plan(x0, x1, hw, cf, ca=.6):
    """Eight-sided deck plan: chamfered fore corners cf, aft corners ca. CCW from above."""
    return [(x0+ca,-hw),(x1-cf,-hw),(x1,-hw+cf),(x1,hw-cf),
            (x1-cf,hw),(x0+ca,hw),(x0,hw-ca),(x0,-hw+ca)]


def loft(kit, name, rings, mat, bevel=0, sink=.9):
    """Solid stacked from plan rings (z, polygon); z=None grounds each vertex on the hull."""
    verts=[]
    for z,poly in rings:
        for x,y in poly:
            if z is None:
                hit=kit.surface(x,y)
                verts.append((x,y,(hit[0].z if hit else 12)-sink))
            else:
                verts.append((x,y,z))
    n=len(rings[0][1])
    faces=[tuple(reversed(range(n)))]
    for r in range(len(rings)-1):
        faces += [(r*n+i,r*n+(i+1)%n,(r+1)*n+(i+1)%n,(r+1)*n+i) for i in range(n)]
    faces.append(tuple(range((len(rings)-1)*n,len(rings)*n)))
    return kit.mesh(name,verts,faces,mat,bevel=bevel)


def along(poly, i, t, out=0):
    """Point a fraction t along plan edge i, pushed `out` along its outward normal, and the edge angle."""
    (ax,ay),(bx,by)=poly[i],poly[(i+1)%len(poly)]
    dx,dy=bx-ax,by-ay
    length=math.hypot(dx,dy)
    nx,ny=dy/length,-dx/length
    return ax+dx*t+nx*out, ay+dy*t+ny*out, math.atan2(dy,dx), length


def build(kit):
    red=sw_kit.material('Port running light',(.6,.05,.03),emission=(1,.08,.04))
    green=sw_kit.material('Starboard running light',(.05,.5,.15),emission=(.1,1,.3))
    amber=sw_kit.material('Mast beacon',(.8,.5,.1),emission=(1,.6,.15))
    white=sw_kit.material('Masthead light',(.9,.9,.85),emission=(1,1,.9))
    # Darker smoked glazing than kit.glass, which reads pale at ship scale.
    glass=sw_kit.material('Bridge smoked viewport glazing',(.02,.03,.04),.3,.1,emission=(.012,.03,.04))

    # Fore pressure casing: the plain forebody blockout, no bridge features.
    x,length,z=34,18,15
    kit.box('Fore pressure casing',(x,0,z),(length,11,4),kit.cream,1.5)
    for px in (x-5,x,x+5):
        kit.box('Casing access cover',(px,0,z+2.1),(4.1,8,.5),kit.cream,.25)

    # Aft machinery casing: the bridge plinth, chamfered, with a forward glacis
    # falling toward the waist and a short stern face over the hull notch.
    casing=[(None,plan(-24.6,-3.6,5.6,2.4,1.4)),(15.2,plan(-24.4,-4.9,5.6,2.4,1.4)),
            (16.6,plan(-24.0,-7.4,5.1,2.0,1.2)),(17.2,plan(-23.6,-8.6,4.6,1.7,1.0))]
    loft(kit,'Aft machinery casing',casing,kit.cream,.3)
    loft(kit,'Casing deck-edge strake',[(16.2,plan(-24.6,-6.3,5.5,2.3,1.5)),(16.9,plan(-24.4,-6.9,5.35,2.2,1.4))],kit.orange,.12)
    # Engine-room ventilation louvres on the stern face of the casing.
    for y in (-2.6,2.6):
        kit.box('Casing stern vent',(-24.5,y,14.6),(.6,3.2,2.2),kit.dark,.15)
        for dz in (-.6,0,.6):
            kit.box('Stern vent louvre',(-24.85,y,14.6+dz),(.35,3.0,.22),kit.steel,.04).rotation_euler[1]=-.4
    for y in (-3.6,3.6):
        kit.box('Casing deck access cover',(-21.8,y,17.3),(2,1.6,.3),kit.cream,.1)

    # Spine tie: the waist service trunks elbow up a sloped steel saddle at
    # their aft ends and plunge into the casing's glacis.
    loft(kit,'Bridge spine tie saddle',[(None,plan(-6.5,1.2,4.6,.4,1.2)),(13.4,plan(-6.5,.4,4.3,.4,1)),
         (14.8,plan(-6.5,-3.2,3.9,.4,.6))],kit.steel,.25)
    rise=math.atan2(2.6,6)
    for y in (-3,0,3):
        kit.cylinder('Trunk riser into casing',(-2.3,y,14.3),.8,6.6,kit.steel,'X',16,.1).rotation_euler[1]=math.pi/2+rise
        kit.cylinder('Riser entry collar',(-5.0,y,15.5),1.05,.6,kit.dark,'X',16,.08).rotation_euler[1]=math.pi/2+rise
    for y in (-4.5,4.5):
        loft(kit,'Spine tie buttress rib',[(None,plan(-6.5,.2,.45,.1,.1)),(16.2,plan(-6.5,-5.4,.4,.1,.1))],kit.cream,.1)

    # Wheelhouse: raked lower bulkhead, recessed glazing band, overhanging
    # visor brow, then a chamfered armoured roof.
    loft(kit,'Bridge lower bulkhead',[(16.9,plan(-21.4,-9.8,4.7,1.9)),(19.0,plan(-21.4,-8.3,5.0,2.1))],kit.cream,.2)
    loft(kit,'Viewport sill ledge',[(18.85,plan(-21.5,-8.0,5.1,2.2)),(19.1,plan(-21.5,-8.05,5.05,2.2))],kit.steel,.06)
    glass_plan=plan(-21.0,-8.6,4.6,2.05)
    loft(kit,'Bridge viewport band',[(19.0,glass_plan),(21.1,plan(-21.0,-8.0,4.6,2.1))],glass)
    loft(kit,'Bridge visor brow',[(21.0,plan(-21.5,-6.7,5.15,2.45)),(21.95,plan(-21.5,-7.1,5.15,2.35))],kit.orange,.15)
    loft(kit,'Bridge armoured roof',[(21.9,plan(-21.4,-7.4,5.0,2.25)),(22.8,plan(-20.5,-9.2,4.2,1.8))],kit.cream,.2)

    # Heavy armoured mullions split the forward facets into slits; the side
    # ports are half-closed behind shutter louvres.
    for edge,count in ((1,2),(2,3),(3,2)):
        for k in range(count+1):
            if edge!=2 and k==0:
                continue
            x,y,ang,_=along(glass_plan,edge,k/count,.3)
            kit.box('Viewport armoured mullion',(x+.15,y,20.05),(.65,.6,2.2),kit.steel,.08).rotation_euler[2]=ang
    for edge in (0,4):
        x,y,ang,length=along(glass_plan,edge,.4,.3)
        for z in (19.45,20.05,20.65):
            kit.box('Side shutter louvre',(x,y,z),(length*.62,.35,.3),kit.steel,.05).rotation_euler=(.4 if edge==4 else -.4,0,ang)

    # Bridge wings: one armoured lookout deck through the wheelhouse, far
    # inboard of the turret 1/2 barbettes.
    loft(kit,'Bridge wing deck',[(18.8,plan(-18.4,-14.1,8.0,.6,.4)),(19.4,plan(-18.4,-13.6,8.0,.8,.4))],kit.cream,.15)
    loft(kit,'Bridge wing visor',[(21.0,plan(-18.4,-13.1,8.1,1.0,.4)),(21.85,plan(-18.4,-13.4,8.1,.9,.4))],kit.orange,.12)
    for side,light in ((1,red),(-1,green)):
        kit.box('Wing lookout glazing',(-16.2,side*7.8,20.2),(3.6,.3,1.6),glass,0)
        kit.box('Wing forward glazing',(-14.05,side*6.6,20.2),(.3,2.6,1.6),glass,0)
        kit.box('Wing corner post',(-14.0,side*7.85,20.2),(.6,.6,1.7),kit.steel,.08)
        kit.box('Wing aft bulkhead',(-18.1,side*6.5,20.2),(.6,3.0,1.7),kit.cream,.08)
        # Triangular gusset under the wing, from the casing shoulder to the wing tip.
        gv=[(-18.0,side*4.6,16.9),(-14.6,side*4.6,16.9),(-14.6,side*7.7,18.85),(-18.0,side*7.7,18.85),
            (-18.0,side*4.6,18.85),(-14.6,side*4.6,18.85)]
        gf=[(0,1,2,3),(3,2,5,4),(0,4,5,1),(0,3,4),(1,5,2)]
        kit.mesh('Wing support gusset',gv,gf if side>0 else [f[::-1] for f in gf],kit.steel)
        kit.box('Wing running light',(-13.7,side*7.9,22.05),(.6,.5,.35),light,.08)

    # Stern-facing hatch with its frame, reached across the casing top.
    kit.box('Bridge aft hatch frame',(-21.55,0,19.3),(.3,2.4,3.0),kit.steel,.15)
    kit.box('Bridge aft hatch',(-21.7,0,19.3),(.25,1.7,2.4),kit.dark,.12)
    kit.box('Hatch landing grating',(-22.7,0,17.35),(1.8,3,.25),kit.steel,.05)

    # Sensor and comms mast: a stout pylon aft on the roof with a crosstree,
    # scanner bar, whip antennas and a beacon. Squat, not a tower.
    z,mx=22.8,-18.2
    kit.box('Mast pedestal',(mx,0,z+.4),(2.6,2.4,1.0),kit.steel,.25)
    kit.mesh('Mast pylon',[(mx-.9,-.75,z+.8),(mx+.9,-.75,z+.8),(mx+.9,.75,z+.8),(mx-.9,.75,z+.8),
                            (mx-.3,-.4,z+4.2),(mx+.3,-.4,z+4.2),(mx+.3,.4,z+4.2),(mx-.3,.4,z+4.2)],
             [(3,2,1,0),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)],kit.steel,bevel=.1)
    kit.box('Mast crosstree',(mx,0,z+3.0),(.5,6.6,.35),kit.steel,.06)
    kit.cylinder('Scanner turntable',(mx,0,z+4.35),.55,.3,kit.steel,'Z',16,.05)
    kit.box('Search radar scanner bar',(mx,0,z+4.7),(.7,5.0,.5),kit.dark,.1)
    kit.box('Masthead light',(mx,0,z+5.2),(.4,.4,.4),white,.08)
    for side,light in ((1,red),(-1,green)):
        kit.box('Crosstree signal light',(mx,side*3.3,z+3.35),(.35,.35,.3),light,.05)
        kit.cylinder('Whip antenna',(mx-1.6,side*3.7,z+2.0),.07,4,kit.steel,'Z',8,0)
    kit.cylinder('Roof comms dish pedestal',(-11.6,-2.5,z+.3),.35,.7,kit.steel,'Z',12,.05)
    dish=kit.cylinder('Roof comms dish',(-11.6,-2.5,z+.9),1.1,.25,kit.steel,'Z',24,.08)
    dish.rotation_euler=(.6,0,-.7)
    kit.cylinder('Roof beacon',(-11.6,2.2,z+.2),.3,.4,amber,'Z',12,.05)
    # The waist service spine: exposed trunks in a recessed bed amidships.
    kit.box('Recessed central service bed',(9,0,WAIST_DECK+.55),(20,10,1.5),kit.dark,.5)
    for y in (-3,0,3):
        kit.cylinder('Exposed longitudinal service trunk',(9,y,WAIST_DECK+1.8),.9,19,kit.steel,'X')
    for x in (2,9,16):
        kit.box('Service trunk support hoop',(x,0,WAIST_DECK+2.4),(1.1,10,1.2),kit.cream,.3)
