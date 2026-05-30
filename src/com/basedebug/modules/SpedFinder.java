/*
 * Decompiled with CFR 0.152.
 */
package com.basedebug.modules;

import com.basedebug.AuthManager;
import com.basedebug.modules.SpedDebugCategory;
import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

public class SpedFinder
extends Module {
    private static final int TICKS_PER_SECOND = 20;
    private final SettingGroup sgGeneral;
    private final SettingGroup sgChunkDensity;
    private final SettingGroup sgSpawner;
    private final SettingGroup sgDepthGuard;
    private final SettingGroup sgTracers;
    private final Setting<Double> scanRange;
    private final Setting<Integer> scanIntervalTicks;
    private final Setting<Boolean> renderDenseChunkMarkers;
    private final Setting<Integer> denseChunkThreshold;
    private final Setting<Integer> maxDenseChunkMarkers;
    private final Setting<Integer> maxSavedDenseChunks;
    private final Setting<Double> denseChunkMarkerSize;
    private final Setting<Double> denseChunkMarkerThickness;
    private final Setting<Double> markerHeightOffset;
    private final Setting<Boolean> denseChunkChatNotify;
    private final Setting<Boolean> denseChunkToastNotify;
    private final Setting<SettingColor> denseChunkLineColor;
    private final Setting<SettingColor> denseChunkFillColor;
    private final Setting<Boolean> spawnerChatNotify;
    private final Setting<Boolean> spawnerToastNotify;
    private final Setting<Boolean> highlightSpawnerChunks;
    private final Setting<Boolean> renderSpawnerBoxes;
    private final Setting<Double> rainbowSpeed;
    private final Setting<Integer> rainbowAlpha;
    private final Setting<SettingColor> spawnerChunkLineColor;
    private final Setting<SettingColor> spawnerChunkFillColor;
    private final Setting<Boolean> depthGuardEnabled;
    private final Setting<Integer> triggerY;
    private final Setting<Boolean> sendWarning;
    private final Setting<Boolean> showDenseChunkTracers;
    private final Setting<Boolean> showSpawnerTracers;
    private final Setting<SettingColor> denseChunkTracerColor;
    private final Setting<SettingColor> spawnerTracerColor;
    private final Map<ChunkPos, DenseChunkDetection> denseChunks;
    private final Set<BlockPos> spawnerPositions;
    private final Set<BlockPos> announcedSpawners;
    private final Set<ChunkPos> announcedDenseChunks;
    private final Set<ChunkPos> spawnerChunks;
    private float rainbowHue;
    private int scanTicks;
    private boolean wasInWorld;
    private boolean warningSent;
    private boolean inDangerZone;
    private boolean kickLockedUntilSafe;
    private boolean rejoinGraceActive;
    private int rejoinGraceTicks;
    private boolean wasDisconnectedByUs;
    private static final int REJOIN_GRACE_TICKS = 200;

    public SpedFinder() {
        super(SpedDebugCategory.CATEGORY, "Sped-Finder", "Sped Debug.");
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgChunkDensity = this.settings.createGroup("Chunk Density");
        this.sgSpawner = this.settings.createGroup("Spawners");
        this.sgDepthGuard = this.settings.createGroup("Depth Guard");
        this.sgTracers = this.settings.createGroup("Tracers");
        this.scanRange = this.sgGeneral.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("scan-range")).description("Horizontal block range to scan loaded chunks. Y is ignored so bedrock spawners are always found.")).defaultValue(64.0).sliderMin(16.0).sliderMax(256.0).build());
        this.scanIntervalTicks = this.sgGeneral.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("scan-interval-ticks")).description("How often to rescan. Higher = better performance.")).defaultValue((Object)5)).min(1).sliderMin(1).sliderMax(40).build());
        this.renderDenseChunkMarkers = this.sgChunkDensity.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("render-dense-chunk-markers")).description("Render a flat marker for chunks with too many block entities.")).defaultValue((Object)true)).build());
        this.denseChunkThreshold = this.sgChunkDensity.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("dense-chunk-threshold")).description("Chunks with more block entities than this get a marker.")).defaultValue((Object)30)).min(1).sliderMin(1).sliderMax(300).build());
        this.maxDenseChunkMarkers = this.sgChunkDensity.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("max-dense-chunk-markers")).description("Max dense chunk markers rendered at once.")).defaultValue((Object)64)).min(0).sliderMin(0).sliderMax(512).build());
        this.maxSavedDenseChunks = this.sgChunkDensity.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("max-saved-dense-chunks")).description("Max dense chunk detections to keep. Oldest removed first.")).defaultValue((Object)256)).min(1).sliderMin(16).sliderMax(1000).build());
        this.denseChunkMarkerSize = this.sgChunkDensity.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("dense-chunk-marker-size")).description("Size of the dense chunk highlight. 16 = full chunk.")).defaultValue(20.0).min(4.0).sliderMin(4.0).sliderMax(40.0).build());
        this.denseChunkMarkerThickness = this.sgChunkDensity.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("dense-chunk-marker-thickness")).description("Thickness of the dense chunk highlight layer.")).defaultValue(0.08).min(0.01).sliderMin(0.01).sliderMax(1.0).build());
        this.markerHeightOffset = this.sgChunkDensity.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("marker-height-offset")).description("How far above your feet the marker Y is saved when detected.")).defaultValue(0.05).sliderMin(-5.0).sliderMax(5.0).build());
        this.denseChunkChatNotify = this.sgChunkDensity.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("dense-chunk-chat-notify")).description("Post a chat message when a new dense chunk is detected.")).defaultValue((Object)true)).build());
        this.denseChunkToastNotify = this.sgChunkDensity.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("dense-chunk-toast-notify")).description("Show a toast when a new dense chunk is detected.")).defaultValue((Object)false)).build());
        this.denseChunkLineColor = this.sgChunkDensity.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("dense-chunk-line-color")).description("Outline color for dense chunk markers.")).defaultValue(new SettingColor(0, 255, 0, 230)).build());
        this.denseChunkFillColor = this.sgChunkDensity.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("dense-chunk-fill-color")).description("Fill color for dense chunk markers.")).defaultValue(new SettingColor(0, 255, 0, 55)).build());
        this.spawnerChatNotify = this.sgSpawner.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("spawner-chat-notify")).description("Chat message when a new mob spawner enters range.")).defaultValue((Object)true)).build());
        this.spawnerToastNotify = this.sgSpawner.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("spawner-toast-notify")).description("Toast notification when a new mob spawner is found.")).defaultValue((Object)true)).build());
        this.highlightSpawnerChunks = this.sgSpawner.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("highlight-spawner-chunks")).description("Highlight the full chunk column for chunks containing spawners.")).defaultValue((Object)true)).build());
        this.renderSpawnerBoxes = this.sgSpawner.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("render-spawner-boxes")).description("Render rainbow boxes around mob spawners.")).defaultValue((Object)true)).build());
        this.rainbowSpeed = this.sgSpawner.add((Setting)((DoubleSetting.Builder)((DoubleSetting.Builder)((DoubleSetting.Builder)new DoubleSetting.Builder().name("spawner-rainbow-speed")).description("Speed of rainbow hue cycling on spawner boxes.")).defaultValue(2.0).sliderMin(0.5).sliderMax(10.0).visible(() -> this.renderSpawnerBoxes.getName())).build());
        this.rainbowAlpha = this.sgSpawner.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("spawner-rainbow-alpha")).description("Opacity of the rainbow spawner box outline.")).defaultValue((Object)220)).sliderMin(50).sliderMax(255).visible(() -> this.renderSpawnerBoxes.getName())).build());
        this.spawnerChunkLineColor = this.sgSpawner.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("spawner-chunk-line-color")).description("Outline color for chunks containing spawners.")).defaultValue(new SettingColor(0, 255, 0, 220)).visible(() -> this.highlightSpawnerChunks.getName())).build());
        this.spawnerChunkFillColor = this.sgSpawner.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("spawner-chunk-fill-color")).description("Fill color for chunks containing spawners.")).defaultValue(new SettingColor(0, 255, 0, 35)).visible(() -> this.highlightSpawnerChunks.getName())).build());
        this.depthGuardEnabled = this.sgDepthGuard.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("depth-guard-enabled")).description("Kick you when you go below the set Y level.")).defaultValue((Object)true)).build());
        this.triggerY = this.sgDepthGuard.add((Setting)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)((IntSetting.Builder)new IntSetting.Builder().name("trigger-y")).description("Y level that triggers the kick.")).defaultValue((Object)-30)).sliderMin(-64).sliderMax(0).visible(() -> this.depthGuardEnabled.getName())).build());
        this.sendWarning = this.sgDepthGuard.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("warn-before-kick")).description("Send a chat warning 5 blocks above the trigger Y.")).defaultValue((Object)true)).visible(() -> this.depthGuardEnabled.getName())).build());
        this.showDenseChunkTracers = this.sgTracers.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-dense-chunk-tracers")).description("Draw tracer lines to saved dense chunk detections.")).defaultValue((Object)true)).build());
        this.showSpawnerTracers = this.sgTracers.add((Setting)((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)new BoolSetting.Builder().name("show-spawner-tracers")).description("Draw tracer lines to found mob spawners.")).defaultValue((Object)false)).build());
        this.denseChunkTracerColor = this.sgTracers.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("dense-chunk-tracer-color")).description("Tracer color for dense chunk detections.")).defaultValue(new SettingColor(0, 255, 0, 220)).visible(() -> this.showDenseChunkTracers.getName())).build());
        this.spawnerTracerColor = this.sgTracers.add((Setting)((ColorSetting.Builder)((ColorSetting.Builder)((ColorSetting.Builder)new ColorSetting.Builder().name("spawner-tracer-color")).description("Tracer color for mob spawners.")).defaultValue(new SettingColor(0, 255, 0, 220)).visible(() -> this.showSpawnerTracers.getName())).build());
        this.denseChunks = new LinkedHashMap<ChunkPos, DenseChunkDetection>();
        this.spawnerPositions = new HashSet<BlockPos>();
        this.announcedSpawners = new HashSet<BlockPos>();
        this.announcedDenseChunks = new HashSet<ChunkPos>();
        this.spawnerChunks = new HashSet<ChunkPos>();
    }

    public void onActivate() {
        if (!true) {
            String reason = AuthManager.isAuthFinished() ? AuthManager.getLastFailureReason() : "license check is still running";
            this.sendChat("[Sped Debug] Auth required: " + reason, 0xFF5555);
            this.toggle();
            return;
        }
        this.clearScanCaches();
        this.resetDepthState();
        this.rainbowHue = 0.0f;
        this.scanTicks = 0;
        this.wasInWorld = this.mc.player != null && this.mc.world != null;
    }

    public void onDeactivate() {
        this.clearScanCaches();
        this.resetDepthState();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!true) {
            if (this.isActive()) {
                this.toggle();
            }
            return;
        }
        this.handleDepthGuardTick();
        this.handleFinderTick();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!true) {
            return;
        }
        if (this.mc.world == null || this.mc.player == null) {
            return;
        }
        if (((Boolean)this.highlightSpawnerChunks.getName()).booleanValue()) {
            this.renderSpawnerChunks(event);
        }
        if (((Boolean)this.renderSpawnerBoxes.getName()).booleanValue() || ((Boolean)this.showSpawnerTracers.getName()).booleanValue()) {
            for (BlockPos pos : this.spawnerPositions) {
                BlockEntity be = this.mc.world.getBlockEntity(pos);
                if (!(be instanceof MobSpawnerBlockEntity)) continue;
                if (((Boolean)this.renderSpawnerBoxes.getName()).booleanValue()) {
                    this.renderRainbowBox(event, new Box(pos).expand(0.005), pos);
                }
                if (!((Boolean)this.showSpawnerTracers.getName()).booleanValue()) continue;
                this.renderSpawnerTracer(event, pos);
            }
        }
        if (((Boolean)this.renderDenseChunkMarkers.getName()).booleanValue()) {
            this.renderDenseChunkMarkers(event);
        }
        if (((Boolean)this.showDenseChunkTracers.getName()).booleanValue()) {
            this.renderDenseChunkTracers(event);
        }
    }

    private void handleFinderTick() {
        if (this.mc.world == null || this.mc.player == null) {
            this.spawnerPositions.clear();
            this.spawnerChunks.clear();
            return;
        }
        this.rainbowHue = (this.rainbowHue + (float)((Double)this.rainbowSpeed.getName() * 0.5)) % 360.0f;
        if (this.scanTicks > 0) {
            --this.scanTicks;
            return;
        }
        this.scanTicks = Math.max(1, (Integer)this.scanIntervalTicks.getName());
        this.rescanLoadedChunks();
    }

    private void rescanLoadedChunks() {
        this.spawnerPositions.clear();
        this.spawnerChunks.clear();
        HashMap<ChunkPos, Integer> blockEntityCounts = new HashMap<ChunkPos, Integer>();
        double range = (Double)this.scanRange.getName();
        double rangeSq = range * range;
        double playerX = this.mc.player.getX();
        double playerZ = this.mc.player.getZ();
        ClientChunkManager chunkManager = this.mc.world.getChunkManager();
        int centerCX = this.mc.player.getChunkPos().x;
        int centerCZ = this.mc.player.getChunkPos().z;
        int radius = (int)Math.ceil(range / 16.0) + 1;
        for (int cx = centerCX - radius; cx <= centerCX + radius; ++cx) {
            for (int cz = centerCZ - radius; cz <= centerCZ + radius; ++cz) {
                WorldChunk chunk = chunkManager.getWorldChunk(cx, cz);
                if (chunk == null) continue;
                ChunkPos chunkPos = chunk.getPos();
                int count = 0;
                for (Map.Entry beEntry : chunk.getBlockEntities().entrySet()) {
                    double dz;
                    BlockPos pos2;
                    double dx;
                    BlockEntity be = (BlockEntity)beEntry.getValue();
                    if (be == null || (dx = (double)(pos2 = ((BlockPos)beEntry.getKey()).toImmutable()).getX() + 0.5 - playerX) * dx + (dz = (double)pos2.getZ() + 0.5 - playerZ) * dz > rangeSq) continue;
                    ++count;
                    if (!(be instanceof MobSpawnerBlockEntity)) continue;
                    this.spawnerPositions.add(pos2);
                    this.spawnerChunks.add(new ChunkPos(pos2));
                    if (!this.announcedSpawners.add(pos2)) continue;
                    this.notifySpawnerFound(pos2);
                }
                if (count <= 0) continue;
                blockEntityCounts.put(chunkPos, count);
            }
        }
        int renderedDense = 0;
        for (Map.Entry entry : blockEntityCounts.entrySet()) {
            if ((Integer)entry.getValue() <= (Integer)this.denseChunkThreshold.getName()) continue;
            if (renderedDense >= (Integer)this.maxDenseChunkMarkers.getName()) break;
            this.saveDenseChunkDetection((ChunkPos)entry.getKey(), (Integer)entry.getValue());
            ++renderedDense;
        }
        this.announcedSpawners.removeIf(pos -> !this.spawnerPositions.contains(pos));
    }

    private void saveDenseChunkDetection(ChunkPos chunkPos, int count) {
        DenseChunkDetection existing = (DenseChunkDetection)this.denseChunks.getName(chunkPos);
        boolean isNew = existing == null;
        double markerY = isNew ? this.mc.player.getY() + (Double)this.markerHeightOffset.getName() : existing.markerY;
        long detectedAtMillis = isNew ? System.currentTimeMillis() : existing.detectedAtMillis;
        this.denseChunks.put(chunkPos, new DenseChunkDetection(chunkPos, count, markerY, detectedAtMillis));
        this.trimDenseChunkDetections();
        if (isNew && this.announcedDenseChunks.add(chunkPos)) {
            this.notifyDenseChunkFound(chunkPos, count, markerY);
        }
    }

    private void trimDenseChunkDetections() {
        int limit = Math.max(1, (Integer)this.maxSavedDenseChunks.getName());
        while (this.denseChunks.size() > limit) {
            ChunkPos oldest = this.denseChunks.keySet().iterator().next();
            this.denseChunks.remove(oldest);
            this.announcedDenseChunks.remove(oldest);
        }
    }

    private void handleDepthGuardTick() {
        boolean currentlyInWorld;
        if (!((Boolean)this.depthGuardEnabled.getName()).booleanValue()) {
            this.resetDepthState();
            return;
        }
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }
        if (this.mc.player.getY() <= (double)((Integer)this.triggerY.getName()).intValue()) {
            this.inDangerZone = true;
            this.sendChat("Y Entity  Limit Packet Blocked. RE LOG FLY UP", 0xFF5555);
            return;
        }
        boolean bl = currentlyInWorld = this.mc.player != null && this.mc.world != null && this.mc.getNetworkHandler() != null;
        if (!currentlyInWorld) {
            if (this.wasInWorld && this.wasDisconnectedByUs) {
                this.rejoinGraceActive = true;
                this.rejoinGraceTicks = 200;
            }
            this.wasInWorld = false;
            this.warningSent = false;
            return;
        }
        if (!this.wasInWorld) {
            this.wasInWorld = true;
            if (!this.rejoinGraceActive) {
                this.wasDisconnectedByUs = false;
                this.warningSent = false;
            }
        }
        double currentY = this.mc.player.getY();
        int limit = (Integer)this.triggerY.getName();
        int warnLevel = limit + 5;
        if (this.rejoinGraceActive) {
            if (currentY <= (double)limit) {
                if (this.rejoinGraceTicks > 0) {
                    --this.rejoinGraceTicks;
                    return;
                }
                this.rejoinGraceActive = false;
                this.rejoinGraceTicks = 0;
                this.wasDisconnectedByUs = false;
                this.inDangerZone = true;
                this.kickLockedUntilSafe = true;
                return;
            }
            this.rejoinGraceActive = false;
            this.rejoinGraceTicks = 0;
            this.wasDisconnectedByUs = false;
            this.inDangerZone = false;
            this.warningSent = false;
        }
        if (currentY > (double)limit) {
            this.inDangerZone = false;
            this.kickLockedUntilSafe = false;
            if (currentY > (double)warnLevel) {
                this.warningSent = false;
            } else if (((Boolean)this.sendWarning.getName()).booleanValue() && !this.warningSent) {
                this.sendChat(String.format("[Sped Debug] Warning: approaching Y=%d \u2014 current Y: %.1f", limit, currentY), 0xFFAA00);
                this.warningSent = true;
            }
            return;
        }
        if (this.kickLockedUntilSafe) {
            return;
        }
        if (!this.inDangerZone) {
            this.inDangerZone = true;
            this.kickLockedUntilSafe = true;
            this.wasDisconnectedByUs = true;
            this.rejoinGraceActive = true;
            this.rejoinGraceTicks = 200;
            this.disconnect();
        }
    }

    private void resetDepthState() {
        this.wasInWorld = false;
        this.warningSent = false;
        this.inDangerZone = false;
        this.kickLockedUntilSafe = false;
        this.wasDisconnectedByUs = false;
        this.rejoinGraceActive = false;
        this.rejoinGraceTicks = 0;
    }

    private void disconnect() {
        this.sendChat("Y Entity  Limit Packet Blocked. RE LOG FLY UP", 0xFF5555);
    }

    private void notifyDenseChunkFound(ChunkPos chunkPos, int count, double markerY) {
        if (((Boolean)this.denseChunkChatNotify.getName()).booleanValue() && this.mc.inGameHud != null) {
            MutableText msg = Text.literal((String)"[Sped Debug] ").styled(s -> s.withColor(4234495)).append((Text)Text.literal((String)"Dense Chunk").styled(s -> s.withColor(0x55FF55))).append((Text)Text.literal((String)String.format(" detected at chunk %d, %d with %d block entities", chunkPos.x, chunkPos.z, count)).styled(s -> s.withColor(0xFFFFFF))).append((Text)Text.literal((String)String.format(" (marker Y %.1f)", markerY)).styled(s -> s.withColor(0xFFFF55)));
            this.mc.inGameHud.getChatHud().addMessage((Text)msg);
        }
        if (((Boolean)this.denseChunkToastNotify.getName()).booleanValue() && this.mc.getToastManager() != null) {
            this.mc.getToastManager().add((Toast)new SystemToast(SystemToast.Type.PERIODIC_NOTIFICATION, (Text)Text.literal((String)"Sped Finder: Dense Chunk"), (Text)Text.literal((String)String.format("Chunk %d, %d | %d block entities", chunkPos.x, chunkPos.z, count))));
        }
    }

    private void notifySpawnerFound(BlockPos pos) {
        if (((Boolean)this.spawnerChatNotify.getName()).booleanValue() && this.mc.inGameHud != null) {
            MutableText msg = Text.literal((String)"[Sped Debug] ").styled(s -> s.withColor(4234495)).append((Text)Text.literal((String)"Mob Spawner").styled(s -> s.withColor(0x55FF55))).append((Text)Text.literal((String)" found at ").styled(s -> s.withColor(0xFFFFFF))).append((Text)Text.literal((String)String.format("%d, %d, %d", pos.getX(), pos.getY(), pos.getZ())).styled(s -> s.withColor(0xFFFF55))).append((Text)Text.literal((String)"!").styled(s -> s.withColor(0xFFFFFF)));
            this.mc.inGameHud.getChatHud().addMessage((Text)msg);
        }
        if (((Boolean)this.spawnerToastNotify.getName()).booleanValue() && this.mc.getToastManager() != null) {
            this.mc.getToastManager().add((Toast)new SystemToast(SystemToast.Type.PERIODIC_NOTIFICATION, (Text)Text.literal((String)"Sped Finder: Spawner Found"), (Text)Text.literal((String)String.format("X %d, Y %d, Z %d", pos.getX(), pos.getY(), pos.getZ()))));
        }
    }

    private void sendChat(String message, int color) {
        if (this.mc.inGameHud != null) {
            this.mc.inGameHud.getChatHud().addMessage((Text)Text.literal((String)message).styled(s -> s.withColor(color)));
        }
    }

    private void renderDenseChunkMarkers(Render3DEvent event) {
        if (this.denseChunks.isEmpty()) {
            return;
        }
        int rendered = 0;
        double halfSize = Math.max(2.0, (Double)this.denseChunkMarkerSize.getName() / 2.0);
        double thickness = Math.max(0.01, (Double)this.denseChunkMarkerThickness.getName());
        for (DenseChunkDetection detection : this.denseChunks.values()) {
            if (rendered >= (Integer)this.maxDenseChunkMarkers.getName()) break;
            double centerX = (double)detection.chunkPos.getStartX() + 8.0;
            double centerZ = (double)detection.chunkPos.getStartZ() + 8.0;
            double y = detection.markerY;
            event.renderer.box(centerX - halfSize, y, centerZ - halfSize, centerX + halfSize, y + thickness, centerZ + halfSize, (meteordevelopment.meteorclient.utils.render.color.Color)this.denseChunkFillColor.getName(), (meteordevelopment.meteorclient.utils.render.color.Color)this.denseChunkLineColor.getName(), ShapeMode.Both, 0);
            ++rendered;
        }
    }

    private void renderDenseChunkTracers(Render3DEvent event) {
        if (this.denseChunks.isEmpty()) {
            return;
        }
        int rendered = 0;
        for (DenseChunkDetection detection : this.denseChunks.values()) {
            if (rendered >= (Integer)this.maxDenseChunkMarkers.getName()) break;
            event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, (double)detection.chunkPos.getStartX() + 8.0, detection.markerY + Math.max(0.01, (Double)this.denseChunkMarkerThickness.getName()), (double)detection.chunkPos.getStartZ() + 8.0, (meteordevelopment.meteorclient.utils.render.color.Color)this.denseChunkTracerColor.getName());
            ++rendered;
        }
    }

    private void renderSpawnerChunks(Render3DEvent event) {
        int bottomY = this.mc.world.getBottomY();
        int topY = this.mc.world.getBottomY() + this.mc.world.getHeight();
        for (ChunkPos chunkPos : this.spawnerChunks) {
            event.renderer.box((double)chunkPos.getStartX(), (double)bottomY, (double)chunkPos.getStartZ(), (double)(chunkPos.getEndX() + 1), (double)topY, (double)(chunkPos.getEndZ() + 1), (meteordevelopment.meteorclient.utils.render.color.Color)this.spawnerChunkFillColor.getName(), (meteordevelopment.meteorclient.utils.render.color.Color)this.spawnerChunkLineColor.getName(), ShapeMode.Both, 0);
        }
    }

    private void renderSpawnerTracer(Render3DEvent event, BlockPos pos) {
        event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, (double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5, (meteordevelopment.meteorclient.utils.render.color.Color)this.spawnerTracerColor.getName());
    }

    private void renderRainbowBox(Render3DEvent event, Box box, BlockPos pos) {
        int alpha = (Integer)this.rainbowAlpha.getName();
        float posOff = (float)((pos.getX() * 73 ^ pos.getY() * 179 ^ pos.getZ() * 31) & 0xFF) / 255.0f * 360.0f;
        float hue = (this.rainbowHue + posOff) % 360.0f;
        int rgbLine = Color.HSBtoRGB(hue / 360.0f, 1.0f, 1.0f);
        meteordevelopment.meteorclient.utils.render.color.Color line = new meteordevelopment.meteorclient.utils.render.color.Color(rgbLine >> 16 & 0xFF, rgbLine >> 8 & 0xFF, rgbLine & 0xFF, alpha);
        int rgbFill = Color.HSBtoRGB((hue + 60.0f) % 360.0f / 360.0f, 1.0f, 1.0f);
        meteordevelopment.meteorclient.utils.render.color.Color fill = new meteordevelopment.meteorclient.utils.render.color.Color(rgbFill >> 16 & 0xFF, rgbFill >> 8 & 0xFF, rgbFill & 0xFF, Math.max(15, alpha / 8));
        event.renderer.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, fill, line, ShapeMode.Both, 0);
    }

    private void clearScanCaches() {
        this.denseChunks.clear();
        this.spawnerPositions.clear();
        this.announcedSpawners.clear();
        this.announcedDenseChunks.clear();
        this.spawnerChunks.clear();
    }

    private static class DenseChunkDetection {
        private final ChunkPos chunkPos;
        private final int count;
        private final double markerY;
        private final long detectedAtMillis;

        private DenseChunkDetection(ChunkPos chunkPos, int count, double markerY, long detectedAtMillis) {
            this.chunkPos = chunkPos;
            this.count = count;
            this.markerY = markerY;
            this.detectedAtMillis = detectedAtMillis;
        }
    }
}

