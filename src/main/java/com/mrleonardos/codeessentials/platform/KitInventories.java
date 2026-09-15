package com.mrleonardos.codeessentials.platform;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.platform.Players;
import com.mrleonardos.codeessentials.api.model.KitDefinition;
import com.mrleonardos.codeessentials.api.model.KitItem;
import com.mrleonardos.codeessentials.internal.kits.KitHands;
import com.mrleonardos.codeessentials.internal.kits.KitStacking;
import com.mrleonardos.codeessentials.internal.kits.WornSlots;

final class KitInventories implements KitHands {

    private final Logger log;

    KitInventories(Logger log) {
        this.log = log;
    }

    @Override
    public Optional<WornSlots> worn(UUID player) {
        EntityPlayerMP online = Players.online(player);
        if (online == null) {
            return Optional.empty();
        }
        KitItem[] slots = new KitItem[KitDefinition.SLOTS];
        boolean[] unrecorded = new boolean[KitDefinition.SLOTS];
        for (int slot = 0; slot < KitDefinition.INVENTORY_SLOTS; slot++) {
            slots[slot] = item(online.inventory.mainInventory[slot]);
            unrecorded[slot] = unrecordable(online.inventory.mainInventory[slot]);
        }
        for (int slot = 0; slot < KitDefinition.ARMOR_SLOTS; slot++) {
            int index = KitDefinition.INVENTORY_SLOTS + slot;
            slots[index] = item(online.inventory.armorInventory[slot]);
            unrecorded[index] = unrecordable(online.inventory.armorInventory[slot]);
        }
        return Optional.of(WornSlots.of(slots, unrecorded));
    }

    @Override
    public boolean dress(UUID player, KitItem[] slots, Set<Integer> untouched) {
        EntityPlayerMP online = Players.online(player);
        if (online == null) {
            return false;
        }
        ItemStack[] main = new ItemStack[KitDefinition.INVENTORY_SLOTS];
        ItemStack[] armor = new ItemStack[KitDefinition.ARMOR_SLOTS];
        for (int slot = 0; slot < KitDefinition.INVENTORY_SLOTS; slot++) {
            if (untouched.contains(Integer.valueOf(slot))) {
                continue;
            }
            ItemStack stack = stackOf(slots[slot]);
            if (slots[slot] != null && stack == null) {
                log.warn(
                    "Item {} of {} is unknown to the registry and stays in the buffer",
                    slots[slot].print(),
                    player);
                return false;
            }
            main[slot] = stack;
        }
        for (int slot = 0; slot < KitDefinition.ARMOR_SLOTS; slot++) {
            int index = KitDefinition.INVENTORY_SLOTS + slot;
            if (untouched.contains(Integer.valueOf(index))) {
                continue;
            }
            ItemStack stack = stackOf(slots[index]);
            if (slots[index] != null && stack == null) {
                log.warn(
                    "Item {} of {} is unknown to the registry and stays in the buffer",
                    slots[index].print(),
                    player);
                return false;
            }
            armor[slot] = stack;
        }
        for (int slot = 0; slot < KitDefinition.INVENTORY_SLOTS; slot++) {
            if (!untouched.contains(Integer.valueOf(slot))) {
                online.inventory.mainInventory[slot] = main[slot];
            }
        }
        for (int slot = 0; slot < KitDefinition.ARMOR_SLOTS; slot++) {
            int index = KitDefinition.INVENTORY_SLOTS + slot;
            if (!untouched.contains(Integer.valueOf(index))) {
                online.inventory.armorInventory[slot] = armor[slot];
            }
        }
        online.inventoryContainer.detectAndSendChanges();
        return true;
    }

    @Override
    public KitStacking stacking() {
        return new KitRules();
    }

    /**
     * Перевести стек в запись кита.
     *
     * @return пустая ссылка, когда записать нечего: реестр не знает предмет, или его имя, урон и число
     *         не проходят границы записи. Чужой мод волен назвать предмет как угодно, и падение на
     *         закрытии редактора стоило бы админу всей правки
     */
    static KitItem item(ItemStack stack) {
        if (!present(stack)) {
            return null;
        }
        String id = Item.itemRegistry.getNameForObject(stack.getItem());
        if (id == null) {
            return null;
        }
        String tags = stack.getTagCompound() == null ? ""
            : stack.getTagCompound()
                .toString();
        try {
            return KitItem.of(id, stack.stackSize, stack.getItemDamage(), tags);
        } catch (IllegalArgumentException outOfBounds) {
            return null;
        }
    }

    /**
     * Занят ли слот предметом, который запись кита выразить не может.
     *
     * @return ложь для пустого слота: пустота выражается пустой записью
     */
    static boolean unrecordable(ItemStack stack) {
        return present(stack) && item(stack) == null;
    }

    private static boolean present(ItemStack stack) {
        return stack != null && stack.getItem() != null && stack.stackSize >= KitItem.MIN_COUNT;
    }

    static ItemStack stackOf(KitItem item) {
        if (item == null) {
            return null;
        }
        Item raw = (Item) Item.itemRegistry.getObject(item.id());
        if (raw == null) {
            return null;
        }
        ItemStack stack = new ItemStack(raw, item.count(), item.damage());
        if (!item.nbt()
            .isEmpty()) {
            NBTBase parsed;
            try {
                parsed = JsonToNBT.func_150315_a(item.nbt());
            } catch (NBTException malformed) {
                return null;
            }
            if (!(parsed instanceof NBTTagCompound)) {
                return null;
            }
            stack.setTagCompound((NBTTagCompound) parsed);
        }
        return stack;
    }

    private static final class KitRules implements KitStacking {

        @Override
        public int limit(KitItem item) {
            Item raw = (Item) Item.itemRegistry.getObject(item.id());
            return raw == null ? 1 : raw.getItemStackLimit(stackOf(item));
        }

        @Override
        public boolean merges(KitItem held, KitItem added) {
            if (!held.sameKind(added)) {
                return false;
            }
            Item raw = (Item) Item.itemRegistry.getObject(held.id());
            if (raw == null) {
                return false;
            }
            return raw.getItemStackLimit(stackOf(held)) > 1 && (!raw.isDamageable() || held.damage() == 0);
        }

        @Override
        public boolean accepts(int slot, KitItem item) {
            ItemStack stack = stackOf(item);
            if (stack == null) {
                return false;
            }
            if (slot < KitDefinition.INVENTORY_SLOTS) {
                return true;
            }
            int armorType = KitDefinition.ARMOR_SLOTS - 1 - (slot - KitDefinition.INVENTORY_SLOTS);
            return stack.getItem()
                .isValidArmor(stack, armorType, null);
        }
    }
}
