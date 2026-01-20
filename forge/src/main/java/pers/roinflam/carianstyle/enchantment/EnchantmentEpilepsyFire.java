package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

/**
 * 癫火附魔
 * <p>
 * 武器附魔，双刃剑效果
 * 攻击敌人时：
 * - 对敌人造成持续3秒的癫火伤害（攻击者最大生命值 × 30% × 等级 × 50% = 15% × 等级）
 * - 对自己造成持续3秒的癫火伤害（自己最大生命值 × 30%）
 * - 创造模式玩家免疫自损
 * - 玩家必须是刚挥动武器时触发
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "epilepsy_fire",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentEpilepsyFire extends EnchantmentBase {

    private static final int BURN_DURATION = 65;
    private static final int DAMAGE_TICKS = 60;

    public EnchantmentEpilepsyFire() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();

        Enchantment epilepsyFire = EnchantmentRegistry.getEnchantmentByClass(EnchantmentEpilepsyFire.class);
        if (epilepsyFire == null) {
            return;
        }

        ItemStack heldItem = attacker.getItemInHand(attacker.getUsedItemHand());
        if (heldItem.isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(epilepsyFire, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        // 玩家必须是刚挥动武器
        if (attacker instanceof Player) {
            if (((Player) attacker).getAttackStrengthScale(0.5F) < 0.9F) {
                return;
            }
        }

        final int effectiveLevel = level;

        // 对攻击者造成癫火伤害（创造模式玩家免疫）
        // 应用火焰燃烧效果（需要同步网络）
        DynamicAttributeManager.apply(attacker,
                DynamicAttributes.EPILEPSY_FIRE_BURNING.createInstance(BURN_DURATION, 0));
        ClientSyncEffectHelper.onAttributeApplied(attacker, DynamicAttributes.EPILEPSY_FIRE_BURNING);

        new SynchronizationTask(5, 1) {
            private int tick = 0;

            @Override
            public void run() {
                if (++tick > DAMAGE_TICKS || !attacker.isAlive()) {
                    this.cancel();
                    return;
                }

                float damage = attacker.getMaxHealth() * 0.2f / 60;
                if (attacker.getHealth() - damage * 2 > 0) {
                    EntityLivingUtil.damageHealthDirectly(attacker, damage);
                } else {
                    EntityLivingUtil.kill(attacker, NewDamageSource.epilepsyFire(attacker.level()));
                    this.cancel();
                }
            }
        }.start();

        // 对受击者造成癫火伤害
        DynamicAttributeManager.apply(victim,
                DynamicAttributes.EPILEPSY_FIRE_BURNING.createInstance(BURN_DURATION, 0));
        ClientSyncEffectHelper.onAttributeApplied(victim, DynamicAttributes.EPILEPSY_FIRE_BURNING);

        new SynchronizationTask(5, 1) {
            private int tick = 0;

            @Override
            public void run() {
                if (++tick > DAMAGE_TICKS || !victim.isAlive()) {
                    this.cancel();
                    return;
                }

                float damage = attacker.getMaxHealth() * 0.2f * effectiveLevel * 0.1f / 60;
                if (victim.getHealth() - damage * 2 > 0) {
                    EntityLivingUtil.damageHealthDirectly(victim, damage);
                } else {
                    EntityLivingUtil.kill(victim, NewDamageSource.epilepsyFire(victim.level()));
                    this.cancel();
                }
            }
        }.start();
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}