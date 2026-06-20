package io.github.kjhuio.raffinatezza.entity;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.UUID;

public class HelmSeatEntity extends Entity {

    private UUID subLevelId;
    private int missingSubLevelTicks;
    private Vec3 localPos = new Vec3(0, 0, 0);

    public boolean inputForward, inputBackward, inputLeft, inputRight, inputUp, inputDown;

    private static final int MAX_MISSING_SUB_LEVEL_TICKS = 20;
    private static final double CRUISE_SPEED = 8.0;
    private static final double VERTICAL_SPEED = 5.0;
    private static final double YAW_SPEED = 1.2;
    private static final double LINEAR_RESPONSE = 0.28;
    private static final double VERTICAL_RESPONSE = 0.35;
    private static final double YAW_RESPONSE = 0.45;
    private static final double LINEAR_DAMPING = 0.10;
    private static final double ANGULAR_DAMPING = 0.22;
    private static final double LEVELING_STRENGTH = 0.12;
    private static final double GROUND_CLEARANCE = 0.08;
    private static final double MAX_LINEAR_SPEED = 12.0;
    private static final double MAX_ANGULAR_SPEED = 2.0;
    private static final TagKey<Block> BALLOONS =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath("raffinatezza", "balloons"));

    public HelmSeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    public void setSubLevel(ServerSubLevel subLevel, Vec3 localPos) {
        this.subLevelId = subLevel.getUniqueId();
        this.localPos = localPos;
    }

    public UUID getSubLevelId() {
        return subLevelId;
    }

    public Vec3 getLocalPos() {
        return localPos;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        ServerSubLevel subLevel = resolveSubLevel();
        if (subLevel == null || subLevel.isRemoved()) {
            Entity passenger = getFirstPassenger();
            if (passenger != null) {
                passenger.stopRiding();
            }
            if (++missingSubLevelTicks > MAX_MISSING_SUB_LEVEL_TICKS) {
                discard();
            }
            return;
        }
        missingSubLevelTicks = 0;

        if (getFirstPassenger() instanceof Player) {
            applyHelmControls(subLevel);
        } else {
            clearInputs();
            stabilize(subLevel);
        }

        // ✅ SABLE INTEGRATION: Position and camera rotation are handled automatically by Sable's EntitySubLevelRotationHelper
        // No manual position updates or camera rotation needed here
    }

    private ServerSubLevel resolveSubLevel() {
        if (subLevelId == null) return null;
        dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container =
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer((net.minecraft.server.level.ServerLevel) level());
        if (container != null && container.getSubLevel(subLevelId) instanceof ServerSubLevel subLevel) {
            return subLevel;
        }
        return null;
    }

    private Vector3d helmForwardVector(ServerSubLevel subLevel) {
        BlockPos helmLocalPos = subLevel.getPlot().getCenterBlock();
        BlockState helmState = subLevel.getLevel().getBlockState(helmLocalPos);

        Direction facing = Direction.NORTH;
        if (helmState.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)) {
            facing = helmState.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
        }

        Vector3d helmLocalForward = switch (facing) {
            case NORTH -> new Vector3d(0, 0, -1);
            case SOUTH -> new Vector3d(0, 0, 1);
            case WEST -> new Vector3d(-1, 0, 0);
            case EAST -> new Vector3d(1, 0, 0);
            default -> new Vector3d(0, 0, -1);
        };
        return subLevel.logicalPose().transformNormal(helmLocalForward);
    }

    private void applyHelmControls(ServerSubLevel subLevel) {
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null || !handle.isValid()) return;

        Vector3d forward = helmForwardVector(subLevel);
        forward.y = 0.0;
        if (forward.lengthSquared() < 1.0E-6) {
            forward.set(0, 0, -1);
        } else {
            forward.normalize();
        }

        Vector3d linearVelocity = handle.getLinearVelocity(new Vector3d());
        Vector3d angularVelocity = handle.getAngularVelocity(new Vector3d());
        Vector3d linearCorrection = new Vector3d();
        Vector3d angularCorrection = new Vector3d();
        boolean hasBalloons = hasBalloonBlocks(subLevel);
        boolean grounded = isGrounded(subLevel);
        boolean onWater = isOnWater(subLevel);

        int throttle = (inputForward ? 1 : 0) - (inputBackward ? 1 : 0);
        // Allow movement when grounded OR when on water
        if (throttle != 0 && (!grounded && !onWater)) {
            Vector3d targetHorizontal = new Vector3d(forward).mul(CRUISE_SPEED * throttle);
            Vector3d currentHorizontal = new Vector3d(linearVelocity.x, 0.0, linearVelocity.z);
            linearCorrection.add(targetHorizontal.sub(currentHorizontal).mul(LINEAR_RESPONSE));
        }

        int lift = (inputUp ? 1 : 0) - (inputDown ? 1 : 0);
        if (lift != 0 && hasBalloons) {
            linearCorrection.y += ((VERTICAL_SPEED * lift) - linearVelocity.y) * VERTICAL_RESPONSE;
        }

        int turn = (inputLeft ? 1 : 0) - (inputRight ? 1 : 0);
        if (turn != 0) {
            angularCorrection.y += ((YAW_SPEED * turn) - angularVelocity.y) * YAW_RESPONSE;
        }

        addLevelingCorrection(subLevel, angularVelocity, angularCorrection);
        handle.addLinearAndAngularVelocity(linearCorrection, angularCorrection);
        stabilize(
                subLevel,
                inputForward && !grounded && !onWater,
                inputBackward && !grounded && !onWater,
                inputLeft,
                inputRight,
                inputUp && hasBalloons,
                inputDown && hasBalloons
        );
    }

    private void stabilize(ServerSubLevel subLevel) {
        stabilize(subLevel, false, false, false, false, false, false);
    }

    private void stabilize(ServerSubLevel subLevel, boolean forward, boolean backward,
                           boolean left, boolean right, boolean up, boolean down) {
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null || !handle.isValid()) return;

        Vector3d linearVelocity = handle.getLinearVelocity(new Vector3d());
        Vector3d angularVelocity = handle.getAngularVelocity(new Vector3d());
        Vector3d linearCorrection = new Vector3d();
        Vector3d angularCorrection = new Vector3d();

        if (!forward && !backward) {
            linearCorrection.x -= linearVelocity.x * LINEAR_DAMPING;
            linearCorrection.z -= linearVelocity.z * LINEAR_DAMPING;
        }
        if (!up && !down) {
            linearCorrection.y -= linearVelocity.y * LINEAR_DAMPING;
        }
        if (!left && !right) {
            angularCorrection.y -= angularVelocity.y * ANGULAR_DAMPING;
        }
        addLevelingCorrection(subLevel, angularVelocity, angularCorrection);

        addSpeedLimitCorrection(linearVelocity, linearCorrection, MAX_LINEAR_SPEED);
        addSpeedLimitCorrection(angularVelocity, angularCorrection, MAX_ANGULAR_SPEED);

        if (linearCorrection.lengthSquared() > 1.0E-6 || angularCorrection.lengthSquared() > 1.0E-6) {
            handle.addLinearAndAngularVelocity(linearCorrection, angularCorrection);
        }
    }

    private void addLevelingCorrection(ServerSubLevel subLevel, Vector3d angularVelocity, Vector3d angularCorrection) {
        angularCorrection.x -= angularVelocity.x * ANGULAR_DAMPING;
        angularCorrection.z -= angularVelocity.z * ANGULAR_DAMPING;

        Vector3d up = subLevel.logicalPose().transformNormal(new Vector3d(0, 1, 0));
        if (up.lengthSquared() > 1.0E-6) {
            up.normalize();
            Vector3d levelAxis = up.cross(new Vector3d(0, 1, 0), new Vector3d());
            angularCorrection.fma(LEVELING_STRENGTH, levelAxis);
        }
    }

    private boolean hasBalloonBlocks(ServerSubLevel subLevel) {
        for (PlotChunkHolder chunkHolder : subLevel.getPlot().getLoadedChunks()) {
            LevelChunk chunk = chunkHolder.getChunk();
            if (chunk == null) continue;

            var chunkPos = chunk.getPos();
            int baseX = chunkPos.x << 4;
            int baseZ = chunkPos.z << 4;
            Level subLevelAsLevel = subLevel.getLevel();

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = subLevelAsLevel.getMinBuildHeight(); y < subLevelAsLevel.getMaxBuildHeight(); y++) {
                        if (subLevelAsLevel.getBlockState(new BlockPos(baseX + x, y, baseZ + z)).is(BALLOONS)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isGrounded(ServerSubLevel subLevel) {
        AABB bounds = subLevel.boundingBox().toMojang();
        double y = bounds.minY - GROUND_CLEARANCE;
        return hasCollisionBelow(bounds.minX, y, bounds.minZ)
                || hasCollisionBelow(bounds.minX, y, bounds.maxZ)
                || hasCollisionBelow(bounds.maxX, y, bounds.minZ)
                || hasCollisionBelow(bounds.maxX, y, bounds.maxZ)
                || hasCollisionBelow((bounds.minX + bounds.maxX) * 0.5, y,
                (bounds.minZ + bounds.maxZ) * 0.5);
    }

    private boolean isOnWater(ServerSubLevel subLevel) {
        AABB bounds = subLevel.boundingBox().toMojang();
        double y = bounds.minY + GROUND_CLEARANCE;
        return hasWaterAt(bounds.minX, y, bounds.minZ)
                || hasWaterAt(bounds.minX, y, bounds.maxZ)
                || hasWaterAt(bounds.maxX, y, bounds.minZ)
                || hasWaterAt(bounds.maxX, y, bounds.maxZ)
                || hasWaterAt((bounds.minX + bounds.maxX) * 0.5, y,
                (bounds.minZ + bounds.maxZ) * 0.5);
    }

    private boolean hasWaterAt(double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level().getBlockState(pos);
        return state.getMaterial().isReplaceable() && (state.getBlock() == Blocks.WATER || state.getBlock() == Blocks.FLOWING_WATER);
    }

    private boolean hasCollisionBelow(double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        BlockState state = level().getBlockState(pos);
        return !state.getCollisionShape(level(), pos).isEmpty();
    }

    private static void addSpeedLimitCorrection(Vector3d velocity, Vector3d correction, double maxSpeed) {
        double speed = velocity.length();
        if (speed > maxSpeed) {
            correction.fma((maxSpeed / speed) - 1.0, velocity);
        }
    }

    private void clearInputs() {
        inputForward = false;
        inputBackward = false;
        inputLeft = false;
        inputRight = false;
        inputUp = false;
        inputDown = false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide && getFirstPassenger() == null) {
            player.startRiding(this, true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // Return position relative to seat (in plot space)
        // Sable will automatically transform this to world coords when dismounting
        return position().add(1.5, 0, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("sub_level")) {
            subLevelId = tag.getUUID("sub_level");
        }
        if (tag.contains("local_x")) {
            localPos = new Vec3(
                    tag.getDouble("local_x"),
                    tag.getDouble("local_y"),
                    tag.getDouble("local_z"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (subLevelId != null) {
            tag.putUUID("sub_level", subLevelId);
        }
        tag.putDouble("local_x", localPos.x);
        tag.putDouble("local_y", localPos.y);
        tag.putDouble("local_z", localPos.z);
    }
}
