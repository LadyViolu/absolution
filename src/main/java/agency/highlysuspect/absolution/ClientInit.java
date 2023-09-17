package agency.highlysuspect.absolution;

import org.lwjgl.glfw.GLFW;

import agency.highlysuspect.absolution.mixin.GameRendererAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class ClientInit implements ClientModInitializer {
	//put these in a config file
	public static boolean ENABLE_CIRCLE = false;
	public static double CIRCLE_RADIUS = 70d;
	public static double CIRCLE_SCALE_FACTOR = 0.5;
	public static boolean CIRCLE_USES_SCALED_PIXELS = true;
	public static double SENSITIVITY = 0.06d;

	public static KeyBinding mouseDragBinding;
	
	@Override
	public void onInitializeClient() {
		mouseDragBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.absolution.mousedrag", // The translation key of the keybinding's name
			InputUtil.Type.MOUSE, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
			GLFW.GLFW_MOUSE_BUTTON_3, // The keycode of the key
			"category.absolution.category" // The translation key of the keybinding's category.
		));
	}
	
	public static Vec3d fudgeLookVec(Vec3d forward) {
		//TODO This is the wrong approach.
		// It works ok for small angles, but it basically maps X motion directly to yaw and Y motion directly to pitch.
		// That's not true, you have to change both pitch *and* yaw to point the camera at the thing on the left-center of your screen if you're not looking at the horizon.
		// ooffunny
		// Also, it is based on the false assumption that i could add directly to the X and Y components of the look vector.
		// That is not how look vectors work at all lol. I wrote this without looking at Entity#getRotationVec (which does some trig)
		
		MinecraftClient client = MinecraftClient.getInstance();
		Window window = client.getWindow();
		Mouse mouse = client.mouse;
		
		//Mouse X and Y in the range -1, 1
		double xRemapped = (mouse.getX() / window.getWidth()) * 2 - 1;
		double yRemapped = (mouse.getY() / window.getHeight()) * 2 - 1;
		
		//How do I explain this
		//Remap so the bottom end of the range is the farthest negative you can look, and the top end of the range is the farthest positive you can look
		//so like if you have 90 degree fov, the left end of the range is -45 degrees and the right is 45 degrees
		//also same for y (but i need to calculate vertical fov)
		double yFov = getCameraVerticalFov();
		double xFov = yFov * ((double) window.getFramebufferWidth() / window.getFramebufferHeight());
		xRemapped *= xFov;
		yRemapped *= yFov;

		Vec3d side = forward.crossProduct(new Vec3d(0, 1, 0));
		Vec3d up = side.crossProduct(forward);

		//Uhh fuck idk, just add it? Maybe it'll even work?
		return forward.add(xRemapped, yRemapped, 0);
	}
	
	public static double getCameraVerticalFov() {
		MinecraftClient client = MinecraftClient.getInstance();
		float tickDelta = client.getTickDelta();
		GameRenderer renderer = client.gameRenderer;
		Camera camera = renderer.getCamera();
		
		return ((GameRendererAccessor) renderer).callGetFov(camera, tickDelta, true);
	}
	
	//Based on copy of Entity#raycast.
	//Slapped in the middle of this class instead of a mixin so i can hotreload it
	public static HitResult raycastFudged(Entity receiver, double maxDistance, float tickDelta, boolean includeFluids) {
		//Todo this method is fucked up lmao
		Vec3d cameraPos = receiver.getCameraPosVec(tickDelta);
		//Vec3d look = receiver.getRotationVec(tickDelta);
		
		///uhh idk based on internals of getrotationvec
		
		Vec3d funny = fudgeLookVec(new Vec3d(0, 0, 0)); //sorry
		//JUST FLIP THE SIGNS AROUND UNTIL SHIT WORKS
		double pitchRad = Math.toRadians(receiver.getPitch(tickDelta) + funny.y);
		double yawRad = Math.toRadians(-receiver.getYaw(tickDelta) - funny.x);
		double yawCos = Math.cos(yawRad);
		double yawSin = Math.sin(yawRad);
		double pitchCos = Math.cos(pitchRad);
		double pitchSin = Math.sin(pitchRad);
		Vec3d look = new Vec3d(yawSin * pitchCos, -pitchSin, yawCos * pitchCos);
		
		Vec3d posEnd = cameraPos.add(look.multiply(maxDistance));
		return receiver.getEntityWorld().raycast(new RaycastContext(cameraPos, posEnd, RaycastContext.ShapeType.OUTLINE, includeFluids ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE, receiver));
	}
}
