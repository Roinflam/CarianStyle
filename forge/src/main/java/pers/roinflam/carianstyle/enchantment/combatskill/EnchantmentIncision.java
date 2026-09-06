package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * 切割附魔
 * <p>v2.2：onLivingDeath 击杀者视角接入怪物附魔触发开关。
 * onDamageAsAttacker 走中央事件分发器，已经在 scanEntity 入口被通用开关拦截。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "incision",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentIncision extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.incision.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "incision";

    /**
     * 触发所需的剩余生命比例下限
     * <p>默认 0.75，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_HEALTH_RATIO =
            EnchantmentValues.define(VALUE_ID, "trigger_health_ratio",
                    0.75D, 0.0D, 1.0D);

    /**
     * 触发时消耗的最大生命占比。⚠ 必须小于 trigger_health_ratio，否则会当场自杀
     * <p>默认 0.5，允许范围 0.0 ~ 0.95。</p>
     */
    private static final EnchantmentValues.Handle SELF_COST_RATIO =
            EnchantmentValues.define(VALUE_ID, "self_cost_ratio",
                    0.5D, 0.0D, 0.95D);

    /**
     * 自身切割状态的持续时间（tick）；同时也是续期的时长上限
     * <p>默认 200，允许范围 20 ~ 6000。</p>
     */
    private static final EnchantmentValues.Handle SELF_EFFECT_DURATION =
            EnchantmentValues.define(VALUE_ID, "self_effect_duration",
                    200, 20, 6000);

    /**
     * 按本次伤害回复生命的比例
     * <p>默认 0.25，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_RATIO =
            EnchantmentValues.define(VALUE_ID, "heal_ratio",
                    0.25D, 0.0D, 2.0D);

    /**
     * 单次治疗量的最大生命占比上限
     * <p>默认 0.25，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_CAP_RATIO =
            EnchantmentValues.define(VALUE_ID, "heal_cap_ratio",
                    0.25D, 0.0D, 2.0D);

    /**
     * 施加给目标的出血持续时间（tick）
     * <p>默认 30，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle HEMORRHAGE_DURATION =
            EnchantmentValues.define(VALUE_ID, "hemorrhage_duration",
                    30, 1, 1200);

    /**
     * 击杀时回复已损失生命的比例
     * <p>默认 0.1，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle KILL_HEAL_RATIO =
            EnchantmentValues.define(VALUE_ID, "kill_heal_ratio",
                    0.1D, 0.0D, 2.0D);

    /**
     * 击杀时为自身切割状态续期的时长（tick，总时长不超过 self_effect_duration）
     * <p>默认 100，允许范围 0 ~ 6000。</p>
     */
    private static final EnchantmentValues.Handle KILL_DURATION_BONUS =
            EnchantmentValues.define(VALUE_ID, "kill_duration_bonus",
                    100, 0, 6000);

    public EnchantmentIncision() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        if (!attacker.hasEffect(CarianStylePotion.INCISION.get())) {
            if (attacker.getHealth() >= attacker.getMaxHealth() * (float) TRIGGER_HEALTH_RATIO.get()) {
                attacker.setHealth(attacker.getHealth() - attacker.getMaxHealth() * (float) SELF_COST_RATIO.get());
                attacker.addEffect(new MobEffectInstance(
                CarianStylePotion.INCISION.get(), SELF_EFFECT_DURATION.getInt(), 0));
            }
        } else {
            float healAmount = Math.min(ctx.getDamage() * (float) HEAL_RATIO.get(),
                attacker.getMaxHealth() * (float) HEAL_CAP_RATIO.get());
            attacker.heal(healAmount);
            victim.addEffect(new MobEffectInstance(
                CarianStylePotion.HEMORRHAGE.get(), HEMORRHAGE_DURATION.getInt(), 0));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity killer = (LivingEntity) evt.getSource().getDirectEntity();

        if (!killer.isAlive() || killer.equals(evt.getEntity())) {
            return;
        }

        // ⭐ v2.2：怪物附魔触发开关（击杀者视角，击杀续命非濒死触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(killer, false)) return;

        ItemStack heldItem = killer.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment incision = EnchantmentRegistry.getEnchantmentByClass(EnchantmentIncision.class);
        if (incision == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(incision, heldItem);

        if (level <= 0) {
            return;
        }

        MobEffectInstance incisionEffect = killer.getEffect(CarianStylePotion.INCISION.get());
        if (incisionEffect == null) {
            return;
        }

        killer.heal((killer.getMaxHealth() - killer.getHealth()) * (float) KILL_HEAL_RATIO.get());

        int newDuration = Math.min(incisionEffect.getDuration() + KILL_DURATION_BONUS.getInt(),
                    SELF_EFFECT_DURATION.getInt());
        killer.addEffect(new MobEffectInstance(CarianStylePotion.INCISION.get(), newDuration, 0));
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
