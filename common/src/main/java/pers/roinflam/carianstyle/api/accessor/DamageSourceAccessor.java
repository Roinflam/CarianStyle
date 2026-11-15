package pers.roinflam.carianstyle.api.accessor;

import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

import java.util.Set;

/**
 * DamageSource 访问器接口
 * <p>
 * 提供对 DamageSource 临时标签的访问能力
 * 注意：此接口必须放在非 mixin 包中，以便在普通代码中引用
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
public interface DamageSourceAccessor {

    /**
     * 获取临时标签集合
     *
     * @return 临时标签集合
     */
    Set<TagKey<DamageType>> carianstyle$getTemporaryTags();

    /**
     * 添加临时标签
     *
     * @param tag 要添加的标签
     */
    void carianstyle$addTemporaryTag(TagKey<DamageType> tag);

    /**
     * 移除临时标签
     *
     * @param tag 要移除的标签
     */
    void carianstyle$removeTemporaryTag(TagKey<DamageType> tag);

    /**
     * 清除所有临时标签
     */
    void carianstyle$clearTemporaryTags();

    /**
     * 检查是否有某个临时标签
     *
     * @param tag 要检查的标签
     * @return 是否有该临时标签
     */
    boolean carianstyle$hasTemporaryTag(TagKey<DamageType> tag);
}