package pers.roinflam.kaliastyle.utils.java.random;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomUtil {

    /**
     * 获取范围内随机数
     *
     * @param min 这个数最小的值Z
     * @param max 这个数最大的值gm
     * @return 随机结果
     */
    public static int getInt(int min, int max) {
        return min + new Random().nextInt(max - min + 1);
    }

    /**
     * 返回百分比几率的结果
     *
     * @param probability 百分比几率
     *
     * @return 结果
     */
    public static boolean percentageChance(double probability) {
        return percentageChance(probability, 8);
    }

    /**
     * 返回...分比几率的结果
     *
     * @param probability 几率
     * @param range       提高多少倍，如果为1则百分比，2为千分比，3为万分比
     * @return 结果
     */
    private static boolean percentageChance(double probability, int range) {
        return getInt(0, 100 * (int) Math.pow(10, range - 1)) < (int) (probability * (int) Math.pow(10, range - 1));
    }

    /**
     * 将一个数随机分成...份，每份概率随机
     *
     * @param totalNumber 总数
     * @param count       份数
     * @return 数量列表
     */
    public static List<Integer> randomList(int totalNumber, int count) {
        List<Integer> list = new ArrayList<>();
        Random rand = new Random();
        int leftNumber = totalNumber;
        int leftCount = count;
        for (int i = 0; i < count - 1; i++) {
            int number = 0;
            if (leftNumber > 0) {
                if (leftNumber / leftCount * 2 < 1) {
                    number = leftNumber;
                } else {
                    number = 1 + rand.nextInt(leftNumber / leftCount * 2);
                }
            } else {
                number = 0;
            }
            list.add(number);
            if (number > 0) {
                leftNumber -= number;
                leftCount--;
            }
        }
        list.add(leftNumber);
        return list;
    }

}
