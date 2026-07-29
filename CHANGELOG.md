# Changelog

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
