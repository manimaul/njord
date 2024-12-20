#!/usr/bin/env python3
import argparse
import json

import math
import os
import os.path
import sys
from dataclasses import dataclass
from typing import Any
import subprocess
import hashlib
import functools

from PIL import Image

base_dir = os.path.dirname(os.path.realpath(__file__))
svg_dir = os.path.join(base_dir, "svg")
svg_t_dir = os.path.join(svg_dir, "tmp")
sprites = os.path.join(base_dir, "simplified")
sprites2x = os.path.join(base_dir, "simplified2x")
sprite_sheet_dir = os.path.join(base_dir, "../chart_server/src/main/resources/www/sprites")
colors = os.path.join(base_dir, "../chart_server/src/main/resources/colors.json")

# https://registry.iho.int/portrayal/list.do?type=5

standard_pattern = [
    [1, 0],
    [0, 1],
]

wide_pattern = [
    [0, 0],
    [0, 1],
    [0, 0],
    [0, 0],
]

patterns = {"VEGATN04P": standard_pattern,
            "VEGATN03P": standard_pattern,
            "NODATA03P": standard_pattern,
            "SNDWAV01P": wide_pattern,
            "QUESMRK1P": standard_pattern,
            "PRTSUR01P": standard_pattern,
            "MARSHES1P": standard_pattern,
            "MARCUL01P": standard_pattern,
            "MARCUL02P": standard_pattern,
            "FSHHAV02P": standard_pattern,
            "FSHFAC04P": standard_pattern,
            "FSHFAC03P": standard_pattern,
            }


@dataclass
class Frame:
    name: str
    cell: Any
    pixel_ratio: int


def find_tile_wh(frames: [Frame]):
    width = 0
    height = 0
    for each in frames:
        width = max(width, each.cell.size[0])
        height = max(height, each.cell.size[1])

    return width, height


def save_sprite_sheet(retna: bool, theme: str):
    max_frames_row = 10
    frames = []

    sprites_t = os.path.join(sprites, theme)
    os.makedirs(sprites_t, exist_ok=True)
    sprites2x_t = os.path.join(sprites2x, theme)
    os.makedirs(sprites2x_t, exist_ok=True)

    files = set(filter(lambda x: x.endswith('.png'), os.listdir(sprites_t)))
    files2x = set(filter(lambda x: x.endswith('.png'), os.listdir(sprites2x_t)))

    if retna:
        for each in files2x:
            if each not in files:
                raise Exception('missing 1x file: ' + each)

    for current_file in files:
        png_path = os.path.join(sprites_t, current_file)
        pixel_ratio = 1

        if retna and current_file in files2x:
            png_path = os.path.join(sprites2x_t, current_file)
            pixel_ratio = 2

        try:
            with Image.open(png_path) as im:
                frames.append(Frame(current_file[:-4], im.getdata(), pixel_ratio))
        except:
            print(current_file + " is not a valid image")

    tile_width, tile_height = find_tile_wh(frames)
    frames = sorted(frames, key=lambda x: x.cell.size[1])

    if len(frames) > max_frames_row:
        spritesheet_width = tile_width * max_frames_row
        required_rows = math.ceil(len(frames) / max_frames_row)
        spritesheet_height = tile_height * required_rows
    else:
        spritesheet_width = tile_width * len(frames)
        spritesheet_height = tile_height

    spritesheet = Image.new("RGBA", (int(spritesheet_width), int(spritesheet_height)))

    sprite_json = dict()

    row = -1
    for i, each in enumerate(frames):
        top = tile_height * row
        column = int(i % max_frames_row)
        if column == 0:
            row += 1
        left = tile_width * column
        bottom = top + tile_height
        right = left + tile_width

        box = (left, top, right, bottom)
        box = [int(i) for i in box]
        cut_frame = each.cell.crop((0, 0, tile_width, tile_height))
        width, height = each.cell.size

        spritesheet.paste(cut_frame, box)
        sprite_json[each.name] = {
            "width": width,
            "height": height,
            "x": int(left),
            "y": int(top),
            "pixelRatio": each.pixel_ratio
        }
        print("tile_width={}, tile_height={}".format(tile_width, tile_height))
        print("column={}, row={}, name={}, width={}, height={}".format(column, row, each.name, width, height))

    name = "{}_simplified".format(theme)
    if retna:
        name = name + "@2x"
    with open(os.path.join(sprite_sheet_dir, "{}.json".format(name)), "w") as fp:
        json.dump(sprite_json, fp, indent=4)

    spritesheet.save(os.path.join(sprite_sheet_dir, "{}.png".format(name)), "PNG")


def pattern_png(png_path, matrix):
    with Image.open(png_path) as im:
        w = len(matrix[0])
        h = len(matrix)
        sz_w = w * im.width
        sz_h = h * im.height
        pattern = Image.new("RGBA", size=(sz_w, sz_h))
        for i in range(0, w):
            for ii in range(0, h):
                if matrix[ii][i] == 1:
                    top = i * im.height
                    left = ii * im.width
                    right = left + im.width
                    bottom = top + im.height
                    box = (left, top, right, bottom)
                    pattern.paste(im, box=box)
        pattern.save(png_path, format="PNG")


def temp_svg(css: str, svg: str, theme: str, svg_dir: str, svg_t_dir: str):
    orig = os.path.join(svg_dir, svg)
    temp = os.path.join(svg_t_dir, "{}_{}".format(theme, svg))
    with open(orig, "r") as fp:
        data = fp.read()
        with open(temp, "w+") as tfp:
            tfp.write(data.replace("</svg>", "<defs><style>{}</style></defs></svg>".format(css)))
            tfp.close()
        fp.close()

    return temp


def svg_unused_to_png(theme: str):
    svg_u_dir = os.path.join(base_dir, "svg_unused")
    svg_t_dir = os.path.join(svg_u_dir, "tmp")
    os.makedirs(svg_t_dir, exist_ok=True)
    with open(os.path.join(svg_dir, "{}SvgStyle.css".format(theme)), "r") as fp:
        css = fp.read()
        fp.close()

    for each in os.listdir(svg_u_dir):
        if each.endswith(".svg"):
            print("each: {}".format(each))
            temp_svg(css, each, theme, svg_u_dir, svg_t_dir)


def svg_to_png(filter: set, theme: str):
    dpi = 96
    cmd = """inkscape
    -o {} 
    --export-dpi={}
    --export-background-opacity=0 
    {}
    """
    with open(os.path.join(svg_dir, "{}SvgStyle.css".format(theme)), "r") as fp:
        css = fp.read()
        fp.close()

    sprites_t = os.path.join(sprites, theme)
    os.makedirs(sprites_t, exist_ok=True)
    sprites2x_t = os.path.join(sprites2x, theme)
    os.makedirs(sprites2x_t, exist_ok=True)
    for each in os.listdir(svg_dir):
        if not each.endswith(".svg"):
            continue
        s = temp_svg(css, each, theme, svg_dir, svg_t_dir)
        for d, dir in [(dpi, sprites_t), (dpi * 2, sprites2x_t)]:
            print("each: {}".format(each))
            name = each[:-4]
            if each in filter:
                png = os.path.join(dir, name + '.png')
                c = cmd.format(png, d, s).split()
                print(subprocess.check_output(c))
                if name in patterns.keys():
                    pattern_png(png, patterns[name])


def md5_sum(p: str):
    with open(p, 'rb') as fp:
        return hashlib.md5(fp.read()).hexdigest()


class SvgCheck:
    all = dict()
    new = set()
    svg = os.path.join(base_dir, "svg")
    svg_data = os.path.join(svg, 'svg.json')

    def __init__(self):
        if os.path.exists(self.svg_data):
            self.all = json.load(open(self.svg_data, "r"))

        for each in os.listdir(self.svg):
            if each.endswith('.svg'):
                svg_sum = md5_sum(os.path.join(self.svg, each))
                name = each[:-4]
                png = name + ".png"
                themes = ['day', 'dusk', 'night']
                dirs = (list(map(lambda ea: os.path.join(sprites, ea, png), themes)) +
                        list(map(lambda ea: os.path.join(sprites2x, ea, png), themes)))
                pngs_exists = functools.reduce(lambda acc, d: acc and os.path.exists(d), dirs, True)

                if each not in self.all.keys() or svg_sum != self.all[each] or not pngs_exists or name in patterns.keys():
                    self.new.add(each)
                self.all[each] = svg_sum

    def svg_check(self):
        print("new files: {}".format(self.new))

    def save(self):
        with open(self.svg_data, "w") as fp:
            fp.write(json.dumps(self.all, indent=4))


css_template = """svg {{
    background-color: {bgcol};
    color: {col};
}}
.layout {{display:none}}  /* used to control visibility of symbolBox, svgBox, pivotPoint (none or inline) */
.symbolBox {{stroke:black;stroke-width:0.32;}}  /* show the cover of the symbol graphics */
.svgBox {{stroke:blue;stroke-width:0.32;}}  /* show the entire SVG cover */
.pivotPoint {{stroke:red;stroke-width:0.64;}}  /* show the pivot/anchor point, 0,0 */
.sl {{stroke-linecap:round;stroke-linejoin:round}} /* default line style elements */
.f0 {{fill:none}}  /* no fill */
"""


def write_css(theme: str):
    with open(colors, "r") as fp:
        c: dict = json.load(fp)['library'][theme.upper()]
    with open(os.path.join(svg_dir, "{}SvgStyle.css".format(theme)), "w+") as fp:
        header = css_template.format(bgcol=c['NODTA'], col=c['CURSR'])
        fp.write(header)
        codes = list(c.keys())
        codes.sort()
        for ea in codes:
            lines = list()
            lines.append(".s{code} {{stroke:{col}}}\n".format(code=ea, col=c[ea]))
            lines.append(".f{code} {{fill:{col}}}\n".format(code=ea, col=c[ea]))
            fp.writelines(lines)
        fp.close()


if __name__ == '__main__':
    check = SvgCheck()
    parser = argparse.ArgumentParser(
        prog='create_sheet',
        description='Create sprite sheet optionally generating PNGs from SVGs')
    # parser.add_argument("-svg", "--svg", help="process SVGs into PNGs", default=False, type=bool)
    parser.add_argument("--svg", help="process SVGs into PNGs", action='store_true')
    parser.add_argument("--svg_unused", help="process unused SVGs into PNGs", action='store_true')
    parser.add_argument("--theme", help="CSS theme to apply to SVGs which can be (day dusk or night)")
    parser.add_argument("--dry", help="dry run", action='store_true')
    args = parser.parse_args()

    if args.dry:
        check.svg_check()
        sys.exit(0)

    themes = args.theme.split(",")
    for theme in themes:
        write_css(theme)

        if args.svg_unused:
            svg_unused_to_png(theme)
            sys.exit(0)

        if args.svg:
            svg_to_png(check.new, theme)
            check.save()
            print('rendered SVGs to PNGs')

        save_sprite_sheet(False, theme)
        save_sprite_sheet(True, theme)
