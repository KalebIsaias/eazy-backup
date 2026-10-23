package io.github.kalebisaias.eazybackup.client.gui;

import io.github.kalebisaias.eazybackup.backup.BackupTask;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Shows the progress of a backup operation, styled like the vanilla "Optimize World" screen.
 */
public class BackupProgressScreen<T> extends Screen {
	private static final int BAR_HALF_WIDTH = 150;
	private static final int TEXT_COLOR = 0xFFA0A0A0;
	private static final int BAR_FILL = 0xFF80FF20;
	private final @Nullable BackupTask task;
	private final CompletableFuture<T> future;
	private final BiConsumer<T, @Nullable Throwable> onDone;
	private boolean finished;

	public BackupProgressScreen(Component title, @Nullable BackupTask task, CompletableFuture<T> future, BiConsumer<T, @Nullable Throwable> onDone) {
		super(title);
		this.task = task;
		this.future = future;
		this.onDone = onDone;
	}

	@Override
	public void tick() {
		if (!this.finished && this.future.isDone()) {
			this.finished = true;
			T result = null;
			Throwable error = null;
			try {
				result = this.future.join();
			} catch (RuntimeException e) {
				error = e;
			}

			this.onDone.accept(result, error);
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int centerX = this.width / 2;
		graphics.centeredText(this.font, this.title, centerX, 20, -1);
		if (this.task == null) {
			return;
		}

		int x0 = centerX - Math.min(BAR_HALF_WIDTH, centerX - 10);
		int x1 = centerX + Math.min(BAR_HALF_WIDTH, centerX - 10);
		int y0 = this.height / 4 + 100;
		int y1 = y0 + 10;
		graphics.centeredText(this.font, this.task.phase().displayName(), centerX, y0 - 9 - 2, TEXT_COLOR);
		graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF000000);
		graphics.fill(x0, y0, x0 + Mth.floor(this.task.progress() * (x1 - x0)), y1, BAR_FILL);
		Component percentage = Component.translatable("optimizeWorld.progress.percentage", Mth.floor(this.task.progress() * 100.0F));
		graphics.centeredText(this.font, percentage, centerX, y0 + (y1 - y0) / 2 - 9 / 2, TEXT_COLOR);
		graphics.centeredText(this.font, Component.literal(this.task.worldName()), centerX, y1 + 9, TEXT_COLOR);
	}
}
