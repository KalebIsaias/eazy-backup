package io.github.kalebisaias.eazybackup.config;

import java.util.zip.Deflater;

public enum CompressionLevel {
	NONE(Deflater.NO_COMPRESSION),
	FAST(Deflater.BEST_SPEED),
	BALANCED(Deflater.DEFAULT_COMPRESSION),
	SMALLEST(Deflater.BEST_COMPRESSION);

	private final int deflaterLevel;

	CompressionLevel(int deflaterLevel) {
		this.deflaterLevel = deflaterLevel;
	}

	public int deflaterLevel() {
		return this.deflaterLevel;
	}

	public String translationKey() {
		return "eazybackup.options.compression." + this.name().toLowerCase(java.util.Locale.ROOT);
	}
}
