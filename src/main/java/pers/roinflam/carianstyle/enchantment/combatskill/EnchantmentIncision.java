package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 切割附魔
 *
 * 激活：血量>=75%时攻击，消耗50%血量获得INCISION状态（200tick）
 * 激活后：攻击治疗自身（min(伤害×0.25, 最大血量×0.25)），给敌人施加出血
 * 击杀：治疗（损失血量×0.1），延长INCISION时间
 * <p>
 * 满蓄力改用 {@link #isFullyCharged}（蓄力超过 90%），以前要求上一 tick 的冷却恰好为 1。
 * 代价走 {@link EntityLivingUtil#payHealthCost}：时间回溯期间不付，付不起时正常死亡，活着才挂切腹状态。
 * 击杀续命只认主手。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "incision",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
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

    // 触发切腹所需的生命值比例（不低于）
    private static final float TRIGGER_HEALTH_RATIO = 0.75F;
    // 触发时消耗的最大生命值比例
    private static final float SELF_COST_RATIO = 0.5F;
    // 切腹状态持续时间（tick），也是击杀续时的上限
    private static final int SELF_EFFECT_DURATION = 200;
    // 切腹期间每次伤害回复：本次伤害的比例
    private static final float HEAL_RATIO = 0.25F;
    // 切腹期间每次伤害回复的上限：最大生命值的比例
    private static final float HEAL_CAP_RATIO = 0.25F;
    // 给目标挂的出血时长（tick）
    private static final int HEMORRHAGE_DURATION = 30;
    // 击杀时回复已损失生命值的比例
    private static final float KILL_HEAL_RATIO = 0.1F;
    // 击杀时切腹延长的时间（tick）
    private static final int KILL_DURATION_BONUS = 100;

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

        // 玩家需要满蓄力
        if (!isFullyCharged(attacker)) {
            return;
        }

        if (attacker.getActivePotionEffect(CarianStylePotion.INCISION) == null) {
            // 激活阶段：血量>=75%时消耗50%最大生命值获得INCISION
            if (attacker.getHealth() >= attacker.getMaxHealth() * TRIGGER_HEALTH_RATIO) {
                EntityLivingUtil.payHealthCost(attacker, attacker.getMaxHealth() * SELF_COST_RATIO);
                if (attacker.isEntityAlive()) {
                    attacker.addPotionEffect(new PotionEffect(CarianStylePotion.INCISION, SELF_EFFECT_DURATION, 0));
                }
            }
        } else {
            // 激活后：治疗自身，给敌人施加出血
            float healAmount = Math.min(ctx.getDamage() * HEAL_RATIO, attacker.getMaxHealth() * HEAL_CAP_RATIO);
            attacker.heal(healAmount);
            victim.addPotionEffect(new PotionEffect(CarianStylePotion.HEMORRHAGE, HEMORRHAGE_DURATION, 0));
        }
    }

    /**
     * 击杀敌人时：治疗并延长INCISION时间
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase killer = (EntityLivingBase) evt.getSource().getImmediateSource();

        // 击杀者必须存活且不是自杀
        if (!killer.isEntityAlive() || killer.equals(evt.getEntityLiving())) {
            return;
        }

        if (shouldBlockMobTrigger(killer, false)) {
            return;
        }

        ItemStack heldItem = killer.getHeldItemMainhand();
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

        PotionEffect incisionEffect = killer.getActivePotionEffect(CarianStylePotion.INCISION);
        if (incisionEffect == null) {
            return;
        }

        // 治疗损失血量的10%
        killer.heal((killer.getMaxHealth() - killer.getHealth()) * KILL_HEAL_RATIO);

        // 延长INCISION时间（+100tick，上限200tick）
        int newDuration = Math.min(incisionEffect.getDuration() + KILL_DURATION_BONUS, SELF_EFFECT_DURATION);
        killer.addPotionEffect(new PotionEffect(CarianStylePotion.INCISION, newDuration, 0));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }
}