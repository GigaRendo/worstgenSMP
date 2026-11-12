package dev.moonvale.worstgenitems;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Item list loaded from config/worstgen_items.json if present, otherwise the copy bundled in the jar. */
public final class ItemCatalog {
	public record Entry(String id, String category, String name, Integer color, String base, String model, String equipSlot, String equipAsset) {
		public ItemStack toStack() {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(base));
			ItemStack stack = new ItemStack(item);
			stack.set(DataComponents.ITEM_MODEL, Identifier.parse(model));
			Style style = Style.EMPTY.withItalic(false);
			if (color != null) style = style.withColor(TextColor.fromRgb(color));
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style));
			EquipmentSlot slot = switch (equipSlot == null ? "" : equipSlot) {
				case "head" -> EquipmentSlot.HEAD;
				case "chest" -> EquipmentSlot.CHEST;
				case "legs" -> EquipmentSlot.LEGS;
				case "feet" -> EquipmentSlot.FEET;
				default -> null;
			};
			if (slot != null) {
				var builder = Equippable.builder(slot);
				if (equipAsset != null) {
					ResourceKey rawKey = ResourceKey.create(ResourceKey.createRegistryKey(Identifier.withDefaultNamespace("equipment_asset")), Identifier.parse(equipAsset));
                    builder = builder.setAsset(rawKey);
				}
				stack.set(DataComponents.EQUIPPABLE, builder.build());
			}
			return stack;
		}
	}

	private static List<Entry> all = List.of();
	private static Map<String, List<Entry>> byCategory = Map.of();

	private ItemCatalog() {}

	public static synchronized int load() {
		Path external = FabricLoader.getInstance().getConfigDir().resolve("worstgen_items.json");
		List<Entry> list = new ArrayList<>();
		try (Reader reader = Files.exists(external)
				? Files.newBufferedReader(external, StandardCharsets.UTF_8)
				: new InputStreamReader(ItemCatalog.class.getResourceAsStream("/worstgen_items.json"), StandardCharsets.UTF_8)) {
			JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
			for (JsonElement el : array) {
				JsonObject o = el.getAsJsonObject();
				String slot = null, asset = null;
				if (o.has("equip") && o.get("equip").isJsonObject()) {
					JsonObject eq = o.getAsJsonObject("equip");
					slot = str(eq, "slot");
					asset = str(eq, "asset");
				}
				Integer color = (o.has("color") && !o.get("color").isJsonNull()) ? o.get("color").getAsInt() : null;
				list.add(new Entry(str(o, "id"), str(o, "category"), str(o, "name"), color, str(o, "base"), str(o, "model"), slot, asset));
			}
		} catch (Exception e) {
			WorstGenItems.LOGGER.error("Could not load worstgen_items.json", e);
		}
		Map<String, List<Entry>> map = new LinkedHashMap<>();
		for (Entry e : list) map.computeIfAbsent(e.category(), k -> new ArrayList<>()).add(e);
		all = list;
		byCategory = map;
		WorstGenItems.LOGGER.info("Loaded {} items in {} categories", list.size(), map.size());
		return list.size();
	}

	private static String str(JsonObject o, String key) {
		return (o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : null;
	}

		public static synchronized Entry findByModel(String model) {
		for (Entry e : all) if (e.model().equals(model)) return e;
		return null;
	}
	public static synchronized Map<String, List<Entry>> byCategory() { return byCategory; }
}
