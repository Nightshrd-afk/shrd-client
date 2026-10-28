package dev.shrd.client.mixin;

import dev.shrd.client.core.ClientState;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raises the floor of the block-light contribution while Fullbright is on, so unlit
 * areas render at a readable brightness without touching the gamma option.
 */
@Mixin(Lightmap.class)
public class LightmapFullbrightMixin {

	@Inject(method = "getBrightness", at = @At("RETURN"), cancellable = true)
	private static void shrdclient$brightenDarkness(DimensionType dimensionType, int lightLevel, CallbackInfoReturnable<Float> cir) {
		if (!ClientState.fullbright()) {
			return;
		}

		float strength = ClientState.fullbrightStrength();
		float vanilla = cir.getReturnValueF();
		float raised = Math.max(vanilla, 0.28f + (strength - 0.25f) * 0.72f);

		cir.setReturnValue(raised);
	}
}
