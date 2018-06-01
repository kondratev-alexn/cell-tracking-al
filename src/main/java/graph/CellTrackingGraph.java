package graph;

import java.awt.Color;
import java.awt.Point;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;

import cellTracking.ImageComponentsAnalysis;
import cellTracking.NearestNeighbourTracking;
import colorPicking.ColorPicker;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Wand;
import ij.plugin.frame.RoiManager;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.process.StackProcessor;
import tracks.Tracks;

/* class representing graph used for cell tracking */
public class CellTrackingGraph {

	private Graph trGraph; // graph just after tracking
	private Graph g; // cell graph
	private ArrayList<ImageComponentsAnalysis> prevComponentsList; // initial components list
	private ArrayList<ImageComponentsAnalysis> componentsList; // list with tracked component analysis
	private ArrayList<ImageProcessor> images; // images with components labeled after tracking. Will be generated by
												// tracking results

	private static int newIndex; // it is global index for regraphing tracking results
	private RoiManager roiManager; // roi manager for saving resulting tracks as rois
	ImagePlus activeImage, resultImage;

	/*
	 * construct a cell tracking graph based on graph after tracking algorithm. cell
	 * tracking graph's node labels are indexes that refer to cell id. So the cell
	 */
	public CellTrackingGraph(NearestNeighbourTracking trackingResult, RoiManager roiManager, ImagePlus activeImage) {
		// Graph trGraph = trackingResult.getGraph();
		this.roiManager = roiManager;
		if (roiManager != null)
			roiManager.reset();
		trGraph = trackingResult.getGraph();
		int nNodes = trGraph.nodes.size();
		int nArcs = trGraph.arcs.size();
		int nAdj = trGraph.adjLists.size();
		g = new Graph(nNodes, nArcs, nAdj);
		componentsList = new ArrayList<ImageComponentsAnalysis>(trackingResult.getSlicesCount());
		images = new ArrayList<ImageProcessor>(trackingResult.getSlicesCount());
		prevComponentsList = trackingResult.getComponentsList();
		// trGraph = trackingResult.getGraph();

		for (int i = 0; i < prevComponentsList.size(); i++) {
			images.add(new ShortProcessor(prevComponentsList.get(i).getWidth(), prevComponentsList.get(i).getHeight()));
		}

		this.activeImage = activeImage;
		// roiManager.selectAndMakeVisible(activeImage, -1);

		// here we should do post-processing for tracks and segmentation, i.e delete
		// short tracks, analyze division
		try {
			// Tracks tracks = new Tracks(trGraph); //now we can use info about tracks to
			// manipulate them
			// tracks should be ok, checked on easy seq.

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		resetNewIndex();
		System.out.println("Graph before analyzing: ");
		System.out.println(trGraph);
		analyseTrackingGraph(); // new g is generated, images filled with newly labeled components
		for (int i = 0; i < componentsList.size(); i++) {
			ImageComponentsAnalysis comps = new ImageComponentsAnalysis(images.get(i),
					prevComponentsList.get(i).getAvrgIntensityImage(), false);
			// NOTE that after this background is a component, so we should filter it out
			comps.filterComponents(0, 2000, 0, 1.0f, 0, 1000, false);
		}
		// after this we got newly generated graph g and component analysis for each
		// slice based on tracking result. Index of each component (intensity)
		// is the same for one component through the track
		// BUT BEWARE: THE INDEXES IN GRAPH g ARE REFERED TO INTENSITIES, NOT TO INDEXES
		// IN ARRAYS
	}

	/* constructor for tra metrics */
	public CellTrackingGraph(Graph g, int imageWidth, int imageHeight, int imageCount) {
		trGraph = g;
		int nNodes = trGraph.nodes.size();
		int nArcs = trGraph.arcs.size();
		int nAdj = trGraph.adjLists.size();
		g = new Graph(nNodes, nArcs, nAdj);

		images = new ArrayList<ImageProcessor>(5);

		// trGraph = trackingResult.getGraph();

		for (int i = 0; i < imageCount; i++) {
			images.add(new ShortProcessor(imageWidth, imageHeight));
		}
	}

	private static int getNewIndex() {
		return newIndex++;
	}

	private static void resetNewIndex() {
		newIndex = 1;
	}

	private static void setNewIndexIterator(int value) {
		newIndex = value;
	}

	/*
	 * draws a component from\on slice @param sliceIndex with index @param
	 * indexInPrev in prevComponentsList and assign intensity @param intensity to it
	 */
	void drawComponentInImage(int sliceIndex, int intensity, int indexInPrev) {
		ImageComponentsAnalysis prevComp = prevComponentsList.get(sliceIndex);
		int x0 = prevComp.getComponentX0(indexInPrev);
		int x1 = prevComp.getComponentX1(indexInPrev);
		int y0 = prevComp.getComponentY0(indexInPrev);
		int y1 = prevComp.getComponentY1(indexInPrev);
		ImageProcessor prevImage = prevComp.getImageComponents();
		int prevIntensity = prevComp.getComponentDisplayIntensity(indexInPrev);
		for (int y = y0; y <= y1; y++)
			for (int x = x0; x <= x1; x++) {
				if (prevImage.get(x, y) == prevIntensity) {
					images.get(sliceIndex).set(x, y, intensity);
				}
			}
	}

	Roi getComponentAsRoi(int sliceIndex, int intensity, int indexInPrev, int mainTrackIndex, int labelIndex,
			int childIndex) {
		ImageComponentsAnalysis prevComp = prevComponentsList.get(sliceIndex);
		int x0 = prevComp.getComponentX0(indexInPrev);
		int y0 = prevComp.getComponentY0(indexInPrev);

		ImageProcessor imageComponents = images.get(sliceIndex);

		Roi roi = null;
		Wand w = new Wand(imageComponents);
		String roiName;

		w.autoOutline(x0, y0, intensity, intensity);
		if (w.npoints > 0) { // we have a roi from the wand...
			roi = new PolygonRoi(w.xpoints, w.ypoints, w.npoints, Roi.TRACED_ROI);
			roiName = String.format("Track%04d", mainTrackIndex);
			roiName = roiName.concat(String.format("t%03d", sliceIndex));
			roiName = roiName.concat(String.format("R%06d", labelIndex));
			roiName = roiName.concat(String.format("child%06d", childIndex));
			// roiName = roiName.concat(String.format("adj%06d", adjIndex));
			roi.setName(roiName);
			roi.setPosition(sliceIndex + 1);
		}

		return roi;
	}

	/* returns copy of adj list */
	/*
	 * ArrayList<ArrayList<Integer>> copyAdjList(ArrayList<ArrayList<Integer>> list)
	 * { ArrayList<ArrayList<Integer>> result = new
	 * ArrayList<ArrayList<Integer>>(list.size()); ArrayList<Integer> currlist; for
	 * (int i=0; i< list.size(); i++) { currlist = new
	 * ArrayList<Integer>(list.get(i).size()); //result.add(new ArrayList()) for
	 * (int j=0; j<list.get(i).size(); j++) { currlist.add(list.get(i).get(j)); }
	 * result.add(currlist); } return result; }
	 */

	/* reconstruct new graph and component images according to tracking results */
	void analyseTrackingGraph() {
		ArrayList<Integer> childs;
		ArrayList<ArrayList<Integer>> adj = Graph.copyAdjList(trGraph.adjLists);
		if (adj.isEmpty()) {
			System.out.println("copied adj list is empty");
			return;
		}
		resetNewIndex();

		for (int i = 0; i < adj.size(); i++) { // nodes "for". cycle through "grand parents" i.e. that begin the track
			childs = adj.get(i);
			// lets skip components that doesn't have children. And use it as indicator that
			// the component was re-tracked.
			// Because we also skip "sole" components which shoudln't be in the track
			if (childs.isEmpty()) {
				continue;
			}
			if (childs.size() == 2) { // if track ended on division
				// do nothing, so it would be registered in addTrack function
				// continue;
			}
			if (childs.size() > 2) {
				System.out.println(" !!! More than 2 children in graph detected !!! ");
			}

			// now only 1 child, make a track. It still would be added for sure, since only
			// 1
			// child. 2 children also handled in addtrack
			addTrack(adj, Graph.getStartingAdjIndex(adj, i), getNewIndex());
		}
	}

	// only called whe component has 1 child besides when in itself or when it has
	// parent (called from division)
	// so first cell should be drawn
	boolean addTrack(ArrayList<ArrayList<Integer>> adj, int startIndexAdj, int startingTrackIndex) {
		boolean added = false;
		ArrayList<Integer> childs;
		int childIndex = -1, t1, t2, ci1, ci2;
		Node v1, v2;
		int startSlice, endSlice, count = 0;
		int trackIndex = startingTrackIndex;
		Roi roi;

		t1 = trGraph.getNodeSliceByGlobalIndex(startIndexAdj);
		startSlice = t1;
		endSlice = t1;
		v1 = new Node(t1, trackIndex);
		drawComponentInImage(t1, trackIndex, trGraph.getNodeIndexByGlobalIndex(startIndexAdj));

		if (roiManager != null) {
			roi = getComponentAsRoi(t1, trackIndex, trGraph.getNodeIndexByGlobalIndex(startIndexAdj),
					startingTrackIndex, count, startIndexAdj);
			if (roi != null) {
				roiManager.addRoi(roi);
			} else
				System.out.println("null roi");
		}

		childs = adj.get(startIndexAdj);
		while (childs.size() == 1) { // track a component until it has no childen (disappear) or 2 children
										// (division)
			// add arc to Graph, draw component
			childIndex = childs.get(0);

			t2 = trGraph.getNodeSliceByGlobalIndex(childIndex);
			v2 = new Node(t2, trackIndex);
			g.addArcFromToAddable(v1, v2);
			// System.out.println("Added arc from " + v1 + " to " + v2 + " with track index
			// " + trackIndex + " and startAdj i " + startIndexAdj + " and child " +
			// childs);

			added = true;
			childs.clear(); // clear to mark component as tracked in parent node
			v1 = v2;
			childs = adj.get(childIndex); // go to child component

			if (t2 - t1 > 1) {
				// write this track and start another one, connected to this
				trackIndex = getNewIndex();
				startSlice = t2;
			}
			t1 = t2; // t1 is for previous slice
			endSlice = t2;
			drawComponentInImage(t1, trackIndex, trGraph.getNodeIndexByGlobalIndex(childIndex));
			if (roiManager != null) {
				roi = getComponentAsRoi(t1, trackIndex, trGraph.getNodeIndexByGlobalIndex(childIndex),
						startingTrackIndex, ++count, childIndex);
				if (roi != null) {
					roiManager.addRoi(roi);
				} else
					System.out.println(" ### roi is null for slice " + t1 + " count " + count);
			}
		}
		if (childs.size() == 2) { // create division in graph
			// add needed arcs from v1 to its children and add 2
			t2 = trGraph.getNodeSliceByGlobalIndex(childs.get(0));
			// SO THERE ARE SOME PROBLMES INVOLVING NEW INDEXES OF PARENT NODES WHEN
			// DIVIDING, CHECK LATER. Should be ok now
			ci1 = getNewIndex(); // get next index and increment it
			v2 = new Node(t2, ci1);
			g.addArcFromToAddable(v1, v2);
			System.out.println();
			System.out.println("Added arc child 1: " + v1 + "---" + v2);

			// no need to draw there because the track will be drawn in previous 'for'
			addTrack(adj, childs.get(0), ci1);

			// add arc here
			t2 = trGraph.getNodeSliceByGlobalIndex(childs.get(1));
			ci2 = getNewIndex(); // get new index and increment it
			v2 = new Node(t2, ci2);
			g.addArcFromToAddable(v1, v2);
			System.out.println();
			System.out.println("Added arc child 2: " + v1 + "---" + v2);

			addTrack(adj, childs.get(1), ci2);

			childs.clear();
		}
		// better add division events here, because we have index
		return added;
	}

	public void writeTracksToFile_ctc_afterAnalysis(String filename) {
		writeTracksToFile_ctc_general(filename, g);
	}

	/*
	 * generated txt file for TRA evaluation in CTC format algorithm should be
	 * changed so that track of one cell that interrupts for 1+ slices is divided
	 * into several tracks
	 */
	public static void writeTracksToFile_ctc_general(String filename, Graph g_analysed) {
		BufferedWriter writer = null;
		resetNewIndex();
		ArrayList<ArrayList<Integer>> adj = Graph.copyAdjList(g_analysed.getAdjList());
		try {
			File logFile = new File(filename);

			// This will output the full path where the file will be written to...
			System.out.println(logFile.getCanonicalPath());

			writer = new BufferedWriter(new FileWriter(logFile));

			String trackTxt;
			ArrayList<Integer> childs;
			int trackAdjIndex;
			for (int i = 0; i < adj.size(); i++) { // nodes "for". cycle through "grand parents" i.e. that begin the
													// track
				childs = adj.get(i);
				// lets skip components that doesn't have children. And use it as indicator that
				// the component was re-tracked.
				// Because we also skip "sole" components which shoudln't be in the track
				if (childs.isEmpty()) {
					continue;
				}
				if (childs.size() == 2) { // if track ended on division
					// do this event in getText
					// continue;
				}

				trackAdjIndex = g_analysed.getNodeIndexByGlobalIndex(i); // get track
																			// index as
																			// intensity
				// now only 1 child, make a track. It still would be added for sure, since only
				// 1 child
				trackTxt = getTrackString(g_analysed, adj, i, getNewIndex(), trackAdjIndex, 0);
				System.out.println(trackTxt);
				writer.write(trackTxt);
				writer.newLine();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				// Close the writer regardless of what happens...
				writer.close();
			} catch (Exception e) {
			}
		}
	}

	/* called only when there is at least 1 child starting from the startAdjIndex */
	private static String getTrackString(Graph g, ArrayList<ArrayList<Integer>> adj, int startIndexAdj, int trackIndex,
			int trackAdjIndex, int parentTrackIndex) {
		String result = "";
		ArrayList<Integer> childs;
		int childIndex = -1, t1, t2, ci1, ci2;
		int startSlice, endSlice;
		int currTrackIndex = trackIndex;

		childs = adj.get(startIndexAdj);
		t1 = g.getNodeSliceByGlobalIndex(startIndexAdj); // slices are labeled 1 to nSlices
		startSlice = t1;
		endSlice = t1;

		while (childs.size() == 1) { // track a component until it has no children (disappear) or 2 children
										// (division)
			childIndex = childs.get(0);
			t2 = g.getNodeSliceByGlobalIndex(childIndex);

			childs.clear(); // clear to mark component as tracked in parent node
			childs = adj.get(childIndex); // go to child component
			if (t2 - t1 > 1) {
				// write this track and start another one, connected to this
				result = result.concat(
						trackString(currTrackIndex, startSlice, endSlice, parentTrackIndex) + System.lineSeparator());
				parentTrackIndex = currTrackIndex;
				currTrackIndex = getNewIndex();
				startSlice = t2;
			}
			t1 = t2; // t1 is for previous slice
			endSlice = t2;
		}

		// end of track reached, write to string
		// "track_id" "start slice" "end slice" "parent_id"
		if (childIndex == -1) { // it means that the parent component has no parent components
			t2 = t1;
		} else {
			t2 = g.getNodeSliceByGlobalIndex(childIndex);
		}

		result = result.concat(trackString(currTrackIndex, startSlice, t2, parentTrackIndex));
		if (childs.size() == 2) { // division

			result = result.concat(System.getProperty("line.separator"));
			ci1 = g.getNodeIndexByGlobalIndex(childs.get(0));
			result = result.concat(getTrackString(g, adj, childs.get(0), getNewIndex(), ci1, currTrackIndex));

			result = result.concat(System.getProperty("line.separator"));
			ci2 = g.getNodeIndexByGlobalIndex(childs.get(1));
			result = result.concat(getTrackString(g, adj, childs.get(1), getNewIndex(), ci2, currTrackIndex));

			childs.clear();
		}
		return result;
	}

	private static String trackString(int trackIndex, int t1, int t2, int parentTrackIndex) {
		return String.valueOf(trackIndex) + " " + String.valueOf(t1) + " " + String.valueOf(t2) + " "
				+ String.valueOf(parentTrackIndex);
	}

	public void showTrackedComponentImages() {
		if (images.isEmpty())
			return;
		ImageStack stack = new ImageStack(images.get(0).getWidth(), images.get(0).getHeight(), images.size());
		for (int i = 0; i < images.size(); i++) {
			stack.setProcessor(images.get(i), i + 1);
		}

		ImagePlus imp = new ImagePlus("Tracked components", stack);
		imp.show();
	}

	public ImagePlus drawColorComponents(ImagePlus image) {
		ColorPicker colorPicker = new ColorPicker();
		ImageStack stack = new ImageStack(image.getWidth(), image.getHeight(), image.getNSlices());
		System.out.println(image.getNSlices());
		ColorProcessor cim;

		stack.setProcessor(image.getStack().getProcessor(1).convertToColorProcessor(), 1);
		for (int i = 1; i <= image.getNSlices(); i++) { // slices are from 1 to n_slices
			cim = image.getStack().getProcessor(i).duplicate().convertToColorProcessor();
			drawComponentsColored(cim, i);
			stack.setProcessor(cim, i);
		}
		ImagePlus result = new ImagePlus("tracks", stack);
		result.show();
		return result;
	}

	/* draw arcs in colorProcessor */
	public void drawComponentsColored(ColorProcessor cim, int slice) {
		int i0, i1, t0, t1;
		Node n0, n1;
		Point p0, p1;
		Roi roi;
		Color color;
		int intensity;
		int x0, x1, y0, y1;
		for (int y=0; y<cim.getHeight(); y++)
			for (int x=0; x<cim.getWidth(); x++) {
				intensity = images.get(slice-1).get(x, y); 
				if (intensity != 0) {
					cim.setColor(ColorPicker.color(intensity));
					cim.drawPixel(x, y);
				}
			}
		
//		for (int k = 0; k < componentsList.size(); k++) {
//			roi = componentsList.get(slice).getComponentAsRoi(k, slice);
//			if (roi != null) {
//				color = ColorPicker.color(componentsList.get(slice).getComponentDisplayIntensity(k));
//				cim.setColor(color);
//				cim.drawPixel(x, y);
//			}
//		}
	}

	public ImagePlus getTrackedComponentImages() {
		if (images.isEmpty())
			return new ImagePlus();
		ImageStack stack = new ImageStack(images.get(0).getWidth(), images.get(0).getHeight(), images.size());
		for (int i = 0; i < images.size(); i++) {
			stack.setProcessor(images.get(i), i + 1);
		}

		ImagePlus imp = new ImagePlus("Tracked components", stack);
		return imp;
	}

	public void printTrackedGraph() {
		System.out.println(g);
	}
}
