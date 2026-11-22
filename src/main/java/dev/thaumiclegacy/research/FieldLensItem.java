package dev.thaumiclegacy.research;

import dev.thaumiclegacy.aspect.AspectCatalog;
import dev.thaumiclegacy.research.AspectDiscoveryData.Specimen;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
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

	public static void registerEntityInteraction() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, ignored) -> {
			if (player.isSpectator() || !(player.getItemInHand(hand).getItem() instanceof FieldLensItem)
					|| !candidate(entity)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer) {
				if (!observeHeldItem(serverPlayer, hand)) {
					Entity target = findEntity(serverPlayer);
					if (target == entity) {
						observe(serverPlayer, specimen(target), null);
					} else {
						noTarget(serverPlayer);
					}
				}
			}
			return InteractionResult.SUCCESS;
		});
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() instanceof ServerPlayer player) {
			if (observeHeldItem(player, context.getHand())) {
				return InteractionResult.CONSUME;
			}
			BlockHitResult hit = clipToClickedBlock(context, player);
			if (hit == null) {
				noTarget(player);
			} else {
				Entity entity = findEntity(player);
				if (entity == null) {
					observeBlock(player, hit.getBlockPos());
				} else {
					observe(player, specimen(entity), null);
				}
			}
		}
		return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			if (!observeHeldItem(serverPlayer, hand)) {
				Entity target = findEntity(serverPlayer);
				if (target != null) {
					observe(serverPlayer, specimen(target), null);
				} else {
					BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
					if (hit.getType() == HitResult.Type.BLOCK) {
						observeBlock(serverPlayer, hit.getBlockPos());
					} else {
						noTarget(serverPlayer);
					}
				}
			}
		}
		return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
	}

	private static boolean observeHeldItem(ServerPlayer player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || player.getOffhandItem().isEmpty()) {
			return false;
		}
		observe(player, new Specimen("item", BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem())), null);
		return true;
	}

	private static Entity findEntity(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 ray = player.getViewVector(1.0F).scale(player.entityInteractionRange());
		Vec3 end = eye.add(ray);
		var level = player.serverLevel();
		var obstruction = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		var hit = ProjectileUtil.getEntityHitResult(level, player, eye, obstruction.getLocation(),
				player.getBoundingBox().expandTowards(ray).inflate(1.0), FieldLensItem::candidate, 0.0F);
		return hit == null ? null : hit.getEntity();
	}

	private static boolean candidate(Entity entity) {
		return entity.isAlive() && !(entity instanceof Player)
				&& (entity instanceof ItemEntity || entity instanceof LivingEntity);
	}

	private static Specimen specimen(Entity entity) {
		return entity instanceof ItemEntity drop
				? new Specimen("item", BuiltInRegistries.ITEM.getKey(drop.getItem().getItem()))
				: new Specimen("entity", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
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

	private static void observeBlock(ServerPlayer player, BlockPos target) {
		var state = player.serverLevel().getBlockState(target);
		ResourceLocation legacy = ObservationCatalog.find(state);
		ResourceLocation id = legacy == null ? BuiltInRegistries.BLOCK.getKey(state.getBlock()) : legacy;
		observe(player, new Specimen("block", id), legacy);
	}

	static void observe(ServerPlayer player, Specimen specimen, ResourceLocation legacy) {
		AspectCatalog catalog = AspectCatalog.current();
		var oldData = ObservationData.get(player);
		var uuid = player.getUUID();
		var old = oldData.getObservations(uuid);
		var data = AspectDiscoveryData.get(player);
		data.importLegacy(uuid, old, catalog);
		boolean wasUnlocked = data.firstResearchUnlocked(uuid, old);
		var result = data.record(uuid, specimen, catalog);
		if (result.state() == AspectDiscoveryData.State.UNSUPPORTED && legacy == null) {
			noTarget(player);
			return;
		}
		int legacyCount = legacy == null ? 0 : oldData.recordObservation(uuid, legacy);
		if (result.state() != AspectDiscoveryData.State.NEW && legacyCount == 0) {
			player.displayClientMessage(Component.translatable("message.thaumiclegacy.already_observed"), true);
			return;
		}

		String name = specimen.kind().equals("item") ? BuiltInRegistries.ITEM.get(specimen.id()).getDescriptionId()
				: specimen.id().toLanguageKey(specimen.kind());
		player.displayClientMessage(Component.translatable("message.thaumiclegacy.observed",
				Component.translatableWithFallback(name, specimen.id().toString())), true);
		if (!wasUnlocked && data.firstResearchUnlocked(uuid, oldData.getObservations(uuid))) {
			player.displayClientMessage(Component.translatable("message.thaumiclegacy.research_unlocked"), false);
		}
		if (ServerPlayNetworking.canSend(player, ScanFeedbackPayload.TYPE)) {
			var discovery = result.discovery() == null ? null : catalog.definitions().get(result.discovery());
			ServerPlayNetworking.send(player, new ScanFeedbackPayload(discovery == null ? 0xD2B277 : discovery.color(),
					discovery != null));
		}
	}

	private static void noTarget(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.thaumiclegacy.aspect_scan_hint"), true);
	}
}
