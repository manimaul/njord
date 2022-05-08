#!/usr/bin/env python3
import json

import math
import os
import os.path
import subprocess as sp

from PIL import Image
from marshmallow_dataclass import dataclass

import shutil

base_dir = os.path.dirname(os.path.realpath(__file__))
sprites = os.path.join(base_dir, "sprites")
sprite_sheet_dir = os.path.join(base_dir, "../chart_server/src/main/resources/www/sprites")
shutil.rmtree(sprites, ignore_errors=True)
os.makedirs(sprites)

color_1 = "#ffffff"
color_2 = "#808080"

red = "#ff0000"
green = "#00ff00"
yellow = "#ffff00"
white = "#ffffff"
blue = "#0000ff"
orange = "#ff8000"
violet = "#c545c3"


@dataclass
class Icon:
    variant: str
    color_1: str = None
    color_2: str = None

    def file_name(self, name: str):
        if self.variant is None:
            return os.path.join(sprites, "{}.png".format(name))
        else:
            return os.path.join(sprites, "{}{}.png".format(name, self.variant))

    def svg_to_png(self, name: str):
        with open(os.path.join(base_dir, "{}.svg".format(name))) as svg:
            svg_str = svg.read()
            if self.color_1 is not None:
                svg_str = svg_str.replace(color_1, self.color_1)
            else:
                svg_str = svg_str.replace(
                    "fill:{};fill-opacity:1;".format(color_1),
                    "fill:{};fill-opacity:0;".format(col_black)
                )
            if self.color_2 is not None:
                svg_str = svg_str.replace(color_2, self.color_2)
            else:
                svg_str = svg_str.replace(
                    "fill:{};fill-opacity:1;".format(color_2),
                    "fill:{};fill-opacity:0;".format(col_black)
                )
            tmp = os.path.join(sprites, "temp.svg".format(name))
            if os.path.exists(tmp):
                os.remove(tmp)
            with open(tmp, "w") as svg_temp:
                svg_temp.write(svg_str)
            png_name = self.file_name(name)
            print(
                sp.getoutput('inkscape --without-gui --export-type=png --export-filename={} {}'.format(png_name, tmp)))


@dataclass
class Theme:
    name: str
    icons: dict[str, list[Icon]]

    def save_sprite_sheet(self):
        print("rendering theme: {}".format(theme.name))
        for icon in self.icons:
            for each in self.icons[icon]:
                each.svg_to_png(name=icon)

        frames = []

        files = os.listdir(sprites)
        files.sort()

        tile_width = 0
        tile_height = 0

        for current_file in files:
            try:
                with Image.open(os.path.join(sprites, current_file)) as im:
                    name = current_file[:-4]
                    image = im.getdata()
                    frames.append((name, image))
                    tile_width = max(tile_width, image.size[0])
                    tile_height = max(tile_height, image.size[1])
            except:
                print(current_file + " is not a valid image")

        max_frames_row = math.ceil(math.sqrt(len(files)))

        if len(frames) > max_frames_row:
            spritesheet_width = tile_width * max_frames_row
            required_rows = math.ceil(len(frames) / max_frames_row)
            spritesheet_height = tile_height * required_rows
        else:
            spritesheet_width = tile_width * len(frames)
            spritesheet_height = tile_height

        spritesheet = Image.new("RGBA", (int(spritesheet_width), int(spritesheet_height)))

        sprite_json = dict()

        for i, (name, image) in enumerate(frames):
            top = tile_height * math.floor(i / max_frames_row)
            left = tile_width * (i % max_frames_row)
            width, height = image.size
            bottom = top + height
            right = left + width

            box = (left, top, right, bottom)
            # box = [int(i) for i in box]

            spritesheet.paste(image, box)
            sprite_json[name] = {
                "width": width,
                "height": height,
                "x": int(left),
                "y": int(top),
                "pixelRatio": 1
            }

        with open(os.path.join(sprite_sheet_dir, "{}_sprites.json".format(self.name)), "w") as fp:
            json.dump(sprite_json, fp, indent=4)

        spritesheet.save(os.path.join(sprite_sheet_dir, "{}_sprites.png".format(self.name)), "PNG")


col_white = "01"
col_black = "02"
col_red = "03"
col_green = "04"
col_blue = "05"
col_yellow = "06"
col_grey = "07"
col_brown = "08"
col_amber = "09"
col_violet = "10"
col_orange = "11"
col_magenta = "12"
col_pink = "13"

if __name__ == '__main__':
    theme = Theme(
        name="day",
        icons={
            "light": [
                Icon(variant=col_red, color_1=red),
                Icon(variant=col_green, color_1=green),
                Icon(variant=col_yellow, color_1=yellow),
                Icon(variant=col_magenta, color_1=violet),
                Icon(variant=col_white, color_1=white),
                Icon(variant=col_blue, color_1=blue),
                Icon(variant=col_orange, color_1=orange),
            ],
            "BOYCAN": [
                Icon(variant=None),
                Icon(variant=col_red, color_1=red, color_2=red),
                Icon(variant=col_green, color_1=green, color_2=green),
                Icon(variant=col_yellow, color_1=yellow, color_2=yellow),
                Icon(variant="{}_{}".format(col_red, col_green), color_1=red, color_2=green),
                Icon(variant="{}_{}".format(col_green, col_red), color_1=green, color_2=red),
            ],
            "BOYCON": [
                Icon(variant=None),
                Icon(variant=col_red, color_1=red, color_2=red),
                Icon(variant=col_green, color_1=green, color_2=green),
                Icon(variant=col_yellow, color_1=yellow, color_2=yellow),
                Icon(variant="{}_{}".format(col_red, col_green), color_1=red, color_2=green),
                Icon(variant="{}_{}".format(col_green, col_red), color_1=green, color_2=red),
            ],
            "pillar": [
                Icon(variant=col_red, color_1=red, color_2=red),
                Icon(variant=col_green, color_1=green, color_2=green),
                Icon(variant=col_yellow, color_1=yellow, color_2=yellow),
                Icon(variant="{}_{}".format(col_red, col_green), color_1=red, color_2=green),
                Icon(variant="{}_{}".format(col_green, col_red), color_1=green, color_2=red),
            ],
            "light_float": [Icon(variant=None)],
            "mooring": [Icon(variant=None)],
            "super_buoy": [Icon(variant=None)],
            "sound": [Icon(variant=col_magenta, color_1=violet)],
            "point": [Icon(variant=None)],
        }
    )

    theme.save_sprite_sheet()
