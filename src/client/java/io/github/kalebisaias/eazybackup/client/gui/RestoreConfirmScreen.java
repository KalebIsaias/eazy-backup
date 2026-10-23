package io.github.kalebisaias.eazybackup.client.gui;

import io.github.kalebisaias.eazybackup.backup.BackupInfo;
import io.github.kalebisaias.eazybackup.config.EazyBackupConfig;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Asks how to restore a backup, laid out like the vanilla backup confirmation screen.
 */
public class RestoreConfirmScreen extends Screen {
	private static final int BUTTON_WIDTH = 150;
	private final Screen parent;
	private final BackupInfo backup;
	private final boolean worldExists;
	/** Receives {@code true} to restore as a copy, {@code false} to replace the world. */
	private final BooleanConsumer onRestore;
	private MultiLineLabel message = MultiLineLabel.EMPTY;

	public RestoreConfirmScreen(Screen parent, BackupInfo backup, boolean worldExists, BooleanConsumer onRestore) {
		super(Component.translatable("eazybackup.restore.confirm.title"));
		this.parent = parent;
		this.backup = backup;
		this.worldExists = worldExists;
		this.onRestore = onRestore;
	}

	@Override
	protected void init() {
		Component description;
		if (!this.worldExists) {
			description = Component.translatable("eazybackup.restore.confirm.missing", this.backup.worldName, Formats.date(this.backup.createdAt));
		} else if (EazyBackupConfig.get().safetyBackupBeforeRestore) {
			description = Component.translatable("eazybackup.restore.confirm.message", this.backup.worldName, Formats.date(this.backup.createdAt))
				.append("\n\n")
				.append(Component.translatable("eazybackup.restore.confirm.safety").withStyle(ChatFormatting.GRAY));
		} else {
			description = Component.translatable("eazybackup.restore.confirm.message", this.backup.worldName, Formats.date(this.backup.createdAt))
				.append("\n\n")
				.append(Component.translatable("eazybackup.restore.confirm.no_safety").withStyle(ChatFormatting.RED));
		}

		this.message = MultiLineLabel.create(this.font, description, this.width - 50);
		int textHeight = (this.message.getLineCount() + 1) * 9;
		int centerX = this.width / 2;
		int buttonsY = Math.min(100 + textHeight, this.height - 52);

		if (this.worldExists) {
			Button replace = Button.builder(Component.translatable("eazybackup.restore.replace"), button -> this.onRestore.accept(false))
				.bounds(centerX - BUTTON_WIDTH - 5, buttonsY, BUTTON_WIDTH, 20)
				.build();
			replace.setTooltip(Tooltip.create(Component.translatable("eazybackup.restore.replace.tooltip")));
			this.addRenderableWidget(replace);
			Button copy = Button.builder(Component.translatable("eazybackup.restore.copy"), button -> this.onRestore.accept(true))
				.bounds(centerX + 5, buttonsY, BUTTON_WIDTH, 20)
				.build();
			copy.setTooltip(Tooltip.create(Component.translatable("eazybackup.restore.copy.tooltip")));
			this.addRenderableWidget(copy);
		} else {
			this.addRenderableWidget(Button.builder(Component.translatable("eazybackup.restore.recreate"), button -> this.onRestore.accept(false))
				.bounds(centerX - BUTTON_WIDTH / 2, buttonsY, BUTTON_WIDTH, 20)
				.build());
		}

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
			.bounds(centerX - BUTTON_WIDTH / 2, buttonsY + 24, BUTTON_WIDTH, 20)
			.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		ActiveTextCollector textRenderer = graphics.textRenderer();
		graphics.centeredText(this.font, this.title, this.width / 2, 50, -1);
		this.message.visitLines(TextAlignment.CENTER, this.width / 2, 70, 9, textRenderer);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			this.onClose();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}
}
