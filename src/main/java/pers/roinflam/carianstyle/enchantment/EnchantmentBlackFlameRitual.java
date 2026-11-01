package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.gameevent.TickEvent;
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

import javax.annotation.Nonnull;

/**
 * 黑焰仪式附魔
 *
 * 攻击：根据自身药水效果数量增伤（正面+10%，负面+20%）
 * 被动：有药水效果时每秒扣血5%并施加灭绝火焰
 */
@AutoRegisterEnchantment(
        id = "black_flame_ritual",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        conflictsWith = {
                EnchantmentShelterOfFire.class,
                EnchantmentHealingByFire.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentBlackFlameRitual extends EnchantmentBase {

    /** 每个增益效果的增伤 */
    private static final float BENEFICIAL_BONUS = 0.1F;
    /** 每个负面 / 中性效果的增伤 */
    private static final float HARMFUL_BONUS = 0.2F;
    /** 自烧的间隔（tick） */
    private static final int TICK_INTERVAL = 20;
    /** 每次自烧后保留的生命比例（烧掉当前生命的 5%） */
    private static final float HEALTH_RETAIN_RATIO = 0.95F;

    /**
     * 只能附在胸甲上；以前任意护甲件都能附
     */
    public EnchantmentBlackFlameRitual() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    /**
     * 算进黑焰仪式的效果：非瞬间、显示在界面上、且粒子可见
     * <p>
     * 用指令隐藏粒子的效果不算；本模组自己的隐藏效果（包括这里挂的灭绝火焰）也不算，否则会自己烧自己停不下来。
     * </p>
     */
    private static boolean isCounted(@Nonnull PotionEffect effect) {
        Potion potion = effect.getPotion();
        return !potion.isInstant() && potion.shouldRender(effect) && effect.doesShowParticles();
    }

    /**
     * 按自身效果数增伤：增益每个 +10%，其余（负面和中性，如发光）每个 +20%
     * <p>
     * 以前中性效果按增益算；持有者只认主手和四件护甲，以前看的是「正在用的那只手」。
     * </p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();

        if (shouldBlockMobTrigger(attacker, false)) {
            return;
        }

        Enchantment blackFlameRitual = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameRitual.class);
        if (blackFlameRitual == null) {
            return;
        }

        int totalLevel = EnchantmentHelper.getEnchantmentLevel(blackFlameRitual, attacker.getHeldItemMainhand());

        for (ItemStack armor : attacker.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(blackFlameRitual, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        float damageMultiplier = 1;
        for (PotionEffect effect : attacker.getActivePotionEffects()) {
            if (isCounted(effect)) {
                damageMultiplier += effect.getPotion().isBeneficial() ? BENEFICIAL_BONUS : HARMFUL_BONUS;
            }
        }

        evt.setAmount(evt.getAmount() * damageMultiplier);
    }

    /**
     * 身上有任意算数的效果时，每秒烧掉当前生命的 5%
     * <p>只对玩家；以前所有生物都会自烧。</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote || evt.phase != TickEvent.Phase.START) {
            return;
        }

        if (evt.player.ticksExisted % TICK_INTERVAL != 0) {
            return;
        }

        EntityPlayer holder = evt.player;

        Enchantment blackFlameRitual = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameRitual.class);
        if (blackFlameRitual == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(blackFlameRitual, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        boolean hasPotion = false;
        for (PotionEffect effect : holder.getActivePotionEffects()) {
            if (isCounted(effect)) {
                hasPotion = true;
                break;
            }
        }

        if (hasPotion) {
            holder.addPotionEffect(new PotionEffect(CarianStylePotion.DESTRUCTION_FIRE_BURNING, 21, 0));
            holder.setHealth(holder.getHealth() * HEALTH_RETAIN_RATIO);
        }
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        if (ench == Enchantments.PROTECTION) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }
}