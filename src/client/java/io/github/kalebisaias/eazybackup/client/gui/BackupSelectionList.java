package io.github.kalebisaias.eazybackup.client.gui;

import io.github.kalebisaias.eazybackup.backup.BackupInfo;
import io.github.kalebisaias.eazybackup.backup.BackupType;
import io.github.kalebisaias.eazybackup.util.Texts;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

final class BackupSelectionList extends ObjectSelectionList<BackupSelectionList.Entry> {
	static final int ICON_SIZE = 32;
	private static final int ROW_HEIGHT = 36;
	private static final int ROW_WIDTH = 280;
	private static final int GRAY = 0xFF808080;
	private static final Identifier LOCK_SPRITE = Identifier.withDefaultNamespace("container/cartography_table/locked");
	private static final Component LOCKED_TOOLTIP = Component.translatable("eazybackup.backup.locked.tooltip");
	private final BackupListScreen screen;

	BackupSelectionList(Minecraft minecraft, int width, int height, BackupListScreen screen) {
		super(minecraft, width, height, 0, ROW_HEIGHT);
		this.screen = screen;
	}

	void setBackups(List<BackupInfo> backups, @Nullable String selectedId) {
		this.clearEntries();
		BackupEntry toSelect = null;
		for (BackupInfo backup : backups) {
			BackupEntry entry = new BackupEntry(backup);
			if (backup.id.equals(selectedId)) {
				toSelect = entry;
			}

			this.addEntry(entry);
		}

		if (backups.isEmpty()) {
			this.addEntry(new EmptyEntry());
		}

		this.setSelected(toSelect);
		this.refreshScrollAmount();
	}

	Optional<BackupInfo> selectedBackup() {
		return this.getSelected() instanceof BackupEntry entry ? Optional.of(entry.backup) : Optional.empty();
	}

	@Override
	public void setSelected(@Nullable Entry selected) {
		super.setSelected(selected instanceof BackupEntry ? selected : null);
		this.screen.onSelectionChanged();
	}

	@Override
	public int getRowWidth() {
		return Math.min(ROW_WIDTH, this.width - 24);
	}

	static Component title(BackupInfo backup) {
		return backup.hasLabel() ? Component.literal(backup.label) : Component.literal(Formats.date(backup.createdAt));
	}

	static ItemStack icon(BackupType type) {
		return new ItemStack(switch (type) {
			case MANUAL -> Items.CHEST;
			case SCHEDULED -> Items.CLOCK;
			case ON_EXIT -> Items.OAK_DOOR;
			case PRE_RESTORE -> Items.TOTEM_OF_UNDYING;
		});
	}

	abstract static class Entry extends ObjectSelectionList.Entry<Entry> {
	}

	final class BackupEntry extends Entry {
		private final BackupInfo backup;
		private final ItemStack icon;
		private final StringWidget titleText;
		private final StringWidget detailsText;
		private final StringWidget infoText;

		BackupEntry(BackupInfo backup) {
			this.backup = backup;
			this.icon = icon(backup.backupType());
			Minecraft minecraft = BackupSelectionList.this.minecraft;
			int maxTextWidth = BackupSelectionList.this.getRowWidth() - ICON_SIZE - 3 - 6;

			MutableComponent title = title(backup).copy();
			this.titleText = new StringWidget(title, minecraft.font).setMaxWidth(maxTextWidth);

			MutableComponent details = backup.backupType().displayName().copy();
			details.append(CommonComponents.SPACE).append(Component.literal("·")).append(CommonComponents.SPACE);
			details.append(backup.hasLabel() ? Component.literal(Formats.date(backup.createdAt)) : Formats.relative(backup.createdAt));
			this.detailsText = new StringWidget(details.withColor(GRAY), minecraft.font).setMaxWidth(maxTextWidth);

			MutableComponent info = Component.literal(Texts.formatSize(backup.sizeBytes));
			if (backup.gameVersion != null) {
				info.append(" · ").append(Component.translatable("eazybackup.backup.version", backup.gameVersion));
			}

			if (backup.locked) {
				info.append(" · ").append(Component.translatable("eazybackup.backup.locked").withStyle(ChatFormatting.GOLD));
			}

			this.infoText = new StringWidget(info.withColor(GRAY), minecraft.font).setMaxWidth(maxTextWidth);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
			int x = this.getContentX();
			int y = this.getContentY();
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			graphics.pose().scale(2.0F, 2.0F);
			graphics.fakeItem(this.icon, 0, 0);
			graphics.pose().popMatrix();
			if (this.backup.locked) {
				int lockX = x + ICON_SIZE - 10;
				int lockY = y + ICON_SIZE - 14;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOCK_SPRITE, lockX, lockY, 10, 14);
				if (mouseX >= lockX && mouseX < lockX + 10 && mouseY >= lockY && mouseY < lockY + 14) {
					graphics.setTooltipForNextFrame(LOCKED_TOOLTIP, mouseX, mouseY);
				}
			}

			int textX = x + ICON_SIZE + 3;
			this.titleText.setPosition(textX, y + 1);
			this.titleText.extractRenderState(graphics, mouseX, mouseY, a);
			this.detailsText.setPosition(textX, y + 9 + 3);
			this.detailsText.extractRenderState(graphics, mouseX, mouseY, a);
			this.infoText.setPosition(textX, y + 9 + 9 + 3);
			this.infoText.extractRenderState(graphics, mouseX, mouseY, a);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (doubleClick) {
				BackupSelectionList.this.screen.onActivated(this.backup);
			}

			return super.mouseClicked(event, doubleClick);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (event.isSelection()) {
				BackupSelectionList.this.screen.onActivated(this.backup);
				return true;
			}

			return super.keyPressed(event);
		}

		@Override
		public Component getNarration() {
			return Component.translatable("narrator.select", CommonComponents.joinForNarration(
				title(this.backup), this.backup.backupType().displayName(), Component.literal(Texts.formatSize(this.backup.sizeBytes))
			));
		}
	}

	final class EmptyEntry extends Entry {
		private static final Component MESSAGE = Component.translatable("eazybackup.screen.backups.empty");

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
			graphics.centeredText(BackupSelectionList.this.minecraft.font, MESSAGE, this.getContentXMiddle(), this.getContentYMiddle() - 4, GRAY);
		}

		@Override
		public Component getNarration() {
			return MESSAGE;
		}
	}
}
