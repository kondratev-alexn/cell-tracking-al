package evaluation;

import java.awt.Point;
import java.util.ArrayList;

import cellTracking.ImageComponentsAnalysis;
import cellTracking.NearestNeighbourTracking;
import graph.CellTrackingGraph;
import graph.Graph;
import graph.Node;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class EvaluationFromRoi {
	RoiManager roiManager = null;
	Graph g;
	ArrayList<ImageComponentsAnalysis> comps;
	ArrayList<ImageProcessor> images;

	public EvaluationFromRoi() {
		roiManager = RoiManager.getInstance();
		if (roiManager == null)
			roiManager = new RoiManager();
		roiManager.reset();
		g = new Graph(5, 5, 5);
		images = new ArrayList<ImageProcessor>(20);
		comps = new ArrayList<ImageComponentsAnalysis>(20);
	}

	/*
	 * full algorithm for converting to TRA format , should be called right after
	 * constructor
	 */
	public void convertToTRAformat(String roiFilePath, String imageFilePath) {
		NearestNeighbourTracking tracking = new NearestNeighbourTracking();
		drawComponents(roiFilePath, imageFilePath);
		showImages();
		ImageComponentsAnalysis comp;
		for (int i = 0; i < images.size(); i++) {
			comp = new ImageComponentsAnalysis(images.get(i), null, false);
			comp.filterComponents(0, 2000, 0, 1.0f, 0, 1000, false);
			tracking.addComponentsAnalysis(comp);
		}

		tracking.trackComponentsTRAresults(5);

		CellTrackingGraph resultGraph = new CellTrackingGraph(tracking, null, null, "", 1);
		ImagePlus imp = resultGraph.getTrackedComponentImages();
		IJ.save(imp, "tracked_components");
		// resultGraph.printTrackedGraph();
		resultGraph.writeTracksToFile_ctc_afterAnalysis("res_track_yaginuma.txt");
	}

	public void drawRoiComponentAnalysis(Roi roi, int slice, int intensity) {
		Point[] points = roi.getContainedPoints();
		for (int i = 0; i < points.length; i++) {
			images.get(slice).set((int) points[i].getX(), (int) points[i].getY(), intensity);
		}
	}

	private int getSlicesCount(Roi[] rois) {
		int slices = -1, curr;
		for (int i = 0; i < rois.length; i++) {
			curr = rois[i].getTPosition();
			if (slices < curr)
				slices = curr;
		}
		return slices;
	}

	public void showImages() {
		if (images.isEmpty())
			return;
		ImageStack stack = new ImageStack(images.get(0).getWidth(), images.get(0).getHeight(), images.size());
		for (int i = 0; i < images.size(); i++) {
			stack.setProcessor(images.get(i), i + 1);
		}
		ImagePlus image = new ImagePlus("manual tracking", stack);
		image.show();
	}

	public void drawComponents(String roiFilePath, String imageFilePath) {
		if (roiManager == null)
			return;
		roiManager.reset();
		roiManager.runCommand("Open", roiFilePath);

		// now rois should be filled and we can get them into desired format...
		Roi[] rois = roiManager.getRoisAsArray();

		int sliceCount = getSlicesCount(rois);
		images.ensureCapacity(sliceCount);
		comps.ensureCapacity(sliceCount);

		ImagePlus image = new ImagePlus(imageFilePath);
		for (int i = 0; i < image.getNSlices(); i++) {
			images.add(new ShortProcessor(image.getWidth(), image.getHeight()));
		}

		ArrayList<String> checkedRois = new ArrayList<String>(200);

		// roiManager.
		Point[] points;
		String label, roiIndex;
		String trackIndexString, sliceString;
		int slice, trackIndex, compIndex = 0, prevTrackIndex = -1, currTrackIndex;

		Node v1 = null, v2 = null;

		for (int i = 0; i < rois.length; i++) {
			points = rois[i].getContainedPoints();
			// roi track names are "Track" + ("%04d",trackIndex) + ("t%03", slice) +
			// ("R%05",something) (last one maybe label #)
			label = rois[i].getName();
			trackIndexString = label.split("Track")[1].split("t")[0];
			sliceString = label.split("t")[1].split("R")[0];
			roiIndex = label.split("R")[1];

			// System.out.println(trackIndexString + ' ' + sliceString + ' ' + roiIndex);

			// this is needed because in Yaginuma results, some tracks contain the same
			// roi...
			if (checkedRois.contains(roiIndex)) {
				System.out.println("roi with label " + label + "was already written");
				continue;
			}
			checkedRois.add(roiIndex);

			trackIndex = Integer.valueOf(trackIndexString) + 1; // tracks should start from 1
			slice = Integer.valueOf(sliceString) - 1; // slices should start from 0

			drawRoiComponentAnalysis(rois[i], slice, trackIndex);

			currTrackIndex = trackIndex;
			if (currTrackIndex == prevTrackIndex) { // continue current track
				++compIndex;
			} else { // new track started
				compIndex = 0;
				v1 = null;
			}
			prevTrackIndex = currTrackIndex;

			// v2 = new Node(slice, compIndex);
			// g.addNode(v2);
			// if (v1 == null) {
			// v1 = v2;
			// continue;
			// }
			// g.addArcFromToAddable(v1, v2); //this only happens for tracks of 2+ nodes
		}
		// after that we should have our graph ready...
	}
}
