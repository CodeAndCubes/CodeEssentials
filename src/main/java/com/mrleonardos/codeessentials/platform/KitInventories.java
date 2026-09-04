package com.mrleonardos.codeessentials.platform;

import java.util.Optional;
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

final class KitInventories implements KitHands {

    private final Logger log;

    KitInventories(Logger log) {
        this.log = log;
    }

    @Override
    public Optional<KitItem[]> worn(UUID player) {
        EntityPlayerMP online = Players.online(player);
        if (online == null) {
            return Optional.empty();
        }
        KitItem[] slots = new KitItem[KitDefinition.SLOTS];
        for (int slot = 0; slot < KitDefinition.INVENTORY_SLOTS; slot++) {
            slots[slot] = item(online.inventory.mainInventory[slot]);
        }
        for (int slot = 0; slot < KitDefinition.ARMOR_SLOTS; slot++) {
            slots[KitDefinition.INVENTORY_SLOTS + slot] = item(online.inventory.armorInventory[slot]);
        }
        return Optional.of(slots);
    }

    @Override
    public boolean dress(UUID player, KitItem[] slots) {
        EntityPlayerMP online = Players.online(player);
        if (online == null) {
            return false;
        }
        for (int slot = 0; slot < KitDefinition.INVENTORY_SLOTS; slot++) {
            ItemStack stack = stackOf(slots[slot]);
            if (slots[slot] != null && stack == null) {
                log.warn(
                    "Item {} of {} is unknown to the registry and stays in the buffer",
                    slots[slot].print(),
                    player);
                return false;
            }
            online.inventory.mainInventory[slot] = stack;
        }
        for (int slot = 0; slot < KitDefinition.ARMOR_SLOTS; slot++) {
            int index = KitDefinition.INVENTORY_SLOTS + slot;
            ItemStack stack = stackOf(slots[index]);
            if (slots[index] != null && stack == null) {
                log.warn(
                    "Item {} of {} is unknown to the registry and stays in the buffer",
                    slots[index].print(),
                    player);
                return false;
            }
            online.inventory.armorInventory[slot] = stack;
        }
        online.inventoryContainer.detectAndSendChanges();
        return true;
    }

    @Override
    public KitStacking stacking() {
        return new KitRules();
    }

    static KitItem item(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        String id = Item.itemRegistry.getNameForObject(stack.getItem());
        if (id == null) {
            return null;
        }
        String tags = stack.getTagCompound() == null ? ""
            : stack.getTagCompound()
                .toString();
        return KitItem.of(id, stack.stackSize, stack.getItemDamage(), tags);
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
