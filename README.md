# Chess Coach

Play chess against a bot in your browser and learn from every move. The app rates each of your moves, much like
chess.com does, and a chat coach explains why a move was weak and what would have been better.

The interface and the coach's default language are Russian. The coach replies in the language you write in.

## Features

- **Play the bot:** four difficulty levels, as White or Black.
- **Move review:** your moves and the bot's are rated from "best move" to "blunder", with an evaluation bar and a material count.
- **Sound:** separate sounds for a move, check and checkmate, with a mute button. The sounds come from lichess (AGPLv3+, see `src/main/resources/static/sounds/NOTICE.md`).
- **Chat coach:** explains moves using the engine's calculations and recognizes moves in your question,
  including Russian notation such as "почему не Кf3?". Click an earlier move, yours or the bot's, to ask about it.
- **Two-step hints:** the first click highlights the piece, the second draws an arrow.
- **Premoves:** queue several moves while the bot is thinking, choosing the piece for a promotion. Right-click the board to cancel the queue.
- **Instant undo:** takes back your last move and the bot's reply.
- **Online play:** accounts, friends by nickname, challenges, and "find an opponent" matchmaking. Moves arrive live, premoves work, and you can resign. Engine review, hints, undo and the coach are off in these games.
- **Lessons:** back-rank mate, knight fork, ladder mate, queen mate, king and pawn endgame, and opening principles.

## Getting started

You need Java 17 or later.

```bash
export OPENAI_API_KEY=your_key   # only needed for the chat
./mvnw spring-boot:run
```

Then open http://localhost:8080.

Create a key at https://platform.openai.com/api-keys. API usage is paid and billed separately from a ChatGPT
subscription. Without a key, everything except the chat works.

Run the tests with `./mvnw test`.

## Playing over the network

By default the server only accepts connections from this computer. To let others join:

```bash
SERVER_ADDRESS=0.0.0.0 ./mvnw spring-boot:run
```

Other players open `http://<your-ip>:8080` and create an account on the "Онлайн" tab. Port 8080 must be open in your
firewall. Players outside your network need a VPN or a public address. To test two accounts on one computer, use a
private window for the second one, because a browser profile holds one session.

Guests can play the bot and each other, but the coach chat answers only requests from the server's own computer. Do
not put the app behind a tunnel or reverse proxy without the public server settings below: proxied requests look
local, so the chat would spend your API credit for anyone. Traffic is plain HTTP, so use a password you don't use anywhere else.

Accounts, friends, sessions and games between people are saved in a local H2 file under `~/.chess-coach/`.

### Public server

For a server on the internet, run the app behind an HTTPS proxy with PostgreSQL and these variables:

| Variable | Value |
|---|---|
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | PostgreSQL connection |
| `ONLINE_INVITE_CODE` | Required to create an account |
| `ONLINE_REQUIRE_LOGIN=true` | Bot games and chat need a login |
| `ONLINE_SECURE_COOKIE=true` | Session cookie over HTTPS only |
| `COACH_LOCAL_ONLY=false` | Lets signed-in players use the chat |

Chat is limited per player by `coach.max-questions-per-hour`. Five wrong passwords lock a nickname for 15 minutes.

## Architecture

Dependencies point inward: the web layer knows about the application logic, the logic knows nothing about the web,
and the rules and engine depend on nothing else, not even Spring.

```
web ──> coach ──> game ──> engine ──> chess
 └───────────────^
```

| Package | Responsibility |
|---|---|
| `chess` | Rules: positions, move generation, FEN, SAN. The immutable `Board` is verified with perft tests |
| `engine` | Alpha-beta search, position evaluation, difficulty levels |
| `game` | Game state, move review (`MoveReview`, `MoveQuality`), `GameService`, storage behind `GameRepository` |
| `coach` | Facts for the model (`CoachPromptBuilder`), conversation history, provider selection |
| `coach.provider` | `CoachModel` implementations for OpenAI and Anthropic |
| `online` | Accounts, friends, challenges, matchmaking and live updates for games between people |
| `web` | REST controllers, streaming chat responses (SSE), DTOs, mapping errors to HTTP responses |
| `config` | Typed configuration and engine registration |

The frontend uses plain ES modules with no build step, in `src/main/resources/static/js`:

| Module | Responsibility |
|---|---|
| `state` | UI state |
| `chess` | Board calculations for display |
| `api` | Server requests |
| `board-view`, `panels-view` | Rendering |
| `game-actions`, `chat` | User actions |
| `online` | Online tab: login, friends, matchmaking, live updates |
| `main` | Wiring |

### How moves are rated

The engine scores every legal move in the position. A move is rated by how much winning chance it loses compared
with the best move:

| Winning chance lost | Rating |
|---|---|
| 0% | Best move |
| under 2% | Excellent |
| under 10% | Good |
| under 20% | Inaccuracy |
| under 30% | Mistake |
| 30% or more | Blunder |

A position with only one legal move is rated "forced". Scores are stored with each move, so undo needs no new analysis.

### How the coach answers

Each question is sent with the position, the rating, the scores of all moves, and likely continuations from the
engine. The model explains these calculations instead of working out variations itself.

## Configuration

Settings are in `src/main/resources/application.properties`:

| Setting | Description |
|---|---|
| `coach.provider` | `auto`, `openai`, or `anthropic`. `auto` uses OpenAI if its key is set, otherwise Anthropic |
| `coach.openai.model` | OpenAI model, `gpt-4o-mini` by default. Set `OPENAI_BASE_URL` to use a different API address |
| `coach.anthropic.model` | Anthropic model. The key goes in `ANTHROPIC_API_KEY` |
| `analysis.depth`, `analysis.time-limit-ms` | Depth and time limit for move analysis |

## Limitations

- Bot games and chats are kept in memory and are lost on restart.
- Online games have no clocks and no draw offers, and a player who leaves does not lose automatically.
- The built-in engine is much weaker than Stockfish, so ratings are approximate, especially in complex positions.
