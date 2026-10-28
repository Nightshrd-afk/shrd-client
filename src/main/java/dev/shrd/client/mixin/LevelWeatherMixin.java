package dev.shrd.client.mixin;

import dev.shrd.client.core.ClientState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reported rain and thunder levels are clamped to zero while No Weather is on. This only
 * changes what your client draws — the server's weather, and everyone else's screen, is
 * untouched.
 */
@Mixin(Level.class)
public class LevelWeatherMixin {

	@Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
	private void shrdclient$hideRain(float delta, CallbackInfoReturnable<Float> cir) {
		if (ClientState.noWeather()) {
			cir.setReturnValue(0.0f);
		}
	}

	@Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
	private void shrdclient$hideThunder(float delta, CallbackInfoReturnable<Float> cir) {
		if (ClientState.noWeather()) {
			cir.setReturnValue(0.0f);
		}
	}
}
