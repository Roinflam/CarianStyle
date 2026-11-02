package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.mc112.attr.Operation;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 冻伤药水效果
 * <p>
 * 效果：
 * - 移动速度降低7.5%×等级
 * - 每0.5秒造成最大生命值×0.25%×(等级+1)的冻伤伤害
 * </p>
 * <p>
 * 修复记录 v2.1：
 * - UUID从"5d59080b-eda9-f5b7-1b3c-51568e5b6682"改为新UUID
 *   原UUID与MobEffectSleep的移速修改器完全相同，导致：
 *   1. 同时拥有冻伤和睡眠时后者覆盖前者的修改器
 *   2. 先移除的效果会把另一个的修改器也删掉
 * </p>
 * <p>
 * 1.12.2 版：已发布的 2.0.0 用的还是旧 UUID。旧存档里冻伤生效中的实体带着旧修饰符进来，
 * 冻伤到期时按新 UUID 删不到，减速会永久留下；读档时由 {@code mc112.migration.LegacyPotionMigration} 清掉
 * （身上有睡眠时那条是睡眠自己的，不删）。
 * </p>
 */
public class MobEffectFrostbite extends IconBase {

    public MobEffectFrostbite(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn);
        // 1.12.2 版：registerPotionAttributeModifier 拿药水名当修饰符名、名字为空会抛异常，
        // 注册器要到构造之后才设名字，这里先设上同一个键
        this.setPotionName("effect." + Reference.MOD_ID + ".frostbite");

        // v2.1修复：换用独立UUID，避免与MobEffectSleep的移速修改器冲突
        // 原UUID "5d59080b-eda9-f5b7-1b3c-51568e5b6682" 与Sleep共用
        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.MOVEMENT_SPEED,
                "a3e7f012-8b9c-4d6e-b1f0-3c7829d5a4e1",
                -0.075,
                Operation.MULTIPLY_TOTAL.id()
        );
    }

    @Override
    public void applyEffectTick(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {
        float damage = entityLivingBaseIn.getMaxHealth() * 0.0025f;
        damage += damage * amplifier;
        entityLivingBaseIn.attackEntityFrom(NewDamageSource.frostbite(entityLivingBaseIn.world), damage);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    protected ResourceLocation getIconTexture() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/frostbite.png");
    }
}
