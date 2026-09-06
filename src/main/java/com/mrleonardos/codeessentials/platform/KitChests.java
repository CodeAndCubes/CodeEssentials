package com.mrleonardos.codeessentials.platform;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.platform.Players;
import com.mrleonardos.codeessentials.api.manage.KitService;
import com.mrleonardos.codeessentials.api.model.KitDefinition;
import com.mrleonardos.codeessentials.api.model.KitItem;
import com.mrleonardos.codeessentials.api.store.StoreResult;
import com.mrleonardos.codeessentials.internal.command.EssentialsMessages;
import com.mrleonardos.codeessentials.internal.command.KitEditors;

final class KitChests implements KitEditors {

    static final int SLOTS = 45;
    static final int SPARES = KitDefinition.SLOTS;

    private final Supplier<KitService> kits;
    private final NameResolver names;
    private final KitInventories inventories;
    private final Map<UUID, EditorInventory> open = new LinkedHashMap<>();
    private final Logger log;

    KitChests(Supplier<KitService> kits, NameResolver names, Logger log) {
        this.kits = kits;
        this.names = names;
        this.inventories = new KitInventories(log);
        this.log = log;
    }

    @Override
    public boolean edit(UUID admin, KitDefinition kit) {
        EntityPlayerMP player = Players.online(admin);
        if (player == null) {
            return false;
        }
        close(admin);
        EditorInventory inventory = new EditorInventory(admin, kit.name(), this::closeFired);
        for (int slot = 0; slot < KitDefinition.SLOTS; slot++) {
            inventory.setInventorySlotContents(
                slot,
                KitInventories.stackOf(
                    kit.slot(slot)
                        .orElse(null)));
        }
        open.put(admin, inventory);
        player.displayGUIChest(inventory);
        return true;
    }

    @Override
    public Optional<KitItem[]> capture(UUID admin) {
        return inventories.worn(admin);
    }

    @Override
    public void closeAll() {
        while (!open.isEmpty()) {
            close(
                open.keySet()
                    .iterator()
                    .next());
        }
    }

    /**
     * Закрытие пришло от самой игры. Ванильный {@code displayGUIChest} закрывает прежний контейнер уже
     * после того, как новый редактор лёг в карту, поэтому опоздавший вызов обязан назвать себя: иначе он
     * выбросит чужой редактор и правки в нём сохранять станет некуда.
     */
    private void closeFired(EditorInventory inventory) {
        if (open.get(inventory.owner) == inventory) {
            close(inventory.owner);
        }
    }

    private void close(UUID admin) {
        EditorInventory inventory = open.remove(admin);
        if (inventory == null) {
            return;
        }
        KitDefinition.Builder builder = KitDefinition.named(inventory.title);
        for (int slot = 0; slot < KitDefinition.SLOTS; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            KitItem item = KitInventories.item(stack);
            if (stack != null && item == null) {
                log.warn(
                    "Item in slot {} of kit {} has no registry name and was dropped from the editor",
                    Integer.valueOf(slot),
                    inventory.title);
                continue;
            }
            if (item != null) {
                builder.slot(slot, item);
            }
        }
        EntityPlayerMP player = Players.online(admin);
        if (player != null) {
            giveBack(player, inventory);
        }
        save(admin, builder.build());
    }

    private void save(UUID admin, KitDefinition kit) {
        String actor = names.name(admin)
            .orElseGet(() -> String.valueOf(admin));
        StoreResult written = kits.get()
            .define(kit, actor);
        if (written.successful()) {
            ServerChat.tell(admin, EssentialsMessages.KIT_SAVED, kit.name(), Integer.valueOf(kit.size()));
            return;
        }
        if (written.failure()
            .orElse(null) == StoreResult.Failure.INVALID_VALUE) {
            ServerChat.tell(admin, EssentialsMessages.ERROR_KIT_EMPTY, kit.name());
            return;
        }
        ServerChat.tell(
            admin,
            EssentialsMessages.failureKey(written),
            kit.name(),
            written.message()
                .orElse(""));
    }

    private static void giveBack(EntityPlayerMP player, EditorInventory inventory) {
        for (int slot = SPARES; slot < SLOTS; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack == null) {
                continue;
            }
            inventory.setInventorySlotContents(slot, null);
            if (!player.inventory.addItemStackToInventory(stack)) {
                player.entityDropItem(stack, 0.5F);
            }
        }
    }

    private interface Closing {

        void fired(EditorInventory inventory);
    }

    private static final class EditorInventory extends InventoryBasic {

        private final UUID owner;
        private final String title;
        private final Closing onClose;

        EditorInventory(UUID owner, String title, Closing onClose) {
            super(title, true, SLOTS);
            this.owner = owner;
            this.title = title;
            this.onClose = onClose;
        }

        @Override
        public void closeInventory() {
            onClose.fired(this);
        }
    }
}
