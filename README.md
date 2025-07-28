# Fitz-Bot

A Discord bot built with Java and Spring Boot that tracks voice channel joins and sends milestone congratulations. The bot features automatic channel cleanup and configurable per-server settings.

## Features

- 🎤 **Voice Join Tracking** - Tracks when users join voice channels (not moves between channels)
- 🏆 **Milestone Celebrations** - Sends congratulatory messages at configurable milestones (default: 1, 5, 10, 25, 50, 100, 250, 500, 1000 joins)
- 🗑️ **Smart Channel Cleanup** - Automatically deletes empty temporary voice channels
- 🏠 **Multi-Server Support** - Each Discord server has its own isolated configuration and data
- ⚙️ **Configurable Bot Channels** - Set which channel receives milestone notifications per server
- 🔧 **REST API Management** - Full bot control via HTTP endpoints
- 📊 **In-Memory Statistics** - Fast voice join counting without persistent storage overhead

## Tech Stack

- **Java 17+** with Spring Boot 3.2.6
- **JDA 5.0.0-beta.21** (Java Discord API)
- **Jackson** for JSON processing
- **Lombok** for clean POJOs
- **Gradle** for build management

## Prerequisites

- Java 17 or higher
- Discord Bot Token
- Gradle (included via wrapper)

## Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd Fitz-Bot
```

### 2. Configure Discord Bot Token
Create or edit `src/main/resources/application.properties`:
```properties
discord.bot.token=YOUR_DISCORD_BOT_TOKEN_HERE
server.port=8080
```

### 3. Create Discord Application
1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a new application
3. Go to "Bot" section and create a bot
4. Copy the bot token to your `application.properties`
5. Under "OAuth2 > URL Generator":
   - Select "bot" and "applications.commands" scopes
   - Select "Send Messages", "Use Slash Commands", "Read Message History", "View Channels" permissions
   - Use the generated URL to invite the bot to your server

### 4. Build and Run
```bash
# Build the project
./gradlew clean build

# Run the application
./gradlew bootRun
```

The bot will start automatically when the application launches and register slash commands immediately.

## Usage

### Slash Commands

#### `/setbotchannel #channel`
Set the channel where milestone messages will be sent.
- **Permission Required**: Manage Server
- **Example**: `/setbotchannel #bot-announcements`

#### `/getbotchannel`
Shows the currently configured bot channel for your server.

### REST API Endpoints

The bot provides HTTP endpoints for management:

```http
# Get bot status
GET http://localhost:8080/bot/status

# Manually start the bot
POST http://localhost:8080/bot/startup

# Stop the bot
POST http://localhost:8080/bot/shutdown

# Force update commands for all servers
POST http://localhost:8080/bot/force-update-commands

# Register commands for a specific server (instant)
POST http://localhost:8080/bot/register-guild-commands/{guildId}
```

## Configuration

### Per-Server Settings
Each Discord server can configure:
- **Bot Channel**: Where milestone messages are sent
- **Custom Milestones**: Override default milestone thresholds (future feature)

### Data Storage
- **Voice Join Counts**: Stored in memory (reset on restart)
- **Server Configurations**: Saved to `data/guild_configs.json`

### Channel Deletion Rules
The bot automatically deletes empty voice channels that match these patterns:
- Names starting with `temp-` or `room-`
- Names containing `private`
- Names containing numbers (user-created rooms)

## Development

### Project Structure
```
src/main/java/org/fitznet/
├── Main.java                 # Spring Boot application entry point
├── BotController.java        # REST API endpoints
├── service/
│   └── BotService.java       # Core bot management logic
├── commands/
│   └── ConfigCommands.java   # Slash command handlers
├── listener/
│   └── LoginListener.java    # Voice event processing
├── data/
│   ├── GuildConfigDatabase.java  # Configuration persistence
│   └── model/
│       └── GuildConfig.java      # Configuration POJO
└── util/
    ├── EmbedUtil.java        # Discord embed utilities
    ├── JsonUtils.java        # JSON processing utilities
    └── Constants.java        # Application constants
```

### Running Tests
```bash
./gradlew test
```

### Code Style
- Uses Lombok for reducing boilerplate
- Comprehensive JavaDoc documentation
- Slf4j for logging
- Builder pattern for complex objects

## Troubleshooting

### Slash Commands Not Appearing
1. Use the force update endpoint: `POST /bot/force-update-commands`
2. Or register for specific server: `POST /bot/register-guild-commands/{guildId}`
3. Global commands can take up to 1 hour to appear

### "Application did not respond" Error
- Check application logs for detailed error messages
- Ensure bot has proper permissions in your Discord server
- Verify the bot token is correct in `application.properties`

### Bot Not Starting Automatically
- Check that `discord.bot.token` is set in `application.properties`
- Review startup logs for connection issues
- Ensure Discord bot token has not expired

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Add tests if applicable
5. Commit your changes (`git commit -m 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

## Support

If you encounter any issues or have questions:
1. Check the troubleshooting section above
2. Review the application logs
3. Open an issue on GitHub with detailed information about your problem

---

Built by [@Mattlol85](https://github.com/Mattlol85)
