package pers.roinflam.carianstyle.fabric.selftest.logic;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.gameevent.GameEvent;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 自测用玩家：Fabric 的 {@code FakePlayer} 对任何伤害 {@code isInvulnerableTo} 都为真，出生保护又只在 tick 里递减，
 * 这里把两样都关掉；统计记到自己的表里，游戏事件记一行，方便断言先后。只在开发运行里用（字段按 Mojmap 名反射）。
 */
public class TestPlayer extends FakePlayer {

    public final Map<ResourceLocation, Integer> customStats = new HashMap<>();
    public final List<String> gameEvents = new ArrayList<>();

    public TestPlayer(ServerLevel level, String name) {
        super(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        try {
            Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(this, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return false;
    }

    @Override
    public void awardStat(Stat<?> stat, int amount) {
        if (stat.getValue() instanceof ResourceLocation rl) {
            customStats.merge(rl, amount, Integer::sum);
        }
    }

    @Override
    public void gameEvent(GameEvent event) {
        ResourceLocation id = BuiltInRegistries.GAME_EVENT.getKey(event);
        gameEvents.add(id == null ? "?" : id.getPath());
        LogicSelfTest.LOG.add(LogicSelfTest.label(this) + ":gameEvent:" + gameEvents.get(gameEvents.size() - 1));
        super.gameEvent(event);
    }

    /** 攻击蓄力拉满（假玩家不 tick，蓄力不会自己涨） */
    public void fullCharge() {
        this.attackStrengthTicker = 1000;
    }

    public int strengthTicker() {
        return this.attackStrengthTicker;
    }
}
