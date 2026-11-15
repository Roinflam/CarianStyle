package pers.roinflam.carianstyle.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.nio.file.Path;

/**
 * 两端都要用的平台能力：common 里的代码需要加载器提供的东西时只经这里取，不直接 import 加载器的类。
 * <p>
 * 实现由各平台模块在 {@code META-INF/services/pers.roinflam.carianstyle.platform.Platform} 里登记，
 * 用 {@link Services#platform()} 取。只放「common 调平台」的方法；平台调 common 的（入口、配置登记、
 * 挂数据包、事件转发）由平台直接调 common 的静态方法，不进接口。
 * </p>
 */
public interface Platform {

    /** 当前进程是不是客户端（单人游戏的内置服务端也算客户端进程）。 */
    boolean isClient();

    /** 是否开发环境（开发时才做的自检用它判断，正式包里一律 false）。 */
    boolean isDevelopment();

    /** 配置目录（游戏目录下的 {@code config}），本模组自己读写的 JSON 放这里。 */
    Path configDir();

    /** 是不是加载器自己的假玩家（假玩家没有真连接，不发包、不算真人）。 */
    boolean isFakePlayer(ServerPlayer player);

    /**
     * 多部位生物的某个部位所属的本体；不是部位就返回 null。
     * 命中末影龙、下界合金巨兽这类生物时，伤害要转给本体才算数。
     */
    Entity partParent(Entity entity);

    /** 实体上跟着存档走的自定义数据，本模组只在里面放自己的键。 */
    CompoundTag persistentData(Entity entity);

    /**
     * 以玩家身份问一格方块能不能破坏，不真的破坏。
     * 领地、保护类模组在这一步拒绝就返回 false。
     */
    boolean probeBlockBreak(Level level, BlockPos pos, BlockState state, Player player);

    /**
     * 按「玩家破坏」移除一格方块，返回是否真的移除了。
     * <p>
     * <b>不含 {@code playerWillDestroy}</b>：破坏前的那一步（粒子、音效、方块自己的善后）由调用方先调原版的
     * {@code Block#playerWillDestroy}，两个平台一样；这里只做真正移除那一步（Forge 上是 {@code onDestroyedByPlayer}，
     * 1.20.1 Forge 的默认实现里还会再调一次 {@code playerWillDestroy}，以前就是这样，没改）。
     * 移除后的 {@code destroy}、掉落也由调用方照旧做。
     * </p>
     *
     * @param willHarvest 之后还要不要掉落（抑制掉落时传 false）
     * @param fluid       这一格原来的流体，移除后要留下的就是它
     */
    boolean destroyByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                            boolean willHarvest, FluidState fluid);

    /**
     * 碎岩连带挖的一格掉完落物后补放经验（中心那一格不调）。
     * <p>
     * Forge 上 {@code playerDestroy} 不放经验，这里按 {@code getExpDrop} 放一半（精准采集按 0 算），和以前一样；
     * Fabric 上原版 {@code playerDestroy} 已经放过全额，减半由 {@code popExperience} 的 mixin 按标记做，这里什么也不做。
     * 只有 1.20.1 有这个方法，1.21.1 两个平台都走 {@code BlockDropsEvent}。
     * </p>
     */
    void popChainBreakExperience(BlockState state, ServerLevel level, BlockPos pos, int fortuneLevel);

    /** 左边的物品收不收这本附魔书（铁砧合书时问）。Forge 调物品自己的判断，Fabric 一律收。 */
    boolean isBookEnchantable(ItemStack stack, ItemStack book);

    /** 物品的附魔能力。Forge 调按物品堆的版本（模组物品可以覆写），Fabric 读物品上的值。 */
    int getEnchantmentValue(ItemStack stack);

    /**
     * 自定义附魔类型挂到 {@code Enchantment.category} 上的值，同一类型每次返回同一个对象。
     * <p>
     * Forge 上是按 {@link CustomEnchantCategory#hostName()} 扩展出来的枚举常量，判定就是这个类型自己的；
     * Fabric 上原版枚举扩展不了，一律给 {@code BREAKABLE}（三种类型都是可损耗物品的子集），
     * 真正的判定由本模组附魔的 {@code canApplyAtEnchantingTable} 做。
     * </p>
     */
    EnchantmentCategory hostCategory(CustomEnchantCategory type);

    /**
     * 本模组附魔在不另加条件时，附魔台怎么判这件物品。
     * <p>
     * Forge 照补丁里的默认实现交给物品判断（别的模组的物品可以覆写），{@code custom} 不用看：
     * {@code category} 字段已经是扩展出来的枚举。Fabric 上 {@code custom} 不为 null 就按它判，否则按 {@code category}。
     * </p>
     *
     * @param custom 附魔注解声明的自定义类型，没有就是 null
     */
    boolean defaultTableCheck(Enchantment enchantment, ItemStack stack, CustomEnchantCategory custom);

    /**
     * 任意附魔（包括原版和别的模组的）在附魔台上能不能出现在这件物品上。
     * Forge 调附魔自己的同名方法，别的模组覆写的照旧算；Fabric 对其余附魔按 {@code category} 判。
     */
    boolean canApplyAtEnchantingTable(Enchantment enchantment, ItemStack stack);

    /** 任意附魔能不能出现在附魔台的书上。Forge 调附魔自己的同名方法，Fabric 上其余附魔一律可以。 */
    boolean isAllowedOnBooks(Enchantment enchantment);
}
