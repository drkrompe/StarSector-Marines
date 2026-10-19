"""Central command spine: a low armoured housing on the dorsal centreline.

The Cerberus sprite carries a long cream capsule between the forward and aft
turret pairs (x about -5..33, |y| <= 8), its fore half banded dark. Here it
is one segmented, chamfered housing standing a few units proud of the decks
it bridges: a banded forward trunk over the forebody, and the waist housing
whose forward end carries the command deck, a low armoured superstructure
with recessed viewport slits under a visor brow, looking forward over the
trunk. Raised access trunks, roof hatches and a small sensor fit stand along
it. The waist service trunks run exposed along the housing flanks. The aft
centre deck beyond the housing belongs to sw_aft_deck. Nothing exceeds z ~21 except
the short sensor mast; everything stays inboard of the four barbettes.
"""
import math
import sw_kit
from sw_hull import WAIST_DECK


def plan(x0, x1, hw, cf, ca=.6):
    """Eight-sided deck plan: chamfered fore corners cf, aft corners ca. CCW from above."""
    return [(x0+ca,-hw),(x1-cf,-hw),(x1,-hw+cf),(x1,hw-cf),
            (x1-cf,hw),(x0+ca,hw),(x0,hw-ca),(x0,-hw+ca)]


def capsule(x0, x1, hw, fore, aft):
    """Twelve-sided plan whose ends are two flat facets each: a faceted
    stand-in for the sprite's rounded capsule ends. fore/aft are end depths."""
    end = lambda d: [(d,1),(d*.38,.7),(0,.3)]
    port = [(x1-d,hw*k) for d,k in end(fore)]
    aft_ = [(x0+d,hw*k) for d,k in end(aft)]
    return ([(x,-y) for x,y in reversed(aft_)] + [(x,-y) for x,y in port] +
            list(reversed(port)) + aft_)


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


def ground(kit, x0, x1, hw, sink=1.0):
    """Lowest and highest hull Z under a footprint: the skirt reaches below the
    lowest deck it spans, so a housing bridging a deck step never floats."""
    zs=[]
    for i in range(7):
        for j in range(5):
            hit=kit.surface(x0+(x1-x0)*i/6, -hw+2*hw*j/4)
            if hit:
                zs.append(hit[0].z)
    return (min(zs)-sink, max(zs)) if zs else (WAIST_DECK-sink, WAIST_DECK)


def section(kit, name, x0, x1, base, prof, fore, aft, mat, bevel=.2):
    """Capsule segment from a section profile [(z, half-width, end inset)].
    Only a capsule end (fore/aft depth > 0) rakes back with height; a joint
    between segments stays a vertical face across the dark band."""
    rings=[(base,capsule(x0,x1,prof[0][1],fore or .3,aft or .3))]
    for z,hw,e in prof:
        a,f=(e if aft else 0),(e if fore else 0)
        rings.append((z,capsule(x0+a,x1-f,hw,max(fore-f,.4) if fore else .3,max(aft-a,.4) if aft else .3)))
    return loft(kit,name,rings,mat,bevel)


def extrude(kit, name, x0, x1, sec, mat, bevel=.05):
    """Prism along X from a (y, z) section, counter-clockwise seen from the bow."""
    n=len(sec)
    verts=[(x0,y,z) for y,z in sec]+[(x1,y,z) for y,z in sec]
    faces=[tuple(reversed(range(n))),tuple(range(n,2*n))]
    faces+=[(i,(i+1)%n,n+(i+1)%n,n+i) for i in range(n)]
    return kit.mesh(name,verts,faces,mat,bevel=bevel)


def cupola(kit, name, x, y, r, rise, lid=True, floor=None):
    """Raised access trunk: octagonal coaming seated on whatever deck is below,
    a chamfered collar and a hinged hatch lid with a dark seal ring."""
    zs=[h[0].z for h in (kit.surface(x+r*math.cos(a*math.pi/4),y+r*math.sin(a*math.pi/4)) for a in range(8)) if h]
    lo,hi=(min(zs),max(zs)) if zs else (WAIST_DECK,WAIST_DECK)
    if floor is not None:
        lo,hi=floor-.4,floor
    top=hi+rise
    kit.cylinder(name+' coaming',(x,y,(lo-.6+top)/2),r,top-lo+.6,kit.cream,'Z',8,.12)
    kit.cylinder(name+' collar',(x,y,top+.15),r*.86,.3,kit.steel,'Z',8,.06)
    if lid:
        kit.cylinder(name+' seal ring',(x,y,top+.33),r*.7,.08,kit.dark,'Z',16,0)
        kit.cylinder(name+' hatch lid',(x,y,top+.45),r*.62,.25,kit.steel,'Z',16,.06)
        kit.box(name+' hinge',(x-r*.62,y,top+.45),(.35,r*.7,.3),kit.dark,.05)
    return top


def build(kit):
    red=sw_kit.material('Port running light',(.6,.05,.03),emission=(1,.08,.04))
    green=sw_kit.material('Starboard running light',(.05,.5,.15),emission=(.1,1,.3))
    amber=sw_kit.material('Mast beacon',(.8,.5,.1),emission=(1,.6,.15))
    white=sw_kit.material('Masthead light',(.9,.9,.85),emission=(1,1,.9))
    # Real glass, lightly smoked: the bridge room is cut in behind it and C2 looks out through it.
    glass=sw_kit.glass('Bridge smoked viewport glazing',(.74,.8,.8))   # per face: a slab tints twice

    # --- Spine housing. A dark core runs the whole length; cream armour
    # segments sit over it with gaps, so the dark bands of the sprite are
    # real recesses between plates. Waist housing aft, banded trunk fore.
    X0,X1=-5.5,31.8
    base,_=ground(kit,X0,X1,7.5)
    section(kit,'Spine dark core',X0+.4,X1-.4,base,[(15.1,6.5,0),(16.2,5.4,.9),(16.7,3.9,1.6)],2.6,2.2,kit.dark,.1)
    waist=[(15.6,8.0,0),(16.9,6.9,1.0),(17.5,5.4,1.8)]
    trunk=[(15.5,6.9,0),(16.7,5.9,.9),(17.2,4.4,1.6)]
    segs=[('Spine aft cap',-5.5,-.9,waist,0,2.8),('Spine waist housing',-.3,7.4,waist,0,0),
          ('Command deck housing',8.0,18.2,waist,1.6,0),
          ('Spine trunk segment',19.1,21.7,trunk,0,0),('Spine trunk segment',22.5,25.1,trunk,0,0),
          ('Spine trunk segment',25.9,28.3,trunk,0,0),('Spine trunk nose',29.1,31.8,trunk,2.6,0)]
    # The fore trunk is gunmetal like the sprite's dark banded fore half;
    # the waist housing and the command deck are the cream capsule.
    for name,x0,x1,prof,fore,aft in segs:
        section(kit,name,x0,x1,base,prof,fore,aft,kit.steel if 'trunk' in name else kit.cream)
    # Oxide shoulder strakes on the waist housing chamfer, the sprite's
    # orange flank stripes: plates laid on the facet, proud of it.
    (ya,za),(yb,zb)=(waist[0][1],waist[0][0]),(waist[1][1],waist[1][0])
    on=lambda t,o: (ya+(yb-ya)*t+o*.76, za+(zb-za)*t+o*.65)
    strake=[on(.18,-.05),on(.18,.22),on(.82,.22),on(.82,-.05)]
    for x0,x1 in ((-.1,7.2),(8.2,17.4)):
        for s in (1,-1):
            sec=[(y*s,z) for y,z in strake]
            if s<0:
                sec.reverse()
            extrude(kit,'Spine shoulder strake',x0,x1,sec,kit.orange)
    # Roof of the waist housing: a central walkway between two louvred
    # ventilation slots, the ladder-like dark rails of the sprite.
    for s in (1,-1):
        kit.box('Spine roof vent slot',(2.6,s*3.9,17.45),(9.4,1.0,.4),kit.dark,.05)
        for k in range(9):
            kit.box('Roof vent louvre',(-1.4+k,s*3.9,17.55),(.25,.9,.2),kit.steel,.03)
    kit.box('Spine roof walkway',(2.6,0,17.6),(9.6,4.4,.25),kit.cream,.08)
    # Forward trunk: a low raised spine over the forebody, its plates carrying
    # bolted access covers; the nose holds a forward sensor blister.
    for x in (20.4,23.8,27.1):
        kit.box('Trunk access cover',(x,0,17.3),(1.9,5.4,.3),kit.cream,.1)
        kit.box('Trunk cover hasp',(x-.9,0,17.45),(.3,1.4,.15),kit.dark,.03)
    kit.box('Trunk nose sensor housing',(29.6,0,17.4),(2.2,2.6,.6),kit.steel,.15)
    kit.box('Trunk nose sensor window',(30.75,0,17.4),(.1,1.8,.35),glass,0)

    # --- Command deck: a low armoured superstructure on the forward end of
    # the waist housing, looking forward over the trunk and the forward
    # turrets. Raked lower bulkhead, recessed slit glazing behind mullions,
    # an overhanging visor brow and a chamfered roof; top at z ~21.
    lo,hi,gt,top=17.3,18.5,19.55,20.65
    loft(kit,'Command deck lower bulkhead',[(lo,plan(8.6,17.6,5.0,1.9,1.0)),(hi,plan(8.6,18.3,5.2,2.1,1.0))],kit.cream,.2)
    loft(kit,'Viewport sill ledge',[(hi-.15,plan(8.5,18.5,5.3,2.2,1.0)),(hi+.1,plan(8.5,18.45,5.25,2.2,1.0))],kit.steel,.06)
    glass_plan=plan(9.0,17.9,4.8,2.0,.8)
    loft(kit,'Command deck viewport slits',[(hi,glass_plan),(gt,plan(9.0,18.15,4.8,2.05,.8))],glass)
    loft(kit,'Command deck visor brow',[(gt-.1,plan(8.5,18.85,5.35,2.4,1.0)),(gt+.55,plan(8.5,18.55,5.35,2.35,1.0))],kit.orange,.15)
    loft(kit,'Command deck armoured roof',[(gt+.5,plan(8.6,18.45,5.2,2.25,1.0)),(top,plan(9.4,17.0,4.3,1.8,.8))],kit.cream,.2)
    # Heavy mullions split the forward facets into slits; the side and aft
    # and aft ports are narrower slits between posts.
    for edge,count in ((1,2),(2,3),(3,2)):
        for k in range(count+1):
            if edge!=2 and k==0:
                continue
            x,y,ang,_=along(glass_plan,edge,k/count,.3)
            kit.box('Viewport armoured mullion',(x+.1,y,(hi+gt)/2),(.6,.55,gt-hi+.15),kit.steel,.08).rotation_euler[2]=ang
    for edge in (0,4):
        for t in (.3,.75):
            x,y,ang,length=along(glass_plan,edge,t,.3)
            kit.box('Side viewport mullion',(x,y,(hi+gt)/2),(.5,.5,gt-hi+.15),kit.steel,.06).rotation_euler[2]=ang
    kit.box('Aft slit shutter',(8.75,0,(hi+gt)/2),(.35,5.6,.35),kit.steel,.05)
    for s,light in ((1,red),(-1,green)):
        kit.box('Command deck running light',(15.6,s*5.4,gt+.25),(.6,.3,.3),light,.06)
    # Roof sensor fit: a short pylon with scanner bar aft, a low radome and a
    # comms dish forward of it, and whips. The mast stays a few units tall.
    z,mx=top,10.8
    kit.box('Mast pedestal',(mx,0,z+.3),(1.8,1.8,.6),kit.steel,.2)
    kit.mesh('Mast pylon',[(mx-.6,-.55,z+.6),(mx+.6,-.55,z+.6),(mx+.6,.55,z+.6),(mx-.6,.55,z+.6),
                            (mx-.25,-.3,z+2.4),(mx+.25,-.3,z+2.4),(mx+.25,.3,z+2.4),(mx-.25,.3,z+2.4)],
             [(3,2,1,0),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)],kit.steel,bevel=.08)
    kit.box('Mast crosstree',(mx,0,z+1.8),(.35,4.4,.3),kit.steel,.05)
    kit.cylinder('Scanner turntable',(mx,0,z+2.5),.4,.25,kit.steel,'Z',16,.04)
    kit.box('Search radar scanner bar',(mx,0,z+2.8),(.55,3.8,.4),kit.dark,.08)
    kit.box('Masthead light',(mx,0,z+3.15),(.3,.3,.3),white,.06)
    for s,light in ((1,red),(-1,green)):
        kit.box('Crosstree signal light',(mx,s*2.2,z+2.05),(.3,.3,.25),light,.04)
        kit.cylinder('Whip antenna',(mx-.9,s*3.0,z+1.2),.06,2.6,kit.steel,'Z',8,0)
    kit.cylinder('Fire-control radome base',(14.6,0,z+.2),1.15,.4,kit.steel,'Z',8,.06)
    kit.mesh('Fire-control radome',[(14.6+1.0*math.cos(a*math.pi/4),1.0*math.sin(a*math.pi/4),z+.4) for a in range(8)]+
             [(14.6+.55*math.cos(a*math.pi/4),.55*math.sin(a*math.pi/4),z+1.2) for a in range(8)],
             [tuple(reversed(range(8))),tuple(range(8,16))]+[(a,(a+1)%8,8+(a+1)%8,8+a) for a in range(8)],kit.cream,bevel=.06)
    kit.cylinder('Roof comms dish pedestal',(12.7,-2.6,z+.25),.3,.5,kit.steel,'Z',12,.04)
    kit.cylinder('Roof comms dish',(12.7,-2.6,z+.7),.85,.2,kit.steel,'Z',24,.06).rotation_euler=(.6,0,-.7)
    kit.cylinder('Roof beacon',(12.7,2.6,z+.2),.25,.4,amber,'Z',12,.04)
    # Aft door from the command deck onto the spine walkway.
    kit.box('Command deck aft door frame',(8.4,0,18.2),(.3,2.2,1.9),kit.steel,.1)
    kit.box('Command deck aft door',(8.3,0,18.15),(.25,1.6,1.6),kit.dark,.08)

    # --- Access points along the spine: roof hatches on the waist housing,
    # raised access trunks at the sprite's four dark rings (two either side
    # of the forward trunk, two flanking the housing's aft cap), and a
    # cupola on the aft cap.
    for x in (-.6+1.9,5.9):
        kit.cylinder('Spine roof hatch',(x,0,17.75),.95,.2,kit.steel,'Z',16,.05)
        kit.cylinder('Spine roof hatch seal',(x,0,17.68),1.15,.08,kit.dark,'Z',16,0)
    cupola(kit,'Aft cap access cupola',-3.2,0,1.3,.9,floor=waist[-1][0])
    for s in (1,-1):
        cupola(kit,'Forward access trunk',20.6,s*12.8,1.5,.7)
        cupola(kit,'Aft access trunk',-3.8,s*12.6,1.7,.8)
    # One low round access hatch over the engine room, where the sprite shows
    # a ring behind the spine; sw_aft_deck recesses its deck plate around it.
    cupola(kit,'Engine room access hatch',-9.6,0,2.3,.35)

    # --- Waist service trunks: still exposed, now run along the housing
    # flanks in the recessed waist, clamped to the spine wall by hoops and
    # entering the forebody and engine-room bulkheads through dark collars.
    for s in (1,-1):
        for y in (9.3,10.9):
            kit.cylinder('Exposed waist service trunk',(8.2,s*y,WAIST_DECK+1.0),.7,17.8,kit.steel,'X',16)
            for x in (-.4,15.7):
                kit.cylinder('Trunk bulkhead collar',(x,s*y,WAIST_DECK+1.0),.95,.6,kit.dark,'X',16,.06)
        for x in (2.5,8.0,13.5):
            kit.box('Trunk clamp hoop',(x,s*10.0,WAIST_DECK+1.3),(.9,3.0,1.0),kit.cream,.25)
            kit.box('Hoop wall bracket',(x,s*8.4,WAIST_DECK+2.2),(.9,.8,2.0),kit.steel,.1)
