package pers.roinflam.carianstyle.potion;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentAeonia;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 猩红腐烂药水效果
 *
 * 效果：
 * - 护甲和韧性减少50%
 * - 治疗量减少25%
 * - 每秒造成持续伤害
 * - 与艾奥尼亚附魔联动：伤害增强、传播效果
 */
public class MobEffectScarletRot extends IconBase {

    public MobEffectScarletRot(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "scarlet_rot");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR, "4ffde3df-c955-f645-d34b-814fda024326", -0.5, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR_TOUGHNESS, "0b4792a8-c918-bf55-5c7a-62a83b54e569", -0.5, 2);
    }

    /**
     * 获取艾奥尼亚附魔实例
     */
    private static Enchantment getAeoniaEnchantment() {
        return EnchantmentRegistry.getEnchantmentByClass(EnchantmentAeonia.class);
    }

    /**
     * 治疗量减少25%
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase healer = evt.getEntityLiving();
            if (healer.isPotionActive(this)) {
                evt.setAmount(evt.getAmount() * 0.75f);
            }
        }
    }

    /**
     * 攻击时25%概率传播猩红腐烂（需要周围有艾奥尼亚附魔持有者）
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                PotionEffect potionEffect = attacker.getActivePotionEffect(this);
                if (potionEffect != null) {
                    if (RandomUtil.percentageChance(25)) {
                        Enchantment aeonia = getAeoniaEnchantment();
                        if (aeonia == null) {
                            return;
                        }

                        @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                                EntityLivingBase.class,
                                attacker,
                                32
                        );
                        for (EntityLivingBase entityLivingBase : entities) {
                            if (!entityLivingBase.getHeldItem(entityLivingBase.getActiveHand()).isEmpty()) {
                                int bonusLevel = EnchantmentHelper.getEnchantmentLevel(aeonia, entityLivingBase.getHeldItem(entityLivingBase.getActiveHand()));
                                if (ConfigLoader.levelLimit) {
                                    bonusLevel = Math.min(bonusLevel, 10);
                                }
                                if (bonusLevel > 0) {
                                    EntityLivingBase hurter = evt.getEntityLiving();
                                    hurter.addPotionEffect(new PotionEffect(this, potionEffect.getDuration(), potionEffect.getAmplifier()));
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 死亡时传播猩红腐烂（需要周围有艾奥尼亚附魔持有者）
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase dead = evt.getEntityLiving();
            PotionEffect potionEffect = dead.getActivePotionEffect(this);
            if (potionEffect != null) {
                Enchantment aeonia = getAeoniaEnchantment();
                if (aeonia == null) {
                    return;
                }

                @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                        EntityLivingBase.class,
                        dead,
                        32
                );
                for (EntityLivingBase entityLivingBase : new ArrayList<>(entities)) {
                    if (!entityLivingBase.getHeldItem(entityLivingBase.getActiveHand()).isEmpty()) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(aeonia, entityLivingBase.getHeldItem(entityLivingBase.getActiveHand()));
                        if (ConfigLoader.levelLimit) {
                            bonusLevel = Math.min(bonusLevel, 10);
                        }
                        if (bonusLevel > 0) {
                            entities = EntityUtil.getNearbyEntities(
                                    EntityLivingBase.class,
                                    dead,
                                    16
                            );
                            for (EntityLivingBase target : new ArrayList<>(entities)) {
                                if (RandomUtil.percentageChance(50)) {
                                    target.addPotionEffect(new PotionEffect(this, potionEffect.getDuration(), potionEffect.getAmplifier()));
                                }
                            }
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * 每秒造成持续伤害
     * 有艾奥尼亚附魔持有者在附近时伤害×2.5
     */
    @Override
    public void performEffect(EntityLivingBase entityLivingBaseIn, int amplifier) {
        if (!entityLivingBaseIn.world.isRemote) {
            if (EntityUtil.getFire(entityLivingBaseIn) <= 0 || RandomUtil.percentageChance(25)) {
                float damage = entityLivingBaseIn.getHealth() * 0.03f + entityLivingBaseIn.getMaxHealth() * 0.00075f;
                damage += damage * amplifier * 0.33;

                Enchantment aeonia = getAeoniaEnchantment();
                if (aeonia != null) {
                    @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                            EntityLivingBase.class,
                            entityLivingBaseIn,
                            32
                    );
                    for (EntityLivingBase entityLivingBase : entities) {
                        if (!entityLivingBase.getHeldItem(entityLivingBase.getActiveHand()).isEmpty()) {
                            int bonusLevel = EnchantmentHelper.getEnchantmentLevel(aeonia, entityLivingBase.getHeldItem(entityLivingBase.getActiveHand()));
                            if (ConfigLoader.levelLimit) {
                                bonusLevel = Math.min(bonusLevel, 10);
                            }
                            if (bonusLevel > 0) {
                                entityLivingBaseIn.attackEntityFrom(NewDamageSource.SCARLET_ROT, damage * 2.5f);
                                return;
                            }
                        }
                    }
                }
                entityLivingBaseIn.attackEntityFrom(NewDamageSource.SCARLET_ROT, damage);
            }
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Nonnull
    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/scarlet_rot.png");
    }
}