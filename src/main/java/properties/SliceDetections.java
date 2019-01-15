package properties;

import java.awt.Point;
import java.util.HashMap;

import graph.MitosisInfo;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import statistics.Measure;

/* representation of components in slice for reading from TRA format (mb will be used for all) */
public class SliceDetections {
	/* image with different components displayed with different intensities */
	private ImageProcessor imageDetections;

	/*
	 * map from component intensity (also track index) in image to the internal
	 * component representation
	 */
	private HashMap<Integer, Detection> detectionsMap;

	/* creates and fills slice detections, with roi */
	public SliceDetections(ImageProcessor traDetections, int slice, MitosisInfo mitosisInfo) {
		imageDetections = traDetections;
		detectionsMap = new HashMap<Integer, Detection>(10);
		fillMap(traDetections, true, slice, mitosisInfo);
	}

	void fillMap(ImageProcessor detections, boolean backgroundZero, int slice, MitosisInfo mitosisInfo) {
		for (int y = 0; y < detections.getHeight(); y++)
			for (int x = 0; x < detections.getWidth(); x++) {
				int intensity = detections.get(x, y);
				if (backgroundZero && intensity == 0)
					continue;
				if (detectionsMap.containsKey(intensity))
					continue;
				
				boolean isMitosis = mitosisInfo.contains(intensity, slice-1);
				
				Detection det = new Detection(intensity, slice, isMitosis);
				det.fillRoi(detections, intensity, x, y, slice);

				detectionsMap.put(intensity, det);
			}
	}

	public ImageProcessor detectionsImage() {
		ImageProcessor ip = new ShortProcessor(imageDetections.getWidth(), imageDetections.getHeight());
		for (Detection detection : detectionsMap.values()) {
			Roi roi = detection.roi();
			for (Point p : roi) {
				if (Measure.isPointIn(p, ip))
					ip.set(p.x, p.y, detection.displayIntensity());
			}
		}
		return ip;
	}

	public boolean isTrackInSlice(int trackIndex) {
		return detectionsMap.containsKey(trackIndex);
	}

	public Roi detectionRoi(int trackIndex) {
		return detectionsMap.get(trackIndex).roi();
	}

	public HashMap<Integer, Detection> detectionsMap() {
		return detectionsMap;
	}

	public void setRoi(Roi roi, int trackIndex) {
		detectionsMap.get(trackIndex).setRoi(roi);
	}
}
