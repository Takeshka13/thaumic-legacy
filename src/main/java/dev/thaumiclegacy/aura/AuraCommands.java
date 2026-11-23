package dev.thaumiclegacy.aura;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Operator-only diagnostics for exercising the aura without a workshop. */
public final class AuraCommands {
	private static final String KEY = "commands.thaumiclegacy.aura.";

	private AuraCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
				literal("thaumiclegacy").then(literal("aura")
						.requires(source -> source.hasPermission(2))
						.then(literal("status").executes(context -> status(context.getSource())))
						.then(literal("spend").then(argument("amount", IntegerArgumentType.integer(1))
								.executes(context -> spend(context.getSource(), IntegerArgumentType.getInteger(context, "amount")))))
						.then(change("flux", "Added %s Flux.", AuraService::addFlux))
						.then(change("restore", "Restored %s Vis.", AuraService::restoreVis))
						.then(change("clean", "Removed %s Flux.", AuraService::removeFlux)))));
	}

	private static int status(CommandSourceStack source) {
		ChunkPos pos = chunk(source);
		var reading = AuraService.read(source.getLevel(), pos);
		source.sendSuccess(() -> Component.translatableWithFallback(KEY + "status",
				"Aura: %s/%s Vis, %s Flux (chunk %s, %s).",
				reading.vis(), reading.capacity(), reading.flux(), pos.x, pos.z), false);
		return reading.vis();
	}

	private static int spend(CommandSourceStack source, int amount) {
		if (!AuraService.trySpendVis(source.getLevel(), chunk(source), amount)) {
			source.sendFailure(Component.translatableWithFallback(KEY + "insufficient", "Cannot spend %s Vis.", amount));
			return 0;
		}
		source.sendSuccess(() -> Component.translatableWithFallback(KEY + "spend", "Spent %s Vis.", amount), false);
		return amount;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> change(String operation, String fallback, AuraChange change) {
		return literal(operation).then(argument("amount", IntegerArgumentType.integer(1)).executes(context -> {
			var source = context.getSource();
			int applied = change.apply(source.getLevel(), chunk(source), IntegerArgumentType.getInteger(context, "amount"));
			source.sendSuccess(() -> Component.translatableWithFallback(KEY + operation, fallback, applied), false);
			return applied;
		}));
	}

	private static ChunkPos chunk(CommandSourceStack source) {
		return new ChunkPos(BlockPos.containing(source.getPosition()));
	}

	@FunctionalInterface
	private interface AuraChange {
		int apply(ServerLevel level, ChunkPos chunk, int amount);
	}
}
