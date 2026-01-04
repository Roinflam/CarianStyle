package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.RandomUtils;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;

/**
 * 精准落雷附魔
 *
 * 护甲附魔，被投射物攻击时反击
 * 被投射物攻击时：
 * - 对攻击者召唤等级次落雷
 * - 每次落雷造成30%原伤害的雷电伤害
 * - 下雨时伤害×2，雷暴时伤害×4
 * - 击退攻击者
 */
@AutoRegisterEnchantment(
        id = "precise_lightning",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentPreciseLightning extends EnchantmentBase {

    public EnchantmentPreciseLightning() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 被投射物攻击时对攻击者召唤落雷
     * 由于需要累加所有护甲的附魔等级，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        // 必须是投射物攻击且有攻击者
        if (!(damageSource.getImmediateSource() instanceof IProjectile)) {
            return;
        }
        if (!(damageSource.getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();

        Enchantment preciseLightning = EnchantmentRegistry.getEnchantmentByClass(EnchantmentPreciseLightning.class);
        if (preciseLightning == null) {
            return;
        }

        // 从受击者的护甲累加附魔等级
        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(preciseLightning, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        final int effectiveLevel = totalLevel;
        final float originalDamage = evt.getAmount();

        // 延迟召唤落雷
        new SynchronizationTask(5, 5) {
            private int time = 0;

            @Override
            public void run() {
                if (++time > effectiveLevel) {
                    this.cancel();
                    return;
                }

                World world = attacker.world;

                // 召唤落雷（effectOnly=true，不造成火焰）
                world.addWeatherEffect(new EntityLightningBolt(
                        world,
                        attacker.posX,
                        attacker.posY,
                        attacker.posZ,
                        true
                ));

                // 重置无敌帧
                attacker.hurtResistantTime = attacker.maxHurtResistantTime / 2;

                // 计算天气加成
                int magnification = 1;
                if (attacker.world.isRaining()) {
                    magnification *= 2;
                } else if (attacker.world.isThundering()) {
                    magnification *= 4;
                }

                // 造成雷电伤害
                attacker.attackEntityFrom(DamageSource.LIGHTNING_BOLT, originalDamage * 0.3f * magnification);

                // 击退攻击者
                if (attacker.onGround) {
                    double x = RandomUtils.nextBoolean() ? victim.posX - attacker.posX : attacker.posX - victim.posX;
                    double z = RandomUtils.nextBoolean() ? victim.posZ - attacker.posZ : attacker.posZ - victim.posZ;
                    attacker.attackedAtYaw = (float) (MathHelper.atan2(z, x) * (180D / Math.PI) - (double) attacker.rotationYaw);
                    attacker.knockBack(victim, 0.2f, x, z);
                }
            }
        }.start();
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentCausalityPrinciple.class));
    }
}