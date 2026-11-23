package dev.thaumiclegacy.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(ItemEntity.class)
public interface ItemEntityThrowerAccessor {
	@Accessor("thrower")
	UUID thaumiclegacy$getThrower();
}
