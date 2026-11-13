package pers.roinflam.carianstyle.block.fire;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockBehaviour;
import pers.roinflam.carianstyle.base.block.flame.FireBase;

/**
 * 黄色火焰方块
 */
public class YellowFlame extends FireBase {

    /** 1.21.1 的方块都要提供编解码器（{@code BaseFireBlock.codec()} 是抽象的），按属性重建即可 */
    public static final MapCodec<YellowFlame> CODEC = simpleCodec(YellowFlame::new);

    public YellowFlame(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<YellowFlame> codec() {
        return CODEC;
    }
}