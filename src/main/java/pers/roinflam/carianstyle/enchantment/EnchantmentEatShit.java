package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
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

    public EnchantmentEatShit() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时给双方施加反胃效果
     * 保留静态监听器，因为需要特殊的immediate source检查
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
        Enchantment eatShit = EnchantmentRegistry.getEnchantmentByClass(EnchantmentEatShit.class);

        if (eatShit == null) {
            return;
        }

        if (attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                eatShit,
                attacker.getHeldItem(attacker.getActiveHand())
        );

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        // 给敌人施加反胃效果（等级 × 4秒）
        int victimDuration = level * 80;  // 等级 × 80 tick = 等级 × 4秒
        victim.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, victimDuration));

        // 给自己施加反胃效果（等级 × 1.5秒）
        int attackerDuration = level * 30;  // 等级 × 30 tick = 等级 × 1.5秒
        attacker.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, attackerDuration));

        // 标记敌人处于治疗减益状态
        EnchantmentDataManager.setCooldown(DEBUFF_KEY, victim.getUniqueID(), victimDuration);
    }

    /**
     * 被标记的实体治疗量减少75%
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 检查是否在治疗减益状态中
        if (EnchantmentDataManager.isOnCooldown(DEBUFF_KEY, evt.getEntity().getUniqueID())) {
            // 减少75%的治疗量
            evt.setAmount(evt.getAmount() * 0.25f);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 2) * ConfigLoader.enchantingDifficulty);
    }
}