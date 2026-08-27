package ru.food333444555.mymoddelaetai.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexConsumerProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.food333444555.mymoddelaetai.config.MobColorManager;
import ru.food333444555.mymoddelaetai.util.ColorUtil;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class OverlayHandler {

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<LivingEntity, EntityModel<LivingEntity>> event) {
        try {
            LivingEntity entity = event.getEntity();
            var settings = MobColorManager.getInstance().getSettingsForEntity(entity);
            if (settings == null) return;
            if (!settings.highlightThroughWalls) return;

            EntityRenderer<? super LivingEntity> renderer = event.getRenderer();
            EntityModel<LivingEntity> model = renderer.getModel();
            var mc = Minecraft.getInstance();

            // prepare color
            float[] rgba = ColorUtil.hexToRGBA(settings.modelColor, settings.alpha);
            float r = rgba[0], g = rgba[1], b = rgba[2], a = rgba[3];

            // render the entity model in a special pass so it shows through walls
            var ms = event.getMatrixStack();
            VertexConsumerProvider buffer = mc.renderBuffers().bufferSource();

            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            int fullbright = 15728880;

            model.renderToBuffer(ms, buffer.getBuffer(RenderType.entityTranslucent(renderer.getTextureLocation(entity))), fullbright, OverlayTexture.NO_OVERLAY, r, g, b, a);

            buffer.endBatch(RenderType.entityTranslucent(renderer.getTextureLocation(entity)));

            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
        } catch (Throwable t) {
            // don't break rendering on errors
            t.printStackTrace();
        }
    }
}
