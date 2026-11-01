package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
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

import javax.annotation.Nonnull;

/**
 * 癫火附魔
 *
 * 武器附魔，双刃剑效果
 * 攻击敌人时：
 * - 对敌人造成持续3秒的癫火伤害（攻击者最大生命值 × 30% × 等级 × 50% = 15% × 等级）
 * - 对自己造成持续3秒的癫火伤害（自己最大生命值 × 30%）
 * - 创造模式玩家免疫自损
 * - 玩家必须是刚挥动武器时触发
 */
@AutoRegisterEnchantment(
        id = "epilepsy_fire",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentEpilepsyFire extends EnchantmentBase {

    /** 火焰显示时长（tick） */
    private static final int BURN_VISUAL_DURATION = 65;
    /** 燃烧时长（tick） */
    private static final int DAMAGE_TICKS = 60;
    /** 起始延迟（tick） */
    private static final int DOT_DELAY = 5;
    /** 参与计算的等级上限 */
    private static final int LEVEL_CAP = 10;
    /** 自己烧掉的比例（按自己最大生命） */
    private static final float SELF_DAMAGE_RATIO = 0.2F;
    /** 对目标每级的比例（乘在自损量上） */
    private static final float VICTIM_DAMAGE_PER_LEVEL = 0.1F;

    public EnchantmentEpilepsyFire() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 满蓄力命中时双方一起燃烧 3 秒：自己烧掉最大生命的 20%，目标烧掉「持有者最大生命 ×20% × 等级 ×10%」
     * <p>
     * 癫火伤害是绝对伤害，抗性提升 V 挡不住；对目标剩不到两跳时直接处决，
     * 对自己的致死一击走伤害管线（不死图腾挡得住）。
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
        Enchantment epilepsyFire = EnchantmentRegistry.getEnchantmentByClass(EnchantmentEpilepsyFire.class);
        if (epilepsyFire == null) {
            return;
        }

        ItemStack heldItem = attacker.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            return;
        }

        int level = capLevel(EnchantmentHelper.getEnchantmentLevel(epilepsyFire, heldItem), LEVEL_CAP);
        if (level <= 0) {
            return;
        }

        if (!isFullyCharged(attacker)) {
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
        return (int) ((5 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }
}