# Changelog

## EmsiChill 5.2.1 - 2026-07-31

### Changed

- Updated the plugin version to `5.2.1`.
- Kept this release as the current package for all changes added after `5.1.5`.
- Changed `/emsichill language <english|spanish>` to save a personal language per player.
- Added `player-languages.yml` for per-player language preferences.
- Added `/graves` as an alias for `/grave`.
- Restricted `/grave` and `/graves` to administrators with `emsichill.grave.admin`.
- Rebuilt grave markers as upright headstones with a stepped stone base, plaque and restrained soul-lantern accent.
- Removed the visible chest from graves; an invisible protected interaction block now keeps opening and collection reliable.
- Reduced each grave label to the owner's name and removed the instruction line, dark text background and excessive effects.
- Simplified grave command output to a restrained gray, white and error-color palette.
- Removed redundant chat confirmations when opening `/invsee` or `/enderchestsee`.
- Improved the `/invsee` layout with clean, unlabeled separators for inventory, hotbar, armor and offhand.

### Fixed

- Fixed the English private-grave message so it includes the remaining private time.
- Fixed Spanish message accents in visible help, language, Paper update and grave text.
- Allowed `/invsee` and `/enderchestsee` to target yourself when no player name is provided.
- Fixed editable `/invsee` shift-click transfers so admins can take items from another player's inspected inventory.
- Fixed editable `/invsee` shift-click transfers so admins can move items from their inventory into the inspected player inventory without using the fake armor/offhand slots.
- Fixed editable `/enderchestsee` shift-click transfers so admins can move items in and out of another player's Ender Chest.
- Kept self `/invsee` read-only to avoid duplicating or deleting items while the inspected inventory and the real player inventory belong to the same admin.
- Fixed `/grave` and `/graves` appearing in command suggestions for players without administrative permission.
- Fixed grave interactions running once per hand and sending the private-grave warning twice.
- Fixed grave displays disappearing after a server restart or chunk unload while the grave itself remained usable.

## EmsiChill 5.2.0 - 2026-07-29

### Added

- Added `/emsichill language <english|spanish>` to switch the main plugin language and reload messages.
- Added English as the default language for new installations.
- Added Spanish as the secondary selectable language.
- Added EmsiChill release update checks for new plugin versions published on GitHub.
- Added admin update actions to check, view changes, stage and ignore new EmsiChill releases.
- Added Paper/Minecraft update checks through PaperMC:
  - `/emsichill update paper check`;
  - `/emsichill update paper download <version> <build>`;
  - `/emsichill update paper ignore <version> <build>`.
- Added safe Paper build staging into `server-updates/` instead of replacing the running server JAR.
- Added Paper build validation with SHA-256 and configurable stable/experimental build handling.
- Added admin support for `/grave locate <player>` to list and visually mark graves owned by another player.
- Added admin support for `/grave recover <player>` to recover all graves owned by an online player.
- Added configurable grave visuals:
  - creation particle and sound effects;
  - temporary locate marker particles;
  - configurable locate marker duration.
- Added the new permission `emsichill.admin.language`.

### Changed

- Updated the plugin version to `5.2.0`.
- Converted README, command documentation, plugin metadata, config comments and most visible console messages to English.
- Kept command names in English only, regardless of the selected message language.
- Updated `/emsichill help` so staff and admin categories only appear to users with matching permissions.
- Updated generated command documentation to use English section names and English command placeholders.
- Updated `/auth` in `plugin.yml` to require `emsichill.auth.admin`, so it is hidden from players without permission.
- Simplified the public `/emsichill` usage text to avoid exposing administrative subcommands through Bukkit help.
- Simplified `/back` usage text so the admin-only player variant is not shown to normal users.
- Updated region menus, grave titles and visible module text to use English wording.
- Improved grave markers with a richer memorial style: floating title, soul lantern, side chains and stronger locate particles.

### Fixed

- Fixed inventory inspection bypasses where items could be moved through shift-click and other unsafe actions.
- Blocked unsafe inspection actions such as:
  - shift-click transfers;
  - hotbar number-key swaps;
  - offhand swaps;
  - item drops;
  - collect-to-cursor;
  - creative clone actions.
- Fixed `/grave locate <player>` not showing another player's graves to administrators.
- Fixed `/grave recover <player>` not routing through the administrative recovery flow.
- Fixed tab completion so administrative suggestions are hidden from users without the required permission.
- Fixed README generator tests to match the new English command documentation.

### Documentation

- Rebuilt `README.md` around EmsiChill 5.2.0.
- Documented the language command.
- Documented EmsiChill plugin update commands and the safe staged-update flow.
- Documented the full Paper/Minecraft update flow, including `server-updates/`, SHA-256 validation, stable builds and why the server JAR is not replaced while running.
- Documented the `updates.paper` configuration block.
- Documented admin-only grave player variants.
- Documented that `/invsee` includes inventory, armor and offhand, with editing gated behind `emsichill.invsee.modify`.
- Updated resource pack configuration comments with clearer SHA-1 and resend behavior notes.
- Updated module configuration files to use English comments.

### Build

- Verified with `mvn test`.
- Result: `55` tests passed, `0` failures.
- Fixed the README generator flow so `mvn package -DskipTests` regenerates documentation normally without requiring `-Dexec.skip=true`.
- Built release artifact:
  - `target/EmsiChill-5.2.0.jar`
