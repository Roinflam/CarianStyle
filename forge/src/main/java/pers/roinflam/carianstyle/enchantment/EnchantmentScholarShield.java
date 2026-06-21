package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

/**
 * 学者盾附魔
 * <p>v2.2：受击反伤+受击减伤双向入口接入怪物附魔触发开关</p>
 * <p>v2.3：onLivingAttack 反伤入口新增 ThreadLocal 重入保护，
 * 阻断双方同时举学者盾对攻时反伤伤害互相触发对方反伤、形成来回反弹的事件级联。
 * 单次反伤与减伤逻辑（含被反伤者举盾减免反伤）完全保留，仅阻断连锁二次反伤。</p>
 *
 * @author RoinFlam
 * @version 2.3
 */
@AutoRegisterEnchantment(
        id = "scholar_shield",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        customType = "SHIELD",
        slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentScholarShield extends EnchantmentBase {

    /**
     * 线程级重入保护标记。
     * <p>
     * 当本线程正在执行学者盾反伤时置为 {@code true}。
     * 反伤使用带攻击者实体的 mobAttack 伤害源，会再次进入 {@link #onLivingAttack}，
     * 若被反伤者也举着学者盾则会再次反伤，形成来回反弹。
     * 此标记在 onLivingAttack 入口拦截，确保反伤只发生一次。
     * 注意：减伤监听器 {@link #onLivingHurt} 不受此标记影响，
     * 被反伤者举盾减免反伤的正常效果完整保留。
     * </p>
     */
    private static final ThreadLocal<Boolean> PROCESSING_RETALIATION = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public EnchantmentScholarShield() {
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EquipmentSlot[]{
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        });
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        // ⭐ v2.3：重入保护 —— 若当前线程正在执行学者盾反伤，直接跳过，
        // 阻断双方举盾对攻时的连锁二次反伤
        if (PROCESSING_RETALIATION.get()) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity attacker)) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角，反伤）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        if (!victim.isUsingItem()) {
            return;
        }

        ItemStack activeItem = victim.getUseItem();
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) {
            return;
        }

        Enchantment scholarShield = EnchantmentRegistry.getEnchantmentByClass(EnchantmentScholarShield.class);
        if (scholarShield == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(scholarShield, activeItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;

        // ⭐ v2.3：反伤期间置位重入标记，确保反伤伤害不会再次触发反伤；
        // try-finally 保证标记可靠复位，避免标记滞留导致学者盾反伤永久失效
        PROCESSING_RETALIATION.set(Boolean.TRUE);
        try {
            attacker.hurt(attacker.damageSources().mobAttack(victim), evt.getAmount() * level * 0.1f);
        } finally {
            PROCESSING_RETALIATION.set(Boolean.FALSE);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getSource().getDirectEntity() == null) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角，减伤）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        if (!victim.isUsingItem()) {
            return;
        }

        ItemStack activeItem = victim.getUseItem();
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) {
            return;
        }

        Enchantment scholarShield = EnchantmentRegistry.getEnchantmentByClass(EnchantmentScholarShield.class);
        if (scholarShield == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(scholarShield, activeItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;

        evt.setAmount(evt.getAmount() - evt.getAmount() * level * 0.075f);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
