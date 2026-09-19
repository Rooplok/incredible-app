import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.w3c.dom.Node;
import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.RandomAccessFile;
import java.util.Iterator;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class HelloWorld {

    // ---------- Базовые цвета ----------
    private static final Color DEFAULT_BACKGROUND = new Color(18, 19, 21);
    private static final Color CARD = new Color(27, 28, 31);
    private static final Color INPUT_BACKGROUND = new Color(35, 36, 40);
    private static final Color BORDER = new Color(57, 59, 64);

    private static final Color MAIN_TEXT = new Color(238, 238, 234);
    private static final Color SECONDARY_TEXT = new Color(150, 153, 158);

    private static final Color DEFAULT_PARTICLE = new Color(205, 209, 212);

    private static final Color BUTTON_HOVER = new Color(45, 47, 51);
    private static final Color CLOSE_HOVER = new Color(190, 55, 55);

    // ---------- Параметры по умолчанию ----------
    private static final int DEFAULT_SPEED = 100;
    private static final int DEFAULT_AMPLITUDE = 100;
    private static final int DEFAULT_MOUSE_FORCE = 2600;
    private static final int DEFAULT_MOUSE_RADIUS = 105;
    private static final int DEFAULT_SPRING = 42;
    private static final int DEFAULT_SPACING = 7;
    private static final int DEFAULT_PARTICLE_SIZE = 2;

    enum WindowButtonType {
        MINIMIZE,
        MAXIMIZE,
        CLOSE
    }

    // ============================================================
    // UI: скруглённая панель
    // ============================================================

    static class RoundedPanel extends JPanel {
        private final int radius;
        private Color fillColor;

        RoundedPanel(int radius, Color fillColor) {
            this.radius = radius;
            this.fillColor = fillColor;
            setOpaque(false);
        }

        void setFillColor(Color color) {
            fillColor = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g2.setColor(fillColor);
            g2.fillRoundRect(
                    0,
                    0,
                    getWidth(),
                    getHeight(),
                    radius,
                    radius
            );

            g2.setColor(BORDER);
            g2.drawRoundRect(
                    0,
                    0,
                    Math.max(0, getWidth() - 1),
                    Math.max(0, getHeight() - 1),
                    radius,
                    radius
            );

            g2.dispose();
        }
    }

    // ============================================================
    // UI: поле ввода
    // ============================================================

    static class RoundedTextField extends JTextField {

        RoundedTextField(String text) {
            super(text);

            setOpaque(false);
            setBorder(new EmptyBorder(16, 18, 16, 18));

            setFont(new Font("SansSerif", Font.PLAIN, 18));
            setForeground(MAIN_TEXT);
            setCaretColor(MAIN_TEXT);

            setSelectionColor(new Color(74, 76, 82));
            setSelectedTextColor(MAIN_TEXT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g2.setColor(INPUT_BACKGROUND);
            g2.fillRoundRect(
                    0,
                    0,
                    getWidth(),
                    getHeight(),
                    16,
                    16
            );

            g2.setColor(BORDER);
            g2.drawRoundRect(
                    0,
                    0,
                    Math.max(0, getWidth() - 1),
                    Math.max(0, getHeight() - 1),
                    16,
                    16
            );

            g2.dispose();

            super.paintComponent(g);
        }
    }

    // ============================================================
    // Частица
    // ============================================================

    static class Particle {

        final float baseX;
        final float baseY;

        final int size;
        final int opacity;

        final float phaseX;
        final float phaseY;

        final float amplitudeX;
        final float amplitudeY;

        final float speedX;
        final float speedY;

        float displacementX = 0f;
        float displacementY = 0f;

        float velocityX = 0f;
        float velocityY = 0f;

        Particle(
                float baseX,
                float baseY,
                int size,
                int opacity,
                float phaseX,
                float phaseY,
                float amplitudeX,
                float amplitudeY,
                float speedX,
                float speedY
        ) {
            this.baseX = baseX;
            this.baseY = baseY;
            this.size = size;
            this.opacity = opacity;

            this.phaseX = phaseX;
            this.phaseY = phaseY;

            this.amplitudeX = amplitudeX;
            this.amplitudeY = amplitudeY;

            this.speedX = speedX;
            this.speedY = speedY;
        }
    }

    // ============================================================
    // Область с частицами
    // ============================================================

    static class ParticleTextPanel extends JPanel {

        private String text = "Hello world";

        private final List<Particle> particles = new ArrayList<>();

        private int cachedWidth = -1;
        private int cachedHeight = -1;
        private String cachedText = "";

        private final long animationStart = System.nanoTime();
        private long lastFrameTime = System.nanoTime();

        private float mouseX;
        private float mouseY;
        private boolean mouseInside = false;

        // Настраиваемые параметры
        private Color particleColor = DEFAULT_PARTICLE;
        private Color particleBackgroundColor = CARD;

        private float speedMultiplier = 1.0f;
        private float amplitudeMultiplier = 1.0f;

        private float mouseForce = DEFAULT_MOUSE_FORCE;
        private float mouseRadius = DEFAULT_MOUSE_RADIUS;
        private float springStrength = DEFAULT_SPRING;

        private int particleStep = DEFAULT_SPACING;
        private int baseParticleSize = DEFAULT_PARTICLE_SIZE;

        private static final float DAMPING = 8.5f;
        private static final float MAX_DISPLACEMENT = 85f;

        ParticleTextPanel() {
            setOpaque(false);

            Timer timer = new Timer(3, e -> repaint());
            timer.setCoalesce(true);
            timer.start();

            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    invalidateParticleCache();
                    rebuildParticles();
                }
            });

            MouseAdapter mouseInteraction = new MouseAdapter() {

                @Override
                public void mouseEntered(MouseEvent e) {
                    mouseInside = true;
                    updateMouse(e);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    mouseInside = false;
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    mouseInside = true;
                    updateMouse(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    mouseInside = true;
                    updateMouse(e);
                }

                private void updateMouse(MouseEvent e) {
                    mouseX = e.getX();
                    mouseY = e.getY();
                }
            };

            addMouseListener(mouseInteraction);
            addMouseMotionListener(mouseInteraction);
        }

        void setText(String newText) {
            text = newText;
            invalidateParticleCache();
            rebuildParticles();
            repaint();
        }

        void setParticleColor(Color color) {
            if (color != null) {
                particleColor = color;
                repaint();
            }
        }

        Color getParticleColor() {
            return particleColor;
        }

        void setParticleBackgroundColor(Color color) {
            if (color != null) {
                particleBackgroundColor = color;
                repaint();
            }
        }

        Color getParticleBackgroundColor() {
            return particleBackgroundColor;
        }

        void setSpeedMultiplier(float value) {
            speedMultiplier = value;
        }

        void setAmplitudeMultiplier(float value) {
            amplitudeMultiplier = value;
        }

        void setMouseForce(float value) {
            mouseForce = value;
        }

        void setMouseRadius(float value) {
            mouseRadius = value;
        }

        void setSpringStrength(float value) {
            springStrength = value;
        }

        void setParticleStep(int value) {
            particleStep = Math.max(3, value);
            invalidateParticleCache();
            rebuildParticles();
        }

        void setBaseParticleSize(int value) {
            baseParticleSize = Math.max(1, value);
            invalidateParticleCache();
            rebuildParticles();
        }

        void resetPhysics() {
            for (Particle p : particles) {
                p.displacementX = 0f;
                p.displacementY = 0f;
                p.velocityX = 0f;
                p.velocityY = 0f;
            }

            repaint();
        }

        private void invalidateParticleCache() {
            cachedWidth = -1;
            cachedHeight = -1;
            cachedText = "";
        }

        private int findBestFontSize(Graphics2D g2, int width, int height) {
            int maxWidth = (int) (width * 0.90);
            int maxHeight = (int) (height * 0.68);

            int low = 10;
            int high = 420;
            int best = 10;

            while (low <= high) {
                int mid = (low + high) / 2;

                Font font = new Font("SansSerif", Font.BOLD, mid);
                FontMetrics fm = g2.getFontMetrics(font);

                int textWidth = fm.stringWidth(text);
                int textHeight = fm.getHeight();

                if (textWidth <= maxWidth && textHeight <= maxHeight) {
                    best = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }

            return best;
        }

        private int hash(int x, int y, int salt) {
            int h =
                    x * 374761393
                    + y * 668265263
                    + salt * 1442695041;

            h = (h ^ (h >>> 13)) * 1274126177;

            return h ^ (h >>> 16);
        }

        private int jitter(int x, int y, int salt, int range) {
            return Math.floorMod(
                    hash(x, y, salt),
                    range * 2 + 1
            ) - range;
        }

        private float normalizedHash(int x, int y, int salt) {
            return Math.floorMod(
                    hash(x, y, salt),
                    10000
            ) / 10000f;
        }

        private void rebuildParticles() {
            int width = getWidth();
            int height = getHeight();

            if (width <= 0 || height <= 0) {
                return;
            }

            if (
                    width == cachedWidth
                    && height == cachedHeight
                    && text.equals(cachedText)
            ) {
                return;
            }

            cachedWidth = width;
            cachedHeight = height;
            cachedText = text;

            particles.clear();

            if (text == null || text.isBlank()) {
                return;
            }

            final int scale = 3;

            BufferedImage mask = new BufferedImage(
                    width * scale,
                    height * scale,
                    BufferedImage.TYPE_INT_ARGB
            );

            Graphics2D mg = mask.createGraphics();

            mg.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            mg.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );

            BufferedImage temp = new BufferedImage(
                    1,
                    1,
                    BufferedImage.TYPE_INT_ARGB
            );

            Graphics2D tg = temp.createGraphics();

            int fontSize = findBestFontSize(
                    tg,
                    width,
                    height
            );

            tg.dispose();

            Font font = new Font(
                    "SansSerif",
                    Font.BOLD,
                    fontSize * scale
            );

            mg.setFont(font);
            mg.setColor(Color.WHITE);

            FontMetrics fm = mg.getFontMetrics();

            int textWidth = fm.stringWidth(text);

            int x =
                    (width * scale - textWidth)
                    / 2;

            int y =
                    (height * scale - fm.getHeight())
                    / 2
                    + fm.getAscent();

            mg.drawString(text, x, y);
            mg.dispose();

            int jitterRange = Math.max(
                    1,
                    Math.min(
                            3,
                            particleStep / 3
                    )
            );

            for (
                    int py = 0;
                    py < height;
                    py += particleStep
            ) {
                for (
                        int px = 0;
                        px < width;
                        px += particleStep
                ) {

                    int sampleX = Math.min(
                            mask.getWidth() - 1,
                            px * scale
                    );

                    int sampleY = Math.min(
                            mask.getHeight() - 1,
                            py * scale
                    );

                    int alpha =
                            (
                                    mask.getRGB(
                                            sampleX,
                                            sampleY
                                    )
                                    >>> 24
                            )
                            & 0xff;

                    if (alpha > 35) {

                        float baseX =
                                px
                                + jitter(
                                        px,
                                        py,
                                        1,
                                        jitterRange
                                );

                        float baseY =
                                py
                                + jitter(
                                        px,
                                        py,
                                        2,
                                        jitterRange
                                );

                        int particleSize =
                                baseParticleSize
                                + Math.floorMod(
                                        hash(px, py, 3),
                                        2
                                );

                        int opacity =
                                165
                                + Math.floorMod(
                                        hash(px, py, 4),
                                        91
                                );

                        float amplitudeX =
                                1.2f
                                + normalizedHash(
                                        px,
                                        py,
                                        5
                                ) * 2.0f;

                        float amplitudeY =
                                1.2f
                                + normalizedHash(
                                        px,
                                        py,
                                        6
                                ) * 2.0f;

                        float phaseX =
                                normalizedHash(
                                        px,
                                        py,
                                        7
                                )
                                * (float) (
                                        Math.PI * 2
                                );

                        float phaseY =
                                normalizedHash(
                                        px,
                                        py,
                                        8
                                )
                                * (float) (
                                        Math.PI * 2
                                );

                        float speedX =
                                1.10f
                                + normalizedHash(
                                        px,
                                        py,
                                        9
                                ) * 0.75f;

                        float speedY =
                                0.95f
                                + normalizedHash(
                                        px,
                                        py,
                                        10
                                ) * 0.70f;

                        particles.add(
                                new Particle(
                                        baseX,
                                        baseY,
                                        particleSize,
                                        opacity,
                                        phaseX,
                                        phaseY,
                                        amplitudeX,
                                        amplitudeY,
                                        speedX,
                                        speedY
                                )
                        );
                    }
                }
            }
        }


        static class ExportParticle {
            final float baseX;
            final float baseY;
            final int size;
            final int opacity;
            final float phaseX;
            final float phaseY;
            final float amplitudeX;
            final float amplitudeY;
            final float speedX;
            final float speedY;

            ExportParticle(
                    float baseX,
                    float baseY,
                    int size,
                    int opacity,
                    float phaseX,
                    float phaseY,
                    float amplitudeX,
                    float amplitudeY,
                    float speedX,
                    float speedY
            ) {
                this.baseX = baseX;
                this.baseY = baseY;
                this.size = size;
                this.opacity = opacity;
                this.phaseX = phaseX;
                this.phaseY = phaseY;
                this.amplitudeX = amplitudeX;
                this.amplitudeY = amplitudeY;
                this.speedX = speedX;
                this.speedY = speedY;
            }
        }

        static class ExportData {
            final int width;
            final int height;
            final Color background;
            final Color particleColor;
            final List<ExportParticle> particles;
            final double durationSeconds;

            ExportData(
                    int width,
                    int height,
                    Color background,
                    Color particleColor,
                    List<ExportParticle> particles,
                    double durationSeconds
            ) {
                this.width = width;
                this.height = height;
                this.background = background;
                this.particleColor = particleColor;
                this.particles = particles;
                this.durationSeconds = durationSeconds;
            }

            BufferedImage renderFrame(double progress) {
                BufferedImage image =
                        new BufferedImage(
                                width,
                                height,
                                BufferedImage.TYPE_INT_RGB
                        );

                Graphics2D g2 = image.createGraphics();

                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                g2.setRenderingHint(
                        RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY
                );

                g2.setColor(background);
                g2.fillRect(
                        0,
                        0,
                        width,
                        height
                );

                /*
                 * Почти весь экспорт повторяет ТО ЖЕ движение,
                 * которое видно в приложении:
                 * sin(time * speed + phase).
                 *
                 * Только последние 20% мягко "сшиваются" с началом,
                 * чтобы GIF/MP4 зацикливались без скачка.
                 */
                double currentTime =
                        progress
                        * durationSeconds;

                final double seamStart =
                        0.80;

                double seamWeight =
                        0.0;

                if (progress > seamStart) {
                    double u =
                            (progress - seamStart)
                            / (1.0 - seamStart);

                    // smoothstep: на обеих границах производная = 0
                    seamWeight =
                            u
                            * u
                            * (3.0 - 2.0 * u);
                }

                double wrappedTime =
                        currentTime
                        - durationSeconds;

                for (ExportParticle particle : particles) {

                    double normalX =
                            Math.sin(
                                    currentTime
                                    * particle.speedX
                                    + particle.phaseX
                            );

                    double normalY =
                            Math.cos(
                                    currentTime
                                    * particle.speedY
                                    + particle.phaseY
                            );

                    double wrappedX =
                            Math.sin(
                                    wrappedTime
                                    * particle.speedX
                                    + particle.phaseX
                            );

                    double wrappedY =
                            Math.cos(
                                    wrappedTime
                                    * particle.speedY
                                    + particle.phaseY
                            );

                    double blendedX =
                            normalX
                            + (
                                    wrappedX
                                    - normalX
                            )
                            * seamWeight;

                    double blendedY =
                            normalY
                            + (
                                    wrappedY
                                    - normalY
                            )
                            * seamWeight;

                    float offsetX =
                            (float) blendedX
                            * particle.amplitudeX;

                    float offsetY =
                            (float) blendedY
                            * particle.amplitudeY;

                    g2.setColor(
                            new Color(
                                    particleColor.getRed(),
                                    particleColor.getGreen(),
                                    particleColor.getBlue(),
                                    particle.opacity
                            )
                    );

                    g2.fill(
                            new Ellipse2D.Float(
                                    particle.baseX + offsetX,
                                    particle.baseY + offsetY,
                                    particle.size,
                                    particle.size
                            )
                    );
                }

                g2.dispose();

                return image;
            }
        }

        ExportData createExportData() {
            if (
                    getWidth() != cachedWidth
                    || getHeight() != cachedHeight
                    || !text.equals(cachedText)
            ) {
                rebuildParticles();
            }

            List<ExportParticle> exportParticles =
                    new ArrayList<>();

            for (Particle p : particles) {
                exportParticles.add(
                        new ExportParticle(
                                p.baseX,
                                p.baseY,
                                p.size,
                                p.opacity,
                                p.phaseX,
                                p.phaseY,
                                p.amplitudeX
                                * amplitudeMultiplier,
                                p.amplitudeY
                                * amplitudeMultiplier,

                                // Точно та же скорость, что используется
                                // в живой анимации.
                                p.speedX
                                * speedMultiplier,

                                p.speedY
                                * speedMultiplier
                        )
                );
            }

            return new ExportData(
                    Math.max(
                            2,
                            getWidth()
                    ),
                    Math.max(
                            2,
                            getHeight()
                    ),
                    particleBackgroundColor,
                    particleColor,
                    exportParticles,

                    // Общая длина бесшовного цикла.
                    4.0
            );
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D backgroundGraphics = (Graphics2D) g.create();

            backgroundGraphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            backgroundGraphics.setColor(particleBackgroundColor);

            // Фон визуальной области теперь повторяет скругление интерфейса.
            backgroundGraphics.fillRoundRect(
                    0,
                    0,
                    getWidth(),
                    getHeight(),
                    22,
                    22
            );

            backgroundGraphics.dispose();

            if (
                    getWidth() != cachedWidth
                    || getHeight() != cachedHeight
                    || !text.equals(cachedText)
            ) {
                rebuildParticles();
            }

            if (particles.isEmpty()) {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g2.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY
            );

            long now = System.nanoTime();

            double time =
                    (now - animationStart)
                    / 1_000_000_000.0;

            float dt =
                    (now - lastFrameTime)
                    / 1_000_000_000f;

            lastFrameTime = now;

            dt = Math.max(
                    0.001f,
                    Math.min(
                            dt,
                            0.033f
                    )
            );

            for (Particle p : particles) {

                float floatOffsetX =
                        (float) Math.sin(
                                time
                                * p.speedX
                                * speedMultiplier
                                + p.phaseX
                        )
                        * p.amplitudeX
                        * amplitudeMultiplier;

                float floatOffsetY =
                        (float) Math.cos(
                                time
                                * p.speedY
                                * speedMultiplier
                                + p.phaseY
                        )
                        * p.amplitudeY
                        * amplitudeMultiplier;

                float currentX =
                        p.baseX
                        + floatOffsetX
                        + p.displacementX;

                float currentY =
                        p.baseY
                        + floatOffsetY
                        + p.displacementY;

                // ---------- Отталкивание курсором ----------
                if (mouseInside) {
                    float dx = currentX - mouseX;
                    float dy = currentY - mouseY;

                    float distanceSquared =
                            dx * dx
                            + dy * dy;

                    float radiusSquared =
                            mouseRadius
                            * mouseRadius;

                    if (
                            distanceSquared < radiusSquared
                            && distanceSquared > 0.01f
                    ) {
                        float distance =
                                (float) Math.sqrt(
                                        distanceSquared
                                );

                        float nx = dx / distance;
                        float ny = dy / distance;

                        float influence =
                                1f
                                - distance / mouseRadius;

                        float force =
                                mouseForce
                                * influence
                                * influence;

                        p.velocityX +=
                                nx
                                * force
                                * dt;

                        p.velocityY +=
                                ny
                                * force
                                * dt;
                    }
                }

                // ---------- Пружинный возврат ----------
                p.velocityX +=
                        -p.displacementX
                        * springStrength
                        * dt;

                p.velocityY +=
                        -p.displacementY
                        * springStrength
                        * dt;

                float dampingFactor =
                        (float) Math.exp(
                                -DAMPING * dt
                        );

                p.velocityX *= dampingFactor;
                p.velocityY *= dampingFactor;

                p.displacementX +=
                        p.velocityX
                        * dt;

                p.displacementY +=
                        p.velocityY
                        * dt;

                // ---------- Ограничение разлёта ----------
                float displacementSquared =
                        p.displacementX
                        * p.displacementX
                        + p.displacementY
                        * p.displacementY;

                if (
                        displacementSquared
                        > MAX_DISPLACEMENT
                        * MAX_DISPLACEMENT
                ) {
                    float length =
                            (float) Math.sqrt(
                                    displacementSquared
                            );

                    float factor =
                            MAX_DISPLACEMENT
                            / length;

                    p.displacementX *= factor;
                    p.displacementY *= factor;

                    p.velocityX *= 0.55f;
                    p.velocityY *= 0.55f;
                }

                float drawX =
                        p.baseX
                        + floatOffsetX
                        + p.displacementX;

                float drawY =
                        p.baseY
                        + floatOffsetY
                        + p.displacementY;

                g2.setColor(
                        new Color(
                                particleColor.getRed(),
                                particleColor.getGreen(),
                                particleColor.getBlue(),
                                p.opacity
                        )
                );

                g2.fill(
                        new Ellipse2D.Float(
                                drawX,
                                drawY,
                                p.size,
                                p.size
                        )
                );
            }

            g2.dispose();
        }
    }

    // ============================================================
    // Тёмный слайдер
    // ============================================================

    static class DarkSlider extends JSlider {

        DarkSlider(
                int min,
                int max,
                int value
        ) {
            super(min, max, value);

            setOpaque(false);
            setFocusable(false);

            setUI(
                    new BasicSliderUI(this) {

                        @Override
                        public void paintTrack(Graphics g) {
                            Graphics2D g2 =
                                    (Graphics2D) g.create();

                            g2.setRenderingHint(
                                    RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON
                            );

                            int y =
                                    trackRect.y
                                    + trackRect.height / 2
                                    - 2;

                            g2.setColor(
                                    new Color(
                                            55,
                                            57,
                                            62
                                    )
                            );

                            g2.fillRoundRect(
                                    trackRect.x,
                                    y,
                                    trackRect.width,
                                    4,
                                    4,
                                    4
                            );

                            int filled =
                                    thumbRect.x
                                    + thumbRect.width / 2
                                    - trackRect.x;

                            g2.setColor(
                                    new Color(
                                            180,
                                            183,
                                            188
                                    )
                            );

                            g2.fillRoundRect(
                                    trackRect.x,
                                    y,
                                    Math.max(
                                            0,
                                            filled
                                    ),
                                    4,
                                    4,
                                    4
                            );

                            g2.dispose();
                        }

                        @Override
                        public void paintThumb(Graphics g) {
                            Graphics2D g2 =
                                    (Graphics2D) g.create();

                            g2.setRenderingHint(
                                    RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON
                            );

                            g2.setColor(
                                    MAIN_TEXT
                            );

                            g2.fillOval(
                                    thumbRect.x + 2,
                                    thumbRect.y + 2,
                                    thumbRect.width - 4,
                                    thumbRect.height - 4
                            );

                            g2.dispose();
                        }
                    }
            );
        }
    }



    // ============================================================
    // Figma-like выбор цвета
    // ============================================================

    static class ModernColorDialog extends JDialog {

        private Color currentColor;
        private Color resultColor;

        private float hue;
        private float saturation;
        private float brightness;

        private boolean internalUpdate = false;

        private final JTextField hexField =
                new JTextField();

        private final JLabel rgbLabel =
                new JLabel();

        private final ColorField colorField =
                new ColorField();

        private final HueBar hueBar =
                new HueBar();

        private final JPanel currentSwatch =
                new JPanel();

        private static final Color[] PRESETS = {
                new Color(238, 238, 234),
                new Color(176, 181, 188),
                new Color(87, 92, 99),
                new Color(31, 32, 35),
                new Color(235, 87, 87),
                new Color(242, 153, 74),
                new Color(242, 201, 76),
                new Color(39, 174, 96),
                new Color(45, 156, 219),
                new Color(47, 128, 237),
                new Color(155, 81, 224),
                new Color(187, 107, 217)
        };

        ModernColorDialog(
                Component parent,
                Color initialColor
        ) {
            super(
                    SwingUtilities.getWindowAncestor(parent)
            );

            setModal(true);
            setUndecorated(true);

            setBackground(
                    new Color(
                            0,
                            0,
                            0,
                            0
                    )
            );

            setSize(
                    370,
                    520
            );

            currentColor =
                    initialColor != null
                            ? initialColor
                            : Color.WHITE;

            float[] hsb =
                    Color.RGBtoHSB(
                            currentColor.getRed(),
                            currentColor.getGreen(),
                            currentColor.getBlue(),
                            null
                    );

            hue = hsb[0];
            saturation = hsb[1];
            brightness = hsb[2];

            RoundedPanel root =
                    new RoundedPanel(
                            22,
                            CARD
                    );

            root.setLayout(
                    new BorderLayout()
            );

            root.setBorder(
                    new EmptyBorder(
                            20,
                            20,
                            18,
                            20
                    )
            );

            // ---------------- Header ----------------

            JPanel header =
                    new JPanel(
                            new BorderLayout()
                    );

            header.setOpaque(false);

            JLabel title =
                    label(
                            "Выбор цвета",
                            20,
                            Font.BOLD,
                            MAIN_TEXT
                    );

            DialogCloseButton close =
                    new DialogCloseButton();

            close.addActionListener(
                    e -> dispose()
            );

            header.add(
                    title,
                    BorderLayout.WEST
            );

            header.add(
                    close,
                    BorderLayout.EAST
            );

            root.add(
                    header,
                    BorderLayout.NORTH
            );

            // ---------------- Main content ----------------

            JPanel content =
                    new JPanel();

            content.setOpaque(false);

            content.setLayout(
                    new BoxLayout(
                            content,
                            BoxLayout.Y_AXIS
                    )
            );

            content.add(
                    Box.createVerticalStrut(16)
            );

            colorField.setAlignmentX(
                    Component.LEFT_ALIGNMENT
            );

            colorField.setPreferredSize(
                    new Dimension(
                            330,
                            225
                    )
            );

            colorField.setMaximumSize(
                    new Dimension(
                            Integer.MAX_VALUE,
                            225
                    )
            );

            content.add(colorField);

            content.add(
                    Box.createVerticalStrut(14)
            );

            hueBar.setAlignmentX(
                    Component.LEFT_ALIGNMENT
            );

            hueBar.setPreferredSize(
                    new Dimension(
                            330,
                            18
                    )
            );

            hueBar.setMaximumSize(
                    new Dimension(
                            Integer.MAX_VALUE,
                            18
                    )
            );

            content.add(hueBar);

            content.add(
                    Box.createVerticalStrut(16)
            );

            // ---------------- Color info ----------------

            JPanel valueRow =
                    new JPanel(
                            new BorderLayout(
                                    10,
                                    0
                            )
                    );

            valueRow.setOpaque(false);

            valueRow.setAlignmentX(
                    Component.LEFT_ALIGNMENT
            );

            currentSwatch.setPreferredSize(
                    new Dimension(
                            42,
                            38
                    )
            );

            currentSwatch.setMinimumSize(
                    new Dimension(
                            42,
                            38
                    )
            );

            currentSwatch.setBorder(
                    BorderFactory.createLineBorder(
                            BORDER
                    )
            );

            hexField.setBackground(
                    INPUT_BACKGROUND
            );

            hexField.setForeground(
                    MAIN_TEXT
            );

            hexField.setCaretColor(
                    MAIN_TEXT
            );

            hexField.setFont(
                    new Font(
                            "Monospaced",
                            Font.PLAIN,
                            14
                    )
            );

            hexField.setBorder(
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(
                                    BORDER
                            ),
                            new EmptyBorder(
                                    9,
                                    11,
                                    9,
                                    11
                            )
                    )
            );

            hexField.addActionListener(
                    e -> applyHexField()
            );

            hexField.addFocusListener(
                    new FocusAdapter() {
                        @Override
                        public void focusLost(
                                FocusEvent e
                        ) {
                            applyHexField();
                        }
                    }
            );

            valueRow.add(
                    currentSwatch,
                    BorderLayout.WEST
            );

            valueRow.add(
                    hexField,
                    BorderLayout.CENTER
            );

            content.add(valueRow);

            content.add(
                    Box.createVerticalStrut(8)
            );

            rgbLabel.setForeground(
                    SECONDARY_TEXT
            );

            rgbLabel.setFont(
                    new Font(
                            "SansSerif",
                            Font.PLAIN,
                            12
                    )
            );

            rgbLabel.setAlignmentX(
                    Component.LEFT_ALIGNMENT
            );

            content.add(rgbLabel);

            content.add(
                    Box.createVerticalStrut(14)
            );

            // ---------------- Presets ----------------

            JPanel presets =
                    new JPanel(
                            new GridLayout(
                                    2,
                                    6,
                                    8,
                                    8
                            )
                    );

            presets.setOpaque(false);

            presets.setAlignmentX(
                    Component.LEFT_ALIGNMENT
            );

            presets.setMaximumSize(
                    new Dimension(
                            Integer.MAX_VALUE,
                            70
                    )
            );

            for (Color preset : PRESETS) {
                JButton swatch =
                        createSwatchButton(
                                preset
                        );

                swatch.addActionListener(
                        e -> setFromColor(
                                preset
                        )
                );

                presets.add(swatch);
            }

            content.add(presets);

            root.add(
                    content,
                    BorderLayout.CENTER
            );

            // ---------------- Actions ----------------

            JPanel actions =
                    new JPanel(
                            new GridLayout(
                                    1,
                                    2,
                                    10,
                                    0
                            )
                    );

            actions.setOpaque(false);

            JButton cancel =
                    createActionButton(
                            "Отмена"
                    );

            JButton apply =
                    createActionButton(
                            "Применить"
                    );

            cancel.addActionListener(
                    e -> dispose()
            );

            apply.addActionListener(e -> {
                resultColor =
                        currentColor;

                dispose();
            });

            actions.add(cancel);
            actions.add(apply);

            root.add(
                    actions,
                    BorderLayout.SOUTH
            );

            setContentPane(root);

            setFromColor(
                    currentColor
            );

            setLocationRelativeTo(
                    parent
            );

            getRootPane().registerKeyboardAction(
                    e -> dispose(),
                    KeyStroke.getKeyStroke(
                            KeyEvent.VK_ESCAPE,
                            0
                    ),
                    JComponent.WHEN_IN_FOCUSED_WINDOW
            );
        }

        private void setFromColor(
                Color color
        ) {
            internalUpdate = true;

            currentColor = color;

            float[] hsb =
                    Color.RGBtoHSB(
                            color.getRed(),
                            color.getGreen(),
                            color.getBlue(),
                            null
                    );

            hue = hsb[0];
            saturation = hsb[1];
            brightness = hsb[2];

            syncTextValues();

            colorField.invalidateCache();
            colorField.repaint();
            hueBar.repaint();

            internalUpdate = false;
        }

        private void setFromHsb(
                float newHue,
                float newSaturation,
                float newBrightness
        ) {
            hue =
                    clamp01(
                            newHue
                    );

            saturation =
                    clamp01(
                            newSaturation
                    );

            brightness =
                    clamp01(
                            newBrightness
                    );

            currentColor =
                    Color.getHSBColor(
                            hue,
                            saturation,
                            brightness
                    );

            syncTextValues();

            colorField.repaint();
            hueBar.repaint();
        }

        private void syncTextValues() {
            currentSwatch.setBackground(
                    currentColor
            );

            hexField.setText(
                    colorToHex(
                            currentColor
                    )
            );

            rgbLabel.setText(
                    String.format(
                            "R %d     G %d     B %d",
                            currentColor.getRed(),
                            currentColor.getGreen(),
                            currentColor.getBlue()
                    )
            );
        }

        private void applyHexField() {
            if (internalUpdate) {
                return;
            }

            Color parsed =
                    parseHexColor(
                            hexField.getText()
                    );

            if (parsed != null) {
                setFromColor(
                        parsed
                );
            } else {
                hexField.setText(
                        colorToHex(
                                currentColor
                        )
                );
            }
        }

        private float clamp01(
                float value
        ) {
            return Math.max(
                    0f,
                    Math.min(
                            1f,
                            value
                    )
            );
        }

        private JButton createSwatchButton(
                Color color
        ) {
            JButton button =
                    new JButton() {

                        @Override
                        protected void paintComponent(
                                Graphics g
                        ) {
                            Graphics2D g2 =
                                    (Graphics2D)
                                            g.create();

                            g2.setRenderingHint(
                                    RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON
                            );

                            g2.setColor(color);

                            g2.fillRoundRect(
                                    2,
                                    2,
                                    getWidth() - 4,
                                    getHeight() - 4,
                                    9,
                                    9
                            );

                            g2.setColor(BORDER);

                            g2.drawRoundRect(
                                    2,
                                    2,
                                    getWidth() - 5,
                                    getHeight() - 5,
                                    9,
                                    9
                            );

                            g2.dispose();
                        }
                    };

            button.setPreferredSize(
                    new Dimension(
                            44,
                            30
                    )
            );

            button.setBorderPainted(false);
            button.setFocusPainted(false);
            button.setContentAreaFilled(false);

            button.setCursor(
                    Cursor.getPredefinedCursor(
                            Cursor.HAND_CURSOR
                    )
            );

            return button;
        }

        // --------------------------------------------------------
        // Saturation / Brightness field
        // --------------------------------------------------------

        class ColorField extends JComponent {

            private BufferedImage cache;
            private float cachedHue = -1f;
            private int cachedWidth = -1;
            private int cachedHeight = -1;

            ColorField() {
                setCursor(
                        Cursor.getPredefinedCursor(
                                Cursor.CROSSHAIR_CURSOR
                        )
                );

                MouseAdapter mouse =
                        new MouseAdapter() {

                            @Override
                            public void mousePressed(
                                    MouseEvent e
                            ) {
                                updateValue(e);
                            }

                            @Override
                            public void mouseDragged(
                                    MouseEvent e
                            ) {
                                updateValue(e);
                            }
                        };

                addMouseListener(mouse);
                addMouseMotionListener(mouse);
            }

            void invalidateCache() {
                cache = null;
                cachedHue = -1f;
            }

            private void updateValue(
                    MouseEvent e
            ) {
                float s =
                        e.getX()
                        / (float)
                        Math.max(
                                1,
                                getWidth() - 1
                        );

                float b =
                        1f
                        - e.getY()
                        / (float)
                        Math.max(
                                1,
                                getHeight() - 1
                        );

                setFromHsb(
                        hue,
                        s,
                        b
                );
            }

            private void ensureCache() {
                int width =
                        Math.max(
                                1,
                                getWidth()
                        );

                int height =
                        Math.max(
                                1,
                                getHeight()
                        );

                if (
                        cache != null
                        && Math.abs(
                                cachedHue
                                - hue
                        ) < 0.0001f
                        && cachedWidth
                        == width
                        && cachedHeight
                        == height
                ) {
                    return;
                }

                cache =
                        new BufferedImage(
                                width,
                                height,
                                BufferedImage.TYPE_INT_RGB
                        );

                for (
                        int y = 0;
                        y < height;
                        y++
                ) {
                    float b =
                            1f
                            - y
                            / (float)
                            Math.max(
                                    1,
                                    height - 1
                            );

                    for (
                            int x = 0;
                            x < width;
                            x++
                    ) {
                        float s =
                                x
                                / (float)
                                Math.max(
                                        1,
                                        width - 1
                                );

                        cache.setRGB(
                                x,
                                y,
                                Color.getHSBColor(
                                        hue,
                                        s,
                                        b
                                ).getRGB()
                        );
                    }
                }

                cachedHue = hue;
                cachedWidth = width;
                cachedHeight = height;
            }

            @Override
            protected void paintComponent(
                    Graphics g
            ) {
                super.paintComponent(g);

                ensureCache();

                Graphics2D g2 =
                        (Graphics2D)
                                g.create();

                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                Shape oldClip =
                        g2.getClip();

                g2.clip(
                        new java.awt.geom.RoundRectangle2D.Float(
                                0,
                                0,
                                getWidth(),
                                getHeight(),
                                12,
                                12
                        )
                );

                g2.drawImage(
                        cache,
                        0,
                        0,
                        null
                );

                g2.setClip(
                        oldClip
                );

                int markerX =
                        Math.round(
                                saturation
                                * (
                                        getWidth()
                                        - 1
                                )
                        );

                int markerY =
                        Math.round(
                                (
                                        1f
                                        - brightness
                                )
                                * (
                                        getHeight()
                                        - 1
                                )
                        );

                g2.setStroke(
                        new BasicStroke(
                                2.2f
                        )
                );

                g2.setColor(
                        Color.WHITE
                );

                g2.drawOval(
                        markerX - 7,
                        markerY - 7,
                        14,
                        14
                );

                g2.setStroke(
                        new BasicStroke(
                                1f
                        )
                );

                g2.setColor(
                        new Color(
                                0,
                                0,
                                0,
                                150
                        )
                );

                g2.drawOval(
                        markerX - 9,
                        markerY - 9,
                        18,
                        18
                );

                g2.dispose();
            }
        }

        // --------------------------------------------------------
        // Hue bar
        // --------------------------------------------------------

        class HueBar extends JComponent {

            HueBar() {
                setCursor(
                        Cursor.getPredefinedCursor(
                                Cursor.HAND_CURSOR
                        )
                );

                MouseAdapter mouse =
                        new MouseAdapter() {

                            @Override
                            public void mousePressed(
                                    MouseEvent e
                            ) {
                                updateHue(e);
                            }

                            @Override
                            public void mouseDragged(
                                    MouseEvent e
                            ) {
                                updateHue(e);
                            }
                        };

                addMouseListener(mouse);
                addMouseMotionListener(mouse);
            }

            private void updateHue(
                    MouseEvent e
            ) {
                float newHue =
                        e.getX()
                        / (float)
                        Math.max(
                                1,
                                getWidth() - 1
                        );

                hue =
                        clamp01(
                                newHue
                        );

                currentColor =
                        Color.getHSBColor(
                                hue,
                                saturation,
                                brightness
                        );

                syncTextValues();

                colorField.invalidateCache();
                colorField.repaint();
                repaint();
            }

            @Override
            protected void paintComponent(
                    Graphics g
            ) {
                super.paintComponent(g);

                Graphics2D g2 =
                        (Graphics2D)
                                g.create();

                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                Shape oldClip =
                        g2.getClip();

                g2.clip(
                        new java.awt.geom.RoundRectangle2D.Float(
                                0,
                                0,
                                getWidth(),
                                getHeight(),
                                10,
                                10
                        )
                );

                for (
                        int x = 0;
                        x < getWidth();
                        x++
                ) {
                    float h =
                            x
                            / (float)
                            Math.max(
                                    1,
                                    getWidth() - 1
                            );

                    g2.setColor(
                            Color.getHSBColor(
                                    h,
                                    1f,
                                    1f
                            )
                    );

                    g2.drawLine(
                            x,
                            0,
                            x,
                            getHeight()
                    );
                }

                g2.setClip(
                        oldClip
                );

                int markerX =
                        Math.round(
                                hue
                                * (
                                        getWidth()
                                        - 1
                                )
                        );

                g2.setColor(
                        Color.WHITE
                );

                g2.fillOval(
                        markerX - 6,
                        getHeight() / 2 - 6,
                        12,
                        12
                );

                g2.setColor(
                        new Color(
                                20,
                                20,
                                22
                        )
                );

                g2.drawOval(
                        markerX - 6,
                        getHeight() / 2 - 6,
                        12,
                        12
                );

                g2.dispose();
            }
        }

        // --------------------------------------------------------
        // Font-independent X button
        // --------------------------------------------------------

        static class DialogCloseButton extends JButton {

            private boolean hover = false;

            DialogCloseButton() {
                setPreferredSize(
                        new Dimension(
                                32,
                                30
                        )
                );

                setBorderPainted(false);
                setFocusPainted(false);
                setContentAreaFilled(false);

                setCursor(
                        Cursor.getPredefinedCursor(
                                Cursor.HAND_CURSOR
                        )
                );

                addMouseListener(
                        new MouseAdapter() {

                            @Override
                            public void mouseEntered(
                                    MouseEvent e
                            ) {
                                hover = true;
                                repaint();
                            }

                            @Override
                            public void mouseExited(
                                    MouseEvent e
                            ) {
                                hover = false;
                                repaint();
                            }
                        }
                );
            }

            @Override
            protected void paintComponent(
                    Graphics g
            ) {
                Graphics2D g2 =
                        (Graphics2D)
                                g.create();

                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                if (hover) {
                    g2.setColor(
                            BUTTON_HOVER
                    );

                    g2.fillRoundRect(
                            0,
                            0,
                            getWidth(),
                            getHeight(),
                            8,
                            8
                    );
                }

                g2.setStroke(
                        new BasicStroke(
                                1.5f,
                                BasicStroke.CAP_ROUND,
                                BasicStroke.JOIN_ROUND
                        )
                );

                g2.setColor(
                        MAIN_TEXT
                );

                int cx =
                        getWidth() / 2;

                int cy =
                        getHeight() / 2;

                g2.drawLine(
                        cx - 5,
                        cy - 5,
                        cx + 5,
                        cy + 5
                );

                g2.drawLine(
                        cx + 5,
                        cy - 5,
                        cx - 5,
                        cy + 5
                );

                g2.dispose();
            }
        }

        static Color showDialog(
                Component parent,
                Color initialColor
        ) {
            ModernColorDialog dialog =
                    new ModernColorDialog(
                            parent,
                            initialColor
                    );

            dialog.setVisible(true);

            return dialog.resultColor;
        }
    }

    // ============================================================
    // Кнопка выбора цвета
    // ============================================================

    static class ColorButton extends JButton {

        private Color selectedColor;

        ColorButton(
                Color initialColor,
                Consumer<Color> onChange
        ) {
            selectedColor = initialColor;

            setPreferredSize(
                    new Dimension(
                            138,
                            38
                    )
            );

            setMinimumSize(
                    new Dimension(
                            138,
                            38
                    )
            );

            setMaximumSize(
                    new Dimension(
                            138,
                            38
                    )
            );

            setBorderPainted(false);
            setFocusPainted(false);
            setContentAreaFilled(false);

            setCursor(
                    Cursor.getPredefinedCursor(
                            Cursor.HAND_CURSOR
                    )
            );

            addActionListener(e -> {
                Color chosen =
                        ModernColorDialog.showDialog(
                                this,
                                selectedColor
                        );

                if (chosen != null) {
                    selectedColor = chosen;
                    repaint();
                    onChange.accept(chosen);
                }
            });
        }

        void setSelectedColor(Color color) {
            selectedColor = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 =
                    (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            g2.setColor(
                    INPUT_BACKGROUND
            );

            g2.fillRoundRect(
                    0,
                    0,
                    getWidth(),
                    getHeight(),
                    12,
                    12
            );

            g2.setColor(BORDER);

            g2.drawRoundRect(
                    0,
                    0,
                    getWidth() - 1,
                    getHeight() - 1,
                    12,
                    12
            );

            g2.setColor(
                    selectedColor
            );

            g2.fillOval(
                    11,
                    9,
                    20,
                    20
            );

            String hex = String.format(
                    "#%02X%02X%02X",
                    selectedColor.getRed(),
                    selectedColor.getGreen(),
                    selectedColor.getBlue()
            );

            g2.setColor(MAIN_TEXT);

            g2.setFont(
                    new Font(
                            "SansSerif",
                            Font.PLAIN,
                            12
                    )
            );

            g2.drawString(
                    hex,
                    42,
                    24
            );

            g2.dispose();
        }
    }

    // ============================================================
    // Блок одного слайдера
    // ============================================================

    static class SliderSetting extends JPanel {

        final DarkSlider slider;
        final JLabel valueLabel;

        SliderSetting(
                String name,
                int min,
                int max,
                int value,
                String suffix,
                IntConsumer onChange
        ) {
            setOpaque(false);

            setAlignmentX(Component.LEFT_ALIGNMENT);

            setMaximumSize(
                    new Dimension(
                            Integer.MAX_VALUE,
                            54
                    )
            );

            setLayout(
                    new BoxLayout(
                            this,
                            BoxLayout.Y_AXIS
                    )
            );

            JPanel top =
                    new JPanel(
                            new BorderLayout()
                    );

            top.setOpaque(false);

            JLabel nameLabel =
                    new JLabel(name);

            nameLabel.setFont(
                    new Font(
                            "SansSerif",
                            Font.PLAIN,
                            13
                    )
            );

            nameLabel.setForeground(
                    MAIN_TEXT
            );

            valueLabel =
                    new JLabel(
                            value + suffix
                    );

            valueLabel.setFont(
                    new Font(
                            "SansSerif",
                            Font.PLAIN,
                            12
                    )
            );

            valueLabel.setForeground(
                    SECONDARY_TEXT
            );

            top.add(
                    nameLabel,
                    BorderLayout.WEST
            );

            top.add(
                    valueLabel,
                    BorderLayout.EAST
            );

            slider =
                    new DarkSlider(
                            min,
                            max,
                            value
                    );

            slider.addChangeListener(e -> {
                int current =
                        slider.getValue();

                valueLabel.setText(
                        current + suffix
                );

                onChange.accept(current);
            });

            add(top);

            add(
                    Box.createVerticalStrut(5)
            );

            add(slider);
        }
    }

    // ============================================================
    // Кнопки окна
    // ============================================================

    static class WindowButton extends JButton {

        private final WindowButtonType type;
        private boolean hover = false;

        WindowButton(WindowButtonType type) {
            this.type = type;

            setBorderPainted(false);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);

            setPreferredSize(
                    new Dimension(
                            48,
                            40
                    )
            );

            setCursor(
                    Cursor.getPredefinedCursor(
                            Cursor.HAND_CURSOR
                    )
            );

            addMouseListener(
                    new MouseAdapter() {

                        @Override
                        public void mouseEntered(
                                MouseEvent e
                        ) {
                            hover = true;
                            repaint();
                        }

                        @Override
                        public void mouseExited(
                                MouseEvent e
                        ) {
                            hover = false;
                            repaint();
                        }
                    }
            );
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 =
                    (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            if (hover) {
                g2.setColor(
                        type
                        == WindowButtonType.CLOSE
                                ? CLOSE_HOVER
                                : BUTTON_HOVER
                );

                g2.fillRect(
                        0,
                        0,
                        getWidth(),
                        getHeight()
                );
            }

            g2.setColor(
                    new Color(
                            215,
                            217,
                            220
                    )
            );

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            g2.setStroke(
                    new BasicStroke(
                            1.4f,
                            BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND
                    )
            );

            switch (type) {
                case MINIMIZE ->
                        g2.drawLine(
                                cx - 6,
                                cy + 2,
                                cx + 6,
                                cy + 2
                        );

                case MAXIMIZE ->
                        g2.drawRect(
                                cx - 6,
                                cy - 5,
                                12,
                                10
                        );

                case CLOSE -> {
                    g2.drawLine(
                            cx - 5,
                            cy - 5,
                            cx + 5,
                            cy + 5
                    );

                    g2.drawLine(
                            cx + 5,
                            cy - 5,
                            cx - 5,
                            cy + 5
                    );
                }
            }

            g2.dispose();
        }
    }

    // ============================================================
    // Заголовочная панель окна
    // ============================================================

    static JPanel createTitleBar(
            JFrame frame,
            Color background
    ) {
        JPanel bar =
                new JPanel(
                        new BorderLayout()
                );

        bar.setBackground(background);

        bar.setPreferredSize(
                new Dimension(
                        0,
                        40
                )
        );

        JLabel title =
                new JLabel(
                        "  Типографика частиц"
                );

        title.setForeground(
                new Color(
                        205,
                        207,
                        210
                )
        );

        title.setFont(
                new Font(
                        "SansSerif",
                        Font.PLAIN,
                        12
                )
        );

        JPanel buttons =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT,
                                0,
                                0
                        )
                );

        buttons.setOpaque(false);

        WindowButton minimize =
                new WindowButton(
                        WindowButtonType.MINIMIZE
                );

        WindowButton maximize =
                new WindowButton(
                        WindowButtonType.MAXIMIZE
                );

        WindowButton close =
                new WindowButton(
                        WindowButtonType.CLOSE
                );

        minimize.addActionListener(
                e -> frame.setState(
                        Frame.ICONIFIED
                )
        );

        maximize.addActionListener(e -> {
            if (
                    (
                            frame.getExtendedState()
                            & Frame.MAXIMIZED_BOTH
                    ) != 0
            ) {
                frame.setExtendedState(
                        Frame.NORMAL
                );
            } else {
                frame.setExtendedState(
                        Frame.MAXIMIZED_BOTH
                );
            }
        });

        close.addActionListener(
                e -> System.exit(0)
        );

        buttons.add(minimize);
        buttons.add(maximize);
        buttons.add(close);

        bar.add(
                title,
                BorderLayout.WEST
        );

        bar.add(
                buttons,
                BorderLayout.EAST
        );

        final Point[] dragOffset = {
                null
        };

        MouseAdapter dragListener =
                new MouseAdapter() {

                    @Override
                    public void mousePressed(
                            MouseEvent e
                    ) {
                        dragOffset[0] =
                                e.getPoint();
                    }

                    @Override
                    public void mouseDragged(
                            MouseEvent e
                    ) {
                        if (
                                dragOffset[0]
                                == null
                        ) {
                            return;
                        }

                        if (
                                (
                                        frame.getExtendedState()
                                        & Frame.MAXIMIZED_BOTH
                                ) != 0
                        ) {
                            return;
                        }

                        Point location =
                                e.getLocationOnScreen();

                        frame.setLocation(
                                location.x
                                - dragOffset[0].x,
                                location.y
                                - dragOffset[0].y
                        );
                    }

                    @Override
                    public void mouseClicked(
                            MouseEvent e
                    ) {
                        if (
                                e.getClickCount() == 2
                                && SwingUtilities
                                        .isLeftMouseButton(e)
                        ) {
                            if (
                                    (
                                            frame.getExtendedState()
                                            & Frame.MAXIMIZED_BOTH
                                    ) != 0
                            ) {
                                frame.setExtendedState(
                                        Frame.NORMAL
                                );
                            } else {
                                frame.setExtendedState(
                                        Frame.MAXIMIZED_BOTH
                                );
                            }
                        }
                    }
                };

        bar.addMouseListener(
                dragListener
        );

        bar.addMouseMotionListener(
                dragListener
        );

        title.addMouseListener(
                dragListener
        );

        title.addMouseMotionListener(
                dragListener
        );

        return bar;
    }

    // ============================================================
    // Вспомогательные методы
    // ============================================================

    private static JLabel label(
            String text,
            int size,
            int style,
            Color color
    ) {
        JLabel label =
                new JLabel(text);

        label.setFont(
                new Font(
                        "SansSerif",
                        style,
                        size
                )
        );

        label.setForeground(color);

        return label;
    }

    private static JPanel createColorRow(
            String name,
            ColorButton button
    ) {
        JPanel row =
                new JPanel(
                        new BorderLayout(
                                10,
                                0
                        )
                );

        row.setOpaque(false);

        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        row.setPreferredSize(
                new Dimension(
                        280,
                        42
                )
        );

        row.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        42
                )
        );

        JLabel label =
                new JLabel(name);

        label.setFont(
                new Font(
                        "SansSerif",
                        Font.PLAIN,
                        13
                )
        );

        label.setForeground(MAIN_TEXT);
        label.setVerticalAlignment(SwingConstants.CENTER);

        row.add(
                label,
                BorderLayout.WEST
        );

        row.add(
                button,
                BorderLayout.EAST
        );

        return row;
    }

    private static JButton createActionButton(
            String text
    ) {
        JButton button =
                new JButton(text);

        button.setFocusPainted(false);
        button.setForeground(MAIN_TEXT);
        button.setBackground(INPUT_BACKGROUND);

        button.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                BORDER
                        ),
                        new EmptyBorder(
                                10,
                                12,
                                10,
                                12
                        )
                )
        );

        button.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );

        button.setFont(
                new Font(
                        "SansSerif",
                        Font.PLAIN,
                        13
                )
        );

        button.setPreferredSize(
                new Dimension(
                        0,
                        40
                )
        );

        button.setMinimumSize(
                new Dimension(
                        0,
                        40
                )
        );

        return button;
    }


    private static String colorToHex(
            Color color
    ) {
        return String.format(
                "#%02X%02X%02X",
                color.getRed(),
                color.getGreen(),
                color.getBlue()
        );
    }

    private static Color parseHexColor(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String cleaned =
                value.trim();

        if (cleaned.startsWith("#")) {
            cleaned =
                    cleaned.substring(1);
        }

        if (!cleaned.matches("[0-9A-Fa-f]{6}")) {
            return null;
        }

        try {
            return new Color(
                    Integer.parseInt(
                            cleaned,
                            16
                    )
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }


    static class ModernNoticeDialog extends JDialog {

        ModernNoticeDialog(
                Component parent,
                String titleText,
                String message,
                boolean error
        ) {
            super(
                    SwingUtilities.getWindowAncestor(parent)
            );

            setModal(true);
            setUndecorated(true);

            setBackground(
                    new Color(
                            0,
                            0,
                            0,
                            0
                    )
            );

            setSize(
                    430,
                    220
            );

            RoundedPanel root =
                    new RoundedPanel(
                            20,
                            CARD
                    );

            root.setLayout(
                    new BorderLayout()
            );

            root.setBorder(
                    new EmptyBorder(
                            20,
                            22,
                            18,
                            22
                    )
            );

            JPanel header =
                    new JPanel(
                            new BorderLayout()
                    );

            header.setOpaque(false);

            JLabel title =
                    label(
                            titleText,
                            18,
                            Font.BOLD,
                            error
                                    ? new Color(
                                            235,
                                            100,
                                            100
                                    )
                                    : MAIN_TEXT
                    );

            ModernColorDialog.DialogCloseButton close =
                    new ModernColorDialog.DialogCloseButton();

            close.addActionListener(
                    e -> dispose()
            );

            header.add(
                    title,
                    BorderLayout.WEST
            );

            header.add(
                    close,
                    BorderLayout.EAST
            );

            JTextArea messageArea =
                    new JTextArea(
                            message
                    );

            messageArea.setEditable(false);
            messageArea.setOpaque(false);
            messageArea.setLineWrap(true);
            messageArea.setWrapStyleWord(true);

            messageArea.setForeground(
                    SECONDARY_TEXT
            );

            messageArea.setFont(
                    new Font(
                            "SansSerif",
                            Font.PLAIN,
                            13
                    )
            );

            JButton ok =
                    createActionButton(
                            "ОК"
                    );

            ok.addActionListener(
                    e -> dispose()
            );

            root.add(
                    header,
                    BorderLayout.NORTH
            );

            root.add(
                    messageArea,
                    BorderLayout.CENTER
            );

            root.add(
                    ok,
                    BorderLayout.SOUTH
            );

            setContentPane(root);

            setLocationRelativeTo(
                    parent
            );

            getRootPane().registerKeyboardAction(
                    e -> dispose(),
                    KeyStroke.getKeyStroke(
                            KeyEvent.VK_ESCAPE,
                            0
                    ),
                    JComponent.WHEN_IN_FOCUSED_WINDOW
            );
        }

        static void show(
                Component parent,
                String title,
                String message,
                boolean error
        ) {
            ModernNoticeDialog dialog =
                    new ModernNoticeDialog(
                            parent,
                            title,
                            message,
                            error
                    );

            dialog.setVisible(true);
        }
    }

    // ============================================================
    // GIF writer
    // ============================================================

    static class GifSequenceWriter implements AutoCloseable {

        private final ImageWriter writer;
        private final ImageWriteParam writeParam;
        private final IIOMetadata metadata;
        private final ImageOutputStream output;

        GifSequenceWriter(
                File file,
                int imageType,
                int delayMs
        ) throws IOException {

            Iterator<ImageWriter> writers =
                    ImageIO.getImageWritersBySuffix(
                            "gif"
                    );

            if (!writers.hasNext()) {
                throw new IOException(
                        "GIF writer недоступен"
                );
            }

            writer =
                    writers.next();

            writeParam =
                    writer.getDefaultWriteParam();

            ImageTypeSpecifier type =
                    ImageTypeSpecifier
                            .createFromBufferedImageType(
                                    imageType
                            );

            metadata =
                    writer.getDefaultImageMetadata(
                            type,
                            writeParam
                    );

            String format =
                    metadata
                            .getNativeMetadataFormatName();

            IIOMetadataNode root =
                    (IIOMetadataNode)
                            metadata.getAsTree(
                                    format
                            );

            IIOMetadataNode graphicsControl =
                    getNode(
                            root,
                            "GraphicControlExtension"
                    );

            graphicsControl.setAttribute(
                    "disposalMethod",
                    "none"
            );

            graphicsControl.setAttribute(
                    "userInputFlag",
                    "FALSE"
            );

            graphicsControl.setAttribute(
                    "transparentColorFlag",
                    "FALSE"
            );

            graphicsControl.setAttribute(
                    "delayTime",
                    Integer.toString(
                            Math.max(
                                    1,
                                    Math.round(
                                            delayMs / 10f
                                    )
                            )
                    )
            );

            graphicsControl.setAttribute(
                    "transparentColorIndex",
                    "0"
            );

            IIOMetadataNode applicationExtensions =
                    getNode(
                            root,
                            "ApplicationExtensions"
                    );

            IIOMetadataNode applicationExtension =
                    new IIOMetadataNode(
                            "ApplicationExtension"
                    );

            applicationExtension.setAttribute(
                    "applicationID",
                    "NETSCAPE"
            );

            applicationExtension.setAttribute(
                    "authenticationCode",
                    "2.0"
            );

            // 0 = бесконечное повторение GIF.
            applicationExtension.setUserObject(
                    new byte[] {
                            0x1,
                            0x0,
                            0x0
                    }
            );

            applicationExtensions.appendChild(
                    applicationExtension
            );

            metadata.setFromTree(
                    format,
                    root
            );

            output =
                    ImageIO.createImageOutputStream(
                            file
                    );

            writer.setOutput(output);
            writer.prepareWriteSequence(null);
        }

        void writeFrame(
                BufferedImage image
        ) throws IOException {
            writer.writeToSequence(
                    new IIOImage(
                            image,
                            null,
                            metadata
                    ),
                    writeParam
            );
        }

        @Override
        public void close() throws IOException {
            writer.endWriteSequence();
            output.close();
            writer.dispose();
        }

        private static IIOMetadataNode getNode(
                IIOMetadataNode root,
                String nodeName
        ) {
            int count =
                    root.getLength();

            for (
                    int i = 0;
                    i < count;
                    i++
            ) {
                Node node =
                        root.item(i);

                if (
                        nodeName.equalsIgnoreCase(
                                node.getNodeName()
                        )
                ) {
                    return (IIOMetadataNode) node;
                }
            }

            IIOMetadataNode node =
                    new IIOMetadataNode(
                            nodeName
                    );

            root.appendChild(node);

            return node;
        }
    }

    private static File chooseExportFile(
            Component parent,
            String extension
    ) {
        Window ownerWindow =
                SwingUtilities.getWindowAncestor(
                        parent
                );

        Frame ownerFrame =
                ownerWindow instanceof Frame
                        ? (Frame) ownerWindow
                        : null;

        FileDialog dialog =
                new FileDialog(
                        ownerFrame,
                        "Сохранить "
                        + extension.toUpperCase(),
                        FileDialog.SAVE
                );

        dialog.setFile(
                "particle-text."
                + extension
        );

        dialog.setVisible(true);

        String selectedFile =
                dialog.getFile();

        if (selectedFile == null) {
            return null;
        }

        String selectedDirectory =
                dialog.getDirectory();

        File file =
                selectedDirectory != null
                        ? new File(
                                selectedDirectory,
                                selectedFile
                        )
                        : new File(
                                selectedFile
                        );

        String lowerName =
                file.getName()
                        .toLowerCase();

        if (
                !lowerName.endsWith(
                        "."
                        + extension.toLowerCase()
                )
        ) {
            file =
                    new File(
                            file.getParentFile(),
                            file.getName()
                            + "."
                            + extension
                    );
        }

        return file;
    }

    private static void exportGif(
            Component parent,
            ParticleTextPanel particlePanel,
            JButton triggerButton
    ) {
        File output =
                chooseExportFile(
                        parent,
                        "gif"
                );

        if (output == null) {
            return;
        }

        ParticleTextPanel.ExportData exportData =
                particlePanel.createExportData();

        // GIF хранит задержку кадра в сотых долях секунды.
        // 50 FPS = 20 мс на кадр — это заметно плавнее 30 FPS
        // и гораздо совместимее с просмотрщиками, чем 100 FPS / 10 мс.
        final int fps = 50;
        final int durationSeconds = 4;
        final int frameCount =
                fps * durationSeconds;

        triggerButton.setEnabled(false);
        triggerButton.setText(
                "Экспорт..."
        );

        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground()
                    throws Exception {

                try (
                        GifSequenceWriter writer =
                                new GifSequenceWriter(
                                        output,
                                        BufferedImage.TYPE_INT_RGB,
                                        1000 / fps
                                )
                ) {
                    for (
                            int frame = 0;
                            frame < frameCount;
                            frame++
                    ) {
                        double progress =
                                frame
                                / (double) frameCount;

                        writer.writeFrame(
                                exportData.renderFrame(
                                        progress
                                )
                        );
                    }
                }

                return null;
            }

            @Override
            protected void done() {
                triggerButton.setEnabled(true);
                triggerButton.setText(
                        "Сохранить GIF"
                );

                try {
                    get();

                    ModernNoticeDialog.show(
                            parent,
                            "Сохранено",
                            output.getAbsolutePath(),
                            false
                    );
                } catch (Exception e) {
                    ModernNoticeDialog.show(
                            parent,
                            "Ошибка экспорта",
                            "Не удалось сохранить GIF:\\n"
                            + e.getMessage(),
                            true
                    );
                }
            }
        }.execute();
    }

    // ============================================================
    // Pure Java MP4 (Photo-JPEG / MJPEG)
    // ============================================================

    static class MjpegMp4Writer {

        private interface PayloadWriter {
            void write(
                    DataOutputStream out
            ) throws IOException;
        }

        static void write(
                File output,
                ParticleTextPanel.ExportData exportData,
                int fps,
                int durationSeconds
        ) throws IOException {

            int frameCount =
                    fps * durationSeconds;

            int[] sampleSizes =
                    new int[frameCount];

            byte[] ftyp =
                    createFtyp();

            try (
                    RandomAccessFile file =
                            new RandomAccessFile(
                                    output,
                                    "rw"
                            )
            ) {
                file.setLength(0);

                file.write(ftyp);

                long mdatPosition =
                        file.getFilePointer();

                // Размер mdat запишем после кодирования всех кадров.
                file.writeInt(0);
                file.writeBytes("mdat");

                long chunkOffset =
                        file.getFilePointer();

                for (
                        int frame = 0;
                        frame < frameCount;
                        frame++
                ) {
                    double progress =
                            frame
                            / (double) frameCount;

                    BufferedImage image =
                            exportData.renderFrame(
                                    progress
                            );

                    byte[] jpeg =
                            encodeJpeg(
                                    image,
                                    0.86f
                            );

                    sampleSizes[frame] =
                            jpeg.length;

                    file.write(jpeg);
                }

                long endOfMdat =
                        file.getFilePointer();

                long mdatSize =
                        endOfMdat
                        - mdatPosition;

                if (
                        mdatSize
                        > 0xFFFFFFFFL
                ) {
                    throw new IOException(
                            "Видео получилось слишком большим "
                            + "для текущего MP4 writer."
                    );
                }

                if (
                        chunkOffset
                        > 0xFFFFFFFFL
                ) {
                    throw new IOException(
                            "Слишком большой offset MP4."
                    );
                }

                byte[] moov =
                        createMoov(
                                exportData.width,
                                exportData.height,
                                fps,
                                frameCount,
                                sampleSizes,
                                chunkOffset
                        );

                file.write(moov);

                // Возвращаемся к заголовку mdat
                // и записываем его настоящий размер.
                file.seek(
                        mdatPosition
                );

                file.writeInt(
                        (int) mdatSize
                );
            }
        }

        private static byte[] encodeJpeg(
                BufferedImage image,
                float quality
        ) throws IOException {

            Iterator<ImageWriter> writers =
                    ImageIO.getImageWritersByFormatName(
                            "jpeg"
                    );

            if (!writers.hasNext()) {
                throw new IOException(
                        "В Java не найден JPEG encoder."
                );
            }

            ImageWriter writer =
                    writers.next();

            ByteArrayOutputStream bytes =
                    new ByteArrayOutputStream();

            try (
                    ImageOutputStream output =
                            ImageIO.createImageOutputStream(
                                    bytes
                            )
            ) {
                writer.setOutput(
                        output
                );

                ImageWriteParam params =
                        writer.getDefaultWriteParam();

                if (
                        params.canWriteCompressed()
                ) {
                    params.setCompressionMode(
                            ImageWriteParam.MODE_EXPLICIT
                    );

                    params.setCompressionQuality(
                            quality
                    );
                }

                writer.write(
                        null,
                        new IIOImage(
                                image,
                                null,
                                null
                        ),
                        params
                );

                output.flush();

            } finally {
                writer.dispose();
            }

            return bytes.toByteArray();
        }

        private static byte[] createFtyp()
                throws IOException {

            return box(
                    "ftyp",
                    out -> {
                        out.writeBytes(
                                "isom"
                        );

                        out.writeInt(
                                0x00000200
                        );

                        out.writeBytes(
                                "isom"
                        );

                        out.writeBytes(
                                "iso2"
                        );

                        out.writeBytes(
                                "mp41"
                        );

                        // QuickTime-compatible JPEG sample entry.
                        out.writeBytes(
                                "qt  "
                        );
                    }
            );
        }

        private static byte[] createMoov(
                int width,
                int height,
                int fps,
                int frameCount,
                int[] sampleSizes,
                long chunkOffset
        ) throws IOException {

            byte[] mvhd =
                    createMvhd(
                            fps,
                            frameCount
                    );

            byte[] trak =
                    createTrak(
                            width,
                            height,
                            fps,
                            frameCount,
                            sampleSizes,
                            chunkOffset
                    );

            return box(
                    "moov",
                    concat(
                            mvhd,
                            trak
                    )
            );
        }

        private static byte[] createMvhd(
                int timescale,
                int duration
        ) throws IOException {

            return fullBox(
                    "mvhd",
                    0,
                    0,
                    out -> {
                        out.writeInt(0);
                        out.writeInt(0);

                        out.writeInt(
                                timescale
                        );

                        out.writeInt(
                                duration
                        );

                        // rate = 1.0
                        out.writeInt(
                                0x00010000
                        );

                        // volume = 1.0
                        out.writeShort(
                                0x0100
                        );

                        out.writeShort(0);

                        out.writeInt(0);
                        out.writeInt(0);

                        writeMatrix(out);

                        // pre_defined[6]
                        for (
                                int i = 0;
                                i < 6;
                                i++
                        ) {
                            out.writeInt(0);
                        }

                        // next_track_ID
                        out.writeInt(2);
                    }
            );
        }

        private static byte[] createTrak(
                int width,
                int height,
                int fps,
                int frameCount,
                int[] sampleSizes,
                long chunkOffset
        ) throws IOException {

            byte[] tkhd =
                    createTkhd(
                            width,
                            height,
                            frameCount
                    );

            byte[] mdia =
                    createMdia(
                            width,
                            height,
                            fps,
                            frameCount,
                            sampleSizes,
                            chunkOffset
                    );

            return box(
                    "trak",
                    concat(
                            tkhd,
                            mdia
                    )
            );
        }

        private static byte[] createTkhd(
                int width,
                int height,
                int duration
        ) throws IOException {

            return fullBox(
                    "tkhd",
                    0,
                    0x000007,
                    out -> {
                        out.writeInt(0);
                        out.writeInt(0);

                        out.writeInt(1);
                        out.writeInt(0);

                        out.writeInt(
                                duration
                        );

                        out.writeInt(0);
                        out.writeInt(0);

                        out.writeShort(0);
                        out.writeShort(0);

                        // No audio volume for video track.
                        out.writeShort(0);
                        out.writeShort(0);

                        writeMatrix(out);

                        out.writeInt(
                                width << 16
                        );

                        out.writeInt(
                                height << 16
                        );
                    }
            );
        }

        private static byte[] createMdia(
                int width,
                int height,
                int fps,
                int frameCount,
                int[] sampleSizes,
                long chunkOffset
        ) throws IOException {

            byte[] mdhd =
                    fullBox(
                            "mdhd",
                            0,
                            0,
                            out -> {
                                out.writeInt(0);
                                out.writeInt(0);

                                out.writeInt(
                                        fps
                                );

                                out.writeInt(
                                        frameCount
                                );

                                // ISO-639 "und"
                                out.writeShort(
                                        0x55C4
                                );

                                out.writeShort(0);
                            }
                    );

            byte[] hdlr =
                    fullBox(
                            "hdlr",
                            0,
                            0,
                            out -> {
                                out.writeInt(0);

                                out.writeBytes(
                                        "vide"
                                );

                                out.writeInt(0);
                                out.writeInt(0);
                                out.writeInt(0);

                                out.writeBytes(
                                        "VideoHandler"
                                );

                                out.writeByte(0);
                            }
                    );

            byte[] minf =
                    createMinf(
                            width,
                            height,
                            frameCount,
                            sampleSizes,
                            chunkOffset
                    );

            return box(
                    "mdia",
                    concat(
                            mdhd,
                            hdlr,
                            minf
                    )
            );
        }

        private static byte[] createMinf(
                int width,
                int height,
                int frameCount,
                int[] sampleSizes,
                long chunkOffset
        ) throws IOException {

            byte[] vmhd =
                    fullBox(
                            "vmhd",
                            0,
                            1,
                            out -> {
                                out.writeShort(0);

                                out.writeShort(0);
                                out.writeShort(0);
                                out.writeShort(0);
                            }
                    );

            byte[] url =
                    fullBox(
                            "url ",
                            0,
                            1,
                            out -> {
                            }
                    );

            byte[] dref =
                    fullBox(
                            "dref",
                            0,
                            0,
                            out -> {
                                out.writeInt(1);
                                out.write(url);
                            }
                    );

            byte[] dinf =
                    box(
                            "dinf",
                            dref
                    );

            byte[] stbl =
                    createStbl(
                            width,
                            height,
                            frameCount,
                            sampleSizes,
                            chunkOffset
                    );

            return box(
                    "minf",
                    concat(
                            vmhd,
                            dinf,
                            stbl
                    )
            );
        }

        private static byte[] createStbl(
                int width,
                int height,
                int frameCount,
                int[] sampleSizes,
                long chunkOffset
        ) throws IOException {

            byte[] jpegSampleEntry =
                    createJpegSampleEntry(
                            width,
                            height
                    );

            byte[] stsd =
                    fullBox(
                            "stsd",
                            0,
                            0,
                            out -> {
                                out.writeInt(1);
                                out.write(
                                        jpegSampleEntry
                                );
                            }
                    );

            byte[] stts =
                    fullBox(
                            "stts",
                            0,
                            0,
                            out -> {
                                out.writeInt(1);

                                out.writeInt(
                                        frameCount
                                );

                                // 1 tick per frame.
                                out.writeInt(1);
                            }
                    );

            byte[] stsc =
                    fullBox(
                            "stsc",
                            0,
                            0,
                            out -> {
                                out.writeInt(1);

                                // first_chunk
                                out.writeInt(1);

                                // all samples live in one mdat chunk
                                out.writeInt(
                                        frameCount
                                );

                                // sample_description_index
                                out.writeInt(1);
                            }
                    );

            byte[] stsz =
                    fullBox(
                            "stsz",
                            0,
                            0,
                            out -> {
                                // Variable sample sizes.
                                out.writeInt(0);

                                out.writeInt(
                                        frameCount
                                );

                                for (
                                        int sampleSize :
                                                sampleSizes
                                ) {
                                    out.writeInt(
                                            sampleSize
                                    );
                                }
                            }
                    );

            byte[] stco =
                    fullBox(
                            "stco",
                            0,
                            0,
                            out -> {
                                out.writeInt(1);

                                out.writeInt(
                                        (int)
                                                chunkOffset
                                );
                            }
                    );

            return box(
                    "stbl",
                    concat(
                            stsd,
                            stts,
                            stsc,
                            stsz,
                            stco
                    )
            );
        }

        private static byte[] createJpegSampleEntry(
                int width,
                int height
        ) throws IOException {

            return box(
                    "jpeg",
                    out -> {
                        // SampleEntry reserved[6]
                        out.write(
                                new byte[6]
                        );

                        // data_reference_index
                        out.writeShort(1);

                        // VisualSampleEntry fields
                        out.writeShort(0);
                        out.writeShort(0);

                        // vendor
                        out.writeBytes(
                                "java"
                        );

                        // temporal_quality / spatial_quality
                        out.writeInt(0);
                        out.writeInt(0);

                        out.writeShort(
                                width
                        );

                        out.writeShort(
                                height
                        );

                        // 72 dpi
                        out.writeInt(
                                0x00480000
                        );

                        out.writeInt(
                                0x00480000
                        );

                        out.writeInt(0);

                        // frame_count
                        out.writeShort(1);

                        byte[] compressorName =
                                new byte[32];

                        byte[] name =
                                "Photo - JPEG"
                                        .getBytes(
                                                java.nio.charset.StandardCharsets.US_ASCII
                                        );

                        int nameLength =
                                Math.min(
                                        31,
                                        name.length
                                );

                        compressorName[0] =
                                (byte)
                                        nameLength;

                        System.arraycopy(
                                name,
                                0,
                                compressorName,
                                1,
                                nameLength
                        );

                        out.write(
                                compressorName
                        );

                        // depth
                        out.writeShort(24);

                        // pre_defined = -1
                        out.writeShort(
                                0xFFFF
                        );
                    }
            );
        }

        private static void writeMatrix(
                DataOutputStream out
        ) throws IOException {

            out.writeInt(
                    0x00010000
            );

            out.writeInt(0);
            out.writeInt(0);

            out.writeInt(0);

            out.writeInt(
                    0x00010000
            );

            out.writeInt(0);

            out.writeInt(0);
            out.writeInt(0);

            out.writeInt(
                    0x40000000
            );
        }

        private static byte[] fullBox(
                String type,
                int version,
                int flags,
                PayloadWriter payload
        ) throws IOException {

            return box(
                    type,
                    out -> {
                        out.writeByte(
                                version
                        );

                        out.writeByte(
                                (flags >>> 16)
                                & 0xFF
                        );

                        out.writeByte(
                                (flags >>> 8)
                                & 0xFF
                        );

                        out.writeByte(
                                flags
                                & 0xFF
                        );

                        payload.write(out);
                    }
            );
        }

        private static byte[] box(
                String type,
                PayloadWriter payload
        ) throws IOException {

            ByteArrayOutputStream payloadBytes =
                    new ByteArrayOutputStream();

            try (
                    DataOutputStream payloadOut =
                            new DataOutputStream(
                                    payloadBytes
                            )
            ) {
                payload.write(
                        payloadOut
                );

                payloadOut.flush();
            }

            return box(
                    type,
                    payloadBytes.toByteArray()
            );
        }

        private static byte[] box(
                String type,
                byte[] payload
        ) throws IOException {

            ByteArrayOutputStream bytes =
                    new ByteArrayOutputStream();

            try (
                    DataOutputStream out =
                            new DataOutputStream(
                                    bytes
                            )
            ) {
                out.writeInt(
                        payload.length + 8
                );

                out.writeBytes(
                        type
                );

                out.write(
                        payload
                );

                out.flush();
            }

            return bytes.toByteArray();
        }

        private static byte[] concat(
                byte[]... arrays
        ) throws IOException {

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            for (byte[] array : arrays) {
                output.write(array);
            }

            return output.toByteArray();
        }
    }

    private static void exportMp4(
            Component parent,
            ParticleTextPanel particlePanel,
            JButton triggerButton
    ) {
        File output =
                chooseExportFile(
                        parent,
                        "mp4"
                );

        if (output == null) {
            return;
        }

        ParticleTextPanel.ExportData exportData =
                particlePanel.createExportData();

        // MP4 экспортируем в 120 FPS.
        // В отличие от GIF здесь нет ограничения в 10 мс на кадр.
        final int fps = 120;
        final int durationSeconds = 4;

        triggerButton.setEnabled(false);

        triggerButton.setText(
                "Экспорт..."
        );

        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground()
                    throws Exception {

                MjpegMp4Writer.write(
                        output,
                        exportData,
                        fps,
                        durationSeconds
                );

                return null;
            }

            @Override
            protected void done() {
                triggerButton.setEnabled(true);

                triggerButton.setText(
                        "Сохранить MP4"
                );

                try {
                    get();

                    ModernNoticeDialog.show(
                            parent,
                            "Сохранено",
                            output.getAbsolutePath(),
                            false
                    );

                } catch (Exception e) {

                    Throwable cause =
                            e.getCause() != null
                                    ? e.getCause()
                                    : e;

                    ModernNoticeDialog.show(
                            parent,
                            "Ошибка экспорта",
                            "Не удалось сохранить MP4:\n"
                            + cause.getMessage(),
                            true
                    );
                }
            }
        }.execute();
    }

    // ============================================================
    // MAIN
    // ============================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            JFrame frame = new JFrame();

            frame.setUndecorated(true);

            frame.setDefaultCloseOperation(
                    JFrame.EXIT_ON_CLOSE
            );

            // Новая версия намеренно шире:
            // справа всегда должна быть видна панель настроек.
            frame.setSize(
                    1380,
                    820
            );

            frame.setMinimumSize(
                    new Dimension(
                            1050,
                            650
                    )
            );

            frame.setLocationRelativeTo(null);

            // ---------- Само окно ----------
            JPanel window =
                    new JPanel(
                            new BorderLayout()
                    );

            window.setBackground(
                    DEFAULT_BACKGROUND
            );

            window.setBorder(
                    BorderFactory.createLineBorder(
                            new Color(
                                    48,
                                    50,
                                    54
                            ),
                            1
                    )
            );

            JPanel titleBar =
                    createTitleBar(
                            frame,
                            DEFAULT_BACKGROUND
                    );

            // ---------- Основной контейнер ----------
            JPanel root =
                    new JPanel(
                            new BorderLayout()
                    );

            root.setBackground(
                    DEFAULT_BACKGROUND
            );

            root.setBorder(
                    new EmptyBorder(
                            30,
                            40,
                            34,
                            40
                    )
            );

            // ---------- Заголовок ----------
            JPanel header =
                    new JPanel();

            header.setOpaque(false);

            header.setLayout(
                    new BoxLayout(
                            header,
                            BoxLayout.Y_AXIS
                    )
            );

            JLabel mainTitle =
                    label(
                            "Типографика частиц",
                            28,
                            Font.BOLD,
                            MAIN_TEXT
                    );

            JLabel subtitle =
                    label(
                            "Введите текст ниже — он будет собран из множества частиц",
                            15,
                            Font.PLAIN,
                            SECONDARY_TEXT
                    );

            header.add(mainTitle);
            header.add(
                    Box.createVerticalStrut(7)
            );
            header.add(subtitle);

            // ---------- Левая карточка ----------
            ParticleTextPanel particlePanel =
                    new ParticleTextPanel();

            RoundedTextField textField =
                    new RoundedTextField(
                            "Hello world"
                    );

            RoundedPanel particleCard =
                    new RoundedPanel(
                            24,
                            CARD
                    );

            particleCard.setLayout(
                    new BorderLayout()
            );

            particleCard.setBorder(
                    new EmptyBorder(
                            16,
                            16,
                            16,
                            16
                    )
            );

            particleCard.add(
                    particlePanel,
                    BorderLayout.CENTER
            );

            JPanel inputWrapper =
                    new JPanel(
                            new BorderLayout()
                    );

            inputWrapper.setOpaque(false);

            inputWrapper.setBorder(
                    new EmptyBorder(
                            12,
                            0,
                            0,
                            0
                    )
            );

            inputWrapper.add(
                    textField,
                    BorderLayout.CENTER
            );

            particleCard.add(
                    inputWrapper,
                    BorderLayout.SOUTH
            );

            // ====================================================
            // ПРАВАЯ ПАНЕЛЬ НАСТРОЕК
            // ====================================================

            RoundedPanel settingsCard =
                    new RoundedPanel(
                            24,
                            CARD
                    );

            settingsCard.setPreferredSize(
                    new Dimension(
                            340,
                            0
                    )
            );

            settingsCard.setMinimumSize(
                    new Dimension(
                            320,
                            0
                    )
            );

            settingsCard.setLayout(
                    new BorderLayout()
            );

            settingsCard.setBorder(
                    new EmptyBorder(
                            20,
                            22,
                            18,
                            22
                    )
            );

            JPanel settings =
                    new JPanel();

            settings.setOpaque(false);

            settings.setLayout(
                    new BoxLayout(
                            settings,
                            BoxLayout.Y_AXIS
                    )
            );

            JLabel settingsTitle =
                    label(
                            "Настройки",
                            20,
                            Font.BOLD,
                            MAIN_TEXT
                    );

            settingsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

            settings.add(settingsTitle);
            settings.add(
                    Box.createVerticalStrut(14)
            );

            // ---------- Цвет частиц ----------
            ColorButton particleColorButton =
                    new ColorButton(
                            DEFAULT_PARTICLE,
                            particlePanel::setParticleColor
                    );

            settings.add(
                    createColorRow(
                            "Цвет частиц",
                            particleColorButton
                    )
            );

            settings.add(
                    Box.createVerticalStrut(12)
            );

            // ---------- Цвет фона ----------
            ColorButton backgroundColorButton =
                    new ColorButton(
                            CARD,
                            particlePanel::setParticleBackgroundColor
                    );

            settings.add(
                    createColorRow(
                            "Фон частиц",
                            backgroundColorButton
                    )
            );

            settings.add(
                    Box.createVerticalStrut(18)
            );

            // ---------- Слайдеры ----------
            SliderSetting speedSetting =
                    new SliderSetting(
                            "Скорость плавания",
                            20,
                            300,
                            DEFAULT_SPEED,
                            "%",
                            value ->
                                    particlePanel
                                            .setSpeedMultiplier(
                                                    value / 100f
                                            )
                    );

            SliderSetting amplitudeSetting =
                    new SliderSetting(
                            "Амплитуда плавания",
                            0,
                            300,
                            DEFAULT_AMPLITUDE,
                            "%",
                            value ->
                                    particlePanel
                                            .setAmplitudeMultiplier(
                                                    value / 100f
                                            )
                    );

            SliderSetting forceSetting =
                    new SliderSetting(
                            "Сила отталкивания",
                            500,
                            6000,
                            DEFAULT_MOUSE_FORCE,
                            "",
                            particlePanel::setMouseForce
                    );

            SliderSetting radiusSetting =
                    new SliderSetting(
                            "Радиус курсора",
                            40,
                            220,
                            DEFAULT_MOUSE_RADIUS,
                            " px",
                            particlePanel::setMouseRadius
                    );

            SliderSetting springSetting =
                    new SliderSetting(
                            "Скорость возврата",
                            10,
                            90,
                            DEFAULT_SPRING,
                            "",
                            particlePanel::setSpringStrength
                    );

            SliderSetting spacingSetting =
                    new SliderSetting(
                            "Расстояние частиц",
                            4,
                            14,
                            DEFAULT_SPACING,
                            " px",
                            particlePanel::setParticleStep
                    );

            SliderSetting sizeSetting =
                    new SliderSetting(
                            "Размер частиц",
                            1,
                            5,
                            DEFAULT_PARTICLE_SIZE,
                            " px",
                            particlePanel::setBaseParticleSize
                    );

            SliderSetting[] sliders = {
                    speedSetting,
                    amplitudeSetting,
                    forceSetting,
                    radiusSetting,
                    springSetting,
                    spacingSetting,
                    sizeSetting
            };

            for (SliderSetting slider : sliders) {
                settings.add(slider);
                settings.add(
                        Box.createVerticalStrut(7)
                );
            }

            // ---------- Кнопка сброса физики ----------
            JButton resetPhysics =
                    createActionButton(
                            "Вернуть частицы в текст"
                    );

            resetPhysics.addActionListener(
                    e -> particlePanel.resetPhysics()
            );

            // ---------- Сброс всех настроек ----------
            JButton resetAll =
                    createActionButton(
                            "Сбросить настройки"
                    );

            resetAll.addActionListener(e -> {

                particleColorButton.setSelectedColor(
                        DEFAULT_PARTICLE
                );

                particlePanel.setParticleColor(
                        DEFAULT_PARTICLE
                );

                backgroundColorButton.setSelectedColor(
                        CARD
                );

                particlePanel.setParticleBackgroundColor(
                        CARD
                );

                speedSetting.slider.setValue(
                        DEFAULT_SPEED
                );

                amplitudeSetting.slider.setValue(
                        DEFAULT_AMPLITUDE
                );

                forceSetting.slider.setValue(
                        DEFAULT_MOUSE_FORCE
                );

                radiusSetting.slider.setValue(
                        DEFAULT_MOUSE_RADIUS
                );

                springSetting.slider.setValue(
                        DEFAULT_SPRING
                );

                spacingSetting.slider.setValue(
                        DEFAULT_SPACING
                );

                sizeSetting.slider.setValue(
                        DEFAULT_PARTICLE_SIZE
                );

                particlePanel.resetPhysics();

                window.repaint();
                root.repaint();
                titleBar.repaint();
            });

            JButton exportGifButton =
                    createActionButton(
                            "Сохранить GIF"
                    );

            JButton exportMp4Button =
                    createActionButton(
                            "Сохранить MP4"
                    );

            exportGifButton.addActionListener(
                    e -> exportGif(
                            frame,
                            particlePanel,
                            exportGifButton
                    )
            );

            exportMp4Button.addActionListener(
                    e -> exportMp4(
                            frame,
                            particlePanel,
                            exportMp4Button
                    )
            );

            JPanel actionButtons =
                    new JPanel(
                            new GridLayout(
                                    2,
                                    2,
                                    8,
                                    8
                            )
                    );

            actionButtons.setOpaque(false);
            actionButtons.add(exportGifButton);
            actionButtons.add(exportMp4Button);
            actionButtons.add(resetPhysics);
            actionButtons.add(resetAll);

            // Без прокрутки: настройки компактно размещаются целиком.
            JPanel bottomActions =
                    new JPanel(
                            new BorderLayout()
                    );

            bottomActions.setOpaque(false);

            bottomActions.setBorder(
                    new EmptyBorder(
                            12,
                            0,
                            0,
                            0
                    )
            );

            bottomActions.add(
                    actionButtons,
                    BorderLayout.CENTER
            );

            settingsCard.add(
                    settings,
                    BorderLayout.CENTER
            );

            settingsCard.add(
                    bottomActions,
                    BorderLayout.SOUTH
            );

            // ---------- Рабочая зона ----------
            JPanel workspace =
                    new JPanel(
                            new BorderLayout(
                                    18,
                                    0
                            )
                    );

            workspace.setOpaque(false);

            workspace.add(
                    particleCard,
                    BorderLayout.CENTER
            );

            workspace.add(
                    settingsCard,
                    BorderLayout.EAST
            );

            JPanel content =
                    new JPanel(
                            new BorderLayout()
                    );

            content.setOpaque(false);

            content.setBorder(
                    new EmptyBorder(
                            22,
                            0,
                            0,
                            0
                    )
            );

            content.add(
                    workspace,
                    BorderLayout.CENTER
            );

            root.add(
                    header,
                    BorderLayout.NORTH
            );

            root.add(
                    content,
                    BorderLayout.CENTER
            );

            // ---------- Синхронизация текста ----------
            textField
                    .getDocument()
                    .addDocumentListener(
                            new DocumentListener() {

                                private void sync() {
                                    particlePanel.setText(
                                            textField.getText()
                                    );
                                }

                                @Override
                                public void insertUpdate(
                                        DocumentEvent e
                                ) {
                                    sync();
                                }

                                @Override
                                public void removeUpdate(
                                        DocumentEvent e
                                ) {
                                    sync();
                                }

                                @Override
                                public void changedUpdate(
                                        DocumentEvent e
                                ) {
                                    sync();
                                }
                            }
                    );

            window.add(
                    titleBar,
                    BorderLayout.NORTH
            );

            window.add(
                    root,
                    BorderLayout.CENTER
            );

            frame.setContentPane(window);
            frame.setVisible(true);
        });
    }
}
