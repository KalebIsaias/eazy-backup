package io.github.kalebisaias.eazybackup.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.kalebisaias.eazybackup.EazyBackup;
import io.github.kalebisaias.eazybackup.backup.BackupException;
import io.github.kalebisaias.eazybackup.backup.BackupInfo;
import io.github.kalebisaias.eazybackup.backup.BackupManager;
import io.github.kalebisaias.eazybackup.backup.BackupScheduler;
import io.github.kalebisaias.eazybackup.backup.BackupStorage;
import io.github.kalebisaias.eazybackup.backup.BackupType;
import io.github.kalebisaias.eazybackup.config.EazyBackupConfig;
import io.github.kalebisaias.eazybackup.util.Texts;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.OptionalLong;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

public final class EazyBackupCommand {
	private static final int LIST_LIMIT = 10;
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault());
	private static final DynamicCommandExceptionType UNKNOWN_BACKUP = new DynamicCommandExceptionType(
		id -> Texts.translatable("eazybackup.command.unknown_backup", id)
	);
	private static final SuggestionProvider<CommandSourceStack> BACKUP_IDS = (context, builder) ->
		SharedSuggestionProvider.suggest(backups(context.getSource().getServer()).stream().map(info -> info.id), builder);

	private EazyBackupCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal(EazyBackup.MOD_ID)
			.requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
			.then(Commands.literal("create")
				.executes(context -> create(context, null))
				.then(Commands.argument("label", StringArgumentType.greedyString())
					.executes(context -> create(context, StringArgumentType.getString(context, "label")))))
			.then(Commands.literal("list").executes(EazyBackupCommand::list))
			.then(Commands.literal("status").executes(EazyBackupCommand::status))
			.then(Commands.literal("lock")
				.then(Commands.argument("backup", StringArgumentType.word()).suggests(BACKUP_IDS)
					.executes(context -> setLocked(context, true))))
			.then(Commands.literal("unlock")
				.then(Commands.argument("backup", StringArgumentType.word()).suggests(BACKUP_IDS)
					.executes(context -> setLocked(context, false))))
			.then(Commands.literal("delete")
				.then(Commands.argument("backup", StringArgumentType.word()).suggests(BACKUP_IDS)
					.executes(EazyBackupCommand::delete))));
	}

	private static int create(CommandContext<CommandSourceStack> context, String label) {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();
		if (BackupManager.isBusy(BackupManager.worldId(server))) {
			source.sendFailure(Texts.translatable("eazybackup.error.busy", server.getWorldData().getLevelName()));
			return 0;
		}

		source.sendSuccess(() -> Texts.translatable("eazybackup.command.started"), true);
		BackupManager.createLiveBackup(server, BackupType.MANUAL, label).whenComplete((info, error) -> server.execute(() -> {
			if (error != null) {
				source.sendFailure(Texts.translatable("eazybackup.chat.backup_failed", BackupException.describe(error)));
			} else {
				source.sendSuccess(() -> Texts.translatable(
					"eazybackup.chat.backup_created",
					info.backupType().displayName(),
					Texts.formatSize(info.sizeBytes),
					Texts.formatDuration(info.durationMillis)
				), true);
			}
		}));
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		List<BackupInfo> backups = backups(source.getServer());
		if (backups.isEmpty()) {
			source.sendSuccess(() -> Texts.translatable("eazybackup.command.list.empty"), false);
			return 0;
		}

		source.sendSuccess(() -> Texts.translatable("eazybackup.command.list.header", backups.size()), false);
		for (BackupInfo backup : backups.subList(0, Math.min(LIST_LIMIT, backups.size()))) {
			source.sendSuccess(() -> describe(backup), false);
		}

		if (backups.size() > LIST_LIMIT) {
			source.sendSuccess(() -> Texts.translatable("eazybackup.command.list.more", backups.size() - LIST_LIMIT).withStyle(ChatFormatting.GRAY), false);
		}

		return backups.size();
	}

	private static int status(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		MinecraftServer server = source.getServer();
		EazyBackupConfig config = EazyBackupConfig.get();
		List<BackupInfo> backups = backups(server);
		long locked = backups.stream().filter(info -> info.locked).count();
		long total = backups.stream().mapToLong(info -> info.sizeBytes).sum();
		source.sendSuccess(() -> Texts.translatable("eazybackup.command.status.count", backups.size(), locked, Texts.formatSize(total), config.maxBackupsPerWorld), false);
		OptionalLong next = BackupScheduler.millisUntilNextBackup(server);
		Component schedule = next.isPresent()
			? Texts.translatable("eazybackup.command.status.next", Texts.formatDuration(next.getAsLong()))
			: Texts.translatable("eazybackup.command.status.disabled");
		source.sendSuccess(() -> schedule, false);
		return 1;
	}

	private static int setLocked(CommandContext<CommandSourceStack> context, boolean locked) throws CommandSyntaxException {
		BackupInfo backup = find(context);
		backup.locked = locked;
		BackupStorage.save(backup);
		context.getSource().sendSuccess(() -> Texts.translatable(locked ? "eazybackup.command.locked" : "eazybackup.command.unlocked", backup.id), true);
		return 1;
	}

	private static int delete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		BackupInfo backup = find(context);
		if (backup.locked) {
			context.getSource().sendFailure(Texts.translatable("eazybackup.command.delete.locked", backup.id));
			return 0;
		}

		try {
			BackupStorage.delete(backup);
		} catch (IOException e) {
			EazyBackup.LOGGER.error("Failed to delete backup {}", backup.id, e);
			context.getSource().sendFailure(Texts.translatable("eazybackup.error.delete_failed", backup.id));
			return 0;
		}

		context.getSource().sendSuccess(() -> Texts.translatable("eazybackup.command.deleted", backup.id), true);
		return 1;
	}

	private static BackupInfo find(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String id = StringArgumentType.getString(context, "backup");
		return backups(context.getSource().getServer()).stream()
			.filter(info -> info.id.equals(id))
			.findFirst()
			.orElseThrow(() -> UNKNOWN_BACKUP.create(id));
	}

	private static List<BackupInfo> backups(MinecraftServer server) {
		return BackupStorage.list(BackupManager.worldId(server));
	}

	private static Component describe(BackupInfo backup) {
		MutableComponent line = Component.literal(backup.locked ? "■ " : "□ ").withStyle(backup.locked ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY);
		line.append(Component.literal(DATE_FORMAT.format(backup.createdInstant())).withStyle(ChatFormatting.WHITE));
		line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
		line.append(backup.backupType().displayName().copy().withStyle(ChatFormatting.GRAY));
		line.append(Component.literal(" · " + Texts.formatSize(backup.sizeBytes)).withStyle(ChatFormatting.GRAY));
		if (backup.hasLabel()) {
			line.append(Component.literal(" · " + backup.label).withStyle(ChatFormatting.YELLOW));
		}

		return line.withStyle(style -> style
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(backup.id)))
			.withClickEvent(new ClickEvent.SuggestCommand("/" + EazyBackup.MOD_ID + " lock " + backup.id)));
	}
}
