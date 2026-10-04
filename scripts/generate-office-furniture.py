#!/usr/bin/env python3
"""Generate original cuboid furniture models using Minecraft's own block textures."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
ASSETS = ROOT / "assets/modding_workspace"
DATA = ROOT / "data/modding_workspace"

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n")

def box(start, end, texture):
    return {"from": start, "to": end, "faces": {side: {"texture": "#" + texture} for side in ("north", "south", "east", "west", "up", "down")}}

textures = {"wood": "minecraft:block/spruce_planks", "edge": "minecraft:block/stripped_spruce_log", "metal": "minecraft:block/iron_block",
    "dark": "minecraft:block/black_concrete", "teal": "minecraft:block/cyan_wool", "paper": "minecraft:block/white_concrete",
    "cork": "minecraft:block/stripped_oak_log", "copper": "minecraft:block/copper_block", "yellow": "minecraft:block/yellow_concrete",
    "green": "minecraft:block/lime_concrete", "particle": "minecraft:block/spruce_planks"}
models = {}
models["agent_desk"] = [box([0,13,0],[16,16,16],"edge"), box([1,0,2],[4,13,14],"wood"), box([12,0,2],[15,13,14],"wood"),
    box([4,6,2],[12,12,14],"wood"), box([4.5,6.5,1.5],[11.5,11.5,2],"edge"), box([6,8.5,1],[10,9.5,1.5],"metal")]
models["office_chair"] = [box([2,6,2],[14,8,14],"teal"), box([2,11,12],[14,22,15],"teal"), box([3,7,12],[5,16,14],"metal"),
    box([11,7,12],[13,16,14],"metal"), box([7,1,7],[9,6,9],"metal"), box([2,0,7],[14,2,9],"dark"), box([7,0,2],[9,2,14],"dark")]
models["project_board"] = [box([0,6,7],[16,24,10],"copper"), box([1,7,6.8],[15,23,7],"cork"), box([6,1,7],[10,6,10],"edge"), box([2,0,4],[14,2,13],"edge")]
for x in (2,9):
    for y in (9,15,20):
        models["project_board"].append(box([x,y,6.5],[x+5,y+2,6.8],"paper" if y==20 else "yellow" if x==2 else "green"))
models["monitor"] = [box([1,3,9],[15,12,10],"dark"), box([1,3,7.8],[2,12,9],"metal"), box([14,3,7.8],[15,12,9],"metal"),
    box([2,3,7.8],[14,4,9],"metal"), box([2,11,7.8],[14,12,9],"metal"), box([7,1,8],[9,4,10],"dark"), box([4,0,5],[12,1,12],"dark"),
    box([12.5,3.2,7.6],[13.3,3.8,7.8],"green")]
models["filing_cabinet"] = [box([1,0,1],[15,16,15],"wood")]
for y in (1,6,11):
    models["filing_cabinet"] += [box([2,y,.8],[14,y+4,1],"edge"), box([3,y+1,.6],[6,y+2.5,.8],"paper"), box([8,y+1.5,.2],[12,y+2.5,.8],"metal")]
recipes = {
    "agent_desk": (["PPP"," I ","P P"], {"P":"#minecraft:planks","I":"minecraft:iron_ingot"}),
    "office_chair": ([" W ","WWW"," I "], {"W":"#minecraft:wool","I":"minecraft:iron_ingot"}),
    "project_board": (["CCC","PPP"," S "], {"C":"minecraft:copper_ingot","P":"minecraft:paper","S":"minecraft:stick"}),
    "monitor": (["III","GRG"," I "], {"I":"minecraft:iron_ingot","G":"minecraft:glass_pane","R":"minecraft:redstone"}),
    "filing_cabinet": (["PPP","ICI","PPP"], {"P":"#minecraft:planks","I":"minecraft:iron_ingot","C":"minecraft:chest"})}
styles = {
    "classic": ("", {}, None),
    "birch": ("Birch ", {"wood":"birch_planks", "edge":"stripped_birch_log", "metal":"quartz_block_top", "dark":"light_gray_concrete", "teal":"blue_wool", "copper":"birch_planks", "particle":"birch_planks"}, "birch_planks"),
    "dark_oak": ("Dark Oak ", {"wood":"dark_oak_planks", "edge":"stripped_dark_oak_log", "metal":"gold_block", "teal":"red_wool", "copper":"gold_block", "particle":"dark_oak_planks"}, "dark_oak_planks"),
    "industrial": ("Industrial ", {"wood":"iron_block", "edge":"gray_concrete", "metal":"polished_andesite", "teal":"gray_wool", "dark":"black_concrete", "copper":"iron_block", "cork":"gray_concrete", "particle":"iron_block"}, "iron_ingot"),
}
labels = dict(zip(models, ["Agent Desk", "Office Chair", "Project Board", "Monitor", "Filing Cabinet"]))
lang_path = ASSETS / "lang/en_us.json"
lang = json.loads(lang_path.read_text())
for style, (prefix, palette, material) in styles.items():
    for base, elements in models.items():
        name = base if style == "classic" else style+"_"+base
        ident = "modding_workspace:"+name
        colors = textures | {key:"minecraft:block/"+value for key,value in palette.items()}
        write(ASSETS / "models/block" / (name+".json"), {"parent":"minecraft:block/block", "textures":colors, "elements":elements,
            "display":{"gui":{"rotation":[30,225,0],"translation":[0,-1,0],"scale":[.6,.6,.6]}}})
        write(ASSETS / "blockstates" / (name+".json"), {"variants":{"facing="+f:{"model":"modding_workspace:block/"+name,"y":y} for f,y in (("north",0),("east",90),("south",180),("west",270))}})
        write(ASSETS / "items" / (name+".json"), {"model":{"type":"minecraft:model","model":"modding_workspace:block/"+name}})
        write(DATA / "loot_table/blocks" / (name+".json"), {"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":ident}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]})
        if style == "classic":
            pattern, key = recipes[base]
            recipe = {"type":"minecraft:crafting_shaped","category":"misc","group":"agent_office","pattern":pattern,"key":key,"result":{"id":ident}}
        else:
            recipe = {"type":"minecraft:crafting_shapeless","category":"misc","group":"agent_office_finishes","ingredients":["modding_workspace:"+base,"minecraft:"+material],"result":{"id":ident}}
            write(DATA / "recipe" / (name+"_to_classic.json"), {"type":"minecraft:crafting_shapeless","category":"misc","ingredients":[ident],"result":{"id":"modding_workspace:"+base}})
        write(DATA / "recipe" / (name+".json"), recipe)
        lang["block.modding_workspace."+name] = prefix+labels[base]
write(lang_path, lang)
