package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.server.FMLServerHandler;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentUnsheathe extends RaryBase {
    public static final UUID ID = UUID.fromString("a9f7b1c6-4c2d-4f0e-9f2c-3a8b3f7d0a5b");
    public static final String NAME = "enchantment.unsheathe";
    private static final HashMap<UUID, Integer> NUM_OF_ATTACKS = new HashMap<>();

    public EnchantmentUnsheathe(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "unsheathe");
        new SynchronizationTask(12000, 12000) {

            @Override
            public void run() {
                for (UUID uuid : NUM_OF_ATTACKS.keySet()) {
                    EntityPlayer entityPlayer = FMLServerHandler.instance().getServer().getPlayerList().getPlayerByUUID(uuid);
                    if (entityPlayer != null) {
                        @Nonnull IAttributeInstance attributeInstance = entityPlayer.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
                        attributeInstance.removeModifier(ID);
                    }
                }
                NUM_OF_ATTACKS.clear();
            }

        }.start();
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.UNSHEATHE;
    }

    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().damageType.equals("waterfowlDance") && evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (attacker instanceof EntityPlayer) {
                    if (EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker) != 1) {
                        return;
                    } else {
                        ((EntityPlayer) attacker).resetCooldown();
                    }
                    if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));

                if (bonusLevel > 0) {
                            if (RandomUtil.percentageChance(1 + NUM_OF_ATTACKS.getOrDefault(attacker.getUniqueID(), 0) * 0.5)) {
                                @Nonnull IAttributeInstance attributeInstance = attacker.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
                                evt.setAmount(evt.getAmount() * bonusLevel * 3.3f);
                                NUM_OF_ATTACKS.remove(attacker.getUniqueID());
                                if (attributeInstance.getModifier(ID) == null) {
                                    attributeInstance.applyModifier(new AttributeModifier(ID, NAME, -0.75, 2));
                                    new SynchronizationTask(bonusLevel * 66) {

                                        @Override
                                        public void run() {
                                            attributeInstance.removeModifier(ID);
                                        }

                                    }.start();
                                }
                            } else {
                                NUM_OF_ATTACKS.put(attacker.getUniqueID(), NUM_OF_ATTACKS.getOrDefault(attacker.getUniqueID(), 0) + 1);
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent evt) {
        if (!evt.getWorld().isRemote) {
            if (evt.getEntity() instanceof EntityLivingBase) {
                EntityLivingBase entityLivingBase = (EntityLivingBase) evt.getEntity();
                @Nonnull IAttributeInstance attributeInstance = entityLivingBase.getEntityAttribute(SharedMonsterAttributes.ATTACK_SPEED);
                if (attributeInstance != null) {
                    attributeInstance.removeModifier(ID);
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((28 + (enchantmentLevel - 1) * 8) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        return !CarianStyleEnchantments.COMBAT_SKILL.contains(ench);
    }

}
