package io.github.kalebisaias.eazybackup.client.gui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A square button showing an item, like the icon buttons of the vanilla menus.
 */
public class ItemIconButton extends Button {
	public static final int SIZE = 20;
	private final ItemStack icon;

	public ItemIconButton(int x, int y, ItemStack icon, Component message, OnPress onPress) {
		super(x, y, SIZE, SIZE, message, onPress, DEFAULT_NARRATION);
		this.icon = icon;
		this.setTooltip(Tooltip.create(message));
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		this.extractDefaultSprite(graphics);
		graphics.fakeItem(this.icon, this.getX() + (this.getWidth() - 16) / 2, this.getY() + (this.getHeight() - 16) / 2);
	}
}
