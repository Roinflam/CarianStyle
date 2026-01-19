// 文件：EnchantmentCorpsePiler.java
// 路径：forge/src/main/java/pers/roinflam/carianstyle/enchantment/combatskill/EnchantmentCorpsePiler.java
package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

import java.util.UUID;

/**
 * 尸山血海附魔
 * <p>
 * 击杀敌人50%概率增加击杀计数（上限50），自身死亡时计数减半
 * 攻击时：增伤+1%×计数×等级，治疗=最大血量×0.05%×计数×等级
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "corpse_piler",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true,
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentCorpsePiler extends EnchantmentBase {

    private static final String KILL_COUNT_KEY = "corpse_piler_kills";

    public EnchantmentCorpsePiler() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时根据击杀计数增伤和治疗（HIGHEST优先级）
     */
    @Override
    protected void onDamageAsAttackerHighest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        UUID uuid = attacker.getUUID();

        int killCount = EnchantmentDataManager.getCounter(KILL_COUNT_KEY, uuid);
        if (killCount <= 0) {
            return;
        }

        // 增伤 +1% × 计数 × 等级
        ctx.addDamage(ctx.getDamage() * killCount * level * 0.01f);

        // 治疗 = 最大血量 × 0.05% × 计数 × 等级
        attacker.heal(attacker.getMaxHealth() * killCount * level * 0.0005f);
    }

    /**
     * 击杀时增加计数，死亡时计数减半
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity dead = evt.getEntity();

        Enchantment corpsePiler = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCorpsePiler.class);
        if (corpsePiler == null) {
            return;
        }

        // 击杀者增加计数
        if (evt.getSource().getDirectEntity() instanceof LivingEntity) {
            LivingEntity killer = (LivingEntity) evt.getSource().getDirectEntity();

            ItemStack heldItem = killer.getItemInHand(killer.getUsedItemHand());
            if (!heldItem.isEmpty()) {
                int level = EnchantmentHelper.getItemEnchantmentLevel(corpsePiler, heldItem);

                if (level > 0) {
                    // 50%概率增加计数
                    if (killer.level().random.nextBoolean()) {
                        int current = EnchantmentDataManager.getCounter(KILL_COUNT_KEY, killer.getUUID());
                        int newCount = Math.min(current + 1, 50);
                        EnchantmentDataManager.setCounter(KILL_COUNT_KEY, killer.getUUID(), newCount, 6000);
                    }
                }
            }
        }

        // 死亡者计数减半
        int deadCount = EnchantmentDataManager.getCounter(KILL_COUNT_KEY, dead.getUUID());
        if (deadCount > 0) {
            EnchantmentDataManager.setCounter(KILL_COUNT_KEY, dead.getUUID(), deadCount / 2, 6000);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((35 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}