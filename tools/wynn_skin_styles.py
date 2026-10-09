import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import wynn_models as wm

RESOURCE = wm.ROOT.parent / "src/client/resources/assets/wynnoverhaul/animations/skin_styles.json"

SPEAR = {
    "SPEAR:TWIN": ["sinister_twinblade"],
    "SPEAR:AXE": ["galvanic_decollator"],
    "SPEAR:GREATAXE": [
        "aurora_battleaxe", "miasmic_axe", "mosaic_axe", "evergreen_axe",
        "paladin_greatsword", "silver_zweihander", "shark_greatsword", "crimson_greatsword", "blueshift_greatsword", "tisaun_greatsword",
    ],
    "SPEAR:HAMMER": [
        "geomancer_hammer", "halfmoon_hammer", "highlands_gavel", "mora_hammer", "phoenix_hammer", "scrap_hammer",
        "bluerose_morning_star", "icicle_mace", "firefly_club",
    ],
    "SPEAR:GLAIVE": ["bamboo_naginata", "tasim_glaive", "fallen_glaive", "battle_monk_guandao"],
    "SPEAR:SCYTHE": ["ancient_halberd", "viridian_halberd", "azure_ranseur", "dullahan_guisarme", "eclipsed_scythe", "monarch_partisan", "dune_corseque"],
    "SPEAR:SWEEP": ["pirate_oar", "fairytale_broom", "brawler_rake", "yuman_guitar"],
}
DAGGER = {
    "DAGGER:STAB": [
        "viridian_rapier", "trickster_main_gauche", "sinister_barb", "serpent_tooth", "silver_dagger", "icicle_shank", "rurie_kunai",
        "tropical_dagger", "highlands_claw", "nightshine_claw", "tesla_claw", "geomancer_fist", "shark_pata", "galvanic_mangler", "ancient_palm",
    ],
    "DAGGER:WHIRL": [
        "azure_nunchucks", "baron_rope_dart", "monarch_fan", "harvest_sickle", "shadestepper_scimitar", "dune_khopesh", "pirate_hook", "rurie_kunai_multi",
    ],
}
WAND = {
    "WAND:STAFF": [
        "ancient_staff", "abyssal_staff", "aurora_staff", "azure_staff", "baron_stave", "lari_stave", "evergreen_branch", "brawler_broom", "icicle_cane",
        "disciple_staff", "eclipsed_staff", "fellowship_staff", "fury_staff", "gothic_staff", "halfmoon_staff", "highlands_staff", "nightshine_staff",
        "nordic_staff", "phoenix_staff", "riftwalker_staff", "shark_staff", "silver_staff", "stargazer_scepter", "tesla_staff",
        "cruor_sceptre", "geomancer_sceptre", "light_bearer_sceptre", "viridian_scepter",
    ],
    "WAND:ORBIT": ["arcanist_tome", "monarch_scroll", "pirate_atlas", "galvanic_chapterhouse", "sinister_edict", "dune_fanous"],
}
RELIK = {
    "RELIK:RITE": [
        "ritualist_focus", "cruor_sigil", "miasmic_censer", "thief_idol", "voodoo_doll", "summoner_marionette", "phoenix_emblem",
        "essentia_spirit", "dune_vajra", "monarch_knell", "jade_teapot",
    ],
}
BOW = {
    "BOW:FIREARM": [
        "baron_chukonu", "boltslinger_repeater", "brawler_blunderbuss", "dune_musket", "galvanic_breechloader", "scrap_pistol",
        "sharpshooter_rifle", "yulie_blaster", "pirate_cannon", "legendary_arm_cannon", "jade_blowpipe", "nightshine_grappler", "vanguard_rover",
        "crimson_crossbow", "fairytale_crossbow", "firefly_crossbow", "nordic_crossbow", "trapper_crescent",
    ],
}
FAMILIES = {"spear": SPEAR, "dagger": DAGGER, "wand": WAND, "relik": RELIK, "bow": BOW}


def style_of(family, skin):
    for style, names in FAMILIES[family].items():
        if skin in names:
            return style
    return None


def cmd_models(node, out):
    if isinstance(node, dict):
        if node.get("type") == "minecraft:range_dispatch" and node.get("property") == "minecraft:custom_model_data":
            for entry in node.get("entries", []):
                model = (entry.get("model") or {}).get("model")
                if isinstance(model, str):
                    out[float(entry["threshold"])] = model
        for value in node.values():
            cmd_models(value, out)
    elif isinstance(node, list):
        for value in node:
            cmd_models(value, out)


def build(pack):
    table = {}
    for rel, raw in wm.entries(pack, "assets/minecraft/items/", ()):
        item = "minecraft:" + rel[:-5]
        models = {}
        cmd_models(json.loads(raw), models)
        for threshold, model in sorted(models.items()):
            parts = model.split("/")
            if len(parts) >= 5 and parts[:3] == ["item", "wynn", "skin"] and parts[3] in FAMILIES:
                style = style_of(parts[3], parts[4])
                if style:
                    table.setdefault(item, {})[str(int(threshold))] = style
    return table


def main():
    table = build(wm.find_pack())
    RESOURCE.write_text(json.dumps(table, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    print({item: len(styles) for item, styles in table.items()}, "->", RESOURCE)
    listed = {name for fam in FAMILIES.values() for names in fam.values() for name in names}
    print(len(listed), "skins named in the style lists")


if __name__ == "__main__":
    main()
