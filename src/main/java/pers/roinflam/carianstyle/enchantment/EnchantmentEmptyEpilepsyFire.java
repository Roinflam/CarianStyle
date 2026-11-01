package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 空癫火附魔
 *
 * 弓箭附魔,双刃剑效果
 * 箭矢命中敌人时：
 * - 对敌人造成持续3秒的癫火伤害(攻击者最大生命值 × 10% × 等级 × 20%)
 * - 对自己造成持续3秒的癫火伤害(自己最大生命值 × 10%)
 * - 创造模式玩家免疫自损
 */
@AutoRegisterEnchantment(
        id = "empty_epilepsy_fire",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentEmptyEpilepsyFire extends EnchantmentBase {

    /** 火焰显示时长（tick） */
    private static final int BURN_VISUAL_DURATION = 65;
    /** 燃烧时长（tick） */
    private static final int DAMAGE_TICKS = 60;
    /** 起始延迟（tick） */
    private static final int DOT_DELAY = 5;
    /** 参与计算的等级上限 */
    private static final int LEVEL_CAP = 10;
    /** 自己烧掉的比例（按自己最大生命） */
    private static final float SELF_DAMAGE_RATIO = 0.1F;
    /** 对目标每级的比例（乘在自损量上） */
    private static final float VICTIM_DAMAGE_PER_LEVEL = 0.2F;

    public EnchantmentEmptyEpilepsyFire() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 箭命中生物时双方一起燃烧 3 秒：自己烧掉最大生命的 10%，目标烧掉「射手最大生命 ×10% × 等级 ×20%」
     * <p>
     * 以前自损 15%、对目标是自损量的「等级」倍（5 级就是射手最大生命的 75%）。
     * 两份伤害都按命中那一刻射手的最大生命一次算好，走持续伤害管理器：癫火是绝对伤害，抗性提升 V 挡不住；
     * 对目标剩不到两跳时直接处决，对自己的致死一击走伤害管线（不死图腾挡得住）。
     * 只认射手主手的弓，射手不是生物时不触发（以前直接强转会崩）。
     * </p>
     */
    @SubscribeEvent
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityArrow arrow = evt.getArrow();
        if (!(arrow.shootingEntity instanceof EntityLivingBase)) {
            return;
        }
        if (!(evt.getRayTraceResult().entityHit instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) arrow.shootingEntity;

        if (shouldBlockMobTrigger(attacker, false)) {
            return;
        }

        EntityLivingBase victim = (EntityLivingBase) evt.getRayTraceResult().entityHit;

        Enchantment emptyEpilepsyFire = EnchantmentRegistry.getEnchantmentByClass(EnchantmentEmptyEpilepsyFire.class);
        if (emptyEpilepsyFire == null) {
            return;
        }

        ItemStack heldItem = attacker.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            return;
        }

        int level = capLevel(EnchantmentHelper.getEnchantmentLevel(emptyEpilepsyFire, heldItem), LEVEL_CAP);
        if (level <= 0) {
            return;
        }

        float selfDamageTotal = attacker.getMaxHealth() * SELF_DAMAGE_RATIO;

        // 创造模式玩家不烧自己
        if (!(attacker instanceof EntityPlayer) || !((EntityPlayer) attacker).isCreative()) {
            attacker.addPotionEffect(new PotionEffect(CarianStylePotion.EPILEPSY_FIRE_BURNING, BURN_VISUAL_DURATION, 0));
            DamageOverTimeManager.applySelfLinear(attacker, selfDamageTotal / DAMAGE_TICKS, DAMAGE_TICKS, DOT_DELAY,
                    NewDamageSource.EPILEPSY_FIRE);
        }

        victim.addPotionEffect(new PotionEffect(CarianStylePotion.EPILEPSY_FIRE_BURNING, BURN_VISUAL_DURATION, 0));
        DamageOverTimeManager.applyLinear(victim, selfDamageTotal * level * VICTIM_DAMAGE_PER_LEVEL / DAMAGE_TICKS,
                DAMAGE_TICKS, DOT_DELAY, NewDamageSource.EPILEPSY_FIRE, true);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}