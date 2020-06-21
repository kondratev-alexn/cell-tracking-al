package visualization;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import evaluation.PluginRunning;
import graph.MitosisInfo;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import properties.CTCResultStruct;
import properties.StackDetection;

/**
 * Class for visualizing results
 * @author Alex
 *
 */
public class Visualization {
	
	public static ImagePlus drawMitosis(ImagePlus impOriginal, ImagePlus impCTCRes, String txtPath) {		
		MitosisInfo mitosisInfo = new MitosisInfo();
		StackDetection stackDetection = new StackDetection();
		stackDetection.fillStack(impCTCRes, mitosisInfo);
		stackDetection.fillTracks(txtPath);
		stackDetection.setDetectionTrackInformation();
		
		ImagePlus result = stackDetection.drawMitosis(impOriginal);

		//boolean sort = true;
		//stackDetection.addToRoiManager(sort);
		
		return result;
	}
	
	public static ImagePlus drawMitosisStartEndFromUnet(ImagePlus impBackground, ImageStack mitStart, ImageStack mitEnd) {
		int w = impBackground.getWidth();
		int h = impBackground.getHeight();
		int nSlices = impBackground.getStackSize();
		final Color GREEN = Color.GREEN;
		final Color RED = Color.RED;
		ImageStack resStack = new ImageStack(w,h,nSlices);
	    for (int c=1; c<=nSlices; ++c) {
	    	ColorProcessor cp = impBackground.getStack().getProcessor(c).convertToColorProcessor();
			for (int y = 0; y < h; y++)
				for (int x = 0; x < w; x++) {
					if (mitStart.getProcessor(c).getf(x, y) > 0.5) {
						cp.setColor(GREEN);
						cp.drawPixel(x, y);		
					}					
					if (mitEnd.getProcessor(c).getf(x, y) > 0.5) {
						cp.setColor(RED);
						cp.drawPixel(x, y);		
					}
				}
	    	resStack.setProcessor(cp, c);			
	    }
	    return new ImagePlus("Colored Mitosis", resStack);
	}
	
	
	
	public static void drawAndSaveMitosisByResultFolder(String resultFolder) throws IOException {
		CTCResultStruct struct = new CTCResultStruct();
		struct.fillByResultsFolder(resultFolder);
		ImagePlus mitosisColored = drawMitosis(struct.impOriginal, struct.impTrackingResults, struct.ctcTracksTxtPath);
		Path res = Paths.get(resultFolder);
		Path mitImagePath = res.resolve(Paths.get(struct.noExtensionName + "_mitosis_colored.tif"));
		IJ.save(mitosisColored, mitImagePath.toString());
	}
}
