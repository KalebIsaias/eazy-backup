package io.github.kalebisaias.eazybackup.client.gui;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.serialization.Codec;
import io.github.kalebisaias.eazybackup.EazyBackup;
import io.github.kalebisaias.eazybackup.backup.BackupStorage;
import io.github.kalebisaias.eazybackup.config.CompressionLevel;
import io.github.kalebisaias.eazybackup.config.EazyBackupConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The mod options, built from vanilla option widgets so it looks like any other options screen.
 */
public class EazyBackupConfigScreen extends OptionsSubScreen {
	private static final int INTERVAL_STEP = 5;

	public EazyBackupConfigScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable("eazybackup.options.title"));
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}

		EazyBackupConfig config = EazyBackupConfig.get();

		this.list.addHeader(Component.translatable("eazybackup.options.section.automatic"));
		this.list.addSmall(
			bool("eazybackup.options.interval_backups", config.intervalBackups, value -> config.intervalBackups = value),
			new OptionInstance<>(
				"eazybackup.options.interval",
				tooltip("eazybackup.options.interval"),
				(caption, value) -> Options.genericValueLabel(caption, Component.translatable("eazybackup.time.minutes", value)),
				new OptionInstance.IntRange(EazyBackupConfig.MIN_INTERVAL_MINUTES / INTERVAL_STEP, EazyBackupConfig.MAX_INTERVAL_MINUTES / INTERVAL_STEP)
					.xmap(step -> step * INTERVAL_STEP, minutes -> minutes / INTERVAL_STEP, true),
				config.intervalMinutes,
				value -> config.intervalMinutes = value
			),
			bool("eazybackup.options.backup_on_exit", config.backupOnExit, value -> config.backupOnExit = value),
			bool("eazybackup.options.skip_when_idle", config.skipWhenIdle, value -> config.skipWhenIdle = value)
		);

		this.list.addHeader(Component.translatable("eazybackup.options.section.storage"));
		this.list.addSmall(
			new OptionInstance<>(
				"eazybackup.options.max_backups",
				tooltip("eazybackup.options.max_backups"),
				Options::genericValueLabel,
				new OptionInstance.IntRange(EazyBackupConfig.MIN_BACKUPS, EazyBackupConfig.MAX_BACKUPS),
				config.maxBackupsPerWorld,
				value -> config.maxBackupsPerWorld = value
			),
			new OptionInstance<>(
				"eazybackup.options.compression",
				tooltip("eazybackup.options.compression"),
				(caption, value) -> Component.translatable(value.translationKey()),
				new OptionInstance.Enum<>(Arrays.asList(CompressionLevel.values()), Codec.STRING.xmap(CompressionLevel::valueOf, CompressionLevel::name)),
				config.compression,
				value -> config.compression = value
			),
			bool("eazybackup.options.safety_backup", config.safetyBackupBeforeRestore, value -> config.safetyBackupBeforeRestore = value)
		);

		this.list.addHeader(Component.translatable("eazybackup.options.section.notifications"));
		this.list.addSmall(
			bool("eazybackup.options.show_toasts", config.showToasts, value -> config.showToasts = value),
			bool("eazybackup.options.notify_operators", config.notifyOperators, value -> config.notifyOperators = value)
		);
	}

	@Override
	protected void addFooter() {
		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		footer.addChild(Button.builder(Component.translatable("eazybackup.button.open_folder"), button -> openBackupFolder()).build());
		footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());
	}

	@Override
	public void removed() {
		super.removed();
		EazyBackupConfig.save();
	}

	private static OptionInstance<Boolean> bool(String key, boolean value, OptionInstance.ValueUpdateListener<Boolean> onChange) {
		return OptionInstance.createBoolean(key, tooltip(key), value, onChange);
	}

	private static <T> OptionInstance.TooltipSupplier<T> tooltip(String key) {
		return OptionInstance.cachedConstantTooltip(Component.translatable(key + ".tooltip"));
	}

	private static void openBackupFolder() {
		Path root = BackupStorage.root();
		try {
			Files.createDirectories(root);
		} catch (IOException e) {
			EazyBackup.LOGGER.error("Failed to create backup folder {}", root, e);
			return;
		}

		Blaze3D.openPath(root);
	}
}
