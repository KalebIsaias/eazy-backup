package io.github.kalebisaias.eazybackup.gametest;

import io.github.kalebisaias.eazybackup.backup.BackupInfo;
import io.github.kalebisaias.eazybackup.backup.BackupManager;
import io.github.kalebisaias.eazybackup.backup.BackupStorage;
import io.github.kalebisaias.eazybackup.backup.BackupType;
import io.github.kalebisaias.eazybackup.client.ClientBackups;
import io.github.kalebisaias.eazybackup.client.gui.BackupListScreen;
import io.github.kalebisaias.eazybackup.client.gui.BackupWorldListScreen;
import io.github.kalebisaias.eazybackup.client.gui.EazyBackupConfigScreen;
import io.github.kalebisaias.eazybackup.client.gui.RestoreConfirmScreen;
import io.github.kalebisaias.eazybackup.config.EazyBackupConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

/**
 * Runs the real game: creates backups of a world, checks retention and locking, restores
 * backups and takes screenshots of every screen of the mod.
 */
@SuppressWarnings("UnstableApiUsage")
public class EazyBackupClientGameTest implements FabricClientGameTest {
	private static final int BACKUP_TIMEOUT = 20 * 60;

	@Override
	public void runTest(ClientGameTestContext context) {
		EazyBackupConfig config = EazyBackupConfig.get();
		config.maxBackupsPerWorld = 2;
		config.backupOnExit = true;

		String worldId;
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			worldId = singleplayer.getServer().computeOnServer(BackupManager::worldId);

			// Live backups of the running world.
			BackupInfo first = backup(context, singleplayer, BackupType.MANUAL, "First");
			assertTrue(Files.isRegularFile(first.archive()), "backup archive exists");
			assertEquals(1, BackupStorage.list(worldId).size(), "backups after the first one");

			first.locked = true;
			BackupStorage.save(first);
			for (int i = 0; i < 3; i++) {
				backup(context, singleplayer, BackupType.SCHEDULED, null);
			}

			List<BackupInfo> backups = BackupStorage.list(worldId);
			assertEquals(3, backups.size(), "2 unlocked backups are kept plus the locked one");
			assertTrue(backups.stream().anyMatch(info -> info.locked && "First".equals(info.label)), "locked backup survives retention");
			// Automatic saving is re-enabled on the next server tick after a backup.
			singleplayer.getServer().waitFor(server -> {
				for (var level : server.getAllLevels()) {
					if (level.noSave) {
						return false;
					}
				}

				return true;
			}, BACKUP_TIMEOUT);

			// The same command operators would use on a server.
			singleplayer.getServer().runCommand("eazybackup create From command");
			context.waitFor(minecraft -> !BackupManager.isBusy(worldId), BACKUP_TIMEOUT);

			context.setScreen(() -> new PauseScreen(true));
			context.waitTicks(5);
			context.takeScreenshot("eazybackup-pause-menu");

			context.setScreen(() -> BackupListScreen.forCurrentWorld(Minecraft.getInstance(), null));
			context.waitTicks(5);
			context.takeScreenshot("eazybackup-backups-in-game");

			context.setScreen(() -> new EazyBackupConfigScreen(new TitleScreen()));
			context.waitTicks(5);
			context.takeScreenshot("eazybackup-options");
			context.setScreen(() -> null);
			config.maxBackupsPerWorld = 10;
		}

		// Leaving the world creates an on-exit backup in the background.
		context.waitFor(minecraft -> !BackupManager.isBusy(worldId), BACKUP_TIMEOUT);
		List<BackupInfo> afterExit = BackupStorage.list(worldId);
		assertEquals(BackupType.ON_EXIT, afterExit.getFirst().backupType(), "newest backup is the on-exit one");

		context.setScreen(() -> new SelectWorldScreen(new TitleScreen()));
		context.waitTicks(10);
		context.takeScreenshot("eazybackup-select-world");

		context.setScreen(() -> new BackupWorldListScreen(new TitleScreen()));
		context.waitTicks(10);
		context.takeScreenshot("eazybackup-world-list");

		BackupListScreen listScreen = context.computeOnClient(minecraft -> new BackupListScreen(new TitleScreen(), worldId, afterExit.getFirst().worldName));
		context.setScreen(() -> listScreen);
		context.waitTicks(5);
		context.takeScreenshot("eazybackup-backups-title-screen");

		BackupInfo locked = afterExit.stream().filter(info -> info.locked).findFirst().orElseThrow();
		context.setScreen(() -> new RestoreConfirmScreen(listScreen, locked, true, asCopy -> {}));
		context.waitTicks(5);
		context.takeScreenshot("eazybackup-restore-confirm");

		// Restore as a copy: a second world appears next to the original.
		Path saves = context.computeOnClient(minecraft -> minecraft.getLevelSource().getBaseDir());
		long worldsBefore = countWorlds(saves);
		context.runOnClient(minecraft -> ClientBackups.restore(minecraft, locked, true, listScreen));
		context.waitFor(minecraft -> minecraft.gui.screen() == listScreen, BACKUP_TIMEOUT);
		assertEquals(worldsBefore + 1, countWorlds(saves), "restoring as a copy creates a new world");

		// Replace the original world: a safety backup is created first.
		int backupsBefore = BackupStorage.list(worldId).size();
		context.runOnClient(minecraft -> ClientBackups.restore(minecraft, locked, false, listScreen));
		context.waitFor(minecraft -> minecraft.gui.screen() == listScreen, BACKUP_TIMEOUT);
		List<BackupInfo> afterRestore = BackupStorage.list(worldId);
		assertEquals(backupsBefore + 1, afterRestore.size(), "a safety backup is created before replacing");
		assertTrue(afterRestore.stream().anyMatch(info -> info.backupType() == BackupType.PRE_RESTORE), "safety backup exists");
		assertTrue(Files.isRegularFile(saves.resolve(worldId).resolve("level.dat")), "restored world has its level.dat");

		context.setScreen(() -> new TitleScreen());
	}

	private static BackupInfo backup(ClientGameTestContext context, TestSingleplayerContext singleplayer, BackupType type, String label) {
		CompletableFuture<BackupInfo> backup = singleplayer.getServer().computeOnServer(server -> BackupManager.createLiveBackup(server, type, label));
		// The game only advances while the test waits, so wait for ticks instead of blocking.
		context.waitFor(minecraft -> backup.isDone(), BACKUP_TIMEOUT);
		return backup.join();
	}

	private static long countWorlds(Path saves) {
		try (Stream<Path> worlds = Files.list(saves)) {
			return worlds.filter(path -> Files.isRegularFile(path.resolve("level.dat"))).count();
		} catch (IOException e) {
			throw new AssertionError("Failed to list worlds", e);
		}
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError("Expected: " + message);
		}
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!expected.equals(actual)) {
			throw new AssertionError("Expected " + message + " to be " + expected + " but was " + actual);
		}
	}
}
