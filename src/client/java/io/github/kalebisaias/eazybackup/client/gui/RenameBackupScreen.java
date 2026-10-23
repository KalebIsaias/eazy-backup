package io.github.kalebisaias.eazybackup.client.gui;

import io.github.kalebisaias.eazybackup.backup.BackupInfo;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Edits the label of a backup, laid out like the vanilla "Edit World" screen.
 */
public class RenameBackupScreen extends Screen {
	private static final Component LABEL = Component.translatable("eazybackup.rename.label").withStyle(ChatFormatting.GRAY);
	private static final int MAX_LENGTH = 64;
	private final Screen parent;
	private final BackupInfo backup;
	private final Consumer<String> onSave;
	private final LinearLayout layout = LinearLayout.vertical().spacing(5);
	private EditBox labelEdit;

	public RenameBackupScreen(Screen parent, BackupInfo backup, Consumer<String> onSave) {
		super(Component.translatable("eazybackup.rename.title"));
		this.parent = parent;
		this.backup = backup;
		this.onSave = onSave;
	}

	@Override
	protected void init() {
		this.layout.addChild(new SpacerElement(200, 20));
		this.layout.addChild(new StringWidget(LABEL, this.font));
		this.labelEdit = this.layout.addChild(new EditBox(this.font, 200, 20, LABEL));
		this.labelEdit.setMaxLength(MAX_LENGTH);
		this.labelEdit.setValue(this.backup.label == null ? "" : this.backup.label);
		this.labelEdit.setHint(Component.literal(Formats.date(this.backup.createdAt)).withStyle(EditBox.SEARCH_HINT_STYLE));
		this.layout.addChild(new SpacerElement(200, 20));
		LinearLayout buttons = this.layout.addChild(LinearLayout.horizontal().spacing(4));
		buttons.addChild(Button.builder(Component.translatable("selectWorld.edit.save"), button -> this.save()).width(98).build());
		buttons.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).width(98).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	@Override
	protected void setInitialFocus() {
		this.setInitialFocus(this.labelEdit);
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		FrameLayout.centerInRectangle(this.layout, this.getRectangle());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.labelEdit.isFocused() && event.isConfirmation()) {
			this.save();
			return true;
		}

		return super.keyPressed(event);
	}

	private void save() {
		this.onSave.accept(this.labelEdit.getValue());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 15, -1);
	}
}
