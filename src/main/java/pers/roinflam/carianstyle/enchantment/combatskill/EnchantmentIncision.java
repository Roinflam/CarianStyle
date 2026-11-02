package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 切割附魔
 * <p>
 * 没有切腹时：血量不低于 75% 的满蓄力一击，付出最大生命 50% 的代价，给自己挂 200 tick 切腹；
 * 切腹期间：每一击回复本次伤害 25% 的生命（至多最大生命 25%），并给目标挂 30 tick 出血；
 * 切腹期间击杀：回复已损失生命的 10%，切腹延长 100 tick（不超过 200 tick）。
 * </p>
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
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class
        }
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentIncision extends EnchantmentBase {

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
     * 触发时消耗的最大生命占比。必须小于 trigger_health_ratio，否则会当场自杀
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
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        if (ctx.isHolderPlayer()) {
            if (!isJustSwung(ctx.getHolderAsPlayer())) {
                return;
            }
        }

        if (!attacker.isPotionActive(CarianStylePotion.INCISION.get())) {
            if (attacker.getHealth() >= attacker.getMaxHealth() * (float) TRIGGER_HEALTH_RATIO.get()) {
                EntityLivingUtil.payHealthCost(attacker, attacker.getMaxHealth() * (float) SELF_COST_RATIO.get());
                if (attacker.isEntityAlive()) {
                    attacker.addPotionEffect(new PotionEffect(
                            CarianStylePotion.INCISION.get(), SELF_EFFECT_DURATION.getInt(), 0));
                }
            }
        } else {
            float healAmount = Math.min(ctx.getDamage() * (float) HEAL_RATIO.get(),
                    attacker.getMaxHealth() * (float) HEAL_CAP_RATIO.get());
            attacker.heal(healAmount);
            victim.addPotionEffect(new PotionEffect(
                    CarianStylePotion.HEMORRHAGE.get(), HEMORRHAGE_DURATION.getInt(), 0));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase killer = (EntityLivingBase) evt.getSource().getImmediateSource();

        if (!killer.isEntityAlive() || killer.equals(evt.getEntityLiving())) {
            return;
        }

        // v2.2：怪物附魔触发开关（击杀者视角，击杀续命非濒死触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(killer, false)) return;

        ItemStack heldItem = killer.getHeldItem(EnumHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment incision = EnchantmentRegistry.getEnchantmentByClass(EnchantmentIncision.class);
        if (incision == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(incision, heldItem);

        if (level <= 0) {
            return;
        }

        PotionEffect incisionEffect = killer.getActivePotionEffect(CarianStylePotion.INCISION.get());
        if (incisionEffect == null) {
            return;
        }

        killer.heal((killer.getMaxHealth() - killer.getHealth()) * (float) KILL_HEAL_RATIO.get());

        int newDuration = Math.min(incisionEffect.getDuration() + KILL_DURATION_BONUS.getInt(),
                SELF_EFFECT_DURATION.getInt());
        killer.addPotionEffect(new PotionEffect(CarianStylePotion.INCISION.get(), newDuration, 0));
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
