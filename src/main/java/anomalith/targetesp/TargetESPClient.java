package anomalith.targetesp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class TargetESPClient implements ClientModInitializer {
    // ---- настройки ----
    static boolean enabled = true;
    static int style = 0;
    static int colorIdx = 0; // 0 = белый (безопасно для шейдеров)
    static final long HOLD_MS = 1500; // сколько держать цель после того как перестали смотреть

    static final String[] STYLES = {"Кольцо", "Орбиты", "Спираль", "Уголки", "Бокс"};
    static final String[] COLOR_NAMES = {"Белый", "Красный", "Зелёный", "Синий", "Фиолетовый", "Голубой", "Жёлтый", "Радуга"};
    static final int[] COLORS = {0xFFFFFFFF, 0xFFFF3030, 0xFF30FF50, 0xFF3070FF, 0xFFC040FF, 0xFF30E0FF, 0xFFFFE030};

    private static KeyBinding keyToggle, keyStyle, keyColor;
    private static LivingEntity target;
    private static long lastSeen;

    @Override
    public void onInitializeClient() {
        keyToggle = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.targetesp.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "category.targetesp"));
        keyStyle = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.targetesp.style", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.targetesp"));
        keyColor = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.targetesp.color", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.targetesp"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (keyToggle.wasPressed()) {
                enabled = !enabled;
                msg(mc, "TargetESP: " + (enabled ? "ВКЛ" : "ВЫКЛ"));
            }
            while (keyStyle.wasPressed()) {
                style = (style + 1) % STYLES.length;
                msg(mc, "TargetESP стиль: " + STYLES[style]);
            }
            while (keyColor.wasPressed()) {
                colorIdx = (colorIdx + 1) % COLOR_NAMES.length;
                msg(mc, "TargetESP цвет: " + COLOR_NAMES[colorIdx]);
            }
            if (mc.targetedEntity instanceof LivingEntity le && le != mc.player) {
                target = le;
                lastSeen = System.currentTimeMillis();
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(TargetESPClient::render);
    }

    private static void msg(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), true);
    }

    private static int currentColor() {
        if (colorIdx == COLOR_NAMES.length - 1) {
            float h = (System.currentTimeMillis() % 4000L) / 4000f;
            return 0xFF000000 | (Color.HSBtoRGB(h, 0.8f, 1f) & 0xFFFFFF);
        }
        return COLORS[colorIdx];
    }

    private static void render(WorldRenderContext ctx) {
        if (!enabled || target == null) return;
        if (System.currentTimeMillis() - lastSeen > HOLD_MS || !target.isAlive() || target.isRemoved()) {
            target = null;
            return;
        }
        MatrixStack ms = ctx.matrixStack();
        if (ms == null || ctx.consumers() == null) return;

        float td = ctx.tickCounter().getTickDelta(true);
        Entity e = target;
        Vec3d p = e.getLerpedPos(td);
        Vec3d cam = ctx.camera().getPos();
        float w = e.getWidth() * 0.5f;
        float h = e.getHeight();
        float t = (System.currentTimeMillis() % 100000L) / 1000f;
        int c = currentColor();

        ms.push();
        ms.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
        MatrixStack.Entry en = ms.peek();
        VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getLines());

        float r = w + 0.35f;
        switch (style) {
            case 0 -> { // бегающее вверх-вниз кольцо
                float y = h * (0.5f + 0.5f * MathHelper.sin(t * 2.2f));
                circle(vc, en, r, y, 0, MathHelper.TAU, 40, c);
                circle(vc, en, r, 0.02f, 0, MathHelper.TAU, 40, (c & 0x00FFFFFF) | 0x80000000);
            }
            case 1 -> { // три вращающиеся дуги на разной высоте
                for (int i = 0; i < 3; i++) {
                    float y = h * (0.5f + 0.5f * MathHelper.sin(t * 1.6f + i * 2.1f));
                    float a0 = t * 2.5f * (i % 2 == 0 ? 1 : -1) + i * MathHelper.TAU / 3f;
                    circle(vc, en, r, y, a0, 1.3f, 16, c);
                }
            }
            case 2 -> { // двойная спираль
                for (int s = 0; s < 2; s++) {
                    int n = 64;
                    for (int i = 0; i < n; i++) {
                        float f0 = i / (float) n, f1 = (i + 1) / (float) n;
                        float a0 = t * 2f + s * MathHelper.PI + f0 * MathHelper.TAU * 2f;
                        float a1 = t * 2f + s * MathHelper.PI + f1 * MathHelper.TAU * 2f;
                        line(vc, en, MathHelper.cos(a0) * r, f0 * h, MathHelper.sin(a0) * r,
                                MathHelper.cos(a1) * r, f1 * h, MathHelper.sin(a1) * r, c);
                    }
                }
            }
            case 3 -> corners(vc, en, w, h, Math.min(0.3f, h * 0.25f), c);
            case 4 -> box(vc, en, w, h, c);
        }
        ms.pop();
    }

    // ---- примитивы ----
    private static void circle(VertexConsumer vc, MatrixStack.Entry en, float r, float y, float start, float len, int seg, int c) {
        for (int i = 0; i < seg; i++) {
            float a0 = start + len * i / seg, a1 = start + len * (i + 1) / seg;
            line(vc, en, MathHelper.cos(a0) * r, y, MathHelper.sin(a0) * r, MathHelper.cos(a1) * r, y, MathHelper.sin(a1) * r, c);
        }
    }

    private static void box(VertexConsumer vc, MatrixStack.Entry en, float w, float h, int c) {
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2)
            line(vc, en, sx * w, 0, sz * w, sx * w, h, sz * w, c);
        for (float y : new float[]{0, h}) {
            line(vc, en, -w, y, -w, w, y, -w, c);
            line(vc, en, w, y, -w, w, y, w, c);
            line(vc, en, w, y, w, -w, y, w, c);
            line(vc, en, -w, y, w, -w, y, -w, c);
        }
    }

    private static void corners(VertexConsumer vc, MatrixStack.Entry en, float w, float h, float l, int c) {
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) for (int yy = 0; yy <= 1; yy++) {
            float x = sx * w, z = sz * w, y = yy * h, dy = yy == 0 ? l : -l;
            line(vc, en, x, y, z, x - sx * l, y, z, c);
            line(vc, en, x, y, z, x, y, z - sz * l, c);
            line(vc, en, x, y, z, x, y + dy, z, c);
        }
    }

    // линия рисуется 3 раза с микросмещением — получается толще
    private static void line(VertexConsumer vc, MatrixStack.Entry en, float x1, float y1, float z1, float x2, float y2, float z2, int c) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-5f) return;
        dx /= len; dy /= len; dz /= len;
        for (int i = -1; i <= 1; i++) {
            float o = i * 0.012f;
            vc.vertex(en, x1, y1 + o, z1).color(c).normal(en, dx, dy, dz);
            vc.vertex(en, x2, y2 + o, z2).color(c).normal(en, dx, dy, dz);
        }
    }
}
