package cellTracking;

import java.awt.Color;
import java.awt.image.ColorModel;
import java.util.ArrayList;
import java.util.Stack;

import graph.Arc;
import graph.Graph;
import graph.Node;
import histogram.FloatHistogram;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;

import point.Point;
import tracks.Track;
import tracks.Tracks;

import cellTracking.PenaltyFunction;
import colorPicking.ColorPicker;

public class NearestNeighbourTracking {
	private Graph cellGraph;

	private int currSlice;
	private int slicesCount;

	private Tracks tracks;

	/*
	 * List of components classes, containing image with labels and information
	 * about them. Should be formed during processing and then processed.
	 */
	private ArrayList<ImageComponentsAnalysis> componentsList;

	public Graph getGraph() {
		return cellGraph;
	}

	public NearestNeighbourTracking() {
		// this.slicesCount = slicesCount;
		componentsList = new ArrayList<ImageComponentsAnalysis>();
		cellGraph = new Graph(5, 5, 5);
	}

	public void addComponentsAnalysis(ImageComponentsAnalysis comps) {
		componentsList.add(comps);
	}

	public int getSlicesCount() {
		return slicesCount;
	}

	public ArrayList<ImageComponentsAnalysis> getComponentsList() {
		return componentsList;
	}

	// fill tracks using cellGraph, should be called after the nnr tracking
	public void fillTracks() {
		try {
			tracks = new Tracks(cellGraph);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * finds nearest components in comp1-comp2 not further than 'radius' pixels. (t1
	 * and t2 refers to time points of comp1 and comp2 respectively) this should
	 * refer to the i -> i+1 step. So t1>t2. t1 and t2 are only required to properly
	 * fill in the graph
	 */
	public void findNearestComponents(ImageComponentsAnalysis comp1, int t1, ImageComponentsAnalysis comp2, int t2,
			double radius, double scoreThreshold) {
		Point m1, m2;
		int closestIndex, backClosestIndex;
		Node v1, v2;
		for (int i = 0; i < comp2.getComponentsCount(); i++) {
			if (comp2.getComponentHasParent(i)) { // only add to components that doesn't have parent
				continue;
			}
			m2 = comp2.getComponentMassCenter(i); //
			// closestIndex = findClosestPointIndex(m2, comp1, radius);
			closestIndex = findBestScoringComponentIndex(comp2, i, comp1, radius, scoreThreshold);
			if (closestIndex != -1) { // closest component found, add to graph
				// should also check back - if for the found component the closest neighbour is
				// the same, then link them, otherwise skip?
				m1 = comp1.getComponentMassCenter(closestIndex);
				// backClosestIndex = findClosestPointIndex(m1, comp2, radius);
				backClosestIndex = findBestScoringComponentIndex(comp1, closestIndex, comp2, radius, scoreThreshold);
				if (backClosestIndex != i)
					continue;
				if (comp1.getComponentChildCount(closestIndex) > 0) { // only if it has no children
					continue;
				}
				v1 = new Node(t1, closestIndex);
				v2 = new Node(t2, i);
				cellGraph.addArcFromToAddable(v1, v2);
				comp2.setComponentHasParent(i);
				comp1.incComponentChildCount(closestIndex);
			} else {
				// closest component for current component in comp1 was not found - may be
				// remove it from detection (it may not be correct detection)
			}
		}
	}

	/* this is for back tracking (mitosys should be tracked with this steps) */
	public void findNearestComponentsBackStep(ImageComponentsAnalysis comp2, int t2, ImageComponentsAnalysis comp1,
			int t1, double radius, double scoreThreshold) {
		Point m2;
		int closestIndex;
		Node v1, v2;
		for (int i = 0; i < comp2.getComponentsCount(); i++) {
			if (comp2.getComponentHasParent(i)) {
				continue;
			}
			m2 = comp2.getComponentMassCenter(i); // component without parent
			// closestIndex = findClosestPointIndex(m2, comp1, radius);
			closestIndex = findBestScoringComponentIndex(comp2, i, comp1, radius, scoreThreshold);
			// here should be daughter-check, not the same scoring function

			if (closestIndex != -1) { // closest component found, add to graph
				if (comp1.getComponentChildCount(closestIndex) > 1 || comp2.getComponentChildCount(i) == 0) {
					continue; // if closest "parent" already has 2 children, then skip. Or if child component
								// doesn't have any children, i.e. its a dead track. Or if state isn't mitosys
				}
				if (comp1.getComponentChildCount(closestIndex) == 1
						&& comp1.getComponentState(closestIndex) != State.MITOSIS) {
					System.out.println("component with index " + closestIndex + " discarded by state");
					continue; // if closest parent has 1 child but not mitosys, then don't add
				}
				// if closest component in comp1 has 0 children or 1 children
				v1 = new Node(t1, closestIndex);
				v2 = new Node(t2, i);
				System.out.println("Arc made during back tracking: " + v1 + " -- " + v2
						+ " with comp1(closest) child count being " + comp1.getComponentChildCount(closestIndex)
						+ "and comp2 child count being " + comp2.getComponentChildCount(i));
				cellGraph.addArcFromToAddable(v1, v2);
				comp2.setComponentHasParent(i);
				comp1.incComponentChildCount(closestIndex);
			} else {
				// closest component for current component in comp2 was not found
			}
		}
	}

	/*
	 * components list should be filled tracking algorithm: first, find nearest
	 * neighbours going from 0 to T time slice then, back tracking: find nearest
	 * component of i in i-1 that wasn't tracked
	 */
	public void trackComponents(double radius, double radiusBackTracking, int n_lookThroughSlices,
			double scoreThreshold) {
		// 0 -> T tracking

		for (int j = 0; j < n_lookThroughSlices; j++) {
			for (int i = 1; i < componentsList.size(); ++i) {
				if (i + j < componentsList.size())
					findNearestComponents(componentsList.get(i - 1), i - 1, componentsList.get(i + j), i + j,
							radius + j * 10, scoreThreshold);
			}
		}

		// back tracking T -> 0
		for (int j = 0; j < n_lookThroughSlices; j++) {
			for (int i = componentsList.size() - 1; i > 0; --i) {
				if (i - j > 0) {
					findNearestComponentsBackStep(componentsList.get(i), i, componentsList.get(i - 1 - j), i - 1 - j,
							radiusBackTracking + j * 10, 2 * scoreThreshold);
				}
			}
		}
	}

	/*
	 * new version of tracking - in 1 direction, using multislice best score and
	 * second best score
	 */
	public void findBestScoringComponents(ImageComponentsAnalysis comp1, int t1,
			ArrayList<ImageComponentsAnalysis> comp2List, int nSlices, double maxRadius, double scoreThreshold,
			double timeDecayCoefficient) {
		int[] indexes;
		int backBestIndex = -1;
		int slice = -1, index = -1;
		Node v1, v2;
		for (int i = 0; i < comp1.getComponentsCount(); i++) {
			if (comp1.getComponentChildCount(i) > 0 || comp1.getComponentState(i) == State.MITOSIS) {
				continue;
			}
			indexes = findBestScoringComponentIndexMultiSlice(comp1, i, t1, comp2List, nSlices, maxRadius,
					scoreThreshold, timeDecayCoefficient);
			slice = indexes[0];
			index = indexes[1];

			if (index != -1) { // something was found
				// check back-compatabilty
				backBestIndex = findBestScoringComponentIndex(comp2List.get(slice), index, comp1, maxRadius,
						scoreThreshold);
				if (backBestIndex != i)
					continue;
				if (comp2List.get(slice).getComponentHasParent(index)
						|| comp2List.get(slice).getComponentState(index) == State.MITOSIS)
					continue;

				v1 = new Node(t1, i);
				v2 = new Node(slice, index);
				cellGraph.addArcFromToAddable(v1, v2);
				if (nSlices > 1) {
					System.out.println("Arc added during multislice:" + v1 + " to " + v2);
					System.out
							.println("or by adj: " + cellGraph.getNodeIndex(v1) + " to " + cellGraph.getNodeIndex(v2));
					System.out.println("v2 parameters: hasParent=" + comp2List.get(slice).getComponentHasParent(index));
				}
				comp2List.get(slice).setComponentHasParent(index);
				comp1.incComponentChildCount(i);
			} else {

			}
		}
	}

	/*
	 * try multi-slice with extra picking of close components: i.e. when score of
	 * the component is lower than some threshold in the closest slice, then connect
	 * it immediately
	 */
	public void trackComponentsOneAndMultiSlice(double maxRadius, int nSlices, double scoreThreshold,
			double oneSliceScoreThreshold, double timeDecayCoefficient) {
		for (int t = 0; t < componentsList.size() - 1; t++) {
			findBestScoringComponents(componentsList.get(t), t, componentsList, 1, maxRadius, oneSliceScoreThreshold,
					timeDecayCoefficient);
		}

		for (int t = 0; t < componentsList.size() - 1; t++) {
			findBestScoringComponents(componentsList.get(t), t, componentsList, nSlices, maxRadius, scoreThreshold,
					timeDecayCoefficient);
		}

		// for (int t = 0; t < componentsList.size() - 1; t++) {
		// findBestScoringComponents(componentsList.get(t), t, componentsList, nSlices,
		// maxRadius, scoreThreshold*2,
		// 0.1);
		// }
	}

	// separate one-slice tracking from multi-slice, so we can do them at different
	// times (i.e. after mitosis detection)
	public void trackComponentsOneSlice(double maxRadius, double oneSliceScoreThreshold) {
		for (int t = 0; t < componentsList.size() - 1; t++) {
			String log = String.format("One-slice tracking, slice %d %n", t);
			IJ.log(log);
			findBestScoringComponents(componentsList.get(t), t, componentsList, 1, maxRadius, oneSliceScoreThreshold,
					1);
		}
	}

	public void trackComponentsMultiSlice(double maxRadius, int nSlices, double scoreThreshold,
			double timeDecayCoefficient) {
		for (int t = 0; t < componentsList.size() - 1; t++) {
			String log = String.format("Multi-slice tracking, slice %d %n", t);
			IJ.log(log);
			findBestScoringComponents(componentsList.get(t), t, componentsList, nSlices, maxRadius, scoreThreshold,
					timeDecayCoefficient);
		}
	}

	/*
	 * Similar to 'findClosestPoint', returns the component index in comp2, which
	 * has the best score for component i1 in comp1. maxRadius sets the look up
	 * radius for components; if min score is higher than scoreThreshold, then don't
	 * consider it "best", return -1 (not found)
	 */
	private int findBestScoringComponentIndex(ImageComponentsAnalysis comp1, int i1, ImageComponentsAnalysis comp2,
			double maxRadius, double scoreThreshold) {
		int min_i = -1;
		double min_score = Double.MAX_VALUE;
		double score;
		for (int i = 0; i < comp2.getComponentsCount(); i++) {
			score = PenaltyFunction.penalFunctionNN(comp1, i1, comp2, i, maxRadius);
			if (score < min_score) {
				min_score = score;
				min_i = i;
			}
		}
		if (min_score > scoreThreshold)
			return -1;
		return min_i;
	}

	/*
	 * find best point for i1 component in comp1 (which slice is t1) in [t1+1 :
	 * t1+nSlices] components. nSlices is the number of slices to look. For standart
	 * method, nSlices = 1. Score weights for farther time slices is reduced.
	 * Returns int[2], where int[0] is time slice and int[1] is the index. Let's
	 * make it find 2 best options
	 */
	private int[] findBestScoringComponentIndexMultiSlice(ImageComponentsAnalysis comp1, int i1, int t1,
			ArrayList<ImageComponentsAnalysis> comp2List, int nSlices, double maxRadius, double scoreThreshold,
			double timeDecayCoefficient) {
		int[] result = new int[2];
		int firstBestSlice = t1 + 1, secondBestSlice = t1 + 1;
		int firstBestIndex = -1, secondBestIndex = -1;
		int dt;
		double score1 = 100, score2 = 100, score;
		for (int t = t1 + 1; t < t1 + nSlices + 1; t++) {
			if (t < 0 || t >= comp2List.size())
				break;
			dt = t - t1 - 1; // for multiplier coefficient
			for (int i2 = 0; i2 < comp2List.get(t).getComponentsCount(); i2++) {
				score = (1 + dt * timeDecayCoefficient)
						* PenaltyFunction.penalFunctionNN(comp1, i1, comp2List.get(t), i2, maxRadius);
				if (score > scoreThreshold) {
					// if (t1 == 4)
					// System.out.format(
					// "Score of component %d in slice %d and comp %d, t=%d was higher than
					// threshold (score = %f) %n",
					// i1, t1, i2, t, score);
					continue;
				} else {
					if (score < 10)
						System.out.format("Score of component %d in slice %d, t=%d is %f %n", i1, t1, t, score);
				}
				if (score < score1) {
					score2 = score1; // previous minimum is now second-minimum
					score1 = score;
					secondBestSlice = firstBestSlice;
					firstBestSlice = t;
					secondBestIndex = firstBestIndex;
					firstBestIndex = i2;
				} else if (score < score2) {
					score2 = score;
					secondBestSlice = t;
					secondBestIndex = i2;
				}
			}
		}
		result[0] = firstBestSlice;
		result[1] = firstBestIndex;
		return result;
	}

	public void analyzeTracksForMitosisByWhiteBlob(float whiteBlobThreshold) {
		Track tr;
		int startAdjIndex, endAdjIndex;
		int startSlice, endSlice;
		int currAdjIndex, currSlice, currNode, endComponentIndex;
		int startNodeIndex;

		Point center;
		int x0, y0, x1, y1, radius;
		float avrgVal;
		FloatHistogram hist;
		ArrayList<Float> trackValues = new ArrayList<Float>();
		tracks.printTracksInfo();
		ImageProcessor ip;

		for (int i = 0; i < tracks.tracksCount(); i++) {
			tr = tracks.getTrack(i);

			if (tracks.getLength(i) < 2 || tr.isEndedOnMitosis())
				continue;

			startAdjIndex = tr.getStartAdjIndex();
			endAdjIndex = tr.getEndAdjIndex();

			startSlice = cellGraph.getNodeSliceByGlobalIndex(startAdjIndex);
			endSlice = tracks.getLastSliceForTrack(i);
			endComponentIndex = cellGraph.getNodeIndexByGlobalIndex(endAdjIndex);

			if (endSlice > componentsList.size() - 4)
				continue;

			center = componentsList.get(endSlice).getComponentMassCenter(endComponentIndex);

			WhiteBlobsDetection detectionNextSlice = new WhiteBlobsDetection(center.getX(), center.getY(), endSlice + 1, 30,
					tr.getEndAdjIndex(), false, new ArrayList<Integer>(), 0, null);
			detectionNextSlice.fillWithBlobCandidates(componentsList.get(endSlice + 1).getInvertedIntensityImage(), 30);

			if (detectionNextSlice.isBestBlobValueAboveThreshold(whiteBlobThreshold)) {
				tr.setEndedOnMitosys();
				System.out.println("track " + i + " ended on mitosis by bright blob in the next frame");
				continue;
			}

			WhiteBlobsDetection detectionNextNextSlice = new WhiteBlobsDetection(center.getX(), center.getY(), endSlice + 2, 30,
					tr.getEndAdjIndex(), false, new ArrayList<Integer>(), 0, null);
			detectionNextNextSlice.fillWithBlobCandidates(componentsList.get(endSlice + 2).getInvertedIntensityImage(), 30);
			
			if (detectionNextNextSlice.isBestBlobValueAboveThreshold(whiteBlobThreshold)) {
				tr.setEndedOnMitosys();
				System.out.println("track " + i + " ended on mitosis by bright blob in the next next frame");
			}
		}
	}

	public void analyzeTracksForMitosisByAverageIntensity(double mitosisStartIntensityCoefficient) {
		Track tr;
		int startAdjIndex, endIndex;
		int startSlice, endSlice;
		int currAdjIndex, currSlice, currNode;
		int startNodeIndex;

		Point center;
		int x0, y0, x1, y1, radius;
		float avrgVal;
		FloatHistogram hist;
		ArrayList<Float> trackValues = new ArrayList<Float>();
		tracks.printTracksInfo();
		ImageProcessor ip;

		for (int i = 0; i < tracks.tracksCount(); i++) {
			tr = tracks.getTrack(i);
			startAdjIndex = tr.getStartAdjIndex();
			endIndex = tr.getEndAdjIndex();

			startSlice = cellGraph.getNodeSliceByGlobalIndex(startAdjIndex);
			startNodeIndex = cellGraph.getNodeIndexByGlobalIndex(startAdjIndex);

			if (cellGraph.getNodeSliceByGlobalIndex(endIndex) > componentsList.size() - 4)
				continue;

			if (tr.isEndedOnMitosis())
				continue;

			// get histogram for each slice in track and for the next slice
			currAdjIndex = startAdjIndex;
			while (currAdjIndex != -1) {
				currSlice = cellGraph.getNodeSliceByGlobalIndex(currAdjIndex);
				currNode = cellGraph.getNodeIndexByGlobalIndex(currAdjIndex);

				ip = componentsList.get(currSlice).getIntensityImage();
				center = componentsList.get(currSlice).getComponentMassCenter(currNode);
				x0 = componentsList.get(currSlice).getComponentX0(currNode);
				x1 = componentsList.get(currSlice).getComponentX1(currNode);
				y0 = componentsList.get(currSlice).getComponentY0(currNode);
				y1 = componentsList.get(currSlice).getComponentY1(currNode);
				radius = Math.max(x1 - x0, y1 - y0) / 2 + 1;

				hist = new FloatHistogram(ip, 0, 1, (int) center.getX(), (int) center.getY(), radius);
				avrgVal = hist.getAverageValue();
				trackValues.add(avrgVal);
				// System.out.format("%f, ", avrgVal);

				currAdjIndex = cellGraph.getFirstChildByGlobalIndex(currAdjIndex);

				if (currAdjIndex == -1) { // calculate last histogram for next slice with same parameters
					ip = componentsList.get(currSlice + 1).getIntensityImage();
					hist = new FloatHistogram(ip, 0.0f, 1.0f, (int) center.getX(), (int) center.getY(), radius);
					avrgVal = hist.getAverageValue();
					trackValues.add(avrgVal);
				}
			}

			// now analyze trackValues
			System.out.println(
					"Histogram averages for track starting with slice " + startSlice + " and index " + startNodeIndex);
			for (int j = 0; j < trackValues.size(); j++) {
				System.out.format("%f, ", trackValues.get(j));
			}
			System.out.println();

			if (trackValues.size() > 3 && checkEndedOnMitosis(trackValues, (float) mitosisStartIntensityCoefficient)) {
				tr.setEndedOnMitosys();
				System.out.println("track " + i + " ended on mitosis by intensity change ");
			}

			trackValues.clear();
		}
	}

	/*
	 * Returns true if the last difference in intensity values array is the highest
	 * through the array, means that the mitosis started
	 */
	private boolean checkEndedOnMitosis(ArrayList<Float> histAveragesList, float multiplierThreshold) {
		if (histAveragesList.size() < 2)
			return false;
		float maxDiff = Float.MIN_VALUE;
		float diff;
		ArrayList<Float> diffs = new ArrayList<Float>(histAveragesList.size() - 1);

		for (int i = 1; i < histAveragesList.size(); i++) {
			diff = Math.abs(histAveragesList.get(i) - histAveragesList.get(i - 1));
			diffs.add(diff);
			if (diff > maxDiff)
				maxDiff = diff;
		}

		return diffs.get(diffs.size() - 1) > maxDiff * multiplierThreshold;
	}

	/* mitosis tracking */
	public void startMitosisTracking(int radius, double childPenalThreshold) {
		int endSlice;
		int componentIndex;
		Point center;
		WhiteBlobsTracking whiteBlobsTracking = new WhiteBlobsTracking(componentsList, cellGraph, tracks);

		Track tr;
		for (int i = 0; i < tracks.tracksCount(); i++) {
			tr = tracks.getTrack(i);
			if (tr.isEndedOnMitosis()) {
				// do white blob tracking...
				endSlice = cellGraph.getNodeSliceByGlobalIndex(tr.getEndAdjIndex());
				componentIndex = cellGraph.getNodeIndexByGlobalIndex(tr.getEndAdjIndex());

				center = componentsList.get(endSlice).getComponentMassCenter(componentIndex);
				if (endSlice > componentsList.size() - 5) // don't bother with mitosis that started just before the end
															// of the sequence
					continue;
				// create white blob that doesn't need second detection, since its the first
				// mitosis slice
				WhiteBlobsDetection whiteBlob = new WhiteBlobsDetection(center.getX(), center.getY(), endSlice + 1,
						radius, tr.getEndAdjIndex(), false, new ArrayList<Integer>(), 0, null);
				System.out.format("WhiteBlobDetection created in t=%d, at (%f,%f), parent adj is %d %n", endSlice + 1,
						center.getX(), center.getY(), tr.getEndAdjIndex());
				whiteBlobsTracking.addWhiteBlobDetection(endSlice + 1, whiteBlob);
			}
		}

		// now analyze images and white blobs...
		trackWhiteBlobs(whiteBlobsTracking, radius, childPenalThreshold);
	}

	/*
	 * must be called after 'start mitosis tracking', namely after initial
	 * detections are created
	 */
	private void trackWhiteBlobs(WhiteBlobsTracking whiteBlobsTracking, int searchRadius, double childPenalThreshold) {
		// fill initial detections with white blobs
		for (int slice = 0; slice < whiteBlobsTracking.getSlicesCount(); slice++) {
		}

		// add first white blobs
		for (int slice = 0; slice < whiteBlobsTracking.getSlicesCount() - 1; slice++) {
			String log = String.format("Mitosis tracking, slice %d %n", slice);
			IJ.log(log);
			System.out.println();
			System.out.println("--- Mitosis tracking: slice " + slice);
			whiteBlobsTracking.fillSliceDetectionsWithUniqueCandidates(slice,
					componentsList.get(slice).getInvertedIntensityImage());
			whiteBlobsTracking.sortBlobsInDetections(slice);
			// here output candidate components for debugging
			// if (whiteBlobsTracking.hasDetections(slice)) {
			// ImagePlus debugComponents = new ImagePlus(Integer.toString(slice),
			// whiteBlobsTracking.getComponentCandidatesImage(slice));
			// debugComponents.show();
			// }
			System.out.println("--- Mitosis tracking: Filling first Blobs");
			whiteBlobsTracking.fillFirstBlobs(slice);
			System.out.println("--- Mitosis tracking: Filling second Blobs");
			whiteBlobsTracking.fillSecondBlobs(slice, childPenalThreshold);

			// whiteBlobsTracking.fillTrackCandidateIndexes(slice, searchTracksRadius);
		}
	}

	// currently not working: doesn't remove components from graph
	public void clearIsolatedComponents() {
		for (int i = 0; i < componentsList.size(); i++)
			for (int j = 0; j < componentsList.get(i).getComponentsCount(); j++) {
				if (componentsList.get(i).getComponentHasParent(j)
						|| componentsList.get(i).getComponentChildCount(j) > 0)
					continue;

				componentsList.get(i).removeComponentByIndex(j);
			}
	}

	/* draws cellGraph as tracks on ip */
	public void drawTracksIp(ImageProcessor ip) {
		ArrayList<Arc> arcs = cellGraph.getArcs();
		int i0, i1, t0, t1;
		Node n0, n1;
		Point p0, p1;
		int x0, x1, y0, y1;
		for (int k = 0; k < arcs.size(); k++) {
			n0 = arcs.get(k).getFromNode();
			n1 = arcs.get(k).getToNode();
			i0 = n0.get_i();
			i1 = n1.get_i();
			t0 = n0.get_t();
			t1 = n1.get_t();

			p0 = componentsList.get(t0).getComponentMassCenter(i0);
			p1 = componentsList.get(t1).getComponentMassCenter(i1);

			x0 = (int) p0.getX();
			y0 = (int) p0.getY();
			x1 = (int) p1.getX();
			y1 = (int) p1.getY();
			ImageFunctions.drawX(ip, x0, y0);
			ImageFunctions.drawX(ip, x1, y1);
			ImageFunctions.drawLine(ip, x0, y0, x1, y1);
		}
	}

	/* draws arcs on ip */
	public void drawTracksIp(ImageProcessor ip, ArrayList<Arc> arcs) {
		int i0, i1, t0, t1;
		Node n0, n1;
		Point p0, p1;
		int x0, x1, y0, y1;
		for (int k = 0; k < arcs.size(); k++) {
			n0 = arcs.get(k).getFromNode();
			n1 = arcs.get(k).getToNode();
			i0 = n0.get_i();
			i1 = n1.get_i();
			t0 = n0.get_t();
			t1 = n1.get_t();

			p0 = componentsList.get(t0).getComponentMassCenter(i0);
			p1 = componentsList.get(t1).getComponentMassCenter(i1);

			x0 = (int) p0.getX();
			y0 = (int) p0.getY();
			x1 = (int) p1.getX();
			y1 = (int) p1.getY();
			ImageFunctions.drawX(ip, x0, y0);
			ImageFunctions.drawX(ip, x1, y1);
			ImageFunctions.drawLine(ip, x0, y0, x1, y1);
		}
	}

	/* draw arcs in colorProcessor */
	public void drawTracksColor(ColorProcessor cim, ArrayList<Arc> arcs, Color color) {
		int i0, i1, t0, t1;
		Node n0, n1;
		Point p0, p1;
		int x0, x1, y0, y1;
		cim.setColor(color);
		for (int k = 0; k < arcs.size(); k++) {
			n0 = arcs.get(k).getFromNode();
			n1 = arcs.get(k).getToNode();
			i0 = n0.get_i();
			i1 = n1.get_i();
			t0 = n0.get_t();
			t1 = n1.get_t();

			p0 = componentsList.get(t0).getComponentMassCenter(i0);
			p1 = componentsList.get(t1).getComponentMassCenter(i1);

			x0 = (int) p0.getX();
			y0 = (int) p0.getY();
			x1 = (int) p1.getX();
			y1 = (int) p1.getY();
			cim.drawDot(x0, y0);
			cim.drawDot(x1, y1);
			cim.drawLine(x0, y0, x1, y1);
		}
	}

	/* draws tracking result on each slice and return the result */
	ImagePlus drawTracksImagePlus(ImagePlus image) {
		ImagePlus result = image.createImagePlus();

		ImageStack stack = image.getStack();

		System.out.println(stack.getSize());
		System.out.println(image.getNSlices());
		stack.setProcessor(image.getStack().getProcessor(1), 1);

		ImageProcessor ip;
		for (int i = 2; i <= image.getNSlices(); i++) { // slices are from 1 to n_slices
			ip = image.getStack().getProcessor(i).duplicate();
			drawTracksIp(ip, cellGraph.getArcsBeforeTimeSlice(i - 1)); // but time is from 0 to nslices-1
			stack.setProcessor(ip, i);
		}

		result.setStack(stack);
		return result;
	}

	/* doesn't work correctly */
	ImagePlus colorTracks(ImagePlus image) {
		ColorPicker colorPicker = new ColorPicker();
		ImageStack stack = new ImageStack(image.getWidth(), image.getHeight(), image.getNSlices());
		System.out.println(image.getNSlices());
		ColorProcessor cim;

		stack.setProcessor(image.getStack().getProcessor(1).convertToColorProcessor(), 1);
		for (int i = 2; i <= image.getNSlices(); i++) { // slices are from 1 to n_slices
			cim = image.getStack().getProcessor(i).duplicate().convertToColorProcessor();
			drawTracksColor(cim, cellGraph.getArcsBeforeTimeSlice(i - 1), colorPicker.nextColor());
			stack.setProcessor(cim, i);
		}
		ImagePlus result = new ImagePlus("tracks", stack);

		return result;
	}

	// following 3 functions are for getting TRA metrics from TrackMate's results
	// (ROIs)

	/*
	 * returns component index in comp2 which has the same intesity (or -1, if not
	 * found)
	 */
	public int findComponentIndexByIntensity(ImageComponentsAnalysis comp1, int i1, ImageComponentsAnalysis comp2) {

		for (int i2 = 0; i2 < comp2.getComponentsCount(); i2++) {
			if (comp2.getComponentDisplayIntensity(i2) == comp1.getComponentDisplayIntensity(i1))
				return i2;
		}
		return -1;
	}

	public void trackComponentsIntensity(ImageComponentsAnalysis comp1, int t1, ImageComponentsAnalysis comp2, int t2) {
		int closestIndex;
		Node v1, v2;
		for (int i = 0; i < comp2.getComponentsCount(); i++) {
			if (comp2.getComponentHasParent(i)) {
				continue;
			}

			closestIndex = findComponentIndexByIntensity(comp2, i, comp1);

			if (closestIndex != -1) { // closest component found, add to graph
				if (comp1.getComponentChildCount(closestIndex) == 1
						&& comp1.getComponentState(closestIndex) != State.MITOSIS) {
					System.out.println("component with index " + closestIndex + " discarded by state");
					continue; // if closest parent has 1 child but not mitosys, then don't add
				}
				// if closest component in comp1 has 0 children or 1 children
				v1 = new Node(t1, closestIndex);
				v2 = new Node(t2, i);
				// System.out.println("Arc made during back tracking: " + v1 + " -- " + v2
				// + " with comp1(closest) child count being " +
				// comp1.getComponentChildCount(closestIndex)
				// + "and comp2 child count being " + comp2.getComponentChildCount(i));
				cellGraph.addArcFromToAddable(v1, v2);
				comp2.setComponentHasParent(i);
				comp1.incComponentChildCount(closestIndex);
			}
		}
	}

	public void trackComponentsTRAresults(int n_lookThroughSlices) {
		for (int j = 0; j < n_lookThroughSlices; j++) {
			for (int i = 1; i < componentsList.size(); ++i) {
				if (i + j < componentsList.size())
					trackComponentsIntensity(componentsList.get(i - 1), i - 1, componentsList.get(i + j), i + j);
			}
		}
	}

}
