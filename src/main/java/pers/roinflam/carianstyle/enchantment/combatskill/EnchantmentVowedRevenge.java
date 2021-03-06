package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentVowedRevenge extends RaryBase {

    public EnchantmentVowedRevenge(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "vowed_revenge");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.VOWED_REVENGE;
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (bonusLevel > 0) {
                        List<Entity> entities = attacker.world.getEntitiesWithinAABB(
                                EntityLivingBase.class,
                                new AxisAlignedBB(attacker.getPosition()).expand(bonusLevel * 2, bonusLevel * 2, bonusLevel * 2),
                                entityLivingBase -> !entityLivingBase.equals(attacker));
                        evt.setAmount((float) (evt.getAmount() + evt.getAmount() * bonusLevel * entities.size() * 0.025));
                        if (attacker.getRevengeTarget() != null && attacker.getRevengeTarget().equals(hurter)) {
                            evt.setAmount((float) (evt.getAmount() + evt.getAmount() * bonusLevel * 0.05));
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 10 + (enchantmentLevel - 1) * 10;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.COMBAT_SKILL.contains(ench);
    }

}
