package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.base.potion.NetworkBase;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 隐身药水效果（隐藏）
 * <p>
 * 效果：
 * - 玩家模型不渲染（隐形）
 * - 生物无法将此实体设为攻击目标
 * - 每 10 tick 让周围 32 格内正以此实体为目标的生物丢掉目标
 * </p>
 * <p>
 * 以前只拦「新设目标」这一刻，隐身之前就锁定了自己的怪物会一直追着打，直到它自己换目标。
 * </p>
 */
public class MobEffectStealth extends NetworkBase {

    /** 清掉周围仇恨的间隔（tick） */
    private static final int AGGRO_CLEAR_INTERVAL = 10;
    /** 清掉仇恨的搜索范围（格，立方体） */
    private static final double AGGRO_CLEAR_RANGE = 32.0D;

    public MobEffectStealth(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "stealth");
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return duration % AGGRO_CLEAR_INTERVAL == 0;
    }

    /**
     * 服务端：已经锁定隐身者的生物丢掉目标
     */
    @Override
    public void performEffect(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {
        if (entityLivingBaseIn.world.isRemote) {
            return;
        }

        AxisAlignedBB searchBox = new AxisAlignedBB(
                entityLivingBaseIn.posX - AGGRO_CLEAR_RANGE,
                entityLivingBaseIn.posY - AGGRO_CLEAR_RANGE,
                entityLivingBaseIn.posZ - AGGRO_CLEAR_RANGE,
                entityLivingBaseIn.posX + AGGRO_CLEAR_RANGE,
                entityLivingBaseIn.posY + AGGRO_CLEAR_RANGE,
                entityLivingBaseIn.posZ + AGGRO_CLEAR_RANGE
        );
        List<EntityLiving> nearbyMobs = entityLivingBaseIn.world.getEntitiesWithinAABB(
                EntityLiving.class,
                searchBox,
                mob -> mob != null && entityLivingBaseIn.equals(mob.getAttackTarget())
        );
        for (EntityLiving mob : nearbyMobs) {
            mob.setAttackTarget(null);
        }
    }

    /**
     * 客户端：隐藏玩家渲染
     */
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onRenderPlayer(@Nonnull RenderPlayerEvent.Pre evt) {
        EntityPlayer entityPlayer = evt.getEntityPlayer();
        if (isAction(entityPlayer.getEntityId())) {
            evt.setCanceled(true);
        }
    }

    /**
     * 服务端：阻止生物将隐身实体设为目标
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingSetAttackTarget(@Nonnull LivingSetAttackTargetEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getTarget() == null) {
            return;
        }

        if (!(evt.getEntityLiving() instanceof EntityLiving)) {
            return;
        }

        EntityLivingBase target = evt.getTarget();
        EntityLiving attacker = (EntityLiving) evt.getEntityLiving();

        if (target.isPotionActive(this)) {
            attacker.setAttackTarget(null);
        }
    }

    @Override
    public int getSerialNumber() {
        return 3;
    }
}