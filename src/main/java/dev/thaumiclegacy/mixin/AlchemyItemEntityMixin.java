package dev.thaumiclegacy.mixin;

import dev.thaumiclegacy.alchemy.Alchemy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(ItemEntity.class)
abstract class AlchemyItemEntityMixin {
	@Inject(method = "tryToMerge", at = @At("HEAD"), cancellable = true)
	private void thaumiclegacy$protectAlchemyDrops(ItemEntity other, CallbackInfo callback) {
		ItemEntity current = (ItemEntity) (Object) this;
		if (Alchemy.isIntakeOutput(current) || Alchemy.isIntakeOutput(other)) {
			callback.cancel();
			return;
		}
		if (current.level() instanceof ServerLevel server
				&& ItemStack.isSameItemSameComponents(current.getItem(), other.getItem())
				&& !Objects.equals(((ItemEntityThrowerAccessor) current).thaumiclegacy$getThrower(),
						((ItemEntityThrowerAccessor) other).thaumiclegacy$getThrower())
				&& Alchemy.isCatalyst(server, current.getItem())) {
			// Vanilla only checks pickup ownership, which is separate from the original thrower.
			callback.cancel();
		}
	}
}
