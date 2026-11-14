package pers.roinflam.carianstyle.fabric.selftest.pack;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.pack.GeneratedDataPack;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置数据包自测（开发运行带 {@code -Dcarianstyle.selftest=pack}）：登记控制台指令 {@code carianselftest snap <标记>}，
 * 打一行已选数据包列表、本模组包的位置、附魔注册表和附魔标签里本模组的条目数，口径同 NeoForge 测试台的数据包场景。
 * 重载、禁用由控制台照常输原版指令，每步之后打一次快照对比。
 */
public final class PackSelfTest {

    private PackSelfTest() {
    }

    public static void install() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("carianselftest")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.literal("snap")
                                .then(Commands.argument("label", StringArgumentType.word())
                                        .executes(context -> {
                                            snapshot(context.getSource().getServer(),
                                                    StringArgumentType.getString(context, "label"));
                                            return 1;
                                        })))));
    }

    private static void snapshot(MinecraftServer server, String label) {
        PackRepository repo = server.getPackRepository();
        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        Registry<Enchantment> registry = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        int registryEntries = 0;
        for (var key : registry.registryKeySet()) {
            if (key.location().getNamespace().equals("carianstyle")) {
                registryEntries++;
            }
        }
        int tags = 0;
        int tagEntries = 0;
        for (var pair : (Iterable<com.mojang.datafixers.util.Pair<TagKey<Enchantment>, HolderSet.Named<Enchantment>>>)
                registry.getTags()::iterator) {
            tags++;
            for (Holder<Enchantment> holder : pair.getSecond()) {
                if (holder.unwrapKey().map(k -> k.location().getNamespace().equals("carianstyle")).orElse(false)) {
                    tagEntries++;
                }
            }
        }
        System.out.println("[SELFTEST-PACK] " + label
                + " selected=" + String.join(",", selected)
                + " indexOfPack=" + selected.indexOf(GeneratedDataPack.PACK_ID)
                + " size=" + selected.size()
                + " registry.carianstyle=" + registryEntries
                + " tags=" + tags
                + " tags.carianstyleEntries=" + tagEntries);
    }
}
