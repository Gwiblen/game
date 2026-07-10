import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.sound.sampled.*;
import java.io.File;

/**
 * tknew - a top-down 2D psychological horror game.
 *
 * Single-file Java SE build: Swing/AWT for rendering, javax.sound.sampled for audio.
 * No external libraries, no image/sprite assets. Everything is drawn with Graphics2D
 * and every sound is synthesized procedurally, except for one optional looping
 * ambient bed (ambientgameaudio.wav) that is used if present and skipped otherwise.
 *
 * Compile:  javac tknew.java
 * Run:      java tknew
 */
public class tknew extends JFrame {

    // ============================================================
    // CONFIG - every tuning knob lives here.
    // ============================================================
    static final class Config {
        // Display
        static final int SCREEN_W = 1000;
        static final int SCREEN_H = 562;
        static final int HITBOX_PADDING = 14;
        static final double DOOR_INTERACT_RANGE_PX = 30.0; // distance-based "close enough to press E" range for doors

        // Sanity
        static final double SANITY_START = 50.0;
        static final double SANITY_WRITE_GAIN_PER_SEC = 19.25;
        static final double SANITY_WALK_LOSS_PER_SEC = 0.25;
        static final double SANITY_THERMO_LOSS_PER_SEC = 0.25;
        static final double SANITY_HIDE_LOSS_PER_SEC = 1.0;
        static final double SANITY_THERMO_OPEN_HIT = 0.5;
        static final double SANITY_LOW_THRESHOLD = 40.0;
        static final double SANITY_BREATH_FAIL_PENALTY = 15.0;
        static final double SANITY_HOLD_AT_MAX_SEC = 5.0;
        static final double WIN_SANITY = 100.0;

        // Pills - bedroom consumable resource (raises sanity, limited charges)
        static final int PILLS_MAX = 3;
        static final double PILLS_GAIN = 20.0;
        static final double PILLS_CAP = 50.0;

        // Hiding / breathing minigame (bed only - closets never trigger this)
        static final double HIDE_MAX_SAFE_TOTAL_SEC = 3.0;
        static final int BREATH_REQUIRED_SUCCESSES = 3;
        static final double BREATH_SLIDER_SPEED = 1.35;
        static final double BREATH_TARGET_START = 0.43;
        static final double BREATH_TARGET_END = 0.57;

        // Closets - forced-out + cooldown instead of breathing
        static final double CLOSET_MAX_HIDE_SEC = 8.0;
        static final double CLOSET_REENTRY_COOLDOWN_SEC = 10.0;

        // Monster AI (node graph timing)
        static final double MONSTER_MOVE_MIN_SEC = 2.0;
        static final double MONSTER_MOVE_MAX_SEC = 6.0;
        static final double MONSTER_ENCOUNTER_DURATION_SEC = 1.0;
        static final double MONSTER_HALL_STAY_SEC = 2.0;
        static final double DOOR_WARNING_MIN_SEC = 0.5;
        static final double DOOR_WARNING_MAX_SEC = 3.0;
        static final double MONSTER_DOOR_BREAK_SEC = 1.5; // extra time for the monster to break a closed door open

        // Hunting mode (active once the house is accessible - 100 sanity or Night 2+)
        static final double HUNT_TARGET_PLAYER_CHANCE_FLASHLIGHT_ON = 0.55;  // chance a move deliberately paths toward the player's room
        static final double HUNT_TARGET_PLAYER_CHANCE_FLASHLIGHT_OFF = 0.20;
        static final double HUNT_BACKTRACK_WEIGHT = 0.35; // relative weight (vs 1.0) for re-picking the room it just came from
        static final double MONSTER_HALLWAY_TRANSIT_SEC = 1.0; // time spent physically crossing a hallway between rooms

        // Ambient self-light (always on, independent of the flashlight)
        static final double SELF_LIGHT_RADIUS_PX = 46.0;

        // Flashlight
        static final double FLASH_ARC_DEG = 45.0;
        static final double FLASH_RANGE_PX = 270.0;
        static final double FLASH_FADE_EXP = 1.65;
        static final double FLASH_EDGE_SOFTNESS = 0.22;
        static final double FLASH_INTENSITY = 1.0;
        static final double FLASHLIGHT_TARGET_BIAS = 0.35;   // chance monster steps toward player's zone when light is on
        static final double FLASHLIGHT_WARNING_SCALE_ON = 0.85;  // shorter door warning when flashlight on
        static final double FLASHLIGHT_WARNING_SCALE_OFF = 1.15; // longer / more forgiving when off

        // Thermal camera / film
        static final double CAM_ARC_DEG = 90.0;
        static final double CAM_RANGE_PX = 320.0;
        static final double CAM_FLASH_SEC = 0.18;
        static final int FILM_MAX = 6;
        static final boolean FAIL_NIGHT2_ON_FILM_OUT = true;

        // Temperature / thermostats
        static final int TEMP_MAX = 72;
        static final int TEMP_MIN = 50;
        static final double TEMP_RATE_PER_SEC = 11.0;
        static final double TEMP_FLUCT_AMPLITUDE = 3.0;
        static final double TEMP_FLUCT_ACCEL = 80.0;
        static final double TEMP_FLUCT_DAMPING = 2.4;
        static final double HALLUCINATION_CHANCE = 0.25;
        static final int HALLUCINATION_DROP_MIN = 8;
        static final int HALLUCINATION_DROP_MAX = 12;

        // Night pacing
        static final double NIGHT2_DURATION_SEC = 420.0;
        static final double NIGHT3_DURATION_SEC = 90.0;

        // Night 3 - unwinnable infinite house
        static final double NIGHT3_CATCH_THRESHOLD = 0.70;
        static final double NIGHT3_CATCH_BASE_CHANCE = 0.015;
        static final double NIGHT3_CATCH_GROWTH = 0.07;
        static final double NIGHT3_CATCH_END_DELAY_SEC = 1.2;
        static final double NIGHT3_FLAVOR_MIN_SEC = 7.0;
        static final double NIGHT3_FLAVOR_MAX_SEC = 12.0;

        // Ending screen
        static final double ENDING_MESSAGE_INTERVAL_SEC = 1.1;

        // Audio levels
        static final double AUDIO_MASTER_BASE = 0.030;
        static final double AUDIO_MASTER_PANIC_BOOST = 0.10;
        static final double AUDIO_AMBIENT_BASE = 0.018;
        static final double AUDIO_FOOTSTEP_GAIN = 0.045;
        static final double AUDIO_CREAK_WARNING_GAIN = 0.05;
        static final double AUDIO_CREAK_ENTER_GAIN = 0.14;
        static final double WAV_AMBIENT_VOLUME01 = 0.22;
    }

    // ============================================================
    // AUDIO - optional looping ambient bed
    // ============================================================
    static final class AmbientLoop {
        private Clip clip;
        private FloatControl gainControl;

        void loadAndLoop(String fileName) {
            try {
                File f = new File(fileName);
                if (!f.exists()) {
                    System.out.println("[AmbientLoop] " + fileName + " not found; continuing without it.");
                    return;
                }
                AudioInputStream rawStream = AudioSystem.getAudioInputStream(f);
                AudioFormat baseFormat = rawStream.getFormat();
                AudioFormat decodedFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED, baseFormat.getSampleRate(), 16,
                        baseFormat.getChannels(), baseFormat.getChannels() * 2, baseFormat.getSampleRate(), false);
                AudioInputStream decodedStream = AudioSystem.getAudioInputStream(decodedFormat, rawStream);
                clip = AudioSystem.getClip();
                clip.open(decodedStream);
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                }
                setVolume01(Config.WAV_AMBIENT_VOLUME01);
                clip.loop(Clip.LOOP_CONTINUOUSLY);
                clip.start();
            } catch (Exception ex) {
                System.out.println("[AmbientLoop] Could not load ambient audio (continuing without it): " + ex.getMessage());
            }
        }

        void setVolume01(double v) {
            if (clip == null || gainControl == null) return;
            v = Math.max(0.0, Math.min(1.0, v));
            double db = -35.0 + (v * 35.0);
            db = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), db));
            gainControl.setValue((float) db);
        }

        void stop() {
            try {
                if (clip != null) { clip.stop(); clip.close(); }
            } catch (Exception ignored) { }
            clip = null;
            gainControl = null;
        }
    }

    // ============================================================
    // AUDIO - procedural SFX + hum bed (click, creak, footstep, fail buzz, pill tone)
    // ============================================================
    static final class ProceduralAudio {
        private volatile boolean running = false;
        private SourceDataLine line;
        private Thread audioThread;
        private volatile double masterLevel = 0.0;
        private volatile double ambientLevel = 0.0;
        private final Object queueLock = new Object();
        private final ArrayDeque<SfxEvent> queue = new ArrayDeque<>();

        private enum SfxType { CLICK, PILL, FAIL, FOOTSTEP, CREAK }

        private static final class SfxEvent {
            final SfxType type;
            final double gain;
            SfxEvent(SfxType type, double gain) { this.type = type; this.gain = gain; }
        }

        void ensureRunning() { if (!running) startEngine(); }

        void startEngine() {
            try {
                AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
                line = AudioSystem.getSourceDataLine(format);
                line.open(format, 4410);
                line.start();
                running = true;
                audioThread = new Thread(this::runAudioLoop, "ProceduralAudio");
                audioThread.setDaemon(true);
                audioThread.start();
            } catch (Exception ex) {
                running = false;
                System.out.println("[ProceduralAudio] Audio device unavailable (continuing silently): " + ex.getMessage());
            }
        }

        void setMasterLevel(double v) { masterLevel = Math.max(0, Math.min(0.25, v)); }
        void setAmbientLevel(double v) { ambientLevel = Math.max(0, Math.min(0.12, v)); }

        private void enqueue(SfxType type, double gain) {
            if (!running) return;
            synchronized (queueLock) { queue.addLast(new SfxEvent(type, gain)); }
        }

        void playClick() { enqueue(SfxType.CLICK, 1.0); }
        void playPillTone() { enqueue(SfxType.PILL, 1.0); }
        void playFailBuzz() { enqueue(SfxType.FAIL, 1.0); }
        void playFootstep(double gain) { enqueue(SfxType.FOOTSTEP, gain); }
        void playCreak(double gain) { enqueue(SfxType.CREAK, gain); }

        private void runAudioLoop() {
            final int sampleRate = 44100;
            final int bufferSamples = 512;
            byte[] outputBytes = new byte[bufferSamples * 2];
            double humPhaseA = 0, humPhaseB = 0;
            OneShotVoice click = new OneShotVoice(sampleRate);
            OneShotVoice pill = new OneShotVoice(sampleRate);
            OneShotVoice fail = new OneShotVoice(sampleRate);
            OneShotVoice step = new OneShotVoice(sampleRate);
            OneShotVoice creak = new OneShotVoice(sampleRate);

            while (running) {
                synchronized (queueLock) {
                    while (!queue.isEmpty()) {
                        SfxEvent ev = queue.removeFirst();
                        switch (ev.type) {
                            case CLICK: click.triggerTone(0.06, 160, 0.06); break;
                            case PILL: pill.triggerSweep(0.35, 330, 520, 0.10); break;
                            case FAIL: fail.triggerSweep(0.26, 160, 70, 0.15); break;
                            case FOOTSTEP: step.triggerTone(0.10, 90 + Math.random() * 30, ev.gain); break;
                            case CREAK: creak.triggerSweep(0.9, 200, 85, ev.gain); break;
                        }
                    }
                }
                for (int i = 0; i < bufferSamples; i++) {
                    double humFreqA = 55.0;
                    humPhaseA += (2 * Math.PI * humFreqA) / sampleRate;
                    if (humPhaseA > 2 * Math.PI) humPhaseA -= 2 * Math.PI;
                    double hum1 = Math.sin(humPhaseA) * ambientLevel;

                    double humFreqB = 110.0;
                    humPhaseB += (2 * Math.PI * humFreqB) / sampleRate;
                    if (humPhaseB > 2 * Math.PI) humPhaseB -= 2 * Math.PI;
                    double hum2 = Math.sin(humPhaseB) * (ambientLevel * 0.35);

                    double sample = hum1 + hum2;
                    sample += click.nextSample() + pill.nextSample() + fail.nextSample() + step.nextSample() + creak.nextSample();
                    sample *= masterLevel;
                    sample = Math.max(-1, Math.min(1, sample));
                    short pcm = (short) (sample * 32767);
                    int idx = i * 2;
                    outputBytes[idx] = (byte) (pcm & 0xFF);
                    outputBytes[idx + 1] = (byte) ((pcm >> 8) & 0xFF);
                }
                line.write(outputBytes, 0, outputBytes.length);
            }
            try { line.drain(); line.close(); } catch (Exception ignored) { }
        }

        /** A single enveloped tone or sweep voice, re-triggerable. */
        private static final class OneShotVoice {
            private final int sampleRate;
            private int samplesRemaining = 0;
            private double phase = 0;
            private double startFreq = 0, endFreq = 0;
            private double gain = 0;
            private double durationSec = 0;
            private boolean isSweep = false;

            OneShotVoice(int sampleRate) { this.sampleRate = sampleRate; }

            void triggerTone(double durationSec, double freq, double gain) {
                this.durationSec = durationSec;
                this.samplesRemaining = (int) (durationSec * sampleRate);
                this.startFreq = freq;
                this.endFreq = freq;
                this.gain = gain;
                this.phase = 0;
                this.isSweep = false;
            }

            void triggerSweep(double durationSec, double freqStart, double freqEnd, double gain) {
                this.durationSec = durationSec;
                this.samplesRemaining = (int) (durationSec * sampleRate);
                this.startFreq = freqStart;
                this.endFreq = freqEnd;
                this.gain = gain;
                this.phase = 0;
                this.isSweep = true;
            }

            double nextSample() {
                if (samplesRemaining <= 0) return 0;
                int totalSamples = (int) (durationSec * sampleRate);
                int elapsed = totalSamples - samplesRemaining;
                double t = (totalSamples <= 1) ? 0 : (elapsed / (double) (totalSamples - 1));
                double envelope = (t < 0.08) ? (t / 0.08) : Math.exp(-(t - 0.08) * 5.5);
                double freq = isSweep ? (startFreq + (endFreq - startFreq) * t) : startFreq;
                phase += (2 * Math.PI * freq) / sampleRate;
                if (phase > 2 * Math.PI) phase -= 2 * Math.PI;
                double s;
                if (!isSweep) {
                    s = (startFreq < 120) ? (2 / Math.PI) * Math.asin(Math.sin(phase)) : Math.sin(phase);
                } else {
                    s = (2 / Math.PI) * Math.atan(Math.tan(phase / 2));
                }
                samplesRemaining--;
                return s * gain * envelope;
            }
        }
    }

    // ============================================================
    // JFrame wrapper + entry point
    // ============================================================
    public tknew() {
        super("tknew");
        GamePanel panel = new GamePanel();
        setContentPane(panel);
        pack();
        setResizable(false);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);
        panel.requestFocusInWindow();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                panel.ambientLoop.stop();
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(tknew::new);
    }

    // ============================================================
    // GAME PANEL - everything gameplay + rendering lives here
    // ============================================================
    static final class GamePanel extends JPanel implements KeyListener, MouseListener, MouseMotionListener {

        // ---- small nested types ----
        enum GameMode { TITLE, NIGHT_INTRO, NIGHT1, CUTSCENE_CALL, NIGHT2, CUTSCENE_MEETING, NIGHT3, ENDING }
        enum MonsterNode { KITCHEN, LIVING, UTILITY, DINING, BEDROOM, HALL }

        static final class Door {
            final String id; final Rectangle rect; boolean open = false;
            final String zoneA, zoneB;
            final Rectangle hallway; // the hallway this door sits in the middle of
            Door(String id, Rectangle rect, String zoneA, String zoneB, Rectangle hallway) {
                this.id = id; this.rect = rect; this.zoneA = zoneA; this.zoneB = zoneB; this.hallway = hallway;
            }
            Rectangle expanded() {
                int p = Config.HITBOX_PADDING;
                return new Rectangle(rect.x - p, rect.y - p, rect.width + p * 2, rect.height + p * 2);
            }
        }
        static final class Closet {
            final String id; final Rectangle rect;
            Closet(String id, Rectangle rect) { this.id = id; this.rect = rect; }
            Rectangle expanded() {
                int p = Config.HITBOX_PADDING;
                return new Rectangle(rect.x - p, rect.y - p, rect.width + p * 2, rect.height + p * 2);
            }
        }
        static final class Thermostat {
            final String id; final Rectangle rect; boolean open = false;
            Thermostat(String id, Rectangle rect) { this.id = id; this.rect = rect; }
            Rectangle expanded() {
                int p = Config.HITBOX_PADDING;
                return new Rectangle(rect.x - p, rect.y - p, rect.width + p * 2, rect.height + p * 2);
            }
        }
        static final class Prop {
            final String id; final String label; final Rectangle rect;
            Prop(String id, String label, Rectangle rect) { this.id = id; this.label = label; this.rect = rect; }
            Rectangle expanded() {
                int p = Config.HITBOX_PADDING;
                return new Rectangle(rect.x - p, rect.y - p, rect.width + p * 2, rect.height + p * 2);
            }
        }
        static final class Hallucination { boolean active = false; String zone = null; int drop = 0; }
        interface SceneDecoration { void draw(Graphics2D g); }

        // ---- dialogue / flavor text ----
        static final String[] PHONE_CALL_LINES = {
                "Doctor: \u201CYou said the sounds returned?\u201D",
                "MC: \u201CIt isn\u2019t sounds. Something is moving through the house.\u201D",
                "Doctor: \u201CYou\u2019re exhausted. I\u2019m increasing your dose.\u201D",
                "MC: \u201CNo, I saw it near the hallway.\u201D",
                "Doctor: \u201CTake the medication and sleep. We\u2019ll speak tomorrow.\u201D"
        };
        static final String[] DOCTOR_MEETING_LINES = {
                "Doctor: \u201CThese are the photos?\u201D",
                "MC: \u201CThat outline. That\u2019s it. That\u2019s what\u2019s in my house.\u201D",
                "Doctor: \u201CI see darkness and glare. Nothing more.\u201D",
                "MC: \u201CIt was standing there.\u201D",
                "Doctor: \u201CI need you to answer your phone tonight.\u201D"
        };
        static final String[] FINAL_TEXT_MESSAGES = {
                "Are you safe?",
                "Please call me back.",
                "I\u2019m sending someone to check on you.",
                "Do not stay in the house.",
                "Answer me."
        };
        static final String[] NIGHT3_FALSE_HINTS = {
                "This room feels different.",
                "Maybe the next door is the way out.",
                "The coordinates keep climbing.",
                "Something about this thermostat is wrong.",
                "You've been here before.",
                "Keep moving. It has to end somewhere.",
                "The kitchen has to be close now.",
                "Count the doors. Something is off."
        };

        static final int W = Config.SCREEN_W;
        static final int H = Config.SCREEN_H;

        // ---- house layout (dependency order matters for field initializers) ----
        final Rectangle roomKitchen = new Rectangle(70, 60, 260, 170);
        final Rectangle roomLiving  = new Rectangle(410, 60, 310, 170);
        final Rectangle roomUtility = new Rectangle(70, 320, 260, 170);
        final Rectangle roomDining  = new Rectangle(410, 320, 310, 170);
        final Rectangle roomBedroom = new Rectangle(760, 320, 200, 170);

        final Rectangle[] hallways = new Rectangle[] {
                new Rectangle(roomKitchen.x + roomKitchen.width, roomKitchen.y + 70, roomLiving.x - (roomKitchen.x + roomKitchen.width), 30),
                new Rectangle(roomKitchen.x + 110, roomKitchen.y + roomKitchen.height, 30, roomUtility.y - (roomKitchen.y + roomKitchen.height)),
                new Rectangle(roomUtility.x + roomUtility.width, roomUtility.y + 70, roomDining.x - (roomUtility.x + roomUtility.width), 30),
                new Rectangle(roomLiving.x + 130, roomLiving.y + roomLiving.height, 30, roomDining.y - (roomLiving.y + roomLiving.height)),
                new Rectangle(roomDining.x + roomDining.width, roomDining.y + 70, roomBedroom.x - (roomDining.x + roomDining.width), 30)
        };
        final Rectangle preBedroomHall = new Rectangle(
                roomDining.x + roomDining.width + 14, roomDining.y + 62,
                (roomBedroom.x - (roomDining.x + roomDining.width)) - 28, 46);
        final Rectangle bedroomBounds = new Rectangle(roomBedroom.x + 10, roomBedroom.y + 10, roomBedroom.width - 20, roomBedroom.height - 20);
        // Everywhere the player is allowed to stand: every room plus every connecting hallway.
        // Movement is collision-checked against this union so the player can no longer wander
        // through walls into the empty space between rooms.
        final Rectangle[] walkableRects = new Rectangle[] {
                roomKitchen, roomLiving, roomUtility, roomDining, roomBedroom,
                hallways[0], hallways[1], hallways[2], hallways[3], hallways[4],
                preBedroomHall
        };

        // One door per hallway connection, centered in the hallway (not anchored to a room's wall).
        final Door doorKitchenLiving = new Door("doorKitchenLiving",
                new Rectangle(hallways[0].x + hallways[0].width / 2 - 7, hallways[0].y, 14, 30), "kitchen", "living", hallways[0]);
        final Door doorKitchenUtility = new Door("doorKitchenUtility",
                new Rectangle(hallways[1].x, hallways[1].y + hallways[1].height / 2 - 7, 30, 14), "kitchen", "utility", hallways[1]);
        final Door doorUtilityDining = new Door("doorUtilityDining",
                new Rectangle(hallways[2].x + hallways[2].width / 2 - 7, hallways[2].y, 14, 30), "utility", "dining", hallways[2]);
        final Door doorLivingDining = new Door("doorLivingDining",
                new Rectangle(hallways[3].x, hallways[3].y + hallways[3].height / 2 - 7, 30, 14), "living", "dining", hallways[3]);
        final Door doorDiningBedroom = new Door("doorDiningBedroom",
                new Rectangle(hallways[4].x + 4, hallways[4].y, 14, 30), "dining", "bedroom", hallways[4]);
        final Door[] doors = { doorKitchenLiving, doorKitchenUtility, doorUtilityDining, doorLivingDining, doorDiningBedroom };

        // Closets sit in room corners (out of the main walking path), not in the hallways.
        final Closet closetKitchen = new Closet("closetKitchen", new Rectangle(roomKitchen.x + 14, roomKitchen.y + roomKitchen.height - 36, 22, 22));
        final Closet closetUtility = new Closet("closetUtility", new Rectangle(roomUtility.x + 14, roomUtility.y + 14, 22, 22));
        final Closet closetDining  = new Closet("closetDining", new Rectangle(roomDining.x + roomDining.width - 36, roomDining.y + roomDining.height - 36, 22, 22));
        final Closet[] closets = { closetKitchen, closetUtility, closetDining };

        final Thermostat thermoBedroom = new Thermostat("thermoBedroom", new Rectangle(roomBedroom.x + 170, roomBedroom.y + 30, 18, 24));
        final Thermostat thermoHallTop = new Thermostat("thermoHallTop", new Rectangle(hallways[0].x + hallways[0].width - 28, hallways[0].y + 4, 18, 22));
        final Thermostat thermoHallMid = new Thermostat("thermoHallMid", new Rectangle(hallways[2].x + hallways[2].width - 28, hallways[2].y + 4, 18, 22));
        final Thermostat thermoPreBed  = new Thermostat("thermoPreBed", new Rectangle(preBedroomHall.x + 10, preBedroomHall.y + 10, 18, 22));
        final Thermostat[] hallThermostats = { thermoHallTop, thermoHallMid, thermoPreBed };
        final Thermostat[] allThermostats = { thermoBedroom, thermoHallTop, thermoHallMid, thermoPreBed };

        final Prop desk = new Prop("desk", "Desk", new Rectangle(roomBedroom.x + 25, roomBedroom.y + 25, 60, 32));
        final Prop bed  = new Prop("bed", "Bed", new Rectangle(roomBedroom.x + 110, roomBedroom.y + 105, 75, 55));
        final Prop pillBottle = new Prop("pillBottle", "Pills", new Rectangle(roomBedroom.x + 55, roomBedroom.y + 78, 18, 18));
        final Prop[] bedroomProps = { desk, bed, pillBottle };
        final Prop kitchenPills = new Prop("kitchenPills", "Sleeping Pills", new Rectangle(roomKitchen.x + 180, roomKitchen.y + 110, 22, 16));

        // ---- player ----
        double px = roomBedroom.x + roomBedroom.width - 50;
        double py = roomBedroom.y + roomBedroom.height - 45;
        double playerRadius = 10;
        double speed = 210;
        double vx = 0, vy = 0;
        double facing = -Math.PI / 2;
        boolean moving = false;
        double walkAnimT = 0, writeAnimT = 0, hideAnimT = 0;
        final Set<Integer> keysDown = new HashSet<>();
        boolean eKeyLatch = false, cKeyLatch = false;
        boolean flashlightOn = true;

        // ---- flow ----
        GameMode mode = GameMode.TITLE;
        int currentNight = 0;
        int introForNight = 1;
        double modeTimer = 0;
        int cutsceneLineIndex = 0;
        double cutsceneLineTimer = 0;

        // ---- sanity ----
        double sanity = Config.SANITY_START;
        double prevSanity = sanity;
        double sanityHoldTimer = 0;
        int pillCharges = Config.PILLS_MAX;

        // ---- action flags ----
        boolean writing = false;
        boolean hidingInBed = false;
        boolean hidingInCloset = false;
        boolean breathingActive = false;
        int breathSuccesses = 0;
        double breathSlider = 0.02;
        int breathDirection = 1;
        double safeHideAccumulator = 0;
        double interactCooldown = 0;
        double screenShakeTimer = 0;

        // ---- closets ----
        String currentClosetId = null;
        double closetHideTimer = 0;
        final Map<String, Double> closetCooldowns = new HashMap<>();
        double closetForcedMessageTimer = 0;

        // ---- night 1 phase / shared fetch-quest pills ----
        boolean bedroomUnlocked = false;
        boolean hasSleepingPills = false;

        // ---- night 2 camera ----
        boolean holdingPhoto = false;
        boolean heldPhotoHasMonster = false;
        int monsterPhotosPlaced = 0;
        int filmRemaining = Config.FILM_MAX;
        double cameraFlashTimer = 0;

        // ---- temperature ----
        final String[] tempZones = { "kitchen", "living", "utility", "dining", "hall", "bedroom" };
        final Map<String, Double> baseTemp = new HashMap<>();
        final Map<String, Double> tempWiggle = new HashMap<>();
        final Map<String, Double> tempWiggleVel = new HashMap<>();
        final Hallucination hallucination = new Hallucination();

        // ---- monster AI ----
        MonsterNode monsterNode = MonsterNode.KITCHEN;
        double monsterMoveCooldown = 0;
        double hallStayTimer = 0;
        boolean pendingDoorAttack = false;
        double doorWarningTimer = 0;
        boolean doorWarningSoundPlayed = false;
        boolean breakingThroughDoor = false;
        double breakThroughTimer = 0;
        boolean breakThroughSoundPlayed = false;
        boolean encounterActive = false;
        double encounterTimer = 0;
        double nightProgress = 0;
        double nightClock = 0;

        // ---- hunting mode (active once the house is accessible) ----
        boolean huntingActive = false;
        MonsterNode previousMonsterNode = null;
        boolean huntTransitActive = false;
        double huntTransitTimer = 0;
        MonsterNode huntTransitFrom = null;
        MonsterNode huntTransitTo = null;
        Door huntTransitDoor = null;
        Rectangle huntTransitHallway = null;

        // ---- night 3 ----
        int gridX = 0, gridY = 0;
        boolean night3Caught = false;
        double night3EndTimer = 0;
        double night3FlavorTimer = 0;
        String night3FlavorText = "";

        // ---- ending ----
        String endingReason = null;
        double endingElapsedTimer = 0;

        // ---- hint ----
        boolean hintVisible = false;
        String hintText = "";

        // ---- timing / audio ----
        long lastFrameNanos = System.nanoTime();
        final ProceduralAudio proceduralAudio = new ProceduralAudio();
        final AmbientLoop ambientLoop = new AmbientLoop();
        javax.swing.Timer frameTimer;

        // ============================================================
        // Construction
        // ============================================================
        GamePanel() {
            setPreferredSize(new Dimension(W, H));
            setFocusable(true);
            addKeyListener(this);
            addMouseListener(this);
            addMouseMotionListener(this);
            for (String z : tempZones) {
                baseTemp.put(z, (double) Config.TEMP_MAX);
                tempWiggle.put(z, 0.0);
                tempWiggleVel.put(z, 0.0);
            }
            ambientLoop.loadAndLoop("ambientgameaudio.wav");
            ambientLoop.setVolume01(Config.WAV_AMBIENT_VOLUME01);
            proceduralAudio.startEngine();
            scheduleNextMonsterMove();
            frameTimer = new javax.swing.Timer(16, e -> tick());
            frameTimer.start();
        }

        // ============================================================
        // Math / geometry helpers
        // ============================================================
        static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
        static int clampInt(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
        static double randRange(double lo, double hi) { return lo + Math.random() * (hi - lo); }

        boolean centerMostlyInside(double cx, double cy, double r, Rectangle rect) {
            double inset = r * 0.5;
            return cx >= rect.x + inset && cx <= rect.x + rect.width - inset
                    && cy >= rect.y + inset && cy <= rect.y + rect.height - inset;
        }
        /** Distance from the player to the nearest point of the door's rectangle - forgiving in every direction. */
        boolean isNearDoor(Door d) {
            double nx = clamp(px, d.rect.x, d.rect.x + d.rect.width);
            double ny = clamp(py, d.rect.y, d.rect.y + d.rect.height);
            double dist = Math.hypot(px - nx, py - ny);
            return dist <= Config.DOOR_INTERACT_RANGE_PX;
        }
        boolean circleHitsRect(double cx, double cy, double r, Rectangle rect) {
            double nx = clamp(cx, rect.x, rect.x + rect.width);
            double ny = clamp(cy, rect.y, rect.y + rect.height);
            double dx = cx - nx, dy = cy - ny;
            return dx * dx + dy * dy < r * r;
        }

        // ============================================================
        // Flow control
        // ============================================================
        void beginNightIntro(int nightNumber) {
            currentNight = nightNumber;
            introForNight = nightNumber;
            mode = GameMode.NIGHT_INTRO;
            modeTimer = 3.0;
        }
        void startNight1() {
            resetForNewNight();
            currentNight = 1;
            mode = GameMode.NIGHT1;
        }
        void startNight2() {
            resetForNewNight();
            currentNight = 2;
            mode = GameMode.NIGHT2;
            doorDiningBedroom.open = true;
            doorKitchenLiving.open = false;
            doorKitchenUtility.open = false;
            doorUtilityDining.open = false;
            doorLivingDining.open = false;
        }
        void startNight3() {
            resetForNewNight();
            currentNight = 3;
            mode = GameMode.NIGHT3;
            gridX = 0; gridY = 0;
            night3Caught = false;
            night3EndTimer = 0;
            for (Door d : doors) d.open = true;
        }
        void resetForNewNight() {
            sanity = Config.SANITY_START; prevSanity = sanity; sanityHoldTimer = 0;
            pillCharges = Config.PILLS_MAX;
            writing = false; hidingInBed = false; hidingInCloset = false;
            for (Thermostat t : allThermostats) t.open = false;
            breathingActive = false; breathSuccesses = 0; breathSlider = 0.02; breathDirection = 1;
            safeHideAccumulator = 0; interactCooldown = 0; screenShakeTimer = 0;
            currentClosetId = null; closetHideTimer = 0; closetForcedMessageTimer = 0; closetCooldowns.clear();
            bedroomUnlocked = false; hasSleepingPills = false;
            holdingPhoto = false; heldPhotoHasMonster = false; monsterPhotosPlaced = 0;
            filmRemaining = Config.FILM_MAX; cameraFlashTimer = 0;
            monsterNode = MonsterNode.KITCHEN;
            pendingDoorAttack = false; doorWarningTimer = 0; doorWarningSoundPlayed = false;
            breakingThroughDoor = false; breakThroughTimer = 0; breakThroughSoundPlayed = false;
            encounterActive = false; encounterTimer = 0; hallStayTimer = 0;
            huntingActive = false; previousMonsterNode = null;
            huntTransitActive = false; huntTransitTimer = 0;
            huntTransitFrom = null; huntTransitTo = null; huntTransitDoor = null; huntTransitHallway = null;
            scheduleNextMonsterMove();
            nightProgress = 0; nightClock = 0;
            for (String z : tempZones) { baseTemp.put(z, (double) Config.TEMP_MAX); tempWiggle.put(z, 0.0); tempWiggleVel.put(z, 0.0); }
            hallucination.active = false; hallucination.zone = null; hallucination.drop = 0;
            night3FlavorTimer = 0; night3FlavorText = "";
            px = roomBedroom.x + roomBedroom.width - 50; py = roomBedroom.y + roomBedroom.height - 45;
            vx = 0; vy = 0; facing = -Math.PI / 2;
        }
        void restartToTitle() {
            mode = GameMode.TITLE;
            modeTimer = 0;
            currentNight = 0;
            resetForNewNight();
            for (Door d : doors) d.open = false;
        }
        void enterEnding(String reason) {
            mode = GameMode.ENDING;
            endingReason = reason;
            endingElapsedTimer = 0;
        }
        void triggerGameOver(String reason) {
            if (mode == GameMode.ENDING) return;
            screenShakeTimer = Math.max(screenShakeTimer, 0.40);
            proceduralAudio.playCreak(Math.max(Config.AUDIO_CREAK_ENTER_GAIN, 0.18));
            enterEnding(reason);
        }
        boolean isGameOver() { return mode == GameMode.ENDING || night3Caught; }
        boolean isEnding() { return mode == GameMode.ENDING; }

        // ============================================================
        // Main tick
        // ============================================================
        void tick() {
            long now = System.nanoTime();
            double dt = clamp((now - lastFrameNanos) / 1_000_000_000.0, 0, 0.05);
            lastFrameNanos = now;
            prevSanity = sanity;

            screenShakeTimer = Math.max(0, screenShakeTimer - dt);
            interactCooldown = Math.max(0, interactCooldown - dt);
            cameraFlashTimer = Math.max(0, cameraFlashTimer - dt);
            closetForcedMessageTimer = Math.max(0, closetForcedMessageTimer - dt);

            if (moving) walkAnimT += dt * 8.0; else walkAnimT *= 0.92;
            if (writing) writeAnimT += dt * 6.0; else writeAnimT *= 0.90;
            if (isHiding()) hideAnimT += dt * 2.2; else hideAnimT *= 0.90;

            updateFlowTimers(dt);

            if (isNightGameplay()) {
                updateNightProgress(dt);
                updateBreathing(dt);
                updateMovement(dt);
                updateHidePunishment(dt);
                updateSanity(dt);
                updateAudioLevels();

                if (mode == GameMode.NIGHT1 || mode == GameMode.NIGHT2) {
                    updateMonster(dt);
                    updateTemperatureBase(dt);
                    updateTemperatureWiggle(dt);
                    updateClosetTimer(dt);
                } else if (mode == GameMode.NIGHT3) {
                    updateNight3Doom(dt);
                    updateNight3Flavor(dt);
                }
            }
            if (mode == GameMode.ENDING) endingElapsedTimer += dt;

            updateHint();
            repaint();
        }
        boolean isNightGameplay() {
            return mode == GameMode.NIGHT1 || mode == GameMode.NIGHT2 || mode == GameMode.NIGHT3;
        }
        void updateFlowTimers(double dt) {
            if (mode == GameMode.NIGHT_INTRO) {
                modeTimer -= dt;
                if (modeTimer <= 0) {
                    if (introForNight == 1) startNight1();
                    else if (introForNight == 2) startNight2();
                    else startNight3();
                }
            } else if (mode == GameMode.CUTSCENE_CALL) {
                modeTimer -= dt;
                advanceCutsceneLine(dt);
                if (modeTimer <= 0) beginNightIntro(2);
            } else if (mode == GameMode.CUTSCENE_MEETING) {
                modeTimer -= dt;
                advanceCutsceneLine(dt);
                if (modeTimer <= 0) beginNightIntro(3);
            }
        }
        void advanceCutsceneLine(double dt) {
            cutsceneLineTimer += dt;
            if (cutsceneLineTimer >= 1.4) { cutsceneLineTimer = 0; cutsceneLineIndex++; }
        }
        void updateNightProgress(double dt) {
            nightClock += dt;
            double duration = (currentNight == 3) ? Config.NIGHT3_DURATION_SEC : Config.NIGHT2_DURATION_SEC;
            nightProgress = clamp(nightClock / duration, 0, 1);
        }

        // ============================================================
        // Movement
        // ============================================================
        void updateMovement(double dt) {
            boolean locked = breathingActive || writing || isHiding() || isGameOver() || interactCooldown > 0;
            if (locked) { vx = 0; vy = 0; moving = false; return; }

            int ax = 0, ay = 0;
            if (keysDown.contains(KeyEvent.VK_W) || keysDown.contains(KeyEvent.VK_UP)) ay -= 1;
            if (keysDown.contains(KeyEvent.VK_S) || keysDown.contains(KeyEvent.VK_DOWN)) ay += 1;
            if (keysDown.contains(KeyEvent.VK_A) || keysDown.contains(KeyEvent.VK_LEFT)) ax -= 1;
            if (keysDown.contains(KeyEvent.VK_D) || keysDown.contains(KeyEvent.VK_RIGHT)) ax += 1;
            double len = Math.hypot(ax, ay);
            double nx = 0, ny = 0;
            if (len > 0) { nx = ax / len; ny = ay / len; }
            vx = nx * speed; vy = ny * speed;
            moving = len > 0;

            if (mode == GameMode.NIGHT1 && !bedroomUnlocked) {
                px += vx * dt; py += vy * dt;
                px = clamp(px, bedroomBounds.x + playerRadius, bedroomBounds.x + bedroomBounds.width - playerRadius);
                py = clamp(py, bedroomBounds.y + playerRadius, bedroomBounds.y + bedroomBounds.height - playerRadius);
                return;
            }
            if (mode == GameMode.NIGHT1 || mode == GameMode.NIGHT2) {
                moveWithWallCollision(dt);
                return;
            }
            if (mode == GameMode.NIGHT3) {
                px += vx * dt; py += vy * dt;
                night3HandleDoorWrap();
            }
        }
        /** Axis-separated movement so the player slides along walls instead of clipping through them. */
        void moveWithWallCollision(double dt) {
            double stepX = vx * dt;
            double stepY = vy * dt;
            if (stepX != 0) {
                double candidateX = px + stepX;
                if (isPositionOpen(candidateX, py)) px = candidateX;
            }
            if (stepY != 0) {
                double candidateY = py + stepY;
                if (isPositionOpen(px, candidateY)) py = candidateY;
            }
        }
        /** True if the player's whole circle would sit inside a room/hallway and not inside a closed door. */
        boolean isPositionOpen(double x, double y) {
            if (!circleWithinWalkableArea(x, y, playerRadius)) return false;
            for (Door d : doors) {
                if (!d.open && circleHitsRect(x, y, playerRadius, d.rect)) return false;
            }
            return true;
        }
        boolean circleWithinWalkableArea(double cx, double cy, double r) {
            return unionContainsPoint(cx, cy)
                    && unionContainsPoint(cx - r, cy) && unionContainsPoint(cx + r, cy)
                    && unionContainsPoint(cx, cy - r) && unionContainsPoint(cx, cy + r);
        }
        boolean unionContainsPoint(double x, double y) {
            for (Rectangle r : walkableRects) if (r.contains(x, y)) return true;
            return false;
        }
        Rectangle night3RoomRect() { return new Rectangle(260, 120, 480, 320); }
        void night3HandleDoorWrap() {
            Rectangle room = night3RoomRect();
            Rectangle inner = new Rectangle(room.x + 16, room.y + 16, room.width - 32, room.height - 32);
            Rectangle doorN = new Rectangle(room.x + room.width / 2 - 30, room.y, 60, 14);
            Rectangle doorS = new Rectangle(room.x + room.width / 2 - 30, room.y + room.height - 14, 60, 14);
            Rectangle doorWst = new Rectangle(room.x, room.y + room.height / 2 - 30, 14, 60);
            Rectangle doorE = new Rectangle(room.x + room.width - 14, room.y + room.height / 2 - 30, 14, 60);
            if (circleHitsRect(px, py, playerRadius, doorN) && vy < 0) {
                gridY -= 1; py = inner.y + inner.height - playerRadius - 2; proceduralAudio.playFootstep(Config.AUDIO_FOOTSTEP_GAIN);
            }
            if (circleHitsRect(px, py, playerRadius, doorS) && vy > 0) {
                gridY += 1; py = inner.y + playerRadius + 2; proceduralAudio.playFootstep(Config.AUDIO_FOOTSTEP_GAIN);
            }
            if (circleHitsRect(px, py, playerRadius, doorWst) && vx < 0) {
                gridX -= 1; px = inner.x + inner.width - playerRadius - 2; proceduralAudio.playFootstep(Config.AUDIO_FOOTSTEP_GAIN);
            }
            if (circleHitsRect(px, py, playerRadius, doorE) && vx > 0) {
                gridX += 1; px = inner.x + playerRadius + 2; proceduralAudio.playFootstep(Config.AUDIO_FOOTSTEP_GAIN);
            }
            px = clamp(px, inner.x + playerRadius, inner.x + inner.width - playerRadius);
            py = clamp(py, inner.y + playerRadius, inner.y + inner.height - playerRadius);
        }

        // ============================================================
        // Monster AI
        // ============================================================
        MonsterNode[] adjacentNodes(MonsterNode n) {
            switch (n) {
                case KITCHEN: return new MonsterNode[]{ MonsterNode.LIVING, MonsterNode.UTILITY };
                case LIVING:  return new MonsterNode[]{ MonsterNode.KITCHEN, MonsterNode.DINING };
                case UTILITY: return new MonsterNode[]{ MonsterNode.KITCHEN, MonsterNode.DINING };
                case DINING:  return new MonsterNode[]{ MonsterNode.LIVING, MonsterNode.UTILITY, MonsterNode.HALL };
                default: return new MonsterNode[0];
            }
        }
        MonsterNode weightedRandomPick(MonsterNode from) {
            double r = Math.random();
            switch (from) {
                case KITCHEN: return (r < 0.50) ? MonsterNode.LIVING : MonsterNode.UTILITY;
                case LIVING:  return (r < 0.20) ? MonsterNode.KITCHEN : MonsterNode.DINING;
                case UTILITY: return (r < 0.20) ? MonsterNode.KITCHEN : MonsterNode.DINING;
                case DINING:
                    if (r < 0.15) return MonsterNode.LIVING;
                    if (r < 0.30) return MonsterNode.UTILITY;
                    return MonsterNode.HALL;
                default: return MonsterNode.KITCHEN;
            }
        }
        MonsterNode stepToward(MonsterNode from, MonsterNode target) {
            if (from == target) return null;
            Map<MonsterNode, MonsterNode> cameFrom = new HashMap<>();
            ArrayDeque<MonsterNode> frontier = new ArrayDeque<>();
            frontier.add(from);
            cameFrom.put(from, null);
            boolean found = false;
            while (!frontier.isEmpty()) {
                MonsterNode current = frontier.poll();
                if (current == target) { found = true; break; }
                for (MonsterNode next : adjacentNodes(current)) {
                    if (!cameFrom.containsKey(next)) {
                        cameFrom.put(next, current);
                        frontier.add(next);
                    }
                }
            }
            if (!found) return null;
            MonsterNode step = target;
            while (cameFrom.get(step) != from && cameFrom.get(step) != null) {
                step = cameFrom.get(step);
            }
            return step;
        }
        String currentPlayerZone() {
            if (roomKitchen.contains(px, py)) return "kitchen";
            if (roomLiving.contains(px, py)) return "living";
            if (roomUtility.contains(px, py)) return "utility";
            if (roomDining.contains(px, py)) return "dining";
            if (roomBedroom.contains(px, py)) return "bedroom";
            return "hall";
        }
        MonsterNode zoneToNode(String zone) {
            switch (zone) {
                case "kitchen": return MonsterNode.KITCHEN;
                case "living": return MonsterNode.LIVING;
                case "utility": return MonsterNode.UTILITY;
                case "dining": return MonsterNode.DINING;
                default: return MonsterNode.HALL;
            }
        }
        String nodeToZone(MonsterNode n) {
            switch (n) {
                case KITCHEN: return "kitchen";
                case LIVING: return "living";
                case UTILITY: return "utility";
                case DINING: return "dining";
                default: return "hall";
            }
        }
        // --- Hunting-mode-only zone<->node mapping (includes BEDROOM). Kept separate from the
        // functions above so Phase-A (pre-hunt) wandering behavior is never affected. ---
        MonsterNode huntZoneToNode(String zone) {
            switch (zone) {
                case "kitchen": return MonsterNode.KITCHEN;
                case "living": return MonsterNode.LIVING;
                case "utility": return MonsterNode.UTILITY;
                case "dining": return MonsterNode.DINING;
                case "bedroom": return MonsterNode.BEDROOM;
                default: return MonsterNode.HALL;
            }
        }
        String huntNodeToZone(MonsterNode n) {
            switch (n) {
                case KITCHEN: return "kitchen";
                case LIVING: return "living";
                case UTILITY: return "utility";
                case DINING: return "dining";
                case BEDROOM: return "bedroom";
                default: return "hall";
            }
        }

        // ============================================================
        // Hunting mode - active once the house is accessible (100 sanity, or Night 2+).
        // The monster becomes a real threat that moves room-to-room looking for the player,
        // rather than a background presence tied to a single staged bedroom-door attack.
        // ============================================================
        void beginHuntingMode() {
            huntingActive = true;
            pendingDoorAttack = false; doorWarningTimer = 0; doorWarningSoundPlayed = false;
            breakingThroughDoor = false; breakThroughTimer = 0; breakThroughSoundPlayed = false;
            encounterActive = false; encounterTimer = 0; hallStayTimer = 0;
            if (monsterNode == MonsterNode.HALL) monsterNode = MonsterNode.DINING;
            previousMonsterNode = null;
            scheduleNextMonsterMove();
        }
        ArrayList<Door> doorsTouchingZone(String zone) {
            ArrayList<Door> result = new ArrayList<>();
            for (Door d : doors) if (zone.equals(d.zoneA) || zone.equals(d.zoneB)) result.add(d);
            return result;
        }
        String otherZone(Door d, String zone) { return zone.equals(d.zoneA) ? d.zoneB : d.zoneA; }
        ArrayList<String> huntNeighborZones(String zone) {
            ArrayList<String> result = new ArrayList<>();
            for (Door d : doorsTouchingZone(zone)) result.add(otherZone(d, zone));
            return result;
        }
        Door doorBetween(MonsterNode a, MonsterNode b) {
            String za = huntNodeToZone(a), zb = huntNodeToZone(b);
            for (Door d : doors) {
                if ((za.equals(d.zoneA) && zb.equals(d.zoneB)) || (za.equals(d.zoneB) && zb.equals(d.zoneA))) return d;
            }
            return null;
        }
        /** BFS over the door-derived room graph. Returns the first hop from fromZone toward targetZone, or null if unreachable/adjacent-only. */
        String huntStepToward(String fromZone, String targetZone) {
            if (fromZone.equals(targetZone)) return null;
            HashMap<String, String> cameFrom = new HashMap<>();
            ArrayDeque<String> frontier = new ArrayDeque<>();
            frontier.add(fromZone);
            cameFrom.put(fromZone, null);
            boolean found = false;
            while (!frontier.isEmpty()) {
                String current = frontier.poll();
                if (current.equals(targetZone)) { found = true; break; }
                for (String next : huntNeighborZones(current)) {
                    if (!cameFrom.containsKey(next)) {
                        cameFrom.put(next, current);
                        frontier.add(next);
                    }
                }
            }
            if (!found) return null;
            String step = targetZone;
            while (true) {
                String pred = cameFrom.get(step);
                if (pred == null || pred.equals(fromZone)) break;
                step = pred;
            }
            return step;
        }
        /** Uniform-random among neighbors, with the room the monster just came from downweighted (not excluded). */
        MonsterNode weightedFallbackPick(MonsterNode from) {
            String fromZone = huntNodeToZone(from);
            ArrayList<String> neighborZones = huntNeighborZones(fromZone);
            if (neighborZones.isEmpty()) return from;
            String previousZone = (previousMonsterNode != null) ? huntNodeToZone(previousMonsterNode) : null;
            double[] weights = new double[neighborZones.size()];
            double totalWeight = 0;
            for (int i = 0; i < neighborZones.size(); i++) {
                double w = (previousZone != null && neighborZones.get(i).equals(previousZone)) ? Config.HUNT_BACKTRACK_WEIGHT : 1.0;
                weights[i] = w;
                totalWeight += w;
            }
            double r = Math.random() * totalWeight;
            double cumulative = 0;
            for (int i = 0; i < neighborZones.size(); i++) {
                cumulative += weights[i];
                if (r <= cumulative) return huntZoneToNode(neighborZones.get(i));
            }
            return huntZoneToNode(neighborZones.get(neighborZones.size() - 1));
        }
        MonsterNode pickHuntNextNode(MonsterNode from) {
            double chance = flashlightOn ? Config.HUNT_TARGET_PLAYER_CHANCE_FLASHLIGHT_ON : Config.HUNT_TARGET_PLAYER_CHANCE_FLASHLIGHT_OFF;
            if (Math.random() < chance) {
                String playerZone = currentPlayerZone();
                if (!playerZone.equals("hall")) {
                    String stepZone = huntStepToward(huntNodeToZone(from), playerZone);
                    if (stepZone != null) return huntZoneToNode(stepZone);
                }
            }
            return weightedFallbackPick(from);
        }
        void beginHuntTransit(MonsterNode target) {
            Door via = doorBetween(monsterNode, target);
            huntTransitActive = true;
            huntTransitFrom = monsterNode;
            huntTransitTo = target;
            huntTransitDoor = via;
            huntTransitHallway = (via != null) ? via.hallway : null;
            double duration = Config.MONSTER_HALLWAY_TRANSIT_SEC;
            if (via != null && !via.open) duration += Config.MONSTER_DOOR_BREAK_SEC;
            huntTransitTimer = duration;
            proceduralAudio.playFootstep(Config.AUDIO_FOOTSTEP_GAIN);
            maybeTriggerHallucination(huntNodeToZone(target));
        }
        boolean isPlayerInHuntTransitHallway() {
            if (huntTransitHallway == null) return false;
            if (huntTransitHallway.contains(px, py)) return true;
            // The dining<->bedroom connector has a second, slightly larger rectangle (added for
            // thermostat placement) that pokes a few pixels outside the main hallway rect.
            if (huntTransitDoor == doorDiningBedroom && preBedroomHall.contains(px, py)) return true;
            return false;
        }
        void updateHuntTransit(double dt) {
            if (isGameOver()) return;
            if (isPlayerInHuntTransitHallway()) {
                triggerGameOver("The hallway wasn't empty. It was already crossing.");
                return;
            }
            huntTransitTimer -= dt;
            if (huntTransitTimer <= 0) {
                if (huntTransitDoor != null && !huntTransitDoor.open) huntTransitDoor.open = true;
                previousMonsterNode = huntTransitFrom;
                monsterNode = huntTransitTo;
                huntTransitActive = false;
                huntTransitFrom = null; huntTransitTo = null; huntTransitDoor = null; huntTransitHallway = null;
                scheduleNextMonsterMove();
            }
        }
        void updateHunting(double dt) {
            if (huntTransitActive) { updateHuntTransit(dt); return; }
            if (isGameOver()) return;
            monsterMoveCooldown -= dt;
            if (monsterMoveCooldown <= 0) {
                MonsterNode next = pickHuntNextNode(monsterNode);
                if (next != monsterNode) beginHuntTransit(next);
                else scheduleNextMonsterMove();
            }
        }
        MonsterNode pickNextMonsterNode(MonsterNode from) {
            if (flashlightOn && Math.random() < Config.FLASHLIGHT_TARGET_BIAS) {
                MonsterNode targetNode = zoneToNode(currentPlayerZone());
                MonsterNode biasedStep = stepToward(from, targetNode);
                if (biasedStep != null) return biasedStep;
            }
            return weightedRandomPick(from);
        }
        void scheduleNextMonsterMove() { monsterMoveCooldown = randRange(effectiveMonsterMoveMinSeconds(), effectiveMonsterMoveMaxSeconds()); }
        void monsterMoveOpportunity() {
            if (isGameOver() || encounterActive || pendingDoorAttack || breakingThroughDoor) return;
            proceduralAudio.playFootstep(Config.AUDIO_FOOTSTEP_GAIN);
            MonsterNode next = pickNextMonsterNode(monsterNode);
            maybeTriggerHallucination(nodeToZone(next));
            monsterNode = next;
            if (monsterNode == MonsterNode.HALL) hallStayTimer = effectiveHallStaySeconds();
            scheduleNextMonsterMove();
        }
        void updateMonster(double dt) {
            if (!huntingActive && houseAccessible()) beginHuntingMode();
            if (huntingActive) { updateHunting(dt); return; }

            updateDoorWarning(dt);
            updateDoorBreakThrough(dt);
            if (encounterActive) {
                encounterTimer -= dt;
                if (encounterTimer <= 0) retreatMonster();
                return;
            }
            if (isGameOver() || breakingThroughDoor) return;
            if (monsterNode == MonsterNode.HALL) {
                if (hallStayTimer > 0) {
                    hallStayTimer -= dt;
                    if (hallStayTimer <= 0) beginDoorWarningThenEncounter();
                }
                return;
            }
            monsterMoveCooldown -= dt;
            if (monsterMoveCooldown <= 0) monsterMoveOpportunity();
        }
        void beginDoorWarningThenEncounter() {
            if (pendingDoorAttack || encounterActive || isGameOver()) return;
            pendingDoorAttack = true;
            doorWarningTimer = randRange(effectiveDoorWarningMinSeconds(), effectiveDoorWarningMaxSeconds());
            doorWarningSoundPlayed = false;
        }
        void updateDoorWarning(double dt) {
            if (!pendingDoorAttack) return;
            if (isGameOver()) { pendingDoorAttack = false; return; }
            if (!doorWarningSoundPlayed) { doorWarningSoundPlayed = true; proceduralAudio.playCreak(Config.AUDIO_CREAK_WARNING_GAIN); }
            doorWarningTimer -= dt;
            if (doorWarningTimer <= 0) {
                pendingDoorAttack = false;
                if (houseAccessible() && !doorDiningBedroom.open) {
                    breakingThroughDoor = true;
                    breakThroughTimer = Config.MONSTER_DOOR_BREAK_SEC;
                    breakThroughSoundPlayed = false;
                } else {
                    beginEncounter();
                }
            }
        }
        void updateDoorBreakThrough(double dt) {
            if (!breakingThroughDoor) return;
            if (isGameOver()) { breakingThroughDoor = false; return; }
            if (!breakThroughSoundPlayed) { breakThroughSoundPlayed = true; proceduralAudio.playCreak(Config.AUDIO_CREAK_ENTER_GAIN); }
            breakThroughTimer -= dt;
            if (breakThroughTimer <= 0) {
                breakingThroughDoor = false;
                doorDiningBedroom.open = true;
                beginEncounter();
            }
        }
        void beginEncounter() {
            if (encounterActive || isGameOver()) return;
            safeHideAccumulator = 0;
            proceduralAudio.playCreak(Config.AUDIO_CREAK_ENTER_GAIN);
            encounterActive = true;
            encounterTimer = Config.MONSTER_ENCOUNTER_DURATION_SEC;
            screenShakeTimer = Math.max(screenShakeTimer, 0.35);
            if (!isHiding()) triggerGameOver("You weren't hiding. The monster rushes in \u2014 and it's over.");
        }
        void retreatMonster() {
            encounterActive = false; encounterTimer = 0;
            monsterNode = MonsterNode.DINING;
            hallStayTimer = 0;
            scheduleNextMonsterMove();
        }
        double effectiveMonsterMoveMinSeconds() {
            double base = Config.MONSTER_MOVE_MIN_SEC;
            if (currentNight == 1) return base;
            double factor = (currentNight == 3) ? (0.35 - 0.20 * nightProgress) : (0.85 - 0.45 * nightProgress);
            return base * Math.max(0.12, factor);
        }
        double effectiveMonsterMoveMaxSeconds() {
            double base = Config.MONSTER_MOVE_MAX_SEC;
            if (currentNight == 1) return base;
            double factor = (currentNight == 3) ? (0.45 - 0.28 * nightProgress) : (0.95 - 0.55 * nightProgress);
            return base * Math.max(0.15, factor);
        }
        double effectiveHallStaySeconds() {
            if (currentNight == 1) return Config.MONSTER_HALL_STAY_SEC;
            return Math.max(0.35, Config.MONSTER_HALL_STAY_SEC * (0.90 - 0.55 * nightProgress));
        }
        double effectiveDoorWarningMinSeconds() {
            double base = (currentNight == 1) ? Config.DOOR_WARNING_MIN_SEC : Math.max(0.15, Config.DOOR_WARNING_MIN_SEC * (0.85 - 0.55 * nightProgress));
            return base * (flashlightOn ? Config.FLASHLIGHT_WARNING_SCALE_ON : Config.FLASHLIGHT_WARNING_SCALE_OFF);
        }
        double effectiveDoorWarningMaxSeconds() {
            double base = (currentNight == 1) ? Config.DOOR_WARNING_MAX_SEC : Math.max(0.35, Config.DOOR_WARNING_MAX_SEC * (0.85 - 0.60 * nightProgress));
            return base * (flashlightOn ? Config.FLASHLIGHT_WARNING_SCALE_ON : Config.FLASHLIGHT_WARNING_SCALE_OFF);
        }
        Point2D getMonsterPoint() {
            if (encounterActive || pendingDoorAttack || breakingThroughDoor) {
                return new Point2D.Double(doorDiningBedroom.rect.x + 20, doorDiningBedroom.rect.y + doorDiningBedroom.rect.height / 2.0);
            }
            if (huntingActive && huntTransitActive && huntTransitHallway != null) {
                return new Point2D.Double(huntTransitHallway.x + huntTransitHallway.width / 2.0, huntTransitHallway.y + huntTransitHallway.height / 2.0);
            }
            String zone = huntingActive ? huntNodeToZone(monsterNode) : nodeToZone(monsterNode);
            Rectangle zr = zoneRect(zone);
            return new Point2D.Double(zr.x + zr.width / 2.0, zr.y + zr.height / 2.0);
        }
        Rectangle zoneRect(String zone) {
            switch (zone) {
                case "kitchen": return roomKitchen;
                case "living": return roomLiving;
                case "utility": return roomUtility;
                case "dining": return roomDining;
                case "hall": return preBedroomHall;
                default: return roomBedroom;
            }
        }

        // ============================================================
        // Temperature / thermostats
        // ============================================================
        double actualTemp(String zone) { return clamp(baseTemp.get(zone) + tempWiggle.get(zone), Config.TEMP_MIN, Config.TEMP_MAX); }
        String effectiveMonsterZone() {
            if (encounterActive) return "bedroom";
            if (pendingDoorAttack || breakingThroughDoor) return "hall";
            if (huntingActive) {
                if (huntTransitActive) return "hall";
                return huntNodeToZone(monsterNode);
            }
            return nodeToZone(monsterNode);
        }
        void updateTemperatureBase(double dt) {
            String hotZone = effectiveMonsterZone();
            for (String z : tempZones) {
                double current = baseTemp.get(z);
                double target = z.equals(hotZone) ? Config.TEMP_MIN : Config.TEMP_MAX;
                if (hallucination.active && z.equals(hallucination.zone)) {
                    target = clamp(target - hallucination.drop, Config.TEMP_MIN, Config.TEMP_MAX);
                }
                double delta = target - current;
                double step = Math.signum(delta) * Math.min(Math.abs(delta), Config.TEMP_RATE_PER_SEC * dt);
                baseTemp.put(z, clamp(current + step, Config.TEMP_MIN, Config.TEMP_MAX));
            }
        }
        void updateTemperatureWiggle(double dt) {
            for (String z : tempZones) {
                double w = tempWiggle.get(z);
                double v = tempWiggleVel.get(z);
                double accel = (Math.random() * 2 - 1) * Config.TEMP_FLUCT_ACCEL;
                v += accel * dt;
                v -= v * Config.TEMP_FLUCT_DAMPING * dt;
                w += v * dt;
                double amp = Config.TEMP_FLUCT_AMPLITUDE;
                if (w > amp) { w = amp; v *= -0.4; }
                if (w < -amp) { w = -amp; v *= -0.4; }
                tempWiggle.put(z, w);
                tempWiggleVel.put(z, v);
            }
        }
        void clearHallucination() { hallucination.active = false; hallucination.zone = null; hallucination.drop = 0; }
        void maybeTriggerHallucination(String monsterDestinationZone) {
            clearHallucination();
            double chance = Config.HALLUCINATION_CHANCE;
            if (currentNight >= 2) chance *= (1.0 + 1.5 * nightProgress);
            chance = Math.min(0.85, chance);
            if (Math.random() > chance) return;
            ArrayList<String> candidates = new ArrayList<>();
            for (String z : tempZones) if (!z.equals(monsterDestinationZone)) candidates.add(z);
            if (candidates.isEmpty()) return;
            String pick = candidates.get((int) (Math.random() * candidates.size()));
            int drop = Config.HALLUCINATION_DROP_MIN + (int) (Math.random() * (Config.HALLUCINATION_DROP_MAX - Config.HALLUCINATION_DROP_MIN + 1));
            hallucination.active = true; hallucination.zone = pick; hallucination.drop = drop;
            double cur = baseTemp.get(pick);
            baseTemp.put(pick, clamp(cur - drop, Config.TEMP_MIN, Config.TEMP_MAX));
        }
        boolean isNearThermostat(Thermostat t) { return centerMostlyInside(px, py, playerRadius, t.expanded()); }
        Thermostat nearestOpenThermostatInRange() {
            for (Thermostat t : allThermostats) if (t.open && isNearThermostat(t)) return t;
            return null;
        }
        boolean thermostatMapViewActive() { return !breathingActive && nearestOpenThermostatInRange() != null; }

        // ============================================================
        // Sanity
        // ============================================================
        void updateSanity(double dt) {
            if (isGameOver()) return;
            if (writing) sanity += Config.SANITY_WRITE_GAIN_PER_SEC * dt;
            for (Thermostat t : allThermostats) if (t.open) sanity -= Config.SANITY_THERMO_LOSS_PER_SEC * dt;
            if (moving) sanity -= Config.SANITY_WALK_LOSS_PER_SEC * dt;
            if (hidingInBed || hidingInCloset) sanity -= Config.SANITY_HIDE_LOSS_PER_SEC * dt;
            normalizeSanity();
            updateSanityHold(dt);
            if (sanity <= 0) triggerGameOver("Your sanity snaps. The house wins.");
        }
        void normalizeSanity() { sanity = clamp(sanity, 0, 100); }
        void updateSanityHold(double dt) {
            if (prevSanity < 100 && sanity >= 100) {
                sanity = 100;
                sanityHoldTimer = Config.SANITY_HOLD_AT_MAX_SEC;
                if (currentNight == 1) bedroomUnlocked = true;
            }
            if (sanityHoldTimer > 0) {
                sanityHoldTimer -= dt;
                sanity = 100;
                if (sanityHoldTimer < 0) sanityHoldTimer = 0;
            }
        }

        // ============================================================
        // Breathing minigame (bed only)
        // ============================================================
        boolean isHiding() { return hidingInBed || hidingInCloset; }
        void startBreathingMinigame() {
            if (breathingActive || isGameOver()) return;
            writing = false;
            for (Thermostat t : allThermostats) t.open = false;
            hidingInBed = false; hidingInCloset = false;
            breathingActive = true; breathSuccesses = 0; breathSlider = 0.02; breathDirection = 1;
            screenShakeTimer = Math.max(screenShakeTimer, 0.22);
        }
        void endBreathingMinigame() { breathingActive = false; proceduralAudio.playClick(); }
        void attemptBreathHit() {
            if (!breathingActive || isGameOver()) return;
            boolean success = breathSlider >= Config.BREATH_TARGET_START && breathSlider <= Config.BREATH_TARGET_END;
            if (success) {
                breathSuccesses++;
                proceduralAudio.playClick();
                if (breathSuccesses >= Config.BREATH_REQUIRED_SUCCESSES) endBreathingMinigame();
            } else {
                sanity -= Config.SANITY_BREATH_FAIL_PENALTY;
                normalizeSanity();
                breathSuccesses = 0;
                screenShakeTimer = Math.max(screenShakeTimer, 0.30);
                proceduralAudio.playFailBuzz();
            }
        }
        void updateBreathing(double dt) {
            if (!breathingActive) return;
            breathSlider += breathDirection * Config.BREATH_SLIDER_SPEED * dt;
            if (breathSlider >= 1) { breathSlider = 1; breathDirection = -1; }
            if (breathSlider <= 0) { breathSlider = 0; breathDirection = 1; }
        }

        // ============================================================
        // Hide punishment (bed -> breathing) and closet timers (forced-out + cooldown)
        // ============================================================
        void updateHidePunishment(double dt) {
            if (encounterActive && !isHiding() && !isGameOver()) {
                triggerGameOver("You left hiding while the monster was still in the room.");
                return;
            }
            if (huntingActive && !isGameOver()) {
                String monsterZone = huntNodeToZone(monsterNode);
                if (monsterZone.equals(currentPlayerZone()) && !isHiding()) {
                    triggerGameOver("It's in the room with you.");
                    return;
                }
            }
            boolean safeToAccumulate = hidingInBed && !encounterActive && !pendingDoorAttack && !isGameOver() && !breathingActive;
            if (safeToAccumulate) {
                safeHideAccumulator += dt;
                if (safeHideAccumulator >= Config.HIDE_MAX_SAFE_TOTAL_SEC) {
                    hidingInBed = false;
                    startBreathingMinigame();
                }
            } else if (!hidingInBed) {
                safeHideAccumulator = 0;
            }
        }
        void updateClosetTimer(double dt) {
            if (!closetCooldowns.isEmpty()) {
                ArrayList<String> keys = new ArrayList<>(closetCooldowns.keySet());
                for (String key : keys) {
                    double remaining = closetCooldowns.get(key) - dt;
                    if (remaining <= 0) closetCooldowns.remove(key);
                    else closetCooldowns.put(key, remaining);
                }
            }
            if (!hidingInCloset) { closetHideTimer = 0; return; }
            closetHideTimer += dt;
            boolean unsafeToEject = encounterActive || pendingDoorAttack;
            if (closetHideTimer >= Config.CLOSET_MAX_HIDE_SEC && !unsafeToEject) {
                if (currentClosetId != null) closetCooldowns.put(currentClosetId, Config.CLOSET_REENTRY_COOLDOWN_SEC);
                hidingInCloset = false;
                closetHideTimer = 0;
                closetForcedMessageTimer = 2.4;
                currentClosetId = null;
            }
        }

        // ============================================================
        // Interactable lookups
        // ============================================================
        boolean houseAccessible() { return currentNight >= 2 || (currentNight == 1 && bedroomUnlocked); }
        Door findInteractableDoor() {
            if (!houseAccessible()) return null;
            for (Door d : doors) if (isNearDoor(d)) return d;
            return null;
        }
        Closet findInteractableCloset() {
            if (!houseAccessible()) return null;
            for (Closet c : closets) {
                if (centerMostlyInside(px, py, playerRadius, c.expanded())) {
                    if (closetCooldowns.containsKey(c.id)) return null;
                    return c;
                }
            }
            return null;
        }
        Thermostat findInteractableThermostat() {
            if (centerMostlyInside(px, py, playerRadius, thermoBedroom.expanded())) return thermoBedroom;
            if (!houseAccessible()) return null;
            for (Thermostat t : hallThermostats) if (centerMostlyInside(px, py, playerRadius, t.expanded())) return t;
            return null;
        }
        Prop findInteractableBedroomProp() {
            for (Prop p : bedroomProps) if (centerMostlyInside(px, py, playerRadius, p.expanded())) return p;
            return null;
        }
        boolean sleepingPillsObjectiveActive() {
            if (currentNight == 1) return bedroomUnlocked;
            if (currentNight == 2) return monsterPhotosPlaced >= 3;
            return false;
        }
        boolean nearKitchenPills() {
            return sleepingPillsObjectiveActive() && !hasSleepingPills && centerMostlyInside(px, py, playerRadius, kitchenPills.expanded());
        }

        // ============================================================
        // Interaction dispatch
        // ============================================================
        void attemptInteract() {
            if (mode == GameMode.TITLE) { beginNightIntro(1); return; }
            if (isEnding()) { restartToTitle(); return; }
            if (breathingActive) { attemptBreathHit(); return; }
            interactCooldown = Math.max(interactCooldown, 0.22);

            if (nearKitchenPills()) {
                hasSleepingPills = true;
                proceduralAudio.playClick();
                return;
            }
            Door door = findInteractableDoor();
            if (door != null) {
                if (isHiding() || writing) { proceduralAudio.playClick(); return; }
                door.open = !door.open;
                proceduralAudio.playClick();
                return;
            }
            Closet closet = findInteractableCloset();
            if (closet != null) {
                if (writing) { proceduralAudio.playClick(); return; }
                if (hidingInCloset) {
                    hidingInCloset = false;
                    currentClosetId = null;
                } else {
                    hidingInCloset = true; hidingInBed = false; writing = false;
                    currentClosetId = closet.id;
                    closetHideTimer = 0;
                }
                proceduralAudio.playClick();
                return;
            }
            Thermostat thermostat = findInteractableThermostat();
            if (thermostat != null) {
                if (writing || isHiding()) { proceduralAudio.playClick(); return; }
                thermostat.open = !thermostat.open;
                if (thermostat.open) {
                    sanity -= Config.SANITY_THERMO_OPEN_HIT;
                    screenShakeTimer = Math.max(screenShakeTimer, 0.08);
                }
                normalizeSanity();
                proceduralAudio.playClick();
                return;
            }
            Prop prop = findInteractableBedroomProp();
            if (prop != null) { handlePropInteract(prop); return; }

            proceduralAudio.playClick();
        }
        void handlePropInteract(Prop prop) {
            if (breathingActive && (prop == desk || prop == bed)) { proceduralAudio.playClick(); return; }
            if (prop == desk) {
                if (currentNight == 2 && holdingPhoto) {
                    placeHeldPhotoAtDesk();
                } else {
                    writing = !writing;
                    if (writing) { hidingInBed = false; hidingInCloset = false; }
                    proceduralAudio.playClick();
                }
                return;
            }
            if (prop == bed) {
                if (encounterActive && hidingInBed) {
                    triggerGameOver("You left the bed while the monster was still in the room.");
                    return;
                }
                if (currentNight == 1 && bedroomUnlocked && hasSleepingPills) {
                    mode = GameMode.CUTSCENE_CALL; modeTimer = 7.0; cutsceneLineIndex = 0; cutsceneLineTimer = 0;
                    proceduralAudio.playClick();
                    return;
                }
                if (currentNight == 2 && monsterPhotosPlaced >= 3 && hasSleepingPills) {
                    mode = GameMode.CUTSCENE_MEETING; modeTimer = 6.0; cutsceneLineIndex = 0; cutsceneLineTimer = 0;
                    proceduralAudio.playClick();
                    return;
                }
                hidingInBed = !hidingInBed;
                if (hidingInBed) { writing = false; hidingInCloset = false; }
                proceduralAudio.playClick();
                return;
            }
            if (prop == pillBottle) {
                if (pillCharges <= 0) { proceduralAudio.playClick(); return; }
                if (sanity > Config.PILLS_CAP) { proceduralAudio.playClick(); return; }
                double before = sanity;
                double target = Math.min(Config.PILLS_CAP, before + Config.PILLS_GAIN);
                if (target > before) { sanity = target; pillCharges--; proceduralAudio.playPillTone(); }
                else proceduralAudio.playClick();
                normalizeSanity();
            }
        }
        void placeHeldPhotoAtDesk() {
            proceduralAudio.playClick();
            if (heldPhotoHasMonster) monsterPhotosPlaced++;
            holdingPhoto = false; heldPhotoHasMonster = false;
            checkFilmOutFailure();
        }

        // ============================================================
        // Thermal camera
        // ============================================================
        double smallestAngleDifference(double a, double b) {
            double d = b - a;
            while (d > Math.PI) d -= Math.PI * 2;
            while (d < -Math.PI) d += Math.PI * 2;
            return d;
        }
        double cross(double ox, double oy, double ax, double ay, double bx, double by) {
            return (ax - ox) * (by - oy) - (ay - oy) * (bx - ox);
        }
        boolean segmentsIntersect(double ax, double ay, double bx, double by, double cx, double cy, double dx, double dy) {
            double d1 = cross(cx, cy, dx, dy, ax, ay);
            double d2 = cross(cx, cy, dx, dy, bx, by);
            double d3 = cross(ax, ay, bx, by, cx, cy);
            double d4 = cross(ax, ay, bx, by, dx, dy);
            return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
        }
        boolean segmentIntersectsRect(double x0, double y0, double x1, double y1, Rectangle rect) {
            if (rect.contains(x0, y0) || rect.contains(x1, y1)) return true;
            double[] rx = { rect.x, rect.x + rect.width, rect.x + rect.width, rect.x };
            double[] ry = { rect.y, rect.y, rect.y + rect.height, rect.y + rect.height };
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                if (segmentsIntersect(x0, y0, x1, y1, rx[i], ry[i], rx[j], ry[j])) return true;
            }
            return false;
        }
        boolean hasLineOfSight(double x0, double y0, double x1, double y1) {
            for (Door d : doors) {
                if (!d.open && segmentIntersectsRect(x0, y0, x1, y1, d.rect)) return false;
            }
            return true;
        }
        boolean monsterInCameraCone() {
            Point2D mp = getMonsterPoint();
            double dx = mp.getX() - px, dy = mp.getY() - py;
            double dist = Math.hypot(dx, dy);
            if (dist > Config.CAM_RANGE_PX) return false;
            double angleTo = Math.atan2(dy, dx);
            double diff = smallestAngleDifference(facing, angleTo);
            double halfArc = Math.toRadians(Config.CAM_ARC_DEG) * 0.5;
            if (Math.abs(diff) > halfArc) return false;
            return hasLineOfSight(px, py, mp.getX(), mp.getY());
        }
        void tryTakePhoto() {
            if (currentNight != 2 || mode != GameMode.NIGHT2) return;
            if (isGameOver()) return;
            if (breathingActive || writing || hidingInBed) return;
            if (holdingPhoto) return;
            if (filmRemaining <= 0) { proceduralAudio.playClick(); return; }
            cameraFlashTimer = Config.CAM_FLASH_SEC;
            filmRemaining--;
            boolean caught = monsterInCameraCone();
            holdingPhoto = true;
            heldPhotoHasMonster = caught;
            screenShakeTimer = Math.max(screenShakeTimer, caught ? 0.22 : 0.10);
            proceduralAudio.playClick();
        }
        void checkFilmOutFailure() {
            if (!Config.FAIL_NIGHT2_ON_FILM_OUT) return;
            if (filmRemaining <= 0 && !holdingPhoto && monsterPhotosPlaced < 3) {
                triggerGameOver("You're out of film, and you have nothing left to show him.");
            }
        }

        // ============================================================
        // Night 3 doom clock + misleading flavor text
        // ============================================================
        void updateNight3Doom(double dt) {
            if (night3Caught) {
                night3EndTimer -= dt;
                if (night3EndTimer <= 0) enterEnding(null);
                return;
            }
            double doom = clamp(nightClock / Config.NIGHT3_DURATION_SEC, 0, 1);
            if (doom > Config.NIGHT3_CATCH_THRESHOLD) {
                double chance = Config.NIGHT3_CATCH_BASE_CHANCE + Config.NIGHT3_CATCH_GROWTH * (doom - Config.NIGHT3_CATCH_THRESHOLD);
                if (Math.random() < chance) {
                    night3Caught = true;
                    night3EndTimer = Config.NIGHT3_CATCH_END_DELAY_SEC;
                    screenShakeTimer = Math.max(screenShakeTimer, 0.5);
                    proceduralAudio.playCreak(Math.max(Config.AUDIO_CREAK_ENTER_GAIN, 0.20));
                }
            }
        }
        void updateNight3Flavor(double dt) {
            night3FlavorTimer -= dt;
            if (night3FlavorTimer <= 0) {
                night3FlavorTimer = randRange(Config.NIGHT3_FLAVOR_MIN_SEC, Config.NIGHT3_FLAVOR_MAX_SEC);
                night3FlavorText = NIGHT3_FALSE_HINTS[(int) (Math.random() * NIGHT3_FALSE_HINTS.length)];
            }
        }

        // ============================================================
        // Hint text + HUD objective text
        // ============================================================
        void updateHint() {
            if (mode == GameMode.TITLE) { hintVisible = true; hintText = "Click anywhere or press E to begin"; return; }
            if (mode == GameMode.NIGHT_INTRO || mode == GameMode.CUTSCENE_CALL || mode == GameMode.CUTSCENE_MEETING) {
                hintVisible = false; hintText = ""; return;
            }
            if (isEnding()) { hintVisible = false; hintText = ""; return; }
            if (breathingActive) { hintVisible = false; hintText = ""; return; }
            if (closetForcedMessageTimer > 0) { hintVisible = true; hintText = "You couldn't stay hidden any longer."; return; }
            if (currentNight == 1 && !bedroomUnlocked && px <= bedroomBounds.x + 40) {
                hintVisible = true; hintText = "Locked. You need to calm down first.";
                return;
            }
            if (nearKitchenPills()) { hintVisible = true; hintText = "Press E to take the sleeping pills"; return; }

            Door door = findInteractableDoor();
            if (door != null) { hintVisible = true; hintText = "Press E to " + (door.open ? "close" : "open") + " door"; return; }
            Closet closet = findInteractableCloset();
            if (closet != null) { hintVisible = true; hintText = hidingInCloset ? "Press E to leave closet" : "Press E to hide in closet"; return; }
            Thermostat thermostat = findInteractableThermostat();
            if (thermostat != null) { hintVisible = true; hintText = "Press E to " + (thermostat.open ? "close" : "open") + " thermostat"; return; }
            Prop prop = findInteractableBedroomProp();
            if (prop != null) {
                hintVisible = true;
                if (prop == desk) {
                    if (currentNight == 2 && holdingPhoto) hintText = "Press E to place photo on the desk";
                    else hintText = writing ? "Press E to stop writing" : "Press E to write";
                } else if (prop == bed) {
                    if (currentNight == 1 && bedroomUnlocked && hasSleepingPills) hintText = "Press E to sleep (end Night 1)";
                    else if (currentNight == 2 && monsterPhotosPlaced >= 3 && hasSleepingPills) hintText = "Press E to sleep (end Night 2)";
                    else hintText = hidingInBed ? "Press E to stop hiding" : "Press E to hide in bed";
                } else if (prop == pillBottle) {
                    if (pillCharges <= 0) hintText = "No pills left";
                    else if (sanity > Config.PILLS_CAP) hintText = "Pills won't help above 50%";
                    else hintText = "Press E to take a pill";
                }
                return;
            }
            hintVisible = false; hintText = "";
        }
        String currentObjectiveText() {
            if (currentNight == 1) {
                if (!bedroomUnlocked) return "Reach 100% sanity";
                if (!hasSleepingPills) return "Get your sleeping pills from the Kitchen";
                return "Return to bed";
            }
            if (currentNight == 2) {
                if (monsterPhotosPlaced < 3) return "Place 3 monster photos at the Desk (" + monsterPhotosPlaced + "/3)";
                if (!hasSleepingPills) return "Get your sleeping pills from the Kitchen";
                return "Return to bed";
            }
            if (currentNight == 3) return night3FlavorText;
            return "";
        }

        // ============================================================
        // Audio level updates
        // ============================================================
        void updateAudioLevels() {
            double panic = clamp(1.0 - sanity / 100.0, 0, 1);
            proceduralAudio.setMasterLevel(Config.AUDIO_MASTER_BASE + panic * Config.AUDIO_MASTER_PANIC_BOOST);
            proceduralAudio.setAmbientLevel(Config.AUDIO_AMBIENT_BASE + panic * 0.020);
        }

        // ============================================================
        // Input
        // ============================================================
        @Override public void keyTyped(KeyEvent e) { }
        @Override public void keyPressed(KeyEvent e) {
            proceduralAudio.ensureRunning();
            keysDown.add(e.getKeyCode());
            if (e.getKeyCode() == KeyEvent.VK_E) { if (!eKeyLatch) { eKeyLatch = true; attemptInteract(); } }
            if (e.getKeyCode() == KeyEvent.VK_C) { if (!cKeyLatch) { cKeyLatch = true; tryTakePhoto(); } }
            if (e.getKeyCode() == KeyEvent.VK_F) { flashlightOn = !flashlightOn; proceduralAudio.playClick(); }
            if (e.getKeyCode() == KeyEvent.VK_R) { restartToTitle(); }
        }
        @Override public void keyReleased(KeyEvent e) {
            keysDown.remove(e.getKeyCode());
            if (e.getKeyCode() == KeyEvent.VK_E) eKeyLatch = false;
            if (e.getKeyCode() == KeyEvent.VK_C) cKeyLatch = false;
        }
        @Override public void mouseClicked(MouseEvent e) {
            proceduralAudio.ensureRunning();
            if (mode == GameMode.TITLE) { beginNightIntro(1); return; }
            if (breathingActive) attemptBreathHit();
        }
        @Override public void mouseMoved(MouseEvent e) {
            double dx = e.getX() - px, dy = e.getY() - py;
            if (Math.hypot(dx, dy) > 2) facing = Math.atan2(dy, dx);
        }
        @Override public void mouseDragged(MouseEvent e) { mouseMoved(e); }
        @Override public void mousePressed(MouseEvent e) { }
        @Override public void mouseReleased(MouseEvent e) { }
        @Override public void mouseEntered(MouseEvent e) { }
        @Override public void mouseExited(MouseEvent e) { }

        // ============================================================
        // Rendering
        // ============================================================
        @Override
        protected void paintComponent(Graphics gg) {
            super.paintComponent(gg);
            Graphics2D g = (Graphics2D) gg.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            applyScreenShakeTransform(g);

            switch (mode) {
                case TITLE: drawTitleScreen(g); g.dispose(); return;
                case NIGHT_INTRO: drawNightIntroCard(g); g.dispose(); return;
                case CUTSCENE_CALL: drawPhoneCallCutscene(g); g.dispose(); return;
                case CUTSCENE_MEETING: drawDoctorMeetingCutscene(g); g.dispose(); return;
                case ENDING: drawEndingScreen(g); g.dispose(); return;
                default: break;
            }

            drawStaticBackground(g);
            if (mode == GameMode.NIGHT1) drawNight1(g);
            else if (mode == GameMode.NIGHT2) drawNight2(g);
            else if (mode == GameMode.NIGHT3) drawNight3(g);
            g.dispose();
        }
        void applyScreenShakeTransform(Graphics2D g) {
            if (screenShakeTimer <= 0) return;
            double panic = clamp(1.0 - sanity / 100.0, 0, 1);
            double magnitude = (panic * 2.4 + 0.8) + (encounterActive ? 1.6 : 0) + (breathingActive ? 1.1 : 0);
            int dx = (int) ((Math.random() - 0.5) * magnitude * 2);
            int dy = (int) ((Math.random() - 0.5) * magnitude * 2);
            g.translate(dx, dy);
        }
        void drawStaticBackground(Graphics2D g) {
            g.setColor(new Color(10, 14, 20));
            g.fillRect(0, 0, W, H);
            g.setColor(new Color(255, 255, 255, 16));
            for (int i = 0; i < 140; i++) {
                g.fillRect((int) (Math.random() * W), (int) (Math.random() * H), 1, 1);
            }
        }

        void drawNight1(Graphics2D g) {
            boolean showMap = thermostatMapViewActive();
            if (showMap) {
                for (Rectangle h : hallways) drawHallway(g, h);
                drawHallway(g, preBedroomHall);
                drawRoom(g, roomKitchen, "Kitchen", new Color(255, 255, 255, 32));
                drawRoom(g, roomLiving, "Living Room", new Color(255, 255, 255, 32));
                drawRoom(g, roomUtility, "Utility Room", new Color(255, 255, 255, 32));
                drawRoom(g, roomDining, "Dining Room", new Color(255, 255, 255, 32));
                drawRoom(g, roomBedroom, "Bedroom", new Color(140, 190, 255, 64));
                drawTemperaturesOnMap(g);
            } else if (bedroomUnlocked) {
                for (Rectangle h : hallways) drawHallway(g, h);
                drawHallway(g, preBedroomHall);
                drawRoom(g, roomKitchen, "Kitchen", new Color(255, 255, 255, 26));
                drawRoom(g, roomLiving, "Living Room", new Color(255, 255, 255, 26));
                drawRoom(g, roomUtility, "Utility Room", new Color(255, 255, 255, 26));
                drawRoom(g, roomDining, "Dining Room", new Color(255, 255, 255, 26));
                drawRoom(g, roomBedroom, "Bedroom", new Color(140, 190, 255, 64));
                for (Door d : doors) drawDoor(g, d);
                drawProp(g, kitchenPills);
            } else {
                drawRoom(g, roomBedroom, "Bedroom", new Color(140, 190, 255, 64));
                g.setColor(new Color(255, 255, 255, 10));
                g.fillRect(roomBedroom.x - 30, roomBedroom.y + 70, 30, 30);
            }
            for (Prop p : bedroomProps) drawProp(g, p);
            drawThermostatIcon(g, thermoBedroom);
            drawPlayerSprite(g);
            drawMonsterSilhouette(g);
            drawBreathingBar(g);
            applyLightingOverlay(g);
            drawInRoomDangerGlow(g);
            drawHud(g);
            drawHintBubble(g);
        }

        void drawNight2(Graphics2D g) {
            boolean showMap = thermostatMapViewActive();
            if (showMap) {
                for (Rectangle h : hallways) drawHallway(g, h);
                drawHallway(g, preBedroomHall);
                drawRoom(g, roomKitchen, "Kitchen", new Color(255, 255, 255, 32));
                drawRoom(g, roomLiving, "Living Room", new Color(255, 255, 255, 32));
                drawRoom(g, roomUtility, "Utility Room", new Color(255, 255, 255, 32));
                drawRoom(g, roomDining, "Dining Room", new Color(255, 255, 255, 32));
                drawRoom(g, roomBedroom, "Bedroom", new Color(140, 190, 255, 64));
                drawTemperaturesOnMap(g);
            } else {
                for (Rectangle h : hallways) drawHallway(g, h);
                drawHallway(g, preBedroomHall);
                drawRoom(g, roomKitchen, " ", new Color(255, 255, 255, 18));
                drawRoom(g, roomLiving, " ", new Color(255, 255, 255, 18));
                drawRoom(g, roomUtility, " ", new Color(255, 255, 255, 18));
                drawRoom(g, roomDining, " ", new Color(255, 255, 255, 18));
                drawRoom(g, roomBedroom, " ", new Color(140, 190, 255, 40));
            }
            for (Door d : doors) drawDoor(g, d);
            for (Closet c : closets) drawClosetIcon(g, c);
            for (Thermostat t : hallThermostats) drawThermostatIcon(g, t);
            drawThermostatIcon(g, thermoBedroom);
            for (Prop p : bedroomProps) drawProp(g, p);
            drawProp(g, kitchenPills);
            drawPlayerSprite(g);
            drawMonsterSilhouette(g);
            drawBreathingBar(g);
            applyLightingOverlay(g);
            drawInRoomDangerGlow(g);
            drawCameraDetectionFeedback(g);
            if (holdingPhoto) drawHeldPhotoThumbnail(g);
            drawHud(g);
            drawHintBubble(g);
        }
        /** Pulsing red glow at the center of the room you're hiding in, when the monster is in there with you. */
        void drawInRoomDangerGlow(Graphics2D g) {
            if (!huntingActive || !isHiding()) return;
            String monsterZone = huntNodeToZone(monsterNode);
            if (!monsterZone.equals(currentPlayerZone())) return;
            Rectangle r = zoneRect(monsterZone);
            int cx = r.x + r.width / 2, cy = r.y + r.height / 2;
            float pulse = (float) (0.35 + 0.30 * Math.sin(System.currentTimeMillis() * 0.006));
            int radius = 90;
            RadialGradientPaint glow = new RadialGradientPaint(
                    new Point(cx, cy), radius, new float[]{ 0f, 1f },
                    new Color[]{ new Color(255, 30, 30, (int) (150 * pulse)), new Color(255, 30, 30, 0) });
            g.setPaint(glow);
            g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        }
        /** Soft pulsing red glow around the screen edges when the monster is currently framed for a photo. */
        void drawCameraDetectionFeedback(Graphics2D g) {
            if (holdingPhoto || breathingActive || writing || hidingInBed) return;
            if (!monsterInCameraCone()) return;
            float pulse = (float) (0.25 + 0.20 * Math.sin(System.currentTimeMillis() * 0.010));
            Color edge = new Color(255, 30, 30, (int) (90 * pulse));
            Color clear = new Color(255, 30, 30, 0);
            int band = 60;
            g.setPaint(new GradientPaint(0, 0, edge, 0, band, clear));
            g.fillRect(0, 0, W, band);
            g.setPaint(new GradientPaint(0, H, edge, 0, H - band, clear));
            g.fillRect(0, H - band, W, band);
            g.setPaint(new GradientPaint(0, 0, edge, band, 0, clear));
            g.fillRect(0, 0, band, H);
            g.setPaint(new GradientPaint(W, 0, edge, W - band, 0, clear));
            g.fillRect(W - band, 0, band, H);
        }

        void drawNight3(Graphics2D g) {
            drawNight3Room(g);
            drawPlayerSprite(g);
            drawBreathingBar(g);
            applyLightingOverlay(g);
            if (night3Caught) drawCaughtOverlay(g);
            drawHud(g);
            drawHintBubble(g);
        }
        void drawNight3Room(Graphics2D g) {
            Rectangle room = night3RoomRect();
            g.setColor(new Color(0, 0, 0, 46));
            g.fillRect(room.x, room.y, room.width, room.height);
            g.setColor(new Color(255, 255, 255, 38));
            g.setStroke(new BasicStroke(2f));
            g.drawRect(room.x, room.y, room.width, room.height);
            Rectangle doorN = new Rectangle(room.x + room.width / 2 - 30, room.y, 60, 14);
            Rectangle doorS = new Rectangle(room.x + room.width / 2 - 30, room.y + room.height - 14, 60, 14);
            Rectangle doorWst = new Rectangle(room.x, room.y + room.height / 2 - 30, 14, 60);
            Rectangle doorE = new Rectangle(room.x + room.width - 14, room.y + room.height / 2 - 30, 14, 60);
            g.setColor(new Color(255, 255, 255, 18));
            g.fill(doorN); g.fill(doorS); g.fill(doorWst); g.fill(doorE);
            g.setColor(new Color(232, 238, 247, 110));
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString("Room (" + gridX + ", " + gridY + ")", room.x + 14, room.y + 18);

            Rectangle fakeThermo = new Rectangle(room.x + room.width - 40, room.y + 16, 18, 22);
            g.setColor(new Color(200, 220, 255, 20));
            g.fillRect(fakeThermo.x, fakeThermo.y, fakeThermo.width, fakeThermo.height);
            g.setColor(new Color(200, 220, 255, 64));
            g.drawRect(fakeThermo.x, fakeThermo.y, fakeThermo.width, fakeThermo.height);
            g.setFont(new Font("SansSerif", Font.PLAIN, 9));
            g.setColor(new Color(255, 120, 120, 150));
            String fakeReading = (30 + (int) (Math.random() * 90)) + "\u00B0?";
            g.drawString(fakeReading, fakeThermo.x + 1, fakeThermo.y + 14);
        }
        void drawCaughtOverlay(Graphics2D g) {
            g.setColor(new Color(0, 0, 0, 140));
            g.fillRect(0, 0, W, H);
            g.setColor(new Color(232, 238, 247, 200));
            g.setFont(new Font("SansSerif", Font.PLAIN, 16));
            String text = "It has you.";
            int tw = g.getFontMetrics().stringWidth(text);
            g.drawString(text, W / 2 - tw / 2, H / 2);
        }

        void drawTitleScreen(Graphics2D g) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, W, H);
            g.setColor(new Color(232, 238, 247, 240));
            g.setFont(new Font("SansSerif", Font.BOLD, 34));
            String title = "SANITY NIGHT";
            int tw = g.getFontMetrics().stringWidth(title);
            g.drawString(title, W / 2 - tw / 2, H / 2 - 30);
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            String sub = "Click anywhere to continue";
            int sw = g.getFontMetrics().stringWidth(sub);
            g.drawString(sub, W / 2 - sw / 2, H / 2 + 10);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            String controls = "Controls: Move WASD/Arrows  |  E interact  |  C camera  |  F flashlight  |  R restart";
            int cw = g.getFontMetrics().stringWidth(controls);
            g.setColor(new Color(232, 238, 247, 170));
            g.drawString(controls, W / 2 - cw / 2, H / 2 + 36);
        }
        void drawNightIntroCard(Graphics2D g) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, W, H);
            g.setColor(new Color(232, 238, 247, 240));
            g.setFont(new Font("SansSerif", Font.BOLD, 40));
            String[] ordinal = { "", "1st Night", "2nd Night", "3rd Night" };
            String text = ordinal[introForNight];
            int tw = g.getFontMetrics().stringWidth(text);
            g.drawString(text, W / 2 - tw / 2, H / 2);
        }
        void drawPhoneCallCutscene(Graphics2D g) { drawDialogueScene(g, PHONE_CALL_LINES, null); }
        void drawDoctorMeetingCutscene(Graphics2D g) { drawDialogueScene(g, DOCTOR_MEETING_LINES, this::drawBlurredPhotosProp); }
        void drawDialogueScene(Graphics2D g, String[] lines, SceneDecoration decoration) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, W, H);
            g.setColor(new Color(232, 238, 247, 220));
            g.setFont(new Font("SansSerif", Font.PLAIN, 18));
            int idx = Math.min(cutsceneLineIndex, lines.length - 1);
            String text = lines[idx];
            int tw = g.getFontMetrics().stringWidth(text);
            g.drawString(text, W / 2 - tw / 2, H / 2);
            if (decoration != null) decoration.draw(g);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(new Color(232, 238, 247, 140));
            String small = "(Cutscene)";
            int sw = g.getFontMetrics().stringWidth(small);
            g.drawString(small, W / 2 - sw / 2, H / 2 + 30);
        }
        void drawBlurredPhotosProp(Graphics2D g) {
            g.setColor(new Color(255, 255, 255, 28));
            g.fillRoundRect(W / 2 - 150, H / 2 + 50, 80, 60, 10, 10);
            g.fillRoundRect(W / 2 - 40, H / 2 + 50, 80, 60, 10, 10);
            g.fillRoundRect(W / 2 + 70, H / 2 + 50, 80, 60, 10, 10);
            g.setColor(new Color(232, 238, 247, 120));
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString("unclear", W / 2 - 145, H / 2 + 85);
            g.drawString("unclear", W / 2 - 35, H / 2 + 85);
            g.drawString("unclear", W / 2 + 75, H / 2 + 85);
        }

        void drawEndingScreen(Graphics2D g) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, W, H);
            int phoneW = 280, phoneH = 420;
            int phoneX = W / 2 - phoneW / 2, phoneY = H / 2 - phoneH / 2 - 10;
            g.setColor(new Color(60, 70, 85, 255));
            g.fillRoundRect(phoneX, phoneY, phoneW, phoneH, 28, 28);
            g.setColor(new Color(15, 17, 22, 255));
            g.fillRoundRect(phoneX + 10, phoneY + 34, phoneW - 20, phoneH - 70, 6, 6);

            g.setColor(new Color(232, 238, 247, 200));
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            String header = "Doctor";
            int hw = g.getFontMetrics().stringWidth(header);
            g.drawString(header, W / 2 - hw / 2, phoneY + 26);

            int visibleCount = endingMessageRevealCount();
            int bubbleY = phoneY + 50;
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            for (int i = 0; i < visibleCount && i < FINAL_TEXT_MESSAGES.length; i++) {
                String msg = FINAL_TEXT_MESSAGES[i];
                int textWidth = g.getFontMetrics().stringWidth(msg);
                int bubbleWidth = Math.min(phoneW - 40, textWidth + 24);
                int bubbleHeight = 30;
                int bubbleX = phoneX + 16;
                g.setColor(new Color(255, 255, 255, 30));
                g.fillRoundRect(bubbleX, bubbleY, bubbleWidth, bubbleHeight, 14, 14);
                g.setColor(new Color(232, 238, 247, 220));
                g.drawString(msg, bubbleX + 12, bubbleY + 20);
                bubbleY += bubbleHeight + 10;
            }

            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(new Color(232, 238, 247, 150));
            String restart = "Press R to restart";
            int rw = g.getFontMetrics().stringWidth(restart);
            g.drawString(restart, W / 2 - rw / 2, phoneY + phoneH + 34);

            if (endingReason != null) {
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g.setColor(new Color(232, 238, 247, 130));
                int ew = g.getFontMetrics().stringWidth(endingReason);
                g.drawString(endingReason, W / 2 - ew / 2, phoneY - 16);
            }
        }
        int endingMessageRevealCount() {
            int count = 1 + (int) (endingElapsedTimer / Config.ENDING_MESSAGE_INTERVAL_SEC);
            return clampInt(count, 1, FINAL_TEXT_MESSAGES.length);
        }

        void applyLightingOverlay(Graphics2D g) {
            // The thermostat readout is a device display, not something you see with your eyes -
            // it stays fully legible regardless of the flashlight or ambient darkness.
            if (thermostatMapViewActive()) return;

            BufferedImage overlay = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D og = overlay.createGraphics();
            og.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Pitch black everywhere by default. The flashlight cone, the small always-on self-light
            // circle, and the breathing spotlight are the only things that punch visibility back in.
            og.setColor(Color.BLACK);
            og.fillRect(0, 0, W, H);

            drawSelfLightCircle(og);
            if (flashlightOn) cutFlashlightCone(og);
            if (breathingActive) drawBreathingSpotlight(og);

            og.dispose();
            g.drawImage(overlay, 0, 0, null);

            if (cameraFlashTimer > 0) {
                float alpha = (float) clamp(cameraFlashTimer / Config.CAM_FLASH_SEC, 0, 1);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, W, H);
                g.setComposite(AlphaComposite.SrcOver);
            }
        }
        /** Small always-on circle of visibility around the player, independent of the flashlight. */
        void drawSelfLightCircle(Graphics2D og) {
            float r = (float) Config.SELF_LIGHT_RADIUS_PX;
            og.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT, 1f));
            RadialGradientPaint spot = new RadialGradientPaint(
                    new Point((int) px, (int) py), r, new float[]{ 0f, 0.55f, 1f },
                    new Color[]{ new Color(255, 255, 255, 235), new Color(255, 255, 255, 140), new Color(255, 255, 255, 0) });
            og.setPaint(spot);
            og.fillOval((int) (px - r), (int) (py - r), (int) (r * 2), (int) (r * 2));
            og.setComposite(AlphaComposite.SrcOver);
        }
        void cutFlashlightCone(Graphics2D og) {
            og.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT, 1f));
            double arc = Math.toRadians(Config.FLASH_ARC_DEG);
            double start = facing - arc / 2;
            double end = facing + arc / 2;
            double range = Config.FLASH_RANGE_PX;
            int steps = 18;
            for (int i = 0; i < steps; i++) {
                double t = i / (double) (steps - 1);
                double r1 = range * (t + 1.0 / steps);
                double fade = Math.pow(1.0 - t, Config.FLASH_FADE_EXP);
                int a = (int) (255 * fade * Config.FLASH_INTENSITY);
                a = (int) (a * (1.0 - Config.FLASH_EDGE_SOFTNESS));
                a = Math.max(0, Math.min(255, a));
                og.setColor(new Color(255, 255, 255, a));
                Shape band = new Arc2D.Double(px - r1, py - r1, r1 * 2, r1 * 2, Math.toDegrees(-end), Math.toDegrees(-(start - end)), Arc2D.PIE);
                og.fill(band);
            }
            og.setComposite(AlphaComposite.SrcOver);
        }
        void drawBreathingSpotlight(Graphics2D og) {
            int cx = (int) Math.round(px);
            int cy = (int) Math.round(py) - 44 + 7;
            double pulse = 1.0 + 0.06 * Math.sin(System.currentTimeMillis() * 0.012);
            float inner = (float) (110 * pulse);
            float outer = (float) (230 * pulse);
            og.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT, 1f));
            RadialGradientPaint spot = new RadialGradientPaint(
                    new Point(cx, cy), outer, new float[]{ 0f, inner / outer, 1f },
                    new Color[]{ new Color(255, 255, 255, 255), new Color(255, 255, 255, 140), new Color(255, 255, 255, 0) });
            og.setPaint(spot);
            og.fillRect(0, 0, W, H);
            og.setComposite(AlphaComposite.SrcOver);
            og.setColor(new Color(255, 255, 255, 24));
            og.setStroke(new BasicStroke(2f));
            og.drawOval((int) (cx - inner), (int) (cy - inner), (int) (inner * 2), (int) (inner * 2));
        }
        void drawBreathingBar(Graphics2D g) {
            if (!breathingActive) return;
            int barW = 140, barH = 14;
            int bx = (int) px - barW / 2;
            int by = (int) py - 44;
            g.setColor(new Color(255, 0, 0, 56));
            g.fillRoundRect(bx, by, barW, barH, 999, 999);
            g.setColor(new Color(255, 255, 255, 46));
            g.drawRoundRect(bx, by, barW, barH, 999, 999);
            int greenX = bx + (int) (barW * Config.BREATH_TARGET_START);
            int greenW = (int) (barW * (Config.BREATH_TARGET_END - Config.BREATH_TARGET_START));
            g.setColor(new Color(93, 255, 155, 82));
            g.fillRoundRect(greenX, by, greenW, barH, 999, 999);
            int sliderX = bx + (int) (barW * breathSlider);
            g.setColor(new Color(255, 255, 255, 240));
            g.fillRoundRect(sliderX - 4, by + 1, 8, barH - 2, 999, 999);
        }
        void drawHud(Graphics2D g) {
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.setColor(new Color(232, 238, 247, 230));
            String sanityLine = String.format("Sanity: %.1f%%", sanity);
            String holdSuffix = (sanityHoldTimer > 0) ? String.format(" (STABLE %.1fs)", sanityHoldTimer) : "";
            g.drawString(sanityLine + holdSuffix, 18, 26);
            g.drawString("Pills: " + pillCharges, 18, 48);
            g.drawString("Flashlight: " + (flashlightOn ? "ON" : "OFF"), 18, 70);
            int objectiveY = 92;
            if (currentNight == 2) {
                g.drawString("Film: " + filmRemaining + "   Photos placed: " + monsterPhotosPlaced + "/3", 18, 92);
                objectiveY = 112;
            }
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(new Color(232, 238, 247, 190));
            String objective = currentObjectiveText();
            if (objective != null && !objective.isEmpty()) g.drawString("Objective: " + objective, 18, objectiveY);

            ArrayList<String> flags = new ArrayList<>();
            if (breathingActive) {
                flags.add("Breathing");
            } else {
                if (writing) flags.add("Writing");
                if (hidingInBed) flags.add("Hiding (Bed)");
                if (hidingInCloset) flags.add("Hiding (Closet)");
                if (!writing && !isHiding()) flags.add(moving ? "Moving" : "Idle");
                if (thermostatMapViewActive()) flags.add("Thermostat View");
                if (encounterActive) flags.add("MONSTER IN ROOM");
            }
            g.setColor(new Color(232, 238, 247, 230));
            g.drawString("State: " + String.join(" / ", flags), 18, H - 18);
        }

        void drawRoom(Graphics2D g, Rectangle r, String title, Color stroke) {
            g.setColor(new Color(0, 0, 0, 46));
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setStroke(new BasicStroke(2f));
            g.setColor(stroke);
            g.drawRect(r.x, r.y, r.width, r.height);
            g.setColor(new Color(232, 238, 247, 180));
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString(title, r.x + 10, r.y + 18);
        }
        void drawHallway(Graphics2D g, Rectangle r) {
            g.setColor(new Color(255, 255, 255, 14));
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setColor(new Color(255, 255, 255, 20));
            g.drawRect(r.x, r.y, r.width, r.height);
        }
        /** Is this door currently inside the flashlight's beam (angle + range), regardless of monster position? */
        boolean isDoorLitByFlashlight(Door d) {
            if (!flashlightOn) return false;
            double cx = d.rect.x + d.rect.width / 2.0;
            double cy = d.rect.y + d.rect.height / 2.0;
            double dx = cx - px, dy = cy - py;
            double dist = Math.hypot(dx, dy);
            if (dist > Config.FLASH_RANGE_PX) return false;
            double angleTo = Math.atan2(dy, dx);
            double diff = smallestAngleDifference(facing, angleTo);
            double halfArc = Math.toRadians(Config.FLASH_ARC_DEG) * 0.5;
            return Math.abs(diff) <= halfArc;
        }
        /** True if the flashlight is shining on this door AND the monster is in one of the two rooms it connects. */
        boolean doorHasMonsterGlow(Door d) {
            if (!isDoorLitByFlashlight(d)) return false;
            if (huntingActive && huntTransitActive) return d == huntTransitDoor;
            String monsterZone = effectiveMonsterZone();
            return monsterZone.equals(d.zoneA) || monsterZone.equals(d.zoneB);
        }
        void drawDoor(Graphics2D g, Door d) {
            g.setColor(d.open ? new Color(120, 190, 255, 35) : new Color(0, 0, 0, 80));
            g.fillRect(d.rect.x, d.rect.y, d.rect.width, d.rect.height);
            g.setColor(new Color(255, 255, 255, 28));
            g.drawRect(d.rect.x, d.rect.y, d.rect.width, d.rect.height);
            if (doorHasMonsterGlow(d)) {
                float pulse = (float) (0.45 + 0.30 * Math.sin(System.currentTimeMillis() * 0.006));
                int pad = 10;
                g.setColor(new Color(255, 40, 40, (int) (140 * pulse)));
                g.fillRoundRect(d.rect.x - pad, d.rect.y - pad, d.rect.width + pad * 2, d.rect.height + pad * 2, 12, 12);
            }
        }
        void drawClosetIcon(Graphics2D g, Closet c) {
            boolean onCooldown = closetCooldowns.containsKey(c.id);
            g.setColor(onCooldown ? new Color(255, 80, 80, 14) : new Color(255, 255, 255, 14));
            g.fillRect(c.rect.x, c.rect.y, c.rect.width, c.rect.height);
            g.setColor(onCooldown ? new Color(255, 80, 80, 40) : new Color(255, 255, 255, 30));
            g.drawRect(c.rect.x, c.rect.y, c.rect.width, c.rect.height);
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.setColor(new Color(232, 238, 247, 160));
            g.drawString("Closet", c.rect.x - 2, c.rect.y - 4);
        }
        void drawThermostatIcon(Graphics2D g, Thermostat t) {
            g.setColor(t.open ? new Color(255, 80, 80, 26) : new Color(200, 220, 255, 20));
            g.fillRect(t.rect.x, t.rect.y, t.rect.width, t.rect.height);
            g.setColor(t.open ? new Color(255, 80, 80, 110) : new Color(200, 220, 255, 64));
            g.drawRect(t.rect.x, t.rect.y, t.rect.width, t.rect.height);
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.setColor(new Color(232, 238, 247, 160));
            g.drawString("T", t.rect.x + 6, t.rect.y + 14);
        }
        void drawProp(Graphics2D g, Prop p) {
            Color fill = new Color(200, 220, 255, 26);
            Color stroke = new Color(200, 220, 255, 64);
            if (p == bed) { fill = new Color(140, 190, 255, 26); stroke = new Color(140, 190, 255, 90); }
            if (p == desk) { fill = new Color(255, 210, 140, 26); stroke = new Color(255, 210, 140, 90); }
            if (p == pillBottle) { fill = new Color(93, 255, 155, 26); stroke = new Color(93, 255, 155, 110); }
            if (p == kitchenPills) {
                fill = hasSleepingPills ? new Color(120, 120, 120, 18) : new Color(93, 255, 155, 30);
                stroke = hasSleepingPills ? new Color(150, 150, 150, 60) : new Color(93, 255, 155, 120);
            }
            g.setColor(fill);
            g.fillRect(p.rect.x, p.rect.y, p.rect.width, p.rect.height);
            g.setColor(stroke);
            g.setStroke(new BasicStroke(2f));
            g.drawRect(p.rect.x, p.rect.y, p.rect.width, p.rect.height);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(new Color(232, 238, 247, 200));
            String label = (p == kitchenPills && hasSleepingPills) ? "Sleeping Pills (taken)" : p.label;
            g.drawString(label, p.rect.x + 4, p.rect.y + p.rect.height + 12);
        }
        void drawHeldPhotoThumbnail(Graphics2D g) {
            g.setColor(new Color(255, 255, 255, 26));
            g.fillRoundRect(W - 150, 18, 120, 80, 12, 12);
            g.setColor(new Color(255, 255, 255, 60));
            g.drawRoundRect(W - 150, 18, 120, 80, 12, 12);
            g.setColor(new Color(232, 238, 247, 210));
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString(heldPhotoHasMonster ? "PHOTO: MONSTER" : "PHOTO: EMPTY", W - 138, 44);
            g.drawString("Place at desk", W - 138, 62);
        }
        void drawTemperaturesOnMap(Graphics2D g) {
            String[] zoneKeys = { "kitchen", "living", "utility", "dining", "hall", "bedroom" };
            Rectangle[] zoneRects = { roomKitchen, roomLiving, roomUtility, roomDining, preBedroomHall, roomBedroom };
            String[] zoneLabels = { "", "", "", "", "HALL", "BED" };
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            for (int i = 0; i < zoneKeys.length; i++) {
                double t = actualTemp(zoneKeys[i]);
                Rectangle r = zoneRects[i];
                int cx = r.x + r.width / 2, cy = r.y + r.height / 2;
                g.setColor(tempColor(t));
                g.drawString(formatTemp(t), cx - 18, cy);
                if (!zoneLabels[i].isEmpty()) {
                    g.setFont(new Font("SansSerif", Font.PLAIN, 11));
                    g.setColor(new Color(232, 238, 247, 120));
                    g.drawString(zoneLabels[i], cx - 14, cy + 18);
                    g.setFont(new Font("SansSerif", Font.PLAIN, 14));
                }
            }
            if (hallucination.active && hallucination.zone != null) {
                Rectangle hr = zoneRect(hallucination.zone);
                float flick = (float) (0.10 + 0.12 * Math.sin(System.currentTimeMillis() * 0.01));
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flick));
                g.setColor(new Color(255, 255, 255, 130));
                g.setStroke(new BasicStroke(2f));
                g.drawRect(hr.x + 4, hr.y + 4, hr.width - 8, hr.height - 8);
                g.setComposite(AlphaComposite.SrcOver);
            }
        }
        String formatTemp(double t) { return Math.round(t) + "\u00B0F"; }
        Color tempColor(double t) {
            double norm = clamp((t - Config.TEMP_MIN) / (double) (Config.TEMP_MAX - Config.TEMP_MIN), 0, 1);
            float alpha = (float) (0.55 + (1 - norm) * 0.35);
            int r = (int) (210 + norm * 35);
            int gr = (int) (225 + norm * 25);
            int b = 255;
            return new Color(r, gr, b, (int) (alpha * 255));
        }

        void drawPlayerSprite(Graphics2D g) {
            boolean isWalking = moving && !writing && !isHiding() && !breathingActive;
            boolean isWriting = writing;
            boolean isSleepPose = isHiding();
            int x = (int) Math.round(px);
            int y = (int) Math.round(py);
            int headD = 12;
            int neckW = 5, neckH = 1;
            int torsoW = 14, torsoH = 21;
            int armW = 6, armH = 16;
            int legW = 5, legH = 14;
            int stride = 0, strideLift = 0;
            if (isWalking) {
                double s = Math.sin(walkAnimT);
                stride = (int) Math.round(s * 3);
                strideLift = (int) Math.round(Math.max(0, s) * 2);
            }
            int writeBob = isWriting ? (int) Math.round(Math.sin(writeAnimT) * 1.0) : 0;

            g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));

            if (isSleepPose) {
                int baseX = x - 18, baseY = y - 6;
                drawFilledOutlinedRect(g, baseX, baseY, torsoH, torsoW);
                drawFilledOutlinedRect(g, baseX + torsoH - 2, baseY + torsoW / 2 - neckH / 2, neckW, neckH);
                drawFilledOutlinedOval(g, baseX + torsoH + neckW - 1, baseY + torsoW / 2 - headD / 2, headD, headD);
                drawFilledOutlinedRect(g, baseX + 4, baseY - 7, armH / 2, armW);
                drawFilledOutlinedRect(g, baseX + 4, baseY + torsoW + 1, armH / 2, armW);
                drawFilledOutlinedRect(g, baseX - 6, baseY + 3, legH / 2, legW);
                drawFilledOutlinedRect(g, baseX - 6, baseY + torsoW - 9, legH / 2, legW);
                return;
            }

            int torsoX = x - torsoW / 2;
            int torsoY = y - torsoH / 2 + writeBob;
            int headX = x - headD / 2;
            int headY = torsoY - headD - neckH - 2;
            int neckX = x - neckW / 2;
            int neckY = torsoY - neckH - 2;
            int legsY = torsoY + torsoH;
            int leftLegX = x - legW - 2;
            int rightLegX = x + 2;
            int leftArmX = torsoX - armW;
            int rightArmX = torsoX + torsoW;
            int armsY = torsoY + 2;

            drawFilledOutlinedOval(g, headX, headY, headD, headD);
            drawFilledOutlinedRect(g, neckX, neckY, neckW, neckH);
            drawFilledOutlinedRect(g, torsoX, torsoY, torsoW, torsoH);
            drawFilledOutlinedRect(g, leftArmX, armsY, armW, armH);
            drawFilledOutlinedRect(g, rightArmX, armsY, armW, armH);

            int leftStride = -stride, rightStride = stride;
            int leftLift = (isWalking && stride > 0) ? strideLift : 0;
            int rightLift = (isWalking && stride < 0) ? strideLift : 0;
            drawFilledOutlinedRect(g, leftLegX + leftStride, legsY - leftLift, legW, legH);
            drawFilledOutlinedRect(g, rightLegX + rightStride, legsY - rightLift, legW, legH);

            if (isWriting) {
                g.setColor(Color.BLACK);
                g.setStroke(new BasicStroke(2.2f));
                g.drawLine(x + 14, y - 18, x + 18, y - 16);
            }
        }
        void drawFilledOutlinedRect(Graphics2D g, int x, int y, int w, int h) {
            g.setColor(Color.WHITE); g.fillRect(x, y, w, h);
            g.setColor(Color.BLACK); g.drawRect(x, y, w, h);
        }
        void drawFilledOutlinedOval(Graphics2D g, int x, int y, int w, int h) {
            g.setColor(Color.WHITE); g.fillOval(x, y, w, h);
            g.setColor(Color.BLACK); g.drawOval(x, y, w, h);
        }
        void drawMonsterSilhouette(Graphics2D g) {
            if (!encounterActive) return;
            int mx = doorDiningBedroom.rect.x + 45;
            int my = doorDiningBedroom.rect.y + doorDiningBedroom.rect.height / 2;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.75f));
            g.setColor(new Color(255, 30, 30, 230));
            g.fillOval(mx + 6, my - 32, 24, 28);
            g.fillRoundRect(mx, my - 12, 32, 44, 12, 12);
            g.fillRoundRect(mx - 14, my + 2, 20, 12, 10, 10);
            g.fillRoundRect(mx + 26, my + 2, 20, 12, 10, 10);
            g.setComposite(AlphaComposite.SrcOver);
        }
        void drawHintBubble(Graphics2D g) {
            if (!hintVisible || hintText == null || hintText.isEmpty()) return;
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            int tw = g.getFontMetrics().stringWidth(hintText);
            int bx = W / 2 - (tw / 2) - 12;
            int by = H - 44;
            g.setColor(new Color(10, 14, 20, 190));
            g.fillRoundRect(bx, by, tw + 24, 26, 999, 999);
            g.setColor(new Color(255, 255, 255, 40));
            g.drawRoundRect(bx, by, tw + 24, 26, 999, 999);
            g.setColor(new Color(232, 238, 247, 220));
            g.drawString(hintText, W / 2 - tw / 2, H - 26);
        }
    }
}
