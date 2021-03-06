package studportControl;

import java.util.List;

import javafx.scene.control.Button;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import view.Dashboard;
import view.StudportBar;

public class Studierendenportal implements Runnable {

	private final String loginUrl = "https://studium.kit.edu/_layouts/login.aspx?ReturnUrl=%2f";
	private String htmlStartpage = null;

	private String alleEn = null;
	private String alleDe = null;
	private String alleBestandenEn = null;
	private String alleBestandenDe = null;
	private String filename = null;

	private final DefaultHttpClient client;
	private final BasicHttpContext context;
	private final RedirectStrategy strategy;
	private HttpGet requestGET;
	private HttpPost requestPOST;
	private HttpResponse response;
	private HttpEntity entity;
	private List<NameValuePair> nameValuePairs;
	private final String username;
	private final String password;
	private final String choice;
	private final StudportBar studportBar;
	private final Button button;
	private final Dashboard dashboard;

	public Studierendenportal(Dashboard dashboard, String username, String password, Button button, StudportBar studportBar) {
		this.dashboard = dashboard;
		this.username = username;
		this.password = password;
		this.button = button;
		this.studportBar = studportBar;
		this.choice = button.getText();

		client = new DefaultHttpClient();
		strategy = new LaxRedirectStrategy();
		client.setRedirectStrategy(strategy);
		context = new BasicHttpContext();
	}

	@Override
	public void run() {
		final boolean login = login(username, password);
		if (login) {
			downloadNotenauszug(choice);
		}
	}

	public boolean login(String username, String password) {
		try {
			requestPOST = new HttpPost(loginUrl);

			nameValuePairs = new NameValuePairFactory().create(username, password);

			requestPOST.setEntity(new UrlEncodedFormEntity(nameValuePairs, Consts.UTF_8));

			response = client.execute(requestPOST, context);

			entity = response.getEntity();

			htmlStartpage = EntityUtils.toString(entity);

			if (htmlStartpage.contains("Sie sind nicht angemeldet")) {
				dashboard.setStatusText("Benutzerdaten falsch!", true);
				dashboard.fadeInLogin();
				stopDownload();
				return false;
			} else {
				openTargetWebsite();
			}
			requestPOST.releaseConnection();

		} catch (Exception e) {
			stopDownload();
			e.printStackTrace();
		}
		return true;
	}

	public void openTargetWebsite() {
		requestGet("https://studium.kit.edu/meinestudienakte/Seiten/BachelorMasterNotenauszug.aspx?$HIS$state=notenspiegelStudent&$HIS$struct=auswahlBaum&$HIS$navigation=Y&$HIS$next=tree.vm&$HIS$nextdir=qispos/notenspiegel/student&$HIS$nodeID=auswahlBaum%7Cabschluss%3Aabschl%3D82%2Cstgnr%3D1&$HIS$expand=0&$HIS$lastState=notenspiegelStudent#auswahlBaum%7Cabschluss%3Aabschl%3D82%2Cstgnr%3D1");
		String html3 = requestGet("https://studium.kit.edu/meinestudienakte/Seiten/BachelorMasterNotenauszug.aspx?$HIS$state=notenspiegelStudent&$HIS$next=list.vm&$HIS$nextdir=qispos/notenspiegel/student&$HIS$createInfos=Y&$HIS$struct=auswahlBaum&$HIS$nodeID=auswahlBaum%7Cabschluss%3Aabschl%3D82%2Cstgnr%3D1%7Cstudiengang%3Astg%3D179&$HIS$expand=0#auswahlBaum%7Cabschluss%3Aabschl%3D82%2Cstgnr%3D1%7Cstudiengang%3Astg%3D179");
		Document doc = Jsoup.parse(html3);
		List<Element> temp = doc.select("a[href]").select("[class=liste1]").select("a[href]");

		for (Element t : temp) {
			t.setBaseUri("https://studium.kit.edu/meinestudienakte/Seiten/BachelorMasterNotenauszug.aspx/");
			if (t.attr("abs:href").contains("studiengang&$HIS$lastState=notenspiegelStudent&$HIS$xslobject=de")) {
				alleDe = t.attr("abs:href");
			}
			if (t.attr("href").contains("studiengang&$HIS$lastState=notenspiegelStudent&$HIS$xslobject=en")) {
				alleEn = t.attr("abs:href");
			}
			if (t.attr("href").contains("studiengangbestanden&$HIS$lastState=notenspiegelStudent&$HIS$xslobject=de")) {
				alleBestandenDe = t.attr("abs:href");
			}
			if (t.attr("href").contains("studiengangbestanden&$HIS$lastState=notenspiegelStudent&$HIS$xslobject=en")) {
				alleBestandenEn = t.attr("abs:href");
			}
		}
	}

	public void downloadNotenauszug(String choice) {
		String downloadPdfUrl = null;
		switch (choice) {
		case Transcript.ALLE_LEISTUNGEN_DEUTSCH:
			filename = "Notenauszug-DE";
			downloadPdfUrl = alleDe;
			break;
		case Transcript.ALLE_LEISTUNGEN_ENGLISCH:
			filename = "Notenauszug-EN";
			downloadPdfUrl = alleEn;
			break;
		case Transcript.BESTANDEN_DEUTSCH:
			filename = "Notenauszug-BE-DE";
			downloadPdfUrl = alleBestandenDe;
			break;
		case Transcript.BESTANDEN_ENGLISCH:
			filename = "Notnauszug-BE-EN";
			downloadPdfUrl = alleBestandenEn;
			break;
		}

		new Thread(new TranscriptDownloader(dashboard, client, downloadPdfUrl, filename, this)).start();
	}

	public String requestGet(String url) {
		try {
			requestGET = new HttpGet(url);

			response = client.execute(requestGET, context);
			entity = response.getEntity();

			return EntityUtils.toString(entity);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			requestGET.releaseConnection();
		}
		return null;
	}

	void stopDownload() {
		studportBar.changeButtonstate(button, false);
	}
}
