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
import pers.roinflam.carianstyle.base.enchantment.rarity.UncommonBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentHealingByFire extends UncommonBase {

    public EnchantmentHealingByFire(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "healing_by_fire");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.HEALING_BY_FIRE;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                if (EntityUtil.getFire(hurter) > 0) {
                    if (hurter.getActivePotionEffects().size() > 0) {
                        int bonusLevel = 0;
                        for (@Nonnull ItemStack itemStack : hurter.getArmorInventoryList()) {
                            if (!itemStack.isEmpty()) {
                                bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                            }
                        }
                        if (ConfigLoader.levelLimit) {
                            bonusLevel = Math.min(bonusLevel, 10);
                        }
                        if (bonusLevel > 0) {
                            if (RandomUtil.percentageChance(bonusLevel * 2.5)) {
                                @Nonnull List<PotionEffect> potionEffects = new ArrayList<>(hurter.getActivePotionEffects());
                                potionEffects.removeIf(potionEffect ->
                                        !potionEffect.getPotion().isBadEffect() ||
                                                potionEffect.getPotion().isInstant() ||
                                                !potionEffect.getPotion().shouldRender(potionEffect)
                                );
                                if (potionEffects.size() > 0) {
                                    PotionEffect potionEffect = potionEffects.get(RandomUtil.getInt(0, potionEffects.size() - 1));
                                    hurter.removePotionEffect(potionEffect.getPotion());
                                    hurter.setAbsorptionAmount(hurter.getAbsorptionAmount() + hurter.getMaxHealth() * 0.1f);
                                }
                            }
                        }
                    }
                }
            }
        }
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
