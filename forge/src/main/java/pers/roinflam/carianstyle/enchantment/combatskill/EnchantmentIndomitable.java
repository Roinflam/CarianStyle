package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

/**
 * 不屈附魔
 * <p>v2.1：受击者视角接入怪物附魔触发开关</p>
 * <p>v2.2：免疫成功时触发「不屈壁障」自绘特效</p>
 *
 * <h3>v2.2 为什么要加这个特效</h3>
 * <p>
 * 本附魔的免疫概率是 {@code 缺失血量百分比 × 75%}——残血时接近必定免疫，
 * 但在此之前<b>玩家完全看不出它有没有生效</b>：血条没动，可能是免疫了，
 * 也可能是对方压根没打中，还可能是别的减伤挡下来了。
 * 一个只在真正免疫时出现的视觉反馈，补的正是这个缺口。
 * </p>
 * <p>
 * <b>严格放在 {@code setCanceled(true)} 的同一分支里</b>：概率没中的时候不能播，
 * 否则视觉就在骗人，比没有反馈更糟。
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "indomitable",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST},
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentIndomitable extends EnchantmentBase {

    public EnchantmentIndomitable() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        LivingEntity holder = evt.getEntity();

        // ⭐ v2.1：怪物附魔触发开关（受击者视角，低血量伤害免疫）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

        Enchantment indomitable = EnchantmentRegistry.getEnchantmentByClass(EnchantmentIndomitable.class);
        if (indomitable == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(indomitable, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        float missingHealthPercent = 1 - holder.getHealth() / holder.getMaxHealth();
        double immuneChance = missingHealthPercent * 100 * 0.75;

        if (RandomUtil.percentageChance(immuneChance)) {
            evt.setCanceled(true);

            // ⭐ v2.2：免疫真正生效，播放「不屈壁障」自绘特效。
            // 位置与朝向都取持有者——这是「我把这一下弹回去了」的自身反馈
            if (holder.level() instanceof ServerLevel serverLevel) {
                CarianStyleCombatArtEffects.indomitable(serverLevel, holder);
            }
        }
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        if (ench == Enchantments.ALL_DAMAGE_PROTECTION ||
                ench == Enchantments.FIRE_PROTECTION ||
                ench == Enchantments.PROJECTILE_PROTECTION ||
                ench == Enchantments.BLAST_PROTECTION) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
