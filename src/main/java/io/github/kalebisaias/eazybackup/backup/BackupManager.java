package io.github.kalebisaias.eazybackup.backup;

import io.github.kalebisaias.eazybackup.EazyBackup;
import io.github.kalebisaias.eazybackup.config.EazyBackupConfig;
import io.github.kalebisaias.eazybackup.util.Texts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Creates and restores backups. Archives are written by a single worker thread, so only one
 * operation touches the disk at a time and the game thread never blocks on compression.
 */
public final class BackupManager {
	private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
	/**
	 * The worker thread is not a daemon so a backup started while the game closes can still finish.
	 * It stops by itself after being idle for a few seconds.
	 */
	private static final ExecutorService WORKER = new ThreadPoolExecutor(0, 1, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
		Thread thread = new Thread(runnable, "Eazy Backup Worker #" + THREAD_COUNTER.incrementAndGet());
		thread.setDaemon(false);
		thread.setPriority(Thread.NORM_PRIORITY - 1);
		return thread;
	});
	private static final Map<String, BackupTask> ACTIVE = new ConcurrentHashMap<>();

	private BackupManager() {
	}

	public static String worldId(MinecraftServer server) {
		return worldDirectory(server).getFileName().toString();
	}

	public static Path worldDirectory(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
	}

	public static Optional<BackupTask> activeTask(String worldId) {
		return Optional.ofNullable(ACTIVE.get(worldId));
	}

	public static boolean isBusy(String worldId) {
		return ACTIVE.containsKey(worldId);
	}

	/**
	 * Backs up the world of a running server. The world is saved and automatic saving is paused while
	 * the archive is written, then restored to its previous state.
	 */
	public static CompletableFuture<BackupInfo> createLiveBackup(MinecraftServer server, BackupType type, @Nullable String label) {
		String worldId = worldId(server);
		String worldName = server.getWorldData().getLevelName();
		BackupTask task = new BackupTask(BackupTask.Kind.BACKUP, worldId, worldName, type, server);
		CompletableFuture<BackupInfo> result = new CompletableFuture<>();
		if (!register(task, result)) {
			return result;
		}

		BackupEvents.started(task);
		server.execute(() -> {
			if (!task.start()) {
				return;
			}

			Map<ServerLevel, Boolean> previousNoSave = new IdentityHashMap<>();
			try {
				task.phase(BackupTask.Phase.SAVING);
				server.saveEverything(true, true, true);
				for (ServerLevel level : server.getAllLevels()) {
					previousNoSave.put(level, level.noSave);
					level.noSave = true;
				}
			} catch (Throwable e) {
				restoreAutoSave(previousNoSave);
				complete(task, result, null, new BackupException(Texts.translatable("eazybackup.error.save_failed"), e));
				return;
			}

			Path source = worldDirectory(server);
			WORKER.execute(() -> {
				BackupInfo info = null;
				Throwable failure = null;
				try {
					info = archive(task, BackupTask.Phase.COMPRESSING, source, worldId, worldName, type, label);
				} catch (Throwable e) {
					failure = e;
				}

				// Re-enable saving on the server thread; if the server already stopped this is a no-op.
				server.execute(() -> restoreAutoSave(previousNoSave));
				complete(task, result, info, failure);
			});
		});
		return result;
	}

	/**
	 * Backs up a world folder that is not loaded, for example right after the world was closed.
	 */
	public static CompletableFuture<BackupInfo> createFolderBackup(Path worldDirectory, String worldId, String worldName, BackupType type, @Nullable String label) {
		BackupTask task = new BackupTask(BackupTask.Kind.BACKUP, worldId, worldName, type, null);
		CompletableFuture<BackupInfo> result = new CompletableFuture<>();
		if (!register(task, result)) {
			return result;
		}

		BackupEvents.started(task);
		WORKER.execute(() -> {
			if (!task.start()) {
				return;
			}

			BackupInfo info = null;
			Throwable failure = null;
			try {
				info = archive(task, BackupTask.Phase.COMPRESSING, worldDirectory, worldId, worldName, type, label);
			} catch (Throwable e) {
				failure = e;
			}

			complete(task, result, info, failure);
		});
		return result;
	}

	/**
	 * Restores a backup into the saves folder. The world must not be loaded.
	 *
	 * @param savesDirectory the folder containing all worlds
	 * @param targetFolder the world folder to restore into. When it is the original world folder,
	 *                     the current world is replaced (after a safety backup, if enabled)
	 * @return the folder the backup was restored into
	 */
	public static CompletableFuture<Path> restore(BackupInfo backup, Path savesDirectory, String targetFolder) {
		BackupTask task = new BackupTask(BackupTask.Kind.RESTORE, backup.worldId, backup.worldName, null, null);
		CompletableFuture<Path> result = new CompletableFuture<>();
		if (!register(task, result)) {
			return result;
		}

		WORKER.execute(() -> {
			if (!task.start()) {
				return;
			}

			try {
				Path restored = doRestore(task, backup, savesDirectory, targetFolder);
				ACTIVE.remove(task.worldId(), task);
				task.finish();
				result.complete(restored);
			} catch (Throwable e) {
				EazyBackup.LOGGER.error("Failed to restore backup {}", backup.id, e);
				ACTIVE.remove(task.worldId(), task);
				task.finish();
				result.completeExceptionally(e);
			}
		});
		return result;
	}

	/**
	 * Blocks until the current operation on a world is done. Used to keep the game from opening or
	 * closing a world while its files are being read or replaced.
	 */
	public static void awaitWorld(String worldId) {
		BackupTask task = ACTIVE.get(worldId);
		if (task == null) {
			return;
		}

		if (task.cancelIfWaiting()) {
			EazyBackup.LOGGER.info("Cancelled queued backup of world {}", worldId);
			ACTIVE.remove(worldId, task);
			task.finish();
			return;
		}

		EazyBackup.LOGGER.info("Waiting for the backup of world {} to finish", worldId);
		task.done().join();
	}

	/**
	 * Waits for every running operation, used when the game is closing.
	 */
	public static void awaitAll(long timeoutSeconds) {
		List<CompletableFuture<Void>> pending = new ArrayList<>();
		for (BackupTask task : ACTIVE.values()) {
			pending.add(task.done());
		}

		if (pending.isEmpty()) {
			return;
		}

		EazyBackup.LOGGER.info("Waiting for {} backup operation(s) to finish", pending.size());
		try {
			CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(timeoutSeconds, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			EazyBackup.LOGGER.warn("Backups did not finish in {} seconds", timeoutSeconds);
		} catch (Exception e) {
			EazyBackup.LOGGER.warn("Failed waiting for backups", e);
		}
	}

	private static boolean register(BackupTask task, CompletableFuture<?> result) {
		if (ACTIVE.putIfAbsent(task.worldId(), task) != null) {
			BackupException error = new BackupException(Texts.translatable("eazybackup.error.busy", task.worldName()));
			if (task.kind() == BackupTask.Kind.BACKUP) {
				BackupEvents.failed(task, error);
			}

			result.completeExceptionally(error);
			return false;
		}

		return true;
	}

	private static void complete(BackupTask task, CompletableFuture<BackupInfo> result, @Nullable BackupInfo info, @Nullable Throwable failure) {
		ACTIVE.remove(task.worldId(), task);
		task.finish();
		if (info != null) {
			EazyBackup.LOGGER.info("Created {} backup of world {} ({})", task.type() == null ? "" : task.type().serializedName(), task.worldId(), info.archive().getFileName());
			BackupEvents.completed(task, info);
			result.complete(info);
		} else {
			Throwable error = failure == null ? new IllegalStateException("Backup finished without a result") : failure;
			EazyBackup.LOGGER.error("Failed to back up world {}", task.worldId(), error);
			BackupEvents.failed(task, error);
			result.completeExceptionally(error);
		}
	}

	private static void restoreAutoSave(Map<ServerLevel, Boolean> previousNoSave) {
		previousNoSave.forEach((level, noSave) -> level.noSave = noSave);
	}

	private static BackupInfo archive(BackupTask task, BackupTask.Phase phase, Path source, String worldId, String worldName, BackupType type, @Nullable String label) throws IOException {
		if (!Files.isDirectory(source)) {
			throw new BackupException(Texts.translatable("eazybackup.error.world_missing", worldName));
		}

		EazyBackupConfig config = EazyBackupConfig.get();
		long startedAt = System.currentTimeMillis();
		Path archive = BackupStorage.newArchivePath(worldId, type, startedAt);
		task.phase(phase);
		ZipArchiver.zip(source, worldId, archive, config.compression.deflaterLevel(), task::progress);

		BackupInfo info = new BackupInfo();
		info.worldId = worldId;
		info.worldName = worldName;
		info.type = type.serializedName();
		info.createdAt = startedAt;
		info.durationMillis = System.currentTimeMillis() - startedAt;
		info.label = label == null || label.isBlank() ? null : label.strip();
		info.gameVersion = SharedConstants.getCurrentVersion().name();
		info.archive = archive;
		info.sizeBytes = Files.size(archive);
		BackupStorage.save(info);

		BackupStorage.prune(worldId, config.maxBackupsPerWorld);
		return info;
	}

	private static Path doRestore(BackupTask task, BackupInfo backup, Path savesDirectory, String targetFolder) throws IOException {
		Path target = savesDirectory.resolve(targetFolder).toAbsolutePath().normalize();
		if (!target.getParent().equals(savesDirectory.toAbsolutePath().normalize())) {
			throw new IOException("Invalid world folder " + targetFolder);
		}

		boolean replacing = Files.exists(target);
		if (replacing && EazyBackupConfig.get().safetyBackupBeforeRestore) {
			BackupInfo safety = archive(task, BackupTask.Phase.SAFETY_BACKUP, target, targetFolder, backup.worldName, BackupType.PRE_RESTORE, null);
			EazyBackup.LOGGER.info("Created safety backup {} before restoring", safety.archive().getFileName());
		}

		Path staging = savesDirectory.resolve(targetFolder + ".eazybackup-restore");
		ZipArchiver.deleteRecursively(staging);
		task.phase(BackupTask.Phase.EXTRACTING);
		try {
			ZipArchiver.unzip(backup.archive(), staging, task::progress);
		} catch (IOException | RuntimeException e) {
			ZipArchiver.deleteRecursively(staging);
			throw e;
		}

		task.phase(BackupTask.Phase.FINISHING);
		if (replacing) {
			Path old = savesDirectory.resolve(targetFolder + ".eazybackup-old");
			ZipArchiver.deleteRecursively(old);
			Files.move(target, old);
			try {
				Files.move(staging, target);
			} catch (IOException e) {
				Files.move(old, target);
				throw e;
			}

			ZipArchiver.deleteRecursively(old);
		} else {
			Files.move(staging, target);
		}

		EazyBackup.LOGGER.info("Restored backup {} into {}", backup.id, target);
		return target;
	}
}
