package dev.moonvale.worstgenitems;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.equipment.Equippable;

public final class CosmeticCommand {
	private static final String K_MODEL = "wg_hat_model";   // cosmetic model currently applied
	private static final String K_OM = "wg_orig_model";
	private static final String K_OA = "wg_orig_asset";

	private enum Slot {
		HELMET("helmet", EquipmentSlot.HEAD),
		CHESTPLATE("chestplate", EquipmentSlot.CHEST),
		LEGGINGS("leggings", EquipmentSlot.LEGS),
		BOOTS("boots", EquipmentSlot.FEET);

		final String id;
		final EquipmentSlot eq;
		Slot(String id, EquipmentSlot eq) { this.id = id; this.eq = eq; }
	}

	private CosmeticCommand() {}

	// ---------- command tree ----------

	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		var root = Commands.literal("cosmetic");
		for (Slot s : Slot.values()) root.then(slotNode(s));
		return root;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> slotNode(Slot s) {
		return Commands.literal(s.id)
			.requires(src -> canUse(src, s))
			.executes(ctx -> equip(ctx.getSource(), ctx.getSource().getPlayerOrException(), s))
			.then(Commands.literal("equip")
				.executes(ctx -> equip(ctx.getSource(), ctx.getSource().getPlayerOrException(), s)))
			.then(Commands.literal("remove")
				.executes(ctx -> remove(ctx.getSource(), ctx.getSource().getPlayerOrException(), s)));
	}

	/** /hat stays as an alias for the helmet slot. */
	public static void registerHatAlias(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("hat")
			.requires(src -> canUse(src, Slot.HELMET))
			.executes(ctx -> equip(ctx.getSource(), ctx.getSource().getPlayerOrException(), Slot.HELMET))
			.then(Commands.literal("remove")
				.executes(ctx -> remove(ctx.getSource(), ctx.getSource().getPlayerOrException(), Slot.HELMET))));
	}

	private static boolean canUse(CommandSourceStack src, Slot s) {
		if (!WgConfig.requirePermission) return true;
		return Permissions.check(src, "wg.cosmetic." + s.id, 2);
	}

	// ---------- helpers ----------

	private static void say(CommandSourceStack src, String msg) {
		src.sendSuccess(() -> Component.literal(msg), false);
	}

	private static void give(ServerPlayer p, ItemStack stack) {
		p.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
	}

	private static boolean isSkinned(ItemStack s) {
		CustomData cd = s.get(DataComponents.CUSTOM_DATA);
		return cd != null && cd.copyTag().contains(K_MODEL);
	}

	private static boolean isRealArmor(ItemStack s, Slot slot) {
		Equippable eq = s.get(DataComponents.EQUIPPABLE);
		return eq != null && eq.slot() == slot.eq && eq.assetId().isPresent();
	}

	/** Does the held item belong in this slot as a cosmetic? */
	private static boolean fitsSlot(ItemStack hand, Slot slot) {
		Equippable eq = hand.get(DataComponents.EQUIPPABLE);
		if (eq != null) return eq.slot() == slot.eq;
		return slot == Slot.HELMET; // item-model hats (no equippable) are helmet-only
	}

	/** Copies the original Equippable's settings and only swaps the asset. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Equippable withAsset(Equippable base, Slot slot, String assetId) {
		var b = Equippable.builder(slot.eq);
		if (base != null) {
			b = b.setEquipSound(base.equipSound())
				.setDispensable(base.dispensable())
				.setSwappable(base.swappable())
				.setDamageOnHurt(base.damageOnHurt())
				.setEquipOnInteract(base.equipOnInteract())
				.setCanBeSheared(base.canBeSheared());
		}
		if (assetId != null && !assetId.isEmpty()) {
			ResourceKey rawKey = ResourceKey.create(
				ResourceKey.createRegistryKey(Identifier.withDefaultNamespace("equipment_asset")),
				Identifier.parse(assetId));
			b = b.setAsset(rawKey);
		}
		return b.build();
	}

	// ---------- equip ----------

	private static int equip(CommandSourceStack src, ServerPlayer p, Slot slot) {
		ItemStack hand = p.getMainHandItem();
		if (hand.isEmpty()) return remove(src, p, slot);

		ItemStack worn = p.getItemBySlot(slot.eq);
		Identifier handModel = hand.get(DataComponents.ITEM_MODEL);
		ItemCatalog.Entry cosmetic = handModel == null ? null : ItemCatalog.findByModel(handModel.toString());

		// Catalog cosmetic over real armor: purely visual
		if (cosmetic != null) {
			if (!fitsSlot(hand, slot)) {
				say(src, "That cosmetic doesn't go in the " + slot.id + " slot.");
				return 0;
			}
			if (!isSkinned(worn) && !isRealArmor(worn, slot)) {
				say(src, "Wear a real " + slot.id + " first, then apply the cosmetic on top.");
				return 0;
			}
			hand.shrink(1);
			if (isSkinned(worn)) restoreSkin(p, worn, slot);
			applySkin(p, p.getItemBySlot(slot.eq), slot, cosmetic);
			say(src, "Cosmetic applied. Your " + slot.id + " keeps its stats and enchantments.");
			return 1;
		}

		// Plain item as a hat (helmet only, like Essentials /hat)
		if (slot == Slot.HELMET) {
			ItemStack one = hand.copyWithCount(1);
			hand.shrink(1);
			ItemStack old = worn.copy();
			p.setItemSlot(EquipmentSlot.HEAD, one);
			if (!old.isEmpty()) give(p, old);
			say(src, "Hat equipped.");
			return 1;
		}

		say(src, "Only cosmetic armor can be used on the " + slot.id + " slot.");
		return 0;
	}

	// ---------- remove ----------

	private static int remove(CommandSourceStack src, ServerPlayer p, Slot slot) {
		ItemStack worn = p.getItemBySlot(slot.eq);
		if (isSkinned(worn)) {
			restoreSkin(p, worn, slot);
			say(src, "Cosmetic removed. Your " + slot.id + " looks normal again.");
		} else if (slot == Slot.HELMET && !worn.isEmpty()) {
			ItemStack old = worn.copy();
			p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			give(p, old);
			say(src, "Hat removed.");
		} else {
			say(src, "You have no cosmetic on your " + slot.id + ".");
			return 0;
		}
		return 1;
	}

	// ---------- apply / restore ----------

	private static void applySkin(ServerPlayer p, ItemStack worn, Slot slot, ItemCatalog.Entry cosmetic) {
		ItemStack ns = worn.copy();
		CustomData cd = ns.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = cd == null ? new CompoundTag() : cd.copyTag();
		Identifier origModel = ns.get(DataComponents.ITEM_MODEL);
		Equippable eq = ns.get(DataComponents.EQUIPPABLE);
		tag.putString(K_OM, origModel == null ? "" : origModel.toString());
		tag.putString(K_OA, (eq != null && eq.assetId().isPresent()) ? eq.assetId().get().identifier().toString() : "");
		tag.putString(K_MODEL, cosmetic.model());
		ns.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		ns.set(DataComponents.ITEM_MODEL, Identifier.parse(cosmetic.model()));
		ns.set(DataComponents.EQUIPPABLE, withAsset(eq, slot, cosmetic.equipAsset())); // null asset = hat style
		p.setItemSlot(slot.eq, ns);
	}

	private static void restoreSkin(ServerPlayer p, ItemStack worn, Slot slot) {
		ItemStack ns = worn.copy();
		CustomData cd = ns.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = cd == null ? new CompoundTag() : cd.copyTag();
		String model = tag.getStringOr(K_MODEL, "");
		String origModel = tag.getStringOr(K_OM, "");
		String origAsset = tag.getStringOr(K_OA, "");
		if (!origModel.isEmpty()) ns.set(DataComponents.ITEM_MODEL, Identifier.parse(origModel));
		else ns.remove(DataComponents.ITEM_MODEL);
		ns.set(DataComponents.EQUIPPABLE, withAsset(ns.get(DataComponents.EQUIPPABLE), slot, origAsset));
		tag.remove(K_MODEL);
		tag.remove(K_OM);
		tag.remove(K_OA);
		if (tag.isEmpty()) ns.remove(DataComponents.CUSTOM_DATA);
		else ns.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		p.setItemSlot(slot.eq, ns);
		ItemCatalog.Entry e = ItemCatalog.findByModel(model);
		if (e != null) give(p, e.toStack());
	}
}
