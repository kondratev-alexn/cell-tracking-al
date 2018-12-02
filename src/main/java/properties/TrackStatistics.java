package properties;

import ij.ImagePlus;
import ij.gui.Roi;

public class TrackStatistics {

	/* create class for statistics. We need image itself, stack containing rois, track information */
	public TrackStatistics(ImagePlus imp, StackDetection stack, TrackCTC track) {
		
	}
	
	void statistics(ImagePlus imp, StackDetection stack, TrackCTC track) {
		int index = track.index();
		for (int i=track.startSlice(); i<=track.endSlice(); ++i) {
			Roi roi = stack.detectionRoi(i, index);

		}
	}
	
}
