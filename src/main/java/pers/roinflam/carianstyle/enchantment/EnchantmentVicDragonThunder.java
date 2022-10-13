package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentVicDragonThunder extends RaryBase {
    private static final Set<UUID> VIC_DRAGON_THUNDER = new HashSet<>();

    public EnchantmentVicDragonThunder(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "vic_dragon_thunder");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.VIC_DRAGON_THUNDER;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            if (!evt.getSource().canHarmInCreative() && evt.getSource().damageType.equals("lightningBolt")) {
                if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItem(hurter.getActiveHand()));
                    if (bonusLevel > 0) {
                        if (bonusLevel * 0.15 >= 1) {
                            evt.setCanceled(true);
                            return;
                        }
                        evt.setAmount(evt.getAmount() - evt.getAmount() * bonusLevel * 0.15f);
                    }
                }
            }
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (bonusLevel > 0) {
                        if (RandomUtil.percentageChance(attacker.world.isThundering() ? 100 : bonusLevel * 5 * (attacker.world.isRaining() ? 2 : 1))) {
                            World world = hurter.world;
                            world.addWeatherEffect(
                                    new EntityLightningBolt(
                                            world,
                                            hurter.posX,
                                            hurter.posY,
                                            hurter.posZ,
                                            true
                                    )
                            );
                            hurter.hurtResistantTime = hurter.maxHurtResistantTime / 2;
                            int magnification = 1;
                            if (attacker.world.isRaining()) {
                                magnification *= 2;
                            } else if (attacker.world.isThundering()) {
                                magnification *= 4;
                            }
                            hurter.attackEntityFrom(DamageSource.LIGHTNING_BOLT, evt.getAmount() * bonusLevel * 0.5f * magnification);
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 30 + (enchantmentLevel - 1) * 15;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                !ench.equals(CarianStyleEnchantments.SCARLET_ROT) &&
                !ench.equals(CarianStyleEnchantments.FIRE_GIVES_POWER) &&
                !ench.equals(CarianStyleEnchantments.FIRE_DEVOURED);
    }
}
