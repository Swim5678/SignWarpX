# SignWarpX [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) [![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21+-green.svg)](https://www.minecraft.net/) [![Paper](https://img.shields.io/badge/Paper-Required-blue.svg)](https://papermc.io/)
Here’s the English translation of your document:

---

# SignWarpX [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) [![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.4+-green.svg)](https://www.minecraft.net/) [![Paper](https://img.shields.io/badge/Paper-Required-blue.svg)](https://papermc.io/)

## Introduction

SignWarpX is a teleportation plugin designed specifically for Minecraft Paper servers. It allows players to easily create personal, group, or public warp anchors through a simple sign system. Whether you’re building a transportation network between cities or creating a private adventure hub, everything can be achieved effortlessly!

## Features

### Core Features

* **Simple Sign System** – Easily create warp anchors by placing signs
* **Permission Management** – Supports public, private, and group warp anchors
* **Invitation System** – Share private warp anchors with specific players
* **Group Management** – Manage group warp anchors and members
* **Admin Interface** – GUI for managing all warp anchors
* **Instant Teleportation** – Supports multiple teleport states and vehicles

### Advanced Teleportation Support

* **Mount Teleportation** – Teleport together while riding horses, pigs, etc.
* **Boat Teleportation** – Includes entities (animals, mobs) in boats
* **Leash Teleportation** – Entities on leads will teleport along with the player

### Customization Options

* **Custom Messages** – Fully customizable plugin messages with placeholders
* **Cooldown System** – Configurable teleport delay and cooldown
* **Item Consumption** – Configurable required item for teleportation (default: Ender Pearl)
* **Custom Warp Limits** – Configurable max number of warp anchors per player

### Group Management System

* **Group Creation** – Create private groups to manage multiple warp anchors
* **Member Management** – Invite/remove group members
* **Batch Management** – Manage multiple warp anchors’ access permissions at once

## Installation Guide

### Requirements

* **Minecraft Version**: 1.21 or higher
* **Server Software**: Paper (recommended) or Paper-based forks
* **Java Version**: Java 21 or higher

### API Version Support

SignWarpX automatically switches API versions:

* **1.21.6 and above** → Uses 1.21.6 API
* **1.21–1.21.5** → Uses 1.21 API

The plugin detects the server version and selects the correct API automatically. No manual configuration required.

### Installation Steps

1. Download the latest [SignWarpX.jar](https://modrinth.com/plugin/signwarpx/versions)
2. Place the file into your server’s `plugins` folder
3. Restart the server
4. Edit `config.yml` to customize settings (optional)

## Usage

### Creating a Warp Target

1. **Place a sign** where you want the teleport destination
2. **Edit the sign text**:

   ```
   Line 1: [WPT] or [wpt]
   Line 2: Warp name (e.g. Home)
   ```

### Creating a Warp Sign

1. **Place a sign** where you want the teleport sign
2. **Edit the sign text**:

   ```
   Line 1: [WP] or [wp]
   Line 2: Existing warp name
   ```

### Using Warp

1. **Hold the required item** (default: Ender Pearl)
2. **Right-click** the warp sign
3. **Wait for countdown** → teleport completes

> ⚠️ **Important**: A WarpTarget must be created first before making a Warp sign.

### Group Management

* Create Group: `/wp group create <group>`
* Add Warp: `/wp group add <group> <warp>`
* Remove Warp: `/wp group remove <group> <warp>`
* Invite Member: `/wp group invite <group> <player>`
* Remove Member: `/wp group uninvite <group> <player>`
* List Groups: `/wp group list`
* Group Info: `/wp group info <group>`
* Delete Group: `/wp group delete <group>`

## Web Management Interface

SignWarpX provides a full-featured web management interface that allows admins to manage all warps easily via browser. The UI features a modern design with intuitive controls and rich functionality.

### Enabling Web Interface

In `config.yml`:

```yaml
web:
  enabled: true
  port: 8080
```

Reload the plugin and visit `http://your-server-ip:8080`.

### Web UI Features

* **Dark/Light Mode** – One-click theme switch
* **Intuitive UI** – Clean and user-friendly design
* **Real-time Sync** – All actions take effect instantly

**Core Management**

* Full warp list with details

**Smart Search & Filtering**

* Filter by type: Public/Private/Group
* Advanced filters: creation time, usage frequency

**Detailed Info Display**

* Exact coordinates (X, Y, Z)
* World and dimension info
* Visibility state (public/private/group)
* Usage stats, last used time, popular warp rankings
* Creator info & timestamps

**Tech Stack**

* Tailwind CSS
* JavaScript frontend
* RESTful API for integrations
* WebSocket for real-time updates

## In-Game GUI Management

```bash
/wp gui
```

**Features**

* Warp list
* Warp details
* Visibility status

![Preview](https://i.imgur.com/60JLVPC.gif)

## Configuration

Includes:

* API version auto-selection
* Customizable teleport costs, cooldowns, leash depth
* Cross-dimension teleport controls
* Warp sign display customization
* WarpTarget creation requirements
* Group system configuration

(See full YAML examples in original document.)

## Commands

| Command                                  | Alias              | Description                   | Permission             |
| ---------------------------------------- | ------------------ | ----------------------------- | ---------------------- |
| `/signwarp reload`                       | `/wp reload`       | Reload config                 | `signwarp.reload`      |
| `/signwarp gui`                          | `/wp gui`          | Open GUI                      | `signwarp.admin`       |
| `/signwarp set <public\|private> <warp>` | `/wp set`          | Set warp visibility           | `signwarp.private.set` |
| `/signwarp invite <player> <warp>`       | `/wp invite`       | Invite player to private warp | `signwarp.invite`      |
| `/signwarp uninvite <player> <warp>`     | `/wp uninvite`     | Remove player invite          | `signwarp.invite`      |
| `/signwarp list-invites <warp>`          | `/wp list-invites` | View invite list              | `signwarp.invite.list` |
| `/signwarp tp <warp>`                    | `/wp tp`           | Direct teleport (admin)       | `signwarp.tp`          |
| `/signwarp list-own [player]`            | `/wp list-own`     | View own or others’ warps     | `signwarp.admin`       |
| `/signwarp group ...`                    | `/wp group ...`    | Group commands                | Group permissions      |

## Message Customization

All plugin messages can be customized in `config.yml`.
Supports Minecraft color codes.

Example:

```yaml
messages:
  teleport-success: "&aTeleported to {warp-name}!"
  private_warp: "&cThis is a private warp. You need an invitation."
  invite_success: "&aInvited {player} to warp '{warp-name}'!"
  group_created: "&aGroup '{group-name}' created successfully!"
```

**Available Placeholders**: `{warp-name}`, `{player}`, `{inviter}`, `{use-item}`, `{use-cost}`, `{time}`, `{cooldown}`, `{visibility}`, `{current}`, `{max}`, `{group-name}`, `{group-owner}`, `{member-count}`, `{warp-count}`

## Screenshots

![Usage Example](https://i.imgur.com/XRjCmyc_d.webp?maxwidth=760\&fidelity=grand)

## Support & Feedback

If you encounter issues or have feature requests:

* [Report issues](https://github.com/Swim5678/SignWarpX/issues)
* [Request features](https://github.com/Swim5678/SignWarpX/issues)
* Give us a ⭐ to support development

## License

This project is licensed under the [MIT License](LICENSE).

## 🙏 Credits

This project is forked from [siriusbks/SignWarp](https://github.com/siriusbks/SignWarp). Thanks to the original author for the excellent work.