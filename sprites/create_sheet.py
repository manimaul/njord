#!/usr/bin/env python3
import argparse
import json

import math
import os
import os.path
from dataclasses import dataclass
from typing import Any
import subprocess

from PIL import Image

base_dir = os.path.dirname(os.path.realpath(__file__))
sprites = os.path.join(base_dir, "simplified")
sprites2x = os.path.join(base_dir, "simplified2x")
sprite_sheet_dir = os.path.join(base_dir, "../chart_server/src/main/resources/www/sprites")


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


def save_sprite_sheet(retna: bool):
    max_frames_row = 10
    frames = []

    files = set(filter(lambda x: x.endswith('.png'), os.listdir(sprites)))
    files2x = set(filter(lambda x: x.endswith('.png'), os.listdir(sprites2x)))

    if retna:
        for each in files2x:
            if each not in files:
                raise Exception('missing 1x file: ' + each)

    for current_file in files:
        png_path = os.path.join(sprites, current_file)
        pixel_ratio = 1

        if retna and current_file in files2x:
            png_path = os.path.join(sprites2x, current_file)
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

    name = "simplified"
    if retna:
        name = name + "@2x"
    with open(os.path.join(sprite_sheet_dir, "{}.json".format(name)), "w") as fp:
        json.dump(sprite_json, fp, indent=4)

    spritesheet.save(os.path.join(sprite_sheet_dir, "{}.png".format(name)), "PNG")


def svg_to_png():

    dpi = 63
    cmd = """inkscape
    -o {} 
    --export-dpi={}
    --export-background-opacity=0 
    {}
    """
    svg = os.path.join(base_dir, "svg")

    for dpi, dir in [(dpi, sprites), (dpi*2, sprites2x)]:
        for each in os.listdir(svg):
            if each.endswith('.svg'):
                png = os.path.join(dir, each[:-3] + 'png')
                s = os.path.join(svg, each)
                c = cmd.format(png, dpi, s).split()
                print(subprocess.check_output(c))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(
                    prog='create_sheet',
                    description='Create sprite sheet optionally generating PNGs from SVGs')
    # parser.add_argument("-svg", "--svg", help="process SVGs into PNGs", default=False, type=bool)
    parser.add_argument("--svg", help="process SVGs into PNGs", action='store_true')
    args = parser.parse_args()
    if args.svg:
        svg_to_png()
        print('rendered SVGs to PNGs')

    save_sprite_sheet(False)
    save_sprite_sheet(True)
