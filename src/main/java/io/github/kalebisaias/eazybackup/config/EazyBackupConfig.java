package io.github.kalebisaias.eazybackup.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.kalebisaias.eazybackup.EazyBackup;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class EazyBackupConfig {
	public static final int MIN_BACKUPS = 1;
	public static final int MAX_BACKUPS = 100;
	public static final int MIN_INTERVAL_MINUTES = 5;
	public static final int MAX_INTERVAL_MINUTES = 240;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static EazyBackupConfig instance = new EazyBackupConfig();

	/** How many unlocked backups are kept per world. Locked backups never count towards this limit. */
	public int maxBackupsPerWorld = 10;
	/** Creates a backup every {@link #intervalMinutes} of play time. */
	public boolean intervalBackups = true;
	public int intervalMinutes = 30;
	/** Skips scheduled backups on servers when nobody was online since the last one. */
	public boolean skipWhenIdle = true;
	/** Creates a backup every time a world is closed. */
	public boolean backupOnExit = true;
	/** Creates a backup of the current world before a backup is restored over it. */
	public boolean safetyBackupBeforeRestore = true;
	public CompressionLevel compression = CompressionLevel.BALANCED;
	public boolean showToasts = true;
	/** Notifies operators in chat about backups on dedicated servers. */
	public boolean notifyOperators = true;
	/** Custom directory for backups. Leave empty to use ".minecraft/backups/eazybackup". */
	public String backupDirectory = "";

	public static EazyBackupConfig get() {
		return instance;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(EazyBackup.MOD_ID + ".json");
	}

	public static void load() {
		Path path = path();
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				EazyBackupConfig loaded = GSON.fromJson(reader, EazyBackupConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (IOException | JsonParseException e) {
				EazyBackup.LOGGER.error("Failed to read config, using defaults", e);
			}
		}

		instance.validate();
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer writer = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			EazyBackup.LOGGER.error("Failed to save config", e);
		}
	}

	private void validate() {
		this.maxBackupsPerWorld = Math.clamp(this.maxBackupsPerWorld, MIN_BACKUPS, MAX_BACKUPS);
		this.intervalMinutes = Math.clamp(this.intervalMinutes, MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES);
		if (this.compression == null) {
			this.compression = CompressionLevel.BALANCED;
		}

		if (this.backupDirectory == null) {
			this.backupDirectory = "";
		}
	}
}
