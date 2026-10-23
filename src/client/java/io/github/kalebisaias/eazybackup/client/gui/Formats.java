package io.github.kalebisaias.eazybackup.client.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;

public final class Formats {
	private static final long MINUTE = 60_000L;
	private static final long HOUR = 60L * MINUTE;
	private static final long DAY = 24L * HOUR;

	private Formats() {
	}

	/**
	 * Formats a timestamp like the "last played" date of the world list.
	 */
	public static String date(long epochMillis) {
		return WorldSelectionList.DATE_FORMAT.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()));
	}

	public static Component relative(long epochMillis) {
		long elapsed = System.currentTimeMillis() - epochMillis;
		if (elapsed < MINUTE) {
			return Component.translatable("eazybackup.time.just_now");
		} else if (elapsed < HOUR) {
			return Component.translatable("eazybackup.time.minutes_ago", elapsed / MINUTE);
		} else if (elapsed < DAY) {
			return Component.translatable("eazybackup.time.hours_ago", elapsed / HOUR);
		} else {
			return Component.translatable("eazybackup.time.days_ago", elapsed / DAY);
		}
	}

	public static Component countdown(long millis) {
		long minutes = (millis + MINUTE - 1L) / MINUTE;
		return minutes <= 1L ? Component.translatable("eazybackup.time.under_a_minute") : Component.translatable("eazybackup.time.minutes", minutes);
	}
}
