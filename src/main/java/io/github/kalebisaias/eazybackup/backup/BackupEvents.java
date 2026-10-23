package io.github.kalebisaias.eazybackup.backup;

import io.github.kalebisaias.eazybackup.EazyBackup;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notifies listeners about backups. Listeners may be called from any thread.
 */
public final class BackupEvents {
	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

	private BackupEvents() {
	}

	public static void register(Listener listener) {
		LISTENERS.add(listener);
	}

	static void started(BackupTask task) {
		for (Listener listener : LISTENERS) {
			try {
				listener.onStarted(task);
			} catch (RuntimeException e) {
				EazyBackup.LOGGER.error("Backup listener failed", e);
			}
		}
	}

	static void completed(BackupTask task, BackupInfo backup) {
		for (Listener listener : LISTENERS) {
			try {
				listener.onCompleted(task, backup);
			} catch (RuntimeException e) {
				EazyBackup.LOGGER.error("Backup listener failed", e);
			}
		}
	}

	static void failed(BackupTask task, Throwable error) {
		for (Listener listener : LISTENERS) {
			try {
				listener.onFailed(task, error);
			} catch (RuntimeException e) {
				EazyBackup.LOGGER.error("Backup listener failed", e);
			}
		}
	}

	public interface Listener {
		default void onStarted(BackupTask task) {
		}

		default void onCompleted(BackupTask task, BackupInfo backup) {
		}

		default void onFailed(BackupTask task, Throwable error) {
		}
	}
}
