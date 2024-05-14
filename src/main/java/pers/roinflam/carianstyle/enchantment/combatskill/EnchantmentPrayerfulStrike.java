package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentPrayerfulStrike extends VeryRaryBase {
    public static final UUID ID = UUID.fromString("b55a7c8a-df03-bca7-b5ea-ec703b261525");
    public static final String NAME = "enchantment.prayerful_strike";
    private static final HashMap<UUID, Integer> READY = new HashMap<>();

    public EnchantmentPrayerfulStrike(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "prayerful_strike");

        new SynchronizationTask(1, 1) {

            @Override
            public void run() {
                for (UUID uuid : new ArrayList<>(READY.keySet())) {
                    if (READY.get(uuid) > 1) {
                        READY.put(uuid, READY.get(uuid) - 1);
                    } else {
                        READY.remove(uuid);
                    }
                }
            }

        }.start();
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.PRAYERFUL_STRIKE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    
                if (bonusLevel > 0) {
                        if (READY.containsKey(attacker.getUniqueID())) {
                            if (READY.get(attacker.getUniqueID()) <= 4 * 20) {
                                READY.put(attacker.getUniqueID(), 8 * 20);
                                float health = Math.min(attacker.getMaxHealth() * 0.025f + hurter.getHealth() * 0.075f, hurter.getMaxHealth() * 0.1f);
                                evt.setAmount(evt.getAmount() + health);

                                @Nonnull IAttributeInstance attributeInstance = attacker.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
                                if (attributeInstance.getModifier(ID) == null) {
                                    attributeInstance.applyModifier(new AttributeModifier(ID, NAME, health / 2, 0));
                                } else {
                                    double base = attributeInstance.getModifier(ID).getAmount();
                                    attributeInstance.removeModifier(ID);
                                    attributeInstance.applyModifier(new AttributeModifier(ID, NAME, Math.min(base + Math.min(health / 2, attacker.getMaxHealth() * 0.05), ConfigLoader.prayerfulStrikeMaxHealth), 0));
                                }

                                attacker.heal(health);
                                attacker.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1, 0);
                                hurter.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1, 0);
                            }
                        } else {
                            READY.put(attacker.getUniqueID(), 8 * 20);
                        }
                    }
                }
                if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItem(hurter.getActiveHand()));
                    
                if (bonusLevel > 0) {
                        if (READY.containsKey(hurter.getUniqueID())) {
                            if (READY.get(hurter.getUniqueID()) <= 4 * 20) {
                                READY.put(hurter.getUniqueID(), 4 * 20);
                            }
                        } else {
                            READY.put(hurter.getUniqueID(), 8 * 20);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent evt) {
        if (!evt.player.world.isRemote && !evt.isEndConquered()) {
            EntityPlayer entityPlayer = evt.player;
            entityPlayer.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).removeModifier(ID);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        READY.remove(evt.getEntityLiving().getUniqueID());
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.phase.equals(TickEvent.Phase.START) && evt.player.ticksExisted % 20 == 0) {
            @Nonnull EntityPlayer entityPlayer = evt.player;
            if (entityPlayer.isEntityAlive()) {
                int coolding = READY.getOrDefault(entityPlayer.getUniqueID(), 0);
                if (coolding >= 4 * 20 && coolding < 8 * 20) {
                    entityPlayer.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1, 3);
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.COMBAT_SKILL.contains(ench) && !ench.equals(CarianStyleEnchantments.SCARLET_ROT);
    }
}
