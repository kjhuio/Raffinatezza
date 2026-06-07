package io.github.kjhuio.raffinatezza.entity;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.UUID;

public class HelmSeatEntity extends Entity {

    // ヘルムのプロット空間での座標（アセンブル後のplot内座標）
    private UUID subLevelId;
    private int missingSubLevelTicks;

    // アセンブル前のHelmブロックのワールド座標（シートの固定位置）
    private double helmWorldX, helmWorldY, helmWorldZ;

    // スポーン直後のマウント待ち（addFreshEntityの次tick以降でstartRidingする）
    private UUID pendingMountPlayer;
    private int pendingMountTicks;

    // 入力状態（パケットで更新）
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
    private static final double LEVELING_STRENGTH = 0.08;
    private static final double MAX_LINEAR_SPEED = 12.0;
    private static final double MAX_ANGULAR_SPEED = 2.0;

    public HelmSeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true); // モデルなし
    }

    public void setHelmPlotPos(BlockPos pos) {
        // 未使用（後方互換のため残す）
    }

    public void setSubLevel(ServerSubLevel subLevel, Vec3 seatWorldPos) {
        this.subLevelId = subLevel.getUniqueId();
        // 座席のワールド座標を保存（毎tickここに固定される）
        this.helmWorldX = seatWorldPos.x;
        this.helmWorldY = seatWorldPos.y;
        this.helmWorldZ = seatWorldPos.z;
    }

    public UUID getSubLevelId() {
        return subLevelId;
    }

    public void setPendingMount(net.minecraft.server.level.ServerPlayer player) {
        this.pendingMountPlayer = player.getUUID();
        this.pendingMountTicks = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        ServerLevel serverLevel = (ServerLevel) level();

        ServerSubLevel subLevel = resolveSubLevel(serverLevel);
        if (subLevel == null || subLevel.isRemoved()) {
            if (++missingSubLevelTicks > MAX_MISSING_SUB_LEVEL_TICKS) {
                discard();
            }
            return;
        }
        missingSubLevelTicks = 0;

        // シートをHelmブロックの元のワールド座標に固定（SubLevelのplot内に入らないようにする）
        setPos(helmWorldX, helmWorldY, helmWorldZ);

        // スポーン直後のマウント処理（最大5tick試みる）
        if (pendingMountPlayer != null) {
            pendingMountTicks++;
            var player = serverLevel.getPlayerByUUID(pendingMountPlayer);
            if (player != null && getFirstPassenger() == null) {
                boolean success = player.startRiding(this, true);
                if (level().isClientSide == false) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Mount attempt " + pendingMountTicks + ": " + (success ? "SUCCESS" : "FAILED")));
                }
            }
            if (getFirstPassenger() != null || pendingMountTicks > 5) {
                if (level().isClientSide == false && player != null) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Mount " + (getFirstPassenger() != null ? "complete" : "failed after 5 tries")));
                }
                pendingMountPlayer = null;
            }
        }

        if (getFirstPassenger() instanceof Player) {
            applyHelmControls(subLevel);
        } else {
            clearInputs();
            stabilize(subLevel, false, false, false, false, false, false);
        }
    }

    private ServerSubLevel resolveSubLevel(ServerLevel level) {
        if (subLevelId == null) return null;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null && container.getSubLevel(subLevelId) instanceof ServerSubLevel subLevel) {
            return subLevel;
        }
        return null;
    }

    private void applyHelmControls(ServerSubLevel subLevel) {
        RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        if (handle == null || !handle.isValid()) return;

        // 船の向きを基準にした方向ベクトル
        Vector3d forward = subLevel.logicalPose().transformNormal(new Vector3d(0, 0, -1));
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

        int throttle = (inputForward ? 1 : 0) - (inputBackward ? 1 : 0);
        if (throttle != 0) {
            Vector3d targetHorizontal = new Vector3d(forward).mul(CRUISE_SPEED * throttle);
            Vector3d currentHorizontal = new Vector3d(linearVelocity.x, 0.0, linearVelocity.z);
            linearCorrection.add(targetHorizontal.sub(currentHorizontal).mul(LINEAR_RESPONSE));
        }

        int lift = (inputUp ? 1 : 0) - (inputDown ? 1 : 0);
        if (lift != 0) {
            linearCorrection.y += ((VERTICAL_SPEED * lift) - linearVelocity.y) * VERTICAL_RESPONSE;
        }

        int turn = (inputLeft ? 1 : 0) - (inputRight ? 1 : 0);
        if (turn != 0) {
            angularCorrection.y += ((YAW_SPEED * turn) - angularVelocity.y) * YAW_RESPONSE;
        }

        addLevelingCorrection(subLevel, angularVelocity, angularCorrection);
        handle.addLinearAndAngularVelocity(linearCorrection, angularCorrection);
        stabilize(subLevel, inputForward, inputBackward, inputLeft, inputRight, inputUp, inputDown);
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

    // 右クリックで乗車
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
        return position().add(1.5, 0, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        helmWorldX = tag.getDouble("hwx");
        helmWorldY = tag.getDouble("hwy");
        helmWorldZ = tag.getDouble("hwz");
        if (tag.hasUUID("sub_level")) {
            subLevelId = tag.getUUID("sub_level");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("hwx", helmWorldX);
        tag.putDouble("hwy", helmWorldY);
        tag.putDouble("hwz", helmWorldZ);
        if (subLevelId != null) {
            tag.putUUID("sub_level", subLevelId);
        }
    }
}
