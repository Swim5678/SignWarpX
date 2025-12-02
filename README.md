# SignWarpX [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) [![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21+-green.svg)](https://www.minecraft.net/) [![Paper](https://img.shields.io/badge/Paper-Required-blue.svg)](https://papermc.io/)

## Introduction

SignWarpX is a teleportation plugin designed for Minecraft Paper servers. It allows players to create personal, group, or public warp points using a simple sign-based system. Whether you're building city transportation networks or creating private adventure hubs, SignWarpX makes it easy!

## Features

### Core Features
- **Sign-based warp system** - Create warp points by simply placing signs
- **Permission management** - Support for public, private, and group warps
- **Invite system** - Share private warps with specific players
- **Group management** - Manage multiple warps with group membership
- **Management interface** - GUI for managing all warp points
- **Instant teleportation** - Support for various teleport states and vehicles
- **Compass navigation** - Navigate to warps using compass with auto permission check every 5 minutes

### Advanced Teleportation
- **Mount teleportation** - Teleport while riding horses, pigs, etc.
- **Boat teleportation** - Teleport with boat and entities inside
- **Leash teleportation** - Teleport with leashed entities

### Customization
- **Custom messages** - Fully customizable plugin messages with placeholders and **Markdown syntax support**
- **Cooldown system** - Configurable teleport delay and cooldowns
- **Item consumption** - Require items for teleportation (default: Ender Pearl)
- **Warp limits** - Set max warps per player

### Group Management System
- **Group creation** - Create private groups to manage multiple warps
- **Member management** - Invite/remove group members
- **Batch management** - Manage access permissions for multiple warps at once

## Installation

### Requirements
- **Minecraft Version**: 1.21 or higher
- **Server Software**: Paper (recommended) or other Paper-based forks
- **Java Version**: Java 21 or higher

### Installation Steps
1. Download the latest [SignWarpX.jar](https://modrinth.com/plugin/signwarpx/versions)
2. Place into the `plugins` folder
3. Restart the server
4. Edit `config.yml` to customize settings (optional)

## Usage

### Create Warp Target
1. **Place a sign** at the location you want to set as the teleport destination
2. **Edit sign content**:
    ```
    Line 1: [WarpTarget] or [WPT] or [wpt]
    Line 2: Warp name (e.g., Home)
    ```

### Create Warp Sign
1. **Place a sign** at the location you want to use as the warp sign
2. **Edit sign content**:
    ```
    Line 1: [Warp] or [WP] or [wp]
    Line 2: Warp name (must already exist)
    ```

### Using Warps
1. **Hold the required item** (default: Ender Pearl)
2. **Right-click** the warp sign
3. **Wait for countdown** to complete teleportation

> ⚠️ **Important**: You must create a WarpTarget before creating a corresponding Warp sign

### Group Management

#### Creating and Managing Groups
- Create group: `/wp group create <group-name>`
- Add warp: `/wp group add <group-name> <warp-name>`
- Remove warp: `/wp group remove <group-name> <warp-name>`
- Invite member: `/wp group invite <group-name> <player-name>`
- Remove member: `/wp group uninvite <group-name> <player-name>`
- List groups: `/wp group list`
- Group info: `/wp group info <group-name>`
- Delete group: `/wp group delete <group-name>`

## Web Management Interface

SignWarpX provides a full-featured web management interface for administrators to easily manage all warp points through a browser. The web interface features modern design with intuitive operation and rich management functions.

### Enable Web Interface

Enable web functionality in `config.yml`:

```yaml
web:
  enabled: true    # Enable web interface
  port: 8080      # Web service port
```

After reloading the plugin, visit `http://your-server-ip:8080` to use the web management interface.

### Web Interface Features

#### Modern User Experience
- **Dark/Light theme** - One-click theme switching for eye protection
- **Intuitive interface** - Clean design, easy to use
- **Real-time data sync** - All operations take effect immediately

#### Core Management Features

**Warp Management**
- View detailed list of all warp points

**Smart Classification & Search**
- Category display: public, private, group warps
- Advanced filters: filter by creation time, usage frequency, etc.

#### Detailed Information Display

- Precise coordinate display (X, Y, Z)
- World name and dimension info
- Cross-dimension teleport indicator

**Permission & Access Control**
- Visibility status (public/private/group)

**Usage Statistics & Analysis**
- Teleport count statistics
- Last used time
- Popular warp rankings

**Creator & History Info**
- Creator player info
- Creation timestamp

### Technical Features

- **Based on Tailwind CSS** - Modern CSS framework, beautiful and efficient
- **JavaScript frontend** - Smooth user interaction experience
- **RESTful API** - Standardized API interface for third-party integration
- **WebSocket real-time communication** - Real-time data updates and status sync

## In-Game GUI

In addition to the web interface, SignWarpX also provides an in-game GUI management interface:

```bash
# Open management interface
/wp gui
```

#### Features
- **Warp list** - View all warp points
- **Detailed info** - View warp point details
- **Visibility status** - View permission settings

![GUI Preview](https://i.imgur.com/60JLVPC.gif)

## Configuration

### Basic Settings

```yaml
# Item required to use warp sign (set to none for no requirement)
use-item: ENDER_PEARL
# Number of items consumed when using warp sign
use-cost: 1
# Wait time before teleport (seconds)
teleport-delay: 5
# Cooldown after teleport completion (seconds)
teleport-use-cooldown: 10
# Leash related settings
max-leash-depth: 5 # Maximum leash depth (prevents overly long leash chains)

# Cross-dimension teleport settings
cross-dimension-teleport:
   # Allow cross-dimension teleport (true allows, false disables)
   enabled: true
   # OP can bypass cross-dimension restrictions (true allows, false disables)
   op-bypass: true
```

### Warp Sign Settings

```yaml
# Warp sign world info display settings
sign-world-info:
   enabled: true
   format: "§7World: §f{world-name}"
# World name display configuration
world-display-names:
   world: "Overworld"
   world_nether: "Nether"
   world_the_end: "The End"
```

### WarpTarget Settings

```yaml
# Item required to create Warp Target sign (set to none for no requirement)
create-wpt-item: DIAMOND
# Number of items consumed when creating Warp Target sign
create-wpt-item-cost: 1
# Maximum warp points each player can create (-1 for unlimited)
max-warps-per-player: 10
# OP unlimited warp creation (true means OP has no limit)
op-unlimited-warps: true
# Default warp visibility (true for private, false for public)
default-visibility: false
# Show creator info on WPT sign (true to show, false to hide)
show-creator-on-sign: true
```

### Group System Settings

```yaml
warp-groups:
   # Enable group feature (true enables, false disables)
   enabled: true
   # Maximum groups each player can create (OP not limited)
   max-groups-per-player: 5
   # Maximum warps per group (OP not limited)
   max-warps-per-group: 10
   # Maximum members per group (OP not limited)
   max-members-per-group: 20
   # Allow normal players to use group feature (true allows, false OP only)
   allow-normal-players: true
```

## Commands

| Command | Alias | Description | Permission |
|---------|-------|-------------|------------|
| `/signwarp reload` | `/wp reload` | Reload configuration | `signwarp.reload` |
| `/signwarp gui` | `/wp gui` | Open management interface | `signwarp.admin` |
| `/signwarp set <public\|private> <warp>` | `/wp set` | Set warp visibility | `signwarp.private.set` |
| `/signwarp invite add <player> <warp>` | `/wp invite add` | Invite player to use private warp | `signwarp.invite` |
| `/signwarp invite remove <player> <warp>` | `/wp invite remove` | Remove player invitation | `signwarp.invite` |
| `/signwarp invite list <warp>` | `/wp invite list` | View invite list | `signwarp.invite.list` |
| `/signwarp tp <warp>` | `/wp tp` | Direct teleport (admin) | `signwarp.tp` |
| `/signwarp owned [player]` | `/wp owned` | View own or (OP) others' warps | `signwarp.admin` |
| `/signwarp history [warp\|player]` | `/wp history` | View teleport history | `signwarp.use` |
| `/signwarp navigate start <warp>` | `/wp navigate start` | Start navigation to warp (compass points to target) | `signwarp.use` |
| `/signwarp navigate stop` | `/wp navigate stop` | Stop current navigation | `signwarp.use` |
| `/signwarp version` | `/wp version` | Check plugin version and updates | OP |
| `/signwarp group ...` | `/wp group ...` | Group related commands (see below) | Various |

#### Group Commands

| Command | Alias | Description | Permission |
|---------|-------|-------------|------------|
| `/signwarp group create <name>` | `/wp group create` | Create new group | `signwarp.group.create` |
| `/signwarp group add <group> <warp>` | `/wp group add` | Add warp to group | `signwarp.group.manage` |
| `/signwarp group remove <group> <warp>` | `/wp group remove` | Remove warp from group | `signwarp.group.manage` |
| `/signwarp group invite <group> <player>` | `/wp group invite` | Invite player to group | `signwarp.group.manage` |
| `/signwarp group uninvite <group> <player>` | `/wp group uninvite` | Remove group member | `signwarp.group.manage` |
| `/signwarp group list` | `/wp group list` | List your groups | `signwarp.group.create` |
| `/signwarp group info <name>` | `/wp group info` | Show group details | Group member or admin |
| `/signwarp group delete <name>` | `/wp group delete` | Delete group | `signwarp.group.manage` |

## Custom Messages

All plugin messages can be customized in `config.yml`, supporting Minecraft color codes:

```yaml
messages:
  teleport-success: "&aSuccessfully teleported to {warp-name}!"
  private_warp: "&cThis is a private warp, invitation required."
  invite_success: "&aInvited {player} to use warp '{warp-name}'!"
  group_created: "&aSuccessfully created group '{group-name}'!"
```

### Available Placeholders

- `{warp-name}` - Warp name
- `{player}` - Player name
- `{inviter}` - Inviter name
- `{use-item}` - Required item name
- `{use-cost}` - Item consumption amount
- `{time}` - Countdown time
- `{cooldown}` - Cooldown time
- `{visibility}` - Visibility status
- `{current}` - Player's current WarpTarget count
- `{max}` - Player's maximum WarpTarget limit
- `{group-name}` - Group name
- `{group-owner}` - Group owner name
- `{member-count}` - Group member count
- `{warp-count}` - Group warp count

## Screenshots

![Plugin Usage Example](https://i.imgur.com/XRjCmyc_d.webp?maxwidth=760&fidelity=grand)

## Support & Feedback

If you encounter any issues or have feature suggestions, feel free to:

- [Report issues](https://github.com/Swim5678/SignWarpX/issues)
- [Request features](https://github.com/Swim5678/SignWarpX/issues)
- ⭐ Star the repo to support development

## License

This project is licensed under the [MIT License](https://github.com/siriusbks/SignWarp/blob/master/LICENSE).

## 🙏 Credits

This project is forked from [siriusbks/SignWarp](https://github.com/siriusbks/SignWarp). Thanks to the original author for their excellent work.
