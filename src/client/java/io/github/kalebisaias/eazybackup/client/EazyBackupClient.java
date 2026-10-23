package io.github.kalebisaias.eazybackup.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.kalebisaias.eazybackup.EazyBackup;
import io.github.kalebisaias.eazybackup.backup.BackupEvents;
import io.github.kalebisaias.eazybackup.backup.BackupManager;
import io.github.kalebisaias.eazybackup.client.gui.BackupListScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class EazyBackupClient implements ClientModInitializer {
	public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(EazyBackup.id("main"));
	/** Gives a running on-exit backup time to finish when the game is closed right after leaving a world. */
	private static final long SHUTDOWN_TIMEOUT_SECONDS = 300L;

	private static KeyMapping createBackupKey;
	private static KeyMapping openBackupsKey;

	@Override
	public void onInitializeClient() {
		createBackupKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.eazybackup.create_backup", InputConstants.KEY_F6, KEY_CATEGORY)
		);
		openBackupsKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.eazybackup.open_backups", InputConstants.UNKNOWN.getValue(), KEY_CATEGORY)
		);

		BackupEvents.register(new ToastNotifier());
		ScreenEvents.AFTER_INIT.register(MenuButtons::onScreenInit);
		ClientTickEvents.END_CLIENT_TICK.register(EazyBackupClient::onClientTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> BackupManager.awaitAll(SHUTDOWN_TIMEOUT_SECONDS));
	}

	private static void onClientTick(Minecraft minecraft) {
		while (createBackupKey.consumeClick()) {
			ClientBackups.createBackupOfCurrentWorld(minecraft);
		}

		while (openBackupsKey.consumeClick()) {
			if (minecraft.gui.screen() == null && minecraft.hasSingleplayerServer()) {
				minecraft.gui.setScreen(BackupListScreen.forCurrentWorld(minecraft, null));
			}
		}
	}
}
