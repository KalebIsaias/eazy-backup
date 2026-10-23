package io.github.kalebisaias.eazybackup.backup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.kalebisaias.eazybackup.EazyBackup;
import io.github.kalebisaias.eazybackup.config.EazyBackupConfig;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

/**
 * File layout:
 * <pre>
 * backups/eazybackup/
 *   &lt;world folder&gt;/
 *     2026-10-22_21-45-03_manual.zip
 *     2026-10-22_21-45-03_manual.json
 * </pre>
 */
public final class BackupStorage {
	public static final String ARCHIVE_EXTENSION = ".zip";
	public static final String METADATA_EXTENSION = ".json";
	private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private BackupStorage() {
	}

	public static Path root() {
		String custom = EazyBackupConfig.get().backupDirectory;
		if (custom != null && !custom.isBlank()) {
			return Path.of(custom).toAbsolutePath().normalize();
		}

		return FabricLoader.getInstance().getGameDir().resolve("backups").resolve(EazyBackup.MOD_ID).toAbsolutePath().normalize();
	}

	public static Path worldDirectory(String worldId) {
		return root().resolve(worldId);
	}

	/**
	 * Returns every backup of a world, newest first.
	 */
	public static List<BackupInfo> list(String worldId) {
		Path directory = worldDirectory(worldId);
		List<BackupInfo> backups = new ArrayList<>();
		if (!Files.isDirectory(directory)) {
			return backups;
		}

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + ARCHIVE_EXTENSION)) {
			for (Path archive : stream) {
				try {
					backups.add(read(worldId, archive));
				} catch (IOException e) {
					EazyBackup.LOGGER.warn("Failed to read backup {}", archive, e);
				}
			}
		} catch (IOException e) {
			EazyBackup.LOGGER.error("Failed to list backups of world {}", worldId, e);
		}

		backups.sort(Comparator.comparingLong((BackupInfo info) -> info.createdAt).reversed());
		return backups;
	}

	/**
	 * Returns a summary of every world folder that contains at least one backup.
	 */
	public static List<WorldBackups> listWorlds() {
		Path root = root();
		List<WorldBackups> worlds = new ArrayList<>();
		if (!Files.isDirectory(root)) {
			return worlds;
		}

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, Files::isDirectory)) {
			for (Path directory : stream) {
				String worldId = directory.getFileName().toString();
				List<BackupInfo> backups = list(worldId);
				if (!backups.isEmpty()) {
					worlds.add(WorldBackups.of(worldId, backups));
				}
			}
		} catch (IOException e) {
			EazyBackup.LOGGER.error("Failed to list backup folders", e);
		}

		return worlds;
	}

	public static Path newArchivePath(String worldId, BackupType type, long createdAt) throws IOException {
		Path directory = worldDirectory(worldId);
		Files.createDirectories(directory);
		String base = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(createdAt), ZoneId.systemDefault()).format(ID_FORMAT) + "_" + type.serializedName();
		Path candidate = directory.resolve(base + ARCHIVE_EXTENSION);
		int suffix = 2;
		while (Files.exists(candidate)) {
			candidate = directory.resolve(base + "_" + suffix++ + ARCHIVE_EXTENSION);
		}

		return candidate;
	}

	public static void save(BackupInfo info) {
		Path metadata = metadataPath(info.archive());
		Path temp = metadata.resolveSibling(metadata.getFileName() + ".tmp");
		try {
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(info, writer);
			}

			Files.move(temp, metadata, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to write backup metadata " + metadata, e);
		}
	}

	public static void delete(BackupInfo info) throws IOException {
		Files.deleteIfExists(info.archive());
		Files.deleteIfExists(metadataPath(info.archive()));
		Path directory = info.archive().getParent();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
			if (!stream.iterator().hasNext()) {
				Files.delete(directory);
			}
		}
	}

	/**
	 * Deletes the oldest unlocked backups of a world until at most {@code keep} unlocked backups remain.
	 *
	 * @return the deleted backups
	 */
	public static List<BackupInfo> prune(String worldId, int keep) {
		List<BackupInfo> unlocked = list(worldId).stream().filter(info -> !info.locked).toList();
		List<BackupInfo> deleted = new ArrayList<>();
		for (int i = keep; i < unlocked.size(); i++) {
			BackupInfo info = unlocked.get(i);
			try {
				delete(info);
				deleted.add(info);
			} catch (IOException e) {
				EazyBackup.LOGGER.error("Failed to delete old backup {}", info.archive(), e);
			}
		}

		if (!deleted.isEmpty()) {
			EazyBackup.LOGGER.info("Deleted {} old backup(s) of world {}", deleted.size(), worldId);
		}

		return deleted;
	}

	private static Path metadataPath(Path archive) {
		String name = archive.getFileName().toString();
		return archive.resolveSibling(name.substring(0, name.length() - ARCHIVE_EXTENSION.length()) + METADATA_EXTENSION);
	}

	private static BackupInfo read(String worldId, Path archive) throws IOException {
		Path metadata = metadataPath(archive);
		BackupInfo info = null;
		if (Files.isRegularFile(metadata)) {
			try (Reader reader = Files.newBufferedReader(metadata, StandardCharsets.UTF_8)) {
				info = GSON.fromJson(reader, BackupInfo.class);
			} catch (JsonParseException e) {
				EazyBackup.LOGGER.warn("Ignoring corrupted backup metadata {}", metadata, e);
			}
		}

		if (info == null) {
			// Archives copied into the folder by hand, or with lost metadata, are still listed.
			info = new BackupInfo();
			info.worldId = worldId;
			info.worldName = worldId;
			info.createdAt = Files.getLastModifiedTime(archive).toMillis();
		}

		String fileName = archive.getFileName().toString();
		info.id = fileName.substring(0, fileName.length() - ARCHIVE_EXTENSION.length());
		info.worldId = worldId;
		info.sizeBytes = Files.size(archive);
		info.archive = archive;
		return info;
	}

	public record WorldBackups(String worldId, String worldName, int count, long totalBytes, long latestBackup) {
		static WorldBackups of(String worldId, List<BackupInfo> backups) {
			long total = 0L;
			for (BackupInfo backup : backups) {
				total += backup.sizeBytes;
			}

			BackupInfo latest = backups.getFirst();
			String name = latest.worldName == null || latest.worldName.isBlank() ? worldId : latest.worldName;
			return new WorldBackups(worldId, name, backups.size(), total, latest.createdAt);
		}
	}
}
