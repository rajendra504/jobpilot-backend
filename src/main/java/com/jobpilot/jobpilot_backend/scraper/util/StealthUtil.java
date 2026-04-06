package com.jobpilot.jobpilot_backend.scraper.util;

import com.microsoft.playwright.*;
import java.util.Random;

public class StealthUtil {

    private static final Random RANDOM = new Random();

    public static void applyStealth(BrowserContext context) {
        context.addInitScript(
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
                        "window.chrome = { runtime: {}, loadTimes: function(){}, csi: function(){}, app: {} };" +
                        "Object.defineProperty(navigator, 'languages', {get: () => ['en-US','en','hi']});" +
                        "Object.defineProperty(navigator, 'plugins', {get: () => [" +
                        "  {name:'Chrome PDF Plugin'},{name:'Chrome PDF Viewer'},{name:'Native Client'}" +
                        "]});" +
                        "Object.defineProperty(navigator, 'platform', {get: () => 'Win32'});" +
                        "Object.defineProperty(navigator, 'hardwareConcurrency', {get: () => 8});" +
                        "Object.defineProperty(navigator, 'deviceMemory', {get: () => 8});" +
                        "const origQuery = window.navigator.permissions.query;" +
                        "window.navigator.permissions.query = (p) => p.name === 'notifications' ? " +
                        "  Promise.resolve({ state: Notification.permission }) : origQuery(p);" +
                        "const getParam = WebGLRenderingContext.prototype.getParameter;" +
                        "WebGLRenderingContext.prototype.getParameter = function(p) {" +
                        "  if (p === 37445) return 'Intel Inc.';" +
                        "  if (p === 37446) return 'Intel Iris OpenGL Engine';" +
                        "  return getParam(p);" +
                        "};"
        );
    }

    public static void humanDelay(int min, int max) {
        try {
            Thread.sleep(min + RANDOM.nextInt(Math.max(1, max - min)));
        } catch (InterruptedException ignored) {}
    }

    public static void slowScroll(Page page) {
        try {
            for (int i = 0; i < 5; i++) {
                int amount = 300 + RANDOM.nextInt(400);
                page.mouse().wheel(0, amount);
                humanDelay(600, 1500);
            }
        } catch (Exception ignored) {}
    }

    public static void randomMouseMove(Page page) {
        try {
            int x = 200 + RANDOM.nextInt(800);
            int y = 200 + RANDOM.nextInt(400);
            page.mouse().move(x, y);
            humanDelay(200, 500);
        } catch (Exception ignored) {}
    }
}