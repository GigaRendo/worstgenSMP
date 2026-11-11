package dev.moonvale.worstgenitems;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorstGenItems implements ModInitializer {
	public static final String MOD_ID = "worstgen_items";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ItemCatalog.load();
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> ItemsCommand.register(dispatcher));
	}
}
