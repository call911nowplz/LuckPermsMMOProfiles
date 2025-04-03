# RefreshProfilePerms

A simple Minecraft plugin that allows you to **add and remove permissions for MMOProfiles fake UUIDs** on-the-fly — no relog required.

### Features

- Supports **MMOProfiles** (proxy mode or not)
- Works with **LuckPerms** directly via command dispatch
- Adds/removes permissions **live** using **Bukkit attachments**
- Optional **context support** (e.g., `server=rpg`, `world=spawn`)
- Uses **player name** and resolves it to their current active profile UUID automatically

### 🛠 Requirements

- Paper 1.21.4+
- LuckPerms (API 5.4+)
- MMOProfiles (with Profile-API)

- I have only tested this with MMOProfilesExtraPerms (https://github.com/CKATEPTb-minecraft/MMOProfilesExtraPerms)
  I am not sure if it works without it


### Installation

1. Place the plugin `.jar` into your `plugins` folder.
2. Ensure LuckPerms and MMOProfiles are installed and working.
3. Restart the server.

### 💬 Commands

/tempperm <player> <add|remove> <permission> [context...]



I am aware i can use LuckPerms API directly instead of commands, But this works just as good so i didn't bother

For support or questions, Join my [Discord](https://discord.gg/JEqREs76yh) and open a ticket
