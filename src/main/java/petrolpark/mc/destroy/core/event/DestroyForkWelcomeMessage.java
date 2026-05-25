package petrolpark.mc.destroy.core.event;

import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.config.DestroyConfigs;

/**
 * On client login, drop a short chat notice telling the player they're on the unofficial
 * NHblock 1.21.1 port (with clickable Discord + GitHub links). Gated by the
 * {@code forkWelcomeMessage} client config — set to false to silence permanently.
 */
@EventBusSubscriber(modid = Destroy.MOD_ID, value = Dist.CLIENT)
public class DestroyForkWelcomeMessage {

    private static final String DISCORD_URL = "https://discord.com/invite/6EBJ3AzbHu";
    private static final String GITHUB_URL = "https://github.com/NHblock714/Destroy/tree/1.21.1-neo";

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!DestroyConfigs.client().forkWelcomeMessage.get()) return;
        LocalPlayer player = event.getPlayer();
        if (player == null) return;

        player.sendSystemMessage(Component.translatable("destroy.fork.welcome.header")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable("destroy.fork.welcome.body")
            .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("destroy.fork.welcome.links",
            buildLink("destroy.fork.welcome.discord", DISCORD_URL),
            buildLink("destroy.fork.welcome.github", GITHUB_URL))
            .withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent buildLink(String labelKey, String url) {
        return Component.translatable(labelKey).withStyle(style -> style
            .withColor(ChatFormatting.AQUA)
            .withUnderlined(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(url))));
    }
}
