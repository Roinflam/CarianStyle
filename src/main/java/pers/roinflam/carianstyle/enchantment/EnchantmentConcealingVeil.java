package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Enchantments;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 隐匿面纱附魔
 *
 * 护甲附魔，潜行时获得隐身效果
 * 攻击或受伤后3秒内无法隐身（战斗冷却）
 */
@AutoRegisterEnchantment(
        id = "concealing_veil",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        conflictsWith = {
                // 与原版保护冲突
        }
)
@Mod.EventBusSubscriber
public class EnchantmentConcealingVeil extends EnchantmentBase {

    private static final String BATTLE_COOLDOWN_KEY = "concealing_veil_battle";
    /** 战斗冷却（tick）：攻击或受伤后这么久内无法隐身 */
    private static final int BATTLE_DURATION = 60;
    /** 每次施加的隐身时长（tick），潜行时每 tick 续上 */
    private static final int STEALTH_REFRESH_TICKS = 2;
    /** 清仇恨的间隔（tick） */
    private static final int AGGRO_CLEAR_INTERVAL = 10;
    /** 清仇恨的范围（格，立方体半边长） */
    private static final double AGGRO_CLEAR_RANGE = 32.0D;

    public EnchantmentConcealingVeil() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 攻击时进入战斗冷却
     * <p>以前分发器只扫攻击者的双手，穿在身上的这件收不到这个回调，出手后照样能立刻隐身。</p>
     */
    @Override
    protected void onAttackLowest(@Nonnull EnchantmentContext ctx, int level) {
        if (ctx.isHolderPlayer()) {
            EnchantmentDataManager.setCooldown(BATTLE_COOLDOWN_KEY, ctx.getHolder().getUniqueID(), BATTLE_DURATION);
        }
    }

    /**
     * 受伤也进入战斗冷却
     * <p>以前只有攻击会重置，挨打后立刻潜行就能隐身，和说明写的「受到攻击」不符。</p>
     */
    @Override
    protected void onHurtAsVictimLowest(@Nonnull EnchantmentContext ctx, int level) {
        if (ctx.isHolderPlayer()) {
            EnchantmentDataManager.setCooldown(BATTLE_COOLDOWN_KEY, ctx.getHolder().getUniqueID(), BATTLE_DURATION);
        }
    }

    /**
     * 潜行且不在战斗冷却中时隐身，并定期清掉周围已经锁定自己的生物的仇恨
     * <p>
     * 隐身效果只拦「新设目标」，进入隐身前就盯上你的怪物会一直追；
     * 现在每 0.5 秒把 32 格内以你为目标的生物的目标清空。
     * </p>
     */
    @Override
    protected void onPlayerTick(@Nonnull EnchantmentContext ctx, int level) {
        EntityPlayer player = ctx.getHolderAsPlayer();
        if (player == null || !player.isSneaking()) {
            return;
        }

        if (EnchantmentDataManager.isOnCooldown(BATTLE_COOLDOWN_KEY, player.getUniqueID())) {
            return;
        }

        ctx.addPotionToHolder(CarianStylePotion.STEALTH, STEALTH_REFRESH_TICKS, 0);

        if (player.ticksExisted % AGGRO_CLEAR_INTERVAL == 0) {
            clearNearbyAggroTowards(player);
        }
    }

    private static void clearNearbyAggroTowards(@Nonnull EntityLivingBase target) {
        AxisAlignedBB box = new AxisAlignedBB(
                target.posX - AGGRO_CLEAR_RANGE, target.posY - AGGRO_CLEAR_RANGE, target.posZ - AGGRO_CLEAR_RANGE,
                target.posX + AGGRO_CLEAR_RANGE, target.posY + AGGRO_CLEAR_RANGE, target.posZ + AGGRO_CLEAR_RANGE
        );
        List<EntityLiving> mobs = target.world.getEntitiesWithinAABB(EntityLiving.class, box,
                mob -> mob != null && target.equals(mob.getAttackTarget()));
        for (EntityLiving mob : mobs) {
            mob.setAttackTarget(null);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.PROTECTION);
    }
}