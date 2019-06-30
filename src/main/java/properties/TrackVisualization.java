package properties;

import java.util.ArrayList;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;

/**
 * Class for visualizing tracking results
 *
 */
public class TrackVisualization {
		
	public static ImagePlus visualize(StackDetection stackDetection) {
		ArrayList<SliceDetections> detections = stackDetection.detections();
		int w = detections.get(0).detectionsImage().getWidth();
		int h = detections.get(0).detectionsImage().getHeight();
		int len = detections.size();
		ImageStack stack = new ImageStack(w, h, len);
		ColorProcessor cim;

		//stack.setProcessor(image.getStack().getProcessor(1).convertToColorProcessor(), 1);
		for (int i = 1; i <= len; i++) { // slices are from 1 to n_slices
			cim = detections.get(i).detectionsImage().duplicate().convertToColorProcessor();
			//drawComponentsColored(cim, i);
			stack.setProcessor(cim, i);
		}
		ImagePlus result = new ImagePlus("tracks", stack);
		result.show();
		return result;
	}
}
