package properties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.tools.ant.DirectoryScanner;

import cellTracking.Cell_Tracker;
import cellTracking.ImageFunctions;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import inra.ijpb.morphology.Strel;
import inra.ijpb.math.ImageCalculator;
import inra.ijpb.morphology.Morphology.Operation;

public class Properties_Measure implements PlugIn {
	public static void main(String[] args) {

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
		// TODO Auto-generated method stub
		// DirectoryChooser dirChoose = new DirectoryChooser("Select a folder with
		// data");
		String dir;
		// dir = dirChoose.getDirectory();
		dir = "C:\\Tokyo\\Data\\Properties Measure";

		try {
			DirectoryScanner scanner = new DirectoryScanner();
			scanner.setBasedir(dir);
			scanner.setCaseSensitive(false);

			scanner.setIncludes(new String[] { "*ch_0.tif" });
			scanner.scan();
			String[] files_ch0 = scanner.getIncludedFiles();
			if (files_ch0.length == 0)
				throw new Exception("No channel 0 tif image was found");

			scanner.setIncludes(new String[] { "*ch_1.tif" });
			scanner.scan();
			String[] files_ch1 = scanner.getIncludedFiles();

			scanner.setIncludes(new String[] { "*results.tif" });
			scanner.scan();
			String[] files_restif = scanner.getIncludedFiles();

			scanner.setIncludes(new String[] { "*results.txt" });
			scanner.scan();
			String[] files_restxt = scanner.getIncludedFiles();

			System.out.println(files_ch0[0]);
			System.out.println(files_ch1[0]);
			System.out.println(files_restif[0]);
			System.out.println(files_restxt[0]);

			String ch0_name = files_ch0[0];
			String ch1_name = files_ch1[0];
			String restif_name = files_restif[0];
			String restxt_name = files_restxt[0];

			ImagePlus imp_ch0 = new ImagePlus(dir + '\\' + ch0_name);
			ImagePlus imp_ch1 = new ImagePlus(dir + '\\' + ch1_name);
			ImagePlus imp_res = new ImagePlus(dir + '\\' + restif_name);
			
			imp_res = makeRingDetections(imp_res, 3);
			imp_res.show();
			
			// filling roi from ctc result image
			StackDetection stackDetection = new StackDetection();
			stackDetection.fillStack(imp_res);
			//stackDetection.makeRingRoi(0, 9);
			
			System.out.println("Stack filled");
			
			// fill tracks mapping
			TrackCTCMap tracksMap = new TrackCTCMap(dir + '\\' + restxt_name, stackDetection);
			System.out.println("Tracks map filled");
			
			FormatSaver format = new FormatSaver();
			format.calculate(tracksMap, stackDetection, imp_ch0, dir);
			
		} catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}

	}
	
	public ImagePlus makeRingDetections(ImagePlus ctcDetections, int dilationRadius) {
		ImageStack stack1 = ctcDetections.getStack().duplicate();
		for (int i=0; i<stack1.size(); ++i) {
			ImageProcessor ip1 = stack1.getProcessor(i+1);
			ImageProcessor ip2 = ctcDetections.getStack().getProcessor(i+1);
			ip1 = ImageFunctions.operationMorph(ip1, Operation.DILATION, Strel.Shape.DISK, dilationRadius);
			ImageProcessor sub = ImageCalculator.combineImages(ip1, ip2, ImageCalculator.Operation.MINUS);
			ShortProcessor black = new ShortProcessor(ip1.getWidth(), ip1.getHeight());
			ImageProcessor ringed = ImageCalculator.combineImages(sub, black, ImageCalculator.Operation.MAX);
			stack1.setProcessor(ringed, i+1);			
		}
		ImagePlus result = new ImagePlus("", stack1);
		return result;
	}
}
