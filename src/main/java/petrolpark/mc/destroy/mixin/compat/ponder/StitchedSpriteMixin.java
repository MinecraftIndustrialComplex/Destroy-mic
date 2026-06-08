package petrolpark.mc.destroy.mixin.compat.ponder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.createmod.catnip.render.StitchedSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * Replace Ponder's non-thread-safe {@code StitchedSprite.ALL} {@link java.util.HashMap}
 * with a {@link ConcurrentHashMap} immediately after Ponder's {@code <clinit>} stores its
 * (empty) HashMap. The race being fixed:
 *
 * <pre>
 * Caused by: java.util.ConcurrentModificationException
 *     at HashMap.computeIfAbsent
 *     at ponder/StitchedSprite.&lt;init&gt;(StitchedSprite.java:23)
 *     at ponder/SpriteShiftEntry.set
 *     at create/CTSpriteShifter.getCT(CTSpriteShifter.java:20)
 *     at &lt;some Create-addon&gt;.ClientWrapper.&lt;clinit&gt;
 * </pre>
 *
 * <p>Multiple Create-addon mods constructing in parallel on the
 * {@code modloading-worker} pool all eventually hit
 * {@code CTSpriteShifter.getCT} → {@code new StitchedSprite(...)} →
 * {@code ALL.computeIfAbsent(...)}. {@code HashMap.computeIfAbsent} mutates the table
 * mid-call; a concurrent reader iterating the same map (Ponder's
 * {@code onTextureStitchPost} or another concurrent {@code computeIfAbsent}) sees
 * {@code modCount} change → throws CME. The losing thread aborts its mod construction
 * and the entire modpack falls into "broken mod state".</p>
 *
 * <p>Any Create-addon can be the loser depending on scheduling, including Destroy
 * itself via {@link petrolpark.mc.destroy.DestroySpriteShifts}'s static {@code getCT}
 * calls. Replacing the underlying map with a {@code ConcurrentHashMap} eliminates the
 * race for every Create-addon, not just Destroy.</p>
 *
 * <p>Deferring {@code DestroySpriteShifts.<clinit>} to {@code FMLClientSetupEvent} is
 * insufficient: the static fields are referenced from Registrate's deferred
 * {@code onRegister} supplier lambdas during {@code RegisterEvent.Block}, which fires
 * before {@code FMLClientSetupEvent.enqueueWork} runs. The race with other Create addons
 * also remains. Patching the map at source fixes the issue ecosystem-wide.</p>
 */
@Mixin(StitchedSprite.class)
public class StitchedSpriteMixin {

    @Shadow @Final @Mutable
    private static Map<ResourceLocation, List<StitchedSprite>> ALL;

    /**
     * Runs at the TAIL of Ponder's {@code <clinit>}, immediately after Ponder assigned
     * a plain {@code HashMap} to {@code ALL}. Wraps the (still-empty) map in a
     * {@code ConcurrentHashMap} via the copy constructor before any other thread can
     * observe it — JVM serialises {@code <clinit>} on a single thread, so the swap is
     * race-free with respect to readers that are blocked waiting on class
     * initialisation.
     */
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void destroy$swapToConcurrentMap(CallbackInfo ci) {
        ALL = new ConcurrentHashMap<>(ALL);
    }
}
