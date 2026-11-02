package pers.roinflam.carianstyle.annotation.context;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;

/**
 * PORT-STUB(B1a)
 *
 * 附魔上下文对象
 * <p>
 * 封装事件处理所需的所有信息和常用操作，让附魔实现代码更加简洁清晰
 * </p>
 * <p>
 * 修复记录 v2.1：
 * - knockback方向修正：LivingEntity.knockback(strength, ratioX, ratioZ) 内部会将
 *   (ratioX, ratioZ)归一化后从速度中减去，即实体被推向(ratioX, ratioZ)的反方向。
 *   因此要把对手推离持有者，ratioX应为 holder.x - opponent.x（从对手指向持有者），
 *   内部取反后对手被推向远离持有者的方向。
 *   原代码传的是 opponent.x - holder.x，效果是把对手拉向持有者。
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@Deprecated
public class EnchantmentContext {
    public EnchantmentContext(@Nonnull Object event, @Nonnull LivingEntity holder, @Nonnull ItemStack item, int level, @Nullable LivingEntity attacker, @Nullable LivingEntity victim, @Nullable DamageSource damageSource) {
    }
    public EnchantmentContext(@Nonnull Object event, @Nonnull LivingEntity holder, @Nonnull ItemStack item, int level) {
        this(event, holder, item, level, null, null, null);
    }
    @SuppressWarnings("unchecked") public <T> T getOriginalEvent() {
        return null;
    }
    @Nonnull public LivingEntity getHolder() {
        return null;
    }
    @Nonnull public ItemStack getEnchantedItem() {
        return null;
    }
    public int getLevel() {
        return 0;
    }
    @Nonnull public Level getWorld() {
        return null;
    }
    @Nullable public LivingEntity getAttacker() {
        return null;
    }
    @Nullable public LivingEntity getVictim() {
        return null;
    }
    @Nullable public DamageSource getDamageSource() {
        return null;
    }
    public boolean isAttacker() {
        return false;
    }
    public boolean isVictim() {
        return false;
    }
    @Nullable public LivingEntity getOpponent() {
        return null;
    }
    public boolean isHolderBlocking() {
        return false;
    }
    public boolean isVictimBlocking() {
        return false;
    }
    public boolean isHolderBurning() {
        return false;
    }
    public boolean isMagicDamage() {
        return false;
    }
    public boolean canHarmInCreative() {
        return false;
    }
    public boolean isDaytime() {
        return false;
    }
    public boolean isRaining() {
        return false;
    }
    public boolean isThundering() {
        return false;
    }
    public boolean holderHasPotion(@Nonnull MobEffect effect) {
        return false;
    }
    public boolean opponentHasPotion(@Nonnull MobEffect effect) {
        return false;
    }
    public void addDamage(float amount) {
    }
    public void reduceDamage(float amount) {
    }
    public void multiplyDamage(float multiplier) {
    }
    public void setDamage(float amount) {
    }
    public float getDamage() {
        return 0.0F;
    }
    public void cancelEvent() {
    }
    public void knockback(double strength) {
    }
    public void knockback(@Nonnull LivingEntity target, double strength) {
    }
    public void healHolder(float amount) {
    }
    public void healOpponent(float amount) {
    }
    public void setHealAmount(float amount) {
    }
    public float getHealAmount() {
        return 0.0F;
    }
    public void addPotionToHolder(@Nonnull MobEffect effect, int duration, int amplifier) {
    }
    public void addPotionToOpponent(@Nonnull MobEffect effect, int duration, int amplifier) {
    }
    public void removePotionFromHolder(@Nonnull MobEffect effect) {
    }
    @Nullable public MobEffectInstance getHolderPotionEffect(@Nonnull MobEffect effect) {
        return null;
    }
    public float getHolderHealth() {
        return 0.0F;
    }
    public float getHolderMaxHealth() {
        return 0.0F;
    }
    public float getHolderHealthPercentage() {
        return 0.0F;
    }
    public void setHolderHealth(float health) {
    }
    public float getOpponentHealth() {
        return 0.0F;
    }
    public float getOpponentMaxHealth() {
        return 0.0F;
    }
    @Nullable public Player getHolderAsPlayer() {
        return null;
    }
    public boolean isHolderPlayer() {
        return false;
    }
    public void setCriticalDamageModifier(float modifier) {
    }
    public float getCriticalDamageModifier() {
        return 0.0F;
    }
}

