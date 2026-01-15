package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 火焰疗愈附魔
 *
 * 护甲附魔，着火时受击有概率净化负面效果
 * 受到攻击时（需着火）：
 * - 2.5% × 等级的概率触发
 * - 随机移除一个负面效果
 * - 获得10%最大生命值的吸收盾
 */
@AutoRegisterEnchantment(
        id = "healing_by_fire",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
@Mod.EventBusSubscriber
public class EnchantmentHealingByFire extends EnchantmentBase {

    public EnchantmentHealingByFire() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 着火时受击有概率净化负面效果并获得吸收盾
     * 由于需要累加所有护甲的附魔等级，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 必须有攻击来源
        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        // 受击者必须着火
        if (EntityUtil.getFire(victim) <= 0) {
            return;
        }

        // 受击者必须有药水效果
        if (victim.getActivePotionEffects().isEmpty()) {
            return;
        }

        Enchantment healingByFire = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHealingByFire.class);
        if (healingByFire == null) {
            return;
        }

        // 从所有护甲累加附魔等级
        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(healingByFire, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        // 2.5% × 等级的概率触发
        if (!RandomUtil.percentageChance(totalLevel * 2.5)) {
            return;
        }

        // 筛选可移除的负面效果（负面、非瞬时、可渲染）
        List<PotionEffect> badEffects = new ArrayList<>(victim.getActivePotionEffects());
        badEffects.removeIf(effect ->
                !effect.getPotion().isBadEffect() ||
                        effect.getPotion().isInstant() ||
                        !effect.getPotion().shouldRender(effect)
        );

        if (badEffects.isEmpty()) {
            return;
        }

        // 随机移除一个负面效果
        PotionEffect toRemove = badEffects.get(RandomUtil.getInt(0, badEffects.size() - 1));
        victim.removePotionEffect(toRemove.getPotion());

        // 获得10%最大生命值的吸收盾
        victim.setAbsorptionAmount(victim.getAbsorptionAmount() + victim.getMaxHealth() * 0.1f);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.PROTECTION);
    }
}