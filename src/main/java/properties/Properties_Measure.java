package properties;

import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import cellTracking.Cell_Tracker;
import cellTracking.ImageFunctions;
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
	
	//final ImageJ ij = new ImageJ();
	
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
		DirectoryChooser dirChoose = new DirectoryChooser("Select a folder with data");
		String dir;
		
		boolean selectDir = true;
		if (selectDir)
			dir = dirChoose.getDirectory();
		else
			dir = "C:\\Tokyo\\Data\\Properties Measure";

		try {
			SimpleDirectoryScanner scanner = new SimpleDirectoryScanner();
			scanner.setDirectory(dir);			

			String ch1_name = scanner.fileNameBySuffix("c1.tif");
			if (ch1_name.isEmpty())
				throw new Exception("No channel 1 tif image was found");
			String ch2_name = scanner.fileNameBySuffix("c2.tif");
			if (ch2_name.isEmpty())
				throw new Exception("No channel 2 tif image was found");
			
			boolean startTracking = false;
			String restif_name = scanner.fileNameBySuffix("results.tif");
			if (restif_name.isEmpty()) {
				IJ.log("No tracking result in CTC image format was found.");
				startTracking = true;
			}
			String restxt_name = scanner.fileNameBySuffix("results.txt");
			if (restxt_name.isEmpty()) {
				IJ.log("No tracking result in CTC text format was found");
				startTracking = true;
			}
			
//			DirectoryScanner scanner = new DirectoryScanner();
//			scanner.setBasedir(dir);
			
//			scanner.setCaseSensitive(false);
//
//			scanner.setIncludes(new String[] { "*c1.tif" });
//			scanner.scan();
//			String[] files_ch1 = scanner.getIncludedFiles();
//			if (files_ch1.length == 0)
//				throw new Exception("No channel 1 tif image was found");
//
//			scanner.setIncludes(new String[] { "*c2.tif" });
//			scanner.scan();
//			String[] files_ch2 = scanner.getIncludedFiles();
//			if (files_ch2.length == 0)
//				throw new Exception("No channel 2 tif image was found");
//
//			scanner.setIncludes(new String[] { "*results.tif" });
//			scanner.scan();
//			String[] files_restif = scanner.getIncludedFiles();
//			if (files_restif.length == 0)
//				throw new Exception("No tracking result in CTC image format was found");
//
//			scanner.setIncludes(new String[] { "*results.txt" });
//			scanner.scan();
//			String[] files_restxt = scanner.getIncludedFiles();
//			if (files_restxt.length == 0)
//				throw new Exception("No tracking result in CTC text format was found");
//
//			String ch1_name = files_ch1[0];
//			String ch2_name = files_ch2[0];
//			String restif_name = files_restif[0];
//			String restxt_name = files_restxt[0];
			
			String[] split = ch2_name.split("c2");
			String name = split[0];
			System.out.println(name);

			ImagePlus imp_ch1 = new ImagePlus(dir + System.getProperty("file.separator") + ch1_name);
			ImagePlus imp_ch2 = new ImagePlus(dir + System.getProperty("file.separator") + ch2_name);
			
			if (startTracking) {
				IJ.log("Launching tracking plugin");
				Cell_Tracker tracker = new Cell_Tracker();
				imp_ch2.show();
				Cell_Tracker o = (Cell_Tracker) IJ.runPlugIn(imp_ch2, Cell_Tracker.class.getName(), "");				
				if (o == null)
					IJ.log("Failed to run plugin");
				
				String txtPath = o.textResultsPath();
				String tifPath = o.tifResultPath();
				Path txt = Paths.get(txtPath);
				Path tif = Paths.get(tifPath);
				Path newdir = Paths.get(dir);
				Files.move(txt, newdir.resolve(txt.getFileName()), StandardCopyOption.REPLACE_EXISTING);
				Files.move(tif, newdir.resolve(tif.getFileName()), StandardCopyOption.REPLACE_EXISTING);
				IJ.log("Result txt and tif files moved.");
				restif_name = tif.getFileName().toString();
				restxt_name = txt.getFileName().toString();
				
			} else {
				
			}
			
			ImagePlus imp_res = new ImagePlus(dir + System.getProperty("file.separator") + restif_name);
			imp_res.show();
			/* Dialog to get background values */
			GenericDialog gd = new GenericDialog("Enter background values");
			gd.addNumericField("Channel 1 (405ex) background", 100, 0);
			gd.addNumericField("Channel 2 (480ex) background", 140, 0);
			gd.showDialog();
			double background0 = gd.getNextNumber();
			double background1 = gd.getNextNumber();			

			/* Subtract background values from channels */
			subtrackBackground(imp_ch1, (int) background0);
			subtrackBackground(imp_ch2, (int) background1);
//			imp_ch1.show();
//			imp_ch2.show();

			ImagePlus ratioImage = ratioImage(imp_ch1, imp_ch2);
//			ratioImage.show();

//			imp_res.show();

			// filling roi from ctc result image
			StackDetection stackDetection = new StackDetection();
			stackDetection.fillStack(imp_res);
			// stackDetection.makeRingRoi(0, 9, 3);
			stackDetection.changeDetectionsToRing(3);
//			stackDetection.show();

			System.out.println("Stack filled");
			IJ.log("Stack with ROIs filled");

			// fill tracks mapping
			TrackCTCMap tracksMap = new TrackCTCMap(dir + System.getProperty("file.separator") + restxt_name, stackDetection);
			System.out.println("Tracks map filled");
			IJ.log("Track information filled");

			FormatSaver format = new FormatSaver();
			IJ.log("Calculating statistics...");
			format.calculate(tracksMap, stackDetection, imp_ch1, imp_ch2, ratioImage, dir, name);
			IJ.log("Done!");

		} catch (Exception e) {
			IJ.log(e.getMessage());
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	}

	private void subtrackBackground(ImagePlus imp, int bg) {
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
