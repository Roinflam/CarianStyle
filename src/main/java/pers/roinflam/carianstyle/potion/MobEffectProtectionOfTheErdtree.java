package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 黄金树庇护药水效果
 * <p>
 * 效果：
 * - 非生物来源的伤害(环境伤害)减免20%×(等级+1)，最高100%
 * - 不对绝对伤害生效
 * </p>
 * <p>
 * 1.12.2 版保留：除了「无视无敌」的伤害（{@code canHarmInCreative()}，虚空、/kill 这类），
 * 绝对伤害（{@code isDamageAbsolute()}）也不减免，所以出血、癫火、猩红腐败这三种减不到。
 * 1.20.1 只排除 {@code bypasses_invulnerability}。这是 1.12.2 一直以来的行为，保持不变。
 * </p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class MobEffectProtectionOfTheErdtree extends IconBase {

    public MobEffectProtectionOfTheErdtree(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn);
    }

    /**
     * 处理环境伤害减免
     *
     * @param evt 伤害事件
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        // 排除创造模式伤害、绝对伤害
        // 1.12.2 版保留：绝对伤害也排除（见类注释）
        if (damageSource.canHarmInCreative() || damageSource.isDamageAbsolute()) {
            return;
        }

        // 排除生物来源伤害
        if (damageSource.getTrueSource() instanceof EntityLivingBase) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        PotionEffect effect = victim.getActivePotionEffect(CarianStylePotion.PROTECTION_OF_THE_ERDTREE.get());
        if (effect != null) {
            int amplifier = effect.getAmplifier();
            // 倍率不能小于 0：效果等级还可能被野兽健壮这类附魔抬高，负的伤害量会变成回血
            evt.setAmount(evt.getAmount() * Math.max(0.0f, 1 - (amplifier + 1) * 0.2f));
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    protected ResourceLocation getIconTexture() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/protection_of_the_erdtree.png");
    }
}
