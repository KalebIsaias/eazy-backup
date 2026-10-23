<p align="center">
  <img src="src/main/resources/assets/eazybackup/icon.png" width="96" alt="Eazy Backup icon">
</p>

<h1 align="center">Eazy Backup</h1>

<p align="center">
  World backups for Fabric that feel like they were always part of the game.
</p>

<p align="center">
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-26.3-62B47A">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Fabric-DBD0B4">
  <img alt="Java" src="https://img.shields.io/badge/Java-25-E76F00">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-blue">
</p>

---

## Features

- **Automatic backups** while you play, every few minutes of play time (time spent paused does not count).
- **Backup on exit**, every time you leave a world.
- **One key backups** (`F6` by default) and a button in the pause menu.
- **Retention**: keep the newest *N* backups of each world, older ones are deleted automatically.
- **Locked backups** are kept forever and never count towards the limit.
- **Restore** a backup over the world, or **restore as a copy** to keep both.
- **Safety backup**: the current world is backed up before a restore replaces it, so a restore can be undone.
- **Recover deleted worlds**: worlds that were deleted still show up with their backups.
- **Rename** backups to remember why you made them.
- **Native UI**: every screen uses vanilla widgets, layouts, toasts and sounds.
- **Safe live backups**: the world is saved and writes are paused while the archive is created, so backups are never half written.
- **Dedicated servers**: timed and on-stop backups, `/eazybackup` command and chat notifications for operators.
- English and Brazilian Portuguese translations.

## Where to find it

| Where | How |
| --- | --- |
| Pause menu | Ender chest button next to *Back to Game* |
| Singleplayer world list | Ender chest button next to the search box. Opens the backups of the selected world, or the list of all worlds |
| Anywhere in game | `F6` creates a backup, a second key (unbound by default) opens the backups screen |
| Mod Menu | *Configure* opens the options |

Backups are regular `.zip` files stored in `.minecraft/backups/eazybackup/<world folder>/`, so they can also be opened or copied by hand.

## Options

| Option | Default | Description |
| --- | --- | --- |
| Timed Backups | On | Backs up the world regularly while you play |
| Every | 30 min | Minutes of play time between timed backups (5 to 240) |
| Backup on Exit | On | Backs up the world every time you leave it |
| Skip When Idle | On | On servers, skips timed backups when nobody played since the last one |
| Backups per World | 10 | Unlocked backups kept per world (1 to 100) |
| Compression | Balanced | None, Fast, Balanced or Smallest |
| Safety Backup | On | Backs up the current world before a restore replaces it |
| Toasts | On | Shows a notification when a backup starts and finishes |
| Notify Operators | On | On dedicated servers, tells operators about backups in chat |

The options are stored in `config/eazybackup.json`. That file also accepts a `backupDirectory` to store backups somewhere else, such as another drive.

## Commands

Requires permission level 3.

| Command | Description |
| --- | --- |
| `/eazybackup create [name]` | Creates a backup of the running world |
| `/eazybackup list` | Lists the newest backups |
| `/eazybackup status` | Shows the backup count, total size and the time until the next timed backup |
| `/eazybackup lock <backup>` | Locks a backup so it is never deleted automatically |
| `/eazybackup unlock <backup>` | Unlocks a backup |
| `/eazybackup delete <backup>` | Deletes an unlocked backup |

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.3.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api).
3. Put both jars in the `mods` folder.
4. Optional: [Mod Menu](https://modrinth.com/mod/modmenu) to open the options from the mod list.

The mod works on the client, on dedicated servers, or both. Players joining a server don't need it.

## Building

Requires JDK 25 (Gradle downloads it automatically if it is missing).

```bash
./gradlew build
```

The jar is written to `build/libs/`. Other useful tasks:

```bash
./gradlew runClient
```

```bash
./gradlew runClientGameTest
```

`runClientGameTest` starts the game, creates a world, makes, locks, prunes and restores backups, and saves screenshots of every screen in `build/run/clientGameTest/screenshots`.

## Roadmap

- Incremental backups that only store changed region files
- Backups of selected dimensions only
- Upload to cloud storage
- Restore single chunks or player data

Ideas and bug reports are welcome in the [issues](https://github.com/KalebIsaias/eazy-backup/issues).

## License

[MIT](LICENSE)
