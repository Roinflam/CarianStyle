package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.UncommonBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentSacredBlade extends UncommonBase {
    public static final UUID ID = UUID.fromString("0dada439-4e61-fd5e-44d7-c620fd5a11fb");
    public static final String NAME = "enchantment.sacred_blade";

    public EnchantmentSacredBlade(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "sacred_blade");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.SACRED_BLADE;
    }

    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    
                if (bonusLevel > 0) {
                        if (hurter.getCreatureAttribute().equals(EnumCreatureAttribute.UNDEAD)) {
                            float damage = evt.getAmount() * bonusLevel * 0.25f * (hurter.getHealth() / hurter.getMaxHealth());
                            evt.setAmount(evt.getAmount() + damage);
                            attacker.heal(Math.min(damage * 0.2f, attacker.getMaxHealth() * 0.1f));

                            double injuryReduction = Math.max(bonusLevel * -0.05, -0.99);
                            @Nonnull IAttributeInstance attributeInstance = attacker.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
                            if (attributeInstance.getModifier(ID) == null) {
                                attributeInstance.applyModifier(new AttributeModifier(ID, NAME, injuryReduction, 2));
                            } else {
                                double base = attributeInstance.getModifier(ID).getAmount();
                                if (base > injuryReduction) {
                                    attributeInstance.removeModifier(ID);
                                    attributeInstance.applyModifier(new AttributeModifier(ID, NAME, injuryReduction, 2));
                                }
                            }
                        } else {
                            evt.setAmount(evt.getAmount() * 0.2f);
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.COMBAT_SKILL.contains(ench) &&
                !ench.equals(CarianStyleEnchantments.SCARLET_ROT) &&
                !ench.equals(CarianStyleEnchantments.DEATH_BLADE) &&
                !ench.equals(CarianStyleEnchantments.BLACK_FLAME_BLADE) &&
                !ench.equals(Enchantments.SHARPNESS);
    }

}
