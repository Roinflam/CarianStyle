package pers.roinflam.carianstyle.utils.java.random;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomUtil {


    public static int getInt(int min, int max) {
        return min + new Random().nextInt(max - min + 1);
    }


    public static boolean percentageChance(double probability) {
        return percentageChance(probability, 8);
    }


    private static boolean percentageChance(double probability, int range) {
        return getInt(0, 100 * (int) Math.pow(10, range - 1)) < (int) (probability * (int) Math.pow(10, range - 1));
    }


    @Nonnull
    public static List<Integer> randomList(int totalNumber, int count) {
        @Nonnull List<Integer> list = new ArrayList<>();
        @Nonnull Random rand = new Random();
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
