package io.github.kalebisaias.eazybackup.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.kalebisaias.eazybackup.EazyBackup;
import io.github.kalebisaias.eazybackup.backup.BackupStorage;
import io.github.kalebisaias.eazybackup.client.gui.widget.ItemIconButton;
import io.github.kalebisaias.eazybackup.util.Texts;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelSummary;
import org.jspecify.annotations.Nullable;

/**
 * Lists every world together with its backups, including worlds that were deleted but still have backups.
 */
public class BackupWorldListScreen extends Screen {
	private static final int ICON_MARGIN = 6;
	private static final int GRAY = 0xFF808080;
	private final @Nullable Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 8 + 9 + 8 + 20 + 4, 33);
	private @Nullable CompletableFuture<List<WorldRow>> pending;
	private List<WorldRow> rows = List.of();
	private @Nullable EditBox searchBox;
	private @Nullable WorldList list;
	private @Nullable Button viewButton;
	private @Nullable ItemIconButton optionsButton;

	public BackupWorldListScreen(@Nullable Screen parent) {
		super(Component.translatable("eazybackup.screen.worlds.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(this.title, this.font));
		this.searchBox = header.addChild(new EditBox(this.font, 0, 0, 200, 20, this.searchBox, Component.translatable("selectWorld.search")));
		this.searchBox.setHint(Component.translatable("gui.selectWorld.search").setStyle(EditBox.SEARCH_HINT_STYLE));
		this.searchBox.setResponder(value -> this.applyFilter());

		this.list = this.layout.addToContents(new WorldList(this.minecraft, this.width, this.layout.getContentHeight()));

		LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		this.viewButton = footer.addChild(Button.builder(Component.translatable("eazybackup.button.view_backups"), button -> this.openSelected()).build());
		footer.addChild(Button.builder(this.parent == null ? CommonComponents.GUI_DONE : CommonComponents.GUI_BACK, button -> this.onClose()).build());
		this.layout.visitWidgets(this::addRenderableWidget);

		this.optionsButton = this.addRenderableWidget(new ItemIconButton(0, 0, new ItemStack(Items.COMPARATOR), Component.translatable("eazybackup.button.options"), button ->
			this.minecraft.gui.setScreen(new EazyBackupConfigScreen(this))
		));

		this.reload();
		this.repositionElements();
	}

	@Override
	protected void setInitialFocus() {
		if (this.searchBox != null) {
			this.setInitialFocus(this.searchBox);
		}
	}

	@Override
	protected void repositionElements() {
		if (this.list != null) {
			this.list.updateSize(this.width, this.layout);
		}

		this.layout.arrangeElements();
		if (this.optionsButton != null) {
			this.optionsButton.setPosition(this.width - ICON_MARGIN - ItemIconButton.SIZE, ICON_MARGIN);
		}
	}

	private void reload() {
		Minecraft minecraft = this.minecraft;
		CompletableFuture<List<LevelSummary>> summaries;
		try {
			summaries = minecraft.getLevelSource().loadLevelSummaries(minecraft.getLevelSource().findLevelCandidates());
		} catch (Exception e) {
			EazyBackup.LOGGER.error("Failed to list worlds", e);
			summaries = CompletableFuture.completedFuture(List.of());
		}

		CompletableFuture<List<BackupStorage.WorldBackups>> backups = CompletableFuture.supplyAsync(BackupStorage::listWorlds, Util.backgroundExecutor());
		this.pending = summaries.exceptionally(error -> List.of()).thenCombine(backups, WorldRow::merge);
	}

	@Override
	public void tick() {
		if (this.pending != null && this.pending.isDone()) {
			this.rows = this.pending.getNow(List.of());
			this.pending = null;
			this.applyFilter();
		}

		if (this.viewButton != null) {
			this.viewButton.active = this.list != null && this.list.getSelected() instanceof WorldEntry;
		}
	}

	private void applyFilter() {
		if (this.list == null) {
			return;
		}

		String filter = this.searchBox == null ? "" : this.searchBox.getValue().toLowerCase(Locale.ROOT);
		this.list.setRows(this.rows.stream()
			.filter(row -> row.name().toLowerCase(Locale.ROOT).contains(filter) || row.worldId().toLowerCase(Locale.ROOT).contains(filter))
			.toList(), this.pending != null);
	}

	private void openSelected() {
		if (this.list != null && this.list.getSelected() instanceof WorldEntry entry) {
			this.open(entry.row);
		}
	}

	private void open(WorldRow row) {
		this.minecraft.gui.setScreen(new BackupListScreen(this, row.worldId(), row.name()));
	}

	@Override
	public void added() {
		super.added();
		// Coming back from a world's backups, counts may have changed.
		if (this.list != null) {
			this.reload();
		}
	}

	@Override
	public void removed() {
		if (this.list != null) {
			this.list.closeIcons();
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	record WorldRow(String worldId, String name, @Nullable LevelSummary summary, BackupStorage.@Nullable WorldBackups backups) {
		static List<WorldRow> merge(List<LevelSummary> summaries, List<BackupStorage.WorldBackups> backups) {
			Map<String, BackupStorage.WorldBackups> byId = new HashMap<>();
			backups.forEach(world -> byId.put(world.worldId(), world));
			List<WorldRow> rows = new ArrayList<>();
			for (LevelSummary summary : summaries) {
				rows.add(new WorldRow(summary.getLevelId(), summary.getLevelName(), summary, byId.remove(summary.getLevelId())));
			}

			byId.values().forEach(world -> rows.add(new WorldRow(world.worldId(), world.worldName(), null, world)));
			rows.sort(Comparator.comparingLong(WorldRow::lastActivity).reversed());
			return rows;
		}

		long lastActivity() {
			long played = this.summary == null ? 0L : this.summary.getLastPlayed();
			long backedUp = this.backups == null ? 0L : this.backups.latestBackup();
			return Math.max(played, backedUp);
		}
	}

	final class WorldList extends ObjectSelectionList<WorldList.Entry> {
		private final Map<String, FaviconTexture> icons = new HashMap<>();

		WorldList(Minecraft minecraft, int width, int height) {
			super(minecraft, width, height, 0, 36);
		}

		void setRows(List<WorldRow> rows, boolean loading) {
			String selectedId = this.getSelected() instanceof WorldEntry entry ? entry.row.worldId() : null;
			this.clearEntries();
			WorldEntry toSelect = null;
			for (WorldRow row : rows) {
				WorldEntry entry = new WorldEntry(row, this.icon(row));
				if (row.worldId().equals(selectedId)) {
					toSelect = entry;
				}

				this.addEntry(entry);
			}

			if (rows.isEmpty()) {
				this.addEntry(new MessageEntry(Component.translatable(loading ? "eazybackup.screen.worlds.loading" : "eazybackup.screen.worlds.empty")));
			}

			this.setSelected(toSelect);
			this.refreshScrollAmount();
		}

		@Override
		public void setSelected(@Nullable Entry selected) {
			super.setSelected(selected instanceof WorldEntry ? selected : null);
		}

		@Override
		public int getRowWidth() {
			return Math.min(280, this.width - 24);
		}

		private FaviconTexture icon(WorldRow row) {
			return this.icons.computeIfAbsent(row.worldId(), worldId -> {
				// A separate texture id, so closing it never affects the vanilla world list.
				FaviconTexture texture = FaviconTexture.forWorld(this.minecraft.getTextureManager(), EazyBackup.MOD_ID + "/" + worldId);
				Path iconFile = row.summary() == null ? null : row.summary().getIcon();
				if (iconFile != null && Files.isRegularFile(iconFile)) {
					try (InputStream stream = Files.newInputStream(iconFile)) {
						texture.upload(NativeImage.read(stream));
					} catch (Exception e) {
						EazyBackup.LOGGER.warn("Invalid icon for world {}", worldId, e);
					}
				}

				return texture;
			});
		}

		void closeIcons() {
			this.icons.values().forEach(FaviconTexture::close);
			this.icons.clear();
		}

		abstract static class Entry extends ObjectSelectionList.Entry<Entry> {
		}

		final class MessageEntry extends Entry {
			private final Component message;

			MessageEntry(Component message) {
				this.message = message;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
				graphics.centeredText(WorldList.this.minecraft.font, this.message, this.getContentXMiddle(), this.getContentYMiddle() - 4, GRAY);
			}

			@Override
			public Component getNarration() {
				return this.message;
			}
		}
	}

	final class WorldEntry extends WorldList.Entry {
		private final WorldRow row;
		private final FaviconTexture icon;
		private final StringWidget nameText;
		private final StringWidget countText;
		private final StringWidget statusText;

		WorldEntry(WorldRow row, FaviconTexture icon) {
			this.row = row;
			this.icon = icon;
			Minecraft minecraft = BackupWorldListScreen.this.minecraft;
			int maxTextWidth = Math.min(280, BackupWorldListScreen.this.width - 24) - 32 - 3 - 6;
			this.nameText = new StringWidget(Component.literal(row.name()), minecraft.font).setMaxWidth(maxTextWidth);

			BackupStorage.WorldBackups backups = row.backups();
			Component count = backups == null
				? Component.translatable("eazybackup.screen.worlds.no_backups")
				: Component.translatable("eazybackup.screen.backups.summary", backups.count(), Texts.formatSize(backups.totalBytes()));
			this.countText = new StringWidget(count.copy().withColor(GRAY), minecraft.font).setMaxWidth(maxTextWidth);

			Component status;
			if (row.summary() == null) {
				status = Component.translatable("eazybackup.screen.worlds.deleted").withStyle(ChatFormatting.GOLD);
			} else if (backups != null) {
				status = Component.translatable("eazybackup.screen.worlds.last_backup", Formats.relative(backups.latestBackup())).withColor(GRAY);
			} else {
				status = Component.literal(row.worldId()).withColor(GRAY);
			}

			this.statusText = new StringWidget(status, minecraft.font).setMaxWidth(maxTextWidth);
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
			int x = this.getContentX();
			int y = this.getContentY();
			graphics.blit(RenderPipelines.GUI_TEXTURED, this.icon.textureLocation(), x, y, 0.0F, 0.0F, 32, 32, 32, 32);
			int textX = x + 32 + 3;
			this.nameText.setPosition(textX, y + 1);
			this.nameText.extractRenderState(graphics, mouseX, mouseY, a);
			this.countText.setPosition(textX, y + 9 + 3);
			this.countText.extractRenderState(graphics, mouseX, mouseY, a);
			this.statusText.setPosition(textX, y + 9 + 9 + 3);
			this.statusText.extractRenderState(graphics, mouseX, mouseY, a);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (doubleClick) {
				BackupWorldListScreen.this.open(this.row);
				return true;
			}

			return super.mouseClicked(event, doubleClick);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (event.isSelection()) {
				BackupWorldListScreen.this.open(this.row);
				return true;
			}

			return super.keyPressed(event);
		}

		@Override
		public Component getNarration() {
			return Component.translatable("narrator.select", this.row.name());
		}
	}
}
