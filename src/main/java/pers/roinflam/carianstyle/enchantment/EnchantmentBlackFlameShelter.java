package pers.roinflam.carianstyle.enchantment;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;

/**
 * PORT-STUB(E3)
 *
 * 黑焰庇护附魔
 * <p>v2.1：LivingDamage受击+LivingHeal受治疗入口接入怪物附魔触发开关</p>
 *
 * <h3>v2.2：加 {@value #MAX_EFFECTIVE_LEVEL} 级硬上限，堵住「挨打变回血」</h3>
 *
 * <h4>原来会发生什么</h4>
 * <p>
 * 减伤是 {@code amount * (1 - totalLevel * 0.125f)}，也就是每级 12.5%。
 * 累加等级到 <b>8 级系数正好归零</b>（完全免疫），<b>9 级及以上系数为负</b>。
 * </p>
 * <p>
 * 负系数不是「减伤超过 100% 所以按 0 算」——1.20.1 的
 * {@code LivingEntity.actuallyHurt} 里那句判断是 {@code if (pDamageAmount != 0.0F)}，
 * <b>不是 {@code > 0}</b>，所以负值会一路走到 {@code setHealth(health - 负数)}，
 * 结果是<b>挨打反而加血</b>。这不是数值失衡，是符号翻转。
 * </p>
 *
 * <h4>正常途径够不着，但这个模组明确允许超上限</h4>
 * <p>
 * 本附魔是 {@code RARE}（{@link EnchantmentRarity} 表里 maxLevel = 3）且注册为
 * <b>胸甲专属</b>，附魔台与铁砧的上限就是 3 级，减伤 37.5%。
 * </p>
 * <p>
 * 但 {@code ConfigLoader.levelLimit} 这个配置项的存在本身就说明等级会超上限——
 * 它的说明写的是「为某些附魔在超过等级上限后设置限制」，而且<b>默认是关的</b>。
 * 指令发放、NBT 改级、服务器自己的附魔发放系统，任何一条路给出 9 级以上，
 * 这个符号翻转就是实打实会跑到的，而且默认没有任何东西拦着。
 * </p>
 *
 * <h4>为什么是 7 而不是 8</h4>
 * <p>
 * 8 级是系数归零、完全免疫。<b>「完全免疫物理伤害」本身就已经是个坏状态</b>——
 * 它不会导致符号翻转，但会让这件胸甲变成无敌装。卡在 7 级（减伤 87.5%）
 * 是「离出问题最近但仍然正常」的那一级：伤害恒为正数，效果依然极强，
 * 但打得死。
 * </p>
 *
 * <h4>为什么硬上限与 levelLimit 并存</h4>
 * <p>
 * 两者管的是不同的事，<b>不能互相替代</b>：
 * </p>
 * <ul>
 *     <li>{@code ConfigLoader.levelLimit} 是<b>平衡</b>开关，服主可以关掉，
 *         全模组统一口径钳到 10 级；</li>
 *     <li>{@link #MAX_EFFECTIVE_LEVEL} 是<b>正确性</b>下限，
 *         防的是符号翻转这种「怎么配都不该发生」的事，因此<b>无条件生效</b>、
 *         不看任何配置。</li>
 * </ul>
 * <p>
 * 现在 7 &lt; 10，levelLimit 那一段在本附魔上确实不会再改变结果。
 * <b>刻意保留</b>：一是与模组里其它附魔的写法保持一致，二是万一将来调了
 * 每级 12.5% 这个系数、硬上限跟着上移，那段就又有意义了。
 * 删掉它只省一行，却把一个配置项的语义从这个类里抹掉了。
 * </p>
 *
 * <h4>本次<b>没有</b>动治疗削减那一侧</h4>
 * <p>
 * 治疗削减是 {@code 1 - totalLevel * 0.25f}，每级 25%——<b>4 级就归零</b>。
 * 但它不会符号翻转：{@code LivingEntity.heal} 里是 {@code if (pHealAmount <= 0) return;}，
 * 负值只是不回血，不会掉血。
 * </p>
 * <p>
 * 也就是说 7 级上限<b>救不了这一侧</b>——4、5、6、7 级的表现完全一样，都是
 * 「一滴都回不了」。这是个平衡问题而非正确性问题，改它要动数值设计，
 * 不在本次「防止符号翻转」的范围内，故保持原样。
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "black_flame_shelter",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST},
        conflictsWith = {
                EnchantmentShelterOfFire.class,
                EnchantmentHealingByFire.class
        },
        baseEnchantability = 20,
        levelMultiplier = 15
)
@Deprecated
public class EnchantmentBlackFlameShelter extends EnchantmentBase {
    private static final String VALUE_ID = "black_flame_shelter";
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle MELEE_REDUCTION_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "melee_reduction_per_level",
                    0.125D, 0.0D, 0.5D);
    private static final EnchantmentValues.Handle PROJECTILE_REDUCTION_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "projectile_reduction_per_level",
                    0.25D, 0.0D, 0.5D);
    public static final int MAX_EFFECTIVE_LEVEL = 7;
    public EnchantmentBlackFlameShelter() {
        super(EnchantTarget.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }
    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        if (ench.is(Enchantments.PROTECTION)) {
            return false;
        }
        return super.checkCompatibility(ench);
    }
}

