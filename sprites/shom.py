#!/usr/bin/env python3

import json
import os

base_dir = os.path.dirname(os.path.realpath(__file__))
res_dir = os.path.join(base_dir, "../chart_server/src/main/resources")
colors = os.path.join(base_dir, "../chart_server/src/main/resources/colors.json")

sweden_colors = [
    ("NODTA", 208, 216, 217),
    ("CHBRN", 215, 187, 114),
    ("LITRD", 250, 80, 80),
    ("LANDA", 250, 241, 163),
    ("LANDF", 215, 187, 114),
    ("DEPDW", 250, 250, 250),
    ("DEPMD", 235, 243, 255),
    ("DEPMS", 199, 225, 255),
    ("DEPVS", 158, 198, 253),
]

shom_colors = [
    ("LANDA", 255, 224, 183),
    ("NODTA", 225, 224, 222),
    ("DEPDW", 255, 249, 245),
    ("LANDF", 255, 200, 125),
    ("CHBRN", 226, 192, 156),
]


def rgb2hex(r, g, b):
    return "#{:02x}{:02x}{:02x}".format(r, g, b)


def make(tpl):
    dd = dict()
    for each in tpl:
        dd[each[0]] = rgb2hex(each[1], each[2], each[3])
    return dd


with open(colors, "r+") as fp:
    d = json.load(fp)
    fp.seek(0)

    d["custom"] = {
        "sweden": {"DAY": make(sweden_colors)},
        "shom": {"DAY": make(shom_colors)}
    }

    j = json.dumps(d, indent=2)
    fp.write(j)
    fp.close()


