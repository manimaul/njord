#!/usr/bin/env python3
import json

import math
import os
import os.path

from PIL import Image

base_dir = os.path.dirname(os.path.realpath(__file__))
sprites = os.path.join(base_dir, "simplified")
sprite_sheet_dir = os.path.join(base_dir, "../chart_server/src/main/resources/www/sprites")


def find_tile_wh(frames):
    width = 0
    height = 0
    for each in frames:
        cell = each[1]
        width = max(width, cell.size[0])
        height = max(width, cell.size[1])

    return width, height


def save_sprite_sheet():
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

    tile_width, tile_height = find_tile_wh(frames)

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
        width, height = current_frame.size

        spritesheet.paste(cut_frame, box)
        sprite_json[name] = {
            "width": width,
            "height": height,
            "x": int(left),
            "y": int(top),
            "pixelRatio": 1
        }

    name = "simplified"
    with open(os.path.join(sprite_sheet_dir, "{}.json".format(name)), "w") as fp:
        json.dump(sprite_json, fp, indent=4)

    spritesheet.save(os.path.join(sprite_sheet_dir, "{}.png".format(name)), "PNG")


if __name__ == '__main__':
    save_sprite_sheet()
