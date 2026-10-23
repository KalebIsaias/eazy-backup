package io.github.kalebisaias.eazybackup.client;

import io.github.kalebisaias.eazybackup.client.gui.BackupListScreen;
import io.github.kalebisaias.eazybackup.client.gui.BackupWorldListScreen;
import io.github.kalebisaias.eazybackup.client.gui.widget.ItemIconButton;
import io.github.kalebisaias.eazybackup.client.mixin.SelectWorldScreenAccessor;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * Adds a backups button next to the vanilla buttons of the pause and world selection menus.
 */
final class MenuButtons {
	private static final int SPACING = 4;
	private static final Component BUTTON_LABEL = Component.translatable("eazybackup.button.backups");

	private MenuButtons() {
	}

	static void onScreenInit(Minecraft minecraft, Screen screen, int width, int height) {
		if (screen instanceof PauseScreen && minecraft.hasSingleplayerServer()) {
			findByTranslationKey(screen, "menu.returnToGame").ifPresent(anchor -> place(screen, anchor, () ->
				minecraft.gui.setScreen(BackupListScreen.forCurrentWorld(minecraft, screen))
			));
		} else if (screen instanceof SelectWorldScreen selectWorldScreen) {
			Screens.getWidgets(screen).stream()
				.filter(EditBox.class::isInstance)
				.findFirst()
				.ifPresent(anchor -> place(screen, anchor, () -> openFromWorldSelection(minecraft, selectWorldScreen)));
		}
	}

	private static void openFromWorldSelection(Minecraft minecraft, SelectWorldScreen screen) {
		WorldSelectionList list = ((SelectWorldScreenAccessor) screen).eazybackup$getList();
		Optional<LevelSummary> selected = list == null
			? Optional.empty()
			: list.getSelectedOpt().map(WorldSelectionList.WorldListEntry::getLevelSummary);
		if (selected.isPresent()) {
			LevelSummary summary = selected.get();
			minecraft.gui.setScreen(new BackupListScreen(screen, summary.getLevelId(), summary.getLevelName()));
		} else {
			minecraft.gui.setScreen(new BackupWorldListScreen(screen));
		}
	}

	/**
	 * Places the button to the right of {@code anchor}. Fabric calls this again after the screen is
	 * resized, in which case the existing button is moved instead of adding another one.
	 */
	private static void place(Screen screen, AbstractWidget anchor, Runnable onPress) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		int x = anchor.getX() + anchor.getWidth() + SPACING;
		int y = anchor.getY() + (anchor.getHeight() - ItemIconButton.SIZE) / 2;
		for (AbstractWidget widget : widgets) {
			if (widget instanceof BackupsButton existing) {
				existing.setPosition(x, y);
				return;
			}
		}

		widgets.add(new BackupsButton(x, y, onPress));
	}

	private static Optional<AbstractWidget> findByTranslationKey(Screen screen, String key) {
		return Screens.getWidgets(screen).stream()
			.filter(widget -> widget.getMessage().getContents() instanceof TranslatableContents contents && contents.getKey().equals(key))
			.findFirst();
	}

	private static final class BackupsButton extends ItemIconButton {
		BackupsButton(int x, int y, Runnable onPress) {
			super(x, y, new ItemStack(Items.ENDER_CHEST), BUTTON_LABEL, button -> onPress.run());
		}
	}
}
