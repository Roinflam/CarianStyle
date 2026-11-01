package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentDarkMoon;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.EnchantmentVicDragonThunder;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 尸山血海附魔
 *
 * 击杀敌人50%概率增加击杀计数（上限50），自身死亡时计数减半
 * 攻击时：增伤+1%×计数×等级，治疗=最大血量×0.05%×计数×等级
 */
@AutoRegisterEnchantment(
        id = "corpse_piler",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
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
    // 每层每级增伤比例
    private static final float DAMAGE_PER_KILL_PER_LEVEL = 0.01F;
    // 每层每级回复的最大生命值比例
    private static final float HEAL_PER_KILL_PER_LEVEL = 0.0005F;
    // 层数上限
    private static final int MAX_KILL_COUNT = 50;
    // 层数保持时长（tick，每次变动重新计时）
    private static final int COUNT_DURATION_TICKS = 6000;

    public EnchantmentCorpsePiler() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 满蓄力攻击时根据击杀计数增伤和治疗（HIGHEST优先级）
     * <p>
     * 回血按最大生命算、与这一下打多少无关，以前不判蓄力，连点每下都回血。
     * </p>
     */
    @Override
    protected void onDamageAsAttackerHighest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        if (!isFullyCharged(attacker)) {
            return;
        }
        UUID uuid = attacker.getUniqueID();

        int killCount = EnchantmentDataManager.getCounter(KILL_COUNT_KEY, uuid);
        if (killCount <= 0) {
            return;
        }

        // 增伤 +1% × 计数 × 等级
        ctx.addDamage(ctx.getDamage() * killCount * level * DAMAGE_PER_KILL_PER_LEVEL);

        // 治疗 = 最大血量 × 0.05% × 计数 × 等级
        attacker.heal(attacker.getMaxHealth() * killCount * level * HEAL_PER_KILL_PER_LEVEL);
    }

    /**
     * 击杀时增加计数，死亡时计数减半
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase dead = evt.getEntityLiving();

        Enchantment corpsePiler = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCorpsePiler.class);
        if (corpsePiler == null) {
            return;
        }

        // 击杀者增加计数
        if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
            EntityLivingBase killer = (EntityLivingBase) evt.getSource().getImmediateSource();

            if (!shouldBlockMobTrigger(killer, false) && !killer.getHeldItemMainhand().isEmpty()) {
                int level = EnchantmentHelper.getEnchantmentLevel(corpsePiler, killer.getHeldItemMainhand());

                if (level > 0) {
                    // 50%概率增加计数
                    if (killer.world.rand.nextBoolean()) {
                        int current = EnchantmentDataManager.getCounter(KILL_COUNT_KEY, killer.getUniqueID());
                        int newCount = Math.min(current + 1, MAX_KILL_COUNT);
                        EnchantmentDataManager.setCounter(KILL_COUNT_KEY, killer.getUniqueID(), newCount, COUNT_DURATION_TICKS);
                    }
                }
            }
        }

        // 死亡者计数减半
        int deadCount = EnchantmentDataManager.getCounter(KILL_COUNT_KEY, dead.getUniqueID());
        if (deadCount > 0) {
            EnchantmentDataManager.setCounter(KILL_COUNT_KEY, dead.getUniqueID(), deadCount / 2, COUNT_DURATION_TICKS);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((35 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}