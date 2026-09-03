package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

/**
 * 吃屎附魔
 * <p>v2.2：攻击者+治疗事件入口接入怪物附魔触发开关</p>
 *
 * <h3>v2.3：{@link #DEBUFF_KEY} 改为 public（行为零变化）</h3>
 * <p>
 * {@code CarianStyleConditionDisplay} 需要读这条记录来显示「治疗被削减，还剩几秒」。
 * 这个 debuff <b>不是原版药水效果</b>，屏幕右侧不会出现任何图标——
 * 中招的玩家只会觉得「我怎么喝了药还是不回血」，完全无从判断。
 * </p>
 * <p>
 * 与其在 HUD 那边复制一份 {@code "eat_shit_debuff"} 字面量，不如把常量公开：
 * 复制的字面量不会跟着改，哪天这里改了键名而那边没跟上，
 * HUD 会安静地永远显示「无」，既不报错也不会被测试发现。
 * </p>
 * <p>
 * <b>本次只改了这一个字段的可见性修饰符，其余逻辑一行未动。</b>
 * </p>
 *
 * @version 2.3
 */
@AutoRegisterEnchantment(id = "eat_shit", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND})
@Mod.EventBusSubscriber
public class EnchantmentEatShit extends EnchantmentBase {

    /**
     * 治疗削减 debuff 在 {@link EnchantmentDataManager} 中的键。
     * <p><b>v2.3 由 private 改为 public</b>：HUD 需要用同一个键读取剩余时间。
     * 注意这条记录是打在<b>受击者</b>身上的，与受击者自己的装备无关（详见类注释）。</p>
     */
    public static final String DEBUFF_KEY = "eat_shit_debuff";

    public EnchantmentEatShit() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity attacker)) return;

        // ⭐ v2.2：怪物附魔触发开关（攻击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        LivingEntity victim = evt.getEntity();
        Enchantment eatShit = EnchantmentRegistry.getEnchantmentByClass(EnchantmentEatShit.class);
        if (eatShit == null) return;
        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(eatShit, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;
        int victimDuration = level * 80;
        victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, victimDuration));
        attacker.addEffect(new MobEffectInstance(MobEffects.CONFUSION, level * 30));
        EnchantmentDataManager.setCooldown(DEBUFF_KEY, victim.getUUID(), victimDuration);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@NotNull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide) return;

        // ⭐ v2.2：怪物附魔触发开关（受治疗者视角，被附加的debuff效果）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(evt.getEntity(), false)) return;

        if (EnchantmentDataManager.isOnCooldown(DEBUFF_KEY, evt.getEntity().getUUID())) {
            evt.setAmount(evt.getAmount() * 0.25f);
        }
    }

    @Override public int getMinCost(int l) { return (int)((25 + (l - 1) * 2) * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
