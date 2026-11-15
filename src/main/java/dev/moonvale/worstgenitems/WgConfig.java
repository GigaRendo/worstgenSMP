package dev.moonvale.worstgenitems;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class WgConfig {
	/** false = everyone can use /wg cosmetic. true = ops only (gamemaster level). */
	public static boolean requirePermission = false;

	private WgConfig() {}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("worstgen-items.properties");
		Properties p = new Properties();
		try {
			if (Files.exists(file)) {
				try (Reader r = Files.newBufferedReader(file)) { p.load(r); }
			}
			requirePermission = Boolean.parseBoolean(p.getProperty("cosmetic.require_permission", "false"));
			p.setProperty("cosmetic.require_permission", String.valueOf(requirePermission));
			try (Writer w = Files.newBufferedWriter(file)) {
				p.store(w, "Worstgen items config");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
