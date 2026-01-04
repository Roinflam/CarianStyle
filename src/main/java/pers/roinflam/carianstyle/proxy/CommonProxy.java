package pers.roinflam.carianstyle.proxy;

import net.minecraft.item.Item;

/**
 * 通用代理
 * <p>
 * 提供服务端和客户端共享的基础实现
 * 客户端特有功能由ClientProxy覆写
 * </p>
 */
public class CommonProxy {

    /**
     * 注册物品渲染器（服务端空实现）
     *
     * @param item 物品
     * @param meta 元数据
     * @param id   模型ID
     */
    public void registerItemRenderer(Item item, int meta, String id) {
        // 服务端不需要注册渲染器
    }

    /**
     * 注册实体渲染器（服务端空实现）
     */
    public void registerEntityRenderer() {
        // 服务端不需要注册渲染器
    }
}