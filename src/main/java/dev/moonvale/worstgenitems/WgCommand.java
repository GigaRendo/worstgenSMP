package dev.moonvale.worstgenitems;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class WgCommand {
	private WgCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("wg")
			.then(CosmeticCommand.node()));
		CosmeticCommand.registerHatAlias(d);
	}
}
