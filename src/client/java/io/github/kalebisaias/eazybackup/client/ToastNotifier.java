package io.github.kalebisaias.eazybackup.client;

import io.github.kalebisaias.eazybackup.backup.BackupEvents;
import io.github.kalebisaias.eazybackup.backup.BackupException;
import io.github.kalebisaias.eazybackup.backup.BackupInfo;
import io.github.kalebisaias.eazybackup.backup.BackupTask;
import io.github.kalebisaias.eazybackup.config.EazyBackupConfig;
import io.github.kalebisaias.eazybackup.util.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Shows vanilla system toasts while backups are created.
 */
final class ToastNotifier implements BackupEvents.Listener {
	static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId();

	@Override
	public void onStarted(BackupTask task) {
		if (task.kind() == BackupTask.Kind.BACKUP && task.type() != null) {
			show(
				Component.translatable("eazybackup.toast.started", task.worldName()),
				task.type().displayName().copy().withStyle(ChatFormatting.GRAY)
			);
		}
	}

	@Override
	public void onCompleted(BackupTask task, BackupInfo backup) {
		show(
			Component.translatable("eazybackup.toast.created", task.worldName()),
			Component.translatable("eazybackup.toast.created.details", Texts.formatSize(backup.sizeBytes), Texts.formatDuration(backup.durationMillis))
		);
	}

	@Override
	public void onFailed(BackupTask task, Throwable error) {
		// Failures are always shown, even with toasts disabled.
		Minecraft minecraft = Minecraft.getInstance();
		Component title = Component.translatable("eazybackup.toast.failed", task.worldName()).withStyle(ChatFormatting.RED);
		Component message = BackupException.describe(error);
		minecraft.execute(() -> SystemToast.addOrUpdate(minecraft.gui.toastManager(), TOAST_ID, title, message));
	}

	static void show(Component title, Component message) {
		if (!EazyBackupConfig.get().showToasts) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> SystemToast.addOrUpdate(minecraft.gui.toastManager(), TOAST_ID, title, message));
	}
}
