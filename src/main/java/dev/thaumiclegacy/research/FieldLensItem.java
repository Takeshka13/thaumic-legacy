package dev.thaumiclegacy.research;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class FieldLensItem extends Item {
	public FieldLensItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() instanceof ServerPlayer player) {
			BlockHitResult hit = clipToClickedBlock(context, player);
			if (hit == null) {
				noTarget(player);
			} else {
				observe(player, hit.getBlockPos());
			}
		}
		return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
			if (hit.getType() == HitResult.Type.BLOCK) {
				observe(serverPlayer, hit.getBlockPos());
			} else {
				noTarget(serverPlayer);
			}
		}
		return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
	}

	private static BlockHitResult clipToClickedBlock(UseOnContext context, ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 click = context.getClickLocation();
		Vec3 offset = click.subtract(eye);
		double reach = player.blockInteractionRange();
		if (!(offset.lengthSqr() <= reach * reach)) {
			return null;
		}

		// Extend slightly beyond the reported surface so the clip includes it.
		Vec3 end = click.add(offset.normalize().scale(1.0E-4));
		BlockHitResult hit = context.getLevel().clip(new ClipContext(
				eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(context.getClickedPos())
				&& eye.distanceToSqr(hit.getLocation()) <= reach * reach) {
			return hit;
		}
		return null;
	}

	private static void observe(ServerPlayer player, BlockPos target) {
		ResourceLocation observation = ObservationCatalog.find(player.serverLevel().getBlockState(target));
		if (observation == null) {
			noTarget(player);
			return;
		}

		ObservationData data = ObservationData.get(player);
		int count = data.recordObservation(player.getUUID(), observation);
		if (count == 0) {
			player.displayClientMessage(Component.translatable("message.thaumiclegacy.already_observed"), true);
			return;
		}

		player.displayClientMessage(Component.translatable("message.thaumiclegacy.observed",
				Component.translatable(observation.toLanguageKey("block"))), true);
		if (ObservationCatalog.firstResearchUnlocked(count) && !ObservationCatalog.firstResearchUnlocked(count - 1)) {
			player.displayClientMessage(Component.translatable("message.thaumiclegacy.research_unlocked"), false);
		}
	}

	private static void noTarget(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.thaumiclegacy.no_target"), true);
	}
}
