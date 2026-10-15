# Animation YAML

Weapon, spell, tool and body-state animations are loaded from YAML. The mod ships its defaults inside the jar (`assets/wynnoverhaul/animations/*.yml`) and then reads every `*.yml` in `config/wynnoverhaul/animations/`. Config files load in filename order and replace entries with the same key, so an override only needs the entries it changes. New keys add new animations.

Edits apply without restarting: the folder is checked once a second and reloaded when a file changes. `/owanim reload` forces a reload and `/owanim folder` prints the folder path. A file with a syntax error is skipped and an invalid entry is skipped on its own, each reported in chat and the log, so one mistake never removes the rest.

## Files

Any file may contain any section. The bundled files are split by weapon family (`spear`, `dagger`, `wand`, `relik`, `bow`, `tool`, `spell`) plus `shared`, `locomotion`, and the generated `attack_moves`, `spell_moves` and `gait`.

## poses

A pose is one swing, spell cast or tool stroke, keyed by name. The part before the first colon is its family (`SPEAR:SWEEP` belongs to `SPEAR`).

| Field | Meaning |
| --- | --- |
| `label` | Name shown in the weapon animation menu |
| `seconds` | Length at full speed. Faster attack rates shorten it |
| `hit` | Fraction of the swing where the strike lands (0 to 1, exclusive) |
| `recover` | Fraction where the recovery starts, between `hit` and 1. While you move, the pose fades back to the walk or sprint stance from here. Defaults to 35% of the way from `hit` to the end |
| `blend` | Seconds to ease in from the previous pose. Default 0.09 |
| `gripOffset` | Distance along the shaft where the off hand grips |
| `holdEnd` | Keep the final frame until the swing relaxes |
| `spin` | Treat head yaw and twist as absolute angles for full turns |
| `handShift`, `handSprintExtra` | Moves the grip hand along the shaft |
| `sounds` | List of `{at, sound, pitch, volume}`, `at` being a fraction of the swing |
| `channels` | Curves, below |

### channels

Each channel is a list of `[time, value]` points. Time runs from 0 to 1 and must increase. A `null` value means "keep whatever the stance has", so `[0, null]` and `[1, null]` at the ends blend in and out of the stance. Values between points are interpolated smoothly.

| Channel | Controls |
| --- | --- |
| `pitch`, `across`, `abduct` | Main arm angles |
| `offPitch`, `offAcross`, `offAbduct` | Off arm angles |
| `turn` | Torso yaw |
| `reach` | Hand push forward and back |
| `headA`, `headY` | Weapon tip pitch and yaw |
| `twist` | Weapon roll |
| `grip` | 0 one handed, 1 two handed |
| `lean` | Torso lean about the hips |
| `bodyY`, `bodyR` | Torso twist and roll on top of `turn` |
| `hipY` | Pelvis yaw. The hips and the feet point along it, so the legs follow a twist instead of staying square to the camera |
| `rootF`, `rootS`, `rootH` | Move the whole body forward, to the left and down, in pixels |
| `footLF`, `footLS`, `footLU` | Left foot target: forward, to the left and up off the ground, in pixels from the standing stance |
| `footRF`, `footRS`, `footRU` | The same for the right foot |

The legs are solved, not posed. Each foot target is a point relative to the standing stance, and a two-bone solver bends the thigh and shin so the foot reaches it. The hips are lowered as far as the stance needs, so a wide stance crouches and a foot with no lift is always on the ground. A foot lift raises it; the body does not float. While you move, the walk takes the legs back from the pose.

## stances

How the weapon is held when not swinging, keyed by family or pose key. Each of `carry`, `ready`, `idle`, `sprint` and the optional `walk` is a map of channel values. Missing channels use the rest value. `gait` sets walk and sprint arm swing amounts, and `gripOffset` the off hand position.

## movesets

A moveset is a complete fighting style for a weapon: the attack chain, the stance it is held in and the name shown in the weapon animation menu. `light` is the list of strokes played in order for consecutive swings. The chain restarts from the first stroke after a pause in attacking.

```yaml
movesets:
  "SPEAR":
    label: "Spear: Lancer"
    light:
      - {pose: "SPEAR"}
      - {pose: "SPEAR:JAB"}
      - {pose: "SPEAR:RISING"}
      - {pose: "SPEAR:SKEWER", finisher: true}
```

| Field | Meaning |
| --- | --- |
| `label` | Name shown in the weapon animation menu |
| `stance` | Stance to hold the weapon in. Defaults to the stance with the moveset's own key, then the family stance |
| `opener` | A `pose` (and optional `mirror`) played instead of the first stroke when a chain starts from idle, so the first swing can draw the weapon out of its carried pose. When the weapon is already in its ready stance the first stroke plays as normal |
| `light` | The strokes, each with a `pose`, optional `mirror` and optional `finisher` |
| `sprint` | A `pose` (and optional `mirror`) played instead of the next chain stroke when a swing starts while sprinting forward. It plays as a finisher and leaves the chain where it was. Strafing, walking and backing up play the normal strokes |
| `cast` | How spell casts look with this moveset, below |
| `spells` | Maps a spell name (lowercase letters only) to a pose, to give this moveset its own animation for that spell |

`cast` reshapes the shared spell pose for the spell being cast, so each moveset casts in its own manner without a pose per spell. All fields are optional: `seconds` multiplies the length, `arc` the arm and weapon tip channels, `body` the lean, step, turn, leg and hip channels, `reach` the hand push, `raise` lifts the arms (negative lowers them), `turn` adds a swing of the torso, `mirror` plays the cast in the opposite direction, `grip` forces one handed (0) or two handed (1), `hit` shifts when the strike lands, `blend` multiplies the ease in, `twirl` adds an oscillation to a channel (`{channel, amplitude, cycles}`), `sounds` replaces the spell's sounds and `addSounds` adds sounds on top. A pose named in `spells` is used as written and ignores `cast`.

`movesetSpells` is a top-level section that does the same as `spells` for several movesets at once: `movesetSpells: {"SPEAR:HAMMER": {bash: "SPELL:BASH@SPEAR:HAMMER"}}`. The bundled `spell_moves.yml` uses it to give each moveset its own cast for most spells.

A spell without an entry in `spell_moves.yml` falls back to the shared pose reshaped by `cast`.

`mirror` plays the stroke in the opposite direction, so an alternating chain reads as forehand and backhand. `finisher` adds the heavy hit sound. Every pose named must exist. The moveset key is what a weapon is assigned in the menu, so it does not need a pose of the same name. The menu lists movesets, plus any pose that no moveset uses.

## trails

`trails` maps a pose or family to the `[from, to]` span of the weapon blade the afterimage follows.

## shared.yml

- `lungeFamilies`: families whose strokes step the body forward
- `cues`: the `finisher`, `thump`, `tail` and `slam` impact sounds
- `impact`: hit sounds per family
- `spellNames`: maps a spell name (lowercase letters only) to its `SPELL:` pose

## Style and weapon swings

The locomotion style also shapes weapon strokes and spell casts, through four parameters in `styleDefaults` and each style:

| Parameter | Meaning |
| --- | --- |
| `swingSpeed` | Multiplies a stroke's `seconds`. Above 1 is slower |
| `swingBody` | Multiplies the lean, step, torso turn, leg and hip channels |
| `swingArc` | Multiplies the arm, reach and weapon tip channels |
| `swingBlend` | Multiplies the time a stroke takes to ease in from the previous pose |

Values are limited to 0.4 to 2. A heavy style makes the same moveset drive the whole body harder and ease in slower, a light one makes it quick and compact.

## gait

`gait` gives the walking and sprinting foot cycle its `duty` (the share of each step a foot is on the ground, never below 0.5) and `lift` (how high a foot rises in pixels). The feet are planted: each one slides back at exactly the speed the body moves, in whatever direction it moves, so walking sideways and backwards turn the same cycle toward that direction. The style's `walkLeg` and `sprintLeg` set how long the stride is. The body dips as far as the stride needs, and the `rig.drop` and `rig.lift` targets below add the style's bob.

## Body states (locomotion.yml)

Walking, jumping, crouching, swimming, climbing, riding, gliding, eating, blocking, hurt and death are defined as additive layers on top of the vanilla pose. They run only when the matching Locomotion setting is on.

### styleDefaults and styles

`styleDefaults` lists every style parameter with its default value. A style (`HEROIC`, `STEALTH`, `LIGHT`, `HEAVY`, `WEARY`) sets a `label` and any parameters that differ. Adding a parameter to `styleDefaults` makes it available as `style.<name>` in every expression, and adding a style makes it selectable in the settings.

### states

States run in list order. Each has:

| Field | Meaning |
| --- | --- |
| `name` | Unique name. A config file entry with the same name replaces the bundled one, a new name is appended |
| `toggle` | Locomotion setting that must be on: `locomotionWalk`, `locomotionJump`, `locomotionCrouch`, `locomotionSwim`, `locomotionClimb`, `locomotionRide`, `locomotionElytra`, `locomotionUseItem`, `locomotionHurt`, `locomotionDeath` |
| `gate` | A signal name. The state is skipped while that signal is 0.001 or lower |
| `vars` | Named values calculated in order and usable by later vars and effects |
| `effects` | What the state changes |

An effect names a `to` target and one operation:

```yaml
- {to: rightArm.x, add: "sin(beat + pi) * 0.9 * style.swimArm * swim * k", freeArms: true}
- {to: leftLeg.z, sub: "0.08 * wFly * k"}
- {to: rightArm.x, approach: "-1.9 + sin(ph) * style.climbArm", rate: "wClimb * min(k, 1)", freeArms: true}
```

`add` adds the value, `sub` subtracts it, and `approach` moves the current value toward a goal by `rate` (0 to 1). `freeArms: true` applies the effect only when no weapon animation owns the arms.

Targets are `head`, `rightArm`, `leftArm`, `rightLeg` or `leftLeg` with the axis `x`, `y`, `z` (rotations in radians) or `posY` (offset in model units), `torso` with `pitch`, `roll`, `yaw` or `shift`, and `rig` with `drop` (how far the body dips while the feet are planted), `lift` (a shift that only applies when no foot cycle or rig pose is driving the legs), `rootF` (the body shifted forward in pixels) or the foot targets `footLF`, `footLS`, `footLU`, `footRF`, `footRS` and `footRU` (left and right foot forward, to the left and up, in pixels from the standing stance). Foot and `rootF` targets add on top of the foot cycle, and the legs are solved from them with the knees bent and the hips lowered to keep the feet reachable. The jump, launch, land, crouch and climb states pose the legs this way.

### Expressions

Numbers, `+ - * /`, parentheses, `pi`, signals, vars, `style.<name>` and the functions `sin`, `cos`, `abs`, `sqrt`, `min`, `max`, `pow(x, y)`, `clamp(x, lo, hi)` and `lerp(a, b, t)`. Dividing by zero gives 0.

### Signals

| Signal | Meaning |
| --- | --- |
| `k` | Overall strength of the layer |
| `wAir`, `wRise`, `wFall`, `wLand` | Airborne, rising, falling, just landed (0 to 1) |
| `wMove`, `wSprint`, `wCrouch` | Moving, sprinting, crouching |
| `wSwim`, `wTread`, `wCrawl` | Swimming, treading water, crawling |
| `wClimb`, `climbMove` | Climbing, and how fast |
| `wRide`, `wFly`, `wUse`, `wBlock`, `wHurt`, `wDead` | Riding, gliding, eating or drinking, blocking, hurt, dying |
| `pace`, `sp`, `boost`, `vanillaSp` | Movement speed measures |
| `phase`, `gait`, `gaitOpp` | Walk cycle phase and its cosine on each leg |
| `age` | Animation time in ticks |
| `grounded` | 1 on the ground, lower when airborne, swimming, climbing, gliding or riding |
| `turnRate` | Turning speed |
| `moveFwd`, `moveSide` | Direction of travel relative to the body, from -1 to 1, with forward and left positive |
| `launchAge`, `leadRight`, `launch` | Time since jumping, which leg leads, jump push-off strength |

## First person body

The `firstPerson` section of shared.yml controls the first-person body. `hidden` lists the poses where the body is not drawn and vanilla hands show instead (`standing`, `crouching`, `riding`, `swimming`, `crawling`, `gliding`, `spinAttack`, `sleeping`). `shift` sets, per pose, how far the body is pushed ahead of the camera in blocks.
