#!/usr/bin/env python3
import os
import json
import shutil

base_dir = os.path.dirname(os.path.realpath(__file__))

svg_dir = "/home/williamkamp/source/OpenS100/ProgramData/PC/S101_Portrayal_1.1.1/Symbols"
with open("/home/williamkamp/source/njord/chart_server/src/main/resources/www/sprites/simplified.json") as fp:
    sj = json.load(fp).keys()

osvg = set(os.listdir(svg_dir))

cp_lst = set()
skipped = set()
for each in sj:
    name = "{}.svg".format(each)
    if name in osvg:
        cp_lst.add(name)
    else:
        skipped.add(name)


def cp_svg():
    for each in osvg:
        if each in cp_lst:
            print("copy {}".format(each))
            shutil.copy(os.path.join(svg_dir, each), os.path.join(base_dir, "nsvg"))
        else:
            print("copy unused {}".format(each))
            shutil.copy(os.path.join(svg_dir, each), os.path.join(base_dir, "nsvg_unused"))


# cp_svg()

for ea in skipped:
    print(ea)
