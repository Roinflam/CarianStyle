package pers.roinflam.carianstyle.mc112.attr;

/**
 * 属性修饰符的运算方式，对应 1.20.1 的 {@code AttributeModifier.Operation}
 * <p>
 * 1.12.2 的 {@code AttributeModifier} 构造器只收一个 int（0 / 1 / 2），这里给它起名字，
 * 让从 1.20.1 搬来的代码照样写 {@code Operation.MULTIPLY_TOTAL}，传给 1.12.2 时调 {@link #id()}。
 * </p>
 */
public enum Operation {
    /** 直接加到基础值上 */
    ADDITION(0),
    /** 按基础值的倍数加 */
    MULTIPLY_BASE(1),
    /** 乘到最终值上 */
    MULTIPLY_TOTAL(2);

    private final int id;

    Operation(int id) {
        this.id = id;
    }

    /**
     * 1.12.2 {@code AttributeModifier} 用的运算编号
     */
    public int id() {
        return id;
    }

    /**
     * 按 1.12.2 的运算编号取枚举
     *
     * @param id 0 / 1 / 2
     * @return 对应的运算方式
     * @throws IllegalArgumentException 编号不认识
     */
    public static Operation fromId(int id) {
        for (Operation op : values()) {
            if (op.id == id) {
                return op;
            }
        }
        throw new IllegalArgumentException("未知的属性运算编号：" + id);
    }
}
