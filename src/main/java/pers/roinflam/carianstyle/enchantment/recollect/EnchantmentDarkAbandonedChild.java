package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 黑暗弃子附魔
 * <p>
 * 主手持有时：满蓄力攻击转为魔法伤害并无视护甲，并随机窃取目标身上一个增益效果；
 * 夜晚受到的伤害 ×0.9，玩家夜晚每秒回复 1.5% 最大生命。
 * </p>
 * <p>
 * 以前在 LivingAttack 最高优先级里处理，没有蓄力要求，连点每下都转伤害类型、都偷效果；
 * LivingAttack 在无敌帧判定之前，被无敌帧挡掉的一下也会偷。现在改在伤害真正结算的 LivingHurt 里做，
 * 并要求满蓄力；偷来的效果复制一份新的，不再和目标共用同一个实例。
 * 武器以前按「最近一次使用物品的那只手」取，现在一律看主手；怪物持有时按怪物触发附魔的开关处理。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "dark_abandoned_child",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentDarkAbandonedChild extends EnchantmentBase {

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    /** 夜晚受伤倍率 */
    private static final float NIGHT_DAMAGE_MULTIPLIER = 0.9F;
    /** 夜晚每秒回复的最大生命比例 */
    private static final float HEAL_RATIO_PER_SECOND = 0.015F;

    public EnchantmentDarkAbandonedChild() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        if (!isFullyCharged(attacker)) {
            return;
        }

        // 护甲在 LivingHurt 之后才结算，这里改伤害源来得及
        DamageSource source = ctx.getDamageSource();
        if (source != null) {
            source.setMagicDamage().setDamageBypassesArmor();
        }

        if (!victim.getActivePotionEffects().isEmpty()) {
            List<PotionEffect> positiveEffects = new ArrayList<>(victim.getActivePotionEffects());
            positiveEffects.removeIf(effect ->
                    effect.getPotion().isBadEffect() ||
                            effect.getPotion().isInstant() ||
                            !effect.getPotion().shouldRender(effect)
            );

            if (!positiveEffects.isEmpty()) {
                PotionEffect stolen = positiveEffects.get(RandomUtil.getInt(0, positiveEffects.size() - 1));
                attacker.addPotionEffect(new PotionEffect(stolen));
                victim.removePotionEffect(stolen.getPotion());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getSource().canHarmInCreative()) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        if (shouldBlockMobTrigger(victim, false)) {
            return;
        }

        ItemStack heldItem = victim.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment darkAbandonedChild = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkAbandonedChild.class);
        if (darkAbandonedChild == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(darkAbandonedChild, heldItem);

        if (level > 0 && !victim.world.isDaytime()) {
            evt.setAmount(evt.getAmount() * NIGHT_DAMAGE_MULTIPLIER);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote || evt.player.world.isDaytime()) {
            return;
        }

        if (evt.phase != TickEvent.Phase.START) {
            return;
        }

        EntityPlayer player = evt.player;
        if (!player.isEntityAlive()) {
            return;
        }

        ItemStack heldItem = player.getHeldItemMainhand();
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment darkAbandonedChild = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkAbandonedChild.class);
        if (darkAbandonedChild == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(darkAbandonedChild, heldItem);

        if (level > 0) {
            player.heal(player.getMaxHealth() * HEAL_RATIO_PER_SECOND / 20);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}