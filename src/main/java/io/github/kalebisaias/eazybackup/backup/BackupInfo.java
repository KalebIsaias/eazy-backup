package io.github.kalebisaias.eazybackup.backup;

import java.nio.file.Path;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Metadata of a single backup. It is stored next to the archive as {@code <id>.json}.
 */
public final class BackupInfo {
	public static final int FORMAT_VERSION = 1;

	public int formatVersion = FORMAT_VERSION;
	public String id = "";
	public String worldId = "";
	public String worldName = "";
	public String type = BackupType.MANUAL.serializedName();
	public long createdAt;
	public long sizeBytes;
	public long durationMillis;
	public boolean locked;
	public @Nullable String label;
	public @Nullable String gameVersion;

	/** Not serialized, resolved when the backup is read from disk. */
	public transient @Nullable Path archive;

	public BackupType backupType() {
		return BackupType.bySerializedName(this.type);
	}

	public Instant createdInstant() {
		return Instant.ofEpochMilli(this.createdAt);
	}

	public boolean hasLabel() {
		return this.label != null && !this.label.isBlank();
	}

	public Path archive() {
		if (this.archive == null) {
			throw new IllegalStateException("Backup " + this.id + " has no archive path");
		}

		return this.archive;
	}
}
