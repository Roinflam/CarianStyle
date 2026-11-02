package pers.roinflam.carianstyle.mixin.helper;

import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.carianstyle.api.accessor.DamageSourceAccessor;

import java.util.HashSet;
import java.util.Set;

/**
 * DamageSource的Mixin
 * <p>
 * 允许在运行时动态添加临时标签，实现类似1.12.2的DamageSource可变特性
 * 临时标签只对当前DamageSource实例有效,不会影响其他实例
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@Mixin(DamageSource.class)
public class DamageSourceMixin implements DamageSourceAccessor {

    /**
     * 临时标签集合
     * 使用@Unique确保字段名不冲突
     */
    @Unique
    private Set<TagKey<DamageType>> carianstyle$temporaryTags = null;

    /**
     * 获取临时标签集合（懒加载）
     */
    @Unique
    private Set<TagKey<DamageType>> carianstyle$getOrCreateTags() {
        if (carianstyle$temporaryTags == null) {
            carianstyle$temporaryTags = new HashSet<>();
        }
        return carianstyle$temporaryTags;
    }

    @Override
    public Set<TagKey<DamageType>> carianstyle$getTemporaryTags() {
        return carianstyle$getOrCreateTags();
    }

    @Override
    public void carianstyle$addTemporaryTag(TagKey<DamageType> tag) {
        carianstyle$getOrCreateTags().add(tag);
    }

    @Override
    public void carianstyle$removeTemporaryTag(TagKey<DamageType> tag) {
        if (carianstyle$temporaryTags != null) {
            carianstyle$temporaryTags.remove(tag);
        }
    }

    @Override
    public void carianstyle$clearTemporaryTags() {
        if (carianstyle$temporaryTags != null) {
            carianstyle$temporaryTags.clear();
        }
    }

    @Override
    public boolean carianstyle$hasTemporaryTag(TagKey<DamageType> tag) {
        return carianstyle$temporaryTags != null && carianstyle$temporaryTags.contains(tag);
    }

    /**
     * 注入到is(TagKey)方法
     * 优先检查临时标签
     */
    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true)
    private void onIsTag(TagKey<DamageType> tag, CallbackInfoReturnable<Boolean> cir) {
        if (carianstyle$hasTemporaryTag(tag)) {
            cir.setReturnValue(true);
        }
    }
}