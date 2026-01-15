package pers.roinflam.carianstyle.enchantment.context;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 附魔上下文对象
 * <p>
 * 封装事件处理所需的所有信息和常用操作，让附魔实现代码更加简洁清晰
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
public class EnchantmentContext {

    // ==================== 基础信息 ====================

    /**
     * 原始事件对象
     */
    private final Object originalEvent;

    /**
     * 附魔持有者（装备附魔的实体）
     */
    private final EntityLivingBase enchantmentHolder;

    /**
     * 附魔所在的物品
     */
    private final ItemStack enchantedItem;

    /**
     * 附魔等级（已经过等级上限验证）
     */
    private final int enchantmentLevel;

    /**
     * 世界对象
     */
    private final World world;

    // ==================== 角色信息 ====================

    /**
     * 攻击者（如果当前上下文涉及攻击）
     */
    @Nullable
    private final EntityLivingBase attacker;

    /**
     * 受击者（如果当前上下文涉及受击）
     */
    @Nullable
    private final EntityLivingBase victim;

    /**
     * 伤害来源
     */
    @Nullable
    private final DamageSource damageSource;

    /**
     * 当前实体是否为攻击者
     */
    private final boolean isAttacker;

    // ==================== 构造函数 ====================

    /**
     * 创建伤害相关事件的上下文
     *
     * @param event 原始事件
     * @param holder 附魔持有者
     * @param item 附魔物品
     * @param level 附魔等级
     * @param attacker 攻击者
     * @param victim 受击者
     * @param damageSource 伤害来源
     */
    public EnchantmentContext(
            @Nonnull Object event,
            @Nonnull EntityLivingBase holder,
            @Nonnull ItemStack item,
            int level,
            @Nullable EntityLivingBase attacker,
            @Nullable EntityLivingBase victim,
            @Nullable DamageSource damageSource
    ) {
        this.originalEvent = event;
        this.enchantmentHolder = holder;
        this.enchantedItem = item;
        this.enchantmentLevel = level;
        this.world = holder.world;
        this.attacker = attacker;
        this.victim = victim;
        this.damageSource = damageSource;
        this.isAttacker = holder.equals(attacker);
    }

    /**
     * 创建简单事件的上下文（不涉及攻击）
     *
     * @param event 原始事件
     * @param holder 附魔持有者
     * @param item 附魔物品
     * @param level 附魔等级
     */
    public EnchantmentContext(
            @Nonnull Object event,
            @Nonnull EntityLivingBase holder,
            @Nonnull ItemStack item,
            int level
    ) {
        this(event, holder, item, level, null, null, null);
    }

    // ==================== 基础获取方法 ====================

    /**
     * 获取原始事件对象
     *
     * @param <T> 事件类型
     * @return 原始事件
     */
    @SuppressWarnings("unchecked")
    public <T> T getOriginalEvent() {
        return (T) originalEvent;
    }

    /**
     * 获取附魔持有者
     *
     * @return 附魔持有者
     */
    @Nonnull
    public EntityLivingBase getHolder() {
        return enchantmentHolder;
    }

    /**
     * 获取附魔物品
     *
     * @return 附魔物品
     */
    @Nonnull
    public ItemStack getEnchantedItem() {
        return enchantedItem;
    }

    /**
     * 获取附魔等级
     *
     * @return 附魔等级
     */
    public int getLevel() {
        return enchantmentLevel;
    }

    /**
     * 获取世界对象
     *
     * @return 世界对象
     */
    @Nonnull
    public World getWorld() {
        return world;
    }

    /**
     * 获取攻击者
     *
     * @return 攻击者，如果不涉及攻击则为null
     */
    @Nullable
    public EntityLivingBase getAttacker() {
        return attacker;
    }

    /**
     * 获取受击者
     *
     * @return 受击者，如果不涉及受击则为null
     */
    @Nullable
    public EntityLivingBase getVictim() {
        return victim;
    }

    /**
     * 获取伤害来源
     *
     * @return 伤害来源，如果不涉及伤害则为null
     */
    @Nullable
    public DamageSource getDamageSource() {
        return damageSource;
    }

    /**
     * 判断附魔持有者是否为攻击者
     *
     * @return 如果持有者是攻击者返回true
     */
    public boolean isAttacker() {
        return isAttacker;
    }

    /**
     * 判断附魔持有者是否为受击者
     *
     * @return 如果持有者是受击者返回true
     */
    public boolean isVictim() {
        return !isAttacker && victim != null;
    }

    /**
     * 获取对手实体（如果持有者是攻击者则返回受击者，反之返回攻击者）
     *
     * @return 对手实体，如果无法确定则为null
     */
    @Nullable
    public EntityLivingBase getOpponent() {
        if (isAttacker) {
            return victim;
        } else {
            return attacker;
        }
    }

    // ==================== 状态判断方法 ====================

    /**
     * 判断持有者是否正在格挡
     *
     * @return 如果正在格挡返回true
     */
    public boolean isHolderBlocking() {
        return enchantmentHolder.isHandActive() && enchantmentHolder.getActiveItemStack().getItem() instanceof net.minecraft.item.ItemShield;
    }

    /**
     * 判断受击者是否正在格挡
     *
     * @return 如果受击者正在格挡返回true
     */
    public boolean isVictimBlocking() {
        return victim != null && victim.isHandActive() && victim.getActiveItemStack().getItem() instanceof net.minecraft.item.ItemShield;
    }

    /**
     * 判断持有者是否在燃烧
     *
     * @return 如果在燃烧返回true
     */
    public boolean isHolderBurning() {
        return enchantmentHolder.isBurning();
    }

    /**
     * 判断是否为魔法伤害
     *
     * @return 如果是魔法伤害返回true
     */
    public boolean isMagicDamage() {
        return damageSource != null && damageSource.isMagicDamage();
    }

    /**
     * 判断伤害是否可以伤害创造模式玩家
     *
     * @return 如果可以伤害创造模式玩家返回true
     */
    public boolean canHarmInCreative() {
        return damageSource != null && damageSource.canHarmInCreative();
    }

    /**
     * 判断是否为白天
     *
     * @return 如果是白天返回true
     */
    public boolean isDaytime() {
        return world.isDaytime();
    }

    /**
     * 判断是否在下雨
     *
     * @return 如果在下雨返回true
     */
    public boolean isRaining() {
        return world.isRaining();
    }

    /**
     * 判断是否在打雷
     *
     * @return 如果在打雷返回true
     */
    public boolean isThundering() {
        return world.isThundering();
    }

    /**
     * 判断持有者是否拥有指定药水效果
     *
     * @param potion 药水效果
     * @return 如果拥有该效果返回true
     */
    public boolean holderHasPotion(@Nonnull Potion potion) {
        return enchantmentHolder.isPotionActive(potion);
    }

    /**
     * 判断对手是否拥有指定药水效果
     *
     * @param potion 药水效果
     * @return 如果拥有该效果返回true
     */
    public boolean opponentHasPotion(@Nonnull Potion potion) {
        EntityLivingBase opponent = getOpponent();
        return opponent != null && opponent.isPotionActive(potion);
    }

    // ==================== 伤害修改方法 ====================

    /**
     * 增加伤害（绝对值）
     *
     * @param amount 增加的伤害值
     */
    public void addDamage(float amount) {
        if (originalEvent instanceof LivingHurtEvent) {
            LivingHurtEvent event = (LivingHurtEvent) originalEvent;
            event.setAmount(event.getAmount() + amount);
        } else if (originalEvent instanceof LivingDamageEvent) {
            LivingDamageEvent event = (LivingDamageEvent) originalEvent;
            event.setAmount(event.getAmount() + amount);
        }
    }

    /**
     * 减少伤害（绝对值）
     *
     * @param amount 减少的伤害值
     */
    public void reduceDamage(float amount) {
        addDamage(-amount);
    }

    /**
     * 伤害倍率修改（百分比）
     *
     * @param multiplier 倍率（1.5表示增加50%，0.5表示减少50%）
     */
    public void multiplyDamage(float multiplier) {
        if (originalEvent instanceof LivingHurtEvent) {
            LivingHurtEvent event = (LivingHurtEvent) originalEvent;
            event.setAmount(event.getAmount() * multiplier);
        } else if (originalEvent instanceof LivingDamageEvent) {
            LivingDamageEvent event = (LivingDamageEvent) originalEvent;
            event.setAmount(event.getAmount() * multiplier);
        }
    }

    /**
     * 设置伤害值
     *
     * @param amount 新的伤害值
     */
    public void setDamage(float amount) {
        if (originalEvent instanceof LivingHurtEvent) {
            LivingHurtEvent event = (LivingHurtEvent) originalEvent;
            event.setAmount(amount);
        } else if (originalEvent instanceof LivingDamageEvent) {
            LivingDamageEvent event = (LivingDamageEvent) originalEvent;
            event.setAmount(amount);
        }
    }

    /**
     * 获取当前伤害值
     *
     * @return 当前伤害值
     */
    public float getDamage() {
        if (originalEvent instanceof LivingHurtEvent) {
            return ((LivingHurtEvent) originalEvent).getAmount();
        } else if (originalEvent instanceof LivingDamageEvent) {
            return ((LivingDamageEvent) originalEvent).getAmount();
        }
        return 0.0f;
    }

    /**
     * 取消事件（阻止伤害或效果）
     */
    public void cancelEvent() {
        if (originalEvent instanceof LivingAttackEvent) {
            ((LivingAttackEvent) originalEvent).setCanceled(true);
        } else if (originalEvent instanceof LivingHurtEvent) {
            ((LivingHurtEvent) originalEvent).setCanceled(true);
        } else if (originalEvent instanceof LivingDamageEvent) {
            ((LivingDamageEvent) originalEvent).setCanceled(true);
        } else if (originalEvent instanceof LivingDeathEvent) {
            ((LivingDeathEvent) originalEvent).setCanceled(true);
        }
    }

    /**
     * 设置伤害来源为魔法伤害
     */
    public void setMagicDamage() {
        if (damageSource != null) {
            damageSource.setMagicDamage();
        }
    }

    // ==================== 击退方法 ====================

    /**
     * 对对手施加击退效果（自动计算方向）
     *
     * @param strength 击退强度
     */
    public void knockback(float strength) {
        EntityLivingBase opponent = getOpponent();
        if (opponent != null && enchantmentHolder != null) {
            double x = opponent.posX - enchantmentHolder.posX;
            double z = opponent.posZ - enchantmentHolder.posZ;
            opponent.knockBack(enchantmentHolder, strength, x, z);
        }
    }

    /**
     * 对指定实体施加击退效果
     *
     * @param target 目标实体
     * @param strength 击退强度
     */
    public void knockback(@Nonnull EntityLivingBase target, float strength) {
        if (enchantmentHolder != null) {
            double x = target.posX - enchantmentHolder.posX;
            double z = target.posZ - enchantmentHolder.posZ;
            target.knockBack(enchantmentHolder, strength, x, z);
        }
    }

    // ==================== 治疗方法 ====================

    /**
     * 治疗持有者
     *
     * @param amount 治疗量
     */
    public void healHolder(float amount) {
        if (enchantmentHolder != null) {
            enchantmentHolder.heal(amount);
        }
    }

    /**
     * 治疗对手
     *
     * @param amount 治疗量
     */
    public void healOpponent(float amount) {
        EntityLivingBase opponent = getOpponent();
        if (opponent != null) {
            opponent.heal(amount);
        }
    }

    /**
     * 修改治疗量（仅在LivingHealEvent中有效）
     *
     * @param amount 新的治疗量
     */
    public void setHealAmount(float amount) {
        if (originalEvent instanceof LivingHealEvent) {
            ((LivingHealEvent) originalEvent).setAmount(amount);
        }
    }

    /**
     * 获取治疗量（仅在LivingHealEvent中有效）
     *
     * @return 治疗量
     */
    public float getHealAmount() {
        if (originalEvent instanceof LivingHealEvent) {
            return ((LivingHealEvent) originalEvent).getAmount();
        }
        return 0.0f;
    }

    // ==================== 药水效果方法 ====================

    /**
     * 给持有者添加药水效果
     *
     * @param potion 药水效果
     * @param duration 持续时间（tick）
     * @param amplifier 效果等级
     */
    public void addPotionToHolder(@Nonnull Potion potion, int duration, int amplifier) {
        if (enchantmentHolder != null) {
            enchantmentHolder.addPotionEffect(new PotionEffect(potion, duration, amplifier));
        }
    }

    /**
     * 给对手添加药水效果
     *
     * @param potion 药水效果
     * @param duration 持续时间（tick）
     * @param amplifier 效果等级
     */
    public void addPotionToOpponent(@Nonnull Potion potion, int duration, int amplifier) {
        EntityLivingBase opponent = getOpponent();
        if (opponent != null) {
            opponent.addPotionEffect(new PotionEffect(potion, duration, amplifier));
        }
    }

    /**
     * 移除持有者的药水效果
     *
     * @param potion 要移除的药水效果
     */
    public void removePotionFromHolder(@Nonnull Potion potion) {
        if (enchantmentHolder != null) {
            enchantmentHolder.removePotionEffect(potion);
        }
    }

    /**
     * 获取持有者的药水效果
     *
     * @param potion 药水效果
     * @return 药水效果实例，如果没有则为null
     */
    @Nullable
    public PotionEffect getHolderPotionEffect(@Nonnull Potion potion) {
        if (enchantmentHolder != null) {
            return enchantmentHolder.getActivePotionEffect(potion);
        }
        return null;
    }

    // ==================== 生命值相关方法 ====================

    /**
     * 获取持有者当前生命值
     *
     * @return 当前生命值
     */
    public float getHolderHealth() {
        return enchantmentHolder.getHealth();
    }

    /**
     * 获取持有者最大生命值
     *
     * @return 最大生命值
     */
    public float getHolderMaxHealth() {
        return enchantmentHolder.getMaxHealth();
    }

    /**
     * 获取持有者生命值百分比
     *
     * @return 生命值百分比（0.0-1.0）
     */
    public float getHolderHealthPercentage() {
        return enchantmentHolder.getHealth() / enchantmentHolder.getMaxHealth();
    }

    /**
     * 设置持有者生命值
     *
     * @param health 新的生命值
     */
    public void setHolderHealth(float health) {
        enchantmentHolder.setHealth(health);
    }

    /**
     * 获取对手当前生命值
     *
     * @return 当前生命值，如果没有对手则为0
     */
    public float getOpponentHealth() {
        EntityLivingBase opponent = getOpponent();
        return opponent != null ? opponent.getHealth() : 0.0f;
    }

    /**
     * 获取对手最大生命值
     *
     * @return 最大生命值，如果没有对手则为0
     */
    public float getOpponentMaxHealth() {
        EntityLivingBase opponent = getOpponent();
        return opponent != null ? opponent.getMaxHealth() : 0.0f;
    }

    // ==================== 玩家特定方法 ====================

    /**
     * 获取持有者作为玩家（如果是玩家）
     *
     * @return 玩家实例，如果不是玩家则为null
     */
    @Nullable
    public EntityPlayer getHolderAsPlayer() {
        return enchantmentHolder instanceof EntityPlayer ? (EntityPlayer) enchantmentHolder : null;
    }

    /**
     * 判断持有者是否为玩家
     *
     * @return 如果是玩家返回true
     */
    public boolean isHolderPlayer() {
        return enchantmentHolder instanceof EntityPlayer;
    }

    // ==================== 暴击相关方法 ====================

    /**
     * 修改暴击倍率（仅在CriticalHitEvent中有效）
     *
     * @param modifier 暴击倍率修正值
     */
    public void setCriticalDamageModifier(float modifier) {
        if (originalEvent instanceof CriticalHitEvent) {
            ((CriticalHitEvent) originalEvent).setDamageModifier(modifier);
        }
    }

    /**
     * 获取暴击倍率（仅在CriticalHitEvent中有效）
     *
     * @return 暴击倍率
     */
    public float getCriticalDamageModifier() {
        if (originalEvent instanceof CriticalHitEvent) {
            return ((CriticalHitEvent) originalEvent).getDamageModifier();
        }
        return 1.0f;
    }
}