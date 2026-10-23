package io.github.kalebisaias.eazybackup.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.kalebisaias.eazybackup.client.gui.EazyBackupConfigScreen;

public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return EazyBackupConfigScreen::new;
	}
}
