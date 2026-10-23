package io.github.kalebisaias.eazybackup.backup;

import io.github.kalebisaias.eazybackup.EazyBackup;
import io.github.kalebisaias.eazybackup.config.EazyBackupConfig;
import java.nio.file.Path;
import java.util.OptionalLong;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * Triggers automatic backups: every few minutes of play time, and when a world is closed.
 */
public final class BackupScheduler {
	private static final int TICKS_PER_MINUTE = 20 * 60;

	private static @Nullable MinecraftServer currentServer;
	private static int ticksSinceBackup;
	private static boolean playersSeen;
	private static @Nullable ClosingWorld closingWorld;

	private BackupScheduler() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(BackupScheduler::onServerStarting);
		ServerTickEvents.END_SERVER_TICK.register(BackupScheduler::onServerTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(BackupScheduler::onServerStopping);
		ServerLifecycleEvents.SERVER_STOPPED.register(BackupScheduler::onServerStopped);
		BackupEvents.register(new BackupEvents.Listener() {
			@Override
			public void onCompleted(BackupTask task, BackupInfo backup) {
				// Any backup of the running world restarts the countdown.
				MinecraftServer server = currentServer;
				if (server != null && task.server() == server) {
					server.execute(BackupScheduler::resetCountdown);
				}
			}
		});
	}

	/**
	 * Milliseconds of play time until the next scheduled backup of the running world.
	 */
	public static OptionalLong millisUntilNextBackup(MinecraftServer server) {
		EazyBackupConfig config = EazyBackupConfig.get();
		if (server != currentServer || !config.intervalBackups) {
			return OptionalLong.empty();
		}

		long remainingTicks = Math.max(0L, (long) config.intervalMinutes * TICKS_PER_MINUTE - ticksSinceBackup);
		return OptionalLong.of(remainingTicks * 50L);
	}

	private static void onServerStarting(MinecraftServer server) {
		// Never open a world while its previous on-exit backup or a restore is still reading its files.
		BackupManager.awaitWorld(BackupManager.worldId(server));
		currentServer = server;
		resetCountdown();
	}

	private static void onServerTick(MinecraftServer server) {
		EazyBackupConfig config = EazyBackupConfig.get();
		if (!config.intervalBackups || server != currentServer) {
			return;
		}

		if (server.getPlayerCount() > 0) {
			playersSeen = true;
		}

		if (++ticksSinceBackup < config.intervalMinutes * TICKS_PER_MINUTE) {
			return;
		}

		resetCountdown();
		if (config.skipWhenIdle && server.isDedicatedServer() && !playersSeen) {
			EazyBackup.LOGGER.debug("Skipping scheduled backup, nobody played since the last one");
			return;
		}

		if (!BackupManager.isBusy(BackupManager.worldId(server))) {
			BackupManager.createLiveBackup(server, BackupType.SCHEDULED, null);
		}

		playersSeen = server.getPlayerCount() > 0;
	}

	private static void onServerStopping(MinecraftServer server) {
		String worldId = BackupManager.worldId(server);
		// The server saves and re-enables writes while stopping, so a live backup must finish first.
		BackupManager.awaitWorld(worldId);
		closingWorld = new ClosingWorld(BackupManager.worldDirectory(server), worldId, server.getWorldData().getLevelName());
	}

	private static void onServerStopped(MinecraftServer server) {
		ClosingWorld world = closingWorld;
		closingWorld = null;
		currentServer = null;
		if (world == null || !EazyBackupConfig.get().backupOnExit) {
			return;
		}

		var backup = BackupManager.createFolderBackup(world.directory(), world.worldId(), world.worldName(), BackupType.ON_EXIT, null);
		if (server.isDedicatedServer()) {
			// Dedicated servers exit right after this, finish the backup before that happens.
			backup.exceptionally(error -> null).join();
		}
	}

	private static void resetCountdown() {
		ticksSinceBackup = 0;
		playersSeen = false;
	}

	private record ClosingWorld(Path directory, String worldId, String worldName) {
	}
}
