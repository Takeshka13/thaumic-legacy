package dev.thaumiclegacy.client;

import dev.thaumiclegacy.logistics.EssentiaPhial;
import dev.thaumiclegacy.revealing.AspectTooltipPayload;
import dev.thaumiclegacy.revealing.RevealingPayload;
import dev.thaumiclegacy.wand.WandItem;
import dev.thaumiclegacy.wand.WandVis;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

final class AspectTooltips {
	private static final Map<String, RevealingPayload.AspectAmount> KNOWN = new HashMap<>();

	private AspectTooltips() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(AspectTooltipPayload.TYPE, (payload, context) -> {
			KNOWN.clear();
			for (var aspect : payload.known()) {
				KNOWN.put(aspect.id(), aspect);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> KNOWN.clear());
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (stack.getItem() instanceof EssentiaPhial) {
				var contents = EssentiaPhial.contents(stack);
				lines.add(contents.isEmpty() ? Component.translatable("tooltip.thaumiclegacy.essentia_phial.empty")
					: Component.translatable("tooltip.thaumiclegacy.essentia_phial.filled",
						name(contents.aspect()), contents.amount(), EssentiaPhial.CAPACITY));
			} else if (stack.getItem() instanceof WandItem) {
				var vis = WandVis.get(stack);
				for (var primal : WandVis.Primal.values()) {
					lines.add(Component.translatable("tooltip.thaumiclegacy.wand.primal",
						name(primal.aspectId()), vis.amount(primal), WandVis.CAPACITY));
				}
			}
		});
	}

	private static Component name(String id) {
		var aspect = KNOWN.get(id);
		return aspect == null ? Component.translatable("screen.thaumiclegacy.journal.aspect.unknown") : aspect.name();
	}
}
