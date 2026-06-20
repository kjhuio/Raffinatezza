package io.github.kjhuio.raffinatezza.block;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import io.github.kjhuio.raffinatezza.entity.HelmSeatEntity;
import io.github.kjhuio.raffinatezza.entity.RafEntityTypes;
import io.github.kjhuio.raffinatezza.network.HelmPilotStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

public class HelmBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<HelmBlock> CODEC = simpleCodec(HelmBlock::new);

    private static final int MAX_BLOCKS = 16384;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final TagKey<Block> MASK_BLOCKS =
            TagKey.create(Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath("raffinatezza", "helm_mask"));

    public HelmBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player, BlockHitResult hit) {

        if (level.isClientSide) return InteractionResult.SUCCESS;

        ServerLevel serverLevel = (ServerLevel) level;

        var containingSubLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(serverLevel, pos);
        if (containingSubLevel instanceof ServerSubLevel serverSubLevel) {
            if (player.isShiftKeyDown()) {
                if (isPilotingThisHelm(player, serverSubLevel)) {
                    stopPiloting(serverLevel, (ServerPlayer) player, serverSubLevel);
                    return InteractionResult.SUCCESS;
                }
                return disassemble(serverLevel, pos, player, serverSubLevel);
            }
            return startPiloting(serverLevel, player, serverSubLevel);
        }

        if (!player.isShiftKeyDown()) {
            player.sendSystemMessage(Component.literal("Sneak + Right Click to assemble"));
            return InteractionResult.SUCCESS;
        }

        return assembleNew(serverLevel, pos, state, player);
    }

    private boolean isPilotingThisHelm(Player player, ServerSubLevel subLevel) {
        return player.getPersistentData().getBoolean("isPilotingHelm")
                && player.getPersistentData().hasUUID("pilotingHelmId")
                && player.getPersistentData().getUUID("pilotingHelmId").equals(subLevel.getUniqueId());
    }

    private InteractionResult startPiloting(ServerLevel serverLevel, Player player, ServerSubLevel subLevel) {
        HelmSeatEntity existingSeat = findSeatForSubLevel(serverLevel, subLevel.getUniqueId());
        if (existingSeat != null && existingSeat.getFirstPassenger() != null) {
            player.sendSystemMessage(Component.literal("Helm is already occupied"));
            return InteractionResult.FAIL;
        }

        if (existingSeat == null) {
            existingSeat = createSeat(serverLevel, subLevel);
            if (existingSeat == null) {
                player.sendSystemMessage(Component.literal("Failed to create seat"));
                return InteractionResult.FAIL;
            }
        }

        if (player.startRiding(existingSeat, true)) {
            // CRITICAL: Prevent vanilla Player.rideTick from dismounting while sneak is held
            player.setShiftKeyDown(false);

            var tag = player.getPersistentData();
            tag.putBoolean("isPilotingHelm", true);
            tag.putUUID("pilotingHelmId", subLevel.getUniqueId());

            if (player instanceof ServerPlayer sp) {
                HelmPilotStatePacket.sendToClient(sp, subLevel.getUniqueId(), true);
            }

            player.sendSystemMessage(Component.literal("Started piloting. Shift+Right-click to stop."));
            return InteractionResult.SUCCESS;
        }

        player.sendSystemMessage(Component.literal("Failed to start piloting"));
        return InteractionResult.FAIL;
    }

    private HelmSeatEntity createSeat(ServerLevel serverLevel, ServerSubLevel subLevel) {
        BlockPos helmLocalPos = subLevel.getPlot().getCenterBlock();
        Direction facing = stateWithFacingOrDefault(subLevel, helmLocalPos);
        Vec3 seatLocalPos = calculateSeatLocalPosition(helmLocalPos, facing);

        HelmSeatEntity seat = RafEntityTypes.HELM_SEAT.get().create(serverLevel);
        if (seat == null) return null;

        // ✅ Seat is created INSIDE the sub-level (in plot space)
        // Sable will automatically transform player position and camera
        Level subLevelInternalLevel = subLevel.getLevel();
        seat.setPos(seatLocalPos.x, seatLocalPos.y, seatLocalPos.z);
        seat.setSubLevel(subLevel, seatLocalPos);
        subLevelInternalLevel.addFreshEntity(seat);  // Add to SUB-LEVEL, not overworld

        return seat;
    }

    private InteractionResult stopPiloting(ServerLevel serverLevel, ServerPlayer player, ServerSubLevel subLevel) {
        HelmSeatEntity seat = findSeatForSubLevel(serverLevel, subLevel.getUniqueId());
        if (seat != null && seat.getFirstPassenger() == player) {
            player.stopRiding();
        }

        var tag = player.getPersistentData();
        tag.putBoolean("isPilotingHelm", false);
        tag.remove("pilotingHelmId");

        HelmPilotStatePacket.sendToClient(player, subLevel.getUniqueId(), false);

        if (seat != null && seat.getFirstPassenger() == null) {
            seat.discard();
        }

        player.sendSystemMessage(Component.literal("Stopped piloting."));
        return InteractionResult.SUCCESS;
    }

    private Vec3 calculateSeatLocalPosition(BlockPos helmLocalPos, Direction facing) {
        double baseX = helmLocalPos.getX() + 0.5;
        double baseY = helmLocalPos.getY() + 0.5;
        double baseZ = helmLocalPos.getZ() + 0.5;
        // Seat is 1 block opposite to the helm's FACING direction
        return switch (facing) {
            case NORTH -> new Vec3(baseX, baseY, baseZ + 1.0);
            case SOUTH -> new Vec3(baseX, baseY, baseZ - 1.0);
            case WEST  -> new Vec3(baseX + 1.0, baseY, baseZ);
            case EAST  -> new Vec3(baseX - 1.0, baseY, baseZ);
            default -> new Vec3(baseX, baseY, baseZ);
        };
    }

    private Direction stateWithFacingOrDefault(ServerSubLevel subLevel, BlockPos localPos) {
        BlockState helmState = subLevel.getLevel().getBlockState(localPos);
        if (helmState.hasProperty(FACING)) {
            return helmState.getValue(FACING);
        }
        return Direction.NORTH;
    }

    private InteractionResult assembleNew(ServerLevel serverLevel, BlockPos pos,
                                          BlockState state, Player player) {
        SubLevelAssemblyHelper.GatherResult result =
                SubLevelAssemblyHelper.gatherConnectedBlocks(
                        pos,
                        serverLevel,
                        MAX_BLOCKS,
                        (originPos, originState, candidatePos, candidateState, direction) ->
                                !candidateState.is(MASK_BLOCKS)
                );

        if (result.assemblyState() != SubLevelAssemblyHelper.GatherResult.State.SUCCESS) {
            player.sendSystemMessage(Component.literal("Assembly failed: " + result.assemblyState()));
            return InteractionResult.FAIL;
        }

        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(
                serverLevel,
                pos,
                result.blocks(),
                result.boundingBox()
        );

        player.sendSystemMessage(Component.literal(
                "Assembled " + result.blocks().size() + " blocks into sub-level"));
        return InteractionResult.CONSUME;
    }

    private InteractionResult disassemble(ServerLevel level, BlockPos helmPos,
                                          Player player, ServerSubLevel subLevel) {
        if (player.isPassenger()) {
            player.sendSystemMessage(Component.literal("Dismount the helm first"));
            return InteractionResult.FAIL;
        }

        HelmSeatEntity seat = findSeatForSubLevel(level, subLevel.getUniqueId());
        if (seat != null && seat.getFirstPassenger() != null) {
            player.sendSystemMessage(Component.literal("A pilot is still at the helm"));
            return InteractionResult.FAIL;
        }

        LevelPlot plot = subLevel.getPlot();
        var pose = subLevel.logicalPose();
        Level subLevelAsLevel = subLevel.getLevel();

        int blocksRestored = 0;
        int itemsRestored = 0;

        for (PlotChunkHolder chunkHolder : plot.getLoadedChunks()) {
            LevelChunk chunk = chunkHolder.getChunk();
            if (chunk == null) continue;

            var localChunkPos = chunk.getPos();
            int baseX = localChunkPos.x << 4;
            int baseZ = localChunkPos.z << 4;

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                        BlockPos plotPos = new BlockPos(baseX + x, y, baseZ + z);
                        BlockState localState = subLevelAsLevel.getBlockState(plotPos);
                        if (localState.isAir()) continue;

                        Vec3 worldVec = pose.transformPosition(new Vec3(
                                plotPos.getX() + 0.5,
                                plotPos.getY() + 0.5,
                                plotPos.getZ() + 0.5));
                        BlockPos overworldPos = BlockPos.containing(worldVec);
                        BlockState restoredState = rotateStateToWorld(localState, subLevel);

                        level.getChunk(overworldPos);
                        level.setBlock(overworldPos, restoredState, 3);

                        BlockEntity be = subLevelAsLevel.getBlockEntity(plotPos);
                        if (be != null) {
                            try {
                                CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
                                tag.putInt("x", overworldPos.getX());
                                tag.putInt("y", overworldPos.getY());
                                tag.putInt("z", overworldPos.getZ());
                                BlockEntity newBe = BlockEntity.loadStatic(
                                        overworldPos, restoredState, tag, level.registryAccess());
                                if (newBe != null) {
                                    level.setBlockEntity(newBe);
                                }
                            } catch (Exception e) {
                                player.sendSystemMessage(Component.literal(
                                        "BlockEntity restore failed at " + overworldPos));
                            }
                        }

                        subLevelAsLevel.removeBlockEntity(plotPos);
                        subLevelAsLevel.setBlock(plotPos, Blocks.AIR.defaultBlockState(), 3);
                        blocksRestored++;
                    }
                }
            }
        }

        try {
            var plotChunkMin = plot.getChunkMin();
            var plotChunkMax = plot.getChunkMax();
            AABB aabb = new AABB(
                    plotChunkMin.x * 16, level.getMinBuildHeight(), plotChunkMin.z * 16,
                    (plotChunkMax.x + 1) * 16, level.getMaxBuildHeight(), (plotChunkMax.z + 1) * 16);
            List<ItemEntity> items = subLevelAsLevel.getEntitiesOfClass(ItemEntity.class, aabb);
            for (ItemEntity item : items) {
                Vec3 worldVec = pose.transformPosition(item.position());
                ItemEntity newItem = new ItemEntity(
                        level, worldVec.x, worldVec.y, worldVec.z, item.getItem());
                newItem.setDeltaMovement(pose.transformNormal(item.getDeltaMovement()));
                level.addFreshEntity(newItem);
                item.discard();
                itemsRestored++;
            }
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Item restore skipped: " + e.getMessage()));
        }

        if (plot instanceof dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot serverPlot) {
            try { serverPlot.destroyAllBlocks(); } catch (Exception ignored) {}
        }
        try { subLevel.deleteAllEntities(); } catch (Exception ignored) {}
        subLevel.markRemoved();

        player.sendSystemMessage(Component.literal(
                "Disassembled " + blocksRestored + " blocks, " + itemsRestored + " items"));
        return InteractionResult.SUCCESS;
    }

    private HelmSeatEntity findSeatForSubLevel(ServerLevel level, UUID subLevelId) {
        for (var entity : level.getAllEntities()) {
            if (entity instanceof HelmSeatEntity seat) {
                if (seat.getSubLevelId() != null && seat.getSubLevelId().equals(subLevelId)) {
                    return seat;
                }
            }
        }
        return null;
    }

    private BlockState rotateStateToWorld(BlockState state, ServerSubLevel subLevel) {
        BlockState rotated = state;
        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty directionProperty) {
                rotated = setDirection(rotated, directionProperty,
                        rotateDirectionToWorld(state.getValue(directionProperty), subLevel));
            }
        }
        return rotated;
    }

    private Direction rotateDirectionToWorld(Direction localDirection, ServerSubLevel subLevel) {
        Vector3d worldVector = subLevel.logicalPose().transformNormal(new Vector3d(
                localDirection.getStepX(),
                localDirection.getStepY(),
                localDirection.getStepZ()
        ));
        return Direction.getNearest(worldVector.x, worldVector.y, worldVector.z);
    }

    private static BlockState setDirection(BlockState state, DirectionProperty property, Direction direction) {
        if (property.getPossibleValues().contains(direction)) {
            return state.setValue(property, direction);
        }

        Direction horizontalDirection = direction.getAxis().isHorizontal()
                ? direction
                : Direction.NORTH;
        if (property.getPossibleValues().contains(horizontalDirection)) {
            return state.setValue(property, horizontalDirection);
        }

        return state;
    }
}
