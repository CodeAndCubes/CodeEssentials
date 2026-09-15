package com.mrleonardos.codeessentials.platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.mrleonardos.codeessentials.internal.kits.KitSettlement;
import com.mrleonardos.codeessentials.internal.kits.WornSlots;

final class KitChests implements KitEditors {

    static final int SLOTS = 45;
    static final int SPARES = KitDefinition.SLOTS;

    private static final String SEPARATOR = ", ";

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
    public Opening edit(UUID admin, KitDefinition kit) {
        EntityPlayerMP player = Players.online(admin);
        if (player == null) {
            return Opening.OFFLINE;
        }
        ItemStack[] picture = new ItemStack[KitDefinition.SLOTS];
        List<KitItem> shown = new ArrayList<>();
        for (int slot = 0; slot < KitDefinition.SLOTS; slot++) {
            KitItem item = kit.slot(slot)
                .orElse(null);
            if (item == null) {
                continue;
            }
            ItemStack stack = KitInventories.stackOf(item);
            if (stack == null) {
                ServerChat.tell(
                    admin,
                    EssentialsMessages.ERROR_KIT_UNSHOWN_ITEM,
                    kit.name(),
                    Integer.valueOf(slot),
                    item.print());
                return Opening.UNSHOWABLE;
            }
            if (item.count() > stack.getMaxStackSize()) {
                ServerChat.tell(
                    admin,
                    EssentialsMessages.ERROR_KIT_BIG_STACK,
                    kit.name(),
                    Integer.valueOf(slot),
                    Integer.valueOf(item.count()),
                    Integer.valueOf(stack.getMaxStackSize()));
                return Opening.UNSHOWABLE;
            }
            picture[slot] = stack;
            shown.add(item);
        }
        close(admin);
        EditorInventory inventory = new EditorInventory(admin, kit.name(), shown, this::closeFired);
        for (int slot = 0; slot < KitDefinition.SLOTS; slot++) {
            inventory.setInventorySlotContents(slot, picture[slot]);
        }
        open.put(admin, inventory);
        player.displayGUIChest(inventory);
        return Opening.OPENED;
    }

    @Override
    public Optional<WornSlots> capture(UUID admin) {
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
        List<KitItem> cells = new ArrayList<>();
        List<KitItem> spares = new ArrayList<>();
        List<ItemStack> strangers = new ArrayList<>();
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack == null) {
                continue;
            }
            KitItem item = KitInventories.item(stack);
            if (item == null) {
                strangers.add(stack);
                continue;
            }
            if (slot < SPARES) {
                cells.add(item);
                builder.slot(slot, item);
            } else {
                spares.add(item);
            }
        }
        settle(admin, inventory, KitSettlement.between(inventory.shown, cells, spares), strangers);
        save(admin, builder.build());
    }

    /**
     * Свести рюкзак админа с картинкой кита. Своё, принесённое в редактор, возвращается, вынутое из
     * картинки забирается обратно: иначе редактор либо съедает предметы админа, либо печатает их из
     * записи кита.
     */
    private void settle(UUID admin, EditorInventory inventory, KitSettlement settlement, List<ItemStack> strangers) {
        EntityPlayerMP player = Players.online(admin);
        if (player == null) {
            if (!settlement.back()
                .isEmpty() || !strangers.isEmpty()) {
                log.warn(
                    "Editor of kit {} closed while {} was away, {} own records and {} unnamed stacks stayed in it",
                    inventory.title,
                    admin,
                    Integer.valueOf(
                        settlement.back()
                            .size()),
                    Integer.valueOf(strangers.size()));
            }
            return;
        }
        List<String> handed = new ArrayList<>();
        for (KitItem item : settlement.back()) {
            hand(player, KitInventories.stackOf(item));
            handed.add(item.print());
        }
        for (ItemStack stack : strangers) {
            hand(player, stack);
            handed.add(stack.getDisplayName());
            log.warn(
                "Item {} does not fit a kit record, the editor of kit {} handed it back instead of writing it down",
                stack.getDisplayName(),
                inventory.title);
        }
        List<KitItem> missing = reclaim(player, settlement.take());
        player.inventoryContainer.detectAndSendChanges();
        if (!handed.isEmpty()) {
            ServerChat.tell(admin, EssentialsMessages.KIT_EDITOR_BACK, inventory.title, join(handed));
        }
        if (!settlement.take()
            .isEmpty()) {
            ServerChat.tell(admin, EssentialsMessages.KIT_EDITOR_TAKEN, inventory.title, prints(settlement.take()));
        }
        if (!missing.isEmpty()) {
            ServerChat.tell(admin, EssentialsMessages.KIT_EDITOR_MISSING, inventory.title, prints(missing));
        }
    }

    /**
     * Забрать из рюкзака то, что админ вынул из картинки кита.
     *
     * @return чего в рюкзаке не нашлось: предмет уже выброшен, съеден или лежит в чужом сундуке
     */
    private static List<KitItem> reclaim(EntityPlayerMP player, List<KitItem> take) {
        List<KitItem> missing = new ArrayList<>();
        for (KitItem item : take) {
            int left = item.count();
            for (int slot = 0; slot < player.inventory.mainInventory.length && left > 0; slot++) {
                ItemStack stack = player.inventory.mainInventory[slot];
                KitItem held = KitInventories.item(stack);
                if (held == null || !held.sameKind(item)) {
                    continue;
                }
                int gone = Math.min(left, stack.stackSize);
                stack.stackSize -= gone;
                if (stack.stackSize <= 0) {
                    player.inventory.mainInventory[slot] = null;
                }
                left -= gone;
            }
            if (left > 0) {
                missing.add(item.withCount(left));
            }
        }
        return missing;
    }

    private static void hand(EntityPlayerMP player, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) {
            return;
        }
        if (!player.inventory.addItemStackToInventory(stack) && stack.stackSize > 0) {
            player.entityDropItem(stack, 0.5F);
        }
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

    private static String prints(List<KitItem> items) {
        List<String> lines = new ArrayList<>();
        for (KitItem item : items) {
            lines.add(item.print());
        }
        return join(lines);
    }

    private static String join(List<String> lines) {
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            if (text.length() > 0) {
                text.append(SEPARATOR);
            }
            text.append(line);
        }
        return text.toString();
    }

    private interface Closing {

        void fired(EditorInventory inventory);
    }

    private static final class EditorInventory extends InventoryBasic {

        private final UUID owner;
        private final String title;
        private final List<KitItem> shown;
        private final Closing onClose;

        EditorInventory(UUID owner, String title, List<KitItem> shown, Closing onClose) {
            super(title, true, SLOTS);
            this.owner = owner;
            this.title = title;
            this.shown = shown;
            this.onClose = onClose;
        }

        @Override
        public void closeInventory() {
            onClose.fired(this);
        }
    }
}
