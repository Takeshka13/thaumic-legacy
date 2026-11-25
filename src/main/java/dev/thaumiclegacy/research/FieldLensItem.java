package dev.thaumiclegacy.research;

import dev.thaumiclegacy.revealing.AspectTooltipPayload;

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
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class FieldLensItem extends Item {
	public FieldLensItem(Properties properties) {
		super(properties);
	}

	public static void registerEntityInteraction() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, ignored) -> {
			if (!(player.getItemInHand(hand).getItem() instanceof FieldLensItem lens) || !lens.canScan(player)
					|| !candidate(entity)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer) {
				lens.beginScan(serverPlayer);
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
		if (context.getPlayer() == null || !canScan(context.getPlayer())) {
			return InteractionResult.PASS;
		}
		if (context.getPlayer() instanceof ServerPlayer player) {
			beginScan(player);
			if (observeHeldItem(player, context.getHand())) {
				return InteractionResult.CONSUME;
			}
			BlockHitResult hit = clipToClickedBlock(context, player);
			if (hit == null) {
				noTarget(player);
			} else {
				Entity entity = findEntity(player);
				if (entity != null) {
					observe(player, specimen(entity), null);
				} else {
					scanBlockOrFluid(player, hit);
				}
			}
		}
		return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
	}


	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!canScan(player)) {
			return InteractionResultHolder.pass(held);
		}
		if (player instanceof ServerPlayer serverPlayer) {
			beginScan(serverPlayer);
			if (!observeHeldItem(serverPlayer, hand)) {
				Entity target = findEntity(serverPlayer);
				if (target != null) {
					observe(serverPlayer, specimen(target), null);
				} else {
					scanBlockOrFluid(serverPlayer, viewBlock(serverPlayer));
				}
			}
		}
		return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
	}

	protected boolean canScan(Player player) {
		return player.isAlive() && !player.isSpectator();
	}

	protected void beginScan(ServerPlayer player) {
	}

	protected boolean revealBlock(ServerPlayer player, BlockPos target) {
		return false;
	}

	protected void onAirScan(ServerPlayer player) {
		noTarget(player);
	}

	private static BlockHitResult viewBlock(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		return player.serverLevel().clip(new ClipContext(eye,
			eye.add(player.getViewVector(1.0F).scale(player.blockInteractionRange())),
			ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
	}

	private static Specimen findFluid(ServerPlayer player, BlockHitResult solid) {
		var level = player.serverLevel();
		Vec3 end = solid.getLocation();
		var hit = level.clip(new ClipContext(player.getEyePosition(), end, ClipContext.Block.OUTLINE,
			ClipContext.Fluid.ANY, player) {
			@Override
			public VoxelShape getBlockShape(BlockState state, BlockGetter world, BlockPos pos) {
				return Shapes.empty();
			}
		});
		if (hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		var fluid = level.getFluidState(hit.getBlockPos());
		if (fluid.is(FluidTags.WATER)) {
			return new Specimen("fluid", ResourceLocation.withDefaultNamespace("water"));
		}
		if (fluid.is(FluidTags.LAVA)) {
			return new Specimen("fluid", ResourceLocation.withDefaultNamespace("lava"));
		}
		return null;
	}

	private void scanBlockOrFluid(ServerPlayer player, BlockHitResult solid) {
		Specimen fluid = findFluid(player, solid);
		if (fluid != null) {
			observe(player, fluid, null);
		} else if (solid.getType() == HitResult.Type.BLOCK) {
			observeBlock(player, solid.getBlockPos());
		} else {
			onAirScan(player);
		}
	}

	public void preview(ServerPlayer player, InteractionHand hand) {
		if (!canScan(player) || !ServerPlayNetworking.canSend(player, LensFocusPayload.TYPE)) {
			return;
		}
		Specimen target = heldSpecimen(player, hand);
		if (target == null) {
			Entity entity = findEntity(player);
			if (entity != null) {
				target = specimen(entity);
			} else {
				var solid = viewBlock(player);
				target = findFluid(player, solid);
				if (target == null && solid.getType() == HitResult.Type.BLOCK) {
					var block = player.serverLevel().getBlockState(solid.getBlockPos()).getBlock();
					target = new Specimen("block", BuiltInRegistries.BLOCK.getKey(block));
				}
			}
		}
		if (target == null) {
			ServerPlayNetworking.send(player, new LensFocusPayload(Component.empty(), java.util.List.of()));
		} else {
			var aspects = AspectDiscoveryData.get(player).observedAspects(player.getUUID(), target, AspectCatalog.current());
			ServerPlayNetworking.send(player, new LensFocusPayload(specimenName(target.kind(), target.id()),
				aspects.stream().limit(LensFocusPayload.MAX_ASPECTS).toList()));
		}
	}

	private static Specimen heldSpecimen(ServerPlayer player, InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && !player.getOffhandItem().isEmpty()
			? new Specimen("item", BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem())) : null;
	}

	private static boolean observeHeldItem(ServerPlayer player, InteractionHand hand) {
		Specimen target = heldSpecimen(player, hand);
		if (target == null) {
			return false;
		}
		observe(player, target, null);
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

	private void observeBlock(ServerPlayer player, BlockPos target) {
		if (revealBlock(player, target)) {
			return;
		}
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
		if (result.state() == AspectDiscoveryData.State.REPEAT
				|| (result.state() == AspectDiscoveryData.State.UNSUPPORTED && legacyCount == 0)) {
			player.displayClientMessage(Component.translatable("message.thaumiclegacy.already_observed"), true);
			return;
		}

		player.displayClientMessage(Component.translatable("message.thaumiclegacy.observed",
				specimenName(specimen.kind(), specimen.id())), true);
		if (!wasUnlocked && data.firstResearchUnlocked(uuid, oldData.getObservations(uuid))) {
			player.displayClientMessage(Component.translatable("message.thaumiclegacy.research_unlocked"), false);
		}
		AspectTooltipPayload.sync(player);
		if (ServerPlayNetworking.canSend(player, ScanFeedbackPayload.TYPE)) {
			var discovery = result.discovery() == null ? null : catalog.definitions().get(result.discovery());
			ServerPlayNetworking.send(player, new ScanFeedbackPayload(discovery == null ? 0xD2B277 : discovery.color(),
					discovery != null));
		}
	}

	public static Component specimenName(String kind, ResourceLocation id) {
		String key = kind.equals("item")
			? BuiltInRegistries.ITEM.getOptional(id).map(Item::getDescriptionId).orElse(id.toLanguageKey(kind))
			: id.toLanguageKey(kind);
		return Component.translatableWithFallback(key, id.toString());
	}

	private static void noTarget(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.thaumiclegacy.aspect_scan_hint"), true);
	}
}
