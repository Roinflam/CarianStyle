package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.mc112.damage.DamageTags;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 黄金誓约药水效果
 * <p>
 * 效果：
 * - 受到伤害时减免10%×(等级+1)，最高100%
 * - 造成伤害时增加15%×(等级+1)
 * </p>
 * <p>
 * 1.12.2 版：「无视无敌」的伤害（虚空、/kill 这类）不参与增减，判断走 {@link DamageTags}，
 * 原版伤害源上就是 {@code canHarmInCreative()}。
 * </p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class MobEffectGoldenVow extends IconBase {

    public MobEffectGoldenVow(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn);
    }

    /**
     * 处理伤害增减效果
     *
     * @param evt 受伤事件
     */
    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (DamageTags.is(damageSource, DamageTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        // 受击者减伤
        PotionEffect victimEffect = victim.getActivePotionEffect(CarianStylePotion.GOLDEN_VOW.get());
        if (victimEffect != null) {
            int amplifier = victimEffect.getAmplifier();
            // 倍率不能小于 0：效果等级还可能被野兽健壮这类附魔抬高，负的伤害量会被后面的增减伤带歪
            evt.setAmount(evt.getAmount() * Math.max(0.0f, 1 - (amplifier + 1) * 0.1f));
        }

        // 攻击者增伤
        if (damageSource.getTrueSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
            PotionEffect attackerEffect = attacker.getActivePotionEffect(CarianStylePotion.GOLDEN_VOW.get());
            if (attackerEffect != null) {
                int amplifier = attackerEffect.getAmplifier();
                evt.setAmount(evt.getAmount() * (1 + (amplifier + 1) * 0.15f));
            }
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
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/golden_vow.png");
    }
}
