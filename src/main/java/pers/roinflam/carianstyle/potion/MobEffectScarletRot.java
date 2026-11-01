package pers.roinflam.carianstyle.potion;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.CombatRules;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.enchantment.EnchantmentAeonia;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 猩红腐烂药水效果
 *
 * 效果：
 * - 护甲和韧性减少50%
 * - 治疗量减少25%
 * - 每秒造成持续伤害
 * - 与艾奥尼亚附魔联动：附近 32 格内有主手持艾奥尼亚的生物时伤害 ×2.5，攻击时 25% 概率传给受击者，
 *   死亡时以 50% 概率扩散给周围 16 格内的生物（10 秒，沿用死者的等级）
 * <p>
 * 以前死亡扩散的时长沿用死者身上剩余的时长；艾奥尼亚持有者按「最近一次使用物品的那只手」判断，
 * 用副手吃东西、举盾后会一直指向副手。现在扩散固定 10 秒、只看主手。
 * 附近有没有艾奥尼亚持有者每个实体缓存 3 秒，每秒的持续伤害不用每次都扫 32 格。
 * </p>
 */
public class MobEffectScarletRot extends IconBase {

    /** 艾奥尼亚邻近判定的缓存有效期（tick） */
    private static final long AEONIA_CACHE_TTL_TICKS = 60L;
    /** 缓存条目超过这个数时，写入前顺手清掉过期的 */
    private static final int AEONIA_CACHE_SWEEP_THRESHOLD = 128;
    /** 艾奥尼亚持有者的搜索半径（格） */
    private static final double AEONIA_SEARCH_RADIUS = 32.0D;
    /** 死亡扩散的半径（格） */
    private static final double DEATH_SPREAD_RADIUS = 16.0D;
    /** 死亡扩散的时长（tick） */
    private static final int DEATH_SPREAD_DURATION = 200;
    /** 死亡扩散给每个目标的概率（%） */
    private static final double DEATH_SPREAD_CHANCE = 50.0D;
    /** 攻击传染的概率（%） */
    private static final double HIT_SPREAD_CHANCE = 25.0D;
    /** 治疗量倍率 */
    private static final float HEAL_MULTIPLIER = 0.75F;
    /** 附近有艾奥尼亚持有者时的伤害倍率 */
    private static final float AEONIA_DAMAGE_MULTIPLIER = 2.5F;

    /** 艾奥尼亚邻近判定缓存：实体 UUID → {结果(1/0), 过期的世界时间} */
    private static final Map<UUID, long[]> AEONIA_CACHE = new HashMap<>();

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
     * 32 格内有没有主手持艾奥尼亚的生物（结果按实体缓存 3 秒）
     *
     * @param center 中心实体
     * @return 有则为 true
     */
    private static boolean hasAeoniaHolderNearby(@Nonnull EntityLivingBase center) {
        long now = center.world.getTotalWorldTime();
        UUID id = center.getUniqueID();

        long[] cached = AEONIA_CACHE.get(id);
        if (cached != null && now < cached[1]) {
            return cached[0] != 0L;
        }

        boolean result = scanForAeoniaHolder(center);

        if (AEONIA_CACHE.size() > AEONIA_CACHE_SWEEP_THRESHOLD) {
            AEONIA_CACHE.values().removeIf(entry -> now >= entry[1]);
        }
        AEONIA_CACHE.put(id, new long[]{result ? 1L : 0L, now + AEONIA_CACHE_TTL_TICKS});
        return result;
    }

    private static boolean scanForAeoniaHolder(@Nonnull EntityLivingBase center) {
        Enchantment aeonia = getAeoniaEnchantment();
        if (aeonia == null) {
            return false;
        }

        List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                center,
                AEONIA_SEARCH_RADIUS
        );
        for (EntityLivingBase entityLivingBase : entities) {
            ItemStack mainHand = entityLivingBase.getHeldItemMainhand();
            if (mainHand.isEmpty() || !mainHand.isItemEnchanted()) {
                continue;
            }
            if (EnchantmentHelper.getEnchantmentLevel(aeonia, mainHand) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 治疗量减少25%
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase healer = evt.getEntityLiving();
            if (healer.isPotionActive(this)) {
                evt.setAmount(evt.getAmount() * HEAL_MULTIPLIER);
            }
        }
    }

    /**
     * 攻击时25%概率传播猩红腐烂（需要周围有艾奥尼亚附魔持有者），沿用攻击者剩余的时长和等级
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
        PotionEffect potionEffect = attacker.getActivePotionEffect(this);
        if (potionEffect != null && RandomUtil.percentageChance(HIT_SPREAD_CHANCE) && hasAeoniaHolderNearby(attacker)) {
            evt.getEntityLiving().addPotionEffect(new PotionEffect(this, potionEffect.getDuration(), potionEffect.getAmplifier()));
        }
    }

    /**
     * 死亡时传播猩红腐烂（需要周围有艾奥尼亚附魔持有者）：16 格内每个生物 50% 概率染上 10 秒，等级沿用死者的
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }
        EntityLivingBase dead = evt.getEntityLiving();
        PotionEffect potionEffect = dead.getActivePotionEffect(this);
        if (potionEffect == null) {
            return;
        }

        if (hasAeoniaHolderNearby(dead)) {
            List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                    EntityLivingBase.class,
                    dead,
                    DEATH_SPREAD_RADIUS
            );
            for (EntityLivingBase target : new ArrayList<>(entities)) {
                if (RandomUtil.percentageChance(DEATH_SPREAD_CHANCE)) {
                    target.addPotionEffect(new PotionEffect(this, DEATH_SPREAD_DURATION, potionEffect.getAmplifier()));
                }
            }
        }
        AEONIA_CACHE.remove(dead.getUniqueID());
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

                if (hasAeoniaHolderNearby(entityLivingBaseIn)) {
                    hurtThroughProtection(entityLivingBaseIn, damage * AEONIA_DAMAGE_MULTIPLIER);
                    return;
                }
                hurtThroughProtection(entityLivingBaseIn, damage);
            }
        }
    }

    /**
     * 造成猩红腐败伤害：无视抗性提升，保护类附魔照常减免
     * <p>
     * 伤害源是绝对伤害，原版会把抗性和保护附魔一起跳过，所以保护附魔这里先按原版公式算掉。
     * 以前抗性提升 V 能把腐败整个挡掉。
     * </p>
     *
     * @param entity 目标
     * @param damage 伤害
     */
    private static void hurtThroughProtection(@Nonnull EntityLivingBase entity, float damage) {
        int protection = EnchantmentHelper.getEnchantmentModifierDamage(entity.getArmorInventoryList(), NewDamageSource.SCARLET_ROT);
        if (protection > 0) {
            damage = CombatRules.getDamageAfterMagicAbsorb(damage, (float) protection);
        }
        entity.attackEntityFrom(NewDamageSource.SCARLET_ROT, damage);
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