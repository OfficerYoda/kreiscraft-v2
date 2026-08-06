package de.kreiscraft.modules.spawn;

import org.bukkit.util.Vector;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

/** Spawn-module configuration. */
@ConfigSerializable public record SpawnConfig(int teleportRange,GlideConfig glide,SwapHandBoostConfig swapHandBoost,SpawnLaunchConfig spawnLaunch){

/** Apply safe defaults for every nested config that was absent from YAML. */
public SpawnConfig{if(teleportRange==0)teleportRange=100;if(glide==null)glide=new GlideConfig(true,3,30,new LocationSpec("",0,0,0,0,0));if(swapHandBoost==null)swapHandBoost=new SwapHandBoostConfig(false,3.0);if(spawnLaunch==null)spawnLaunch=new SpawnLaunchConfig(false,new LocationSpec("",0,0,0,0,0),new Vector(0,5,0));}

// ── nested records ──────────────────────────────────────────────────────

/**
 * Configuration for the spawn-area elytra glide feature.
 *
 * <p>Note: {@code enabled} is a primitive {@code boolean}; when the {@code glide:} key is
 * absent from the config file, Configurate initialises it to {@code false} (the Java default
 * for {@code boolean}). In production the Batch C {@code config.yml} always contains an
 * explicit {@code enabled: true} entry, so this is only relevant for edge-case testing.
 *
 * <ul>
 *   <li>{@code multiplyValue} – velocity multiplier at glide start (reserved for future
 *       launch-pad behaviour; stored but not yet used at runtime). Defaults to 3.</li>
 *   <li>{@code launchLocation} – optional launch pad Location (unset when world is empty).</li>
 * </ul>
 */
@ConfigSerializable public record GlideConfig(boolean enabled,int multiplyValue,int spawnRadius,LocationSpec launchLocation){

public GlideConfig{
// multiplyValue/spawnRadius: 0 means "missing from config" — apply safe defaults.
if(multiplyValue==0)multiplyValue=3;if(spawnRadius==0)spawnRadius=30;if(launchLocation==null)launchLocation=new LocationSpec("",0,0,0,0,0);}}

/** Configuration for the offhand-swap velocity boost while gliding. */
@ConfigSerializable public record SwapHandBoostConfig(boolean enabled,double vectorMultiplier){

public SwapHandBoostConfig{
// 0.0 means "missing from config" — apply the safe default multiplier.
if(vectorMultiplier==0.0)vectorMultiplier=3.0;}}

/**
 * Configuration for the block-trigger velocity launch pad.
 *
 * <p>The {@code velocity} default of {@code (0, 5, 0)} is applied when the field is
 * absent (detected as an all-zero vector, since {@code (0, 0, 0)} is not a useful launch
 * velocity).
 */
@ConfigSerializable public record SpawnLaunchConfig(boolean enabled,LocationSpec trigger,Vector velocity){

public SpawnLaunchConfig{if(trigger==null)trigger=new LocationSpec("",0,0,0,0,0);
// (0, 0, 0) treated as "not configured" — no meaningful launch uses zero velocity.
if(velocity==null||(velocity.getX()==0.0&&velocity.getY()==0.0&&velocity.getZ()==0.0)){velocity=new Vector(0,5,0);}}}}
