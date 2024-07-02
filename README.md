Original README.md below...
========

A quick fork I made of the wonderful Magic Plugin by Nathan Wolf to update it to work with the new AuraSkills (formerly Aurelium Skills) API.

It should work with everything its supposed to, _however_ I could not get it to use the AuraSkills XP as _currency_ for learning new Spells. It hooks fine into the Mana system and using the Sorcery skill, but still requires having its own independent system for Spell Points. I didn't need this functionality so gave up on trying to get it to work.

My code changes are pretty rough, not terribly tidy, but I don't care because I got working what I needed. :)


Feel free to use this fork however you like. I've compiled some .jars and tossed them up here, but feel free to clone the repo and do it yourself if you don't feel like trusting an Internet rando.


Magic
========


[![](https://discord.com/api/guilds/580099743875727363/widget.png)](https://discord.elmakers.com "Discord")
![](https://img.shields.io/github/languages/code-size/elBukkit/MagicPlugin.svg "Code size in bytes")
[![](https://img.shields.io/github/license/elBukkit/MagicPlugin.svg)](https://github.com/elBukkit/MagicPlugin/blob/master/LICENSE "License")

Magic plugin for CraftBukkit/Spigot. Adds powerful magic wands and items to your server.

Players may obtain or craft wands that can past a variety of configurable spells.

## Download

Distributed through BukkitDev: https://dev.bukkit.org/projects/magic

Distributed through Spigot: https://www.spigotmc.org/resources/magic.1056/

## Usage

FAQ: https://github.com/elBukkit/MagicPlugin/wiki/FAQ

Wiki: https://github.com/elBukkit/MagicPlugin/wiki

Reference: http://reference.elmakers.com/

Config info: http://magic.elmakers.com/

## Compile

Requires recent Java 8 JDK. Import directly as Maven Project. Build against parent with `clean install`

## Credits

- Lead Developer: NathanWolf
- Contributing Developers:
  - killme
  - dumptruckman
  - S-Toad
  - Boosik
  - Dr00bles
  - grisstyl

Please see README.md in the resource packs for artist credits!

https://github.com/elBukkit/MagicPlugin/blob/master/Magic/src/resource-pack/default/README.md

## Notes on Spell Icons

Some of the spell icons contained in this plugin are used with permission from eleazzaar's Painterly Spell Icons pack:

http://opengameart.org/content/painterly-spell-icons-part-1

This pack is licensed under Creative Commons 3.0, any re-use needs to fall under that license and 
not the less-restrictive MIT license that covers the rest of the Magic source code and assets.

## Issues

Issues, feature requests, or suggestions for this plugin should be made at our issue tracker:

https://github.com/elBukkit/MagicPlugin/issues