package dev.thaumiclegacy.node;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumiclegacy.node.AuraNodeBlock.Variant;
import dev.thaumiclegacy.wand.WandVis.Primal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

final class AuraNodeFeature extends Feature<AuraNodeFeature.Configuration> {
	private static final long NODE_SALT = 0x320918DE101F0C6BL;

	AuraNodeFeature() {
		super(Configuration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<Configuration> context) {
		var level = context.level();
		BlockPos pos = context.origin();
		if (!level.getLevel().dimension().equals(Level.OVERWORLD) || level.isOutsideBuildHeight(pos)
				|| !level.ensureCanWrite(pos)) {
			return false;
		}
		var original = level.getBlockState(pos);
		if (!original.isAir()) {
			return false;
		}
		BlockPos groundPos = pos.below();
		var ground = level.getBlockState(groundPos);
		if (!ground.getFluidState().isEmpty() || !ground.isFaceSturdy(level, groundPos, Direction.UP)) {
			return false;
		}
		Plan plan = plan(level.getSeed(), pos, context.config());
		var state = AuraNodes.BLOCK.defaultBlockState().setValue(AuraNodeBlock.VARIANT, plan.variant());
		if (!level.setBlock(pos, state, Block.UPDATE_CLIENTS)) {
			return false;
		}
		if (!(level.getBlockEntity(pos) instanceof AuraNodeBlockEntity node)) {
			level.setBlock(pos, original, Block.UPDATE_CLIENTS);
			return false;
		}
		// The chunk owns its newly created block entity until world generation publishes it.
		node.initialize(plan.id(), plan.capacities(), 0);
		return true;
	}

	static Plan plan(long worldSeed, BlockPos pos, Configuration configuration) {
		// Keep identity and contents stable even when storage and rendering access them in different orders.
		RandomSource random = RandomSource.create(worldSeed ^ NODE_SALT ^ Long.rotateLeft(pos.asLong(), 23));
		int roll = random.nextInt(configuration.normalWeight() + configuration.brightWeight() + configuration.unstableWeight());
		Variant variant = roll < configuration.normalWeight() ? Variant.NORMAL
				: roll < configuration.normalWeight() + configuration.brightWeight() ? Variant.BRIGHT : Variant.UNSTABLE;
		Map<Primal, Integer> capacities = new EnumMap<>(Primal.class);
		for (Primal primal : Primal.values()) {
			capacities.put(primal, variant.minCapacity() + random.nextInt(variant.maxCapacity() - variant.minCapacity() + 1));
		}
		UUID id = UUID.nameUUIDFromBytes(ByteBuffer.allocate(3 * Long.BYTES)
				.putLong(NODE_SALT).putLong(worldSeed).putLong(pos.asLong()).array());
		return new Plan(id, variant, Map.copyOf(capacities));
	}

	record Plan(UUID id, Variant variant, Map<Primal, Integer> capacities) { }

	record Configuration(int normalWeight, int brightWeight, int unstableWeight) implements FeatureConfiguration {
		private static final Codec<Integer> WEIGHT = Codec.intRange(0, 1_000);
		private static final Codec<Configuration> CODEC = RecordCodecBuilder.<Configuration>create(instance ->
				instance.group(
						WEIGHT.fieldOf("normal_weight").forGetter(Configuration::normalWeight),
						WEIGHT.fieldOf("bright_weight").forGetter(Configuration::brightWeight),
						WEIGHT.fieldOf("unstable_weight").forGetter(Configuration::unstableWeight)
				).apply(instance, Configuration::new)).validate(config ->
					config.normalWeight + config.brightWeight + config.unstableWeight > 0
							? DataResult.success(config) : DataResult.error(() -> "At least one aura node variant must have positive weight"));
	}
}
