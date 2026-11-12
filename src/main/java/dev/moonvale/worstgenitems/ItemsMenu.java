package dev.moonvale.worstgenitems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Prediction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Server-side chest GUI: category view, then paged item view. Click an item to take a copy. */
public class ItemsMenu extends ChestMenu {
	private static final int SIZE = 54;
	private static final int PAGE = 45;
	private static final String ALL = "*";

	private final SimpleContainer box;
	private String category = null; // null = category view
	private int page = 0;
	private List<ItemCatalog.Entry> shown = List.of();
	private List<String> keys = List.of();

	private ItemsMenu(int id, Inventory inventory, SimpleContainer box) {
		super(MenuType.GENERIC_9x6, id, inventory, box, 6);
		this.box = box;
		render();
	}

	public static void open(ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider(
			(id, inv, p) -> new ItemsMenu(id, inv, new SimpleContainer(SIZE)),
			Component.literal("WorstGenR Items")));
	}

	private static ItemStack named(Item item, String text, int rgb) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(text).withStyle(Style.EMPTY.withItalic(false).withColor(TextColor.fromRgb(rgb))));
		return s;
	}

	private void render() {
		box.clearContent();
		if (category == null) {
			List<String> k = new ArrayList<>();
			k.add(ALL);
			k.addAll(ItemCatalog.byCategory().keySet());
			keys = k;
			for (int i = 0; i < keys.size() && i < PAGE; i++) {
				String key = keys.get(i);
				if (ALL.equals(key)) {
					box.setItem(i, named(Items.NETHER_STAR, "All Items (" + ItemCatalog.all().size() + ")", 0xFFFFFF));
				} else {
					List<ItemCatalog.Entry> list = ItemCatalog.byCategory().get(key);
					ItemStack icon = list.get(0).toStack();
					icon.set(DataComponents.CUSTOM_NAME, Component.literal(key + " (" + list.size() + ")")
						.withStyle(Style.EMPTY.withItalic(false).withColor(TextColor.fromRgb(0xFFFFFF))));
					box.setItem(i, icon);
				}
			}
			bottomRow(false, false, false);
			box.setItem(49, named(Items.PAPER, "Pick a category", 0xAAAAAA));
		} else {
			shown = ALL.equals(category) ? ItemCatalog.all() : ItemCatalog.byCategory().getOrDefault(category, List.of());
			int pages = Math.max(1, (shown.size() + PAGE - 1) / PAGE);
			page = Math.max(0, Math.min(page, pages - 1));
			for (int i = 0; i < PAGE; i++) {
				int idx = page * PAGE + i;
				if (idx < shown.size()) box.setItem(i, shown.get(idx).toStack());
			}
			bottomRow(true, page > 0, page < pages - 1);
			box.setItem(49, named(Items.PAPER, "Page " + (page + 1) + "/" + pages + "  -  click an item to take it", 0xAAAAAA));
		}
	}

	private void bottomRow(boolean back, boolean prev, boolean next) {
		for (int i = PAGE; i < SIZE; i++) box.setItem(i, named(Items.GLASS_PANE, " ", 0x555555));
		if (back) box.setItem(45, named(Items.BOOK, "Categories", 0xFFFF55));
		if (prev) box.setItem(48, named(Items.ARROW, "Previous page", 0x55FF55));
		if (next) box.setItem(50, named(Items.ARROW, "Next page", 0x55FF55));
		box.setItem(53, named(Items.BARRIER, "Close", 0xFF5555));
	}

	private void handle(ServerPlayer player, int slot) {
		if (slot < PAGE) {
			if (category == null) {
				if (slot < keys.size()) {
					category = keys.get(slot);
					page = 0;
					render();
				}
			} else {
				int idx = page * PAGE + slot;
				if (idx < shown.size()) {
					ItemStack stack = shown.get(idx).toStack();
					player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
				}
			}
			return;
		}
		switch (slot) {
			case 45 -> { if (category != null) { category = null; page = 0; render(); } }
			case 48 -> { if (category != null && page > 0) { page--; render(); } }
			case 50 -> { if (category != null) { page++; render(); } }
			case 53 -> player.closeContainer();
			default -> { }
		}
	}

	@Override
	public void clicked(int slotId, int clickData, ContainerInput containerInput, Player player) {
		if (slotId >= 0 && slotId < SIZE) {
			if (player instanceof ServerPlayer sp) handle(sp, slotId);
			this.sendAllDataToRemote();
			return;
		}
		super.clicked(slotId, clickData, containerInput, player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
		return false;
	}
}
