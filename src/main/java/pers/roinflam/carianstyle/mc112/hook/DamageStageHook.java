package pers.roinflam.carianstyle.mc112.hook;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.util.CombatRules;
import net.minecraft.util.DamageSource;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.mc112.damage.DamageTags;
import pers.roinflam.carianstyle.mc112.damage.ICarianDamageSource;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 伤害流程里补上 1.12.2 开关表达不了的那几个 1.20.1 标签
 * <p>
 * 1.12.2 的伤害流程：{@code attackEntityFrom} → {@code LivingAttackEvent} → 盾牌检查（要求没开「无视护甲」）
 * → 无敌帧检查 → {@code damageEntity} → {@code LivingHurtEvent} → 护甲（无视护甲时跳过）
 * → 抗性提升和保护附魔（绝对伤害时都跳过）→ 吸收 → {@code LivingDamageEvent}。
 * </p>
 * <ul>
 *   <li><b>无视无敌帧</b>（{@code bypasses_cooldown}）：攻击事件在无敌帧检查之前，事件里把目标的
 *       {@code hurtResistantTime} 清零。用最低优先级、不收已取消的事件：攻击阶段各优先级的附魔都可能刚挂上这个标签，
 *       放到最后能都看到；被取消的攻击不清，免得没挨打却丢了无敌帧。</li>
 *   <li><b>受伤阶段再无视护甲</b>（{@link ICarianDamageSource#bypassesArmorAtHurt()}）：受伤事件最高优先级打开开关，
 *       这时盾牌已经检查过了，效果是「无视护甲、盾牌照挡」，和 1.20.1 的 {@code bypasses_armor} 一样。</li>
 *   <li><b>只无视抗性</b>（{@code bypasses_resistance}，猩红腐败）：伤害源是绝对伤害（抗性、保护附魔都跳过），
 *       受伤事件最低优先级手动套一次保护附魔。猩红腐败同时无视护甲，护甲那步本来就跳过，所以先套保护附魔和原版顺序等价；
 *       以后若有「不无视护甲、只无视抗性」的伤害，保护附魔会先于护甲算，结果略有出入。</li>
 *   <li><b>只无视附魔</b>（{@code bypasses_enchantments}）：反过来，手动套抗性提升（每级 -20%）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class DamageStageHook {

    private DamageStageHook() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim.world.isRemote) {
            return;
        }
        if (DamageTags.is(event.getSource(), DamageTags.BYPASSES_COOLDOWN)) {
            victim.hurtResistantTime = 0;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurtHighest(@Nonnull LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (source instanceof ICarianDamageSource && ((ICarianDamageSource) source).bypassesArmorAtHurt()
                && !source.isUnblockable()) {
            source.setDamageBypassesArmor();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurtLowest(@Nonnull LivingHurtEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        DamageSource source = event.getSource();
        if (victim.world.isRemote || !source.isDamageAbsolute() || event.getAmount() <= 0.0F) {
            return;
        }
        if (DamageTags.is(source, DamageTags.BYPASSES_RESISTANCE) && !DamageTags.is(source, DamageTags.BYPASSES_ENCHANTMENTS)) {
            // 原版 applyPotionDamageCalculations 里保护附魔那一半
            int modifier = EnchantmentHelper.getEnchantmentModifierDamage(victim.getArmorInventoryList(), source);
            if (modifier > 0) {
                event.setAmount(CombatRules.getDamageAfterMagicAbsorb(event.getAmount(), (float) modifier));
            }
        } else if (DamageTags.is(source, DamageTags.BYPASSES_ENCHANTMENTS) && !DamageTags.is(source, DamageTags.BYPASSES_RESISTANCE)
                && victim.isPotionActive(MobEffects.RESISTANCE)) {
            // 原版 applyPotionDamageCalculations 里抗性提升那一半：每级 -20%
            int reduce = (victim.getActivePotionEffect(MobEffects.RESISTANCE).getAmplifier() + 1) * 5;
            float amount = event.getAmount() * (25 - reduce);
            event.setAmount(Math.max(amount / 25.0F, 0.0F));
        }
    }
}
