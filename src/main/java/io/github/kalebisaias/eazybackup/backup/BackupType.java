package io.github.kalebisaias.eazybackup.backup;

import io.github.kalebisaias.eazybackup.util.Texts;
import java.util.Locale;
import net.minecraft.network.chat.Component;

public enum BackupType {
	MANUAL,
	SCHEDULED,
	ON_EXIT,
	PRE_RESTORE;

	public String serializedName() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public Component displayName() {
		return Texts.translatable("eazybackup.type." + this.serializedName());
	}

	public static BackupType bySerializedName(String name) {
		for (BackupType type : values()) {
			if (type.serializedName().equals(name)) {
				return type;
			}
		}

		return MANUAL;
	}
}
