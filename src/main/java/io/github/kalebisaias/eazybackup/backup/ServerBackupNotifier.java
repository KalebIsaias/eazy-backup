package io.github.kalebisaias.eazybackup.backup;

import io.github.kalebisaias.eazybackup.config.EazyBackupConfig;
import io.github.kalebisaias.eazybackup.util.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells operators of a dedicated server about backups in chat.
 */
public final class ServerBackupNotifier implements BackupEvents.Listener {
	@Override
	public void onCompleted(BackupTask task, BackupInfo backup) {
		this.notify(task, Texts.translatable(
			"eazybackup.chat.backup_created",
			backup.backupType().displayName(),
			Texts.formatSize(backup.sizeBytes),
			Texts.formatDuration(backup.durationMillis)
		).withStyle(ChatFormatting.GRAY));
	}

	@Override
	public void onFailed(BackupTask task, Throwable error) {
		this.notify(task, Texts.translatable("eazybackup.chat.backup_failed", BackupException.describe(error)).withStyle(ChatFormatting.RED));
	}

	private void notify(BackupTask task, Component message) {
		MinecraftServer server = task.server();
		if (server == null || !server.isDedicatedServer() || !EazyBackupConfig.get().notifyOperators) {
			return;
		}

		Component prefixed = Component.literal("[Eazy Backup] ").withStyle(ChatFormatting.GOLD).append(message);
		server.execute(() -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (server.getPlayerList().isOp(player.nameAndId())) {
					player.sendSystemMessage(prefixed);
				}
			}
		});
	}
}
