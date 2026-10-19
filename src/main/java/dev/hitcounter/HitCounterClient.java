package dev.hitcounter;

import com.google.gson.Gson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.nio.file.Files;
import java.nio.file.Path;

public class HitCounterClient implements ClientModInitializer {
	private static final Gson GSON = new Gson();

	/** Saved position (in GUI-scaled pixels). */
	public static class Config {
		public int x = 6;
		public int y = 6;
	}

	private static Config config = new Config();

	private static int hits = 0;

	private static boolean dragging = false;
	private static boolean wasDown = false;
	private static double offX, offY;

	@Override
	public void onInitializeClient() {
		loadConfig();

		// Called on every left-click attack, before the game handles it.
		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			HitResult hr = client.hitResult;
			if (hr != null && hr.getType() == HitResult.Type.ENTITY) {
				if (((EntityHitResult) hr).getEntity() instanceof LivingEntity) {
					hits++;
				}
			} else {
				hits = 0; // miss -> reset
			}
			return false; // never cancel the attack
		});

		HudElementRegistry.attachElementAfter(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("hitcounter", "counter"),
				HitCounterClient::render
		);
	}

	private static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) return;

		Font font = mc.font;
		String text = "Hits: " + hits;
		int pad = 3;
		int w = font.width(text) + pad * 2;
		int h = font.lineHeight + pad * 2;
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();

		boolean chat = mc.screen instanceof ChatScreen;
		if (chat) {
			handleDrag(mc, w, h, sw, sh);
		} else {
			if (dragging) saveConfig();
			dragging = false;
			wasDown = false;
		}

		// keep it inside the screen
		config.x = Math.max(0, Math.min(config.x, sw - w));
		config.y = Math.max(0, Math.min(config.y, sh - h));

		int x = config.x, y = config.y;
		g.fill(x, y, x + w, y + h, 0x90000000);
		if (chat) { // outline so you can see it is movable
			int c = dragging ? 0xFFFF5555 : 0xFFAAAAAA;
			g.fill(x, y, x + w, y + 1, c);
			g.fill(x, y + h - 1, x + w, y + h, c);
			g.fill(x, y, x + 1, y + h, c);
			g.fill(x + w - 1, y, x + w, y + h, c);
		}
		g.drawString(font, text, x + pad, y + pad, 0xFFFFFFFF, true);
	}

	private static void handleDrag(Minecraft mc, int w, int h, int sw, int sh) {
		double mx = mc.mouseHandler.xpos() * sw / (double) mc.getWindow().getScreenWidth();
		double my = mc.mouseHandler.ypos() * sh / (double) mc.getWindow().getScreenHeight();
		boolean down = mc.mouseHandler.isRightPressed();

		if (down && !wasDown
				&& mx >= config.x && mx <= config.x + w
				&& my >= config.y && my <= config.y + h) {
			dragging = true;
			offX = mx - config.x;
			offY = my - config.y;
		}
		if (!down) {
			if (dragging) saveConfig();
			dragging = false;
		}
		if (dragging) {
			config.x = (int) (mx - offX);
			config.y = (int) (my - offY);
		}
		wasDown = down;
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("hitcounter.json");
	}

	private static void loadConfig() {
		try {
			Path p = configPath();
			if (Files.exists(p)) {
				Config c = GSON.fromJson(Files.readString(p), Config.class);
				if (c != null) config = c;
			}
		} catch (Exception ignored) {
		}
	}

	private static void saveConfig() {
		try {
			Files.writeString(configPath(), GSON.toJson(config));
		} catch (Exception ignored) {
		}
	}
}
