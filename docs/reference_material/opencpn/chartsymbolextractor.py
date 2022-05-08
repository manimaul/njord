#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# by Will Kamp <manimaul!gmail.com>
# use this anyway you want

from xml.dom.minidom import parseString
import os
import json
import yaml
import subprocess as sp
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


def hpgl_line_styles_symbols_patterns(filter_set: set = set(["BOYCAN01"])):
    dom = parseString(lines)
    result = {
        "linestyles": [],
        "symbols": [],
        "patterns": [],
    }
    # for line_style in dom.getElementsByTagName("line-style"):
    #     item = dict()
    #     item["name"] = line_style.getElementsByTagName("name")[0].firstChild.nodeValue
    #     item["description"] = line_style.getElementsByTagName("description")[0].firstChild.nodeValue
    #     item["hpgl"] = line_style.getElementsByTagName("HPGL")[0].firstChild.nodeValue
    #     item["color_ref"] = line_style.getElementsByTagName("color-ref")[0].firstChild.nodeValue
    #     result["linestyles"].append(item)
    # for line_style in dom.getElementsByTagName("pattern"):
    #     item = dict()
    #     item["name"] = line_style.getElementsByTagName("name")[0].firstChild.nodeValue
    #     item["description"] = line_style.getElementsByTagName("description")[0].firstChild.nodeValue
    #     item["definition"] = line_style.getElementsByTagName("definition")[0].firstChild.nodeValue
    #     item["filltype"] = line_style.getElementsByTagName("filltype")[0].firstChild.nodeValue
    #     item["spacing"] = line_style.getElementsByTagName("spacing")[0].firstChild.nodeValue
    #     try:
    #         item["hpgl"] = line_style.getElementsByTagName("HPGL")[0].firstChild.nodeValue
    #         item["color_ref"] = line_style.getElementsByTagName("color-ref")[0].firstChild.nodeValue
    #         result["patterns"].append(item)
    #     except:
    #         print("skipping {}".format(item["name"]))
    #         pass

    for symbol in dom.getElementsByTagName("symbol"):
        item = dict()
        try:
            name = symbol.getElementsByTagName("name")[0].firstChild.nodeValue
            item["name"] = name
            if filter_set is not None and name not in filter_set:
                continue
            vector = symbol.getElementsByTagNameNS("*", "vector")[0]
            item["width"] = int(vector.attributes["width"].value)
            item["height"] = int(vector.attributes["height"].value)
            item["distance_min"] = int(vector.getElementsByTagName("distance")[0].attributes["min"].value)
            item["distance_max"] = int(vector.getElementsByTagName("distance")[0].attributes["max"].value)
            item["pivot_x"] = int(vector.getElementsByTagName("pivot")[0].attributes["x"].value)
            item["pivot_y"] = int(vector.getElementsByTagName("pivot")[0].attributes["y"].value)
            item["origin_x"] = int(vector.getElementsByTagName("origin")[0].attributes["x"].value)
            item["origin_y"] = int(vector.getElementsByTagName("origin")[0].attributes["y"].value)
            item["hpgl"] = vector.getElementsByTagName("HPGL")[0].firstChild.nodeValue
            result["symbols"].append(item)
        except Exception as error:
            print("skipping {} error = {}".format(item["name"], error))
            pass

    hpgl_dir = os.path.join(script_dir, "out/hpgl")
    os.makedirs(hpgl_dir, exist_ok=True)
    with open(os.path.join(hpgl_dir, "hpgl_items.json"), "w") as hpgl_json:
        json.dump(result, fp=hpgl_json)

    container = sp.getoutput("docker run -dit -w /hpgl -v {}:/hpgl hpgl".format(hpgl_dir))
    print("container running {}".format(container))

    for group in result:
        out_dir = os.path.join(hpgl_dir, group)
        os.makedirs(out_dir, exist_ok=True)
        for each in result[group]:
            name = each["name"]
            hpgl_name = "{}.hpgl".format(name)
            with open(os.path.join(out_dir, hpgl_name), "w") as hpgl:
                hpgl.write(each["hpgl"])
            print(sp.getoutput("docker exec -w /hpgl {} hp2xx -r 180 -m svg /hpgl/{}/{}".format(container, group, hpgl_name)))
            svg_name = os.path.join(out_dir, "{}.hpgl.svg".format(name))
            with open(svg_name, "r+") as svg:
                data = svg.read().replace("stroke:rgb(255,255,255); fill:none; stroke-width:0.100mm", "stroke:rgb(0,0,0); fill:none; stroke-width:4mm")
                svg.seek(0)
                svg.write(data)
                svg.truncate()

            # width = sp.getoutput('inkscape --batch-process --actions="select-all;SelectionGroup;query-width;" {}'.format(svg_name))
            print(sp.getoutput('inkscape --batch-process '
                               '--actions "select-all" '
                               '--verb "ObjectFlipHorizontally;FitCanvasToDrawing;FileSave;FileClose" {}'.format(svg_name)))
            # inkscape --action-list | less
            # inkscape --verb-list | less
            svg_opt_name = os.path.join(out_dir, "{}.svg".format(name))
            print(sp.getoutput('scour -i {} -o {}'.format(svg_name, svg_opt_name)))
            # print(sp.getoutput('inkscape --export-plain-svg={} {}'.format(svg_name, svg_name)))

    print("kill container <{}>".format(sp.getoutput("docker kill {}".format(container))))


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


# def str_int(input: str):
#     try:
#         return int(input.replace('?', ""))
#     except:
#         return input


# def filter_sy_rule(rule: dict):
#     _, value = rule
#     sy = list(filter(lambda d: "SY" in d, value))
#     return len(sy) > 0


def read_symbol_rules(theme: str = "Paper"):
    dom = parseString(lines)
    result = dict()
    for lookup in dom.getElementsByTagName("lookup"):
        name = str(lookup.attributes['name'].value).upper()  # ex BOYSPP
        table_name = lookup.getElementsByTagName("table-name")[0].firstChild.nodeValue
        rule = dict()
        if table_name not in result:
            result[table_name] = dict()  # key = Plain, Symbolized, Simplified, Paper
        if name not in result[table_name]:
            result[table_name][name] = list()  # rules
        result[table_name][name].append(rule)
        inst = lookup.getElementsByTagName("instruction")
        attr = lookup.getElementsByTagName("attrib-code")
        if attr is not None:
            rule["ATT"] = list()
            for att in attr:
                att_str = att.firstChild.nodeValue
                att_key = att_str[0:6].upper()
                att_values = list(filter(lambda x: len(x.strip()) > 0 and not x.endswith("?"), att_str[6:].split(",")))
                # att_values = list(map(str_int, att_values))
                rule["ATT"].append({att_key: att_values})
        if inst is not None and inst.item(0) is not None and inst.item(0).firstChild is not None:
            for ea in inst.item(0).firstChild.nodeValue.split(";"):
                if ea.startswith("SY"):
                    for sy in ea[3:-1].split(","):
                        rule["SY"] = sy
    result = result[theme]
    for each in result:
        symbols = result[each]
        for each in symbols:
            if "SY" not in each:
                symbols.remove(each)

    # result = dict(filter(filter_sy_rule, result[theme].items()))
    return result


def read_sprites(render_img: str = None, only_names: set = None):
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
                im.save(os.path.join(script_dir, "{}/{}.png".format(render_img, name)))
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


def generate_yaml():
    with open("paper_symbol_rules.yaml", 'w', encoding="utf-8") as f:
        yaml.dump(read_symbol_rules("Paper"), f, indent=2)
    with open("simplified_symbol_rules.yaml", 'w', encoding="utf-8") as f:
        yaml.dump(read_symbol_rules("Simplified"), f, indent=2)


if __name__ == '__main__':

    # import os
    # os.makedirs("out", exist_ok=True)

    # os.makedirs("out/paper")
    # read_sprites(render_img="out/paper", only_names=set(read_symbols()["Paper"]))
    # os.makedirs("out/simplified")
    # read_sprites(render_img="out/simplified", only_names=set(read_symbols()["Simplified"]))
    # print(json.dumps(read_symbols()["Paper"], indent=2))
    # read_sprites(render_img=True, only_names=paper_symbols)
    # print(json.dumps(read_symbols(), indent=2))
    # hpgl_line_styles()
    hpgl_line_styles_symbols_patterns(filter_set=None)
