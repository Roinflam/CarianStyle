package pers.roinflam.carianstyle.mc112.hook;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockableLoot;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import pers.roinflam.carianstyle.base.enchantment.DisabledEnchantmentDropFilter;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 刚生成的战利品里清掉被禁用的附魔（对应 1.20.1 的 {@code isDiscoverable}）
 * <p>
 * 1.12.2 的 {@code enchant_randomly} 给书和装备抽附魔时不看任何开关，禁用的附魔会出现在战利品箱里。
 * 战利品是玩家第一次打开箱子时才生成的（{@code fillWithLoot}），这里在那一刻清：
 * </p>
 * <ol>
 *   <li>{@code PlayerInteractEvent.RightClickBlock}（服务端）：点的是还没生成战利品的 {@code TileEntityLockableLoot}
 *       （{@code getLootTable() != null}），记下这个玩家和位置；</li>
 *   <li>紧接着的 {@code PlayerContainerEvent.Open}：那个位置的战利品已经生成（{@code getLootTable()} 变成 null），
 *       就把这个容器里非玩家背包的栏位过一遍 {@link DisabledEnchantmentDropFilter#withoutDisabledEnchantments}。</li>
 * </ol>
 * <p>
 * 只清刚生成的战利品，不动玩家自己存的东西。漏斗抽战利品箱、运输矿车箱子这两种少见的来路不处理。
 * 本类调用 1.20.1 同名类 {@code DisabledEnchantmentDropFilter} 的公开静态方法清理物品。
 * </p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class DisabledLootCleaner {

    /** 玩家 → 刚右键的、还没生成战利品的箱子位置 */
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    /** 右键之后多少 tick 内打开界面才算数 */
    private static final int PENDING_TICKS = 20;

    private DisabledLootCleaner() {
    }

    private static final class Pending {
        final int dimension;
        final BlockPos pos;
        final long worldTime;

        Pending(int dimension, BlockPos pos, long worldTime) {
            this.dimension = dimension;
            this.pos = pos;
            this.worldTime = worldTime;
        }
    }

    private static boolean hasBlacklist() {
        String[] blacklist = ConfigLoader.uninstallEnchantment;
        return blacklist != null && blacklist.length > 0;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(@Nonnull PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote || !hasBlacklist()) {
            return;
        }
        TileEntity tile = player.world.getTileEntity(event.getPos());
        if (tile instanceof TileEntityLockableLoot && ((TileEntityLockableLoot) tile).getLootTable() != null) {
            PENDING.put(player.getUniqueID(), new Pending(player.world.provider.getDimension(),
                    event.getPos().toImmutable(), player.world.getTotalWorldTime()));
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(@Nonnull PlayerContainerEvent.Open event) {
        EntityPlayer player = event.getEntityPlayer();
        if (player.world.isRemote) {
            return;
        }
        Pending pending = PENDING.remove(player.getUniqueID());
        if (pending == null || !hasBlacklist()
                || pending.dimension != player.world.provider.getDimension()
                || player.world.getTotalWorldTime() - pending.worldTime > PENDING_TICKS) {
            return;
        }
        TileEntity tile = player.world.getTileEntity(pending.pos);
        if (!(tile instanceof TileEntityLockableLoot) || ((TileEntityLockableLoot) tile).getLootTable() != null) {
            // 战利品没在这次打开时生成，不是刚生成的东西
            return;
        }
        Container container = event.getContainer();
        for (Slot slot : container.inventorySlots) {
            if (slot.inventory == player.inventory) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || !stack.hasTagCompound()) {
                continue;
            }
            ItemStack cleaned = DisabledEnchantmentDropFilter.withoutDisabledEnchantments(stack);
            if (cleaned != stack) {
                slot.putStack(cleaned);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(@Nonnull PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.player.getUniqueID());
    }
}
