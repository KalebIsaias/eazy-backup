package io.github.kalebisaias.eazybackup;

import io.github.kalebisaias.eazybackup.backup.BackupEvents;
import io.github.kalebisaias.eazybackup.backup.BackupScheduler;
import io.github.kalebisaias.eazybackup.backup.ServerBackupNotifier;
import io.github.kalebisaias.eazybackup.command.EazyBackupCommand;
import io.github.kalebisaias.eazybackup.config.EazyBackupConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EazyBackup implements ModInitializer {
	public static final String MOD_ID = "eazybackup";
	public static final Logger LOGGER = LoggerFactory.getLogger("Eazy Backup");

	@Override
	public void onInitialize() {
		EazyBackupConfig.load();
		BackupScheduler.register();
		BackupEvents.register(new ServerBackupNotifier());
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> EazyBackupCommand.register(dispatcher));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
