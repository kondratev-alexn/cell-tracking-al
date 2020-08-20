package properties;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import cellTracking.Cell_Tracker;
import cellTracking.ImageFunctions;
import graph.MitosisInfo;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import inra.ijpb.morphology.Strel;
import inra.ijpb.math.ImageCalculator;
import inra.ijpb.morphology.Morphology.Operation;

public class Properties_Measure implements PlugIn {

	// final ImageJ ij = new ImageJ();

	public static void main(String[] args) {
		Locale.setDefault(Locale.US);
		Class<?> clazz = Properties_Measure.class;
		String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring("file:".length(),
				url.length() - clazz.getName().length() - ".class".length());
		System.setProperty("plugins.dir", pluginsDir);
		new ImageJ();

		IJ.runPlugIn(clazz.getName(), "Properties");
	}

	@Override
	public void run(String arg) {
		DirectoryChooser dirChoose = new DirectoryChooser("Select a folder with data (c1 and c2 tif images)");

		String dataDir;

		boolean selectDir = true;
		if (selectDir)
			dataDir = dirChoose.getDirectory();
		else
			dataDir = "G:\\Tokyo\\Data\\Properties Measure\\";
		if (dataDir == null)
			return;

		try {
			SimpleDirectoryScanner scanner = new SimpleDirectoryScanner();
			scanner.setDirectory(dataDir);

			String ch1_name = scanner.fileNameBySuffix("c1.tif");
			if (ch1_name.isEmpty())
				throw new Exception("No channel 1 tif image was found");
			String ch2_name = scanner.fileNameBySuffix("c2.tif");
			if (ch2_name.isEmpty())
				throw new Exception("No channel 2 tif image was found");

			// if you need behavior that asks tracking folder anyway, just comment lines below
			boolean askTrackingResultsFolder = false;
			String restif_name = scanner.fileNameBySuffix("results.tif");
			if (restif_name.isEmpty()) {
				IJ.log("No tracking result in CTC image format was found.");
				askTrackingResultsFolder = true;
			}
			String restxt_name = scanner.fileNameBySuffix("results.txt");
			if (restxt_name.isEmpty()) {
				IJ.log("No tracking result in CTC text format was found");
				askTrackingResultsFolder = true;
			}

			String mitosisInfoName = scanner.fileNameBySuffix("mitosis_info.ser");
			if (mitosisInfoName.isEmpty()) {
				IJ.log("No mitosis information file was found");
				askTrackingResultsFolder = true;
			}

			String[] split = ch2_name.split("c2");
			String name = split[0];
			System.out.println(name);

			ImagePlus imp_ch1 = new ImagePlus(dataDir + System.getProperty("file.separator") + ch1_name);
			ImagePlus imp_ch2 = new ImagePlus(dataDir + System.getProperty("file.separator") + ch2_name);

			String txtPath = dataDir + restxt_name;
			String tifPath = dataDir + restif_name;
			String infoFilePath = dataDir + mitosisInfoName;

			if (askTrackingResultsFolder) {
				/*
				 * if some of tracking results was not find with data, ask for tracking results
				 * directory, and launch the tracking plugin if cancelled or wrong directory is
				 * chosen
				 */

				DirectoryChooser dirChooseTrackingResults = new DirectoryChooser("Select a folder with tracking results");
				String trackingResultsDir = null;

				trackingResultsDir = dirChooseTrackingResults.getDirectory();
				scanner.setDirectory(trackingResultsDir);

				boolean launchTracking = false;
				if (trackingResultsDir == null) { // cancelled, launch
					launchTracking = true;
				}

				if (!launchTracking) {
					restif_name = scanner.fileNameBySuffix("results.tif");
					if (restif_name.isEmpty()) {
						IJ.log("No tracking result in CTC image format was found.");
						launchTracking = true;
					}
					restxt_name = scanner.fileNameBySuffix("results.txt");
					if (restxt_name.isEmpty()) {
						IJ.log("No tracking result in CTC text format was found");
						launchTracking = true;
					}

					mitosisInfoName = scanner.fileNameBySuffix("mitosis_info.ser");
					if (mitosisInfoName.isEmpty()) {
						IJ.log("No mitosis information file was found");
						launchTracking = true;
					}

					txtPath = trackingResultsDir + restxt_name;
					tifPath = trackingResultsDir + restif_name;
					infoFilePath = trackingResultsDir + mitosisInfoName;
				}
				if (launchTracking) {

					IJ.log("Launching tracking plugin");

					imp_ch2.show();
					Cell_Tracker o = (Cell_Tracker) IJ.runPlugIn(imp_ch2, Cell_Tracker.class.getName(), "no save");
					if (o == null) {
						IJ.log("Failed to run plugin");
						return;
					}

					txtPath = o.textResultsPath();
					tifPath = o.tifResultPath();
					infoFilePath = o.mitosisInfoFilePath();
					System.out.println(txtPath);
					System.out.println(tifPath);
					System.out.println(infoFilePath);
					Path txt = Paths.get(txtPath);
					Path tif = Paths.get(tifPath);
					Path infoPath = Paths.get(infoFilePath);
					Path newdir = Paths.get(dataDir);
					// Files.move(txt, newdir.resolve(txt.getFileName()),
					// StandardCopyOption.REPLACE_EXISTING);
					// Files.move(tif, newdir.resolve(tif.getFileName()),
					// StandardCopyOption.REPLACE_EXISTING);
					// Files.move(infoPath, newdir.resolve(infoPath.getFileName()),
					// StandardCopyOption.REPLACE_EXISTING);
					// IJ.log("Result txt, tif and mitosis info files moved.");
					restif_name = tif.getFileName().toString();
					restxt_name = txt.getFileName().toString();
					mitosisInfoName = infoPath.getFileName().toString();
				}

			} else {

			}

			ImagePlus imp_res = new ImagePlus(tifPath);
			imp_res.show();
			/* Dialog to get background values */
			GenericDialog gd = new GenericDialog("Enter background values");
			gd.addNumericField("Channel 1 (405ex) background", 100, 0);
			gd.addNumericField("Channel 2 (480ex) background", 140, 0);
			gd.addNumericField("Ring radius (pixels)", 3, 0);
			gd.showDialog();
			double background0 = gd.getNextNumber();
			double background1 = gd.getNextNumber();
			int radius = (int) gd.getNextNumber();

			/* Subtract background values from channels */
			subtractBackground(imp_ch1, (int) background0);
			subtractBackground(imp_ch2, (int) background1);
			// imp_ch1.show();
			// imp_ch2.show();

			ImagePlus ratioImage = ratioImage(imp_ch1, imp_ch2);
			// ratioImage.show();

			// imp_res.show();
			String ctcResultTxt = dataDir + System.getProperty("file.separator") + restxt_name;

			// getting mitosis info
			// MitosisInfo mitosisInfo = MitosisInfo.DeserializeMitosisInfo(dataDir +
			// System.getProperty("file.separator") + mitosisInfoName);
			System.out.println("mitosis info file" + infoFilePath);
			MitosisInfo mitosisInfo = MitosisInfo.DeserializeMitosisInfo(infoFilePath);
			if (mitosisInfo == null)
				mitosisInfo = new MitosisInfo();

			// filling roi from ctc result image
			StackDetection stackDetection = new StackDetection();
			stackDetection.fillStack(imp_res, mitosisInfo);
			System.out.println("Stack filled");
			IJ.log("Stack with ROIs filled");

			stackDetection.fillTracks(txtPath);
			System.out.println("Tracks map filled");
			IJ.log("Track information filled");

			System.out.println("");
			System.out.println(mitosisInfo.toString());
			stackDetection.changeDetectionsToRing(radius);
			stackDetection.setDetectionTrackInformation();

			boolean sort = true;
			stackDetection.addToRoiManager(sort);

			// prompt a folder selector to save results
			DirectoryChooser dirChooseSave = new DirectoryChooser("Select a folder to save measurements.");
			String dirCalculatedStatisticsSave = dirChooseSave.getDirectory();

			if (dirCalculatedStatisticsSave == null)
				dirCalculatedStatisticsSave = "";

			FormatSaver format = new FormatSaver();
			IJ.log("Calculating statistics...");
			format.calculate(stackDetection, imp_ch1, imp_ch2, ratioImage, dirCalculatedStatisticsSave, name);
			IJ.log("Done!");

		} catch (Exception e) {
			IJ.log(e.getMessage());
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	}

	private void subtractBackground(ImagePlus imp, int bg) {
		ImageStack stack = imp.getStack();
		for (int i = 0; i < stack.size(); ++i) {
			ImageProcessor ip = stack.getProcessor(i + 1);
			for (int x = 0; x < ip.getPixelCount(); ++x) {
				int v = ip.get(x) - bg;
				if (v < 0)
					v = 0;
				ip.set(x, v);
			}
		}
	}

	// ratio is channel1 / channel2 (405ex/480ex)
	private ImagePlus ratioImage(ImagePlus ch1, ImagePlus ch2) {
		ImageStack stack = ch1.getStack().convertToFloat();
		ImageStack stack0 = ch1.getStack(), stack1 = ch2.getStack();

		for (int i = 0; i < stack.getSize(); ++i) {
			ImageProcessor ip1 = stack1.getProcessor(i + 1);
			ImageProcessor ip = stack.getProcessor(i + 1);
			for (int px = 0; px < ip.getPixelCount(); ++px) {
				ip.setf(px, ip.getf(px) / ip1.getf(px));
			}
		}
		ImagePlus result = new ImagePlus("Ratio", stack);
		return result;
	}

	public ImagePlus makeRingDetections(ImagePlus ctcDetections, int dilationRadius) {
		ImageStack stack1 = ctcDetections.getStack().duplicate();
		for (int i = 0; i < stack1.size(); ++i) {
			ImageProcessor ip1 = stack1.getProcessor(i + 1);
			ImageProcessor ip2 = ctcDetections.getStack().getProcessor(i + 1);
			ip1 = ImageFunctions.operationMorph(ip1, Operation.DILATION, Strel.Shape.DISK, dilationRadius);
			ImageProcessor sub = ImageCalculator.combineImages(ip1, ip2, ImageCalculator.Operation.MINUS);
			ShortProcessor black = new ShortProcessor(ip1.getWidth(), ip1.getHeight());
			ImageProcessor ringed = ImageCalculator.combineImages(sub, black, ImageCalculator.Operation.MAX);
			stack1.setProcessor(ringed, i + 1);
		}
		ImagePlus result = new ImagePlus("", stack1);
		return result;
	}
}
