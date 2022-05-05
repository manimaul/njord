#!/usr/bin/env python3
import json

import math
import os
import os.path

from PIL import Image
from cairosvg import svg2png
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
violet = "#c545c3"


@dataclass
class Icon:
    variant: str
    color_1: str = None
    color_2: str = None

    def svg_to_png(self, name: str):
        with open(os.path.join(base_dir, "{}.svg".format(name))) as svg:
            svg_str = svg.read()
            if self.color_1 is not None:
                svg_str = svg_str.replace(color_1, self.color_1)
            if self.color_2 is not None:
                svg_str = svg_str.replace(color_2, self.color_2)
            svg2png(
                bytestring=svg_str,
                write_to=os.path.join(sprites, "{}_{}.png".format(name, self.variant)),
                dpi=240
            )


@dataclass
class Theme:
    name: str
    icons: dict[str, list[Icon]]

    def save_sprite_sheet(self):
        print("rendering theme: {}".format(theme.name))
        for icon in self.icons:
            for each in self.icons[icon]:
                each.svg_to_png(name=icon)

        max_frames_row = 10.0
        frames = []

        files = os.listdir(sprites)
        files.sort()

        for current_file in files:
            try:
                with Image.open(os.path.join(sprites, current_file)) as im:
                    frames.append((current_file[:-4], im.getdata()))
            except:
                print(current_file + " is not a valid image")

        tile_width = frames[0][1].size[0]
        tile_height = frames[0][1].size[1]

        if len(frames) > max_frames_row:
            spritesheet_width = tile_width * max_frames_row
            required_rows = math.ceil(len(frames) / max_frames_row)
            spritesheet_height = tile_height * required_rows
        else:
            spritesheet_width = tile_width * len(frames)
            spritesheet_height = tile_height

        spritesheet = Image.new("RGBA", (int(spritesheet_width), int(spritesheet_height)))

        sprite_json = dict()

        for i, (name, current_frame) in enumerate(frames):
            top = tile_height * math.floor(i / max_frames_row)
            left = tile_width * (i % max_frames_row)
            bottom = top + tile_height
            right = left + tile_width

            box = (left, top, right, bottom)
            box = [int(i) for i in box]
            cut_frame = current_frame.crop((0, 0, tile_width, tile_height))

            spritesheet.paste(cut_frame, box)
            sprite_json[name] = {
                "width": tile_width,
                "height": tile_height,
                "x": int(left),
                "y": int(top),
                "pixelRatio": 1
            }

        with open(os.path.join(sprite_sheet_dir, "{}_sprites.json".format(self.name)), "w") as fp:
            json.dump(sprite_json, fp, indent=4)

        spritesheet.save(os.path.join(sprite_sheet_dir, "{}_sprites.png".format(self.name)), "PNG")


if __name__ == '__main__':
    theme = Theme(
        name="day",
        icons={
            "light": [
                Icon(variant="red", color_1=red),
                Icon(variant="green", color_1=green),
                Icon(variant="yellow", color_1=yellow),
                Icon(variant="violet", color_1=violet),
            ],
            "can": [
                Icon(variant="red", color_1=red, color_2=red),
                Icon(variant="green", color_1=green, color_2=green),
                Icon(variant="yellow", color_1=yellow, color_2=yellow),
                Icon(variant="red_green", color_1=red, color_2=green),
                Icon(variant="green_red", color_1=green, color_2=red),
            ],
            "conical": [
                Icon(variant="red", color_1=red, color_2=red),
                Icon(variant="green", color_1=green, color_2=green),
                Icon(variant="yellow", color_1=yellow, color_2=yellow),
                Icon(variant="red_green", color_1=red, color_2=green),
                Icon(variant="green_red", color_1=green, color_2=red),
            ],
            "pillar": [
                Icon(variant="red", color_1=red, color_2=red),
                Icon(variant="green", color_1=green, color_2=green),
                Icon(variant="yellow", color_1=yellow, color_2=yellow),
                Icon(variant="red_green", color_1=red, color_2=green),
                Icon(variant="green_red", color_1=green, color_2=red),
            ],
            "light_float": [Icon(variant="blk")],
            "mooring": [Icon(variant="blk")],
            "super_buoy": [Icon(variant="blk")],
            "sound": [Icon(variant="violet", color_1=violet)],
            "point": [Icon(variant="blk")],
        }
    )

    theme.save_sprite_sheet()
