package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentHealingByFire;
import pers.roinflam.carianstyle.enchantment.EnchantmentShelterOfFire;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;
import java.util.UUID;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;

/**
 * PORT-STUB(E2)
 *
 * 满月附魔
 * <p>修复: DarkMoon检查getUsedItemHand -> InteractionHand.MAIN_HAND</p>
 * <p>v2.2新增: onLivingDeath 入口接入怪物附魔触发开关</p>
 * <p>v2.3新增: onLivingHeal 入口补齐怪物附魔触发开关</p>
 * <p>v2.4新增: 月华复活特效</p>
 *
 * <h3>v2.5：特效时长与实际回血时长严格对齐</h3>
 * <p>
 * <b>问题：</b>本附魔的回血持续时间<b>取决于持有者有没有装备暗月</b>——
 * 不带是 200 tick(10 秒)，带是 400 tick(20 秒)。而客户端无从得知这一点，
 * 早期版本只能让渲染器取最长的 20 秒写死，结果不带暗月时
 * <b>特效比实际回血多播 10 秒</b>——玩家早就满血了，月光还挂在头顶。
 * </p>
 * <p>
 * <b>修复：</b>把 {@code duration} 的计算<b>提前到特效调用之前</b>，
 * 再通过 {@link CarianStyleEffects#moonBlessing(ServerLevel, LivingEntity, int)}
 * 把实际 tick 数发给客户端。渲染器会用它把归一化进度换算回绝对秒数，
 * 因此 10 秒版与 20 秒版的<b>动画速度完全一致</b>，只是持续段长短不同。
 * </p>
 * <p>
 * <b>顺序要求：</b>{@code hasDarkMoon} 与 {@code duration} 的计算<b>必须</b>
 * 排在 {@code moonBlessing(...)} 调用之前——这是接入特效时唯一调整的代码顺序。
 * 若将来有人把特效调用挪到前面，特效时长会退回默认值，
 * 又会出现「特效比回血长」的老毛病。
 * </p>
 * <p>
 * <b>特效不产生任何机制影响</b>：不生成实体、不触发事件、不造成伤害，
 * 只向 64 格内的客户端广播一个约 34 字节的轻量包。
 * </p>
 *
 * @version 2.5
 */
@AutoRegisterEnchantment(id = "full_moon", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnchantTarget.ARMOR_CHEST, slots = {EquipmentSlot.CHEST}, conflictsWith = {EnchantmentHealingByFire.class, EnchantmentShelterOfFire.class}, baseEnchantability = 35, levelMultiplier = 0)
@Deprecated
public class EnchantmentFullMoon extends EnchantmentBase {
    private static final String FULL_MOON_STATE_KEY = "full_moon_state";
    private static final String FULL_MOON_COOLDOWN_KEY = "full_moon_cooldown";
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    private static final int HEAL_DURATION_TICKS = 200;
    private static final int HEAL_DURATION_TICKS_DARK_MOON = 400;
    public EnchantmentFullMoon() {
        super(EnchantTarget.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }
    @Override
    protected boolean checkCompatibility(EnchantKey ench) {
        if (ench.isOf(EnchantmentTimeReversal.class)) return false;
        return super.checkCompatibility(ench);
    }
}

