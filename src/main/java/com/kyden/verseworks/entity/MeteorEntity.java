package com.kyden.verseworks.entity;

import com.kyden.verseworks.sound.VerseSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class MeteorEntity extends Entity {
    public static final float HITBOX_SIZE = 2.95F;
    public static final float RENDER_SCALE = 3.0F;

    private static final double GRAVITY = 0.014D;
    private static final double DRAG = 0.995D;
    private static final double HORIZONTAL_SPEED = 0.18D;
    private static final double BASE_FALL_SPEED = 0.42D;
    private static final double SPAWN_ALTITUDE = 76.0D;
    private static final double SPAWN_ALTITUDE_VARIATION = 18.0D;
    private static final double APPROACH_DISTANCE = 24.0D;
    private static final double APPROACH_VARIATION = 10.0D;
    private static final int MAX_LIFETIME_TICKS = 20 * 30;
    private static final int CRATER_RADIUS = 5;
    private static final int CRATER_RIM_RADIUS = 6;
    private static final int FIRE_ATTEMPTS = 18;

    public MeteorEntity(EntityType<? extends MeteorEntity> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
        this.noPhysics = false;
    }

    public static MeteorEntity spawnToward(ServerLevel level, Vec3 targetPos) {
        MeteorEntity meteor = new MeteorEntity(VerseEntities.METEOR.get(), level);
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Mth.TWO_PI;
        Vec3 horizontal = new Vec3(Mth.cos((float) angle), 0.0D, Mth.sin((float) angle));
        double distance = APPROACH_DISTANCE + random.nextDouble() * APPROACH_VARIATION;
        Vec3 spawnPos = targetPos.add(-horizontal.x * distance, SPAWN_ALTITUDE + random.nextDouble() * SPAWN_ALTITUDE_VARIATION, -horizontal.z * distance);
        Vec3 aimPoint = targetPos.add((random.nextDouble() - 0.5D) * 6.0D, 0.0D, (random.nextDouble() - 0.5D) * 6.0D);
        Vec3 direction = aimPoint.subtract(spawnPos).normalize();
        Vec3 velocity = new Vec3(direction.x * HORIZONTAL_SPEED, -BASE_FALL_SPEED - random.nextDouble() * 0.04D, direction.z * HORIZONTAL_SPEED);
        float yaw = (float) (Mth.atan2(velocity.z, velocity.x) * Mth.RAD_TO_DEG) - 90.0F;

        meteor.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        meteor.setDeltaMovement(velocity);
        meteor.setYRot(yaw);
        meteor.setXRot(25.0F);
        meteor.yRotO = yaw;
        meteor.xRotO = 25.0F;
        meteor.setRemainingFireTicks(Integer.MAX_VALUE);
        return meteor;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 previousCenter = new Vec3(this.xo, this.yo, this.zo);

        if (this.tickCount > MAX_LIFETIME_TICKS || this.getY() < this.level().getMinBuildHeight() - 32) {
            this.discard();
            return;
        }

        Vec3 velocity = this.getDeltaMovement();
        velocity = velocity.add(0.0D, -GRAVITY, 0.0D);
        this.setDeltaMovement(velocity);
        this.move(MoverType.SELF, velocity);
        this.setDeltaMovement(this.getDeltaMovement().scale(DRAG));
        orientFromMotion();

        if (!this.level().isClientSide()) {
            Vec3 impactPos = findImpactPoint(previousCenter);
            if (impactPos != null) {
                impact((ServerLevel) this.level(), impactPos);
            }
        }
    }

    public float getRenderPitch(float partialTick) {
        Vec3 motion = this.getDeltaMovement();
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        return (float) (-Mth.atan2(motion.y, horizontal) * Mth.RAD_TO_DEG) + spinPhase(0.12F, 17.0F, partialTick);
    }

    public float getRenderYaw(float partialTick) {
        Vec3 motion = this.getDeltaMovement();
        float travelYaw = (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0F;
        return travelYaw + spinPhase(0.09F, 41.0F, partialTick);
    }

    public float getRenderRoll(float partialTick) {
        return spinPhase(0.11F, 73.0F, partialTick);
    }

    private float spinPhase(float speedScale, float offset, float partialTick) {
        long seed = this.getUUID().getLeastSignificantBits() ^ this.getUUID().getMostSignificantBits();
        float seedDegrees = (float) Math.floorMod(seed + (long) offset * 37L, 360L);
        return seedDegrees + (this.tickCount + partialTick) * (0.35F + seedFactor(offset) * speedScale * 4.0F);
    }

    private float seedFactor(float offset) {
        long seed = this.getUUID().getLeastSignificantBits() ^ ((long) Float.floatToIntBits(offset) << 17);
        return 0.25F + (float) Math.floorMod(seed, 1000L) / 1000.0F;
    }

    private void orientFromMotion() {
        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-4D) {
            return;
        }

        float yaw = (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0F;
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        float pitch = (float) (-Mth.atan2(motion.y, horizontal) * Mth.RAD_TO_DEG);
        this.setYRot(yaw);
        this.setXRot(pitch);
    }

    private Vec3 findImpactPoint(Vec3 previousCenter) {
        Vec3 previousProbe = previousCenter.add(0.0D, 0.4D, 0.0D);
        Vec3 currentProbe = this.position().add(0.0D, -0.75D, 0.0D);
        BlockHitResult hit = this.level().clip(new ClipContext(previousProbe, currentProbe, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }

        if (this.horizontalCollision || this.verticalCollision || this.onGround()) {
            return currentProbe;
        }

        BlockPos sampleCenter = BlockPos.containing(currentProbe);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos sample = sampleCenter.offset(dx, 0, dz);
                if (isImpactSurface(sample)) {
                    return Vec3.atCenterOf(sample);
                }
            }
        }
        return null;
    }

    private boolean isImpactSurface(BlockPos pos) {
        BlockState state = this.level().getBlockState(pos);
        return !state.isAir() && state.blocksMotion() && !state.liquid();
    }

    private void impact(ServerLevel level, Vec3 impactPos) {
        level.playSound(null, impactPos.x, impactPos.y, impactPos.z, VerseSounds.METEOR_IMPACT.get(), SoundSource.BLOCKS, 2.8F, 1.0F);
        level.explode(this, impactPos.x, impactPos.y, impactPos.z, 4.5F, false, ExplosionInteraction.BLOCK);
        BlockPos center = BlockPos.containing(impactPos);
        carveCrater(level, center);
        placeFires(level, center);
        this.discard();
    }

    private void carveCrater(ServerLevel level, BlockPos center) {
        RandomSource random = level.getRandom();
        for (int dx = -CRATER_RIM_RADIUS; dx <= CRATER_RIM_RADIUS; dx++) {
            for (int dz = -CRATER_RIM_RADIUS; dz <= CRATER_RIM_RADIUS; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > CRATER_RIM_RADIUS) {
                    continue;
                }

                int craterDepth = distance > CRATER_RADIUS
                    ? 0
                    : Math.max(1, Mth.floor((1.0D - (distance / CRATER_RADIUS)) * 4.0D) + random.nextInt(2));

                for (int dy = 2; dy >= -craterDepth; dy--) {
                    BlockPos carvePos = center.offset(dx, dy, dz);
                    if (canCarve(level, carvePos)) {
                        level.destroyBlock(carvePos, false, this);
                    }
                }

                BlockPos floorPos = center.offset(dx, -craterDepth, dz);
                if (distance <= CRATER_RADIUS) {
                    if (distance <= 1.75D && random.nextFloat() < 0.55F) {
                        setBlockIfPossible(level, floorPos, Blocks.LAVA.defaultBlockState());
                    } else if (random.nextFloat() < 0.85F) {
                        setBlockIfPossible(level, floorPos, Blocks.COBBLESTONE.defaultBlockState());
                    }
                }

                if (distance > CRATER_RADIUS - 0.75D && distance <= CRATER_RIM_RADIUS) {
                    buildRim(level, center.offset(dx, 0, dz), random);
                }
            }
        }
    }

    private boolean canCarve(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F && !state.is(Blocks.BEDROCK);
    }

    private void buildRim(ServerLevel level, BlockPos columnBase, RandomSource random) {
        BlockPos ground = findGround(level, columnBase.above(3), columnBase.getY() - 5);
        if (ground == null) {
            return;
        }

        int height = 1 + (random.nextFloat() < 0.35F ? 1 : 0);
        for (int index = 0; index < height; index++) {
            setBlockIfPossible(level, ground.above(index), Blocks.COBBLESTONE.defaultBlockState());
        }
    }

    private void placeFires(ServerLevel level, BlockPos center) {
        RandomSource random = level.getRandom();
        for (int index = 0; index < FIRE_ATTEMPTS; index++) {
            int dx = random.nextInt(CRATER_RIM_RADIUS * 2 + 1) - CRATER_RIM_RADIUS;
            int dz = random.nextInt(CRATER_RIM_RADIUS * 2 + 1) - CRATER_RIM_RADIUS;
            BlockPos ground = findGround(level, center.offset(dx, 3, dz), center.getY() - 6);
            if (ground == null) {
                continue;
            }

            BlockPos firePos = ground.above();
            if (!level.isEmptyBlock(firePos)) {
                continue;
            }

            BlockState fire = Blocks.FIRE.defaultBlockState();
            if (fire.canSurvive(level, firePos)) {
                level.setBlock(firePos, fire, Block.UPDATE_ALL);
            }
        }
    }

    private BlockPos findGround(ServerLevel level, BlockPos startPos, int minY) {
        for (int y = startPos.getY(); y >= Math.max(minY, level.getMinBuildHeight()); y--) {
            BlockPos pos = new BlockPos(startPos.getX(), y, startPos.getZ());
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.blocksMotion() && !state.liquid()) {
                return pos;
            }
        }
        return null;
    }

    private void setBlockIfPossible(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState existing = level.getBlockState(pos);
        if (existing.getDestroySpeed(level, pos) < 0.0F || existing.is(Blocks.BEDROCK)) {
            return;
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}