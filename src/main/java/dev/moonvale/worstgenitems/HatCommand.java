package dev.moonvale.worstgenitems;

import com.mojang.brigadier.CommandDispatcher;
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

public final class HatCommand {
	private static final String K_MODEL = "wg_hat_model";
	private static final String K_OM = "wg_orig_model";
	private static final String K_OA = "wg_orig_asset";

	private HatCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("hat")
			.executes(ctx -> run(ctx.getSource(), ctx.getSource().getPlayerOrException(), false))
			.then(Commands.literal("remove")
				.executes(ctx -> run(ctx.getSource(), ctx.getSource().getPlayerOrException(), true))));
	}

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

	private static boolean isRealArmor(ItemStack s) {
		Equippable eq = s.get(DataComponents.EQUIPPABLE);
		return eq != null && eq.slot() == EquipmentSlot.HEAD && eq.assetId().isPresent();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Equippable headEquippable(String assetId) {
		var builder = Equippable.builder(EquipmentSlot.HEAD);
		if (assetId != null && !assetId.isEmpty()) {
			ResourceKey rawKey = ResourceKey.create(
				ResourceKey.createRegistryKey(Identifier.withDefaultNamespace("equipment_asset")),
				Identifier.parse(assetId));
			builder = builder.setAsset(rawKey);
		}
		return builder.build();
	}

	private static int run(CommandSourceStack src, ServerPlayer p, boolean remove) {
		ItemStack hand = p.getMainHandItem();
		ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);

		if (remove || hand.isEmpty()) {
			if (isSkinned(head)) {
				restoreSkin(p, head);
				say(src, "Cosmetic removed. Your helmet looks normal again.");
			} else if (!head.isEmpty()) {
				ItemStack old = head.copy();
				p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
				give(p, old);
				say(src, "Hat removed.");
			} else {
				say(src, "You are not wearing anything on your head.");
			}
			return 1;
		}

		Identifier handModel = hand.get(DataComponents.ITEM_MODEL);
		ItemCatalog.Entry cosmetic = handModel == null ? null : ItemCatalog.findByModel(handModel.toString());

		if (cosmetic != null && cosmetic.equipAsset() == null && (isSkinned(head) || isRealArmor(head))) {
			hand.shrink(1);
			if (isSkinned(head)) restoreSkin(p, head);
			applySkin(p, p.getItemBySlot(EquipmentSlot.HEAD), cosmetic.model());
			say(src, "Cosmetic applied. Your helmet keeps its protection.");
			return 1;
		}

		ItemStack one = hand.copyWithCount(1);
		hand.shrink(1);
		ItemStack old = head.copy();
		p.setItemSlot(EquipmentSlot.HEAD, one);
		if (!old.isEmpty()) give(p, old);
		say(src, "Hat equipped.");
		return 1;
	}

	private static void applySkin(ServerPlayer p, ItemStack head, String model) {
		ItemStack ns = head.copy();
		CustomData cd = ns.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = cd == null ? new CompoundTag() : cd.copyTag();
		Identifier origModel = ns.get(DataComponents.ITEM_MODEL);
		Equippable eq = ns.get(DataComponents.EQUIPPABLE);
		tag.putString(K_OM, origModel == null ? "" : origModel.toString());
		tag.putString(K_OA, (eq != null && eq.assetId().isPresent()) ? eq.assetId().get().identifier().toString() : "");
		tag.putString(K_MODEL, model);
		ns.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		ns.set(DataComponents.ITEM_MODEL, Identifier.parse(model));
		ns.set(DataComponents.EQUIPPABLE, headEquippable(null));
		p.setItemSlot(EquipmentSlot.HEAD, ns);
	}

	private static void restoreSkin(ServerPlayer p, ItemStack head) {
		ItemStack ns = head.copy();
		CustomData cd = ns.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = cd == null ? new CompoundTag() : cd.copyTag();
		String model = tag.getStringOr(K_MODEL, "");
		String origModel = tag.getStringOr(K_OM, "");
		String origAsset = tag.getStringOr(K_OA, "");
		if (!origModel.isEmpty()) ns.set(DataComponents.ITEM_MODEL, Identifier.parse(origModel));
		else ns.remove(DataComponents.ITEM_MODEL);
		ns.set(DataComponents.EQUIPPABLE, headEquippable(origAsset));
		tag.remove(K_MODEL);
		tag.remove(K_OM);
		tag.remove(K_OA);
		if (tag.isEmpty()) ns.remove(DataComponents.CUSTOM_DATA);
		else ns.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		p.setItemSlot(EquipmentSlot.HEAD, ns);
		ItemCatalog.Entry e = ItemCatalog.findByModel(model);
		if (e != null) give(p, e.toStack());
	}
}
