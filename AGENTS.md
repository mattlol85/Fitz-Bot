# AGENTS.md — Fitz-Bot Codebase Guide

## Architecture Overview

This is a **hybrid Spring Boot + Discord bot** application. A single JVM process runs:
1. A Spring Boot web server (port 8080) for REST management
2. A JDA-based Discord bot started automatically via `CommandLineRunner` in `Main.java`

The bot auto-starts on app launch (`Main.run()` → `BotService.startBot()`). `BotService.stopBot()` shuts down the **entire Spring application**, not just JDA.

## Key Components

| Layer | Class | Role |
|---|---|---|
| Entry point | `Main.java` | Spring Boot app + auto-starts bot |
| Bot lifecycle | `BotService.java` | JDA setup, command registration, guild management |
| Voice tracking | `listener/LoginListener.java` | Handles `GuildVoiceUpdateEvent`, tracks joins, fires milestones |
| Discord commands | `commands/ConfigCommands.java` | `/setbotchannel`, `/getbotchannel` (plain class, not Spring bean) |
| Media commands | `commands/JoenetCommands.java` | `/joenet download` multi-step UI (Spring `@Component`) |
| Persistence | `data/GuildConfigDatabase.java` | File-backed JSON store → `data/guild_configs.json` |
| Data model | `data/model/GuildConfig.java` | Per-guild state: botChannelId, milestones, userJoinCounts, trackingStartDate |
| Media APIs | `service/RadarrService.java`, `SonarrService.java` | `RestTemplate` calls to Radarr/Sonarr |
| REST API | `controller/BotController.java` | `/bot/*` endpoints for bot management |

## Critical Design Patterns

**Spring bean vs. plain instantiation**: `JoenetCommands` is `@Component` (needs `@Autowired` services). `ConfigCommands` and `LoginListener` are created with `new` inside `BotService`, so they have **no access to Spring beans**. `GuildConfigDatabase` is instantiated directly inside `LoginListener`, not autowired.

**Command registration**: Commands are registered **per-guild** on startup for instant availability, plus globally as fallback. Commands are defined via static `getCommands()` returning `SlashCommandData[]`. Both command classes contribute to a merged list in `BotService`.

**Multi-step Discord UI** (`JoenetCommands`): Interaction flow uses component ID routing with `joenet:` prefix:
- Slash command → Button (`joenet:movies` / `joenet:tv`) → Modal (`joenet:search:movie`) → Select menu (`joenet:select:movie`) → download
- Season selection uses: `joenet:seasons:{tvdbId}:{seriesTitle}` as the select menu ID
- Results are capped at 5 to stay within Discord's select menu limit; specials (season 0) are filtered out.

**Voice join detection**: Only counts joining *from outside* a voice channel — channel moves are ignored:
```java
event.getChannelLeft() == null && event.getChannelJoined() != null
```

**Tracking start date**: `GuildConfig.initializeTrackingIfNeeded()` lazily sets `trackingStartDate` on the first `incrementUserJoinCount` call. This persists the "as of" date used in milestone embeds.

**JSON persistence**: `GuildConfigDatabase` reads/writes `data/guild_configs.json` on every mutation. `JsonUtils.MAPPER` (Jackson with `JavaTimeModule`) handles `LocalDateTime` serialization as ISO 8601. Key is `Long` (guild ID), value is `GuildConfig`.

## Developer Workflows

```bash
# Build & run
./gradlew bootRun

# Run all tests (parallel execution configured in build.gradle)
./gradlew test

# Build fat JAR
./gradlew clean build
# Output: build/libs/FitzcordBot-<version>.jar
```

**REST API for local dev** (see `bot-api.http`):
```
GET  /bot/status
POST /bot/startup
POST /bot/shutdown
POST /bot/force-update-commands
POST /bot/register-guild-commands/{guildId}
POST /bot/reset-join-counts/{guildId}
```

Actuator endpoints available: `health`, `info`, `loggers`, `logfile`, `prometheus`. Log file: `logs/fitz-bot.log`.

## Configuration

All secrets via environment variables with fallback defaults in `application.properties`:
- `DISCORD_BOT_TOKEN`
- `JOENET_HOST`, `JOENET_RADARR_APIKEY`, `JOENET_SONARR_APIKEY`
- `JOENET_RADARR_QUALITY_PROFILE_ID`, `JOENET_RADARR_ROOT_FOLDER_PATH`
- `JOENET_SONARR_QUALITY_PROFILE_ID`, `JOENET_SONARR_ROOT_FOLDER_PATH`

Use `application-dev.properties` for local overrides (activate with `--spring.profiles.active=dev`).

## Testing Conventions

- Tests use **JUnit 5 + Mockito**. `@Value`-injected fields are set via `ReflectionTestUtils.setField()`.
- Services with `@PostConstruct` init (e.g., `SonarrService.init()`) must be called manually in `@BeforeEach` after setting fields.
- Test config for parallel execution is in `src/test/resources/junit-platform.properties`.
- No Spring context is loaded for unit tests — pure Mockito mocking only.

## Milestone Defaults

Defined in `util/Constants.java`:
```java
public static final int[] DEFAULT_MILESTONES = {1, 5, 10, 25, 50, 100, 250, 500, 1000};
```
Per-guild overrides stored in `GuildConfig.milestones` (null = use defaults).

