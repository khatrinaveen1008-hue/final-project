package base;



//✅ Specific imports - wildcard * mat use karo
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;

import net.sourceforge.tess4j.Tesseract;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.List;          // ✅ Sirf java.util.List
import java.util.Base64;

public class LoginUtil {

    private static final String SPREADSHEET_ID = "1cHDoLgxSuq9SL5FxO1N5HMHO62Abf9YCzCpKjstI9Rw";
    private static final String RANGE          = "Sheet1!A2:B2";

    // ==============================
    // 🔹 Google Sheet se credentials
    // ==============================
    public static String[] getCredentials() throws Exception {
        GoogleCredential credential = GoogleCredential
                .fromStream(new FileInputStream("pondsdata-58d9eaf0ae83.json"))
                .createScoped(Collections.singleton("https://www.googleapis.com/auth/spreadsheets"));

        Sheets service = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(),
                credential
        ).setApplicationName("Login").build();

        ValueRange response = service.spreadsheets().values()
                .get(SPREADSHEET_ID, RANGE)
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
            throw new RuntimeException("❌ Login data nahi mila!");
        }

        return new String[]{
            values.get(0).get(0).toString(),
            values.get(0).get(1).toString()
        };
    }

    // ==============================
    // 🔹 Image ko clean karo
    //    (OCR better padhe isliye)
    // ==============================
    private static BufferedImage preprocessImage(BufferedImage original) {
        // Step 1: Image badi karo (3x zoom)
        int newWidth  = original.getWidth()  * 3;
        int newHeight = original.getHeight() * 3;
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        // Step 2: Grayscale + Contrast badhaao
        BufferedImage processed = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                Color color = new Color(scaled.getRGB(x, y));
                int gray = (color.getRed() + color.getGreen() + color.getBlue()) / 3;

                // Black/White threshold - noise hatao
                int bw = gray > 128 ? 255 : 0;
                processed.setRGB(x, y, new Color(bw, bw, bw).getRGB());
            }
        }
        return processed;
    }

    // ==============================
    // 🔹 Tesseract se captcha padho
    // ==============================
    private static String solveCaptchaOCR(Page page) throws Exception {
        // Captcha screenshot lo
        Locator captchaImg;
        try {
            captchaImg = page.frameLocator("iframe")
                    .locator("img[src*='CaptchaImage.axd']");
            captchaImg.waitFor(new Locator.WaitForOptions().setTimeout(5000));
        } catch (Exception e) {
            captchaImg = page.locator("img[src*='CaptchaImage.axd']");
        }

        byte[] captchaBytes = captchaImg.screenshot();
        System.out.println("📸 Captcha screenshot liya!");

        // Bytes → BufferedImage
        BufferedImage originalImg = ImageIO.read(new ByteArrayInputStream(captchaBytes));

        // Image preprocess karo
        BufferedImage cleanImg = preprocessImage(originalImg);

        // Temp file save karo
        File tempFile = File.createTempFile("captcha_", ".png");
        ImageIO.write(cleanImg, "PNG", tempFile);

        // Tesseract setup
        Tesseract tesseract = new Tesseract();

        // ✅ Eclipse ke liye - tessdata folder path
        // GitHub Actions ke liye env se lega
        String tessDataPath = System.getenv("TESSDATA_PREFIX") != null
                ? System.getenv("TESSDATA_PREFIX")
                : "C:/Program Files/Tesseract-OCR/tessdata"; // 👈 Apna path daalo

        tesseract.setDatapath(tessDataPath);
        tesseract.setLanguage("eng");

        // Sirf letters + numbers
        tesseract.setVariable("tessedit_char_whitelist",
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789");
        tesseract.setPageSegMode(8); // Single word mode

        // OCR run karo
        String result = tesseract.doOCR(tempFile);

        // Clean karo - spaces, newlines hatao
        result = result.replaceAll("[^a-zA-Z0-9]", "").trim();

        System.out.println("🔑 OCR Result: " + result);

        // Temp file delete karo
        tempFile.delete();

        return result;
    }

    // ==============================
    // 🔹 Main Login Method
    // ==============================
    public static void login(Page page) throws Exception {
        String[] creds  = getCredentials();
        String username = creds[0];
        String password = creds[1];

        boolean loginSuccess = false;
        int loginAttempts    = 0;

        while (!loginSuccess && loginAttempts < 5) {
            loginAttempts++;
            System.out.println("\n🔄 Login attempt: " + loginAttempts);

            try {
                page.navigate("https://systems.hid.gov.in/MIS/");
                page.waitForTimeout(3000);

                // Username + Password fill
                try {
                    page.frameLocator("iframe").locator("#txtuser").fill(username);
                    page.frameLocator("iframe").locator("#txtpass").fill(password);
                } catch (Exception e) {
                    page.fill("#txtuser", username);
                    page.fill("#txtpass", password);
                }

                page.waitForTimeout(1000);

                // ✅ Captcha OCR se solve karo
                String captchaText = solveCaptchaOCR(page);

                if (captchaText.isEmpty()) {
                    System.out.println("⚠️ OCR kuch nahi padh paya, retry...");
                    continue;
                }

                // Captcha fill karo
                try {
                    page.frameLocator("iframe").locator("#txtCaptcha").fill(captchaText);
                } catch (Exception e) {
                    page.fill("#txtCaptcha", captchaText);
                }

                page.waitForTimeout(500);

                // Login click
                try {
                    page.frameLocator("iframe").locator("#btnLogin").click();
                } catch (Exception e) {
                    page.click("#btnLogin");
                }

                page.waitForTimeout(4000);

                // Login check
                String currentUrl = page.url();
                System.out.println("   🌐 URL: " + currentUrl);

                if (!currentUrl.equals("https://systems.hid.gov.in/MIS/")) {
                    loginSuccess = true;
                    System.out.println("✅ Login Successful!");
                } else {
                    System.out.println("❌ Wrong captcha, retry...");
                    page.waitForTimeout(2000);
                }

            } catch (Exception e) {
                System.out.println("❌ Attempt " + loginAttempts + " error: " + e.getMessage());
                page.waitForTimeout(2000);
            }
        }

        if (!loginSuccess) {
            throw new RuntimeException("❌ 5 attempts ke baad bhi login fail!");
        }

        page.keyboard().press("Escape");
        page.waitForTimeout(1000);
        System.out.println("✅ Login Complete!");
    }
}