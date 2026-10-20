#!/usr/bin/env python3
"""Generate an original chibi red enemy-tank sprite pack for tower defense.

Requires Pillow: python3 -m pip install Pillow
Run: python3 tools/generate_cartoon_tank_sprites.py
Output: assets/enemy_tank_cartoon/ (four transparent horizontal PNG strips).
"""
from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Sequence

from PIL import Image, ImageDraw

FRAME = 64
SCALE = 4
OUT = Path(__file__).resolve().parents[1] / "assets" / "enemy_tank_cartoon"

# Match the red/slate cartoon enemy set while keeping this tank its own design.
INK = (43, 35, 45, 255)
INK_SOFT = (67, 54, 63, 255)
RED = (207, 48, 58, 255)
RED_LIGHT = (244, 83, 72, 255)
RED_HI = (255, 143, 101, 255)
RED_DARK = (135, 35, 49, 255)
RED_DEEP = (92, 33, 43, 255)
CHARCOAL = (51, 56, 65, 255)
SLATE = (88, 97, 109, 255)
SLATE_LIGHT = (153, 167, 177, 255)
METAL = (120, 134, 145, 255)
METAL_HI = (215, 224, 218, 255)
TRACK = (37, 39, 46, 255)
TRACK_HI = (94, 96, 101, 255)
GOLD = (255, 192, 65, 255)
GOLD_HI = (255, 231, 133, 255)
FIRE_RED = (220, 65, 40, 255)
FIRE_ORANGE = (255, 123, 42, 255)
FIRE_YELLOW = (255, 207, 73, 255)
SMOKE_DARK = (43, 41, 48, 246)
SMOKE_MID = (72, 69, 77, 231)


def px(v: float) -> int:
    return int(round(v*SCALE))


class Art:
    """Supersampled vector painter for tidy, hard-edged 64px art."""
    def __init__(self) -> None:
        self.image = Image.new("RGBA", (FRAME*SCALE, FRAME*SCALE), (0,0,0,0))
        self.d = ImageDraw.Draw(self.image)

    @staticmethod
    def p(point):
        return (px(point[0]), px(point[1]))

    @staticmethod
    def b(box):
        return tuple(px(v) for v in box)

    def ellipse(self, box, fill, outline=INK, width=1.6):
        self.d.ellipse(self.b(box), fill=fill, outline=outline,
                       width=max(1,px(width)) if outline is not None else 1)

    def roundrect(self, box, radius, fill, outline=INK, width=1.7):
        self.d.rounded_rectangle(self.b(box), radius=max(1,px(radius)), fill=fill,
                                 outline=outline,
                                 width=max(1,px(width)) if outline is not None else 1)

    def polygon(self, points: Sequence[tuple[float,float]], fill,
                outline=INK, width=1.7):
        coords=[self.p(p) for p in points]
        self.d.polygon(coords,fill=fill)
        if outline is not None and width>0:
            self.d.line(coords+[coords[0]],fill=outline,width=max(1,px(width)),joint="curve")

    def line(self, points: Sequence[tuple[float,float]], fill, width=1.3, round_caps=True):
        coords=[self.p(p) for p in points]
        w=max(1,px(width))
        self.d.line(coords,fill=fill,width=w,joint="curve")
        if round_caps:
            r=max(1,w//2)
            for x,y in (coords[0],coords[-1]):
                self.d.ellipse((x-r,y-r,x+r,y+r),fill=fill)

    def finish(self):
        return self.image.resize((FRAME,FRAME),Image.Resampling.LANCZOS)


def draw_smoke_puff(a: Art,cx,cy,rx,ry,strength=1.0,dark=True):
    color=SMOKE_DARK if dark else (139,145,151,195)
    fill=(color[0],color[1],color[2],int(color[3]*strength))
    outline=INK if strength>0.35 else None
    a.ellipse((cx-rx,cy-ry,cx+rx,cy+ry),fill,outline,0.75 if outline else 0)
    if strength>0.45:
        hi=(120,117,124,155) if dark else (226,225,210,150)
        a.ellipse((cx-rx*0.48,cy-ry*0.72,cx+rx*0.1,cy-ry*0.12),
                  (hi[0],hi[1],hi[2],int(hi[3]*strength)),None,0)


def draw_track_belt(a,x0,phase=0.0,damaged=False,dy=0.0,side=0):
    """One long track belt with twelve moving links and six visible road wheels."""
    outer=TRACK if not damaged else (29,29,35,255)
    channel=(67,71,79,255) if not damaged else (43,42,47,255)
    link=TRACK_HI if not damaged else (67,63,66,255)
    rim=SLATE_LIGHT if not damaged else (68,64,66,255)
    hub=RED_LIGHT if not damaged else (79,46,49,255)
    a.roundrect((x0,8.7+dy,x0+11.0,56.5+dy),4.3,outer,INK,1.9)
    a.roundrect((x0+1.2,10.0+dy,x0+9.8,55.2+dy),3.3,channel,(28,29,35,255),0.9)
    # Links ride around the full belt; one cycle advances them over the full loop.
    shift=(phase/math.tau)*44.0
    for k in range(13):
        y=10.5+((k*3.7+shift)%44.0)+dy
        a.line([(x0+1.8,y),(x0+9.2,y)],link,0.9)
        a.line([(x0+2.0,y+0.7),(x0+9.0,y+0.7)],(35,36,43,255),0.45)
        # Tiny raised cleats on the outer edge make the track read as a chain.
        a.ellipse((x0+0.8,y-0.45,x0+1.7,y+0.55),SLATE if not damaged else (52,50,55,255),None,0)
        a.ellipse((x0+9.3,y-0.45,x0+10.2,y+0.55),SLATE if not damaged else (52,50,55,255),None,0)
    # Six road wheels per side, all with a bright hub and phase-rotating bolts.
    for i,cy0 in enumerate((15.8,23.0,30.2,37.4,44.6,51.8)):
        cy=cy0+dy
        cx=x0+5.5
        a.ellipse((cx-3.8,cy-3.65,cx+3.8,cy+3.65),(42,45,52,255),INK,1.15)
        a.ellipse((cx-2.95,cy-2.9,cx+2.95,cy+2.9),rim,INK,0.8)
        a.ellipse((cx-1.65,cy-1.65,cx+1.65,cy+1.65),hub,INK,0.65)
        for j in range(4):
            ang=phase+i*0.27+side*0.35+j*math.tau/4
            bx=cx+math.cos(ang)*2.05
            by=cy+math.sin(ang)*2.05
            a.ellipse((bx-0.34,by-0.34,bx+0.34,by+0.34),GOLD_HI if not damaged else (105,91,77,255),None,0)
    # Strong side rims separate the moving chain from the red hull.
    a.line([(x0+0.8,13.0+dy),(x0+0.8,52.0+dy)],(126,130,132,255) if not damaged else (55,52,57,255),0.75)


def draw_hull(a,*,phase=0.0,dy=0.0,damaged=False,smoke_frame=None):
    """Shared top-down hull; body stays the same in movement, firing and wrecks."""
    if smoke_frame is not None:
        # Small exhaust/dust puffs trail behind the rear of the moving tank.
        p=math.tau*smoke_frame/8.0
        for x,s in ((13.1,0.0),(50.9,0.8)):
            y=58.2+dy+1.0*math.sin(p+s)
            draw_smoke_puff(a,x,y,2.4,1.65,0.75+0.12*math.sin(p+s),dark=False)
    draw_track_belt(a,7.0,phase,damaged,dy,side=0)
    draw_track_belt(a,46.0,phase+0.4,damaged,dy,side=1)

    chassis=(45,49,57,255) if not damaged else (34,35,41,255)
    outer=(106,34,47,255) if not damaged else (48,42,47,255)
    hull=RED if not damaged else (70,49,53,255)
    highlight=RED_LIGHT if not damaged else (93,60,61,255)
    side_shade=RED_DARK if not damaged else (44,40,46,255)
    a.roundrect((14.2,7.2+dy,49.8,57.0+dy),8.0,chassis,INK,2.2)
    a.roundrect((15.4,8.3+dy,48.6,55.9+dy),7.2,outer,INK,1.8)
    a.roundrect((16.6,9.5+dy,47.4,54.8+dy),6.0,hull,INK,1.65)
    # Broad rounded front glacis plate, lighter red along the top edge.
    a.polygon([(19.0,10.0+dy),(45.0,10.0+dy),(47.0,14.5+dy),
               (44.6,23.0+dy),(19.4,23.0+dy),(17.0,14.5+dy)],
              highlight,INK,1.35)
    if not damaged:
        a.polygon([(20.0,11.0+dy),(31.0,11.0+dy),(29.0,20.4+dy),(19.0,19.5+dy)],RED_HI,None,0)
        a.line([(21.0,11.8+dy),(27.0,12.1+dy)],(255,181,131,255),0.7)
    else:
        a.polygon([(18.0,12.0+dy),(31.0,10.5+dy),(33.0,17.0+dy),
                   (27.0,23.0+dy),(17.8,19.0+dy)],(36,36,42,245),None,0)
        a.line([(31.0,12.0+dy),(28.0,16.4+dy),(33.8,21.4+dy)],INK,0.85)
        a.ellipse((38.0,14.0+dy,43.8,18.8+dy),(231,91,38,165),None,0)
    # Front armor lip, small headlights, and a chunky gunmetal bumper.
    a.roundrect((20.0,8.8+dy,44.0,12.0+dy),1.2,CHARCOAL if not damaged else (38,39,45,255),INK,1.0)
    for x in (21.5,42.5):
        light=GOLD_HI if not damaged else (84,69,56,255)
        a.ellipse((x-1.5,10.0+dy,x+1.5,12.7+dy),light,INK,0.65)
    for x in (26.8,29.5,32.0,34.5,37.2):
        a.line([(x,9.6+dy),(x,11.4+dy)],SLATE_LIGHT if not damaged else (66,62,65,255),0.48)
    a.roundrect((18.7,7.0+dy,45.3,10.1+dy),1.2,
                (52,57,66,255) if not damaged else (34,35,41,255),INK,1.1)
    a.line([(21.0,7.9+dy),(43.0,7.9+dy)],SLATE_LIGHT if not damaged else (58,54,57,255),0.6)
    # Side armor skirts over the inner halves of the track belts.
    for x in (15.4,46.6):
        a.roundrect((x-1.0,17.0+dy,x+1.6,46.0+dy),1.1,
                    highlight if not damaged else side_shade,INK,0.95)
        for y in (21.0,27.0,33.0,39.0,44.0):
            a.ellipse((x-0.05,y-0.45+dy,x+0.75,y+0.35+dy),GOLD if not damaged else (77,66,59,255),None,0)
    # Rear deck/engine grille, visible below the turret.
    a.roundrect((21.0,44.5+dy,43.0,52.7+dy),2.0,
                (59,66,76,255) if not damaged else (39,39,44,255),INK,1.1)
    for i in range(5):
        y=46.0+i*1.15+dy
        a.line([(24.0,y),(40.0,y)],SLATE_LIGHT if not damaged else (67,63,66,255),0.48)
    if not damaged:
        a.polygon([(22.0,45.2+dy),(28.0,45.2+dy),(28.0,51.9+dy),(22.0,51.9+dy)],RED_DARK,None,0)
        a.polygon([(36.0,45.2+dy),(42.0,45.2+dy),(42.0,51.9+dy),(36.0,51.9+dy)],RED_DARK,None,0)
    # Rear lights and tow loops.
    for x in (19.5,44.5):
        a.ellipse((x-1.25,52.0+dy,x+1.25,54.0+dy),RED_LIGHT if not damaged else FIRE_RED,INK,0.55)
        a.roundrect((x-2.2,55.0+dy,x+2.2,57.1+dy),0.75,CHARCOAL if not damaged else (35,35,40,255),INK,0.65)
    # A few hand-painted scuffs/shine marks.
    if not damaged:
        a.line([(17.4,26.0+dy),(18.1,31.0+dy)],RED_HI,0.7)
        a.line([(46.6,26.0+dy),(45.9,31.0+dy)],RED_HI,0.7)
        a.ellipse((18.8,41.2+dy,20.0,42.4+dy),GOLD_HI,None,0)
        a.ellipse((44.0,41.2+dy,45.2,42.4+dy),GOLD_HI,None,0)


def draw_turret(a,*,recoil=0.0,damaged=False,phase=0.0,cx=32.0,cy=32.0):
    """Compact rotating turret and long barrel, drawn facing toward the top edge."""
    ring=(57,62,72,255) if not damaged else (35,35,41,255)
    armor=(196,45,55,255) if not damaged else (63,45,48,255)
    armor_hi=RED_LIGHT if not damaged else (92,59,59,255)
    armor_side=RED_DARK if not damaged else (43,39,45,255)
    metal=METAL if not damaged else (60,58,62,255)
    # Turret race and small rounded turret body.
    a.ellipse((cx-11.0,cy-10.2,cx+11.0,cy+10.5),ring,INK,1.7)
    a.ellipse((cx-9.3,cy-8.6,cx+9.3,cy+8.9),SLATE if not damaged else (48,47,53,255),INK,1.0)
    a.roundrect((cx-8.4,cy-10.0,cx+8.4,cy+10.1),5.2,armor,INK,1.8)
    a.polygon([(cx-6.8,cy-8.1),(cx-1.2,cy-9.1),(cx+2.0,cy-4.5),
               (cx-4.1,cy-3.4)],armor_hi,None,0)
    a.polygon([(cx+5.4,cy-6.6),(cx+8.0,cy-3.5),(cx+7.8,cy+6.1),
               (cx+4.8,cy+8.2)],armor_side,None,0)
    # Hatch at the rear of the turret; it rotates with the gun.
    a.ellipse((cx-4.6,cy+1.7,cx+4.8,cy+9.5),ring,INK,1.1)
    a.ellipse((cx-3.1,cy+3.0,cx+3.1,cy+8.3),armor_hi if not damaged else (76,56,57,255),INK,0.85)
    a.ellipse((cx-0.9,cy+4.3,cx+0.9,cy+6.1),GOLD if not damaged else (79,65,56,255),INK,0.5)
    # Cannon mantlet and barrel, with the recoil sliding the barrel back into the turret.
    a.roundrect((cx-4.4,cy-7.2+recoil,cx+4.4,cy-2.6+recoil),1.4,ring,INK,1.0)
    a.line([(cx,cy-4.4+recoil),(cx,cy-24.1+recoil)],INK,6.0)
    a.line([(cx-0.1,cy-4.8+recoil),(cx-0.1,cy-23.8+recoil)],metal,3.4)
    a.line([(cx-0.85,cy-7.5+recoil),(cx-0.85,cy-20.3+recoil)],METAL_HI if not damaged else (105,97,96,255),0.65)
    # Large muzzle brake: chunky ports make the barrel readable in small frames.
    a.roundrect((cx-4.1,cy-26.0+recoil,cx+4.1,cy-21.5+recoil),1.0,
                (73,79,89,255) if not damaged else (41,40,45,255),INK,1.1)
    for x in (cx-2.4,cx+2.4):
        a.roundrect((x-0.6,cy-25.1+recoil,x+0.6,cy-22.2+recoil),0.35,
                    (29,30,36,255),None,0)
    a.line([(cx-3.0,cy-22.0+recoil),(cx+3.0,cy-22.0+recoil)],SLATE_LIGHT if not damaged else (77,71,73,255),0.55)
    # Tiny range marks on the turret cheeks.
    if not damaged:
        for x in (cx-6.6,cx+6.6):
            a.ellipse((x-0.65,cy-0.5,x+0.65,cy+0.8),GOLD_HI,INK,0.45)


def draw_turret_layer(angle=0.0,*,recoil=0.0,damaged=False,phase=0.0):
    layer=Art()
    draw_turret(layer,recoil=recoil,damaged=damaged,phase=phase)
    if angle:
        layer.image=layer.image.rotate(-angle,resample=Image.Resampling.BICUBIC,
                                       center=(FRAME*SCALE/2,FRAME*SCALE/2),expand=False)
    return layer.image


def draw_moving_tank(a,*,frame,phase,dy,damaged=False,with_turret=True):
    draw_hull(a,phase=phase,dy=dy,damaged=damaged,smoke_frame=frame)
    if with_turret:
        a.image.alpha_composite(draw_turret_layer(0.0,damaged=damaged,phase=phase))


def draw_flash(a,cx,cy,radius=7.5):
    # Heavy, bright comic flash from the cannon muzzle; clean points, no blur.
    pts=[]
    for i in range(20):
        ang=-math.pi/2+i*math.tau/20
        r=radius if i%2==0 else radius*(0.55 if i%4==0 else 0.68)
        pts.append((cx+math.cos(ang)*r,cy+math.sin(ang)*r))
    a.polygon(pts,FIRE_ORANGE,INK,0.9)
    inner=[]
    for i in range(12):
        ang=-math.pi/2+i*math.tau/12
        r=radius*0.56 if i%2==0 else radius*0.38
        inner.append((cx+math.cos(ang)*r,cy+math.sin(ang)*r))
    a.polygon(inner,FIRE_YELLOW,INK,0.55)
    a.ellipse((cx-radius*0.23,cy-radius*0.32,cx+radius*0.23,cy+radius*0.25),GOLD_HI,None,0)


def draw_shell(a,x,y,angle=-0.55):
    ca,sa=math.cos(angle),math.sin(angle)
    pts=[]
    for ox,oy in [(-2.5,-0.9),(1.5,-0.9),(2.5,0.0),(1.5,0.9),(-2.5,0.9)]:
        pts.append((x+ox*ca-oy*sa,y+ox*sa+oy*ca))
    a.polygon(pts,GOLD,INK,0.6)
    a.line([(x-1.0*ca,y-0.65*sa),(x+0.8*ca,y-0.65*sa)],GOLD_HI,0.45)


def draw_fire(a,cx,cy,scale=1.0):
    pts=[(cx-5.5*scale,cy+3.2*scale),(cx-4.7*scale,cy-2.0*scale),
         (cx-2.4*scale,cy-0.8*scale),(cx-0.7*scale,cy-7.0*scale),
         (cx+1.5*scale,cy-3.0*scale),(cx+4.0*scale,cy-6.0*scale),
         (cx+4.8*scale,cy-0.3*scale),(cx+6.0*scale,cy+3.4*scale)]
    a.polygon(pts,FIRE_RED,INK,0.85)
    a.polygon([(cx-3.2*scale,cy+2.5*scale),(cx-1.2*scale,cy-2.0*scale),
               (cx+0.1*scale,cy+0.1*scale),(cx+2.0*scale,cy-3.2*scale),
               (cx+3.4*scale,cy+2.7*scale)],FIRE_ORANGE,None,0)
    a.ellipse((cx-1.0*scale,cy-0.2*scale,cx+1.2*scale,cy+2.8*scale),FIRE_YELLOW,None,0)


def draw_explosion(a,cx=32.0,cy=29.0,radius=22.0):
    pts=[]
    for i in range(24):
        ang=-math.pi/2+i*math.tau/24
        r=radius if i%2==0 else radius*(0.57 if i%4==0 else 0.69)
        pts.append((cx+math.cos(ang)*r,cy+math.sin(ang)*r))
    a.polygon(pts,(255,111,38,232),INK,1.2)
    inner=[]
    for i in range(16):
        ang=-math.pi/2+i*math.tau/16
        r=radius*0.58 if i%2==0 else radius*0.41
        inner.append((cx+math.cos(ang)*r,cy+math.sin(ang)*r))
    a.polygon(inner,(255,199,61,230),INK,0.85)
    a.ellipse((cx-radius*0.29,cy-radius*0.30,cx+radius*0.29,cy+radius*0.30),
              (255,239,160,240),None,0)


def draw_heavy_smoke(a,frame,*,two_columns=False):
    # Opaque charcoal puffs billow upward but retain crisp illustrative outlines.
    if two_columns:
        groups=[(25.5,31.0,6.7,5.8),(22.8,24.5,6.2,6.2),(27.0,17.5,6.5,6.0),
                (39.2,31.0,6.7,5.8),(41.6,24.0,6.2,6.4),(37.7,17.0,6.5,6.0),
                (32.0,11.2,5.5,5.2)]
    else:
        groups=[(31.5,30.0,7.0,6.0),(27.0,23.0,7.0,6.5),(36.0,17.5,6.8,6.6),
                (30.0,11.0,6.0,5.5),(36.0,6.8,4.8,4.5)]
    wobble=0.55*math.sin(frame*0.72)
    for i,(cx,cy,rx,ry) in enumerate(groups):
        x=cx+wobble*((i%2)*2-1)
        fill=SMOKE_DARK if i<4 else SMOKE_MID
        a.ellipse((x-rx,cy-ry,x+rx,cy+ry),fill,INK,0.85)
        a.ellipse((x-rx*0.47,cy-ry*0.72,x+rx*0.12,cy-ry*0.14),
                  (101,97,103,150),None,0)
    for x,y,r in ((21,12,0.9),(44,9,1.0),(42,28,0.8)):
        a.ellipse((x-r,y-r,x+r,y+r),(41,39,46,218),None,0)


def draw_detached_turret(a,cx=42.0,cy=16.0,angle=-32.0):
    """A simplified same-design turret tossed clear of the wreck."""
    layer=Art()
    draw_turret(layer,damaged=False)
    rot=layer.image.rotate(angle,resample=Image.Resampling.BICUBIC,
                           center=(FRAME*SCALE/2,FRAME*SCALE/2),expand=False)
    # Paste the rotated drawing at its new center without leaving the 64px frame.
    a.image.alpha_composite(rot,dest=(int((cx-32.0)*SCALE),int((cy-32.0)*SCALE)))


def draw_track_debris(a):
    # Short, clearly separated chain runs thrown just outside the burned hull.
    for x,y,flip in ((1.5,38.0,False),(54.4,43.0,True),(2.0,48.5,True),(53.5,34.5,False)):
        a.roundrect((x,y,x+8.0,y+4.4),1.6,(32,33,39,255),INK,0.9)
        for j in range(3):
            xx=x+1.4+j*2.5
            if flip:
                a.line([(xx,y+0.75),(xx-0.35,y+3.5)],SLATE_LIGHT,0.75)
            else:
                a.line([(xx,y+0.75),(xx+0.35,y+3.5)],SLATE_LIGHT,0.75)


def render_frame(animation,index,count):
    a=Art()
    if animation=="forward":
        phase=math.tau*index/count
        # Heavy suspension bob is larger and slower than the jeep's bounce.
        dy=1.1*(1.0-math.cos(phase))
        draw_moving_tank(a,frame=index,phase=phase,dy=dy)
    elif animation=="turret_rotate":
        # Hull and tracks remain exactly still; turret advances 45 degrees per frame.
        draw_hull(a,phase=0.0,dy=0.0,damaged=False,smoke_frame=None)
        angle=45.0*index
        a.image.alpha_composite(draw_turret_layer(angle,phase=0.0))
    elif animation=="shoot":
        recoil={0:0.0,1:0.0,2:1.1,3:2.1,4:1.2,5:0.0}[index]
        body_back={0:0.0,1:0.0,2:1.0,3:2.4,4:1.2,5:0.0}[index]
        draw_hull(a,phase=0.0,dy=body_back,damaged=False,smoke_frame=None)
        a.image.alpha_composite(draw_turret_layer(0.0,recoil=recoil,phase=0.0))
        muzzle_y=6.0+recoil+body_back
        if index==1:
            # A tiny muzzle spark gives the six-frame shot a clear anticipation beat.
            draw_flash(a,32.0,5.0,2.3)
        if index in (2,3):
            draw_flash(a,32.0,muzzle_y+1.5,8.0 if index==2 else 7.0)
        shells={2:(39.0,27.5,-0.85),3:(42.0,24.0,-0.5),4:(45.0,25.0,-0.12)}
        if index in shells:
            draw_shell(a,*shells[index])
        if index in (3,4,5):
            power={3:0.82,4:0.94,5:0.46}[index]
            smoke_scale={3:0.78,4:1.0,5:1.25}[index]
            draw_smoke_puff(a,32.0+(index-3)*0.8,8.2-(index-3)*0.8,
                            3.2*smoke_scale,2.7*smoke_scale,power,dark=False)
            draw_smoke_puff(a,34.0+(index-3)*0.6,6.4-(index-3)*0.4,
                            2.1*smoke_scale,1.8*smoke_scale,power*0.8,dark=False)
    elif animation=="explosion":
        if index==0:
            draw_hull(a,phase=0.0,dy=0.0,damaged=False,smoke_frame=None)
            a.image.alpha_composite(draw_turret_layer())
        elif index==1:
            draw_hull(a,phase=0.0,dy=0.0,damaged=False,smoke_frame=None)
            a.image.alpha_composite(draw_turret_layer())
            draw_fire(a,32.0,24.0,0.85)
        elif index==2:
            draw_hull(a,phase=0.0,dy=1.0,damaged=False,smoke_frame=None)
            a.image.alpha_composite(draw_turret_layer(recoil=0.8))
            draw_explosion(a,32.0,28.0,24.0)
            draw_fire(a,32.0,24.5,1.0)
        elif index==3:
            draw_hull(a,phase=0.0,dy=0.4,damaged=True,smoke_frame=None)
            draw_heavy_smoke(a,index,two_columns=False)
            draw_detached_turret(a,38.5,19.0,90.0)
            draw_fire(a,25.0,25.0,0.55)
        elif index==4:
            draw_hull(a,phase=0.0,dy=0.0,damaged=True,smoke_frame=None)
            draw_heavy_smoke(a,index,two_columns=False)
            draw_fire(a,23.5,25.0,0.75)
            draw_fire(a,40.0,28.0,0.82)
            draw_track_debris(a)
        else:
            draw_hull(a,phase=0.0,dy=0.0,damaged=True,smoke_frame=None)
            draw_heavy_smoke(a,index,two_columns=True)
            # Faint embers remain, but no large flame in the final charcoal frame.
            for x,y in ((22.0,25.0),(41.5,29.0)):
                a.ellipse((x-0.65,y-0.65,x+0.65,y+0.65),FIRE_ORANGE,None,0)
            draw_track_debris(a)
    else:
        raise ValueError(f"Unknown animation: {animation}")
    return a.finish()


def build_sheet(animation,count,fps,loop,note):
    frames=[render_frame(animation,i,count) for i in range(count)]
    sheet=Image.new("RGBA",(FRAME*count,FRAME),(0,0,0,0))
    for i,frame in enumerate(frames):
        sheet.alpha_composite(frame,(FRAME*i,0))
    filename=f"tank_{animation}.png"
    sheet.save(OUT/filename,optimize=True)
    return {
        "file":filename,"animation":animation,"frames":count,
        "frameWidth":FRAME,"frameHeight":FRAME,
        "sheetWidth":FRAME*count,"sheetHeight":FRAME,
        "layout":"horizontal","alpha":True,"fps":fps,"loop":loop,"notes":note,
    }


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    definitions=[
        ("forward",8,8,True,"Heavy forward travel; six road wheels and tread links rotate, hull bobs, small rear exhaust puffs."),
        ("turret_rotate",8,8,True,"Stationary hull; turret and barrel rotate through eight 45-degree steps, looping through 360 degrees."),
        ("shoot",6,8,False,"One heavy cannon shot; bright muzzle burst, tank recoil, brass casing and expanding muzzle smoke."),
        ("explosion",6,8,False,"Intact tank, turret fire, comic blast, flying turret with heavy smoke, burning wreck with scattered track links, charcoal remains; hold frame 6."),
    ]
    sheets=[build_sheet(*row) for row in definitions]
    manifest={
        "pack":"cartoon_red_enemy_tank",
        "version":1,
        "description":"Original chibi enemy tank viewed from above, with a rounded red hull and rotating turret.",
        "vehicle":{
            "teamColors":["bright red","dark slate gray"],
            "features":["chubby rounded hull","visible continuous tracks","six road wheels on each side","long cannon with muzzle brake","small rotating turret and hatch"],
            "orientation":"front points to the top edge in forward, firing and explosion sheets",
            "styleNote":"Original colorful tower-defense cartoon; not a reproduction of any named game vehicle.",
        },
        "frame":{"width":FRAME,"height":FRAME,"format":"PNG RGBA","background":"transparent"},
        "sheets":sheets,
        "construct3":{
            "import":"Import each PNG as a sprite and set animation frame size to 64x64.",
            "frameOrder":"Frames are arranged left-to-right, starting at zero in the image editor.",
            "turretRotate":"Eight frames at 45 degrees per frame; loop after frame 8.",
            "oneShot":"Shoot and explosion are one-shot; hold explosion frame 6.",
            "motionBlur":"No motion blur; tank links and details remain crisp.",
        },
    }
    (OUT/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(f"Generated {len(sheets)} sprite sheets in {OUT}")


if __name__=="__main__":
    main()
