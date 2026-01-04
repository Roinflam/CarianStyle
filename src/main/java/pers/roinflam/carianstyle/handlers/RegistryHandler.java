package pers.roinflam.carianstyle.handlers;

import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.potion.Potion;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import pers.roinflam.carianstyle.CarianStyle;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.*;
import pers.roinflam.carianstyle.tileentity.MoveLight;
import pers.roinflam.carianstyle.utils.IHasModel;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 注册处理器
 * <p>
 * 负责处理所有游戏内容的注册事件
 * </p>
 */
@Mod.EventBusSubscriber
public class RegistryHandler {

    /**
     * 附魔注册事件
     * <p>
     * 根据配置过滤禁用的附魔后注册
     * </p>
     */
    @SubscribeEvent
    public static void onEnchantmentRegister(@Nonnull RegistryEvent.Register<Enchantment> evt) {
        // 构建禁用附魔ID集合（使用Set提高查找效率）
        Set<String> disabledIds = new HashSet<>(Arrays.asList(ConfigLoader.uninstallEnchantment));

        // 过滤禁用的附魔
        CarianStyleEnchantments.ENCHANTMENTS.removeIf(enchantment -> {
            ResourceLocation registryName = enchantment.getRegistryName();
            if (registryName == null) {
                return false;
            }
            // 1.12.2 使用 getResourcePath() 获取路径部分
            String enchantmentId = registryName.getResourcePath();
            boolean disabled = disabledIds.contains(enchantmentId);
            if (disabled) {
                LogUtil.info("卡利亚式附魔 - 已禁用附魔：%s", enchantmentId);
            }
            return disabled;
        });

        evt.getRegistry().registerAll(CarianStyleEnchantments.ENCHANTMENTS.toArray(new Enchantment[0]));
        LogUtil.debug("卡利亚式附魔 - 附魔注册完成，共 %d 个", CarianStyleEnchantments.ENCHANTMENTS.size());
    }

    /**
     * 药水效果注册事件
     */
    @SubscribeEvent
    public static void onPotionRegister(@Nonnull RegistryEvent.Register<Potion> evt) {
        evt.getRegistry().registerAll(CarianStylePotion.POTIONS.toArray(new Potion[0]));
        LogUtil.debug("卡利亚式附魔 - 药水效果注册完成，共 %d 个", CarianStylePotion.POTIONS.size());
    }

    /**
     * 方块注册事件
     */
    @SubscribeEvent
    public static void onBlockRegister(@Nonnull RegistryEvent.Register<Block> evt) {
        evt.getRegistry().registerAll(CarianStyleBlocks.BLOCKS.toArray(new Block[0]));
        TileEntity.register(MoveLight.ID, MoveLight.class);
        LogUtil.debug("卡利亚式附魔 - 方块注册完成，共 %d 个", CarianStyleBlocks.BLOCKS.size());
    }

    /**
     * 实体注册事件
     */
    @SubscribeEvent
    public static void onEntityEntryRegister(@Nonnull RegistryEvent.Register<EntityEntry> evt) {
        evt.getRegistry().registerAll(CarianStyleEntity.ENTITY_ENTRIES.toArray(new EntityEntry[0]));
        LogUtil.debug("卡利亚式附魔 - 实体注册完成，共 %d 个", CarianStyleEntity.ENTITY_ENTRIES.size());
    }

    /**
     * 物品注册事件
     */
    @SubscribeEvent
    public static void onItemRegister(@Nonnull RegistryEvent.Register<Item> evt) {
        evt.getRegistry().registerAll(CarianStyleItem.ITEMS.toArray(new Item[0]));
        LogUtil.debug("卡利亚式附魔 - 物品注册完成，共 %d 个", CarianStyleItem.ITEMS.size());
    }

    /**
     * 模型注册事件（客户端）
     */
    @SubscribeEvent
    public static void onModelRegister(ModelRegistryEvent evt) {
        for (Item item : CarianStyleItem.ITEMS) {
            if (item instanceof IHasModel) {
                CarianStyle.proxy.registerItemRenderer(item, 0, "inventory");
            }
        }
    }
}