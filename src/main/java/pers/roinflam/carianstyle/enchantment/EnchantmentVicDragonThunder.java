package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;

/**
 * 古龙雷附魔
 *
 * 武器附魔，雷电攻击
 * 防御效果（受到雷电伤害时）：
 * - 减少 15% × 等级 的雷电伤害（超过100%时完全免疫）
 * 攻击效果：
 * - 概率召唤落雷（雷暴时100%，下雨时10%×等级，晴天5%×等级）
 * - 造成额外 50% × 等级 × 天气倍率 的雷电伤害
 * - 天气倍率：晴天1，下雨2，雷暴4
 * <p>
 * 落雷要求满蓄力，以前连点每一下都掷一次，雷暴天每下必出。
 * 天气倍率先判雷暴：以前先判下雨，雷暴时一定也在下雨，×4 永远走不到，雷雨天实际只有 ×2。
 * 持有物一律看主手，以前看正在使用的那只手，举着副手盾时会变成副手。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "vic_dragon_thunder",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentVicDragonThunder extends EnchantmentBase {

    // 每级雷电伤害减免比例
    private static final float REDUCTION_PER_LEVEL = 0.15F;
    // 每级落雷基础概率（%）
    private static final int CHANCE_PER_LEVEL = 5;
    // 每级落雷伤害（本次最终伤害的倍数）
    private static final float DAMAGE_PER_LEVEL = 0.5F;
    // 落雷后受击者的无敌时间（tick）
    private static final int STRIKE_INVULNERABLE_TIME = 10;
    // 等级上限（开了等级限制时）
    private static final int LEVEL_CAP = 10;

    public EnchantmentVicDragonThunder() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 雷电伤害减免和攻击时召唤落雷
     * 由于同一个事件需要处理两种情况（受击减伤+攻击增伤），保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        Enchantment vicDragonThunder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentVicDragonThunder.class);
        if (vicDragonThunder == null) {
            return;
        }

        // 防御：雷电伤害减免
        if (!shouldBlockMobTrigger(victim, false)
                && !evt.getSource().canHarmInCreative() && "lightningBolt".equals(evt.getSource().damageType)) {
            ItemStack victimHeld = victim.getHeldItemMainhand();
            if (!victimHeld.isEmpty()) {
                int level = capLevel(EnchantmentHelper.getEnchantmentLevel(vicDragonThunder, victimHeld), LEVEL_CAP);
                if (level > 0) {
                    // 减免 15% × 等级（超过100%时完全免疫）
                    if (level * REDUCTION_PER_LEVEL >= 1) {
                        evt.setCanceled(true);
                        return;
                    }
                    evt.setAmount(evt.getAmount() - evt.getAmount() * level * REDUCTION_PER_LEVEL);
                }
            }
        }

        // 攻击：召唤落雷（近战，直接来源必须是生物）
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
        if (shouldBlockMobTrigger(attacker, false)) {
            return;
        }

        ItemStack attackerHeld = attacker.getHeldItemMainhand();
        if (attackerHeld.isEmpty()) {
            return;
        }

        int level = capLevel(EnchantmentHelper.getEnchantmentLevel(vicDragonThunder, attackerHeld), LEVEL_CAP);
        if (level <= 0) {
            return;
        }

        // 连点会让每一下都掷一次落雷，要求满蓄力
        if (!isFullyCharged(attacker)) {
            return;
        }

        // 计算触发概率：雷暴100%，下雨10%×等级，晴天5%×等级
        int triggerChance;
        if (attacker.world.isThundering()) {
            triggerChance = 100;
        } else if (attacker.world.isRaining()) {
            triggerChance = level * CHANCE_PER_LEVEL * 2;
        } else {
            triggerChance = level * CHANCE_PER_LEVEL;
        }

        if (!RandomUtil.percentageChance(triggerChance)) {
            return;
        }

        World world = victim.world;

        // 召唤落雷
        world.addWeatherEffect(new EntityLightningBolt(
                world,
                victim.posX,
                victim.posY,
                victim.posZ,
                true
        ));

        // 重置无敌帧
        victim.hurtResistantTime = STRIKE_INVULNERABLE_TIME;

        // 计算天气倍率：雷暴时一定也在下雨，要先判雷暴
        int magnification = 1;
        if (attacker.world.isThundering()) {
            magnification = 4;
        } else if (attacker.world.isRaining()) {
            magnification = 2;
        }

        // 造成额外雷电伤害
        victim.attackEntityFrom(DamageSource.LIGHTNING_BOLT, evt.getAmount() * level * DAMAGE_PER_LEVEL * magnification);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentScarletCorruption.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentFireGivesPower.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentFireDevoured.class));
    }
}