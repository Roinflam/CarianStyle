package pers.roinflam.carianstyle.codex;

import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * 附魔的主题分组。
 *
 * <h3>为什么不用模组内部的分类</h3>
 * <p>
 * 代码里的 {@code EnchantmentCategory}（通用 / 战技 / 回忆 / 律法 / 死亡）是<b>实现分类</b>——
 * 它决定附魔走哪条注册与触发路径，对开发有意义，但对玩家几乎没有意义：
 * 「回忆」这一组里既有满月也有黑焰刀刃，玩家看不出它们有什么共同点。
 * </p>
 * <p>
 * 本枚举改按<b>题材</b>分组，与 MC 百科上该模组的资料页保持一致：
 * 群星、癫火、猩红腐败、蒙格温王朝……每一组内的附魔在原作里本来就属于同一个体系，
 * 玩家找「我想玩血流」时能直接定位到「蒙格温王朝」，而不必先知道它在代码里算「通用」还是「战技」。
 * </p>
 * <p>
 * 实现分类没有丢弃，它移到了详情页里作为一行信息展示。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
public enum CodexTheme {

    /** 卡利亚王室 */
    CARIAN_ROYALTY("carian_royalty", 0xFF8FB8E8),

    /** 黄金树 */
    ERDTREE("erdtree", 0xFFE8D48A),

    /** 野兽 */
    BEAST("beast", 0xFFC49A6C),

    /** 双指 */
    TWO_FINGERS("two_fingers", 0xFFB9AE97),

    /** 护符 */
    TALISMAN("talisman", 0xFFB0B6C8),

    /** 立场 */
    STANCE("stance", 0xFF9FD0C8),

    /** 黄金律法 */
    GOLDEN_ORDER("golden_order", 0xFFE0C060),

    /** 噬神大蛇 */
    GOD_DEVOURING_SERPENT("god_devouring_serpent", 0xFF8FC48F),

    /** 褪色者 */
    TARNISHED("tarnished", 0xFFC9BFA8),

    /** 熔炉百相 */
    CRUCIBLE("crucible", 0xFFD09A5A),

    /** 火焰啊 */
    FLAME("flame", 0xFFE8874A),

    /** 拉塔恩 */
    RADAHN("radahn", 0xFFE07A5A),

    /** 诅咒 */
    CURSE("curse", 0xFF9A7FB0),

    /** 古龙 */
    ANCIENT_DRAGON("ancient_dragon", 0xFFE0D060),

    /** 猩红腐败 */
    SCARLET_ROT("scarlet_rot", 0xFFD06A4A),

    /** 战技 */
    ASH_OF_WAR("ash_of_war", 0xFFC8C0B0),

    /** 癫火 */
    FRENZIED_FLAME("frenzied_flame", 0xFFE8B040),

    /** 睡眠 */
    SLEEP("sleep", 0xFFB49AD8),

    /** 蒙格温王朝 */
    MOHGWYN("mohgwyn", 0xFFC85A5A),

    /** 罗蕾塔 */
    LORETTA("loretta", 0xFFA8C0E0),

    /** 祖灵森林 */
    ANCESTRAL("ancestral", 0xFF7FC0A0),

    /** 玛莲妮亚 */
    MALENIA("malenia", 0xFFE09090),

    /** 死亡卢恩 */
    DEATH_RUNE("death_rune", 0xFF8A96A8),

    /** 辉石魔法 */
    GLINTSTONE("glintstone", 0xFF9AB8E8),

    /** 群星 */
    STARS("stars", 0xFFA8A8E0);

    /** 语言键与配置键的后缀 */
    private final String key;

    /** 界面上的强调色（ARGB） */
    private final int accentColor;

    /** 名称语言键，构造期合成避免运行期拼接 */
    private final String langKey;

    CodexTheme(String key, int accentColor) {
        this.key = key;
        this.accentColor = accentColor;
        this.langKey = "carianstyle.codex.theme." + key;
    }

    /**
     * @return 配置键
     */
    @Nonnull
    public String getKey() {
        return key;
    }

    /**
     * @return 界面强调色（ARGB）
     */
    public int getAccentColor() {
        return accentColor;
    }

    /**
     * @return 主题显示名
     */
    @Nonnull
    public Component getDisplayName() {
        return Component.translatable(langKey);
    }

    /**
     * 附魔 id 到主题的对照表。
     * <p>
     * 写成静态表而不是注解字段，是为了<b>不必再改动 113 个附魔类</b>——
     * 主题归属是纯展示信息，与附魔的运行逻辑无关，放在这里改起来也只有一处。
     * </p>
     * <p>新增附魔时若忘了登记，它会落到 {@link #resolve} 的兜底分支，不会报错也不会消失。</p>
     */
    private static final Map<String, CodexTheme> BY_ID = new HashMap<>();

    static {
        // 卡利亚王室（4）
        BY_ID.put("carian_phalanx", CARIAN_ROYALTY);
        BY_ID.put("carian_retaliation", CARIAN_ROYALTY);
        BY_ID.put("greatblade_phalanx", CARIAN_ROYALTY);
        BY_ID.put("lucidity", CARIAN_ROYALTY);
        // 黄金树（3）
        BY_ID.put("blessing_of_the_erdtree", ERDTREE);
        BY_ID.put("golden_vow", ERDTREE);
        BY_ID.put("protection_of_the_erdtree", ERDTREE);
        // 野兽（2）
        BY_ID.put("beast_robust", BEAST);
        BY_ID.put("beast_vitality", BEAST);
        // 双指（1）
        BY_ID.put("exclude", TWO_FINGERS);
        // 护符（12）
        BY_ID.put("blessed_dew_talisman", TALISMAN);
        BY_ID.put("blue_feathered_branchsword", TALISMAN);
        BY_ID.put("concealing_veil", TALISMAN);
        BY_ID.put("daedicar_woe", TALISMAN);
        BY_ID.put("dragoncrest_greatshield", TALISMAN);
        BY_ID.put("godskin_swaddling", TALISMAN);
        BY_ID.put("golden_dung_turtle", TALISMAN);
        BY_ID.put("green_turtle", TALISMAN);
        BY_ID.put("hard_arrow", TALISMAN);
        BY_ID.put("long_tail_cat", TALISMAN);
        BY_ID.put("magic_scorpion_charm", TALISMAN);
        BY_ID.put("red_feathered_branchsword", TALISMAN);
        // 立场（2）
        BY_ID.put("realm_of_magic", STANCE);
        BY_ID.put("topps_stand", STANCE);
        // 黄金律法（9）
        BY_ID.put("causality_principle", GOLDEN_ORDER);
        BY_ID.put("golden_law", GOLDEN_ORDER);
        BY_ID.put("holy_ground", GOLDEN_ORDER);
        BY_ID.put("immutable_shield", GOLDEN_ORDER);
        BY_ID.put("indomitable", GOLDEN_ORDER);
        BY_ID.put("prayerful_strike", GOLDEN_ORDER);
        BY_ID.put("regressive_principle", GOLDEN_ORDER);
        BY_ID.put("sacred_blade", GOLDEN_ORDER);
        BY_ID.put("sacred_order", GOLDEN_ORDER);
        // 噬神大蛇（1）
        BY_ID.put("blasphemy", GOD_DEVOURING_SERPENT);
        // 褪色者（1）
        BY_ID.put("warrior", TARNISHED);
        // 熔炉百相（3）
        BY_ID.put("crucible_knot_talisman", CRUCIBLE);
        BY_ID.put("crucible_scale_talisman", CRUCIBLE);
        BY_ID.put("furnace_feather", CRUCIBLE);
        // 火焰啊（5）
        BY_ID.put("fire_devoured", FLAME);
        BY_ID.put("fire_gives_power", FLAME);
        BY_ID.put("giant_flame", FLAME);
        BY_ID.put("healing_by_fire", FLAME);
        BY_ID.put("shelter_of_fire", FLAME);
        // 拉塔恩（2）
        BY_ID.put("broken_star", RADAHN);
        BY_ID.put("call_star", RADAHN);
        // 诅咒（2）
        BY_ID.put("bad_omen", CURSE);
        BY_ID.put("eat_shit", CURSE);
        // 古龙（4）
        BY_ID.put("ancient_dragon_lightning", ANCIENT_DRAGON);
        BY_ID.put("precise_lightning", ANCIENT_DRAGON);
        BY_ID.put("time_reversal", ANCIENT_DRAGON);
        BY_ID.put("vic_dragon_thunder", ANCIENT_DRAGON);
        // 猩红腐败（3）
        BY_ID.put("dragon_breath_corruption", SCARLET_ROT);
        BY_ID.put("scarlet_lonia", SCARLET_ROT);
        BY_ID.put("scarlet_rot", SCARLET_ROT);
        // 战技（21）
        BY_ID.put("assassin_gambit", ASH_OF_WAR);
        BY_ID.put("continuous_shooting", ASH_OF_WAR);
        BY_ID.put("corpse_piler", ASH_OF_WAR);
        BY_ID.put("cragblade", ASH_OF_WAR);
        BY_ID.put("double_slash", ASH_OF_WAR);
        BY_ID.put("freezing_earthquake", ASH_OF_WAR);
        BY_ID.put("gravitas", ASH_OF_WAR);
        BY_ID.put("incision", ASH_OF_WAR);
        BY_ID.put("lion_claw", ASH_OF_WAR);
        BY_ID.put("lunge_up", ASH_OF_WAR);
        BY_ID.put("offer_sword", ASH_OF_WAR);
        BY_ID.put("parry", ASH_OF_WAR);
        BY_ID.put("patience", ASH_OF_WAR);
        BY_ID.put("quickstep", ASH_OF_WAR);
        BY_ID.put("repeating_thrust", ASH_OF_WAR);
        BY_ID.put("shield_bash", ASH_OF_WAR);
        BY_ID.put("sky_shot", ASH_OF_WAR);
        BY_ID.put("stamp_sweep", ASH_OF_WAR);
        BY_ID.put("sword_dance", ASH_OF_WAR);
        BY_ID.put("unsheathe", ASH_OF_WAR);
        BY_ID.put("vowed_revenge", ASH_OF_WAR);
        // 癫火（5）
        BY_ID.put("calamity", FRENZIED_FLAME);
        BY_ID.put("empty_epilepsy_fire", FRENZIED_FLAME);
        BY_ID.put("epilepsy_fire", FRENZIED_FLAME);
        BY_ID.put("epilepsy_spread", FRENZIED_FLAME);
        BY_ID.put("howl_shabriri", FRENZIED_FLAME);
        // 睡眠（2）
        BY_ID.put("hypnotic_arrow", SLEEP);
        BY_ID.put("hypnotic_smoke", SLEEP);
        // 蒙格温王朝（4）
        BY_ID.put("blood", MOHGWYN);
        BY_ID.put("blood_blade", MOHGWYN);
        BY_ID.put("blood_collection", MOHGWYN);
        BY_ID.put("blood_slash", MOHGWYN);
        // 罗蕾塔（2）
        BY_ID.put("loretta_big_bow", LORETTA);
        BY_ID.put("loretta_trick", LORETTA);
        // 祖灵森林（2）
        BY_ID.put("ancestral_spirit_horn", ANCESTRAL);
        BY_ID.put("ancestral_spirits", ANCESTRAL);
        // 玛莲妮亚（5）
        BY_ID.put("aeonia", MALENIA);
        BY_ID.put("corrupted_wing_sword", MALENIA);
        BY_ID.put("mikaela_blade", MALENIA);
        BY_ID.put("millicent_prosthesis", MALENIA);
        BY_ID.put("waterfowl_flurry", MALENIA);
        // 死亡卢恩（7）
        BY_ID.put("black_flame_blade", DEATH_RUNE);
        BY_ID.put("black_flame_ritual", DEATH_RUNE);
        BY_ID.put("black_flame_shelter", DEATH_RUNE);
        BY_ID.put("death_blade", DEATH_RUNE);
        BY_ID.put("doomed_death", DEATH_RUNE);
        BY_ID.put("invisible_weapon", DEATH_RUNE);
        BY_ID.put("living_corpse", DEATH_RUNE);
        // 辉石魔法（5）
        BY_ID.put("pyroxene_ice", GLINTSTONE);
        BY_ID.put("rock_blaster", GLINTSTONE);
        BY_ID.put("scholar_shield", GLINTSTONE);
        BY_ID.put("starlight", GLINTSTONE);
        BY_ID.put("wave_stone_magic", GLINTSTONE);
        // 群星（6）
        BY_ID.put("adura_moonlight_sword", STARS);
        BY_ID.put("dark_abandoned_child", STARS);
        BY_ID.put("dark_moon", STARS);
        BY_ID.put("full_moon", STARS);
        BY_ID.put("moon_of_noxtura", STARS);
        BY_ID.put("stars_law", STARS);
    }

    /**
     * 按附魔 id 解析主题。
     *
     * @param id 附魔注册 id
     * @return 对应主题；未登记时返回 {@code null}，由调用方决定兜底行为
     */
    @Nullable
    public static CodexTheme resolve(@Nullable String id) {
        return id == null ? null : BY_ID.get(id);
    }
}
