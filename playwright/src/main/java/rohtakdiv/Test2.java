package rohtakdiv;



	

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.SelectOption;

import java.util.*;
import java.io.*;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.sheets.v4.*;
import com.google.api.services.sheets.v4.model.*;

public class Test2 {

    public static void main(String[] args) throws Exception {

        String spreadsheetId = "1nwkXWvMoX969eK7SWonDlK3aP5rqGHzm5KcRuQ_cqHE";
        String range = "Sheet4!A2:C1000";

        GoogleCredential credential = GoogleCredential
                .fromStream(new FileInputStream("pondsdata-9b4461d3270d.json"))
                .createScoped(Collections.singleton("https://www.googleapis.com/auth/spreadsheets"));

        Sheets service = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(),
                credential
        ).setApplicationName("Pond Data").build();

        ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

        List<List<Object>> sheetRows = response.getValues();

        if (sheetRows == null || sheetRows.isEmpty()) {
            System.out.println("❌ Sheet mein koi data nahi mila!");
            return;
        }

        Map<String, String[]> sheetData = new LinkedHashMap<>();
        for (List<Object> row : sheetRows) {
            String pondName   = row.size() >= 1 ? row.get(0).toString().trim() : "";
            String waterLevel = row.size() >= 2 ? row.get(1).toString().trim() : "";
            String remarks    = row.size() >= 3 ? row.get(2).toString().trim() : "";
            if (!pondName.isEmpty()) {
                sheetData.put(pondName, new String[]{waterLevel, remarks});
            }
        }

        System.out.println("✅ Sheet se " + sheetData.size() + " records padhe");
        System.out.println("==========================================");

        try (Playwright playwright = Playwright.create()) {

            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(false)
            );

            Page page = browser.newPage();

            page.navigate("https://systems.hid.gov.in/MIS/");
            page.waitForTimeout(3000);

            try {
                page.frameLocator("iframe").locator("#txtuser").fill("9416495817");
                page.frameLocator("iframe").locator("#txtpass").fill("123456");
                page.frameLocator("iframe").locator("#btnLogin").click();
            } catch (Exception e) {
                page.fill("#txtuser", "9416495817");
                page.fill("#txtpass", "123456");
                page.click("#btnLogin");
            }

            page.waitForTimeout(5000);
            page.keyboard().press("Escape");
            System.out.println("✅ Login Successful");
            page.locator("img[src='/MIS/Content/assets/images/WaterMonitoringNew.jpg']").click();
            page.waitForTimeout(2000);

            page.navigate("https://systems.hid.gov.in/twp/UI/AddPondStatusDaily.aspx");
            page.waitForTimeout(5000);

            page.selectOption("#ContentPlaceHolder1_ddlUnit", "LCU");
            page.selectOption("#ContentPlaceHolder1_ddlCircle",
                    new SelectOption().setLabel("Yamuna Water Service Circle, Rohtak"));
            page.selectOption("#ContentPlaceHolder1_ddldivision", "D0039");

            page.waitForSelector("#ContentPlaceHolder1_gvList");
            System.out.println("✅ Table Loaded");

            int updatedCount  = 0;
            int skippedCount  = 0;
            int notFoundCount = 0;

            for (Map.Entry<String, String[]> entry : sheetData.entrySet()) {

                String sheetPondName = entry.getKey();
                String waterLevel    = entry.getValue()[0];
                String remarks       = entry.getValue()[1];

                System.out.println("\n🔍 Dhundh raha hoon: " + sheetPondName);

                try {
                    page.waitForSelector("#ContentPlaceHolder1_gvList");
                    Locator rows = page.locator("#ContentPlaceHolder1_gvList tr");
                    int count = rows.count();

                    int matchedRowIndex  = -1;
                    String matchedSiteName = null;

                    // ==============================
                    // 🔹 TABLE SCAN - NAAM SE MATCH KARO
                    // ==============================
                    for (int i = 1; i < count; i++) {
                        try {
                            String fullText = rows.nth(i).locator("td").nth(1).innerText().trim();
                            String sitePondName = "";

                            for (String line : fullText.split("\n")) {
                                line = line.trim();
                                if (line.startsWith("Pond Name :")) {
                                    sitePondName = line.replace("Pond Name :", "").trim();
                                    break;
                                }
                            }

                            if (!sitePondName.isEmpty() &&
                                (sitePondName.toLowerCase().contains(sheetPondName.toLowerCase()) ||
                                 sheetPondName.toLowerCase().contains(sitePondName.toLowerCase()))) {

                                matchedRowIndex  = i - 1;
                                matchedSiteName  = sitePondName;
                                break;
                            }

                        } catch (Exception ignored) {}
                    }

                    if (matchedRowIndex == -1) {
                        System.out.println("   ⚠️ Match nahi mila site pe!");
                        notFoundCount++;
                        continue;
                    }

                    System.out.println("   ✅ Match: " + matchedSiteName + " | Index: " + matchedRowIndex);

                    // ==============================
                    // 🔹 KEY FIX: DROPDOWN EXIST KARTA HAI?
                    // Agar nahi karta = already updated, skip karo
                    // ==============================
                    String dropdownId  = "#ContentPlaceHolder1_gvList_ddlStatus_"  + matchedRowIndex;
                    String remarksId   = "#ContentPlaceHolder1_gvList_txtReason_"  + matchedRowIndex;
                    String updateBtnId = "#ContentPlaceHolder1_gvList_btn_Update_" + matchedRowIndex;

                    boolean dropdownExists = page.locator(dropdownId).count() > 0;

                    if (!dropdownExists) {
                        System.out.println("   ⏭️ Already updated hai, skip kar raha hoon!");
                        skippedCount++;
                        continue;
                    }

                    // ==============================
                    // 🔹 WATER LEVEL SET KARO
                    // ==============================
                    if (!waterLevel.isEmpty()) {
                        page.selectOption(dropdownId, new SelectOption().setLabel(waterLevel));
                        page.waitForTimeout(500);
                        System.out.println("   💧 Water Level: " + waterLevel);
                    }

                    // ==============================
                    // 🔹 REMARKS SET KARO
                    // ==============================
                    if (!remarks.isEmpty()) {
                        page.fill(remarksId, "");
                        page.fill(remarksId, remarks);
                        page.waitForTimeout(500);
                        System.out.println("   📝 Remarks: " + remarks);
                    }

                    // ==============================
                    // 🔹 UPDATE CLICK KARO
                    // ==============================
                    page.click(updateBtnId);
                    System.out.println("   🖱️ Update Clicked...");

                    page.waitForTimeout(3000);
                    page.waitForSelector("#ContentPlaceHolder1_gvList");

                    System.out.println("   💾 Updated Successfully!");
                    updatedCount++;

                } catch (Exception e) {
                    System.out.println("   ❌ Error: " + e.getMessage());
                }
            }

            System.out.println("\n==========================================");
            System.out.println("✅ Updated  : " + updatedCount);
            System.out.println("⏭️ Skipped  : " + skippedCount);
            System.out.println("⚠️ Not Found: " + notFoundCount);
            System.out.println("==========================================");
        }
    }
}




	



