package dev.thaumiclegacy.wand;

import dev.thaumiclegacy.node.AuraNodeBlockEntity;
import dev.thaumiclegacy.node.AuraNodes;
import dev.thaumiclegacy.research.NodeResearch;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

public final class WandItem extends Item {
	private static final int USE_DURATION = 72_000;
	private static final int TRANSFER_TICKS = 5;

	public WandItem(Properties properties) {
		super(properties.stacksTo(1).component(WandVis.COMPONENT, WandVis.EMPTY));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null || !context.getLevel().getBlockState(context.getClickedPos()).is(AuraNodes.BLOCK)) {
			return InteractionResult.PASS;
		}
		return use(context.getLevel(), player, context.getHand()).getResult();
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack wand = player.getItemInHand(hand);
		if (!player.isAlive() || player.isSpectator() || aimedNode(player) == null) {
			return InteractionResultHolder.pass(wand);
		}
		if (player instanceof ServerPlayer server && !NodeResearch.canCharge(server)) {
			feedback(server, "learn_first");
			return InteractionResultHolder.fail(wand);
		}
		player.startUsingItem(hand);
		return InteractionResultHolder.consume(wand);
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return USE_DURATION;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
		if (!(entity instanceof ServerPlayer player) || (USE_DURATION - remainingTicks + 1) % TRANSFER_TICKS != 0) {
			return;
		}
		BlockPos pos = aimedNode(player);
		if (pos == null || player.isSpectator() || !player.isAlive() || !NodeResearch.canCharge(player)
				|| !level.mayInteract(player, pos) || !(level.getBlockEntity(pos) instanceof AuraNodeBlockEntity node)) {
			player.stopUsingItem();
			return;
		}
		var snapshot = node.snapshot();
		var wand = WandVis.get(stack);
		boolean hasSpaceForNode = false;
		for (var primal : WandVis.Primal.values()) {
			if (wand.amount(primal) >= WandVis.CAPACITY || snapshot.capacity(primal) == 0) {
				continue;
			}
			hasSpaceForNode = true;
			if (snapshot.reserve(primal) > 0 && node.transferTo(stack, primal, 1) == 1) {
				NodeResearch.recordCharge(player);
				return;
			}
		}
		feedback(player, hasSpaceForNode ? "drained" : "wand_full");
		player.stopUsingItem();
	}

	private static BlockPos aimedNode(Player player) {
		if (player.pick(player.blockInteractionRange(), 1.0F, false) instanceof BlockHitResult hit
				&& hit.getType() == HitResult.Type.BLOCK) {
			var pos = hit.getBlockPos();
			if (player.level().hasChunkAt(pos) && player.level().getBlockState(pos).is(AuraNodes.BLOCK)) {
				return pos;
			}
		}
		return null;
	}

	private static void feedback(ServerPlayer player, String key) {
		player.displayClientMessage(Component.translatable("message.thaumiclegacy.node." + key), true);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		WandVis vis = WandVis.get(stack);
		for (var primal : WandVis.Primal.values()) {
			tooltip.add(Component.translatable("tooltip.thaumiclegacy.wand.primal",
					Component.translatable(primal.translationKey()), vis.amount(primal), WandVis.CAPACITY));
		}
	}
}
