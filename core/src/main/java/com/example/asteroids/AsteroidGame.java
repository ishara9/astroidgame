package com.example.asteroids;

// ============================================================
//  libGDX – Asteroid Shooter Sample Game
//  Single-file demo covering the core libGDX patterns:
//    • ApplicationAdapter lifecycle
//    • SpriteBatch + ShapeRenderer rendering
//    • InputProcessor keyboard handling
//    • Game-loop update / render separation
//    • Simple AABB collision detection
//    • Screen-wrap movement
//    • Score + lives HUD via BitmapFont
// ============================================================
//
//  build.gradle dependencies (core module):
//    api "com.badlogicgames.gdx:gdx:1.12.1"
//  desktop launcher:
//    api "com.badlogicgames.gdx:gdx-backend-lwjgl3:1.12.1"
//    api "com.badlogicgames.gdx:gdx-platform:1.12.1:natives-desktop"
// ============================================================

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;

public class AsteroidGame extends ApplicationAdapter {

    // ── Constants ────────────────────────────────────────────
    private static final float W = 800f, H = 600f;
    private static final float SHIP_SPEED       = 500f;
    private static final float SHIP_SIZE        = 20f;
    private static final float BULLET_SPEED     = 480f;
    private static final float BULLET_LIFE      = 1.2f;   // seconds
    private static final float ASTEROID_MIN_SPD = 60f;
    private static final float ASTEROID_MAX_SPD = 60f;
    private static final int   INITIAL_ASTEROIDS = 2;
    private static final int   MAX_LIVES          = 3;
    private static final float RESPAWN_DELAY      = 2f;

    // ── Rendering ────────────────────────────────────────────
    private ShapeRenderer shapes;
    private SpriteBatch   batch;
    private BitmapFont    font;

    // ── Game state ───────────────────────────────────────────
    private enum State { PLAYING, RESPAWNING, GAME_OVER }
    private State state;

    // ── Player ───────────────────────────────────────────────
    private Vector2 shipPos;
    private Vector2 shipVel;
    private float   shipAngle;   // degrees, 0 = right
    private int     lives;
    private int     score;
    private float   respawnTimer;

    // ── Bullets ──────────────────────────────────────────────
    private static class Bullet {
        Vector2 pos, vel;
        float   life;
        Bullet(Vector2 p, Vector2 v) { pos = p.cpy(); vel = v.cpy(); life = BULLET_LIFE; }
    }
    private Array<Bullet> bullets;

    // ── Asteroids ────────────────────────────────────────────
    private static class Asteroid {
        Vector2 pos, vel;
        float   radius;
        int     tier;       // 3 = large, 2 = medium, 1 = small
        float   rotation, rotSpeed;
        float[] jitter;

        Asteroid(Vector2 p, Vector2 v, int tier) {
            pos = p.cpy(); vel = v.cpy(); this.tier = tier;
            radius    = tier * 18f;
            rotation  = MathUtils.random(360f);
            rotSpeed = MathUtils.randomSign() * MathUtils.random(30f, 90f) * (1f / tier);

            int sides = 8 + tier * 2;
            jitter = new float[sides];

            for (int i = 0; i < sides; i++) {
                jitter[i] = 0.75f + MathUtils.random.nextFloat() * 0.45f;
            }

            Gdx.app.log("Asteroid", "rotSpeed = " + rotSpeed);
        }
    }
    private Array<Asteroid> asteroids;

    // ── Shooting cooldown ────────────────────────────────────
    private float shootCooldown = 0f;

    // ── Particles (simple flash on collision) ────────────────
    private static class Particle {
        Vector2 pos, vel;
        float   life, maxLife;
        Particle(Vector2 p, float angle, float speed, float life) {
            pos = p.cpy(); this.life = life; maxLife = life;
            vel = new Vector2(MathUtils.cosDeg(angle) * speed,
                              MathUtils.sinDeg(angle) * speed);
        }
    }
    private Array<Particle> particles;

    // ─────────────────────────────────────────────────────────
    //  ApplicationAdapter lifecycle
    // ─────────────────────────────────────────────────────────

    @Override
    public void create() {
        shapes    = new ShapeRenderer();
        batch     = new SpriteBatch();
        font      = new BitmapFont();            // default Arial bitmap font
        font.setColor(Color.WHITE);
        font.getData().setScale(1.4f);

        bullets    = new Array<>();
        asteroids  = new Array<>();
        particles  = new Array<>();

        startNewGame();
    }

    private void startNewGame() {
        lives = MAX_LIVES;
        score = 0;
        state = State.PLAYING;
        spawnShip();
        initialize();
        spawnAsteroidWave(INITIAL_ASTEROIDS);
    }

    private void initialize() {
        asteroids.clear();
    }

    private void spawnShip() {
        shipPos   = new Vector2(W / 2f, H / 2f);
        shipVel   = new Vector2(0, 0);
        shipAngle = 90f;
    }

    private void spawnAsteroidWave(int count) {
        for (int i = 0; i < count; i++) spawnAsteroid(3);
    }

    /** Spawn large asteroid away from the ship */
    private void spawnAsteroid(int tier) {
        Vector2 pos;
        do {
            pos = new Vector2(MathUtils.random(W), MathUtils.random(H));
        } while (pos.dst(shipPos) < 120f);

        float angle = MathUtils.random(360f);
        float speed = MathUtils.random(ASTEROID_MIN_SPD, ASTEROID_MAX_SPD);
        Vector2 vel = new Vector2(MathUtils.cosDeg(angle) * speed,
                                  MathUtils.sinDeg(angle) * speed);
        asteroids.add(new Asteroid(pos, vel, tier));
    }

    // ─────────────────────────────────────────────────────────
    //  Main loop
    // ─────────────────────────────────────────────────────────

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();

        update(dt);
        draw();
    }

    // ── UPDATE ───────────────────────────────────────────────

    private void update(float dt) {
        if (state == State.GAME_OVER) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.R)) startNewGame();
            return;
        }

        if (state == State.RESPAWNING) {
            respawnTimer -= dt;
            if (respawnTimer <= 0) {
                state = State.PLAYING;
                spawnShip();
            }
            updateAsteroids(dt);
            updateParticles(dt);
            return;
        }

        // ── Input ────────────────────────────────────────────
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT))  shipAngle += 180f * dt;
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) shipAngle -= 180f * dt;

        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
            // Thrust in facing direction
            shipVel.x += MathUtils.cosDeg(shipAngle) * SHIP_SPEED * dt;
            shipVel.y += MathUtils.sinDeg(shipAngle) * SHIP_SPEED * dt;
            shipVel.clamp(0, SHIP_SPEED);
        }

        // Apply drag
        shipVel.scl(1f - 0.8f * dt);

        shootCooldown -= dt;
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE) && shootCooldown <= 0f) {
            fireBullet();
            shootCooldown = 0.25f;
        }

        // ── Move ship ────────────────────────────────────────
        shipPos.add(shipVel.x * dt, shipVel.y * dt);
        wrap(shipPos);

        // ── Bullets ──────────────────────────────────────────
        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.pos.add(b.vel.x * dt, b.vel.y * dt);
            b.life -= dt;
            wrap(b.pos);
            if (b.life <= 0) bullets.removeIndex(i);
        }

        updateAsteroids(dt);
        checkCollisions();
        updateParticles(dt);

        // Next wave when all asteroids gone
        if (asteroids.isEmpty()) spawnAsteroidWave(INITIAL_ASTEROIDS + score / 500);
    }

    private void fireBullet() {
        Vector2 vel = new Vector2(
            MathUtils.cosDeg(shipAngle) * BULLET_SPEED,
            MathUtils.sinDeg(shipAngle) * BULLET_SPEED
        );
        bullets.add(new Bullet(shipPos, vel));
    }

    private void updateAsteroids(float dt) {
        for (Asteroid a : asteroids) {
            a.pos.add(a.vel.x * dt, a.vel.y * dt);
            a.rotation += a.rotSpeed * dt;
            wrap(a.pos);
        }
    }

    private void updateParticles(float dt) {
        for (int i = particles.size - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.pos.add(p.vel.x * dt, p.vel.y * dt);
            p.life -= dt;
            if (p.life <= 0) particles.removeIndex(i);
        }
    }

    // ── COLLISION ────────────────────────────────────────────

    private void checkCollisions() {
        // Bullet vs Asteroid
        for (int bi = bullets.size - 1; bi >= 0; bi--) {
            Bullet b = bullets.get(bi);
            for (int ai = asteroids.size - 1; ai >= 0; ai--) {
                Asteroid a = asteroids.get(ai);
                if (b.pos.dst(a.pos) < a.radius) {
                    bullets.removeIndex(bi);
                    destroyAsteroid(ai);
                    break;
                }
            }
        }

        // Ship vs Asteroid
        if (state != State.PLAYING) return;
        for (int ai = asteroids.size - 1; ai >= 0; ai--) {
            Asteroid a = asteroids.get(ai);
            if (shipPos.dst(a.pos) < a.radius + SHIP_SIZE * 0.5f) {
                explode(shipPos, 20, Color.CYAN);
                lives--;
                if (lives <= 0) {
                    state = State.GAME_OVER;
                } else {
                    state = State.RESPAWNING;
                    respawnTimer = RESPAWN_DELAY;
                }
                return;
            }
        }
    }

    private void destroyAsteroid(int index) {
        Asteroid a = asteroids.get(index);
        int pts = (4 - a.tier) * 100;   // small=300, med=200, large=100
        score += pts;

        explode(a.pos, 10 + (4 - a.tier) * 5, Color.ORANGE);

        // Split into smaller pieces
        if (a.tier > 1) {
            for (int i = 0; i < 2; i++) {
                float angle = MathUtils.random(360f);
                float speed = MathUtils.random(ASTEROID_MIN_SPD, ASTEROID_MAX_SPD) * 1.3f;
                Vector2 vel = new Vector2(MathUtils.cosDeg(angle) * speed,
                                         MathUtils.sinDeg(angle) * speed);
                asteroids.add(new Asteroid(a.pos, vel, a.tier - 1));
            }
        }
        asteroids.removeIndex(index);
    }

    private void explode(Vector2 origin, int count, Color c) {
        for (int i = 0; i < count; i++) {
            float angle = MathUtils.random(360f);
            float speed = MathUtils.random(40f, 160f);
            particles.add(new Particle(origin, angle, speed,
                                       MathUtils.random(0.4f, 1.0f)));
        }
    }

    // ── Screen wrap ──────────────────────────────────────────

    private void wrap(Vector2 v) {
        if (v.x < 0) v.x += W;  if (v.x > W) v.x -= W;
        if (v.y < 0) v.y += H;  if (v.y > H) v.y -= H;
    }

    // ─────────────────────────────────────────────────────────
    //  DRAW
    // ─────────────────────────────────────────────────────────

    private void draw() {
        ScreenUtils.clear(0.04f, 0.04f, 0.12f, 1f);

        shapes.begin(ShapeRenderer.ShapeType.Line);

        // ── Asteroids ────────────────────────────────────────
        shapes.setColor(0.8f, 0.7f, 0.5f, 1f);
        for (Asteroid a : asteroids) drawAsteroid(a);

        // ── Bullets ──────────────────────────────────────────
        shapes.setColor(Color.YELLOW);
        for (Bullet b : bullets)
            shapes.circle(b.pos.x, b.pos.y, 3f, 6);

        // ── Ship ─────────────────────────────────────────────
        if (state == State.PLAYING) {
            shapes.setColor(Color.CYAN);
            drawShip();
        }

        shapes.end();

        // ── Particles ────────────────────────────────────────
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Particle p : particles) {
            float alpha = p.life / p.maxLife;
            shapes.setColor(1f, 0.5f * alpha, 0f, alpha);
            shapes.circle(p.pos.x, p.pos.y, 2.5f * alpha, 6);
        }
        shapes.end();

        // ── HUD ──────────────────────────────────────────────
        batch.begin();
        font.draw(batch, "SCORE: " + score, 12, H - 10);
        font.draw(batch, "LIVES: " + lives, W - 120, H - 10);

        if (state == State.GAME_OVER) {
            font.getData().setScale(2.5f);
            font.draw(batch, "GAME OVER", W / 2f - 95, H / 2f + 20);
            font.getData().setScale(1.2f);
            font.draw(batch, "Press R to restart", W / 2f - 85, H / 2f - 20);
            font.getData().setScale(1.4f);
        }

        if (state == State.RESPAWNING) {
            font.getData().setScale(1.2f);
            font.draw(batch, "Respawning...", W / 2f - 60, H / 2f);
            font.getData().setScale(1.4f);
        }
        batch.end();
    }

    /** Draw a triangle-ish ship pointing in shipAngle direction */
    private void drawShip() {
        // Ship is defined as 3 local-space points, then rotated + translated
        float[] pts = buildShipPoints();
        shapes.line(pts[0], pts[1], pts[2], pts[3]);
        shapes.line(pts[2], pts[3], pts[4], pts[5]);
        shapes.line(pts[4], pts[5], pts[0], pts[1]);
    }

    private float[] buildShipPoints() {
        // Three vertices of the ship triangle in local space
        float[][] local = {
            { SHIP_SIZE,           0f },      // nose
            { -SHIP_SIZE * 0.6f,   SHIP_SIZE * 0.7f },  // left wing
            { -SHIP_SIZE * 0.6f,  -SHIP_SIZE * 0.7f }   // right wing
        };
        float[] world = new float[6];
        float cos = MathUtils.cosDeg(shipAngle);
        float sin = MathUtils.sinDeg(shipAngle);
        for (int i = 0; i < 3; i++) {
            float lx = local[i][0], ly = local[i][1];
            world[i * 2]     = shipPos.x + lx * cos - ly * sin;
            world[i * 2 + 1] = shipPos.y + lx * sin + ly * cos;
        }
        return world;
    }

    /** Draw asteroid as an irregular polygon */
    private void drawAsteroid(Asteroid a) {
        int sides = a.jitter.length;
        for (int i = 0; i < sides; i++) {

            float angle1 = a.rotation + i * (360f / sides);
            float angle2 = a.rotation + (i + 1) % sides * (360f / sides);

            float r1 = a.radius * a.jitter[i];
            float r2 = a.radius * a.jitter[(i + 1) % sides];

            float x1 = a.pos.x + MathUtils.cosDeg(angle1) * r1;
            float y1 = a.pos.y + MathUtils.sinDeg(angle1) * r1;

            float x2 = a.pos.x + MathUtils.cosDeg(angle2) * r2;
            float y2 = a.pos.y + MathUtils.sinDeg(angle2) * r2;

            shapes.line(x1, y1, x2, y2);
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Cleanup
    // ─────────────────────────────────────────────────────────

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
