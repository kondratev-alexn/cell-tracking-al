package properties;

import java.util.HashMap;

import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

/* representation of components in slice for reading from TRA format (mb will be used for all) */
public class SliceDetections {
	/* image with different components displayed with different intensities */
	private ImageProcessor imageDetections;
	
	/* map from component intensity (also track index) in image to the internal component representation */
	private HashMap<Integer, Detection> detectionsMap;
	
	/* creates and fills slice detections, with roi */
	public SliceDetections(ImageProcessor traDetections, int slice){ 
		imageDetections = traDetections;
		detectionsMap = new HashMap<Integer, Detection>(10);
		fillMap(traDetections, true, slice);
	}
	
	void fillMap(ImageProcessor detections, boolean backgroundZero, int slice) {
		for (int y=0; y<detections.getHeight(); y++)
			for (int x=0; x<detections.getWidth(); x++) {
				int intensity = detections.get(x, y);
				if (backgroundZero && intensity == 0)
					continue;
				if (detectionsMap.containsKey(intensity))
					continue;
				
				Detection det = new Detection(intensity, slice);
				det.fillRoi(detections, intensity, x, y, slice);
				
				detectionsMap.put(intensity, det);
			}
	}
	
	public boolean isTrackInSlice(int trackIndex) {
		return detectionsMap.containsKey(trackIndex);
	}
	
	public Roi detectionRoi(int trackIndex) {
		return detectionsMap.get(trackIndex).roi();
	}
}
