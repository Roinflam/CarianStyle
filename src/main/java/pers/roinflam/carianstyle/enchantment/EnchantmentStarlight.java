package pers.roinflam.carianstyle.enchantment;

import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.block.light.HideLight;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.tileentity.MoveLight;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 星光附魔
 *
 * 护甲附魔，玩家脚下发光
 * 效果：
 * - 在玩家脚所在的方块放置隐形光源（该位置不是空气，比如有水时不亮）
 * - 亮度 = 全身星光等级之和 × 3，最高 15
 * <p>
 * 亮度写在光源方块自己的状态里，每个玩家各亮各的，换装后脚下已有的光源也会改亮度；
 * 以前亮度是 0.5 + 等级×0.1 乘 15，同一级更亮、不封顶，并且全服共用一个值。
 * 只对玩家生效（玩家 tick，仅服务端），怪物穿了不发光，客户端不自己放光源。
 * 护甲扫描结果每个玩家缓存 1 秒，换装最多晚 1 秒生效。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "starlight",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
@Mod.EventBusSubscriber
public class EnchantmentStarlight extends EnchantmentBase {

    // 护甲扫描缓存的刷新间隔（tick）
    private static final int RESCAN_INTERVAL = 20;
    // 每级星光提供的亮度
    private static final int BRIGHTNESS_PER_LEVEL = 3;
    // 亮度上限
    private static final int MAX_BRIGHTNESS = 15;
    // 放光源用的更新标志：只通知客户端，不通知邻居（2 = 发给客户端）
    private static final int LIGHT_BLOCK_FLAGS = 2;

    /** 每个玩家的护甲扫描缓存：UUID -> {亮度, 距上次扫描的 tick 数} */
    private static final Map<UUID, int[]> SCAN_CACHE = new HashMap<>();

    public EnchantmentStarlight() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 每 tick 在玩家脚下放置 / 续期一个隐形光源
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote || evt.phase != TickEvent.Phase.START) {
            return;
        }

        EntityPlayer player = evt.player;
        Enchantment starlight = EnchantmentRegistry.getEnchantmentByClass(EnchantmentStarlight.class);
        if (starlight == null) {
            return;
        }

        int brightness = brightness(player, starlight);
        if (brightness <= 0) {
            return;
        }

        World world = player.world;
        BlockPos blockPos = new BlockPos(
                MathHelper.floor(player.posX),
                MathHelper.floor(player.posY - 0.2D) + 1,
                MathHelper.floor(player.posZ));

        if (!world.isAirBlock(blockPos)) {
            return;
        }

        IBlockState lightState = CarianStyleBlocks.HIDE_LIGHT.getDefaultState().withProperty(HideLight.LEVEL, brightness);
        TileEntity tileEntity = world.getTileEntity(blockPos);
        if (tileEntity instanceof MoveLight) {
            // 同种方块改状态不会重建方块实体，脚下这格直接换亮度
            if (world.getBlockState(blockPos) != lightState) {
                world.setBlockState(blockPos, lightState, LIGHT_BLOCK_FLAGS);
            }
            ((MoveLight) tileEntity).retime();
            return;
        } else if (world.getBlockState(blockPos).getBlock() instanceof HideLight) {
            world.setBlockToAir(blockPos);
        }

        world.setBlockState(blockPos, lightState, LIGHT_BLOCK_FLAGS);
    }

    /**
     * 取缓存的亮度，到点了重新扫一遍护甲
     */
    private static int brightness(@Nonnull EntityPlayer player, @Nonnull Enchantment starlight) {
        int[] cache = SCAN_CACHE.get(player.getUniqueID());
        if (cache == null) {
            // 第一次进来立刻扫一次，而不是先按没装备处理一秒
            cache = new int[]{0, RESCAN_INTERVAL};
            SCAN_CACHE.put(player.getUniqueID(), cache);
        }
        if (++cache[1] < RESCAN_INTERVAL) {
            return cache[0];
        }
        cache[1] = 0;

        int totalLevel = 0;
        for (ItemStack armor : player.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(starlight, armor);
            }
        }
        cache[0] = Math.min(totalLevel * BRIGHTNESS_PER_LEVEL, MAX_BRIGHTNESS);
        return cache[0];
    }

    /**
     * 登出时清掉缓存条目，长期开着的服务器上不会越攒越多
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(@Nonnull PlayerEvent.PlayerLoggedOutEvent evt) {
        SCAN_CACHE.remove(evt.player.getUniqueID());
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((23 + (enchantmentLevel - 1) * 9) * ConfigLoader.enchantingDifficulty);
    }
}