package pers.roinflam.carianstyle.fabric;

import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import pers.roinflam.carianstyle.annotation.pack.GeneratedDataPack;

/**
 * Fabric 上挂内置数据包 {@code carianstyle:generated} 用的包源。
 * <p>
 * Fabric API 没有给运行期生成的数据包留接口，由 {@code mixin/pack/FabricPackRepositoryMixin} 在服务端数据包仓库建好时
 * 把 {@link #SOURCE} 加进去；包每次扫描包源时现建，按当时的配置生成内容（同 NeoForge 的 {@code AddPackFindersEvent}）。
 * </p>
 * <p>
 * 包的来源不用 {@link PackSource#BUILT_IN}：Fabric 上已选列表重建时，「会自动加入」的来源的包第一次被当作新发现的包追加到最上面，
 * 之后每次重建又按默认位置排回最下面，位置会变。这里换一个不自动加入的来源（显示照旧标「内置」），
 * 包本身是必选的，照样始终启用、老世界照样补进已选列表，而且始终排在最下面，世界数据包和别的模组能用同名文件覆盖。
 * NeoForge 上没有这个问题，照旧用 {@link PackSource#BUILT_IN}。
 * </p>
 */
public final class FabricDataPack {

    /** 显示直接借原版内置包的装饰（连同灰色样式），只把「自动加入」换成否 */
    public static final PackSource PACK_SOURCE = PackSource.create(PackSource.BUILT_IN::decorate, false);

    /** 只给服务端数据包仓库加 */
    public static final RepositorySource SOURCE = consumer -> consumer.accept(GeneratedDataPack.createPack(PACK_SOURCE));

    private FabricDataPack() {
    }
}
