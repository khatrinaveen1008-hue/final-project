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

	public class NameEnter {

	    public static void main(String[] args) throws Exception {

	        // 🔹 GOOGLE SHEET SETUP
	        String spreadsheetId = "1nwkXWvMoX969eK7SWonDlK3aP5rqGHzm5KcRuQ_cqHE"; // 👈 apni sheet ID
	        String range = "Sheet3!A1"; // 👈 sheet name

	        GoogleCredential credential = GoogleCredential
	                .fromStream(new FileInputStream("pondsdata-9b4461d3270d.json"))
	                .createScoped(Collections.singleton("https://www.googleapis.com/auth/spreadsheets"));

	        Sheets service = new Sheets.Builder(
	                GoogleNetHttpTransport.newTrustedTransport(),
	                JacksonFactory.getDefaultInstance(),
	                credential
	        ).setApplicationName("Pond Data").build();

	        List<List<Object>> sheetData = new ArrayList<>();

	        // 🔹 PLAYWRIGHT START
	        try (Playwright playwright = Playwright.create()) {

	            Browser browser = playwright.chromium().launch(
	                    new BrowserType.LaunchOptions().setHeadless(false)
	            );

	            Page page = browser.newPage();

	            // 🔹 OPEN SITE
	            page.navigate("https://systems.hid.gov.in/MIS/");
	            page.waitForTimeout(3000);

	            // 🔹 LOGIN
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

	            System.out.println("Login Successful");

	            // 🔹 DIRECT PAGE
	            page.navigate("https://systems.hid.gov.in/twp/UI/AddPondStatusDaily.aspx");
	            page.waitForTimeout(5000);

	            // 🔹 DROPDOWN SELECT
	            page.selectOption("#ContentPlaceHolder1_ddlUnit", "LCU");
	            page.selectOption("#ContentPlaceHolder1_ddlCircle",
	                    new SelectOption().setLabel("Yamuna Water Service Circle, Rohtak"));
	            page.selectOption("#ContentPlaceHolder1_ddldivision", "D0039");

	            page.waitForSelector("#ContentPlaceHolder1_gvList");

	            System.out.println("Table Loaded");

	            // 🔹 TABLE ROWS
	            Locator rows = page.locator("#ContentPlaceHolder1_gvList tr");
	            int count = rows.count();

	            System.out.println("Total Rows: " + count);

	            // Header add (optional)
	            sheetData.add(Arrays.asList("Pond Name"));

	            for (int i = 1; i < count; i++) {

	                try {
	                    String pondName = rows.nth(i).locator("td").nth(1).innerText().trim();

	                    System.out.println("Pond: " + pondName);

	                    sheetData.add(Arrays.asList(pondName));

	                } catch (Exception e) {
	                    System.out.println("Error reading row: " + i);
	                }
	            }
	        }

	        // 🔹 WRITE TO GOOGLE SHEET
	        ValueRange body = new ValueRange().setValues(sheetData);

	        service.spreadsheets().values()
	                .update(spreadsheetId, range, body)
	                .setValueInputOption("RAW")
	                .execute();

	        System.out.println("✅ Data Uploaded to Google Sheet");
	    }
	}


