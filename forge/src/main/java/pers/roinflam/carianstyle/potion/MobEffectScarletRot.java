package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
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
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);

        this.addAttributeModifier(
                Attributes.ARMOR,
                "4ffde3df-c955-f645-d34b-814fda024326",
                -0.5,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        this.addAttributeModifier(
                Attributes.ARMOR_TOUGHNESS,
                "0b4792a8-c918-bf55-5c7a-62a83b54e569",
                -0.5,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
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
        if (!evt.getEntity().level().isClientSide) {
            LivingEntity healer = evt.getEntity();
            if (healer.hasEffect(this)) {
                evt.setAmount(evt.getAmount() * 0.75f);
            }
        }
    }

    /**
     * 攻击时25%概率传播猩红腐烂（需要周围有艾奥尼亚附魔持有者）
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().level().isClientSide) {
            if (evt.getSource().getDirectEntity() instanceof LivingEntity) {
                LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();
                MobEffectInstance potionEffect = attacker.getEffect(this);
                if (potionEffect != null) {
                    if (RandomUtil.percentageChance(25)) {
                        Enchantment aeonia = getAeoniaEnchantment();
                        if (aeonia == null) {
                            return;
                        }

                        @Nonnull List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                                LivingEntity.class,
                                attacker,
                                32
                        );
                        for (LivingEntity entityLivingBase : entities) {
                            if (!entityLivingBase.getMainHandItem().isEmpty()) {
                                int bonusLevel = EnchantmentHelper.getItemEnchantmentLevel(aeonia, entityLivingBase.getMainHandItem());
                                if (ConfigLoader.levelLimit) {
                                    bonusLevel = Math.min(bonusLevel, 10);
                                }
                                if (bonusLevel > 0) {
                                    LivingEntity hurter = evt.getEntity();
                                    hurter.addEffect(new MobEffectInstance(this, potionEffect.getDuration(), potionEffect.getAmplifier()));
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
        if (!evt.getEntity().level().isClientSide) {
            LivingEntity dead = evt.getEntity();
            MobEffectInstance potionEffect = dead.getEffect(this);
            if (potionEffect != null) {
                Enchantment aeonia = getAeoniaEnchantment();
                if (aeonia == null) {
                    return;
                }

                @Nonnull List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                        LivingEntity.class,
                        dead,
                        32
                );
                for (LivingEntity entityLivingBase : new ArrayList<>(entities)) {
                    if (!entityLivingBase.getMainHandItem().isEmpty()) {
                        int bonusLevel = EnchantmentHelper.getItemEnchantmentLevel(aeonia, entityLivingBase.getMainHandItem());
                        if (ConfigLoader.levelLimit) {
                            bonusLevel = Math.min(bonusLevel, 10);
                        }
                        if (bonusLevel > 0) {
                            entities = EntityUtil.getNearbyEntities(
                                    LivingEntity.class,
                                    dead,
                                    16
                            );
                            for (LivingEntity target : new ArrayList<>(entities)) {
                                if (RandomUtil.percentageChance(50)) {
                                    target.addEffect(new MobEffectInstance(this, potionEffect.getDuration(), potionEffect.getAmplifier()));
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
    public void applyEffectTick(@Nonnull LivingEntity entityLivingBaseIn, int amplifier) {
        if (!entityLivingBaseIn.level().isClientSide) {
            if (entityLivingBaseIn.getRemainingFireTicks() <= 0 || RandomUtil.percentageChance(25)) {
                float damage = entityLivingBaseIn.getHealth() * 0.03f + entityLivingBaseIn.getMaxHealth() * 0.00075f;
                damage += damage * amplifier * 0.33;

                Enchantment aeonia = getAeoniaEnchantment();
                if (aeonia != null) {
                    @Nonnull List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                            LivingEntity.class,
                            entityLivingBaseIn,
                            32
                    );
                    for (LivingEntity entityLivingBase : entities) {
                        if (!entityLivingBase.getMainHandItem().isEmpty()) {
                            int bonusLevel = EnchantmentHelper.getItemEnchantmentLevel(aeonia, entityLivingBase.getMainHandItem());
                            if (ConfigLoader.levelLimit) {
                                bonusLevel = Math.min(bonusLevel, 10);
                            }
                            if (bonusLevel > 0) {
                                // 修正：使用 scarletRot() 方法获取 DamageSource
                                entityLivingBaseIn.hurt(NewDamageSource.scarletRot(entityLivingBaseIn.level()), damage * 2.5f);
                                return;
                            }
                        }
                    }
                }
                // 修正：使用 scarletRot() 方法获取 DamageSource
                entityLivingBaseIn.hurt(NewDamageSource.scarletRot(entityLivingBaseIn.level()), damage);
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Nonnull
    @Override
    @OnlyIn(Dist.CLIENT)
    protected ResourceLocation getIconTexture() {
        return new ResourceLocation(Reference.MOD_ID, "textures/mob_effect/scarlet_rot.png");
    }
}