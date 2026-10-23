package io.github.kalebisaias.eazybackup.backup;

import io.github.kalebisaias.eazybackup.util.Texts;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * A backup or restore operation that is queued or running. Progress fields are written by the worker
 * thread and read by the UI, so they are volatile.
 */
public final class BackupTask {
	private final Kind kind;
	private final String worldId;
	private final String worldName;
	private final @Nullable BackupType type;
	private final @Nullable MinecraftServer server;
	private final long createdAt = System.currentTimeMillis();
	private final AtomicReference<State> state = new AtomicReference<>(State.WAITING);
	private final CompletableFuture<Void> done = new CompletableFuture<>();
	private volatile Phase phase = Phase.WAITING;
	private volatile float progress;

	BackupTask(Kind kind, String worldId, String worldName, @Nullable BackupType type, @Nullable MinecraftServer server) {
		this.kind = kind;
		this.worldId = worldId;
		this.worldName = worldName;
		this.type = type;
		this.server = server;
	}

	public Kind kind() {
		return this.kind;
	}

	public String worldId() {
		return this.worldId;
	}

	public String worldName() {
		return this.worldName;
	}

	public @Nullable BackupType type() {
		return this.type;
	}

	public @Nullable MinecraftServer server() {
		return this.server;
	}

	public long createdAt() {
		return this.createdAt;
	}

	public Phase phase() {
		return this.phase;
	}

	public float progress() {
		return this.progress;
	}

	public boolean isDone() {
		return this.done.isDone();
	}

	void phase(Phase phase) {
		this.phase = phase;
		this.progress = 0.0F;
	}

	void progress(float progress) {
		this.progress = progress;
	}

	boolean start() {
		return this.state.compareAndSet(State.WAITING, State.RUNNING);
	}

	/**
	 * Cancels the task if it has not started touching the world yet.
	 */
	boolean cancelIfWaiting() {
		return this.state.compareAndSet(State.WAITING, State.CANCELLED);
	}

	void finish() {
		this.done.complete(null);
	}

	CompletableFuture<Void> done() {
		return this.done;
	}

	public enum Kind {
		BACKUP,
		RESTORE
	}

	public enum Phase {
		WAITING("eazybackup.phase.waiting"),
		SAVING("eazybackup.phase.saving"),
		SAFETY_BACKUP("eazybackup.phase.safety_backup"),
		COMPRESSING("eazybackup.phase.compressing"),
		EXTRACTING("eazybackup.phase.extracting"),
		FINISHING("eazybackup.phase.finishing");

		private final String translationKey;

		Phase(String translationKey) {
			this.translationKey = translationKey;
		}

		public Component displayName() {
			return Texts.translatable(this.translationKey);
		}
	}

	private enum State {
		WAITING,
		RUNNING,
		CANCELLED
	}
}
