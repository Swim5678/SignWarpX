# SignWarpX [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) [![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.8+-green.svg)](https://www.minecraft.net/) [![Paper](https://img.shields.io/badge/Paper-Required-blue.svg)](https://papermc.io/)

## Introduction

**SignWarpX** is a teleportation plugin for **Minecraft Paper servers**.
It lets players create **personal or public warps** using a simple sign system.
Perfect for city networks or private adventure hubs.

## Features

### Core Features
* **Sign-based warp system** - Easily create warp targets and teleport signs
* **Public / Private warps** - with permission control
* **Invite system** - for sharing private warps with specific players
* **In-game GUI** & **Web interface** - for full administration
* **Compass navigation** - Navigate to warps using a compass with auto permission check every 5 minutes
* **Transfer confirmation setting** - Prompts players to confirm teleportation, with the ability to skip the transfer delay after confirmation

### Teleport Scrolls (New!)
* **Craftable Teleport Scrolls** - Create blank scrolls and bind them to warp targets via signs
* **Combine in Anvils** - Combine bound scrolls in anvils to increase their uses
* **Daily Limits** - Configurable daily usage limits for players

### Advanced Teleportation
* **Mount teleportation** - Teleport while riding horses, pigs, etc.
* **Boat teleportation** - Teleport with boats and entities inside
* **Leash teleportation** - Teleport with leashed entities

### Customization
* **Customizable messages** with placeholders and **Markdown syntax support**
* **Cooldown system** - Configurable teleport delay and cooldowns
* **Item consumption** - Require items for teleportation (default: Ender Pearl)
* **Warp limits** - Set max warps per player

## Requirements

* **Minecraft**: 1.21.8+
* **Server**: Paper or Paper-based fork
* **Java**: 21+

## Installation

1. Download [SignWarpX.jar](https://modrinth.com/plugin/signwarpx/versions)
2. Place into `plugins/` folder
3. Restart server
4. (Optional) Edit `config.yml`

## Usage

### Create Warp Target

```text
[WPT] or [wpt]
Home
```

### Create Warp Sign

```text
[WP] or [wp]
Home
```

### Warp

* Hold the required item (default: Ender Pearl)
* Right-click warp sign → Wait for countdown → Teleport

**Note**: You must create a WarpTarget before creating a Warp sign.

### Using Teleport Scrolls
* **Crafting**: Craft a blank Teleport Scroll (default recipe enabled).
* **Binding**: Right-click a `[WarpTarget]` sign with a blank scroll to bind it to that location.
* **Using**: Right-click to consume a use and teleport.

## Web Interface

SignWarpX provides a full-featured Web management interface for administrators.

### Features
* **Dark/Light mode** - One-click theme switching
* **Real-time sync** via WebSocket
* **Smart filters & search** - Filter by type (public/private), creation time, usage frequency
* **RESTful API support** for third-party integration
* **Detailed info display** - Coordinates, world name, dimension, visibility, usage stats, creator info

Enable in `config.yml`:

```yaml
web:
  enabled: true
  port: 8080
```

Then open: `http://your-server-ip:8080`

## In-Game GUI

```bash
/wp gui
```

* Warp list & details
* Visibility & permissions

![Preview](https://i.imgur.com/60JLVPC.gif)

## Commands

| Command | Alias | Description | Permission |
| :--- | :--- | :--- | :--- |
| `/signwarp reload` | `/wp reload` | Reload config | `signwarp.reload` |
| `/signwarp gui` | `/wp gui` | Open GUI | `signwarp.admin` |
| `/signwarp set <public\|private> <warp>` | `/wp set` | Set warp visibility | `signwarp.private.set` |
| `/signwarp invite add <player> <warp>` | `/wp invite add` | Invite player | `signwarp.invite` |
| `/signwarp invite remove <player> <warp>` | `/wp invite remove` | Remove player invitation | `signwarp.invite` |
| `/signwarp invite list <warp>` | `/wp invite list` | View invite list | `signwarp.invite.list` |
| `/signwarp tp <warp>` | `/wp tp` | Admin teleport | `signwarp.tp` |
| `/signwarp owned [player]` | `/wp owned` | View own or (OP) others' warps | `signwarp.admin` |
| `/signwarp history [warp\|player]` | `/wp history` | View teleport history | `signwarp.use` |
| `/signwarp navigate start <warp>` | `/wp navigate start` | Start compass navigation | `signwarp.use` |
| `/signwarp navigate stop` | `/wp navigate stop` | Stop navigation | `signwarp.use` |
| `/signwarp scroll give <player> <type> ...` | `/wp scroll give` | Give blank or bound scroll | OP |
| `/signwarp scroll reset <player>` | `/wp scroll reset` | Reset player's scroll daily usage | OP |
| `/signwarp clear warp [confirm]` | `/wp clear warp` | Clear all warps (OP only) | OP |
| `/signwarp clear histories [confirm]` | `/wp clear histories` | Clear teleport histories (OP) | OP |
| `/signwarp version` | `/wp version` | Check plugin version | OP |

## Custom Messages

Configurable in `config.yml` with placeholders:

```yaml
messages:
  teleport-success: "&aTeleported to {warp-name}!"
  private_warp: "&cThis is a private warp, invitation required."
  invite_success: "&aInvited {player} to use warp '{warp-name}'!"
```

### Available Placeholders
`{warp-name}`, `{player}`, `{inviter}`, `{use-item}`, `{use-cost}`, `{time}`, `{cooldown}`, `{visibility}`, `{current}`, `{max}`

## Screenshots

![Usage Example](https://i.imgur.com/XRjCmyc_d.webp?maxwidth=760&fidelity=grand)

## Support

* [Report issues](https://github.com/Swim5678/SignWarpX/issues)
* [Request features](https://github.com/Swim5678/SignWarpX/issues)
* Star the repo to support development

## License

Licensed under [MIT](LICENSE).

## Credits

Forked from [siriusbks/SignWarp](https://github.com/siriusbks/SignWarp).
Thanks to the original author for their great work.
