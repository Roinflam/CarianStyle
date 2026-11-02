package pers.roinflam.carianstyle.enchantment;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E4)
 *
 * 岩石爆破附魔
 * <p>
 * 镐子附魔，BFS 连通范围挖掘
 * 挖掘方块时：
 * - 从破坏点向外 BFS 扩散，仅破坏与之六面相邻连通的同类型方块
 * - 范围半径 = 等级（受配置上限限制）
 * - 耐久只剩 1 点时拒绝挖掘，连带挖给中心那一格留够耐久，镐不会被挖坏
 * </p>
 *
 * <h3>v3.2 修复：附魔类型由 DIGGER 收窄为 PICKAXE</h3>
 * <p>
 * <b>问题：</b>原来注解与构造函数都用的是原版 {@code EnchantmentCategory.DIGGER}，
 * 而 DIGGER 的判定是 {@code item instanceof DiggerItem}——
 * 它同时包含<b>镐、斧、锹、锄</b>四类工具。
 * </p>
 * <p>
 * 但本附魔的实际逻辑在 {@link #onBreak} 里明确要求
 * {@code tool.getItem() instanceof PickaxeItem}，也就是<b>只有镐子才会生效</b>。
 * 于是玩家用附魔台附斧头 / 锹 / 锄时，岩石爆破照样会出现在候选池里并被刷出来，
 * 附上去之后却毫无效果，在玩家看来就是「工具附魔串了、刷出来一堆没用的」。
 * </p>
 * <p>
 * <b>修复：</b>改用 {@code CarianStyleEnchantments} 中定义的自定义类型
 * {@code "PICKAXE"}，让候选池与实际生效条件一致。
 * 注解的 {@code type} 参数随之替换为 {@code customType}，两边保持同步。
 * </p>
 * <p>
 * <b>兼容性：</b>已经附在斧 / 锹 / 锄上的旧存档物品不会被自动清除，
 * 但它们本来就没有任何效果；铁砧与附魔台之后不会再把本附魔加到这些工具上。
 * </p>
 *
 * @author RoinFlam
 * @version 3.2
 */
@Deprecated
public class EnchantmentRockBlaster extends EnchantmentBase {
    private static final String VALUE_ID = "rock_blaster";
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final ThreadLocal<Set<Long>> processingBlocks = ThreadLocal.withInitial(HashSet::new);
    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};
    private static final Set<String> COMMON_STONE_IDS = Set.of(
            "minecraft:stone",
            "minecraft:cobblestone",
            "minecraft:deepslate",
            "minecraft:cobbled_deepslate",
            "minecraft:granite",
            "minecraft:diorite",
            "minecraft:andesite",
            "minecraft:tuff",
            "minecraft:calcite",
            "minecraft:smooth_basalt",
            "minecraft:gravel",
            "minecraft:netherrack",
            "minecraft:basalt",
            "minecraft:blackstone",
            "minecraft:end_stone"
    );
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return false;
    }
}

