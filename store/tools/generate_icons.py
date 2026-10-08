#!/usr/bin/env python3
"""Draws the launcher icon PNGs (all densities) and the 512 px Play Store icon.
Run from the repository root:  python3 store/tools/generate_icons.py store/graphics"""
import math, os, sys
from PIL import Image, ImageDraw
RES = 'app/src/main/res'
BG=(10,13,15,255); GREEN=(38,242,115,255)
SS=4  # supersampling

def mark(size_px, scale_units, offset_units, bg=None, shape=None):
    """Render the mark. Pixel = (unit-offset)*size/scale_units."""
    S=size_px*SS
    k=S/scale_units
    img=Image.new('RGBA',(S,S),(0,0,0,0))
    d=ImageDraw.Draw(img)
    if bg:
        if shape=='round': d.ellipse([0,0,S-1,S-1],fill=bg)
        elif shape=='square': d.rectangle([0,0,S-1,S-1],fill=bg)
        else: d.rounded_rectangle([0,0,S-1,S-1],radius=int(S*0.22),fill=bg)
    def P(x,y): return ((x-offset_units)*k,(y-offset_units)*k)
    cx,cy=54,54; r=26; w=6
    # dim full ring
    ring=Image.new('RGBA',(S,S),(0,0,0,0)); rd=ImageDraw.Draw(ring)
    x0,y0=P(cx-r,cy-r); x1,y1=P(cx+r,cy+r)
    rd.ellipse([x0,y0,x1,y1],outline=(38,242,115,56),width=int(w*k))
    img=Image.alpha_composite(img,ring)
    d=ImageDraw.Draw(img)
    # 270 degree arc from top clockwise to 9 o'clock (PIL angles: 0=3 o'clock, clockwise positive)
    d.arc([x0,y0,x1,y1],start=270,end=180,fill=GREEN,width=int(w*k))
    def cap(x,y,rad):
        px,py=P(x,y); rr=rad*k
        d.ellipse([px-rr,py-rr,px+rr,py+rr],fill=GREEN)
    cap(54,28,w/2)       # round cap at the start
    cap(28,54,5.5)       # live dot at the end
    # hands
    d.line([P(54,38),P(54,54),P(65,60)],fill=GREEN,width=int(w*k),joint='curve')
    cap(54,38,w/2); cap(65,60,w/2)
    return img.resize((size_px,size_px),Image.LANCZOS)

dens={'mdpi':48,'hdpi':72,'xhdpi':96,'xxhdpi':144,'xxxhdpi':192}
for n,px in dens.items():
    os.makedirs(f'{RES}/mipmap-{n}',exist_ok=True)
    mark(px,72,18,bg=BG).save(f'{RES}/mipmap-{n}/ic_launcher.png',optimize=True)
    mark(px,72,18,bg=BG,shape='round').save(f'{RES}/mipmap-{n}/ic_launcher_round.png',optimize=True)
out=sys.argv[1] if len(sys.argv)>1 else None
if out:
    os.makedirs(out,exist_ok=True)
    # Play Store icon: 512x512, full-bleed square (Play applies its own mask)
    mark(512,72,18,bg=BG,shape='square').save(f'{out}/play-icon-512.png',optimize=True)
