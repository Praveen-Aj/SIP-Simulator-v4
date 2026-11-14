package test;

import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * Phase 1A Display Bounds & Scaling Validation Test
 * Simulates display configurations and validates that:
 * 1. Initial window dimensions never exceed available screen bounds.
 * 2. Window height leaves clearance for the Windows taskbar.
 * 3. Ultrawide monitors do not excessively stretch the window on initial launch.
 * 4. High-DPI scaled displays retain valid minimum sizes and margins.
 * 5. Window bounds remain centered and within visible desktop coordinates.
 */
public class DisplayScalingValidationTest {

    public static class WindowCalculationResult {
        public final int minWidth;
        public final int minHeight;
        public final int targetWidth;
        public final int targetHeight;
        public final int targetX;
        public final int targetY;

        public WindowCalculationResult(Rectangle maxBounds) {
            this.minWidth = Math.min(960, Math.max(700, maxBounds.width - 40));
            this.minHeight = Math.min(620, Math.max(500, maxBounds.height - 40));

            int tw = (int) Math.round(maxBounds.width * 0.82);
            if (tw > 1380) {
                tw = 1380;
            }
            if (tw < this.minWidth) {
                tw = this.minWidth;
            }
            this.targetWidth = tw;

            int th = (int) Math.round(maxBounds.height * 0.88);
            if (th > 840) {
                th = 840;
            }
            if (th < this.minHeight) {
                th = this.minHeight;
            }
            this.targetHeight = th;

            this.targetX = maxBounds.x + (maxBounds.width - this.targetWidth) / 2;
            this.targetY = maxBounds.y + (maxBounds.height - this.targetHeight) / 2;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Phase 1A: Display Scaling & Bounds Validation ===");

        // Define the 6 target configurations (accounting for Windows taskbar insets)
        TestCase[] testCases = new TestCase[] {
            new TestCase("1366x768 @ 100% scaling", new Rectangle(0, 0, 1366, 728), 1366, 768),
            new TestCase("1920x1080 @ 100% scaling", new Rectangle(0, 0, 1920, 1032), 1920, 1080),
            new TestCase("1920x1080 @ 150% scaling (virtual 1280x688)", new Rectangle(0, 0, 1280, 688), 1280, 720),
            new TestCase("2560x1440 @ 125% scaling (virtual 2048x1112)", new Rectangle(0, 0, 2048, 1112), 2048, 1152),
            new TestCase("3440x1440 Ultrawide @ 100% scaling", new Rectangle(0, 0, 3440, 1392), 3440, 1440),
            new TestCase("3840x2160 @ 200% scaling (virtual 1920x1032)", new Rectangle(0, 0, 1920, 1032), 1920, 1080)
        };

        int passed = 0;
        for (TestCase tc : testCases) {
            WindowCalculationResult res = new WindowCalculationResult(tc.maxBounds);
            System.out.println("\n[PROFILE] " + tc.profileName);
            System.out.printf("  Usable Screen: %dx%d (Taskbar reserved: %dpx)\n",
                    tc.maxBounds.width, tc.maxBounds.height, tc.rawHeight - tc.maxBounds.height);
            System.out.printf("  Calculated Window: %dx%d at (%d, %d)\n",
                    res.targetWidth, res.targetHeight, res.targetX, res.targetY);
            System.out.printf("  Minimum Usable Size: %dx%d\n", res.minWidth, res.minHeight);

            // Assertions
            boolean fitsX = (res.targetX >= tc.maxBounds.x) && (res.targetX + res.targetWidth <= tc.maxBounds.x + tc.maxBounds.width);
            boolean fitsY = (res.targetY >= tc.maxBounds.y) && (res.targetY + res.targetHeight <= tc.maxBounds.y + tc.maxBounds.height);
            boolean aboveTaskbar = (res.targetY + res.targetHeight <= tc.maxBounds.height);
            boolean minValid = (res.minWidth <= tc.maxBounds.width) && (res.minHeight <= tc.maxBounds.height);
            boolean ultrawideNotStretched = res.targetWidth <= 1380;

            if (fitsX && fitsY && aboveTaskbar && minValid && ultrawideNotStretched) {
                System.out.println("  Result: PASS (Within screen bounds, taskbar cleared, no ultrawide overstretch)");
                passed++;
            } else {
                System.err.println("  Result: FAIL! Details: fitsX=" + fitsX + " fitsY=" + fitsY
                        + " aboveTaskbar=" + aboveTaskbar + " minValid=" + minValid);
            }
        }

        System.out.println("\n=================================================");
        System.out.printf("DISPLAY VALIDATION SUMMARY: %d / %d PROFILES PASSED\n", passed, testCases.length);
        System.out.println("=================================================");

        if (passed != testCases.length) {
            System.exit(1);
        }
    }

    private static class TestCase {
        final String profileName;
        final Rectangle maxBounds;
        final int rawWidth;
        final int rawHeight;

        TestCase(String profileName, Rectangle maxBounds, int rawWidth, int rawHeight) {
            this.profileName = profileName;
            this.maxBounds = maxBounds;
            this.rawWidth = rawWidth;
            this.rawHeight = rawHeight;
        }
    }
}
