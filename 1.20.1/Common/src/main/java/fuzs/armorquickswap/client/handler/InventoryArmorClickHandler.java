package fuzs.armorquickswap.client.handler;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.InputConstants;
import fuzs.puzzleslib.api.client.screen.v2.ScreenHelper;
import fuzs.puzzleslib.api.event.v1.core.EventResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

public class InventoryArmorClickHandler {
    private static final Map<Class<? extends Slot>, UnaryOperator<Slot>> SLOT_CLAZZ_METHOD_HANDLES = Maps.newIdentityHashMap();

    public static EventResult onBeforeMouseClick(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {

        if (button != InputConstants.MOUSE_BUTTON_RIGHT) return EventResult.PASS;

        Slot hoveredSlot = ScreenHelper.INSTANCE.findSlot(screen, mouseX, mouseY);

        if (hoveredSlot != null) {
            ItemStack itemStack = hoveredSlot.getItem();
            Equipable equipable = Equipable.get(itemStack);

            if (equipable != null && !itemStack.isStackable()) {

                Minecraft minecraft = ScreenHelper.INSTANCE.getMinecraft(screen);
                Inventory inventory = minecraft.player.getInventory();

                hoveredSlot = findNestedSlot(hoveredSlot);
                if (hoveredSlot.container != inventory) return EventResult.PASS;

                Slot armorSlot = LocalArmorStandGearHandler.findInventorySlot(screen.getMenu(), equipable.getEquipmentSlot().getIndex(inventory.items.size()));
                if (armorSlot == null) return EventResult.PASS;

                if (!ItemStack.isSameItemSameTags(hoveredSlot.getItem(), armorSlot.getItem())) {

                    if (minecraft.gameMode.hasInfiniteItems()) {

                        performCreativeItemSwap(minecraft.player, hoveredSlot, armorSlot);
                    } else {

                        minecraft.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, armorSlot.index, hoveredSlot.getContainerSlot(), ClickType.SWAP, minecraft.player);
                    }
                }

                return EventResult.INTERRUPT;
            }
        }

        return EventResult.PASS;
    }

    private static void performCreativeItemSwap(Player player, Slot hoveredSlot, Slot armorSlot) {
        ItemStack hoveredItem = hoveredSlot.getItem();
        ItemStack armorItem = armorSlot.getItem();
        player.getInventory().setItem(hoveredSlot.getContainerSlot(), armorItem.copy());
        player.getInventory().setItem(armorSlot.getContainerSlot(), hoveredItem.copy());
        player.inventoryMenu.broadcastChanges();
    }

    public static Slot findNestedSlot(Slot slot) {
        return findNestedSlot(slot, 5);
    }

    private static Slot findNestedSlot(Slot slot, int searchDepth) {
        Objects.requireNonNull(slot, "slot is null");
        slot = SLOT_CLAZZ_METHOD_HANDLES.computeIfAbsent(slot.getClass(), clazz -> findNestedSlot(clazz, searchDepth)).apply(slot);
        Objects.requireNonNull(slot, "slot is null");
        return slot;
    }

    private static UnaryOperator<Slot> findNestedSlot(Class<? extends Slot> clazz, int searchDepth) {
        // the creative mode screen wraps slots, but in a terrible way where not all properties of the original are forwarded, mainly public fields,
        // so we use this general approach in case another mod has a similar idea and just search for a wrapped slot inside every slot instance
        // the resulting method handle is cached, so this isn't really a burden (not that the code runs often anyway)
        if (searchDepth >= 0 && clazz != Slot.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Slot.class.isAssignableFrom(field.getType()) && !clazz.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        MethodHandle methodHandle = MethodHandles.lookup().unreflectGetter(field);
                        return innerSlot -> {
                            try {
                                return findNestedSlot((Slot) methodHandle.invoke(innerSlot), searchDepth - 1);
                            } catch (Throwable e) {
                                throw new RuntimeException(e);
                            }
                        };
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return UnaryOperator.identity();
    }
}
