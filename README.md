<div align="center">

<p>
  <img src="assets/icon.png" alt="EmsiChill icon" width="144">
</p>

# EmsiChill

**A modular all-in-one suite for Paper servers**

Authentication, skins, homes, teleports, regions, graves, social tools, local resource packs and server administration in one plugin.

![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)
![Paper](https://img.shields.io/badge/Paper_API-26.2-blue?style=flat-square)
![Version](https://img.shields.io/badge/version-5.2.2-brightgreen?style=flat-square)
![License](https://img.shields.io/badge/license-MIT--0-lightgrey?style=flat-square)

</div>

---

## Overview

EmsiChill is built for small and medium survival or semi-survival servers that want a consistent set of features without installing a separate plugin for every task. Modules can be enabled independently, while commands, messages and administration stay under one project.

English is the default language. Every player can select English or Spanish without changing the language used by anyone else. Command names always remain in English.

## Requirements

| Requirement | Supported version |
|---|---|
| Java | `25` |
| Server | `Paper` |
| Minecraft / Paper API | `26.2` |

Spigot, Bukkit, Sponge, Folia, BungeeCord, Waterfall and Velocity are not supported targets. EmsiChill is a Paper server plugin, not a proxy plugin.

## Installation

1. Stop the Paper server.
2. Put `EmsiChill-5.2.2.jar` inside `plugins/`.
3. Start the server and wait for `plugins/EmsiChill/` to be generated.
4. Review `plugins/EmsiChill/config.yml` and the module configurations.
5. Run `/emsichill inspect` to check the installation.

When replacing the JAR, always perform a full server restart. `/reload` and `/emsichill reload` do not safely replace running Java code.

## Modules

| Module | What it provides |
|---|---|
| Authentication | Registration, login, password changes, login protection and optional temporary sessions. |
| Skins | Premium-account skins, random skins, favorites, history, reset and player heads. |
| Homes | Named homes, configurable limits and administration of offline-player homes. |
| Teleport | TPA, `/back`, safe random teleport, delays, cooldowns and request controls. |
| Player information | Playtime, leaderboard and last-seen information. |
| Regions | Claims, members, co-owners, ownership transfer, upgrades, settings and border previews. |
| Graves | Protected death storage, privacy time, expiration, recovery and persistent headstone displays. |
| Social | Sitting, crawling, standing and coordinate sharing. |
| Staff | Staff chat, vanish, staff mode, inventory inspection, freezing, mutes and warnings. |
| Resource packs | Local pack discovery, automatic packaging, hashing, hosting, caching and connection-stage loading. |
| Maintenance | Status, inspection, backups, data migration and EmsiChill/Paper update checks. |

Modules are controlled in `plugins/EmsiChill/config.yml`:

```yaml
modules:
  homes: true
  authentication: true
  skins: true
  teleport: true
  player-info: true
  staff: true
  regions: true
  graves: true
  social: true
  resource-packs: true
```

Disabling a module keeps its saved data. Use `/emsichill reload` after ordinary YAML changes and restart the server after changing the JAR or runtime-level settings.

## Languages

Players select their own message language with:

```mcfunction
/emsichill language english
/emsichill language spanish
```

Preferences are stored by UUID in `player-languages.yml`. English and Spanish players can therefore use the same server simultaneously. Console or authorized administration tools can change the global fallback with `emsichill.admin.language`.

## Graves

When grave mode is active, EmsiChill stores inventory contents, armor, offhand and experience after death. A compact headstone marks the location without exposing a visible chest.

| Action | Result |
|---|---|
| Right-click the headstone | Opens the grave inventory. |
| Shift + right-click | Collects the grave; overflow drops safely nearby. |
| Interact during another player's privacy time | Denies access and shows the remaining time once. |
| Use grave administration permission | Locates, inspects or recovers another player's graves. |

Headstone displays are restored when their chunks load after a restart. Grave chunks are not kept loaded permanently. `/grave` and `/graves` are administrative commands; regular players recover their own items through the headstone.

## Inventory Inspection

- `/invsee [player]` displays storage, hotbar, armor and offhand in a separated 54-slot interface.
- `/enderchestsee [player]` and `/ecsee [player]` open an Ender Chest.
- Omitting the player targets the administrator running the command.
- Self `/invsee` is read-only to prevent duplication from editing a mirrored inventory.
- Viewing and editing use separate permissions.
- Controlled shift-click transfers work in both directions.
- Unsafe swaps, drops, creative cloning and collect-to-cursor actions are blocked.

## Commands

The following reference is generated from `plugin.yml`, which is also the source used by `/emsichill help`.

<!-- EMSICHILL_COMMANDS_START -->

## Player commands

| Command | Description |
|---|---|
| `/register <password> <password>` | Registers an account. |
| `/login <password>` | Logs into an account. |
| `/changepassword <current> <new> <new>` | Changes your password. |
| `/unregister <password>` | Removes your own registration. |
| `/skin <name>` | Applies the skin of a premium account. |
| `/skin random` | Applies a random premium skin. |
| `/skin reset` | Resets your skin. |
| `/skin save <name>` | Saves a skin as a favorite. |
| `/skin unsave <name>` | Removes a skin from favorites. |
| `/skin favorites` | Opens the favorite skins menu. |
| `/skin history` | Opens your skin history. |
| `/skin clearhistory` | Clears your own skin history. |
| `/skull <name>` | Gets the head of a premium account. |
| `/sethome [name]` | Saves a home. |
| `/home [name]` | Teleports to a home using the configured delay. |
| `/delhome <name>` | Deletes a home. |
| `/homes` | Lists your homes. |
| `/tpa <player>` | Requests a teleport to another player. |
| `/tpahere <player>` | Requests another player to teleport to you. |
| `/tpaccept` | Accepts a teleport request. |
| `/tpdeny` | Denies a teleport request. |
| `/tpcancel` | Cancels a sent teleport request. |
| `/tptoggle` | Toggles incoming teleport requests. |
| `/back` | Returns to your previous location or grave. |
| `/rtp` | Finds a safe random location. |
| `/playtime [player]` | Checks playtime. |
| `/playtimetop` | Shows the playtime leaderboard. |
| `/seen [player]` | Checks the last seen time. |
| `/sit` | Toggles the sitting pose. |
| `/crawl` | Toggles the crawling pose. |
| `/stand` | Resets your pose. |
| `/whereami` | Shares your dimension and coordinates in chat. |
| `/emsichill language <english\|spanish>` | Changes your personal plugin language. |

## Region commands

| Command | Description |
|---|---|
| `/region claim <name>` | Claims a region centered on your position. |
| `/region list` | Lists your regions and coordinates. |
| `/region info [name]` | Shows region information. |
| `/region teleport <name>` | Teleports to one of your regions. |
| `/region view [name]` | Temporarily displays region borders. |
| `/region build` | Opens the menu to buy more region slots. |
| `/region upgrade [name]` | Opens the region expansion menu. |
| `/region settings [name]` | Opens region settings. |
| `/region add <player>` | Allows a member to build. |
| `/region remove <player>` | Removes a member. |
| `/region owner <player>` | Adds a co-owner. |
| `/region unowner <player>` | Removes a co-owner. |
| `/region transfer <player>` | Transfers primary ownership. |
| `/region delete <name> confirm` | Permanently deletes a region. |
| `/region help` | Shows region help. |

## Staff and moderation commands

| Command | Description |
|---|---|
| `/invsee [player]` | Opens inventory, armor and offhand. Editing requires an extra permission. |
| `/enderchestsee [player]` | Opens the Ender Chest. Editing requires an extra permission. |
| `/freeze <player> [seconds]` | Freezes, unfreezes or applies a timed freeze. |
| `/mute <player> [time]` | Mutes a player permanently or for 30s, 10m, 2h or 1d. |
| `/unmute <player>` | Removes an active mute. |
| `/warn <player> <reason>` | Records a warning with date, moderator and reason. |
| `/warnings <player>` | Shows the recent moderation history. |
| `/staffchat toggle` | Toggles staff chat. |
| `/staffchat <message>` | Sends a message to staff. |
| `/vanish [player]` | Toggles vanish mode. |
| `/vanishlist` | Lists vanished players. |
| `/staffmode [player]` | Toggles moderation tools. |
| `/skin <player> <skin>` | Changes another player's skin. |
| `/home <player> [home]` | Lists or uses homes from another player, including offline players. |
| `/back <player>` | Sends another player to their previous location. |
| `/auth unregister <player>` | Administratively removes an account registration. |
| `/auth changepassword <player> <new>` | Administratively changes a password. |
| `/grave list` | Lists the administrator's active graves. |
| `/grave locate <player>` | Lists and visually marks graves owned by a player. |
| `/grave recover <player>` | Recovers all online graves owned by a player. |
| `/grave admin recover <player>` | Administratively recovers a player's graves. |

## Administrative configuration commands

| Command | Description |
|---|---|
| `/emsichill homes limit <amount>` | Changes the default home limit. |
| `/emsichill rtp cooldown <minutes>` | Changes the global RTP cooldown. |
| `/deathcontrol default <grave\|keep\|drop>` | Changes the default death mode. |
| `/deathcontrol <player> <grave\|keep\|drop>` | Changes a player's death mode. |
| `/auth reload` | Reloads the authentication module. |
| `/emsichill rp reload` | Rebuilds local resource packs for the next player connection. |
| `/emsichill rp push` | Forces the active packs onto online players and may interrupt their game. |
| `/emsichill update check` | Checks for a new Release without installing it. |
| `/emsichill update changes <version>` | Shows a short in-game summary of Release notes. |
| `/emsichill update install <version>` | Downloads, validates and stages a Release when installs are enabled. |
| `/emsichill update ignore <version>` | Hides automatic notices for a specific Release. |
| `/emsichill update paper check` | Checks whether PaperMC published a new Paper/Minecraft build. |
| `/emsichill update paper download <version> <build>` | Downloads and verifies a new Paper build for the next restart. |
| `/emsichill update paper ignore <version> <build>` | Hides automatic notices for a specific Paper build. |
| `/emsichill reload` | Reloads plugin configuration. |
| `/emsichill status` | Shows module status. |
| `/emsichill inspect` | Checks data and configuration for problems. |
| `/emsichill backup` | Creates a data backup. |
| `/emsichill migrate` | Saves and normalizes current data. |
| `/emsichill help <category>` | Shows generated help by category. |

<!-- EMSICHILL_COMMANDS_END -->

## Permissions

Commands intended for normal players are enabled by default through these nodes:

```text
emsichill.skin
emsichill.skull
emsichill.home
emsichill.sethome
emsichill.delhome
emsichill.tpa
emsichill.back
emsichill.rtp
emsichill.playtime
emsichill.seen
emsichill.region.use
emsichill.region.claim
emsichill.pose
emsichill.whereami
```

Optional limit and delay nodes are disabled by default:

| Permission | Purpose |
|---|---|
| `emsichill.homes.3` | Raises the personal home limit to three. |
| `emsichill.homes.5` | Raises the personal home limit to five. |
| `emsichill.teleport.bypassdelay` | Skips configured teleport delays. |

Administrative nodes default to operators:

| Area | Permissions |
|---|---|
| EmsiChill | `emsichill.admin.reload`, `emsichill.admin.language`, `emsichill.admin.maintenance`, `emsichill.admin.update` |
| Resource packs | `emsichill.resourcepack.admin` |
| Authentication | `emsichill.auth.admin` |
| Skins | `emsichill.skin.others`, `emsichill.skin.bypasscooldown`, `emsichill.skin.favorites.unlimited` |
| Homes and teleport | `emsichill.homes.unlimited`, `emsichill.homes.admin`, `emsichill.homes.others`, `emsichill.back.others`, `emsichill.rtp.bypasscooldown`, `emsichill.rtp.admin` |
| Staff chat and visibility | `emsichill.staffchat`, `emsichill.vanish`, `emsichill.vanish.others`, `emsichill.vanish.see`, `emsichill.staffmode`, `emsichill.staffmode.others` |
| Inventory inspection | `emsichill.invsee.view`, `emsichill.invsee.modify`, `emsichill.enderchestsee.view`, `emsichill.enderchestsee.modify` |
| Moderation | `emsichill.freeze`, `emsichill.mute`, `emsichill.unmute`, `emsichill.warn`, `emsichill.warnings` |
| Regions | `emsichill.region.admin`, `emsichill.region.unlimited` |
| Death management | `emsichill.grave.admin`, `emsichill.deathcontrol.admin` |

Players without administrative permission do not receive protected commands through tab completion or EmsiChill help. A permissions plugin can grant individual nodes to trusted non-OP users.

## Local Resource Packs

Place every Java resource pack directly inside:

```text
plugins/EmsiChill/ResourcePacks/
```

Each source can be a `.zip` file or an unpacked folder. `pack.mcmeta` must be at its root or inside one immediate wrapper folder. For example, BetterModel works in this form:

```text
ResourcePacks/
|-- BetterModel/
|   `-- resourcepack/
|       |-- pack.mcmeta
|       |-- pack.png
|       `-- assets/
|-- MyPack.zip
|-- config.yml
`-- .generated/
```

EmsiChill processes sources alphabetically and keeps each pack independent. Prefix names with `01-`, `02-` and so on when stack order matters. Generated client files are stored in `.generated/` and should not be edited manually.

### Resource-pack workflow

| Command | Behavior |
|---|---|
| `/emsichill rp reload` | Rebuilds the local pack list. Connected players are not interrupted; changes apply when they reconnect. |
| `/emsichill rp push` | Forces the active pack list onto connected players. Minecraft may display its full-screen reload view. |

On a normal connection, Paper sends all packs in one request before the world appears. Unchanged packs are reused from the Minecraft client cache, while new or modified packs are downloaded. Minecraft owns its permission prompt and full-screen reload interface; the plugin cannot resize those client screens.

Main settings in `ResourcePacks/config.yml`:

```yaml
send-during-configuration: true
send-delay-ticks: 20
clear-existing: false
log-status: false

pack:
  required: true
  prompt: ""
  maximum-uncompressed-megabytes: 512

hosting:
  enabled: true
  bind-address: "0.0.0.0"
  port: 8165
  public-base-url: "auto"
```

Port `8165` must be reachable through the firewall and router for Internet players. With `public-base-url: "auto"`, EmsiChill uses the hostname the player used to connect. A reverse proxy may expose `/emsichill-packs/` through a public HTTPS address instead.

This module sends Java Edition packs. Bedrock players joining through Geyser require a separate Bedrock-compatible pack and conversion/distribution setup.

## Configuration Files

| File | Purpose |
|---|---|
| `config.yml` | Language fallback, modules, audit logging and update settings. |
| `messages_en.yml`, `messages_es.yml` | Visible English and Spanish messages. |
| `player-languages.yml` | Per-player language preferences. |
| `AuthenticationManager/config.yml` | Registration, login, sessions and login restrictions. |
| `Skin/config.yml` | Skin cache, cooldown, favorites and history. |
| `Home/config.yml` | Default and permission-based home limits. |
| `Teleport/config.yml` | TPA, `/back`, RTP, delays and cooldowns. |
| `PlayerInfo/config.yml` | Playtime and player-information behavior. |
| `Regions/config.yml` | Claim limits, sizes, upgrades and protection settings. |
| `Graves/config.yml` | Death mode, privacy, expiration and headstone visuals. |
| `Social/config.yml` | Pose and social settings. |
| `Staff/config.yml` | Staff chat, vanish, staff mode and moderation behavior. |
| `ResourcePacks/config.yml` | Local pack processing and internal HTTP hosting. |

## Updates And Maintenance

EmsiChill checks GitHub releases and PaperMC builds when their update sections are enabled. Downloads are staged for a safe restart; the running plugin or Paper JAR is never replaced in memory.

Recommended maintenance commands:

```mcfunction
/emsichill status
/emsichill inspect
/emsichill backup
/emsichill update check
/emsichill update paper check
```

Paper downloads are verified and placed in `server-updates/`. Stop the server before replacing its current Paper JAR.

## Stored Data And Safety

- Account passwords are stored as salted hashes, not plain text.
- YAML data writes use backup and recovery safeguards.
- Authentication sessions, homes, regions, graves, moderation records and player statistics persist across restarts.
- `/region delete` requires `confirm`.
- Self inventory inspection remains read-only.
- Administrative command suggestions are permission-filtered.
- `/emsichill backup` should be run before major updates or configuration migrations.

## Build From Source

```powershell
mvn clean package
```

The release JAR is created as `target/EmsiChill-5.2.2.jar`. The build also regenerates the command tables in this README and runs the automated test suite.

## License

EmsiChill is licensed under the **MIT No Attribution (MIT-0)** license. See [`LICENSE`](LICENSE).
