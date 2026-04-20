# 🚀 Asteroid Shooter

A simple asteroid shooter game built with Java and [libGDX](https://libgdx.com/).

---

## Controls

| Key | Action |
|-----|--------|
| `←` / `→` | Rotate ship |
| `↑` | Thrust |
| `SPACE` | Shoot |
| `R` | Restart (on Game Over) |

---

## Requirements

- Java 17+
- Gradle 8+

---

## Running the Game

```bash
git clone https://github.com/ishara9/astroidgame.git
cd asteroid-game
./gradlew desktop:run
```

On **Windows** use `gradlew.bat desktop:run`.

---

## Building a JAR

```bash
./gradlew desktop:jar
```

Output: `desktop/build/libs/asteroid-game.jar`

Run it with:
```bash
java -jar desktop/build/libs/asteroid-game.jar
```

---

## Project Structure

```
asteroid-game/
├── core/                   # Game logic (platform-independent)
│   └── src/main/java/com/example/asteroids/
│       └── AsteroidGame.java
├── desktop/                # Desktop launcher (LWJGL3)
│   └── src/main/java/com/example/asteroids/desktop/
│       └── DesktopLauncher.java
├── build.gradle
├── settings.gradle
└── .gitignore
```

---

## Tech Stack

- **Java 17**
- **libGDX 1.12.1**
- **LWJGL3** (desktop backend)
- **Gradle** (build tool)

