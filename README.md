<div align="center">

<img src="https://cdn.modrinth.com/data/cached_images/e3f51ebffe86ec358a3c714d5dc42318ed71fe7b.jpeg" alt="CarianStyle" width="800">

# CarianStyle | 卡利亚式附魔

[![CurseForge Downloads](https://cf.way2muchnoise.eu/full_carianstyle_downloads.svg?badge_style=for_the_badge)](https://www.curseforge.com/minecraft/mc-mods/carianstyle)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/carianstyle?style=for-the-badge&logo=modrinth&label=Modrinth)](https://modrinth.com/mod/carianstyle)
[![MC Version](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![Forge](https://img.shields.io/badge/Forge-47.x-orange?style=for-the-badge)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)
[![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey?style=for-the-badge)](LICENSE)

[![GitHub](https://img.shields.io/badge/GitHub-Roinflam-181717?style=for-the-badge&logo=github)](https://github.com/Roinflam)
[![Bilibili](https://img.shields.io/badge/Bilibili-@Roinflam-00A1D6?style=for-the-badge&logo=bilibili)](https://space.bilibili.com/285030707/)

This is the **1.20.1** branch. The Minecraft 1.12.2 version is on the [`1.12.2` branch](https://github.com/Roinflam/CarianStyle/tree/1.12.2).

本分支是 **1.20.1** 版，Minecraft 1.12.2 版在 [`1.12.2` 分支](https://github.com/Roinflam/CarianStyle/tree/1.12.2)。

</div>

<details open>
<summary><h2>English</h2></summary>

**CarianStyle** turns the Ashes of War, sorceries, incantations, talismans and Remembrances of *Elden Ring* into **113 enchantments**. You get them the vanilla way: the enchanting table, the anvil and loot. Nothing new to learn, but every enchantment keeps the temper of the original: the flame school only shows its full strength while you are on fire, the stars and moons wake up at night, Remembrance enchantments are extremely rare and exclude one another, and great power usually comes with a price.

Most effects scale with a percentage of the damage dealt or of health, so they stay balanced whether you swing a vanilla diamond sword or a modded weapon that hits for thousands.

### Versions

| | 1.20.1 | 1.12.2 |
|---|---|---|
| Loader / Java | Forge 47.x, Java 17 | Forge 14.23.5.2855 or newer, Java 8 |
| Install on | Client and server | Client and server |
| Dependencies | None (Cloth Config API for the config screen is bundled) | None |
| Enchantment Codex (`K`) | Yes | No |
| Config files | `config/carianstyle-common.toml`, plus `config/carianstyle/enchantment_values.json` for every enchantment's numbers | `config/carianstyle.cfg` |

Both versions have the same set of enchantments. Everything on this page describes the 1.20.1 version; the 1.12.2 version gets fewer updates and some numbers and conditions differ.

### Enchantment types

Enchantments come in five types. Apart from General ones, enchantments of the same type are mutually exclusive, so a piece of gear can only carry one of each:

- **General (67):** most of the mod: talismans, flames, stars, the Golden Order and more.
- **Combat Arts (26):** Ashes of War, i.e. weapon skills such as Stamp (Sweep), Repeating Thrust, Parry and Unsheathe. One per weapon.
- **Remembrances (15):** based on the Remembrance bosses. All of them sit in the rarest tier and are the strongest.
- **Death (4):** fire when you take a lethal hit (Contagious Fury: a hit that would drop you to 30% HP or lower), such as Scarlet Aeonia and Greatblade Phalanx.
- **Laws (1):** Stellar Order.

Rarity decides the maximum level: Uncommon enchantments go up to V (26), Rare ones up to III (51), and Very Rare ones only have level I (36). 23 of them are treasure enchantments that never appear at the enchanting table, only in loot. Two are curses.

### Full enchantment list

The same texts you see in game. `[Level]` means the enchantment level.

<details>
<summary><b>General (67)</b></summary>

| Enchantment | Max | Applies to | Effect |
|---|---|---|---|
| **Carian Phalanx** | III | Bow | On an arrow hit, [Level] x 2% chance to summon 9 glintblades that home in on the target, each dealing [Level] x 5% magic damage. |
| **Carian Retaliation** | III | Shield | Taking magic or ranged damage while guarding summons 3 glintblades that home in on the attacker, each dealing [Level] x 20% magic damage. |
| **Lucidity** *(treasure)* | V | Armor | Debuffs you receive are one level higher, but their durations are reduced by [Level] x 15% (levels add up across armor pieces). |
| **Blessing of the Erdtree** *(treasure)* | III | Chestplate | Attacking or being attacked grants Blessing of the Erdtree [Level] for [Level] x 2.5s: max HP +[Level] x 15%, and restore [Level] x 4% of max HP whenever its remaining duration reaches a multiple of 3s (Level 1 lasts under 3s and never heals). |
| **Golden Vow** *(treasure)* | III | Armor | Attacking or being attacked grants Golden Vow [Level] for [Level] x 2.5s: damage dealt +[Level] x 15%, damage negation +[Level] x 10%. (Levels from multiple armor pieces add up; the buff from attacking is capped at level 5.) |
| **Protection of the Erdtree** *(treasure)* | III | Armor | Attacking or being attacked grants Protection of the Erdtree [Level] for [Level] x 2.5s: negation against non-creature damage +[Level] x 20%. (Levels from multiple armor pieces add up; the buff from attacking is capped at level 5.) |
| **Bestial Constitution** *(treasure)* | I | Chestplate | All buff and debuff levels are doubled, but their durations are reduced by 60% (instant effects are unaffected). |
| **Bestial Vitality** *(treasure)* | V | Chestplate | All buff and debuff durations +[Level] x 30%. |
| **Rejection** | III | Leggings | On being attacked by a creature, knock back every creature within ([Level] - 1) x 0.75 + 5 blocks with a force of 0.5 (up to 20). |
| **Blessed Dew** | V | Chestplate | While your hunger bar is full, restore [Level] x 0.2% of your max HP per second. |
| **Blue-Feathered Branchsword** | V | Sword | At 20% HP or below, gain [Level] x 10% negation against damage from creatures. |
| **Concealing Veil** | I | Armor | While sneaking, enter stealth: other players can't see you and no creature can track you. Attacking or taking damage prevents stealth for 3s. |
| **Daedicar's Woe** *(treasure, curse)* | I | Armor | Curse Damage taken x3. I-frames after being hit -50%. |
| **Dragoncrest Greatshield** | I | Armor | Each instance of physical damage taken adds a stack: armor and armor toughness +7.5%, movement speed -1%, lasting 30s (refreshed on each hit), up to 20 stacks. At full stacks, gain an additional 25% physical damage negation. |
| **Godskin Swaddling Cloth** | III | Sword | Every 4 fully charged hits restore ([Level] - 1) x 1% + 3% of your max HP. |
| **Gold Scarab** | V | Sword | Experience dropped by slain targets +[Level] x 30%. |
| **Green Turtle** | V | Armor | Healing received +[Level] x 7.5%, and each heal restores up to [Level] x 0.15 extra HP, scaling with the share of HP you've lost. Levels on multiple armor pieces add up. |
| **Longtail Cat** | III | Armor | Immune to fall damage below ([Level] - 1) x 25% + 50% of your max HP (levels on multiple armor pieces add up). |
| **Magic Scorpion Charm** | V | Chestplate | Magic damage +[Level] x 10%, and dealing magic damage restores [Level] x 1% of lost HP. Cost: physical damage taken +[Level] x 10%. |
| **Red-Feathered Branchsword** | V | Sword | At 20% HP or below, damage dealt +[Level] x 20%. |
| **Rigid Arrow** | V | Bow | Bow damage +[Level] x 80%. Cost: while any other creature is within 12 blocks, damage taken from creatures +[Level] x 80% and knockback taken +[Level] x 75%. |
| **Realm of Sorcery** *(treasure)* | I | Armor | Magic damage dealt by every creature of your kind within 6 blocks, including yourself, +50% (worn by a player, this means every nearby player). |
| **Thops's Barrier** *(treasure)* | I | Armor | No creature within 6 blocks, including yourself, can deal or take magic damage. |
| **Immutable Shield** | III | Shield | While guarding: damage that gets past the shield -[Level] x 10%. Each time the shield fully blocks an attack, restore [Level] x 1% of your max HP and clear all buffs and debuffs from the attacker. |
| **Law of Causality** | III | Armor | After taking damage from creatures 5 times, deal retaliatory damage equal to [Level] x 75% of that damage to every creature within [Level] x 3 blocks (at most 10). (1s cooldown; [Level] is the total across all worn armor.) |
| **Law of Regression** *(treasure)* | III | Armor | Frequently (about once per second) clears all buffs and debuffs from every creature within [Level] x 3 blocks (at most 8), including yourself. ([Level] is the total across all worn armor.) |
| **Sacred Order** | I | Armor | You cannot be healed. When you enter a world (logging in, changing dimensions, etc.) without a shield, gain a damage-absorbing shield worth 100% of your max HP. Killing a creature grants a damage-absorbing shield worth 10% of your max HP (capped at 300% of max HP). While you have a shield, damage dealt +50%; hits that leave your shield standing reflect magic damage equal to 5% of the remaining shield. |
| **Crucible Feather** *(treasure)* | III | Armor | Damage taken x([Level] x 50% + 100%) (each piece applies separately, so the multipliers stack). I-frames after being hit +[Level] x 150%, and gain Speed [Level] and Jump Boost [Level] for [Level] x 2s. |
| **Crucible Knot** | III | Chestplate | Damage taken from attackers standing higher than you -[Level] x 25%. |
| **Crucible Scale** | III | Armor | When a single hit deals at least your max HP, that damage is reduced by [Level] x 15% (levels from multiple armor pieces add up, up to -90%). |
| **Flame, Devour Them All** | III | Sword | While ablaze: fully charged attacks deal fire damage equal to [Level] x 15% of the damage to up to 16 other creatures within [Level] blocks of the target (sparing other players, teammates and your pets), setting them ablaze for 10s. |
| **Flame, Grant Me Strength** | V | Sword | Attacks set you ablaze for 10s. While ablaze: damage dealt +[Level] x 7.5%, and gain [Level] x 3.75% negation against non-magic damage. |
| **Flame, Heal Me** | V | Armor | While ablaze: on being attacked, [Level] x 2.5% chance to remove one debuff (levels on all armor pieces add up). Each removal grants a permanent damage-absorbing shield worth 10% of your max HP. |
| **Flame, Protect Me** | V | Armor | While ablaze: gain [Level] x 2% damage negation and restore [Level] x 0.1% max HP per second (each armor piece applies separately). |
| **Starcaller Cry** | III | Bow | Where the arrow lands, creatures within [Level] x 2 blocks are pulled toward the center; 1s later lightning strikes every creature within [Level] blocks, dealing [Level] x 30% of the arrow's base damage (x3 at night). |
| **Dung Eater** | V | Sword | After dealing damage with a fully charged melee hit: you are nauseated for [Level] x 1.5s; the target is nauseated for [Level] x 4s and defiled for [Level] x 4s, reducing healing received by 75%. |
| **Honed Bolt** | III | Armor | When hit by a ranged attack, call down [Level] lightning bolts on the attacker (levels add up across armor pieces), each dealing 30% of the damage taken (x2 in rain, x4 in thunderstorms). |
| **Vyke's Dragonbolt** | III | Sword | Lightning damage negation +[Level] x 15%. After dealing damage with a fully charged melee attack, [Level] x 5% chance to call down lightning (chance x2 in rain, guaranteed in thunderstorms) dealing [Level] x 50% damage (x2 in rain, x4 in thunderstorms). |
| **Ekzykes's Decay** | III | Bow | Where the arrow lands, every creature within [Level] x 2 blocks - including yourself - receives Scarlet Rot [Level] for [Level] x 5s: 3% of current HP plus 0.075% of max HP as magic damage per second (ignores armor, +33% per level), healing received -25%, armor -[Level] x 50%. |
| **Scarlet Rot** | III | Sword | Fully charged attacks inflict Scarlet Rot 1 for [Level] x 20s: 3% of current HP plus 0.075% of max HP as magic damage per second (ignores armor, +33% per level), healing received -25%, armor -50%. |
| **Hoarfrost Stomp** | III | Armor | Triggers when a single hit from a creature deals at least 25% of your max HP (levels on all armor pieces add up): launch up to 20 grounded creatures within 3 + ([Level] - 1) x 2 blocks (max 10) with a force of [Level] x 0.35 and inflict Frostbite [Level] for [Level] x 5s: movement speed -[Level] x 7.5%, and [Level] x 0.25% of max HP as physical damage every 0.5s. |
| **Frenzied Burst** | III | Bow | Arrow hits burn you for 10% of your max HP over 3s, and burn the target for [Level] x 0.2 times that self-inflicted damage. While burning, healing received -90%. |
| **Howl of Shabriri** | III | Sword | Fully charged attacks burn you for 5% of your max HP over 3s (your healing received -90% meanwhile) and inflict Madness on the target for [Level] x 3s: armor and armor toughness -15% and healing received -10% per stack, up to 6 stacks. Targets at full stacks take an extra [Level] x 15% damage. |
| **Shabriri's Calamity** *(curse)* | I | Chestplate | Curse Damage taken x1.5. Hostile creatures strongly prefer to target you. |
| **Unendurable Frenzy** | III | Sword | Fully charged attacks burn you for 20% of your max HP over 3s, and burn the target for [Level] x 0.1 times that self-inflicted damage. While burning, healing received -90%. |
| **Sleep Mist** | V | Sword | After a fully charged attack, [Level] x 2% chance to inflict Sleep [Level] for [Level] x 3s: blindness, unable to move, jump or attack. The first hit from a creature awakens the target, dealing +75% + [Effect Level] x 25% damage and clearing Sleep. |
| **St. Trina's Arrow** | V | Bow | On an arrow hit, [Level] x 3% chance to inflict Sleep [Level] for [Level] x 3s: blindness, unable to move, jump or attack. The first hit from a creature awakens the target, dealing +75% + [Effect Level] x 25% damage and clearing Sleep. |
| **Blood Tax** | III | Sword | On fully charged hits: damage dealt +[Level] x 0.15% for every 1% of HP lost. After dealing damage, restore [Level] x 2% of lost HP. (If the main-hand weapon has Bloody Slash: healing is doubled.) |
| **Bloody Slash** | III | Sword | Fully charged attacks consume 10% of your max HP (at 10% HP or less, you die instead) to deal an extra [Level] x 5% of the target's current HP as damage. On a kill, restore [Level] x 2.5% of your max HP. (If the main-hand weapon has Blood Tax: healing is doubled.) |
| **Loretta's Greatbow** | I | Bow | Arrow damage +50%, and arrows explode on impact. |
| **Loretta's Mastery** *(treasure)* | I | Bow | Arrow damage -25%, but arrows produce four large explosions on impact. |
| **Ancestral Spirit's Horn** | III | Chestplate | Magic damage negation +25%. After taking magic damage, restore [Level] x 5% of that damage as HP over 10s. |
| **Goddess of Rot** | I | Sword | While held in the main hand, you constantly have Scarlet Rot 1. Within 32 blocks of you: Scarlet Rot damage is raised to 250%, rot-afflicted creatures have a 25% chance to infect whatever they hit in melee, and when a rot-afflicted creature dies, each creature within 16 blocks has a 50% chance to catch it for 10s. Fully charged attacks on an infected target restore 10% of your max HP. |
| **Millicent's Prosthesis** | III | Sword | While empowered, damage dealt +[Level] x 5%. Fully charged attacks add a stack (each: attack damage +[Level] x 1%, attack speed +[Level] x 2%), lasting 1.5s. At 7 stacks the damage bonus doubles, and the next fully charged attack raises it to 16 stacks lasting 3s. |
| **Waterfowl Dance** *(treasure)* | III | Sword | Fully charged attacks split their damage evenly into [Level] + 1 strikes landing 0.1s apart (ignoring hit invulnerability). |
| **Black Flame Blade** *(treasure)* | III | Sword | Attacks burn the target for [Level] x 15% of the original damage over 5s. |
| **Black Flame Protection** | III | Chestplate | Physical damage negation +[Level] x 12.5% (armor-ignoring damage still gets through). Cost: healing received -[Level] x 25%. |
| **Black Flame Ritual** | I | Chestplate | Damage dealt +10% for each buff you have (+20% for each debuff). Cost: while you have any status effect, burns 5% of your current HP per second. |
| **Blade of Death** *(treasure)* | I | Sword | Base damage reduced to 50%. Fully charged attacks burn the target for 50% of the original damage over 5s and reduce its max HP by 10% for 45s. Burn damage halved against bosses. |
| **Unseen Blade** *(treasure)* | III | Sword, bow, crossbow | After dealing damage, become invisible for [Level] x 1s (3x as long when hitting with an arrow). |
| **Bedrock Blast** *(treasure)* | III | Pickaxe | Breaking a block with a pickaxe also breaks every connected block of the same type (within 3x3x3 at level I, 5x5x5 from level II), costing 1 durability each. |
| **Glintstone Fling** | I | Sword | Magic damage you deal is converted to physical damage, and damage +50%. (Does not apply to damage converted by Adula's Moonblade or Forsaken Child of Darkness.) |
| **Glintstone Icecrag** | V | Bow | Damage is converted to magic damage. Arrow hits inflict Frostbite [Level] for 10s: movement speed -[Level] x 7.5%, and [Level] x 0.25% of max HP as physical damage every 0.5s (+100% per level). |
| **Scholar's Shield** | V | Shield | While guarding: damage negation +[Level] x 7.5% (not against environmental damage), and melee attacks reflect [Level] x 10% of their damage back at the attacker (even when blocked). |
| **Starlight** | V | Armor | Continuously lights up where you stand at light level 15 (regardless of level; not while standing in water or other blocks). |
| **Adula's Moonblade** | III | Sword | Fully charged attacks deal magic damage and inflict Frostbite 1 for 10s on the target and every creature within [Level] blocks of it, sparing your own kind, teammates and pets (+1 level if already applied, up to 10): movement speed -[Level] x 7.5%, and [Level] x 0.25% of max HP as physical damage every 0.5s (+100% per level). (Stacking is twice as fast at night.) |
| **Moon of Nokstella** *(treasure)* | I | Armor | At night, each second has a 2.5% chance to make creatures within 32 blocks that are targeting you turn on another kind of creature they can see that is no farther from them than you. |

</details>

<details>
<summary><b>Combat Arts (26)</b></summary>

| Enchantment | Max | Applies to | Effect |
|---|---|---|---|
| **Holy Ground** | III | Shield | While guarding: every player within 16 blocks, including yourself (friend or foe), takes [Level] x 5% less damage. Every 3s, each of them also restores [Level] x 1.5% of max HP and gains a permanent damage-absorbing shield worth [Level] x 3% of max HP (capped at [Level] x 33%). |
| **Prayerful Strike** *(treasure)* | I | Sword | In combat, charges once every 4s (the charge is lost if you don't strike within 4s of it filling, or if a creature hits you while it is ready). Your next attack deals an extra 2.5% of your max HP plus 7.5% of the target's current HP (at most 10% of the target's max HP), restores the same amount of HP, and permanently raises your max HP by half that value (after the first time, at most 5% of your current maximum per hit; all of it is lost on death). |
| **Sacred Blade** | V | Sword | Damage against non-undead -80%. Against undead: deal up to an extra [Level] x 25% damage scaling with the target's current HP. On fully charged hits, also restore 20% of that bonus damage as HP (at most 10% of your max HP) and permanently weaken the target's attack damage by [Level] x 5% (stacks, up to 99%). |
| **Vow of the Indomitable** *(treasure)* | I | Chestplate | Scaling with lost HP, incoming damage has up to a 75% chance to be negated entirely. |
| **Assassin's Gambit** *(treasure)* | III | Sword | After taking a melee hit from a creature, become invisible for [Level] seconds. A melee hit while invisible breaks stealth and deals an extra [Level] x 25% damage (on a critical hit, the critical multiplier is doubled instead: 1.5x -> 3x). |
| **Barrage** | I | Bow | Bow draw speed x5. Arrow damage -50%. |
| **Corpse Piler** *(treasure)* | III | Sword | Melee kills have a 50% chance to grant a Corpse Pile stack: on fully charged attacks, each stack grants +[Level] x 1% damage and restores [Level] x 0.05% of max HP, up to 50 stacks (all stacks are lost after 5 minutes without gaining one; half are lost on death). |
| **Cragblade** | III | Sword | For 10s after dealing damage (dealing damage again restarts the timer): attack power, armor, armor toughness and existing knockback resistance +[Level] x 10% each. |
| **Double Slash** | III | Sword | Fully charged attacks have a [Level] x 5% + 20% chance to trigger a follow-up strike dealing [Level] x 20% damage (magic damage that ignores armor). |
| **Endure** | III | Sword | When struck in melee, store [Level] x 10% of the damage (capped at max HP x [Level] x 40%). Your next attack releases the entire stored amount as bonus damage. |
| **Gravitas** | V | Sword | Attacks reduce the target's attack, movement and flight speed by 10% + [Level] x 4% for [Level] x 2s, and create a gravity field centered on you for [Level] x 4s: every creature within 12 blocks suffers -10% attack, movement and flight speed and cannot jump. |
| **Lion's Claw** | III | Sword | Attacks have a 20% chance to ignore armor and deal an extra [Level] x 15% damage. |
| **Offering Blade** | V | Sword | At full HP, attacks deal an additional [Level] x 10% damage. |
| **Parry** | V | Shield | A counterattack within 0.5s of taking a frontal melee hit on your shield deals an extra [Level] x 25% damage (2s cooldown). |
| **Quickstep** | V | Boots | Movement speed +[Level] x 20% at most, scaling with lost HP. |
| **Repeating Thrust** | V | Sword | Each fully charged hit on the same target adds a stack, and each stack gives +[Level] x 5% damage (no cap; stacks clear after 10s without a fully charged hit, or when you switch targets). |
| **Seppuku** | I | Sword | Triggers on a fully charged hit at or above 75% HP: spend 50% of your max HP to enter Seppuku for 10s - lose 12.5% of max HP per second (can be fatal), movement speed +120% (decaying to +30%), damage +40%, attack speed +80%, armor and armor toughness -75%, healing received +60%. While in Seppuku, fully charged hits restore 25% of the damage as HP (at most 25% of your max HP) and inflict blood loss for 1.5s, draining 7% of the target's max HP. A melee kill extends Seppuku by 5s (up to 10s), refreshes the speed bonus and restores 10% of your missing HP. |
| **Shield Bash** | V | Shield | When a melee attack hits you while your shield is raised, knock the attacker back with an additional force of [Level] x 0.25. |
| **Sky Shot** | III | Bow | Shots against targets at least 5 blocks above you deal an extra [Level] x 100% damage plus about 30% of the target's current HP (at full draw; scales with arrow speed). Cost: triggering it inflicts Slowness II on you for 5s. |
| **Stamp (Sweep)** | III | Sword | Fully charged sprinting attacks become a spinning slash: this hit deals +[Level] x 10% damage, and every other creature within 3 blocks (teammates excluded) takes the same damage (up to 20). |
| **Stamp (Upward Cut)** | III | Sword | A fully charged sprinting attack cancels the sprint into a charged upward slash, launching the target with a force of [Level] x 0.3 and dealing an extra [Level] x 15% damage. |
| **Sword Dance** *(treasure)* | I | Sword | Targets you attack receive no i-frames. Cost: your damage is further multiplied by your attack charge (minimum 40%). |
| **Unsheathe** | III | Sword | Each fully charged attack has a 1% chance to trigger an unsheathing strike: damage x[Level] x 330%, followed by [Level] x 3.3s of -75% attack speed. If it does not trigger, the chance increases by 0.5% (stacking, and resetting to 1% once triggered). |
| **Vow of Vengeance** | III | Sword | Damage dealt +[Level] x 2.5% for each creature within [Level] x 2 blocks (target included, up to 20), plus a further [Level] x 5% against the last creature that attacked you within 5s. |
| **Blood Blade** | III | Sword | Fully charged attacks consume 15% of your max HP to deal extra damage of up to [Level] x 33% plus [Level] x 3.3% of the target's current HP, scaled by your current HP ratio. |
| **Rotten Winged Sword** | V | Sword | Fully charged attacks add a stack (up to 20; each stack fades after 15s). Each stack: damage dealt +[Level] x 0.75%. |

</details>

<details>
<summary><b>Remembrances (15)</b></summary>

| Enchantment | Max | Applies to | Effect |
|---|---|---|---|
| **Golden Order** | I | Sword | Damage dealt +15%, scaling with lost HP up to +60%. Gain 15% damage negation. Immune to any hit dealing 15% or less of your current HP, and fully negate one hit every 5s. |
| **Blasphemy** | I | Sword | On killing a creature with a melee hit, drain 10% of its max HP and restore 10% of your max hunger. |
| **Warrior** | I | Sword | Melee damage dealt +25%. On killing a creature in melee, restore 25% of lost HP. Half of the damage you take is converted into a slow drain over 3s. |
| **Giantsflame Take Thee** | I | Chestplate | Gain 0.25% damage negation for every 1% of HP remaining. Immune to fire damage, converting it to healing instead (at most twice per second). While ablaze: melee attackers take fire damage equal to 50% of the damage they deal to you. |
| **Starscourge** | I | Sword | At or above 50% HP: damage dealt +50%. Below 50% HP: gain 25% damage negation. (Both effects are doubled at night.) |
| **Omen** | I | Sword | Fully charged attacks inflict Omen for 10s: damage taken +25%, healing received -50%, attack power and attack speed -20%. On a fully charged hit against an Omen-afflicted target, half of the damage cannot be negated (killing the target outright if that half is lethal). Hits that are not fully charged deal 50% less damage to Omen-afflicted targets. |
| **Placidusax's Rewind** | I | Chestplate | Upon lethal damage, prevents death and grants 5s of invulnerability. Damage taken during that time is stored and reflected back at attackers; when it ends, restore HP equal to 25% of the stored damage (300s cooldown). |
| **Rapture of Blood** | I | Sword | Every third fully charged hit (on any target) drains 12% of the target's current HP (restoring at most 18% of your max HP) and inflicts blood loss for 1.5s, draining 7% of its max HP. (If the main-hand weapon also has Bloody Slash and Blood Tax: blood loss +7%.) |
| **Ancestral Spirit** | I | Chestplate | Gain 50% magic damage negation. After taking damage, restore 5% of lost HP over 10s. |
| **Miquella's Blade** | I | Sword | Base damage -60%. Each fully charged attack grants a stack (no limit): damage dealt +20%, damage taken +10%. All stacks are lost if you go 2s without a fully charged attack. |
| **Destined Death** | I | Sword | Fully charged attacks burn the target for 2% of its max HP plus 20% of the original damage over 2s, and reduce its max HP by 10% for 45s. Burn damage halved against bosses. |
| **Those Who Live in Death** | I | Chestplate | Upon lethal damage, prevents death and fully restores HP, then bleeds out rapidly and dies (240s cooldown). |
| **Dark Moon** | I | Sword | At night: fully charged melee magic damage +25%, healing received +25%, and gain Night Vision. When such a hit strikes a creature targeting you, it deals an extra 5% of the creature's current HP and restores 5% of the total damage as HP (at most 5% of your max HP). (If your armor has Full Moon: all values +50%.) |
| **Forsaken Child of Darkness** | I | Sword | Fully charged attacks are converted to magic damage, ignore armor, and steal one random buff from the target. At night: gain 10% damage negation and restore 1.5% max HP per second. |
| **Full Moon** | I | Chestplate | At night: healing received +25%. Upon lethal damage, prevents death and restores 10% max HP per second for 10s, with 50% damage negation during that time (180s cooldown, halved at night). (If the main-hand weapon has Dark Moon: the restoration and negation last twice as long.) |

</details>

<details>
<summary><b>Death (4)</b></summary>

| Enchantment | Max | Applies to | Effect |
|---|---|---|---|
| **Greatblade Phalanx** | III | Armor | Upon lethal damage, summon 3 greatblade glintblades that home in on the attacker, each dealing magic damage equal to [Level] x 10% of the attacker's lost HP and producing a massive explosion (doesn't stack across armor pieces; 300s cooldown). |
| **Ancient Dragons' Lightning Strike** | III | Chestplate | Upon lethal damage, call down up to [Level] x 100 lightning bolts on every creature within 60 blocks, at most [Level] x 15 per target. Each bolt deals 5% of current HP plus 0.5% of max HP (the latter x2 in rain, x4 in thunderstorms). (90s cooldown.) |
| **Scarlet Aeonia** | III | Armor | Upon lethal damage, prevents death: push away creatures within [Level] x 4 blocks and become invulnerable but immobile for 1.5s. When it ends, creatures within [Level] x 2 blocks lose [Level] x 5% of their current HP, receive Scarlet Rot [Level] for [Level] x 10s and are knocked back with a force of [Level] x 0.75 - then you die. (90s cooldown.) |
| **Contagious Fury** | III | Armor | When a hit would drop you to 30% HP or lower: negate it, set your HP to 30%, knock back every creature within [Level] x 4 blocks, and stay invulnerable but immobile until you die. After 1.5s those creatures burn for [Level] x 60% of your max HP over 3s - then you die. (90s cooldown.) |

</details>

<details>
<summary><b>Laws (1)</b></summary>

| Enchantment | Max | Applies to | Effect |
|---|---|---|---|
| **Stellar Order** | I | Sword | At night: healing received +50%, movement speed +26%. Fully charged hits inflict Frostbite 1 on the target, and taking physical damage inflicts it on the attacker, for 10s (+1 level if already applied, up to 10). Fully charged magic damage against frostbitten targets +[Frostbite Level] x 7.5%. |

</details>


### In-game screenshots

Many enchantments come with their own effects: magic circles, lightning, flames, a full moon, a blooming rot flower... All shots below are taken in game (with a shader pack).

![Full Moon: cheats death and raises a full moon overhead, then keeps restoring health](https://cdn.modrinth.com/data/cached_images/c0e6421a66390ab2404f3e94b00ef49da6460648.jpeg)
*Full Moon: cheats death and raises a full moon overhead, then keeps restoring health*

![Realm of Sorcery: players inside the circle, you included, deal 50% more magic damage](https://cdn.modrinth.com/data/cached_images/cd57ea2d041e0caebb14a7ac0a71dacbd50c889f.jpeg)
*Realm of Sorcery: players inside the circle, you included, deal 50% more magic damage*

![Thops's Barrier: nobody inside the barrier can deal or take magic damage](https://cdn.modrinth.com/data/cached_images/f67d6162af69600cdd3c60942ea98e1c409a95b9.jpeg)
*Thops's Barrier: nobody inside the barrier can deal or take magic damage*

![Holy Ground: while guarding, players in range take less damage, heal and gain a shield](https://cdn.modrinth.com/data/cached_images/902b9929bb947b42c1f50ea16dcf0c915bbb5f16.jpeg)
*Holy Ground: while guarding, players in range take less damage, heal and gain a shield*

![Law of Regression: repeatedly clears every buff and debuff in range](https://cdn.modrinth.com/data/cached_images/4b157b6303b3d9023257d768e2fbf6b1bcddfa71.jpeg)
*Law of Regression: repeatedly clears every buff and debuff in range*

![Vyke's Dragonbolt: attacks call down red lightning, and in a thunderstorm every fully charged hit does](https://cdn.modrinth.com/data/cached_images/84abd563d481702609174236eb75243662e19b9c.jpeg)
*Vyke's Dragonbolt: attacks call down red lightning, and in a thunderstorm every fully charged hit does*

![Unendurable Frenzy: every fully charged attack sets you ablaze, and the target with you](https://cdn.modrinth.com/data/cached_images/af2ebe986c54571bebcf003475217d0c3d180f7f.jpeg)
*Unendurable Frenzy: every fully charged attack sets you ablaze, and the target with you*

![Scarlet Aeonia: on a lethal hit, a rot flower blooms and infects everything nearby](https://cdn.modrinth.com/data/cached_images/e92f979c88a63346dbe443576fe527e8e6ebc6cd.jpeg)
*Scarlet Aeonia: on a lethal hit, a rot flower blooms and infects everything nearby*


### Enchantment Codex

Press `K` in game (rebindable in Controls) to open the codex:

- **Browse and search** every enchantment by name or effect text, grouped by type and theme.
- **Detail page:** what it does, which items accept it, what it conflicts with, where to get it, and every tunable number.
- **Insert an item:** pick a piece of gear from your inventory and instantly see which enchantments it can still take, which it already has, which are blocked by its current enchantments, and whether you need the enchanting table or an anvil.
- **Other enchantments:** vanilla and other mods' enchantments can be looked up here too.
- **Effect toggles:** particles, circles and on-screen indicators can be turned off per enchantment; this only changes your own screen.

![Detail page: click a level to turn the formulas into actual numbers](https://cdn.modrinth.com/data/cached_images/6d1e818ca91eecef22d8c37e83c2ca8b91c13477_0.webp)
*Detail page: click a level to turn the formulas into actual numbers*

![Conflicts link to each other; tunable values list their defaults and allowed ranges](https://cdn.modrinth.com/data/cached_images/dfe4def21d2005b9973f6efe8ff8d6bf64a402f8.png)
*Conflicts link to each other; tunable values list their defaults and allowed ranges*

![Insert an item: the list is grouped by what it can still take, and each entry says whether you need the enchanting table or an anvil](https://cdn.modrinth.com/data/cached_images/f8d770bdea7b3a3cb7898f7a88d0c2e6775967cf.png)
*Insert an item: the list is grouped by what it can still take, and each entry says whether you need the enchanting table or an anvil*

![Other enchantments: vanilla and other mods' enchantments, with the same item check](https://cdn.modrinth.com/data/cached_images/9955f9092f3666a75e4c99309ff0637ca0597ed7_0.webp)
*Other enchantments: vanilla and other mods' enchantments, with the same item check*

![Effect toggles: switch particles, circles and on-screen indicators off one by one; only your own screen changes](https://cdn.modrinth.com/data/cached_images/34b95c9680e8d35d939c3771acbc6eed223caeec_0.webp)
*Effect toggles: switch particles, circles and on-screen indicators off one by one; only your own screen changes*


### Getting enchantments

- **Enchanting table:** they show up alongside vanilla enchantments, weighted by rarity.
- **Anvil:** apply enchanted books to your gear.
- **Loot:** chests, fishing and mob-dropped enchanted books. Treasure enchantments only come from here.
- **Villagers:** by default they are kept out of librarian book trades, so a hundred-odd new entries do not bury Mending and the other vanilla books. This can be enabled in the config.

### For server owners and modpack makers

- **Config screen:** select CarianStyle in the Mods list and click *Config*, or edit `config/carianstyle-common.toml` directly.
- **Disable enchantments:** add their IDs (e.g. `doomed_death`) to the disable list.
- **Tune numbers:** `config/carianstyle/enchantment_values.json` lists every number of every enchantment with its allowed range. In single player, press *Reload values* in the codex to apply changes right away; on a server, restart it. Values the client needs for drawing (such as the size of a range circle) are synced to players when they join. Description texts do not change automatically, so update the language file if you want them to match.
- **How hard they are to get:** turn a whole rarity tier into treasure enchantments, change the enchanting-table difficulty multiplier, choose whether rarity weights apply, and allow or block villager trades.
- **Mobs:** mobs wearing enchanted gear trigger the effects too by default. Death-triggered enchantments and near-death revives such as Full Moon and Those Who Live in Death are off for mobs by default, so bosses do not revive or retaliate when you finish them. On servers with many enchanted mobs you can turn mob triggers off completely to save performance.
- **Very high levels:** if commands or other mods push enchantments far beyond their maximum level, enable *Level limit* or *High-level decay* so each extra level gives less (both off by default).
- **PvP:** stealth enchantments hide the hitbox too, so F3+B does not reveal invisible players. This follows the server's config.

### FAQ

**Too many enchantments to remember?**
Press `K` for the in-game codex. MC百科 (mcmod.cn) also has a page for every enchantment, in Chinese.

**Does it work with other weapon or combat mods?**
Most effects are percentage-based, so they stay balanced with modded weapons. Conflicts are shown in the in-game codex.

**Do servers need it?**
Yes, install it on both the client and the server.

**Can I put it in a modpack?**
Yes, no need to ask. Please credit the author; commercial use is not allowed (see the license below).

### License and credits

- Licensed under [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/): free to use, modify and include in modpacks; no commercial use; redistributed or modified versions must credit the original author (Roinflam), state what was changed, and use the same license.
- ELDEN RING is a trademark of FromSoftware, Inc. / BANDAI NAMCO Entertainment Inc. The names, concepts and artwork from Elden Ring referenced in this mod belong to their respective owners. This is an unofficial fan-made mod and is not affiliated with or endorsed by them.
- The 1.20.1 version bundles [Cloth Config API](https://github.com/shedaniel/cloth-config) by shedaniel (GNU LGPLv3), unmodified, only for the config screen.

</details>

<details>
<summary><h2>中文</h2></summary>

卡利亚式附魔把《艾尔登法环》里的战技、魔法、祷告、护符和追忆做成了 **113 个附魔**。它们都从原版的附魔台、铁砧和战利品里获得，不用学新系统；但每一个都带着原作的脾气：火焰流派要自己身上着火才能发挥全力，群星和月亮的力量在夜里才会苏醒，追忆附魔极难获得、彼此互斥，强大的效果往往伴随着代价。

大多数效果按伤害或生命值的百分比结算，所以无论拿的是原版的钻石剑，还是其他模组里一刀上千伤害的武器，强度都不会失控。

### 版本须知

| | 1.20.1 | 1.12.2 |
|---|---|---|
| 加载器与 Java | Forge 47.x，Java 17 | Forge 14.23.5.2855 及以上，Java 8 |
| 安装位置 | 客户端和服务端都要装 | 客户端和服务端都要装 |
| 前置模组 | 无（配置界面用到的 Cloth Config API 已内置） | 无 |
| 附魔百科（`K` 键） | 有 | 无 |
| 配置文件 | `config/carianstyle-common.toml`，以及各附魔数值 `config/carianstyle/enchantment_values.json` | `config/carianstyle.cfg` |

两个版本的附魔种类相同。本页的效果、数值都以 1.20.1 版为准；1.12.2 版更新较少，部分数值和触发条件与 1.20.1 版不同。

### 附魔分类

附魔分为五类。除通用附魔外，同一类的附魔互相冲突，一件装备上只能有其中一个：

- **通用附魔（67 个）**：护符、火焰、群星、黄金律法等大部分附魔都在这一类；
- **战技附魔（26 个）**：对应原作的战灰，如箭步回旋斩、连击、格挡、居合，一把武器只能带一个战技；
- **追忆附魔（15 个）**：对应原作各位追忆 Boss，全部是最稀有的一档，效果也最强；
- **死亡附魔（4 个）**：受到致命伤害（发狂扩散是生命值将降到 30% 以下）时发动，如猩红艾奥尼亚、巨剑阵；
- **律法附魔（1 个）**：群星律法。

稀有度决定最高等级：「普通」稀有度的附魔最高 Ⅴ 级（26 个），「稀有」的最高 Ⅲ 级（51 个），「极稀有」的只有 Ⅰ 级（36 个）。其中 23 个是宝藏附魔，附魔台附不出来，只能从战利品里找；另有 2 个诅咒附魔。

### 全部附魔

和游戏里看到的说明一致，`[附魔等级]` 指附魔的等级。

<details>
<summary><b>通用附魔（67 个）</b></summary>

| 附魔 | 最高等级 | 可附魔物品 | 效果 |
|---|---|---|---|
| **卡利亚圆阵** | Ⅲ | 弓 | 箭矢命中后[附魔等级]×2%概率生成9道魔法辉剑自动追踪射向目标，每道造成[附魔等级]×5%魔法伤害 |
| **卡利亚式奉还** | Ⅲ | 盾牌 | 举盾受到魔法或远程伤害时生成3道魔法辉剑自动追踪射向攻击者，每道造成[附魔等级]×20%魔法伤害 |
| **镇定**（宝藏） | Ⅴ | 盔甲 | 受到的负面效果等级+1，但持续时间-[附魔等级]×15%（多件盔甲等级相加） |
| **黄金树庇护**（宝藏） | Ⅲ | 盔甲 | 攻击或被攻击时获得[附魔等级]级黄金树庇护[附魔等级]×2.5秒：非生物伤害减免+[等级]×20%（多件盔甲等级相加；攻击触发的至多5级） |
| **黄金树恩惠**（宝藏） | Ⅲ | 胸甲 | 攻击或被攻击时获得[附魔等级]级黄金树恩惠[附魔等级]×2.5秒：最大生命值上限+[等级]×15%，剩余时间每到3秒的整数倍时回复[等级]×4%最大生命值（Ⅰ级持续不足3秒，不会回血） |
| **黄金树立誓**（宝藏） | Ⅲ | 盔甲 | 攻击或被攻击时获得[附魔等级]级黄金树立誓[附魔等级]×2.5秒：造成伤害+[等级]×15%，伤害减免+[等级]×10%（多件盔甲等级相加；攻击触发的至多5级） |
| **野兽健壮**（宝藏） | Ⅰ | 胸甲 | 所有增益和负面效果等级翻倍，但持续时间-60%（瞬间效果除外） |
| **野兽活力**（宝藏） | Ⅴ | 胸甲 | 所有增益和负面效果持续时间+[附魔等级]×30% |
| **拒绝** | Ⅲ | 护腿 | 被生物攻击时以0.5力度击退([附魔等级]-1)×0.75+5格内的所有生物（至多20个） |
| **恩惠露滴** | Ⅴ | 胸甲 | 饱食度满时每秒回复[附魔等级]×0.2%最大生命值 |
| **狄蒂卡之祸**（宝藏、诅咒） | Ⅰ | 盔甲 | 诅咒 受到的伤害×3；受伤无敌时间-50% |
| **硬箭** | Ⅴ | 弓 | 弓箭伤害+[附魔等级]×80%；代价：12格内有其他生物时，受到来自生物的伤害+[附魔等级]×80%，被击退效果+[附魔等级]×75% |
| **神皮襁褓** | Ⅲ | 剑 | 满蓄力攻击每命中4次回复([附魔等级]-1)×1%+3%最大生命值 |
| **红羽七刃剑** | Ⅴ | 剑 | 生命值≤20%时造成伤害+[附魔等级]×20% |
| **绿龟** | Ⅴ | 盔甲 | 恢复效果+[附魔等级]×7.5%；每次恢复额外回复至多[附魔等级]×0.15点生命值（已损失生命越多越接近上限）；多件护甲等级相加 |
| **蓝羽七刃剑** | Ⅴ | 剑 | 生命值≤20%时获得[附魔等级]×10%生物伤害减免 |
| **金色粪金龟** | Ⅴ | 剑 | 击杀目标时经验掉落+[附魔等级]×30% |
| **长尾猫** | Ⅲ | 盔甲 | 免疫低于([附魔等级]-1)×25%+50%最大生命值的坠落伤害（多件护甲等级相加） |
| **隐身面纱** | Ⅰ | 盔甲 | 潜行时进入隐身状态：其他玩家看不见你，所有生物无法追踪你；攻击或受到伤害后3秒内无法隐身 |
| **魔力对蝎** | Ⅴ | 胸甲 | 魔法伤害+[附魔等级]×10%，造成魔法伤害时回复[附魔等级]×1%已损失生命值；代价：物理伤害承受+[附魔等级]×10% |
| **龙徽大盾** | Ⅰ | 盔甲 | 每受到一次物理伤害叠加一层：护甲和盔甲韧性+7.5%、移速-1%，持续30秒（受击刷新），至多20层；满层时额外获得25%物理伤害减免 |
| **托普斯的力场**（宝藏） | Ⅰ | 盔甲 | 6格内所有生物（含自身）无法造成或受到魔法伤害 |
| **魔法之境**（宝藏） | Ⅰ | 盔甲 | 6格内所有同种生物（含自身；穿在玩家身上时即附近所有玩家）造成的魔法伤害+50% |
| **不变盾牌** | Ⅲ | 盾牌 | 举盾时：未被盾牌完全挡下的伤害-[附魔等级]×10%；盾牌完全挡下攻击时回复[附魔等级]×1%最大生命值，并清除攻击者全部增益和负面效果 |
| **回归性原理**（宝藏） | Ⅲ | 盔甲 | 频繁（平均每秒一次）清除[附魔等级]×3格（至多8格）内所有生物（含自身）的全部增益和负面效果（[附魔等级]为全身盔甲等级之和） |
| **因果性原理** | Ⅲ | 盔甲 | 每受到5次生物伤害后，对[附魔等级]×3格（至多10格）内所有生物造成该伤害[附魔等级]×75%的反伤（冷却1秒；[附魔等级]为全身盔甲等级之和） |
| **圣律** | Ⅰ | 盔甲 | 自身无法被治疗；进入世界（登录、切换维度等）时若没有吸收盾，获得100%最大生命值的伤害吸收盾；击杀生物获得10%生命值上限的伤害吸收盾（上限300%最大生命值）；拥有吸收盾时造成伤害+50%；受击后吸收盾仍有剩余时，反射剩余吸收盾值5%的魔法伤害 |
| **熔炉瘤** | Ⅲ | 胸甲 | 攻击者所处位置高于自身时，受到的伤害-[附魔等级]×25% |
| **熔炉羽**（宝藏） | Ⅲ | 盔甲 | 受到的伤害×([附魔等级]×50%+100%)（多件分别结算，倍率相乘）；受伤无敌时间+[附魔等级]×150%，获得[附魔等级]级速度和跳跃提升[附魔等级]×2秒 |
| **熔炉鳞** | Ⅲ | 盔甲 | 单次伤害达到生命值上限时，该伤害-[附魔等级]×15%（多件盔甲等级相加，至多-90%） |
| **火焰啊，吞噬一切！** | Ⅲ | 剑 | 着火时满蓄力攻击：对目标[附魔等级]格内至多16个其他生物（不波及其他玩家、队友与自己的宠物）造成[附魔等级]×15%伤害的火焰伤害，并使其着火10秒 |
| **火焰啊，赐予我力量！** | Ⅴ | 剑 | 攻击使自身着火10秒；着火时：造成伤害+[附魔等级]×7.5%，获得[附魔等级]×3.75%非魔法伤害减免 |
| **火焰的庇护啊** | Ⅴ | 盔甲 | 着火时：获得[附魔等级]×2%伤害减免，每秒回复[附魔等级]×0.1%最大生命值（多件盔甲各自生效） |
| **火焰的疗愈啊** | Ⅴ | 盔甲 | 着火时：被攻击后[附魔等级]×2.5%概率移除一个负面效果（多件盔甲等级相加），每移除一个获得10%最大生命值的永久伤害吸收盾 |
| **唤星** | Ⅲ | 弓 | 箭矢落地时将[附魔等级]×2格内生物吸向中心，1秒后对[附魔等级]格内生物降下落雷，造成箭矢基础伤害[附魔等级]×30%的伤害（夜晚×3） |
| **食粪** | Ⅴ | 剑 | 满蓄力近战造成伤害后：自身反胃[附魔等级]×1.5秒；目标反胃[附魔等级]×4秒并被玷污[附魔等级]×4秒：恢复效果-75% |
| **精准雷击** | Ⅲ | 盔甲 | 被远程攻击命中后对攻击者降下[附魔等级]道落雷（多件盔甲等级相加），每道造成30%受到伤害（雨天×2，雷暴×4） |
| **维克的龙雷** | Ⅲ | 剑 | 雷电伤害减免+[附魔等级]×15%；满蓄力近战造成伤害后[附魔等级]×5%概率降下落雷（雨天概率×2，雷暴必定触发），造成[附魔等级]×50%伤害（雨天×2，雷暴×4） |
| **猩红腐败** | Ⅲ | 剑 | 满蓄力攻击目标后施加1级猩红腐败，持续[附魔等级]×20秒：每秒受到3%当前+0.075%最大生命值的魔法伤害（无视护甲，每级+33%），恢复效果-25%，护甲-50% |
| **艾格基斯的腐败** | Ⅲ | 弓 | 箭矢落地时使[附魔等级]×2格内所有生物（含自身）获得[附魔等级]级猩红腐败[附魔等级]×5秒：每秒受到3%当前+0.075%最大生命值的魔法伤害（无视护甲，每级+33%），恢复效果-25%，护甲-[附魔等级]×50% |
| **冻霜踏地** | Ⅲ | 盔甲 | 单次受到生物造成的伤害达到25%最大生命值时触发（各件盔甲等级相加）：将3+([附魔等级]-1)×2格（至多10格）内至多20个站在地面上的生物以[附魔等级]×0.35力度击飞，并施加[附魔等级]级冻伤[附魔等级]×5秒：移速-[等级]×7.5%，每0.5秒受到[等级]×0.25%最大生命值的物理伤害 |
| **划空癫火** | Ⅲ | 弓 | 箭矢命中后3秒内燃烧自身10%最大生命值，同时燃烧目标[附魔等级]×0.2倍的自燃伤害；燃烧期间恢复效果-90% |
| **夏玻利利的嘶吼** | Ⅲ | 剑 | 满蓄力攻击后3秒内燃烧自身5%最大生命值（期间自身恢复效果-90%），使目标获得发狂[附魔等级]×3秒：每层护甲与盔甲韧性-15%、恢复效果-10%，至多6层；对满层目标额外造成[附魔等级]×15%伤害 |
| **夏玻利利的灾祸**（诅咒） | Ⅰ | 胸甲 | 诅咒 受到的伤害×1.5；怪物强烈倾向于攻击你 |
| **难耐癫火** | Ⅲ | 剑 | 满蓄力攻击后3秒内燃烧自身20%最大生命值，同时燃烧目标[附魔等级]×0.1倍的自燃伤害；燃烧期间恢复效果-90% |
| **催眠烟雾** | Ⅴ | 剑 | 满蓄力攻击后[附魔等级]×2%概率施加[附魔等级]级睡眠[附魔等级]×3秒：失明、无法移动、跳跃和攻击；首次受到生物伤害时觉醒：伤害+75%+[效果等级]×25%并清除睡眠 |
| **托莉娜箭** | Ⅴ | 弓 | 箭矢命中后[附魔等级]×3%概率施加[附魔等级]级睡眠[附魔等级]×3秒：失明、无法移动、跳跃和攻击；首次受到生物伤害时觉醒：伤害+75%+[效果等级]×25%并清除睡眠 |
| **鲜血征收** | Ⅲ | 剑 | 满蓄力攻击时：每损失1%生命值造成伤害+[附魔等级]×0.15%；造成伤害后回复[附魔等级]×2%已损失生命值（当主手武器拥有「鲜血斩击」时：回复翻倍） |
| **鲜血斩击** | Ⅲ | 剑 | 满蓄力攻击消耗10%最大生命值（生命值不高于10%时直接死亡），额外造成[附魔等级]×5%目标当前生命值伤害；击杀后回复[附魔等级]×2.5%最大生命值（当主手武器拥有「鲜血征收」时：回复翻倍） |
| **罗蕾塔的大弓** | Ⅰ | 弓 | 箭矢伤害+50%，命中时产生爆炸 |
| **罗蕾塔的绝招**（宝藏） | Ⅰ | 弓 | 箭矢伤害-25%，但命中时产生4次大范围爆炸 |
| **祖灵角** | Ⅲ | 胸甲 | 魔法伤害减免+25%；受到魔法伤害后10秒内回复[附魔等级]×5%伤害值的生命值 |
| **水鸟乱舞**（宝藏） | Ⅲ | 剑 | 满蓄力攻击时，伤害平分为[附魔等级]+1段，每0.1秒连续命中一段（无视受击无敌时间） |
| **米莉森的义手** | Ⅲ | 剑 | 增幅期间造成伤害+[附魔等级]×5%；满蓄力攻击叠加1层增幅（每层攻击力+[附魔等级]×1%、攻速+[附魔等级]×2%），持续1.5秒；叠满7层后伤害加成翻倍，再满蓄力攻击一次则增幅升至16层并持续3秒 |
| **腐败女神** | Ⅰ | 剑 | 主手持有时自身持续获得1级猩红腐败；以自身为中心32格内：猩红腐败伤害提升至250%，患猩红腐败的生物近战命中时25%概率传染给目标，患猩红腐败的生物死亡时16格内每个生物各有50%概率被传染10秒；满蓄力攻击已感染猩红腐败的目标回复10%最大生命值 |
| **无形刀刃**（宝藏） | Ⅲ | 剑、弓、弩 | 造成伤害后获得[附魔等级]×1秒隐身（用箭命中时持续时间×3） |
| **死亡刀刃**（宝藏） | Ⅰ | 剑 | 基础伤害降至50%；满蓄力攻击目标后：5秒内燃烧50%原伤害；45秒内最大生命值-10%；对Boss的燃烧伤害减半 |
| **黑焰仪式** | Ⅰ | 胸甲 | 每拥有一个增益效果造成伤害+10%（负面效果+20%）；代价：身上有任意状态效果时，每秒燃烧5%当前生命值 |
| **黑焰刀刃**（宝藏） | Ⅲ | 剑 | 攻击目标后5秒内燃烧[附魔等级]×15%原伤害的生命值 |
| **黑焰庇护** | Ⅲ | 胸甲 | 物理伤害减免+[附魔等级]×12.5%（无视护甲的伤害可穿透）；代价：恢复效果-[附魔等级]×25% |
| **挥石魔法** | Ⅰ | 剑 | 造成的魔法伤害转为物理伤害，伤害+50%（「亚杜拉的月光剑」「黑暗弃子」转出的魔法伤害不触发） |
| **星光** | Ⅴ | 盔甲 | 持续照亮所在位置，亮度为15（与附魔等级无关；所在位置有水等方块时不亮） |
| **爆碎岩盘**（宝藏） | Ⅲ | 镐 | 用镐挖掘方块时连锁破坏与之相连的同种方块（Ⅰ级3×3×3、Ⅱ级起5×5×5范围内），每格消耗1点耐久 |
| **辉石冰块** | Ⅴ | 弓 | 伤害转为魔法伤害；箭矢命中后施加[附魔等级]级冻伤10秒：移速-[等级]×7.5%，每0.5秒受到[等级]×0.25%最大生命值物理伤害（每级+100%） |
| **魔力盾牌** | Ⅴ | 盾牌 | 举盾时：伤害抗性+[附魔等级]×7.5%（环境伤害不算）；被近战攻击时反弹[附魔等级]×10%伤害给攻击者（盾牌挡下也会反弹） |
| **亚杜拉的月光剑** | Ⅲ | 剑 | 满蓄力攻击时伤害转为魔法伤害，并对目标及其[附魔等级]格内的生物（不波及同类、队友和自己的宠物）施加1级冻伤10秒（已有则+1级，至多10级）：移速-[等级]×7.5%，每0.5秒受到[等级]×0.25%最大生命值物理伤害（每级+100%）（夜晚叠层效率翻倍） |
| **诺克史端拉之月**（宝藏） | Ⅰ | 盔甲 | 夜晚时：每秒有2.5%概率让32格内以你为目标的生物转而攻击它视线内、离它不比你远的另一种生物 |

</details>

<details>
<summary><b>战技附魔（26 个）</b></summary>

| 附魔 | 最高等级 | 可附魔物品 | 效果 |
|---|---|---|---|
| **圣域** | Ⅲ | 盾牌 | 举盾时：16格内所有玩家（含自身，不分敌我）受到伤害-[附魔等级]×5%；每3秒为他们回复[附魔等级]×1.5%最大生命值，并获得[附魔等级]×3%永久伤害吸收盾（上限[附魔等级]×33%最大生命值） |
| **无敌**（宝藏） | Ⅰ | 胸甲 | 基于已损失生命值，受伤时至多75%概率完全免疫该伤害 |
| **祈祷一击**（宝藏） | Ⅰ | 剑 | 战斗中每4秒蓄力一次（蓄满后4秒内未出手或被生物击中则作废），下次攻击额外造成2.5%自身最大+7.5%目标当前生命值伤害（至多10%目标最大生命值），回复等量生命值，并永久+此值一半的生命值上限（首次之后单次至多当前上限5%，死亡时全部失去） |
| **神圣刀刃** | Ⅴ | 剑 | 对非亡灵造成伤害-80%；对亡灵：基于目标当前血量额外造成至多[附魔等级]×25%伤害；满蓄力时回复20%额外伤害的生命值（至多10%最大生命值），并永久削弱目标[附魔等级]×5%攻击力（可叠加，至多99%） |
| **二连斩** | Ⅲ | 剑 | 满蓄力攻击有[附魔等级]×5%+20%概率追加一次攻击，造成[附魔等级]×20%伤害（魔法伤害，无视护甲） |
| **切腹** | Ⅰ | 剑 | 生命值≥75%时满蓄力造成伤害触发：消耗50%最大生命值获得10秒切腹状态——每秒失去12.5%最大生命值（可致死），移速+120%（逐渐降至+30%），伤害+40%，攻速+80%，护甲与护甲韧性-75%，恢复效果+60%；切腹状态下满蓄力造成伤害回复25%伤害值生命值（至多25%最大生命值），并使目标失血1.5秒：流失7%最大生命值；近战击杀延长切腹5秒（至多10秒）并刷新加速、回复10%已损失生命值 |
| **剑舞**（宝藏） | Ⅰ | 剑 | 攻击目标后目标无受伤无敌时间；代价：伤害额外乘以攻击蓄力进度（最低40%） |
| **复仇誓言** | Ⅲ | 剑 | [附魔等级]×2格内每存在一个生物（含目标，最多计20个），造成伤害+[附魔等级]×2.5%；对5秒内最后攻击过你的目标额外+[附魔等级]×5%伤害 |
| **对空射击** | Ⅲ | 弓 | 射击高于自身至少5格的目标时额外造成[附魔等级]×100%伤害+约30%目标当前生命值伤害（满弓时，随箭速变化）；代价：触发后获得减速II 5秒 |
| **尸横遍野**（宝藏） | Ⅲ | 剑 | 近战击杀生物50%概率获得一层尸山：满蓄力攻击时每层+[附魔等级]×1%伤害，并回复[附魔等级]×0.05%最大生命值，至多50层（5分钟没有新增则清空，死亡损失一半） |
| **居合** | Ⅲ | 剑 | 满蓄力攻击时有1%概率触发居合斩：伤害×[附魔等级]×330%，之后[附魔等级]×3.3秒攻速-75%；未触发则概率+0.5%（可叠加，触发后重置为1%） |
| **岩石剑** | Ⅲ | 剑 | 造成伤害后10秒内（再次造成伤害重新计时）：攻击力、护甲、护甲韧性及已有的击退抗性各+[附魔等级]×10% |
| **忍耐** | Ⅲ | 剑 | 受到近战攻击后储存[附魔等级]×10%伤害值（上限为生命值上限×[附魔等级]×40%），下次攻击释放全部储存值为额外伤害 |
| **暗杀办法**（宝藏） | Ⅲ | 剑 | 受到生物近战攻击后获得[附魔等级]秒隐身；隐身状态下近战造成伤害时解除隐身并额外造成[附魔等级]×25%伤害（若为暴击则改为暴击倍率翻倍：1.5倍→3倍） |
| **格挡** | Ⅴ | 盾牌 | 盾牌正面受到近战攻击后0.5秒内反击额外造成[附魔等级]×25%伤害（冷却2秒） |
| **狮子斩** | Ⅲ | 剑 | 攻击有20%概率无视护甲并额外造成[附魔等级]×15%伤害 |
| **献斗剑** | Ⅴ | 剑 | 满血时攻击额外造成[附魔等级]×10%伤害 |
| **盾牌冲击** | Ⅴ | 盾牌 | 举盾时受到近战攻击后以[附魔等级]×0.25力度额外击退攻击者 |
| **碎步** | Ⅴ | 靴子 | 基于已损失生命值提升至多[附魔等级]×20%移速 |
| **箭步上砍** | Ⅲ | 剑 | 满蓄力冲刺攻击时中断冲刺并蓄力上挑，以[附魔等级]×0.3力度击飞目标并额外造成[附魔等级]×15%伤害 |
| **箭步回旋斩** | Ⅲ | 剑 | 满蓄力冲刺攻击时回旋斩击：本次伤害+[附魔等级]×10%，并对3格内其他生物（不含队友）造成同样的伤害（最多20个） |
| **连击** | Ⅴ | 剑 | 满蓄力攻击同一目标时每击叠加1层，每层伤害+[附魔等级]×5%（不封顶；10秒内未再满蓄力命中或切换目标时清空） |
| **连续射击** | Ⅰ | 弓 | 拉弓速度×5；箭矢伤害-50% |
| **重力** | Ⅴ | 剑 | 攻击时削弱目标10%+[附魔等级]×4%的攻击、移动和飞行速度[附魔等级]×2秒；同时以自身为中心生成持续[附魔等级]×4秒的重力场：12格内所有生物攻击、移动和飞行速度-10%且无法跳跃 |
| **血刃** | Ⅲ | 剑 | 满蓄力攻击消耗15%最大生命值，按自身当前生命值比例额外造成至多[附魔等级]×33%伤害+[附魔等级]×3.3%目标当前生命值伤害 |
| **腐败翼剑** | Ⅴ | 剑 | 满蓄力攻击时叠加1层（至多20层，每层15秒后消退）；每层使造成伤害+[附魔等级]×0.75% |

</details>

<details>
<summary><b>追忆附魔（15 个）</b></summary>

| 附魔 | 最高等级 | 可附魔物品 | 效果 |
|---|---|---|---|
| **黄金律法** | Ⅰ | 剑 | 造成伤害+15%，基于已损失生命值至多+60%；获得15%伤害减免；免疫不超过15%当前生命值的伤害；每5秒免疫一次伤害 |
| **亵渎** | Ⅰ | 剑 | 近战击杀生物后汲取其10%最大生命值，并恢复10%最大饥饿值 |
| **战士** | Ⅰ | 剑 | 近战造成伤害+25%；近战击杀生物后回复25%已损失生命值；受到的50%伤害转为3秒内缓慢流失 |
| **感受巨人火焰吧** | Ⅰ | 胸甲 | 每拥有1%生命值获得0.25%伤害减免；免疫火焰伤害并将其转为治疗（每秒至多2次）；着火时：受到近战攻击后，对攻击者造成所受伤害50%的火焰伤害 |
| **碎星** | Ⅰ | 剑 | 生命值≥50%时：造成伤害+50%；生命值＜50%时：获得25%伤害减免（夜晚时以上两项效果翻倍） |
| **噩兆** | Ⅰ | 剑 | 满蓄力攻击时施加10秒噩兆：受到的伤害+25%，恢复效果-50%，攻击力和攻击速度-20%；满蓄力攻击噩兆目标时，50%伤害无法被减免（足以致命时直接击杀）；未满蓄力攻击噩兆目标时伤害-50% |
| **普拉顿桑克斯的回溯** | Ⅰ | 胸甲 | 受到致命伤害时阻止死亡并免疫伤害5秒，期间受到的伤害被储存并反击给攻击者，结束后回复25%已储存伤害值的生命值（冷却300秒） |
| **鲜血的欢愉** | Ⅰ | 剑 | 每第三次满蓄力攻击（不限同一目标）汲取目标12%当前生命值（至多回复18%最大生命值），并使其失血1.5秒：流失7%最大生命值（当主手武器同时拥有「鲜血斩击」和「鲜血征收」时：失血量+7%） |
| **祖灵** | Ⅰ | 胸甲 | 获得50%魔法伤害减免；受伤后10秒内回复5%已损失生命值 |
| **米凯拉的锋刃** | Ⅰ | 剑 | 基础伤害-60%；每次满蓄力攻击获得一层增幅（不封顶）：造成伤害+20%、受到伤害+10%；2秒内没有再次满蓄力攻击则层数全部清空 |
| **命定之死** | Ⅰ | 剑 | 满蓄力攻击目标后：2秒内燃烧2%最大生命值+20%原伤害；45秒内最大生命值-10%；对Boss的燃烧伤害减半 |
| **死诞者** | Ⅰ | 胸甲 | 受到致命伤害时阻止死亡并回满生命值，随后迅速流血致死（冷却240秒） |
| **暗月** | Ⅰ | 剑 | 夜晚时：满蓄力近战的魔法伤害+25%，恢复效果+25%，获得夜视；满蓄力近战魔法伤害命中以你为目标的生物时额外造成5%目标当前生命值伤害，回复总伤害5%生命值（至多5%最大生命值）（当护甲拥有「满月」附魔时：所有数值+50%） |
| **满月** | Ⅰ | 胸甲 | 夜晚时：恢复效果+25%；受到致命伤害时阻止死亡，10秒内每秒回复10%最大生命值，期间获得50%伤害减免（冷却180秒，夜晚减半）（当主手武器拥有「暗月」附魔时：回复与减伤持续时间翻倍） |
| **黑暗弃子** | Ⅰ | 剑 | 满蓄力攻击转为魔法伤害并无视护甲，并随机窃取目标一个增益效果；夜晚时：获得10%伤害减免，每秒回复1.5%最大生命值 |

</details>

<details>
<summary><b>死亡附魔（4 个）</b></summary>

| 附魔 | 最高等级 | 可附魔物品 | 效果 |
|---|---|---|---|
| **巨剑阵** | Ⅲ | 盔甲 | 受到致命伤害时生成3道巨型魔法辉剑追踪射向攻击者，每道造成[附魔等级]×10%攻击者已损失生命值的魔法伤害并产生巨大爆炸（多件盔甲不叠加）（冷却300秒） |
| **古龙雷击** | Ⅲ | 胸甲 | 受到致命伤害时对60格内所有生物降下至多[附魔等级]×100道落雷，每个目标至多[附魔等级]×15道，每道造成5%当前+0.5%最大生命值伤害（后者雨天×2，雷暴×4）（冷却90秒） |
| **猩红艾奥尼亚** | Ⅲ | 盔甲 | 受到致命伤害时阻止死亡，推开[附魔等级]×4格内生物并进入1.5秒无敌（无法移动），结束后：[附魔等级]×2格内生物失去[附魔等级]×5%当前生命值并获得[附魔等级]级猩红腐败[附魔等级]×10秒，以[附魔等级]×0.75力度击退，随后自身死亡（冷却90秒） |
| **发狂扩散** | Ⅲ | 盔甲 | 受到会使生命值降至30%及以下的伤害时：抵消该伤害并将生命值设为30%，击退[附魔等级]×4格内所有生物，自身无敌且无法移动直至死亡；1.5秒后这些生物3秒内受到你最大生命值×[附魔等级]×60%的燃烧伤害，随后自身死亡（冷却90秒） |

</details>

<details>
<summary><b>律法附魔（1 个）</b></summary>

| 附魔 | 最高等级 | 可附魔物品 | 效果 |
|---|---|---|---|
| **群星律法** | Ⅰ | 剑 | 夜晚时：恢复效果+50%，移速+26%；满蓄力造成伤害或受到物理伤害时对对方施加1级冻伤10秒（已有则+1级，至多10级）；满蓄力攻击冻伤目标时魔法伤害+[冻伤等级]×7.5% |

</details>


### 游戏内实拍

许多附魔都有专门绘制的特效：法阵、雷柱、火焰、月亮、花朵……下面都是游戏内截图（开启了光影）。

![满月：濒死时阻止死亡，头顶升起一轮满月，之后持续回复生命](https://cdn.modrinth.com/data/cached_images/c0e6421a66390ab2404f3e94b00ef49da6460648.jpeg)
*满月：濒死时阻止死亡，头顶升起一轮满月，之后持续回复生命*

![魔法之境：法阵里的玩家（含自己）造成的魔法伤害提高 50%](https://cdn.modrinth.com/data/cached_images/cd57ea2d041e0caebb14a7ac0a71dacbd50c889f.jpeg)
*魔法之境：法阵里的玩家（含自己）造成的魔法伤害提高 50%*

![托普斯的力场：力场内谁都无法造成或受到魔法伤害](https://cdn.modrinth.com/data/cached_images/f67d6162af69600cdd3c60942ea98e1c409a95b9.jpeg)
*托普斯的力场：力场内谁都无法造成或受到魔法伤害*

![圣域：举盾时为范围内的玩家减伤、回血、叠加护盾](https://cdn.modrinth.com/data/cached_images/902b9929bb947b42c1f50ea16dcf0c915bbb5f16.jpeg)
*圣域：举盾时为范围内的玩家减伤、回血、叠加护盾*

![回归性原理：反复清除范围内所有生物的增益和负面效果](https://cdn.modrinth.com/data/cached_images/4b157b6303b3d9023257d768e2fbf6b1bcddfa71.jpeg)
*回归性原理：反复清除范围内所有生物的增益和负面效果*

![维克的龙雷：攻击时降下红雷，雷暴天里每一下满蓄力攻击都必定落雷](https://cdn.modrinth.com/data/cached_images/84abd563d481702609174236eb75243662e19b9c.jpeg)
*维克的龙雷：攻击时降下红雷，雷暴天里每一下满蓄力攻击都必定落雷*

![难耐癫火：满蓄力攻击时烧自己，也一并烧向目标](https://cdn.modrinth.com/data/cached_images/af2ebe986c54571bebcf003475217d0c3d180f7f.jpeg)
*难耐癫火：满蓄力攻击时烧自己，也一并烧向目标*

![猩红艾奥尼亚：濒死时绽放腐败之花，让周围的敌人染上猩红腐败](https://cdn.modrinth.com/data/cached_images/e92f979c88a63346dbe443576fe527e8e6ebc6cd.jpeg)
*猩红艾奥尼亚：濒死时绽放腐败之花，让周围的敌人染上猩红腐败*


### 附魔百科

游戏里按 `K` 键（可在按键设置里改）打开附魔百科：

- **浏览和搜索**：按名称或效果搜索，按类别、主题分组，列表里直接看到稀有度、最高等级和冲突数；
- **详情页**：效果说明、能附在哪些物品上、和哪些附魔冲突、从哪里获得，以及这个附魔的全部可调数值；
- **放入物品**：从背包里选一件装备，立刻看到它还能附哪些附魔、哪些已经有了、哪些被已有附魔挡住，以及是用附魔台还是铁砧；
- **其他附魔**：原版和其他模组的附魔也能在这里查；
- **特效开关**：粒子、光圈、屏幕提示可以按附魔逐个关闭，只影响自己的画面。

![附魔详情：点等级可以把公式换算成具体数值，下面是基础信息和适用物品](https://media.forgecdn.net/attachments/description/615940/description_cea0e1b3-6448-4f32-897f-0ae3f3108a6f.png)
*附魔详情：点等级可以把公式换算成具体数值，下面是基础信息和适用物品*

![冲突附魔可以点击跳转；可调数值列出默认值和允许范围](https://media.forgecdn.net/attachments/description/615940/description_3e16cbd6-8a7f-455a-abf6-d67bc9a54a47.png)
*冲突附魔可以点击跳转；可调数值列出默认值和允许范围*

![放入物品：列表按「能附上 / 已经有了 / 被挡住」分组，并说明该用附魔台还是铁砧](https://media.forgecdn.net/attachments/description/615940/description_a46aa6de-c820-4f8f-a132-99a0876fc0c0.png)
*放入物品：列表按「能附上 / 已经有了 / 被挡住」分组，并说明该用附魔台还是铁砧*

![其他附魔：原版和其他模组的附魔也能查，同样能放入物品](https://media.forgecdn.net/attachments/description/615940/description_c1be80a7-8e5e-435b-8057-333a16d41c21.png)
*其他附魔：原版和其他模组的附魔也能查，同样能放入物品*

![特效开关：粒子、法阵、屏幕提示可以逐个关闭，只影响自己的画面](https://media.forgecdn.net/attachments/description/615940/description_e537f639-ff2c-487a-9211-edf732227c35.png)
*特效开关：粒子、法阵、屏幕提示可以逐个关闭，只影响自己的画面*


### 获取方式

- **附魔台**：和原版附魔一起按稀有度加权出现，越稀有越难出；
- **铁砧**：把附魔书合到装备上；
- **战利品**：战利品箱、钓鱼、怪物掉落的附魔书里都可能出现，宝藏附魔只能从这些地方获得；
- **村民交易**：默认不出现在图书管理员的附魔书交易里，避免一百多个附魔把经验修补等原版附魔挤掉，可在配置里打开。

### 给服主和整合包作者

- **配置界面**：在「模组」列表中选中卡利亚式附魔，点「配置」打开；也可以直接改 `config/carianstyle-common.toml`；
- **禁用附魔**：在配置的禁用列表里填附魔 ID（如 `doomed_death`），对应附魔就不会再出现；
- **调整数值**：`config/carianstyle/enchantment_values.json` 列出了每个附魔的全部数值和允许范围，单人游戏里改完在附魔百科点「重载数值」即可生效，服务器改完后重启生效；客户端绘制要用到的数值（如范围圈大小）会在玩家进服时自动同步。附魔说明文字不会跟着改，需要的话请同步修改语言文件；
- **获取难度**：可以把某一档稀有度的附魔全部改成宝藏附魔，调整附魔台难度倍率，选择是否按稀有度加权，以及是否允许村民交易；
- **怪物触发**：怪物穿戴带附魔的装备时默认也会触发效果，但死亡触发类和满月、死诞者等濒死复活类默认不触发，避免 Boss 在被击杀时复活或反杀；服务器里带附魔的怪物很多时，可以整个关掉以降低开销；
- **超高等级**：用指令或其他模组把附魔加到远超上限的等级时，可以开启「等级上限」或「高等级衰减」，让等级越高、每一级的收益越少（默认都关闭）；
- **PVP**：隐身类附魔会连碰撞箱一起隐藏，按 F3+B 也看不出隐身的玩家，由服务端配置决定。

### 常见问题

**附魔太多，记不住效果怎么办？**
按 `K` 打开附魔百科查看；[MC百科](https://www.mcmod.cn/class/6247.html) 上每个附魔也都有单独的资料页。

**和其他武器、战斗模组兼容吗？**
大部分效果按伤害或生命值的百分比计算，配合其他模组的武器也能保持平衡。冲突关系以游戏内百科显示的为准。

**服务器要装吗？**
客户端和服务端都要装。

**可以加进整合包吗？**
可以，无需另行询问；请注明原作者，不得用于商业用途（见下方许可协议）。

### 许可与声明

- 本模组采用 [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.zh-hans)（知识共享 署名-非商业性使用-相同方式共享 4.0 国际）协议：可以免费使用、修改、加入整合包；不得用于商业用途；转载或发布修改版需署名原作者 Roinflam、注明改动，并沿用同一协议；
- 《艾尔登法环》相关名称、设定与美术素材的版权归 FromSoftware / BANDAI NAMCO 所有，本模组为非官方同人作品，与其无关联；
- 1.20.1 版内置 shedaniel 的 [Cloth Config API](https://github.com/shedaniel/cloth-config)（GNU LGPLv3），未作修改，只用于配置界面。

</details>
