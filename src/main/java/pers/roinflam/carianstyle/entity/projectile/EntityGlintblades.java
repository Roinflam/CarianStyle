package pers.roinflam.carianstyle.entity.projectile;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

/**
 * PORT-STUB(B2b)
 * <p>魔法辉剑实体：只有构造器和空的数据 / 存档方法，飞行、追踪、伤害都还没写。</p>
 */
@Deprecated
public class EntityGlintblades extends Entity {

    public EntityGlintblades(World worldIn) {
        super(worldIn);
        setSize(0.75F, 0.75F);
    }

    @Override
    protected void entityInit() {
        // 桩
    }

    @Override
    protected void readEntityFromNBT(@Nonnull NBTTagCompound compound) {
        // 桩
    }

    @Override
    protected void writeEntityToNBT(@Nonnull NBTTagCompound compound) {
        // 桩
    }
}
