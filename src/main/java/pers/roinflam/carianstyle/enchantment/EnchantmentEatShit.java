package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;

/**
 * 吃屎附魔
 *
 * 武器附魔，反胃诅咒
 * 攻击时：
 * - 给敌人施加反胃效果（等级 × 4秒）
 * - 给自己施加反胃效果（等级 × 1.5秒）
 * - 敌人在反胃期间治疗量减少75%
 */
@AutoRegisterEnchantment(
        id = "eat_shit",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
@Mod.EventBusSubscriber
public class EnchantmentEatShit extends EnchantmentBase {

    private static final String DEBUFF_KEY = "eat_shit_debuff";
    /** 参与计算的等级上限 */
    private static final int LEVEL_CAP = 10;
    /** 目标每级反胃 / 玷污时长（tick） */
    private static final int VICTIM_TICKS_PER_LEVEL = 80;
    /** 自己每级反胃时长（tick） */
    private static final int SELF_TICKS_PER_LEVEL = 30;
    /** 玷污期间恢复量倍率 */
    private static final float HEAL_MULTIPLIER = 0.25F;

    public EnchantmentEatShit() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 满蓄力近战造成伤害后，目标反胃并被玷污（恢复 -75%），自己也反胃一小会儿
     * <p>
     * 以前每一下都挂，连点就能让目标一直反胃、一直吃治疗削减，现在要求满蓄力（自己的反胃也一起跳过）。
     * 只认主手。
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        if (shouldBlockMobTrigger(attacker, false)) {
            return;
        }

        Enchantment eatShit = EnchantmentRegistry.getEnchantmentByClass(EnchantmentEatShit.class);
        if (eatShit == null) {
            return;
        }

        ItemStack heldItem = attacker.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(eatShit, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, LEVEL_CAP);
        }

        if (level <= 0) {
            return;
        }

        if (!isFullyCharged(attacker)) {
            return;
        }

        int victimDuration = level * VICTIM_TICKS_PER_LEVEL;
        victim.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, victimDuration));
        attacker.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, level * SELF_TICKS_PER_LEVEL));

        // 标记敌人处于治疗减益状态
        EnchantmentDataManager.setCooldown(DEBUFF_KEY, victim.getUniqueID(), victimDuration);
    }

    /**
     * 被玷污的实体恢复量 -75%
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (shouldBlockMobTrigger(evt.getEntityLiving(), false)) {
            return;
        }

        if (EnchantmentDataManager.isOnCooldown(DEBUFF_KEY, evt.getEntity().getUniqueID())) {
            evt.setAmount(evt.getAmount() * HEAL_MULTIPLIER);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 2) * ConfigLoader.enchantingDifficulty);
    }
}