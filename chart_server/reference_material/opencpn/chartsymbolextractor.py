#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# by Will Kamp <manimaul!gmail.com>
# use this anyway you want

from xml.dom.minidom import parseString
import os
import json
from PIL import Image

f = open("chartsymbols.xml", "r")
lines = f.read()
f.close()

script_dir = os.path.dirname(os.path.realpath(__file__))

"""
tables:
<color-tables>, <lookups>, <line-styles>, <patterns>, <symbols>

Notes:

<lookup id="345" RCID="32380" name="AIRARE">
    <type>Area</type>
    <disp-prio>Area 1</disp-prio>
    <radar-prio>Suppressed</radar-prio>
    <table-name>Symbolized</table-name>
    <attrib-code index="0">CONVIS1</attrib-code>
    <instruction>AC(LANDA);AP(AIRARE02);LS(SOLD,1,CHBLK)</instruction>
    <display-cat>Standard</display-cat>
    <comment>22220</comment>
</lookup>

<type> = can be Area, Line, Point


<table-name> Plain, Lines, Simplified, Paper

<attrib-code index="0">
index can be 0,1,2,3
$SCODEAISSLP01 "AISSLP01" = this might reference the description of a symbol in the <symbols> table


<display-cat>
Standard, DisplayBase Other, Mariners

<instruction>
; delimeted 
SY = symbol
    example: SY(PLNPOS02,ORIENT)
    
TX = text

CS = this looks like it references functions in s52cnsy.cpp
https://github.com/OpenCPN/OpenCPN/blob/6acf43c93a71463be907f228f7175bf906ad019e/src/s52cnsy.cpp

"""


def read_symbols():
    dom = parseString(lines)
    result = dict()
    for lookup in dom.getElementsByTagName("lookup"):
        table_name = lookup.getElementsByTagName("table-name")[0].firstChild.nodeValue
        if table_name not in result:
            result[table_name] = set()
        inst = lookup.getElementsByTagName("instruction")
        if inst is not None and inst.item(0) is not None and inst.item(0).firstChild is not None:
            for ea in inst.item(0).firstChild.nodeValue.split(";"):
                if ea.startswith("SY"):
                    for ea in ea[3:-1].split(","):
                        result[table_name].add(ea)
    for key in result:
        result[key] = list(result[key])
    return result


def read_sprites(render_img: bool = False, only_names: set = None):
    dom = parseString(lines)
    result = dict()
    for symbol in dom.getElementsByTagName("symbol"):
        name = symbol.getElementsByTagName("name")[0].firstChild.nodeValue
        if only_names is not None and name not in only_names:
            continue
        btmEle = symbol.getElementsByTagName("bitmap")
        if len(btmEle) > 0:
            locEle = btmEle[0].getElementsByTagName("graphics-location")
            width = int(btmEle[0].attributes["width"].value)
            height = int(btmEle[0].attributes["height"].value)
            x = locEle[0].attributes["x"].value
            y = locEle[0].attributes["y"].value
            if render_img:
                im = Image.open(os.path.join(script_dir, "rastersymbols-day.png"))
                # (left, upper, right, lower) = (20, 20, 100, 100)
                im = im.crop((int(x), int(y), int(x) + int(width), int(y) + int(height)))
                im.save(os.path.join(script_dir, "out/{}.png".format(name)))
            result[name] = {
                "width": int(width),
                "height": int(height),
                "x": int(x),
                "y": int(y),
                "pixelRatio": 1
            }
    print(json.dumps(result, indent=2))


def read_colors():
    dom = parseString(lines)
    result = dict()
    for col_table in dom.getElementsByTagName("color-table"):
        name = col_table.attributes["name"].value
        col_list = dict()
        result[name] = col_list
        for child in col_table.getElementsByTagName("color"):
            col_name = child.attributes["name"].value
            r = int(child.attributes["r"].value)
            g = int(child.attributes["g"].value)
            b = int(child.attributes["b"].value)
            color_rgb = (r, g, b)
            hex_color = '#{:02x}{:02x}{:02x}'.format(*color_rgb)
            col_list[col_name] = hex_color
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    read_sprites(render_img=True)
    # paper_symbols = set(read_symbols()["Paper"])
    # read_sprites(render_img=True, only_names=paper_symbols)
    # print(json.dumps(read_symbols(), indent=2))
