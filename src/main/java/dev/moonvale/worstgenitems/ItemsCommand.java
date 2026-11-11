package dev.moonvale.worstgenitems;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public final class ItemsCommand {
	private ItemsCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(build("items"));
		dispatcher.register(build("wgitems"));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
		return Commands.literal(name)
			.requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
			.executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				ItemsMenu.open(player);
				return 1;
			})
			.then(Commands.literal("reload").executes(ctx -> {
				int n = ItemCatalog.load();
				ctx.getSource().sendSuccess(() -> Component.literal("Reloaded " + n + " items."), false);
				return 1;
			}));
	}
}
