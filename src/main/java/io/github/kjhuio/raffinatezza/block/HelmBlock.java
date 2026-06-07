package io.github.kjhuio.raffinatezza.block;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import io.github.kjhuio.raffinatezza.entity.HelmSeatEntity;
import io.github.kjhuio.raffinatezza.entity.RafEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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

        if (!player.isShiftKeyDown()) {
            player.sendSystemMessage(
                    Component.literal("Sneak + Right Click to assemble/mount"));
            return InteractionResult.SUCCESS;
        }

        // 既にSubLevel内にある場合はマウント処理のみ実行
        var containingSubLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(serverLevel, pos);
        if (containingSubLevel instanceof ServerSubLevel serverSubLevel) {
            return mountExistingHelm(serverLevel, pos, player, serverSubLevel);
        }

        // 連結ブロックを収集（マスクブロックを除外）
        SubLevelAssemblyHelper.GatherResult result =
                SubLevelAssemblyHelper.gatherConnectedBlocks(
                        pos,
                        serverLevel,
                        MAX_BLOCKS,
                        (originPos, originState, candidatePos, candidateState, direction) ->
                                !candidateState.is(MASK_BLOCKS)
                );

        if (result.assemblyState() != SubLevelAssemblyHelper.GatherResult.State.SUCCESS) {
            player.sendSystemMessage(
                    Component.literal("Assembly failed: " + result.assemblyState()));
            return InteractionResult.FAIL;
        }

        // アセンブル
        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(
                serverLevel,
                pos,
                result.blocks(),
                result.boundingBox()
        );

        // Helmの向きに応じて座席位置を計算
        Vec3 seatPos = calculateSeatPosition(pos, state.getValue(FACING));

        // シートエンティティをオーバーワールドにスポーンしてプレイヤーを乗せる
        HelmSeatEntity seat = RafEntityTypes.HELM_SEAT.get().create(serverLevel);
        if (seat != null) {
            // addFreshEntity前に必ず座標を設定（Sableのkick判定を回避）
            seat.setPos(seatPos.x, seatPos.y, seatPos.z);
            seat.setSubLevel(subLevel, seatPos);
            serverLevel.addFreshEntity(seat);
            
            player.sendSystemMessage(Component.literal("Seat spawned at " + 
                String.format("%.2f, %.2f, %.2f", seatPos.x, seatPos.y, seatPos.z)));
            
            // addFreshEntity直後はエンティティが未登録のためstartRidingが失敗する場合があるので
            // 次tick以降でマウントを試みる
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                seat.setPendingMount(sp);
            }
        } else {
            player.sendSystemMessage(Component.literal("Failed to create seat entity"));
        }

        return InteractionResult.CONSUME;
    }

    /**
     * Helmの向きに応じた座席位置を計算
     * 北向き(NORTH)の場合は南側1ブロック手前に座席を配置
     */
    private Vec3 calculateSeatPosition(BlockPos helmPos, Direction facing) {
        double baseX = helmPos.getX() + 0.5;
        double baseY = helmPos.getY() + 0.5;
        double baseZ = helmPos.getZ() + 0.5;

        // Helmの向きの反対方向に1ブロックオフセット
        // 例: Helmが北向き(NORTH, z-)の場合、座席は南側(z+)に配置
        return switch (facing) {
            case NORTH -> new Vec3(baseX, baseY, baseZ + 1.0);  // 南側
            case SOUTH -> new Vec3(baseX, baseY, baseZ - 1.0);  // 北側
            case WEST -> new Vec3(baseX + 1.0, baseY, baseZ);   // 東側
            case EAST -> new Vec3(baseX - 1.0, baseY, baseZ);   // 西側
            default -> new Vec3(baseX, baseY, baseZ);
        };
    }

    /**
     * HelmがすでにSubLevel内にある場合のマウント処理
     */
    private InteractionResult mountExistingHelm(ServerLevel serverLevel, BlockPos helmPos,
                                                Player player, ServerSubLevel subLevel) {
        // 既存のシートエンティティを探す
        HelmSeatEntity existingSeat = findSeatForSubLevel(serverLevel, subLevel.getUniqueId());
        
        if (existingSeat != null) {
            // 既存のシートが見つかった場合、乗車
            if (existingSeat.getFirstPassenger() == null) {
                player.startRiding(existingSeat, true);
                return InteractionResult.SUCCESS;
            } else {
                player.sendSystemMessage(Component.literal("Helm is already occupied"));
                return InteractionResult.FAIL;
            }
        } else {
            // シートが存在しない場合は新規作成
            HelmSeatEntity seat = RafEntityTypes.HELM_SEAT.get().create(serverLevel);
            if (seat != null) {
                // Helmの向きを取得（SubLevel内のBlockStateから）
                BlockState helmState = serverLevel.getBlockState(helmPos);
                Direction facing = helmState.hasProperty(FACING) ? helmState.getValue(FACING) : Direction.NORTH;
                
                // Helmの向きに応じて座席位置を計算
                Vec3 seatPos = calculateSeatPosition(helmPos, facing);
                
                seat.setPos(seatPos.x, seatPos.y, seatPos.z);
                seat.setSubLevel(subLevel, seatPos);
                serverLevel.addFreshEntity(seat);
                
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    seat.setPendingMount(sp);
                }
                return InteractionResult.CONSUME;
            }
        }
        
        return InteractionResult.FAIL;
    }

    /**
     * 指定されたSubLevelに対応するHelmSeatEntityを探す
     */
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
}
