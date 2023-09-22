#!/usr/bin/env python3
import os.path
import subprocess

base_dir = os.path.dirname(os.path.realpath(__file__))
svg = os.path.join(base_dir, "svg")
sprites = os.path.join(base_dir, "simplified")
sprites2x = os.path.join(base_dir, "simplified2x")

cmd = """inkscape
-o {} 
--export-dpi={}
--export-background-opacity=0 
{}
"""

for dpi, dir in [(90, sprites), (180, sprites2x)]:
    for each in os.listdir(svg):
        if each.endswith('.svg'):
            png = os.path.join(dir, each[:-3] + 'png')
            s = os.path.join(svg, each)
            c = cmd.format(png, dpi, s).split()
            print(subprocess.check_output(c))

