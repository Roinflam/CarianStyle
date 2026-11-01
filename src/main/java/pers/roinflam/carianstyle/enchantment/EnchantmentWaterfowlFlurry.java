package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.utils.helper.AttackCooldownHelper;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;

/**
 * 水鸟乱舞附魔
 *
 * 武器附魔，连击系统
 * 满蓄力攻击时：
 * - 将伤害分成(等级+1)段，首段随这一下，其余每2tick补一段
 * - 每段前把目标无敌时间设为 10 tick
 * - 下一 tick 开头清零玩家攻击冷却
 * <p>
 * 防递归改用按攻击者记的标记：以前把这一下伤害源的类型名改写成 waterfowlDance，
 * 同一事件链里别的附魔会看到被改过的名字，打死目标时的死亡信息也会变成水鸟乱舞的那条。
 * 满蓄力改用 {@link #isFullyCharged}（蓄力超过 90%），以前要求上一 tick 的冷却恰好为 1。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "waterfowl_flurry",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentWaterfowlFlurry extends EnchantmentBase {

    // 「乱舞进行中」标记的保持时长（tick，要盖住全部段数，分段结束时会提前清掉）
    private static final int MARKER_DURATION = 100;
    // 两段之间的间隔（tick）
    private static final int HIT_INTERVAL = 2;
    // 每段命中前目标的无敌时间（tick）
    private static final int HIT_INVULNERABLE_TIME = 10;
    // 乱舞进行中标记的键
    private static final String DAMAGE_TYPE_MARKER = "waterfowl_dance_marker";

    public EnchantmentWaterfowlFlurry() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时触发连击
     */
    @Override
    protected void onHurtAsAttackerHighest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();
        DamageSource damageSource = ctx.getDamageSource();

        if (victim == null || damageSource == null) {
            return;
        }

        if (attacker instanceof EntityPlayer) {
            if (!isFullyCharged(attacker)) {
                return;
            }
            // 冷却清零推迟到下一 tick 开头，不在本次事件链中途清
            AttackCooldownHelper.resetNextTick((EntityPlayer) attacker);
        }

        // 防止递归：后续各段也会进到这里
        if (EnchantmentDataManager.getData(DAMAGE_TYPE_MARKER, attacker.getUniqueID()) != null) {
            return;
        }

        // 防止与死亡之刃冲突
        if ("deathBlade".equals(damageSource.damageType) || "noDeathBlade".equals(damageSource.damageType)) {
            return;
        }

        // 将伤害分成(等级+1)段
        float damagePerHit = ctx.getDamage() / (level + 1);
        ctx.setDamage(damagePerHit);

        EnchantmentDataManager.setData(DAMAGE_TYPE_MARKER, attacker.getUniqueID(), Boolean.TRUE, MARKER_DURATION);

        // 延迟造成剩余伤害
        new SynchronizationTask(1, HIT_INTERVAL) {
            private int time = 0;

            @Override
            public void run() {
                if (++time > level || !victim.isEntityAlive()) {
                    EnchantmentDataManager.removeData(DAMAGE_TYPE_MARKER, attacker.getUniqueID());
                    this.cancel();
                    return;
                }

                victim.hurtResistantTime = HIT_INVULNERABLE_TIME;
                victim.attackEntityFrom(damageSource, damagePerHit);
            }
        }.start();
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 30) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}