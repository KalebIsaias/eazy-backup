package io.github.kalebisaias.eazybackup.backup;

import java.util.concurrent.CompletionException;
import net.minecraft.network.chat.Component;

/**
 * A failure that can be shown to the player.
 */
public class BackupException extends RuntimeException {
	private final Component component;

	public BackupException(Component component) {
		super(component.getString());
		this.component = component;
	}

	public BackupException(Component component, Throwable cause) {
		super(component.getString(), cause);
		this.component = component;
	}

	public Component component() {
		return this.component;
	}

	public static Component describe(Throwable error) {
		Throwable cause = error;
		while (cause instanceof CompletionException && cause.getCause() != null) {
			cause = cause.getCause();
		}

		if (cause instanceof BackupException backupException) {
			return backupException.component();
		}

		String message = cause.getMessage();
		return Component.literal(message == null || message.isBlank() ? cause.getClass().getSimpleName() : message);
	}
}
