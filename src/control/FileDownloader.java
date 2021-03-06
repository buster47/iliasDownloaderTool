package control;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import model.IliasPdf;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.protocol.BasicHttpContext;

import view.Dashboard;

public class FileDownloader implements Runnable {
	private HttpGet request;
	private HttpResponse response;
	private BasicHttpContext context;
	private HttpEntity entity;
	private final IliasPdf pdf;
	private String targetPath;
	private final String type;
	private String name;

	public FileDownloader(IliasPdf pdf, String type) {
		this.pdf = pdf;
		this.targetPath = LocalPdfStorage.getInstance().suggestDownloadPath(pdf);
		this.type = type;
	}

	@Override
	public void run() {
		name = pdf.getName().replace(":", " - ").replace("/", "+");
		askForStoragePosition();
	}

	private void askForStoragePosition() {
		final FileChooser fileChooser = new FileChooser();
		fileChooser.setInitialDirectory(new File(targetPath));
		fileChooser.setInitialFileName(name + type);
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				final File selectedFile = fileChooser.showSaveDialog(new Stage());
				if (selectedFile != null) {
					targetPath = selectedFile.getAbsolutePath();
					if (!targetPath.endsWith(".pdf")) {
						targetPath = targetPath + ".pdf";
					}
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							download();
						}
					});
				}
			}
		});
	}

	private void download() {
		try {
			request = new HttpGet(pdf.getUrl());

			response = Ilias.getClient().execute(request, context);
			entity = response.getEntity();

			BufferedInputStream in = new BufferedInputStream(entity.getContent());
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(new File(targetPath)));

			int inByte;
			while ((inByte = in.read()) != -1) {
				out.write(inByte);
			}

			in.close();
			out.close();

			request.releaseConnection();
		} catch (IOException e) {
			e.printStackTrace();
		}
		LocalPdfStorage.getInstance().addPdf(pdf, targetPath);
		Dashboard.fileDownloaded(pdf);
	}
}
