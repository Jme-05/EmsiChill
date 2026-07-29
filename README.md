<div align="center">

<p>
  <img src="assets/icon.png" alt="EmsiChill icon" width="150">
</p>

# EmsiChill

**Modular suite for Paper servers**

Authentication, skins, homes, teleports, regions, graves, poses, resource packs, player info and staff tools in one plugin.

![Java](https://img.shields.io/badge/Java-25-orange?style=flat-square)
![Paper](https://img.shields.io/badge/Paper_API-26.2-blue?style=flat-square)
![Version](https://img.shields.io/badge/version-5.2.0-brightgreen?style=flat-square)
![License](https://img.shields.io/badge/license-MIT--0-lightgrey?style=flat-square)

</div>

---

## What is EmsiChill

EmsiChill is a modular **Paper** plugin that bundles common survival and semi-survival server features into one JAR. It is designed for small and medium servers that want authentication, teleports, regions, graves, staff tools and simple player data without stacking many separate plugins.

Every major feature is a module. You can enable or disable modules from `plugins/EmsiChill/config.yml` without deleting saved module data.

## Requirements

| Requirement | Version |
|---|---|
| Java | `25` |
| Server | `Paper` |
| Paper API | `26.2` |

Spigot, Bukkit and forks that do not follow Paper behavior are not guaranteed to work.

## Main Features

- **Authentication**: register, login, password changes and optional temporary sessions.
- **Skins**: premium skins, favorites, history, random skins and `/skull`.
- **Homes and teleport**: `/home`, `/sethome`, TPA, `/back` and safe RTP.
- **Regions**: claims with members, co-owners, upgrades, settings and grief protection.
- **Graves**: item recovery on death, temporary privacy, expiration and visible grave markers.
- **Social tools**: `/sit`, `/crawl`, `/stand`, `/whereami`, `/seen` and playtime tracking.
- **Resource packs**: automatic pack sending with direct URLs and SHA-1 validation.
- **Staff tools**: vanish, staffmode, staffchat, invsee, enderchestsee, freeze, mute and warn.
- **Maintenance**: reload, status, inspect, backup, migration, EmsiChill updates and Paper build notices.

## Quick Install

1. Download the `.jar` from Releases.
2. Stop the server.
3. Put the `.jar` inside `plugins/`.
4. Start the server to generate `plugins/EmsiChill/`.
5. Review the generated configuration before opening the server publicly.
6. Run `/emsichill inspect` to catch basic data or configuration problems.

For an existing installation, create a backup first:

```mcfunction
/emsichill backup
```

## Configuration

Configuration lives inside `plugins/EmsiChill/`.

Important files:

- `config.yml`: language, prefix, active modules, audit log and update settings.
- `messages_en.yml` / `messages_es.yml`: visible plugin messages.
- `AuthenticationManager/config.yml`: registration, login, sessions and pre-login blocking.
- `Skin/config.yml`: cache, cooldowns, favorites, history and random skins.
- `Teleport/config.yml`: TPA, `/back`, RTP, delays and cancellation rules.
- `Home/config.yml`: default home limit and permission-based limits.
- `Regions/config.yml`: claims, radii, upgrades, settings and limits.
- `Graves/config.yml`: death mode, graves, privacy, expiration and visual markers.
- `Staff/config.yml`: staffchat, vanish, staffmode and moderation history.
- `ResourcePacks/config.yml`: packs sent on join, URL, SHA-1 and required/optional mode.

After changing YAML files, reload with:

```mcfunction
/emsichill reload
```

To switch the main plugin language:

```mcfunction
/emsichill language english
/emsichill language spanish
```

Restart the server when replacing the JAR, updating Paper or changing critical runtime settings.

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
| `/grave list` | Lists active graves. |
| `/grave locate <id>` | Shows and visually marks one of your grave locations. |
| `/grave recover <id>` | Recovers one of your own graves. |

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
| `/invsee <player>` | Opens inventory, armor and offhand. Editing requires an extra permission. |
| `/enderchestsee <player>` | Opens the Ender Chest. Editing requires an extra permission. |
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
| `/grave locate <player>` | Lists and visually marks graves owned by a player. |
| `/grave recover <player>` | Recovers all online graves owned by a player. |
| `/grave admin recover <player>` | Administratively recovers a player's graves. |

## Administrative configuration commands

| Command | Description |
|---|---|
| `/emsichill language <english\|spanish>` | Changes the main plugin language and reloads messages. |
| `/emsichill homes limit <amount>` | Changes the default home limit. |
| `/emsichill rtp cooldown <minutes>` | Changes the global RTP cooldown. |
| `/deathcontrol default <grave\|keep\|drop>` | Changes the default death mode. |
| `/deathcontrol <player> <grave\|keep\|drop>` | Changes a player's death mode. |
| `/auth reload` | Reloads the authentication module. |
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

## Paper/Minecraft Updates

EmsiChill can automatically check whether PaperMC has published a newer Paper build for Minecraft. When it finds one, it notifies the console and admins with `emsichill.admin.update`.

For safety, the plugin **does not replace the running server JAR**. It downloads the new `paper-*.jar`, validates size and SHA-256, and stages it in a folder so an administrator can apply it on the next restart.

Recommended flow:

```mcfunction
/emsichill update paper check
/emsichill update paper download <version> <build>
```

After downloading:

1. Stop the server.
2. Open the `server-updates/` folder.
3. Replace the current server JAR with the downloaded `paper-*.jar`.
4. Start the server again.
5. Check the console and run `/emsichill inspect`.

Main configuration in `plugins/EmsiChill/config.yml`:

```yaml
updates:
  paper:
    enabled: true
    project: paper
    include-experimental-builds: false
    automatic:
      enabled: true
      interval-minutes: 30
      notify-console: true
      notify-admins: true
    download:
      enabled: true
      directory: server-updates
      max-download-megabytes: 120
```

`include-experimental-builds: false` only accepts stable builds. Setting it to `true` also allows beta or experimental builds, which is not recommended for public servers.

## Saved Data

EmsiChill stores local YAML data inside `plugins/EmsiChill/`.

Examples:

- registered accounts and auth sessions;
- selected skins, favorites and history;
- homes, cooldowns and teleport preferences;
- regions, members, owners and settings;
- active graves;
- sanctions, mutes, warnings and staffmode;
- playtime, first seen and last seen.

Passwords are not stored as plain text. The auth module uses salted hashes for safer credential storage.

## Important Notes

- Modules disabled from `config.yml` should not keep active commands or listeners running.
- Resource packs need direct `.zip` URLs and the real SHA-1 hash of the final file.
- `/invsee` opens inventory, armor and offhand; editing requires `emsichill.invsee.modify`.
- `/grave locate <player>` and `/grave recover <player>` are admin-only variants.
- `/region delete` requires `confirm` to avoid accidental deletion.
- `/emsichill reload` does not replace a full restart when changing the JAR or updating Paper.
- Review permissions before opening a public server.

## License

EmsiChill uses the **MIT No Attribution (MIT-0)** license.

See [`LICENSE`](LICENSE) for the full terms.
