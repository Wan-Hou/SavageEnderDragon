package com.dragonfight.fight;

import com.cupboard.util.BlockSearch;
import com.dragonfight.DragonfightMod;
import com.dragonfight.config.ConfigurationCache;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonDeathPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE;

/**
 * Custom manager for handling additional dragon difficulty
 */
public class DragonFightManagerCustom
{
    public static ImmutableList<ConfigurationCache.EntitySpawnData> spawnOnCrystalDeath   = ImmutableList.of();
    public static ImmutableList<ConfigurationCache.EntitySpawnData> spawnOnCrystalRespawn = ImmutableList.of();
    public static ImmutableList<ConfigurationCache.EntitySpawnData> spawnOnDragonSitting  = ImmutableList.of();

    private static final float    CRYSTAL_RESPAWN_TIME    = 8000;
    private static final int      LIGHTNING_DESTROY_RANGE = 10 * 10;
    private static final float    ADD_TIMER               = 2000;
    private static       BlockPos crystalRespawnPos       = null;
    private static       int      crystalRespawnTimer     = 0;

    private static int               timeSinceLastLanding = 0;

    /**
     * ^^ Add counters
     */
    private static       boolean            spawnAdds    = false;
    private final static BlockPos           spawnPos     = new BlockPos(0, 68, 0);
    private static       int                spawnCounter = 0;
    private static       List<LivingEntity> meleeAdds    = new ArrayList<>();

    private static int advancingLightningCurrent = 0;
    private static int advancingLightningStop    = 0;

    private static int advancingExplosionCurrent = 0;
    private static int advancingExplosionStop    = 0;

    private static EnderDragon dragonEntity = null;

    public static boolean isFightRunning = true;

    public static  AttributeModifier AA_GRAVITY_MOD = new AttributeModifier("fall", 5.0, AttributeModifier.Operation.ADDITION);
    private static AttributeModifier MAX_HP_MOD     = new AttributeModifier("dragonhp", 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);

    public static void onCrystalDeath(final EndCrystal enderCrystalEntity, final DamageSource damageSource)
    {
        AreaEffectCloud areaeffectcloudentity =
          new AreaEffectCloud(enderCrystalEntity.level(), enderCrystalEntity.getX(), enderCrystalEntity.getY(), enderCrystalEntity.getZ());

        if (dragonEntity != null)
        {
            areaeffectcloudentity.setOwner(dragonEntity);
        }

        notifyPlayer(enderCrystalEntity.level(), "Crystal died from:" + damageSource);
        // Spawn ground area effect making the player walk away
        areaeffectcloudentity.setParticle(ParticleTypes.DRAGON_BREATH);
        areaeffectcloudentity.setRadius(1.0F);
        areaeffectcloudentity.setDuration((int) ((CRYSTAL_RESPAWN_TIME / getDifficulty()) * DragonfightMod.config.getCommonConfig().crystalRespawnTimeModifier));
        areaeffectcloudentity.setRadiusPerTick((5.0F - areaeffectcloudentity.getRadius()) / (float) areaeffectcloudentity.getDuration());
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.HARM, 100, 1));
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 1));
        areaeffectcloudentity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
        enderCrystalEntity.level().addFreshEntity(areaeffectcloudentity);

        addCrystalRespawnPos(enderCrystalEntity.blockPosition());

        if (!(damageSource.getEntity() instanceof Player))
        {
            return;
        }

        // On ranged crystal kill
        if (damageSource.getEntity().blockPosition().distSqr(enderCrystalEntity.blockPosition()) > LIGHTNING_DESTROY_RANGE)
        {
            if (!DragonfightMod.config.getCommonConfig().disableLightning)
            {
                // Hit player destroying the crystals from range with lightning
                LightningBolt lightningboltentity =
                  (LightningBolt) spawnEntity((ServerLevel) enderCrystalEntity.level(),
                    new ConfigurationCache.EntitySpawnData(EntityType.LIGHTNING_BOLT, null),
                    damageSource.getEntity().position());
                lightningboltentity.setVisualOnly(false);
            }

            if (!spawnOnCrystalDeath.isEmpty())
            {
                // Spawn phantoms aggrod to the player
                for (int i = 0; i < Math.max(1, (getDifficulty() / 4d) * DragonfightMod.config.getCommonConfig().mobSpawnAmountModifier); i++)
                {
                    BlockPos searchedPos = BlockSearch.findAround((ServerLevel) enderCrystalEntity.level(),
                      damageSource.getEntity().blockPosition().offset(i + 1, 5, i + 1),
                      15,
                      15,
                      1,
                      (level, checkPos) -> level.getBlockState(checkPos).isAir() && level.getBlockState(checkPos.above()).isAir());

                    if (searchedPos == null)
                    {
                        searchedPos = damageSource.getEntity().blockPosition();
                    }

                    final LivingEntity entity = (LivingEntity) spawnEntity((ServerLevel) enderCrystalEntity.level(),
                      spawnOnCrystalDeath.get(DragonfightMod.rand.nextInt(spawnOnCrystalDeath.size())),
                      createVec3(searchedPos));
                    if (entity instanceof Mob)
                    {
                        ((Mob) entity).setTarget((LivingEntity) damageSource.getEntity());
                    }
                }
            }
        }
        else
        {
            // Melee kill reduces dragon HP
            if (dragonEntity != null && dragonEntity.getHealth() > 100)
            {
                dragonEntity.setHealth(dragonEntity.getHealth() * 0.9f);
                float f = (DragonfightMod.rand.nextFloat() - 0.5F) * 8.0F;
                float f1 = (DragonfightMod.rand.nextFloat() - 0.5F) * 4.0F;
                float f2 = (DragonfightMod.rand.nextFloat() - 0.5F) * 8.0F;
                dragonEntity.level().addParticle(ParticleTypes.EXPLOSION_EMITTER,
                  dragonEntity.getX() + (double) f,
                  dragonEntity.getY() + 2.0D + (double) f1,
                  dragonEntity.getZ() + (double) f2,
                  0.0D,
                  0.0D,
                  0.0D);
            }
        }
    }

    private static void addCrystalRespawnPos(final BlockPos position)
    {
        if (dragonEntity == null || Math.sqrt(dragonEntity.blockPosition().distSqr(position)) > 1000)
        {
            return;
        }

        final Set<BlockPos> existing = getCrystalRespawnPositions();
        if (!existing.contains(position))
        {
            DragonfightMod.config.getCommonConfig().crystalPendingRespawns.add(position.getX() + ";" + position.getY() + ";" + position.getZ());
            DragonfightMod.config.save();
        }
    }

    private static Set<BlockPos> getCrystalRespawnPositions()
    {
        final Set<BlockPos> existing = new HashSet<>();

        for (final String data : DragonfightMod.config.getCommonConfig().crystalPendingRespawns)
        {
            String[] dataArray = data.split(";");
            if (dataArray != null && dataArray.length == 3)
            {
                final BlockPos pos = new BlockPos(Integer.parseInt(dataArray[0]), Integer.parseInt(dataArray[1]), Integer.parseInt(dataArray[2]));

                if (dragonEntity != null && Math.sqrt(dragonEntity.blockPosition().distSqr(pos)) > 1000)
                {
                    continue;
                }

                existing.add(pos);
            }
        }

        return existing;
    }

    public static Map<UUID, Integer> flyingPlayers = new HashMap<>();

    public static void onWorldTick(final Level world)
    {
        final EndDragonFight manager = ((ServerLevel) world).getDragonFight();
        if (manager == null || ((IDragonfightAccessor) manager).getDragonEvent().getPlayers().isEmpty() || dragonEntity == null)
        {
            reset();
            return;
        }

        if (crystalRespawnPos != null)
        {
            if (--crystalRespawnTimer > 0)
            {
                if (crystalRespawnTimer == 200)
                {
                    // Spawns pre-respawn lightning
                    spawnLightningAtCircle(crystalRespawnPos, 8, world);
                }
            }
            else
            {
                notifyPlayer(world, "Respawning crystal at" + crystalRespawnPos);
                respawnCrystalAt(crystalRespawnPos, world);
            }
        }

        if (dragonEntity.getHealth() < dragonEntity.getMaxHealth() && dragonEntity.isAlive())
        {
            if (!isFightRunning && !((IDragonfightAccessor) manager).getDragonEvent().getPlayers().isEmpty())
            {
                // Cleans entities on fight start
                List<Monster> monsterEntities = world.getEntitiesOfClass(Monster.class, dragonEntity.getBoundingBox().inflate(150));
                for (final Monster entity : monsterEntities)
                {
                    if (!(entity instanceof Npc) && !entity.isPersistenceRequired())
                    {
                        entity.remove(Entity.RemovalReason.DISCARDED);
                    }
                }

                setDragonHealth();

                isFightRunning = true;
            }
        }
        else
        {
            if (isFightRunning)
            {
                reset();
            }
            isFightRunning = false;
        }

        if (!isFightRunning)
        {
            return;
        }

        // Fix dragon flying forever on death
        if (dragonEntity.getPhaseManager().getCurrentPhase() instanceof DragonDeathPhase && dragonEntity.getPhaseManager().getCurrentPhase().getFlyTargetLocation() != null)
        {
            if (dragonEntity.getPhaseManager().getCurrentPhase().getFlyTargetLocation().distanceToSqr(dragonEntity.blockPosition().getX(),
              dragonEntity.blockPosition().getY(), dragonEntity.getZ()) < 10)
            {
                dragonEntity.setHealth(0);
            }
        }

        timeSinceLastLanding++;
        // Fix landing
        if (timeSinceLastLanding > 120 * 20 && dragonEntity != null)
        {
            timeSinceLastLanding = 0;
            dragonEntity.getPhaseManager().setPhase(EnderDragonPhase.LANDING_APPROACH);
            notifyPlayer(world, "Forcing landing phase");
        }


        if (dragonEntity.getHealth() > dragonEntity.getMaxHealth() * 0.9)
        {
            return;
        }

        if (DragonfightMod.config.getCommonConfig().antiflightAbility)
        {
            for (final Player player : ((IDragonfightAccessor) manager).getDragonEvent().getPlayers())
            {
                int time = flyingPlayers.computeIfAbsent(player.getUUID(), s -> 0);

                if (isFlying(player))
                {
                    if (time == 300)
                    {
                        flyingPlayers.put(player.getUUID(), ++time);
                    }
                    else if (time > 400)
                    {
                        player.hurt(dragonEntity.damageSources().fall(), player.getMaxHealth() * 0.9f);
                        player.setHealth(1);
                        flyingPlayers.put(player.getUUID(), 0);
                    }
                    else
                    {
                        flyingPlayers.put(player.getUUID(), ++time);
                    }
                }
                else
                {
                    if (time > 300)
                    {
                        player.hurt(dragonEntity.damageSources().fall(), player.getMaxHealth() * 0.9f);
                        player.setHealth(1);
                    }
                    flyingPlayers.put(player.getUUID(), 0);
                }
            }
        }

        if (advancingLightningCurrent > 0 && world.getGameTime() % 100 == 0)
        {
            advancingLightningCurrent += 3;
            spawnLightningAtCircle(spawnPos, advancingLightningCurrent, world);

            if (advancingLightningCurrent > advancingLightningStop)
            {
                advancingLightningCurrent = 0;
                advancingLightningStop = 0;
            }
        }

        if (dragonEntity != null && advancingExplosionCurrent == 0 && advancingLightningCurrent == 0
              && (dragonEntity.getHealth() / dragonEntity.getMaxHealth()) < (DragonfightMod.config.getCommonConfig().disableLightning ? 0.5d : 0.20d))
        {
            advancingExplosionCurrent = 8;
            advancingExplosionStop = 50;
        }

        if (advancingExplosionCurrent > 0 && world.getGameTime() % 200 == 0)
        {
            advancingExplosionCurrent += 3;
            explodeInCircleAround(spawnPos, advancingExplosionCurrent, world);

            if (advancingExplosionCurrent > advancingExplosionStop)
            {
                advancingExplosionCurrent = 0;
                advancingExplosionStop = 0;
            }
        }

        if (spawnAdds && spawnCounter++ > (ADD_TIMER / (getDifficulty() * DragonfightMod.config.getCommonConfig().mobSpawnAmountModifier)))
        {
            notifyPlayer(world, "Spawning melee add");
            spawnMeleeAdds(world);
            spawnCounter = 0;
        }
    }

    /**
     * Re-adds the health modifiers
     */
    private static void setDragonHealth()
    {
        final double pct = dragonEntity.getHealth() / dragonEntity.getMaxHealth();

        if (dragonEntity.getAttribute(Attributes.MAX_HEALTH).hasModifier(MAX_HP_MOD))
        {
            dragonEntity.getAttribute(Attributes.MAX_HEALTH).removeModifier(MAX_HP_MOD);
        }

        MAX_HP_MOD = new AttributeModifier("dragonhp",
          (Math.max(1, getDifficulty() / 5) * DragonfightMod.config.getCommonConfig().dragonHealthModifier),
          AttributeModifier.Operation.MULTIPLY_TOTAL);

        dragonEntity.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(MAX_HP_MOD);
        dragonEntity.setHealth((float) (dragonEntity.getMaxHealth() * pct));
    }

    /**
     * Check if a player is considered flying
     *
     * @param player
     * @return
     */
    private static boolean isFlying(final Player player)
    {
        return player != null && (player.hasImpulse || !player.onGround()) && player.fallDistance <= 0.1f && player.level().isEmptyBlock(player.blockPosition().below(2));
    }

    /**
     * Reset saved counters
     */
    private static void reset()
    {
        crystalRespawnPos = null;
        spawnAdds = false;
        spawnCounter = 0;

        if (DragonfightMod.server == null)
        {
            return;
        }

        for (final LivingEntity living : meleeAdds)
        {
            living.remove(Entity.RemovalReason.DISCARDED);
        }

        isFightRunning = false;
        flyingPlayers.clear();
        meleeAdds.clear();
    }

    /**
     * Spawn aggroed enderman as melee adds
     *
     * @param world
     */
    private static void spawnMeleeAdds(final Level world)
    {
        meleeAdds.removeIf(Entity::isRemoved);

        if (meleeAdds.size() >= (getDifficulty() * DragonfightMod.config.getCommonConfig().mobSpawnAmountModifier) || spawnOnDragonSitting.isEmpty())
        {
            return;
        }

        BlockPos searchedPos = BlockSearch.findAround((ServerLevel) world,
          spawnPos,
          30,
          30,
          1,
          (level, checkPos) -> level.getBlockState(checkPos).isAir() && level.getBlockState(checkPos.above()).isAir() && level.getBlockState(checkPos.below())
            .isSolid());
        if (searchedPos == null)
        {
            searchedPos = spawnPos;
        }

        final LivingEntity entity =
          (LivingEntity) spawnEntity((ServerLevel) world, spawnOnDragonSitting.get(DragonfightMod.rand.nextInt(spawnOnDragonSitting.size())), createVec3(searchedPos));

        final List<Player> closesPlayers = world.getNearbyPlayers(TargetingConditions.DEFAULT, entity, entity.getBoundingBox().inflate(20));
        if (!closesPlayers.isEmpty())
        {
            final Player closestPlayer = closesPlayers.get(DragonfightMod.rand.nextInt(closesPlayers.size()));
            if (entity instanceof Mob)
            {
                ((Mob) entity).setTarget(closestPlayer);
            }
        }
        else
        {
            final List<Player> farPlayers = world.getNearbyPlayers(TargetingConditions.DEFAULT, entity, entity.getBoundingBox().inflate(60, 120, 60));
            if (!farPlayers.isEmpty())
            {
                final Player closestPlayer = farPlayers.get(DragonfightMod.rand.nextInt(farPlayers.size()));
                if (entity instanceof Mob)
                {
                    ((Mob) entity).setTarget(closestPlayer);
                }
            }
        }

        meleeAdds.add(entity);
    }

    /**
     * Respawns a crystal at the given pos
     *
     * @param pos   pos to respawn at
     * @param world world to respawn in
     */
    private static void respawnCrystalAt(final BlockPos pos, final Level world)
    {
        if (world.getEntitiesOfClass(EndCrystal.class, new AABB(pos).inflate(2)).isEmpty())
        {
            // Respawn crystal
            final EndCrystal crystal = (EndCrystal) spawnEntity((ServerLevel) world, new ConfigurationCache.EntitySpawnData(EntityType.END_CRYSTAL, null), createVec3(pos));
            final Vec3 spawnPos = createVec3(new BlockPos((int) (pos.getX() * 0.8), pos.getY(), (int) (pos.getZ() * 0.8)));

            for (int i = 0; i < Math.max(1, (getDifficulty() / 3d) * DragonfightMod.config.getCommonConfig().mobSpawnAmountModifier); i++)
            {
                // Spawn blaze on respawn
                final LivingEntity entity =
                  (LivingEntity) spawnEntity((ServerLevel) world, spawnOnCrystalRespawn.get(DragonfightMod.rand.nextInt(spawnOnCrystalRespawn.size())), spawnPos);

                if (entity instanceof Mob)
                {
                    ((Mob) entity).setTarget(world.getNearestPlayer(entity, 100));
                }
            }

            float f = (DragonfightMod.rand.nextFloat() - 0.5F) * 8.0F;
            float f1 = (DragonfightMod.rand.nextFloat() - 0.5F) * 4.0F;
            float f2 = (DragonfightMod.rand.nextFloat() - 0.5F) * 8.0F;
            world.addParticle(ParticleTypes.EXPLOSION_EMITTER, crystal.getX() + (double) f, crystal.getY() + 2.0D + (double) f1, crystal.getZ() + (double) f2, 0.0D, 0.0D, 0.0D);
        }

        crystalRespawnPos = null;
        checkCrystalsToRespawn(world);
    }

    /**
     * Called when the dragon heals
     *
     * @param dragonEntity
     */
    public static void onDragonHeal(final EnderDragon dragonEntity)
    {
        dragonEntity.setHealth(Math.min(dragonEntity.getMaxHealth(), dragonEntity.getHealth() + (getDifficulty() / 7f)));
    }

    /**
     * Called when attacking a player
     *
     * @param damage
     * @return
     */
    public static float onAttackPlayer(final float damage)
    {
        return (float) ((damage + getDifficulty() / 2f) * DragonfightMod.config.getCommonConfig().dragonDamageModifier);
    }

    public static void onPhaseChange(
      final EnderDragonPhase<?> newPhase,
      final EnderDragonPhase<? extends DragonPhaseInstance> oldphase,
      final EnderDragon dragon)
    {
        // Avoid doing anything when we're reading a new entity, as nbt read does save the phases
        if (dragonEntity != dragon)
        {
            dragonEntity = dragon;
            return;
        }

        if (dragon == null || !(dragon.level() instanceof ServerLevel) || !dragonEntity.isAlive())
        {
            return;
        }

        final EndDragonFight manager = ((ServerLevel) dragon.level()).getDragonFight();
        if (manager == null || ((IDragonfightAccessor) manager).getDragonEvent().getPlayers().isEmpty())
        {
            return;
        }

        notifyPlayer(dragon.level(), "Next phase:" + newPhase.toString());

        if (newPhase == EnderDragonPhase.TAKEOFF)
        {
            // Start spawning endermen
            spawnAdds = true;

            checkCrystalsToRespawn(dragon.level());
            if ((dragon.getHealth() / dragon.getMaxHealth()) < 0.25d && dragon.getDragonFight() != null)
            {
                dragon.level().playLocalSound(dragon.getX(),
                  dragon.getY(),
                  dragon.getZ(),
                  SoundEvents.ENDER_DRAGON_GROWL,
                  dragon.getSoundSource(),
                  2.5F,
                  0.8F + DragonfightMod.rand.nextFloat() * 0.3F,
                  false);
                for (final Player playerEntity : ((IDragonfightAccessor) dragon.getDragonFight()).getDragonEvent().getPlayers())
                {
                    playerEntity.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, getDifficulty() / 3));
                    if ((dragon.getHealth() / dragon.getMaxHealth()) < 0.10d)
                    {
                        playerEntity.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200, 1));
                    }
                }
            }
        }
        if ((newPhase == EnderDragonPhase.LANDING_APPROACH) || (newPhase == EnderDragonPhase.DYING))
        {
            // Stop spawning
            timeSinceLastLanding = 0;
            spawnAdds = false;
        }
        if (oldphase == EnderDragonPhase.LANDING && newPhase == EnderDragonPhase.SITTING_SCANNING)
        {
            timeSinceLastLanding = 0;

            final double healthpercent = (dragon.getHealth() / dragon.getMaxHealth());
            if (healthpercent < 0.5d)
            {
                advancingLightningCurrent = 6;
                advancingLightningStop = 50;
            }
            else
            {
                spawnLightningAtCircle(spawnPos, DragonfightMod.rand.nextInt(16) + 8, dragon.level());
            }
        }
    }

    private static void checkCrystalsToRespawn(final Level world)
    {
        if (crystalRespawnPos != null)
        {
            return;
        }

        final List<BlockPos> positions = new ArrayList<>(getCrystalRespawnPositions());
        Collections.shuffle(positions);
        for (final BlockPos pos : positions)
        {
            if (world.getEntitiesOfClass(EndCrystal.class, new AABB(pos).inflate(5)).isEmpty())
            {
                crystalRespawnPos = pos;
                crystalRespawnTimer = (int) Math.max(400, (CRYSTAL_RESPAWN_TIME / getDifficulty()) * DragonfightMod.config.getCommonConfig().crystalRespawnTimeModifier);
                notifyPlayer(world, "Adding respawn at :" + crystalRespawnPos + " in:" + crystalRespawnTimer);
                break;
            }
        }
    }

    /**
     * Spawns a circular lightning hit
     *
     * @param midPoint
     * @param radius
     * @param world
     */
    private static void spawnLightningAtCircle(final BlockPos midPoint, final int radius, final Level world)
    {
        if (DragonfightMod.config.getCommonConfig().disableLightning)
        {
            return;
        }

        Set<BlockPos> lightningPositions = getCircularPositionsAround(midPoint, radius, 15 - (radius / 10));
        for (final BlockPos lightningPos : lightningPositions)
        {
            notifyPlayer(world,
              "spawning plightning at!" + new BlockPos(lightningPos.getX(),
                world.getHeightmapPos(WORLD_SURFACE, lightningPos).getY(),
                lightningPos.getZ()));

            final int yLevel = world.getHeightmapPos(WORLD_SURFACE, lightningPos).getY();

            // Dont hit too varied height differences
            if (Math.abs(midPoint.getY() - yLevel) > 20)
            {
                continue;
            }

            LightningBolt lightningboltentity = EntityType.LIGHTNING_BOLT.create(world);
            lightningboltentity.moveTo(lightningPos.getX(), yLevel, lightningPos.getZ());
            lightningboltentity.setVisualOnly(false);
            world.addFreshEntity(lightningboltentity);
        }
    }

    /**
     * Spawns a circular lightning hit
     *
     * @param midPoint
     * @param radius
     * @param world
     */
    private static void explodeInCircleAround(final BlockPos midPoint, final int radius, final Level world)
    {
        Set<BlockPos> explodePos = getCircularPositionsAround(midPoint, radius, 15);
        for (final BlockPos lightningPos : explodePos)
        {
            notifyPlayer(world,
              "spawning explosion at!" + new BlockPos(lightningPos.getX(),
                world.getHeightmapPos(WORLD_SURFACE, lightningPos).getY(),
                lightningPos.getZ()));

            final int yLevel = world.getHeightmapPos(WORLD_SURFACE, lightningPos).getY();

            // Dont hit too varied height differences
            if (Math.abs(midPoint.getY() - yLevel) > 20)
            {
                continue;
            }

            world.explode(dragonEntity,
              lightningPos.getX(),
              lightningPos.getY(),
              lightningPos.getZ(),
              1 + getDifficulty() / 4f,
              false,
              Level.ExplosionInteraction.NONE);
        }
    }

    private static Set<BlockPos> getCircularPositionsAround(final BlockPos start, final int radius, int precision)
    {
        Set<BlockPos> positions = new HashSet<>();

        precision = (int) (precision / DragonfightMod.config.getCommonConfig().lightningExplosionDensity);
        final int randomOffset = DragonfightMod.rand.nextInt(40);
        for (int i = randomOffset; i < 360 + randomOffset; i += precision)
        {
            int x = (int) Math.round(radius * Math.cos(Math.toRadians(i)));
            int z = (int) Math.round(radius * Math.sin(Math.toRadians(i)));

            positions.add(start.offset(x, 0, z));
        }

        return positions;
    }

    /**
     * Notify OP's of the fights state for debugging
     *
     * @param world
     * @param message
     */
    public static void notifyPlayer(final Level world, final String message)
    {
        if (DragonfightMod.config.getCommonConfig().printDragonPhases)
        {
            for (final Player player : ((ServerLevel) world).players())
            {
                if (world.getServer() != null && ((ServerLevel) world).getServer().getProfilePermissions(player.getGameProfile()) > 0)
                {
                    player.sendSystemMessage(Component.literal(message));
                }
            }
        }
    }

    /**
     * Get the total difficulty number
     *
     * @return
     */
    private static int getDifficulty()
    {
        int difficulty = DragonfightMod.config.getCommonConfig().dragonDifficulty;

        if (dragonEntity != null)
        {
            difficulty += dragonEntity.level().getDifficulty().getId();
            if (dragonEntity.getDragonFight() != null)
            {
                difficulty += ((IDragonfightAccessor) dragonEntity.getDragonFight()).getDragonEvent().getPlayers().size();
            }
        }

        return Math.max(difficulty, 1);
    }

    private static Vec3 createVec3(final BlockPos pos)
    {
        return new Vec3(pos.getX(), pos.getY(), pos.getZ());
    }

    private static Entity spawnEntity(final ServerLevel world, ConfigurationCache.EntitySpawnData spawnData, Vec3 pos)
    {
        CompoundTag compoundtag = new CompoundTag();

        if (spawnData.nbt != null)
        {
            compoundtag = spawnData.nbt.copy();
        }

        compoundtag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(spawnData.type).toString());
        Entity entity = EntityType.loadEntityRecursive(compoundtag, world, (p_138828_) -> {

            final double offset = pos.x % 1d != 0d || pos.z % 1d != 0d ? 0 : 0.5;

            p_138828_.moveTo(pos.x + offset, pos.y, pos.z + offset, p_138828_.getYRot(), p_138828_.getXRot());
            return p_138828_;
        });

        if (entity == null)
        {
            return null;
        }

        entity.setUUID(UUID.randomUUID());

        if (entity instanceof Mob)
        {
            ((Mob) entity).finalizeSpawn(world, world.getCurrentDifficultyAt(entity.blockPosition()), MobSpawnType.COMMAND, (SpawnGroupData) null, (CompoundTag) null);
        }

        world.addFreshEntity(entity);

        return entity;
    }
}
