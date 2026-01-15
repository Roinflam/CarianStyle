package pers.roinflam.carianstyle.utils.java.random;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机工具类
 * <p>
 * 提供各种随机数生成和概率计算方法
 * 使用ThreadLocalRandom避免多线程竞争，性能更优
 * </p>
 */
public class RandomUtil {

    /**
     * 获取指定范围内的随机整数（包含边界）
     *
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @return 随机整数
     */
    public static int getInt(int min, int max) {
        if (min >= max) {
            return min;
        }
        return min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }

    /**
     * 概率判断（默认精度）
     *
     * @param probability 概率值（0-100）
     * @return 是否命中
     */
    public static boolean percentageChance(double probability) {
        if (probability <= 0) {
            return false;
        }
        if (probability >= 100) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble(100) < probability;
    }

    /**
     * 概率判断（指定精度）
     *
     * @param probability 概率值（0-100）
     * @param precision   精度（小数位数）
     * @return 是否命中
     */
    public static boolean percentageChance(double probability, int precision) {
        if (probability <= 0) {
            return false;
        }
        if (probability >= 100) {
            return true;
        }
        double multiplier = Math.pow(10, precision);
        int range = (int) (100 * multiplier);
        int threshold = (int) (probability * multiplier);
        return ThreadLocalRandom.current().nextInt(range) < threshold;
    }

    /**
     * 将总数随机分配到指定数量的组中
     *
     * @param totalNumber 总数
     * @param count       组数
     * @return 分配结果列表
     */
    @Nonnull
    public static List<Integer> randomList(int totalNumber, int count) {
        List<Integer> list = new ArrayList<>(count);
        Random rand = ThreadLocalRandom.current();

        int leftNumber = totalNumber;
        int leftCount = count;

        for (int i = 0; i < count - 1; i++) {
            int number = 0;
            if (leftNumber > 0 && leftCount > 0) {
                int avg = leftNumber / leftCount;
                int maxAlloc = Math.max(1, avg * 2);
                number = rand.nextInt(maxAlloc) + 1;
                number = Math.min(number, leftNumber);
            }
            list.add(number);
            leftNumber -= number;
            leftCount--;
        }

        // 最后一组分配剩余的所有
        list.add(Math.max(0, leftNumber));
        return list;
    }
}