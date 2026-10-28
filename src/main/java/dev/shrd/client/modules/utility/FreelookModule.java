package dev.shrd.client.modules.utility;

import dev.shrd.client.core.Category;
import dev.shrd.client.core.Module;
import dev.shrd.client.core.setting.BooleanSetting;
import dev.shrd.client.util.PlayerUtils;
import net.minecraft.client.CameraType;

/**
 * Hold the bound key to look around freely, then snap back to where you were aiming.
 * <p>
 * Shrd Client does this by remembering your view, optionally swinging the camera into
 * third person so you can see around your character, and restoring both the moment the
 * key is released. That keeps it a pure client-side convenience: it changes nothing
 * about how your character moves or what the server sees.
 */
public class FreelookModule extends Module {

	private final BooleanSetting thirdPerson = add(new BooleanSetting("Third Person", "Use the shoulder camera while looking around.", true));

	private float savedYaw;
	private float savedPitch;
	private boolean savedFieldOfViewActive;
	private CameraType savedCameraType = CameraType.FIRST_PERSON;

	public FreelookModule() {
		super("Freelook", "Hold to look around, release to snap back to your original aim.", Category.UTILITY);
	}

	@Override
	public boolean isHold() {
		return true;
	}

	@Override
	protected void onEnable() {
		if (!PlayerUtils.inGame()) {
			return;
		}

		savedYaw = PlayerUtils.player().getYRot();
		savedPitch = PlayerUtils.player().getXRot();
		savedCameraType = PlayerUtils.mc().options.getCameraType();
		savedFieldOfViewActive = true;

		if (thirdPerson.get() && savedCameraType == CameraType.FIRST_PERSON) {
			PlayerUtils.mc().options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
	}

	@Override
	protected void onDisable() {
		if (!savedFieldOfViewActive || !PlayerUtils.inGame()) {
			return;
		}

		var player = PlayerUtils.player();
		player.setYRot(savedYaw);
		player.setXRot(savedPitch);
		// Matching interpolation fields make the camera snap instead of sliding back.
		player.yRotO = savedYaw;
		player.xRotO = savedPitch;

		if (thirdPerson.get()) {
			PlayerUtils.mc().options.setCameraType(savedCameraType);
		}

		savedFieldOfViewActive = false;
	}
}
