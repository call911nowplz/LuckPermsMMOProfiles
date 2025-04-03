# MMOProfilePerms (formerly RefreshProfilePerms)

A lightweight Minecraft plugin for managing **LuckPerms permissions** attached to **MMOProfiles' fake UUIDs**, with live Bukkit attachments. Add, remove, and inspect permissions on a per-profile basis — **no relog required**.

---

## Features

- Fully supports **MMOProfiles** and **LuckPerms**
- Compatible with **proxy-mode** profiles
- Add or remove permissions without the need of re-logging
- Supports **contexts** (e.g. `server=spawn`, `world=hub`)
- Automatically resolves **player names to current profile UUIDs**
- Use UUIDs directly for offline profiles
- Includes `/listprofiles` command for easy UUID discovery (click to copy)

---

## Commands

### `/mmoperm <player|UUID> <add|remove|check|permissions> [permission] [context...]`

- `add` / `remove`: Adds or removes a permission (uses LuckPerms & Bukkit attachment)
- `check`: Executes `lp user <uuid> info`
- `permissions`: Executes `lp user <uuid> permission info`

You can use either a **player name** (uses their current active profile) or **UUID** (useful for offline users).

---

### `/listprofiles <player>`

- Lists all profiles and UUIDs for the given player
- If run in-game, **clickable UUIDs** let you copy them to clipboard
- Console output is plain text

---

## Requirements

- Minecraft **1.20+** (tested on Paper 1.21.4)
- **LuckPerms** (API 5.4+)
- **MMOProfiles**


---

## 📥 Installation

1. Drop the `.jar` file into your `/plugins` directory
2. Ensure **LuckPerms** and **MMOProfiles** are installed
3. Restart the server

---

## Why not use the LuckPerms API directly?

Because command dispatching is simple and supports LuckPerms contexts perfectly. It just works — no need for complex API integration.

---

## Support

Need help or want to suggest something? Join my [Discord server](https://discord.gg/JEqREs76yh) and open a ticket
