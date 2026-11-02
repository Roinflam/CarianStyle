package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E3)
 *
 * 吃屎附魔
 * <p>v2.2：攻击者+治疗事件入口接入怪物附魔触发开关</p>
 *
 * <h3>v2.3：{@link #DEBUFF_KEY} 改为 public（行为零变化）</h3>
 * <p>
 * {@code CarianStyleConditionDisplay} 需要读这条记录来显示「治疗被削减，还剩几秒」。
 * 这个 debuff <b>不是原版药水效果</b>，屏幕右侧不会出现任何图标——
 * 中招的玩家只会觉得「我怎么喝了药还是不回血」，完全无从判断。
 * </p>
 * <p>
 * 与其在 HUD 那边复制一份 {@code "eat_shit_debuff"} 字面量，不如把常量公开：
 * 复制的字面量不会跟着改，哪天这里改了键名而那边没跟上，
 * HUD 会安静地永远显示「无」，既不报错也不会被测试发现。
 * </p>
 * <p>
 * <b>本次只改了这一个字段的可见性修饰符，其余逻辑一行未动。</b>
 * </p>
 *
 * <h3>v2.4：施加 debuff 要求满蓄力</h3>
 * <p>
 * 玩家必须满蓄力挥击才会给目标上反胃与治疗削减（自己吃反胃的代价也一起跳过）；
 * 怪物持有者不受影响。{@link #onLivingHeal} 的治疗削减结算不变。
 * </p>
 *
 * @version 2.4
 */
@AutoRegisterEnchantment(id = "eat_shit", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnchantTarget.WEAPON, slots = {EquipmentSlot.MAINHAND}, baseEnchantability = 25, levelMultiplier = 2)
@Deprecated
public class EnchantmentEatShit extends EnchantmentBase {
    private static final String VALUE_ID = "eat_shit";
    private static final EnchantmentValues.Handle VICTIM_TICKS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "victim_ticks_per_level",
                    80, 1, 1200);
    private static final EnchantmentValues.Handle SELF_TICKS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "self_ticks_per_level",
                    30, 1, 1200);
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    0.25D, 0.0D, 5.0D);
    public static final String DEBUFF_KEY = "eat_shit_debuff";
    public EnchantmentEatShit() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
}

