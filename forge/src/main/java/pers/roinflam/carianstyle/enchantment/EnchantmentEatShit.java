package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
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
@AutoRegisterEnchantment(id = "eat_shit", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND})
@Mod.EventBusSubscriber
public class EnchantmentEatShit extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.eat_shit.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "eat_shit";

    /**
     * 每级施加给目标的效果时长（tick）
     * <p>默认 80，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle VICTIM_TICKS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "victim_ticks_per_level",
                    80, 1, 1200);

    /**
     * 每级施加给自身的混乱时长（tick）
     * <p>默认 30，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle SELF_TICKS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "self_ticks_per_level",
                    30, 1, 1200);

    /**
     * 触发时的伤害倍率
     * <p>默认 0.25，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    0.25D, 0.0D, 5.0D);


    /**
     * 治疗削减 debuff 在 {@link EnchantmentDataManager} 中的键。
     * <p><b>v2.3 由 private 改为 public</b>：HUD 需要用同一个键读取剩余时间。
     * 注意这条记录是打在<b>受击者</b>身上的，与受击者自己的装备无关（详见类注释）。</p>
     */
    public static final String DEBUFF_KEY = "eat_shit_debuff";

    public EnchantmentEatShit() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity attacker)) return;

        // ⭐ v2.2：怪物附魔触发开关（攻击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        LivingEntity victim = evt.getEntity();
        Enchantment eatShit = EnchantmentRegistry.getEnchantmentByClass(EnchantmentEatShit.class);
        if (eatShit == null) return;
        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(eatShit, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;
        // ⭐ v2.4：连点会让每一下都挂上反胃和治疗削减，要求满蓄力
        if (!EnchantmentBase.isFullyCharged(attacker)) return;
        int victimDuration = level * VICTIM_TICKS_PER_LEVEL.getInt();
        victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, victimDuration));
        attacker.addEffect(new MobEffectInstance(MobEffects.CONFUSION, level * SELF_TICKS_PER_LEVEL.getInt()));
        EnchantmentDataManager.setCooldown(DEBUFF_KEY, victim.getUUID(), victimDuration);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@NotNull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide) return;

        // ⭐ v2.2：怪物附魔触发开关（受治疗者视角，被附加的debuff效果）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(evt.getEntity(), false)) return;

        if (EnchantmentDataManager.isOnCooldown(DEBUFF_KEY, evt.getEntity().getUUID())) {
            evt.setAmount(evt.getAmount() * (float) DAMAGE_MULTIPLIER.get());
        }
    }

    @Override public int getMinCost(int l) { return (int)((25 + (l - 1) * 2) * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
