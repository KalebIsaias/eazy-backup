package io.github.kalebisaias.eazybackup.client.gui;

import com.mojang.blaze3d.Blaze3D;
import io.github.kalebisaias.eazybackup.EazyBackup;
import io.github.kalebisaias.eazybackup.backup.BackupInfo;
import io.github.kalebisaias.eazybackup.backup.BackupManager;
import io.github.kalebisaias.eazybackup.backup.BackupScheduler;
import io.github.kalebisaias.eazybackup.backup.BackupStorage;
import io.github.kalebisaias.eazybackup.backup.BackupTask;
import io.github.kalebisaias.eazybackup.client.ClientBackups;
import io.github.kalebisaias.eazybackup.client.gui.widget.ItemIconButton;
import io.github.kalebisaias.eazybackup.client.mixin.SelectWorldScreenAccessor;
import io.github.kalebisaias.eazybackup.util.Texts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Lists the backups of one world, modeled after the vanilla world selection screen.
 */
public class BackupListScreen extends Screen {
	private static final int HEADER_HEIGHT = 8 + 9 + 4 + 9 + 8;
	private static final int FOOTER_HEIGHT = 60;
	private static final int ICON_MARGIN = 6;
	private static final Component CREATE = Component.translatable("eazybackup.button.create");
	private static final Component RESTORE = Component.translatable("eazybackup.button.restore");
	private static final Component LOCK = Component.translatable("eazybackup.button.lock");
	private static final Component UNLOCK = Component.translatable("eazybackup.button.unlock");
	private static final Component RENAME = Component.translatable("eazybackup.button.rename");
	private static final Component DELETE = Component.translatable("selectWorld.delete");
	private static final Tooltip LOCK_TOOLTIP = Tooltip.create(Component.translatable("eazybackup.button.lock.tooltip"));
	private static final Tooltip RESTORE_IN_GAME_TOOLTIP = Tooltip.create(Component.translatable("eazybackup.button.restore.in_game"));
	private static final Tooltip DELETE_LOCKED_TOOLTIP = Tooltip.create(Component.translatable("eazybackup.button.delete.locked"));
	private static final Tooltip CREATE_MISSING_TOOLTIP = Tooltip.create(Component.translatable("eazybackup.button.create.missing"));

	private final @Nullable Screen parent;
	private final String worldId;
	private final String worldName;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT);
	private @Nullable BackupSelectionList list;
	private @Nullable SpacerElement infoLine;
	private @Nullable Button createButton;
	private @Nullable Button restoreButton;
	private @Nullable Button lockButton;
	private @Nullable Button renameButton;
	private @Nullable Button deleteButton;
	private @Nullable ItemIconButton folderButton;
	private @Nullable ItemIconButton optionsButton;
	private List<BackupInfo> backups = List.of();
	private boolean wasBusy;

	public BackupListScreen(@Nullable Screen parent, String worldId, String worldName) {
		super(Component.translatable("eazybackup.screen.backups.title", worldName));
		this.parent = parent;
		this.worldId = worldId;
		this.worldName = worldName;
	}

	/**
	 * Opens the backups of the world that is currently being played.
	 */
	public static Screen forCurrentWorld(Minecraft minecraft, @Nullable Screen parent) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server == null) {
			return parent == null ? new BackupWorldListScreen(null) : parent;
		}

		return new BackupListScreen(parent, BackupManager.worldId(server), server.getWorldData().getLevelName());
	}

	@Override
	protected void init() {
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font).setMaxWidth(this.width - 2 * (ICON_MARGIN + 2 * ItemIconButton.SIZE + 4)));
		this.infoLine = header.addChild(new SpacerElement(200, 9));

		this.list = this.layout.addToContents(new BackupSelectionList(this.minecraft, this.width, this.layout.getContentHeight(), this));
		this.createFooter();
		this.layout.visitWidgets(this::addRenderableWidget);

		this.folderButton = this.addRenderableWidget(new ItemIconButton(0, 0, new ItemStack(Items.CHEST), Component.translatable("eazybackup.button.open_folder"), button -> this.openFolder()));
		this.optionsButton = this.addRenderableWidget(new ItemIconButton(0, 0, new ItemStack(Items.COMPARATOR), Component.translatable("eazybackup.button.options"), button ->
			this.minecraft.gui.setScreen(new EazyBackupConfigScreen(this))
		));

		this.reload();
		this.repositionElements();
	}

	private void createFooter() {
		GridLayout footer = this.layout.addToFooter(new GridLayout().columnSpacing(8).rowSpacing(4));
		footer.defaultCellSetting().alignHorizontallyCenter();
		GridLayout.RowHelper rows = footer.createRowHelper(4);
		this.createButton = rows.addChild(Button.builder(CREATE, button -> this.createBackup()).build(), 2);
		this.restoreButton = rows.addChild(Button.builder(RESTORE, button -> this.selected().ifPresent(this::openRestore)).build(), 2);
		this.lockButton = rows.addChild(Button.builder(LOCK, button -> this.selected().ifPresent(this::toggleLock)).width(71).build());
		this.renameButton = rows.addChild(Button.builder(RENAME, button -> this.selected().ifPresent(this::openRename)).width(71).build());
		this.deleteButton = rows.addChild(Button.builder(DELETE, button -> this.selected().ifPresent(this::confirmDelete)).width(71).build());
		rows.addChild(Button.builder(this.parent == null ? CommonComponents.GUI_DONE : CommonComponents.GUI_BACK, button -> this.onClose()).width(71).build());
	}

	@Override
	protected void repositionElements() {
		if (this.list != null) {
			this.list.updateSize(this.width, this.layout);
		}

		this.layout.arrangeElements();
		int iconY = (HEADER_HEIGHT - ItemIconButton.SIZE) / 2;
		if (this.optionsButton != null && this.folderButton != null) {
			this.optionsButton.setPosition(this.width - ICON_MARGIN - ItemIconButton.SIZE, iconY);
			this.folderButton.setPosition(this.optionsButton.getX() - 4 - ItemIconButton.SIZE, iconY);
		}
	}

	@Override
	public void tick() {
		boolean busy = BackupManager.isBusy(this.worldId);
		if (this.wasBusy && !busy) {
			this.reload();
		}

		this.wasBusy = busy;
		this.updateButtons();
	}

	void reload() {
		this.backups = BackupStorage.list(this.worldId);
		if (this.list != null) {
			String selectedId = this.selected().map(info -> info.id).orElse(null);
			this.list.setBackups(this.backups, selectedId);
		}

		this.updateButtons();
	}

	void onSelectionChanged() {
		this.updateButtons();
	}

	/**
	 * Called when an entry is double clicked or activated with the keyboard.
	 */
	void onActivated(BackupInfo backup) {
		if (this.canRestore()) {
			this.openRestore(backup);
		}
	}

	private Optional<BackupInfo> selected() {
		return this.list == null ? Optional.empty() : this.list.selectedBackup();
	}

	private boolean isWorldOpen() {
		return ClientBackups.serverRunning(this.minecraft, this.worldId) != null;
	}

	private boolean canRestore() {
		return !this.isWorldOpen() && !BackupManager.isBusy(this.worldId);
	}

	private void updateButtons() {
		if (this.createButton == null || this.restoreButton == null || this.lockButton == null || this.renameButton == null || this.deleteButton == null) {
			return;
		}

		boolean busy = BackupManager.isBusy(this.worldId);
		boolean worldOpen = this.isWorldOpen();
		boolean worldExists = worldOpen || ClientBackups.worldExists(this.minecraft, this.worldId);
		Optional<BackupInfo> selected = this.selected();

		this.createButton.active = !busy && worldExists;
		this.createButton.setTooltip(worldExists ? null : CREATE_MISSING_TOOLTIP);
		this.restoreButton.active = selected.isPresent() && !busy && !worldOpen;
		this.restoreButton.setTooltip(worldOpen ? RESTORE_IN_GAME_TOOLTIP : null);
		boolean locked = selected.map(info -> info.locked).orElse(false);
		this.lockButton.active = selected.isPresent();
		this.lockButton.setMessage(locked ? UNLOCK : LOCK);
		this.lockButton.setTooltip(LOCK_TOOLTIP);
		this.renameButton.active = selected.isPresent();
		this.deleteButton.active = selected.isPresent() && !locked && !busy;
		this.deleteButton.setTooltip(locked ? DELETE_LOCKED_TOOLTIP : null);
	}

	private Component infoText() {
		Optional<BackupTask> task = BackupManager.activeTask(this.worldId);
		if (task.isPresent()) {
			int percent = Mth.floor(task.get().progress() * 100.0F);
			return Component.translatable("eazybackup.screen.backups.working", task.get().phase().displayName(), percent).withStyle(ChatFormatting.YELLOW);
		}

		if (this.backups.isEmpty()) {
			return Component.translatable("eazybackup.screen.backups.none").withStyle(ChatFormatting.GRAY);
		}

		long totalBytes = this.backups.stream().mapToLong(info -> info.sizeBytes).sum();
		Component summary = Component.translatable("eazybackup.screen.backups.summary", this.backups.size(), Texts.formatSize(totalBytes));
		IntegratedServer server = ClientBackups.serverRunning(this.minecraft, this.worldId);
		if (server != null) {
			OptionalLong next = BackupScheduler.millisUntilNextBackup(server);
			if (next.isPresent()) {
				summary = Component.translatable("eazybackup.screen.backups.summary_next", summary, Formats.countdown(next.getAsLong()));
			}
		}

		return summary.copy().withStyle(ChatFormatting.GRAY);
	}

	private void createBackup() {
		ClientBackups.createBackup(this.minecraft, this.worldId, this.worldName);
		this.wasBusy = true;
		this.updateButtons();
	}

	private void openRestore(BackupInfo backup) {
		boolean worldExists = ClientBackups.worldExists(this.minecraft, this.worldId);
		this.minecraft.gui.setScreen(new RestoreConfirmScreen(this, backup, worldExists, asCopy -> ClientBackups.restore(this.minecraft, backup, asCopy, this)));
	}

	private void toggleLock(BackupInfo backup) {
		backup.locked = !backup.locked;
		BackupStorage.save(backup);
		this.updateButtons();
	}

	private void openRename(BackupInfo backup) {
		this.minecraft.gui.setScreen(new RenameBackupScreen(this, backup, label -> {
			backup.label = label.isBlank() ? null : label.strip();
			BackupStorage.save(backup);
			this.minecraft.gui.setScreen(this);
			this.reload();
		}));
	}

	private void confirmDelete(BackupInfo backup) {
		this.minecraft.gui.setScreen(new ConfirmScreen(
			confirmed -> {
				if (confirmed) {
					try {
						BackupStorage.delete(backup);
					} catch (IOException e) {
						EazyBackup.LOGGER.error("Failed to delete backup {}", backup.id, e);
					}
				}

				this.minecraft.gui.setScreen(this);
				this.reload();
			},
			Component.translatable("eazybackup.delete.title"),
			Component.translatable("eazybackup.delete.message", BackupSelectionList.title(backup)),
			Component.translatable("selectWorld.deleteButton"),
			CommonComponents.GUI_CANCEL
		));
	}

	private void openFolder() {
		Path directory = BackupStorage.worldDirectory(this.worldId);
		try {
			Files.createDirectories(directory);
		} catch (IOException e) {
			EazyBackup.LOGGER.error("Failed to create backup folder {}", directory, e);
			return;
		}

		Blaze3D.openPath(directory);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		if (this.infoLine != null) {
			graphics.centeredText(this.font, this.infoText(), this.width / 2, this.infoLine.getY(), -1);
		}
	}

	@Override
	public void onClose() {
		if (this.parent instanceof SelectWorldScreen selectWorldScreen) {
			WorldSelectionList worlds = ((SelectWorldScreenAccessor) selectWorldScreen).eazybackup$getList();
			if (worlds != null) {
				// A restore may have changed the worlds on disk.
				worlds.reloadWorldList();
			}
		}

		this.minecraft.gui.setScreen(this.parent);
	}
}
