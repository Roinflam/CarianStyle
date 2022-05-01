package pers.roinflam.kaliastyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.kaliastyle.init.KaliaStyleEnchantments;
import pers.roinflam.kaliastyle.init.KaliaStylePotion;
import pers.roinflam.kaliastyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.kaliastyle.utils.util.EnchantmentUtil;

@Mod.EventBusSubscriber
public class EnchantmentDeathBlade extends Enchantment {

    public EnchantmentDeathBlade(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "death_blade");
        KaliaStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return KaliaStyleEnchantments.DEATH_BLADE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (attacker.getHeldItemMainhand() != null) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItemMainhand());
                    if (bonusLevel > 0) {
                        DamageSource damageSource = evt.getSource();
                        if (!damageSource.damageType.equals("deathBlade") && !damageSource.canHarmInCreative()) {
                            float damage = (float) (evt.getAmount() * 0.5 / 100);
                            evt.setAmount((float) (evt.getAmount() * 0.5));
                            damageSource.damageType = "deathBlade";
                            hurter.addPotionEffect(new PotionEffect(KaliaStylePotion.DOOMED_DEATH_BURNING, 5 * 20 + 5, 0));
                            hurter.addPotionEffect(new PotionEffect(KaliaStylePotion.DOOMED_DEATH, 10 * 20, 0));
                            new SynchronizationTask(1, 1) {
                                private int tick = 0;

                                @Override
                                public void run() {
                                    if (++tick > 100 || !hurter.isEntityAlive()) {
                                        this.cancel();
                                        return;
                                    }
                                    if (hurter.getHealth() - damage * 1.1 > 0) {
                                        hurter.setHealth(hurter.getHealth() - damage);
                                    } else {
                                        hurter.hurtResistantTime = 0;
                                        hurter.attackEntityFrom(damageSource.setDamageBypassesArmor(), (float) (damage * 1.1));
                                        this.cancel();
                                    }
                                }

                            }.start();
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 50;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}
