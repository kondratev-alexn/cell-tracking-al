package visualization;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import evaluation.PluginRunning;
import graph.MitosisInfo;
import ij.IJ;
import ij.ImagePlus;
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
	
	
	
	public static void drawAndSaveMitosisByResultFolder(String resultFolder) throws IOException {
		CTCResultStruct struct = new CTCResultStruct();
		struct.fillByResultsFolder(resultFolder);
		ImagePlus mitosisColored = drawMitosis(struct.impOriginal, struct.impTrackingResults, struct.ctcTracksTxtPath);
		Path res = Paths.get(resultFolder);
		Path mitImagePath = res.resolve(Paths.get(struct.noExtensionName + "_mitosis_colored.tif"));
		IJ.save(mitosisColored, mitImagePath.toString());
	}
}
