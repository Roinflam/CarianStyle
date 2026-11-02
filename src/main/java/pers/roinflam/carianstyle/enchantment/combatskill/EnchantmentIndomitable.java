package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
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
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E1)
 *
 * 不屈附魔
 * <p>v2.1：受击者视角接入怪物附魔触发开关</p>
 * <p>v2.2：免疫成功时触发「不屈壁障」自绘特效</p>
 *
 * <h3>v2.2 为什么要加这个特效</h3>
 * <p>
 * 本附魔的免疫概率是 {@code 缺失血量百分比 × 75%}——残血时接近必定免疫，
 * 但在此之前<b>玩家完全看不出它有没有生效</b>：血条没动，可能是免疫了，
 * 也可能是对方压根没打中，还可能是别的减伤挡下来了。
 * 一个只在真正免疫时出现的视觉反馈，补的正是这个缺口。
 * </p>
 * <p>
 * <b>严格放在 {@code setCanceled(true)} 的同一分支里</b>：概率没中的时候不能播，
 * 否则视觉就在骗人，比没有反馈更糟。
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@Deprecated
public class EnchantmentIndomitable extends EnchantmentBase {
    private static final String VALUE_ID = "indomitable";
    private static final EnchantmentValues.Handle IMMUNE_CHANCE_FACTOR = EnchantmentValues.define(VALUE_ID, "immune_chance_factor",
                    0.75D, 0.0D, 1.0D);
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return false;
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

