#!/usr/bin/env python3
"""Install cartoon tower artwork into Dejban's existing Godot asset slots.

Run with Pillow installed: python tools/install_turret_art.py
Preserves the existing Tower.gd contract: fixed 96x84 bases and 4-frame
96x84 rotating tops (idle/idle/fire/recoil), plus static legacy head images.
"""
from PIL import Image, ImageDraw
import math, os

ROOT='games/dejban/godot/assets/sprites/towers'
FAMILIES={'machinegun':'machine_gun','artillery':'cannon','rocket':'missile','laser':'laser','tesla':'tesla'}
COLORS={1:((143,91,56),(76,51,38),(180,117,62)),2:((158,165,150),(75,81,77),(215,204,166)),3:((64,149,202),(30,73,112),(75,213,255)),4:((142,77,174),(59,39,88),(218,122,255)),5:((219,164,47),(112,69,25),(255,226,104))}
INK=(42,34,37,255)
def rgba(rgb): return (*rgb[:3],255)
def poly(d,p,fill,outline=INK,w=3):
 d.polygon(p,fill=fill)
 if outline is not None: d.line(p+[p[0]],fill=outline,width=w,joint='curve')
def ellipse(d,b,fill,outline=INK,w=3): d.ellipse(b,fill=fill,outline=outline,width=w)
def line(d,p,fill=INK,w=3): d.line(p,fill=fill,width=w,joint='curve')

def base_sprite():
 im=Image.new('RGBA',(96,84),(0,0,0,0)); d=ImageDraw.Draw(im)
 ellipse(d,(20,48,76,77),(37,29,26,75),None,0)
 poly(d,[(18,49),(31,40),(64,40),(78,49),(73,65),(63,73),(32,73),(21,65)],rgba((112,107,97)))
 poly(d,[(23,49),(33,44),(62,44),(72,49),(67,57),(29,57)],rgba((175,164,138)))
 line(d,[(32,48),(32,57),(26,62)],rgba((83,78,70)),2); line(d,[(64,47),(63,55),(69,61)],rgba((83,78,70)),2)
 poly(d,[(23,62),(33,58),(40,62),(38,69),(29,72),(23,68)],rgba((160,117,70)))
 poly(d,[(57,62),(66,58),(73,62),(71,69),(62,72),(57,68)],rgba((207,166,99)))
 return im

def turret_frame(tower,level,frame):
 metal,shadow,accent=map(rgba,COLORS[level]); im=Image.new('RGBA',(96,84),(0,0,0,0)); d=ImageDraw.Draw(im)
 recoil=frame==3; shot=frame==2; # 0/1 idle, 2 fire, 3 spring recoil
 cy=47+(2 if recoil else 0)
 ellipse(d,(30,32,66,65),rgba((53,46,42)))
 ellipse(d,(34,34,62,59),metal)
 # upgrade plates, bolts, and conspicuous level glow
 if level>=2:
  poly(d,[(32,44),(38,39),(43,42),(42,51),(35,54)],accent,w=2)
  poly(d,[(54,42),(60,40),(64,46),(60,54),(54,51)],accent,w=2)
 if level>=3:
  ellipse(d,(42,39,54,51),accent,INK,2)
 # generic barrels point up; artwork retains vertical north orientation for game rotation.
 if tower in ('machinegun','artillery','laser'):
  count=(1,1,2,2,3)[level-1] if tower=='machinegun' else ((1,1,2,3,1)[level-1] if tower=='laser' else 1)
  length=(20,23,25,27,29)[level-1] if tower=='machinegun' else ((19,22,25,28,30)[level-1] if tower=='laser' else (17,20,23,27,30)[level-1])
  width=5 if tower!='artillery' else 10
  for k in range(count):
   x=48+(k-(count-1)/2)*7
   end_y=cy-length+(4 if recoil else 0)
   line(d,[(x,cy+2),(x,end_y)],shadow,width+4)
   line(d,[(x,cy+2),(x,end_y)],metal,width)
   poly(d,[(x-width/2-2,end_y-2),(x+width/2+2,end_y-2),(x+width/2+2,end_y+4),(x-width/2-2,end_y+4)],accent,w=2)
   if shot and k==count//2:
    poly(d,[(x,end_y-14),(x-6,end_y-4),(x-2,end_y-4),(x-6,end_y+2),(x+7,end_y-8),(x+2,end_y-8)],rgba((255,191,58)),None)
  # rotating armor silhouette
  if tower=='artillery':
   poly(d,[(34,43),(39,34),(55,33),(62,42),(59,54),(38,56)],metal)
   # redraw raised gun over the armor so muzzle remains legible
   line(d,[(48,cy+2),(48,cy-length+(4 if recoil else 0))],shadow,14)
   line(d,[(48,cy+2),(48,cy-length+(4 if recoil else 0))],metal,10)
  elif tower=='machinegun': poly(d,[(36,42),(41,37),(55,37),(60,43),(56,55),(40,55)],metal)
  else:
   for k in range(count):
    x=48+(k-(count-1)/2)*7; ellipse(d,(x-5,39,x+5,49),accent,INK,2)
 elif tower=='rocket':
  count=(2,4,6,8,12)[level-1]; cols=2 if count<=4 else 3; rows=math.ceil(count/cols)
  poly(d,[(30,37),(66,37),(68,57),(62,63),(34,63),(28,57)],metal)
  for j in range(count):
   col=j%cols; row=j//cols; x=48+(col-(cols-1)/2)*9; y=47+(row-(rows-1)/2)*7
   ellipse(d,(x-3,y-4,x+3,y+4),shadow,INK,2)
   ellipse(d,(x-1,y-3,x+1,y-1),accent,None,0)
  if shot:
   # single animated launch flash and smoke plume
   ellipse(d,(44,26,52,36),rgba((255,170,63)),INK,1); ellipse(d,(42,21,50,29),rgba((216,216,205)),None,0)
 else: # Tesla coil, level-up rings and lightning
  coil=(8,10,13,16,20)[level-1]
  ellipse(d,(48-coil,cy-coil,48+coil,cy+coil),shadow)
  ellipse(d,(43,cy-6,53,cy+6),accent,INK,2)
  if level>=4: ellipse(d,(29,31,67,68),None,accent,3)
  line(d,[(48,cy-8),(42,cy-17),(52,cy-23),(46,cy-32)],accent if level>=3 else rgba((226,194,95)),4 if shot else 2)
  if shot: line(d,[(46,cy-32),(57,cy-37)],rgba((245,249,255)),3)
 # level 4 purple arc, level 5 gold top accents and energy core
 if level==4 and tower!='tesla':
  ellipse(d,(29,31,67,68),None,accent,3)
 if level==5:
  for x in (37,59): ellipse(d,(x-3,40,x+3,46),rgba((255,224,101)),INK,1)
  ellipse(d,(43,38,53,48),rgba((255,241,150)),INK,2)
 return im

def main():
 os.makedirs(ROOT,exist_ok=True); base=base_sprite(); written=0
 base.save(os.path.join(ROOT,'base.png'))
 for family,game_id in FAMILIES.items():
  for level in range(1,6):
   top=Image.new('RGBA',(96*4,84),(0,0,0,0))
   frames=[turret_frame(family,level,f) for f in range(4)]
   for i,im in enumerate(frames): top.alpha_composite(im,(96*i,0))
   # The deployed game intentionally uses one shared stationary foundation for every turret.
   base.save(os.path.join(ROOT,f'base_{game_id}_lv{level}.png'))
   top.save(os.path.join(ROOT,f'top_{game_id}_lv{level}.png'))
   # Keep legacy static-head asset keys useful for the armory and fallback scenes.
   head=frames[0].crop((9,11,86,73))
   head.save(os.path.join(ROOT,f'head_{game_id}_lv{level}.png'))
   written+=3
 print(f'Installed {written} base/top/head assets for 5 families x 5 levels into {ROOT}')
if __name__=='__main__': main()
