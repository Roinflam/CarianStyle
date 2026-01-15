package pers.roinflam.carianstyle.utils.helper.world;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraftforge.fml.common.IWorldGenerator;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;
import java.util.Random;

public class WorldGenBase implements IWorldGenerator {
    protected final DimensionType dimensionType;
    protected final IBlockState blockState;
    protected final int minY;
    protected final int maxY;
    protected final int minSize;
    protected final int maxSize;
    protected final int minChances;
    protected final int maxChances;

    public WorldGenBase(DimensionType dimensionType, IBlockState blockState, int minY, int maxY, int minSize, int maxSize, int minChances, int maxChances) {
        this.dimensionType = dimensionType;
        this.blockState = blockState;
        this.minY = minY;
        this.maxY = maxY;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.minChances = minChances;
        this.maxChances = maxChances;
    }

    @Override
    public void generate(@Nonnull Random random, int chunkX, int chunkZ, @Nonnull World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (world.provider.getDimensionType().equals(dimensionType)) {
            generateOre(blockState, world, random, chunkX * 16, chunkZ * 16);
        }
    }

    private void generateOre(@Nonnull IBlockState ore, @Nonnull World world, @Nonnull Random random, int x, int z) {
        for (int i = 0; i < RandomUtil.getInt(minChances, maxChances); i++) {
            @Nonnull BlockPos pos = new BlockPos(x + random.nextInt(16), RandomUtil.getInt(minY, maxY), z + random.nextInt(16));
            @Nonnull WorldGenMinable generator = new WorldGenMinable(ore, RandomUtil.getInt(minSize, maxSize));
            generator.generate(world, random, pos);
        }
    }

}
