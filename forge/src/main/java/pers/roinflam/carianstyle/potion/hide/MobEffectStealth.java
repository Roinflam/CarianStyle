package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.base.potion.NetworkBase;

import javax.annotation.Nonnull;

/**
 * 隐身药水效果（隐藏）
 * <p>
 * 效果：
 * - 玩家模型不渲染（隐形）
 * - 生物无法将此实体设为攻击目标
 * </p>
 */
public class MobEffectStealth extends NetworkBase {

    public MobEffectStealth(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "stealth");
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