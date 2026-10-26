package com.heishandashu.teacraft.core;

import net.minecraft.network.chat.Component;

/**
 * 把 tick 数变成人可读的时长文本。
 *
 * <h2>为什么单独一个类</h2>
 * <p>原先这段逻辑写在 {@code TeaTrayHud}（客户端类）里，
 * 自检想验证它时直接炸了：</p>
 * <pre>
 *   java.lang.RuntimeException:
 *     Cannot load class net.minecraft.client.gui.GuiGraphicsExtractor
 *     in environment type SERVER
 * </pre>
 * <p>加载客户端类会连带加载它的字段类型，而服务端没有 GUI 类。
 * 所以纯逻辑必须放到不依赖任何客户端类型的地方 —— 就是这里。</p>
 *
 * <h2>换算</h2>
 * <p>20 tick = 1 秒（24000 tick = 1 游戏日 = 20 分钟现实时间）。</p>
 * <p>单位用<b>现实时间</b>，因为玩家关心的是「还要等多久」。
 * 例：3 个游戏日 = 72000 tick = 3600 秒 = 显示「1小时」。</p>
 */
public final class TeaDurations {

    private TeaDurations() {
    }

    /**
     * 格式化时长，精确到秒。
     *
     * <p>需求：「倒计时精确到秒。」</p>
     *
     * <p>只显示有意义的单位：剩 45 秒就显示「45秒」，不显示「0分钟45秒」。</p>
     */
    public static String format(long ticks) {
        long totalSeconds = Math.max(0L, ticks) / 20L;
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(Component.translatable("teacraft.unit.day").getString());
        }
        if (hours > 0) {
            sb.append(hours).append(Component.translatable("teacraft.unit.hour").getString());
        }
        if (minutes > 0) {
            sb.append(minutes).append(Component.translatable("teacraft.unit.minute").getString());
        }
        // 秒：只要前面没有更大的单位，或者确实还有秒数，就显示 —— 保证「精确到秒」
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append(Component.translatable("teacraft.unit.second").getString());
        }
        return sb.toString();
    }
}
