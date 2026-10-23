package io.github.kalebisaias.eazybackup.client;

import io.github.kalebisaias.eazybackup.EazyBackup;
import io.github.kalebisaias.eazybackup.backup.BackupException;
import io.github.kalebisaias.eazybackup.backup.BackupInfo;
import io.github.kalebisaias.eazybackup.backup.BackupManager;
import io.github.kalebisaias.eazybackup.backup.BackupTask;
import io.github.kalebisaias.eazybackup.backup.BackupType;
import io.github.kalebisaias.eazybackup.client.gui.BackupProgressScreen;
import io.github.kalebisaias.eazybackup.client.gui.Formats;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FileUtil;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jspecify.annotations.Nullable;

/**
 * Client side entry points for creating and restoring backups.
 */
public final class ClientBackups {
	private ClientBackups() {
	}

	/**
	 * Returns the integrated server if it is running the given world.
	 */
	public static @Nullable IntegratedServer serverRunning(Minecraft minecraft, String worldId) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server != null && server.isRunning() && BackupManager.worldId(server).equals(worldId)) {
			return server;
		}

		return null;
	}

	public static void createBackupOfCurrentWorld(Minecraft minecraft) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server == null) {
			SystemToast.addOrUpdate(
				minecraft.gui.toastManager(),
				ToastNotifier.TOAST_ID,
				Component.translatable("eazybackup.toast.unavailable").withStyle(ChatFormatting.RED),
				Component.translatable("eazybackup.toast.unavailable.details")
			);
			return;
		}

		BackupManager.createLiveBackup(server, BackupType.MANUAL, null);
	}

	/**
	 * Backs up a world, whether it is currently open or not.
	 */
	public static CompletableFuture<BackupInfo> createBackup(Minecraft minecraft, String worldId, String worldName) {
		IntegratedServer server = serverRunning(minecraft, worldId);
		if (server != null) {
			return BackupManager.createLiveBackup(server, BackupType.MANUAL, null);
		}

		Path worldDirectory = minecraft.getLevelSource().getLevelPath(worldId);
		return BackupManager.createFolderBackup(worldDirectory, worldId, worldName, BackupType.MANUAL, null);
	}

	public static boolean worldExists(Minecraft minecraft, String worldId) {
		return Files.isDirectory(minecraft.getLevelSource().getLevelPath(worldId));
	}

	/**
	 * Restores a backup and shows its progress, then returns to {@code returnTo}.
	 *
	 * @param asCopy restores into a new world instead of replacing the original one
	 */
	public static void restore(Minecraft minecraft, BackupInfo backup, boolean asCopy, Screen returnTo) {
		LevelStorageSource levelSource = minecraft.getLevelSource();
		Path saves = levelSource.getBaseDir();
		String folder;
		String newName = null;
		try {
			if (asCopy) {
				folder = FileUtil.findAvailableName(saves, backup.worldId, "");
				newName = I18n.get("eazybackup.restore.copy_name", backup.worldName, Formats.date(backup.createdAt));
			} else {
				folder = backup.worldId;
				if (worldExists(minecraft, folder)) {
					// Fails if the world is open in this or another game instance.
					levelSource.createAccess(folder).close();
				}
			}
		} catch (IOException e) {
			EazyBackup.LOGGER.error("Cannot restore backup {}", backup.id, e);
			minecraft.gui.setScreen(new AlertScreen(
				() -> minecraft.gui.setScreen(returnTo),
				Component.translatable("eazybackup.restore.failed"),
				Component.translatable("eazybackup.error.world_in_use", backup.worldName)
			));
			return;
		}

		CompletableFuture<Path> restore = BackupManager.restore(backup, saves, folder);
		BackupTask task = BackupManager.activeTask(backup.worldId).orElse(null);
		String restoredName = newName;
		minecraft.gui.setScreen(new BackupProgressScreen<Path>(Component.translatable("eazybackup.restore.title"), task, restore, (Path path, Throwable error) -> {
			if (error != null) {
				minecraft.gui.setScreen(new AlertScreen(
					() -> minecraft.gui.setScreen(returnTo),
					Component.translatable("eazybackup.restore.failed"),
					BackupException.describe(error)
				));
				return;
			}

			if (restoredName != null) {
				try (LevelStorageSource.LevelStorageAccess access = levelSource.createAccess(path.getFileName().toString())) {
					access.renameLevel(restoredName);
				} catch (IOException e) {
					EazyBackup.LOGGER.warn("Failed to rename restored world {}", path, e);
				}
			}

			SystemToast.addOrUpdate(
				minecraft.gui.toastManager(),
				ToastNotifier.TOAST_ID,
				Component.translatable("eazybackup.toast.restored"),
				Component.literal(restoredName != null ? restoredName : backup.worldName)
			);
			minecraft.gui.setScreen(returnTo);
		}));
	}
}
