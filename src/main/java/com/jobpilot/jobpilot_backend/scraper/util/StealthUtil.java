package com.jobpilot.jobpilot_backend.scraper.util;

import com.microsoft.playwright.*;

import java.util.Random;

public class StealthUtil {

    private static final Random RANDOM = new Random();

    public static void applyStealth(BrowserContext context) {
        context.addInitScript(
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
                        "window.chrome = { runtime: {} };" +
                        "Object.defineProperty(navigator, 'languages', {get: () => ['en-US','en']});" +
                        "Object.defineProperty(navigator, 'plugins', {get: () => [1,2,3,4,5]});"
        );
    }

    public static void humanDelay(int min, int max) {
        try {
            Thread.sleep(min + RANDOM.nextInt(max - min));
        } catch (InterruptedException ignored) {}
    }

    public static void slowScroll(Page page) {
        for (int i = 0; i < 6; i++) {
            page.mouse().wheel(0, 2000);
            humanDelay(1000, 2500);
        }
    }
}
