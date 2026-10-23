package io.github.kalebisaias.eazybackup.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.kalebisaias.eazybackup.EazyBackup;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Translatable components with the English text as fallback, so players without the mod installed
 * (for example on a dedicated server) still see readable messages.
 */
public final class Texts {
	private static final Map<String, String> FALLBACKS = loadFallbacks();

	private Texts() {
	}

	public static MutableComponent translatable(String key, Object... args) {
		return Component.translatableWithFallback(key, FALLBACKS.get(key), args);
	}

	public static String formatSize(long bytes) {
		if (bytes < 1024L) {
			return bytes + " B";
		}

		String[] units = {"KB", "MB", "GB", "TB"};
		double value = bytes;
		int unit = -1;
		do {
			value /= 1024.0;
			unit++;
		} while (value >= 1024.0 && unit < units.length - 1);

		return String.format(Locale.ROOT, value >= 100.0 ? "%.0f %s" : "%.1f %s", value, units[unit]);
	}

	public static String formatDuration(long millis) {
		long seconds = Math.max(0L, millis / 1000L);
		if (seconds < 60L) {
			return seconds + "s";
		}

		long minutes = seconds / 60L;
		if (minutes < 60L) {
			return minutes + "m " + seconds % 60L + "s";
		}

		return minutes / 60L + "h " + minutes % 60L + "m";
	}

	private static Map<String, String> loadFallbacks() {
		Map<String, String> fallbacks = new HashMap<>();
		try (InputStream input = Texts.class.getResourceAsStream("/assets/" + EazyBackup.MOD_ID + "/lang/en_us.json")) {
			if (input != null) {
				JsonObject json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
				json.entrySet().forEach(entry -> fallbacks.put(entry.getKey(), entry.getValue().getAsString()));
			}
		} catch (IOException | RuntimeException e) {
			EazyBackup.LOGGER.warn("Failed to load fallback translations", e);
		}

		return fallbacks;
	}
}
