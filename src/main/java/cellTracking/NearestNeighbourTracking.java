package cellTracking;

import java.util.ArrayList;

import graph.Arc;
import graph.Graph;
import graph.Node;
import histogram.FloatHistogram;
import ij.process.ImageProcessor;
import ij.ImagePlus;
import ij.ImageStack;

import point.Point;
import tracks.Track;
import tracks.Tracks;

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
		//this.slicesCount = slicesCount;
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
			if (comp1.getComponentChildCount(i) > 0) {
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
				if (comp2List.get(slice).getComponentHasParent(index))
					continue;

				v1 = new Node(t1, i);
				v2 = new Node(slice, index);
				cellGraph.addArcFromToAddable(v1, v2);
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
	public void trackComponentsMultiSlice(double maxRadius, int nSlices, double scoreThreshold,
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

	/*
	 * returns index of the component in comp, which mass center is the closest to p
	 */
	private int findClosestPointIndex(Point p, ImageComponentsAnalysis comp, double radius) {
		int min_i = -1;
		double min_dist = Double.MAX_VALUE;
		double dist;
		for (int i = 0; i < comp.getComponentsCount(); i++) {
			dist = Point.dist(p, comp.getComponentMassCenter(i));
			if (dist < radius && dist < min_dist) {
				min_dist = dist;
				min_i = i;
			}
		}
		return min_i;
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
			score = penalFunctionNN(comp1, i1, comp2, i, maxRadius);
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
				score = (1 + dt * timeDecayCoefficient) * penalFunctionNN(comp1, i1, comp2List.get(t), i2, maxRadius);
				// System.out.format("Score of component %d in slice %d, t=%d is %f %n", i1, t1,
				// t, score);
				if (score > scoreThreshold) {
					// if (t1 ==4) System.out.format("Score of component %d in slice %d and comp %d,
					// t=%d was higher than threshold (score = %f) %n", i1, t1, i2, t, score);
					continue;
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

	/*
	 * calculates the penalty function between component with index=i1 in comp1 and
	 * i2 in comp. (So the less it is, the better, the more the chance they should
	 * be connected) Curent problem with it: only counts for slice-slice, bad for
	 * slice-multislice mb change distance for overlap
	 */
	private double penalFunctionNN(ImageComponentsAnalysis comp1, int i1, ImageComponentsAnalysis comp2, int i2,
			double maxRadius) {
		Point m1 = comp1.getComponentMassCenter(i1);
		Point m2 = comp2.getComponentMassCenter(i2);
		double dist = Point.dist(m1, m2);
		if (dist > maxRadius)
			return 100;

		int area1, area2;
		float circ1, circ2;
		float intensity1, intensity2;
		double p_circ, p_area, p_int, p_dist;
		area1 = comp1.getComponentArea(i1);
		circ1 = comp1.getComponentCircularity(i1);
		intensity1 = comp1.getComponentAvrgIntensity(i1);
		area2 = comp2.getComponentArea(i2);
		circ2 = comp2.getComponentCircularity(i2);
		intensity2 = comp2.getComponentAvrgIntensity(i2);
		p_area = normVal(area1, area2);
		p_circ = normVal(circ1, circ2);
		p_int = normVal(intensity1, intensity2);

		int minDist_in2 = findClosestPointIndex(m1, comp2, maxRadius);
		int minDist_in1 = findClosestPointIndex(m2, comp1, maxRadius);
		double minDist1 = Double.MAX_VALUE, minDist2 = Double.MAX_VALUE;

		// if closest component was not found closer than maxRadius, then let minDist be
		// huge, so score will be =1
		if (minDist_in2 != -1)
			minDist1 = Point.dist(m1, comp2.getComponentMassCenter(minDist_in2));
		if (minDist_in1 != -1)
			minDist2 = Point.dist(m2, comp1.getComponentMassCenter(minDist_in1));

		if (minDist_in1 == -1 && minDist_in2 == -1)
			p_dist = 1;
		else
			p_dist = normVal(Math.min(minDist1, minDist2), dist);

		// weights for area,circularity, avrg intensity and distance values
		double w_a = 0.8;
		double w_c = 0.2;
		double w_i = 0.4;
		double w_d = 1;
		double w_sum = w_a + w_c + w_i + w_d;
		w_a /= w_sum; // normalize value to [0,1]
		w_c /= w_sum;
		w_i /= w_sum;
		w_d /= w_sum;
		double penal = w_a * p_area + w_c * p_circ + w_i * p_int + w_d * p_dist;
		// System.out.format("Score between component with area %d, intensity %f, circ
		// %f %n", area1, intensity1, circ1);
		// System.out.format("and component with area %d, intensity %f, circ %f %n",
		// area2, intensity2, circ2);
		// System.out.format("with dist between them %f and min dist %f is %f %n%n",
		// dist, Math.min(minDist1, minDist2),
		// penal);
		return penal;
	}

	/* gets difference between value in [0,1] */
	private double normVal(double v1, double v2) {
		double v = Math.abs(v1 - v2) / Math.sqrt(v1 * v1 + v2 * v2);
		return v;
	}

	public void analyzeTracksForMitosis() {
		Track tr;
		int startIndex, endIndex;
		int startSlice, endSlice;
		int currAdjIndex, currSlice, currNode;
		int startNodeIndex;

		Point center;
		int x0, y0, x1, y1, radius;
		float avrgVal;
		FloatHistogram hist;
		ArrayList<Float> trackValues = new ArrayList<Float>();
		tracks.printTracksInfo();

		for (int i = 0; i < tracks.tracksCount(); i++) {
			tr = tracks.getTrack(i);
			startIndex = tr.getStartAdjIndex();
			endIndex = tr.getEndAdjIndex();

			startSlice = cellGraph.getNodeSliceByGlobalIndex(startIndex);
			startNodeIndex = cellGraph.getNodeIndexByGlobalIndex(startIndex);

			if (cellGraph.getNodeSliceByGlobalIndex(endIndex) > componentsList.size() - 4)
				continue;

			// get histogram for each slice in track and for the next slice
			currAdjIndex = startIndex;
			while (currAdjIndex != -1) {
				currSlice = cellGraph.getNodeSliceByGlobalIndex(currAdjIndex);
				currNode = cellGraph.getNodeIndexByGlobalIndex(currAdjIndex);

				ImageProcessor ip = componentsList.get(currSlice).getIntensityImage();
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

			// now analyse trackValues
			System.out.println(
					"Histogram averages for track starting with slice " + startSlice + " and index " + startNodeIndex);
			for (int j = 0; j < trackValues.size(); j++) {
				System.out.format("%f, ", trackValues.get(j));
			}
			System.out.println();

			if (trackValues.size() > 3 && checkEndedOnMitosis(trackValues))
				tr.setEndedOnMitosys();

			trackValues.clear();
		}
	}

	/*
	 * Returns true if the last difference in intensity values array is the highest
	 * through the array Means that the mitosis started
	 */
	private boolean checkEndedOnMitosis(ArrayList<Float> histAveragesList) {
		if (histAveragesList.size() < 2)
			return false;
		float maxDiff = Float.MIN_VALUE;
		float diff;
		ArrayList<Float> diffs = new ArrayList<Float>(histAveragesList.size() - 1);

		for (int i = 1; i < histAveragesList.size(); i++) {
			diff = histAveragesList.get(i) - histAveragesList.get(i - 1);
			diffs.add(diff);
			if (diff > maxDiff)
				maxDiff = diff;
		}

		return maxDiff == diffs.get(diffs.size() - 1);
	}

	/* mitosis tracking */
	private void startMitosisTracking(int radius) {
		int endSlice;
		int nodeIndex;
		Point center;
		WhiteBlobsTracking whiteBlobsTracking = new WhiteBlobsTracking(slicesCount);

		Track tr;
		for (int i = 0; i < tracks.tracksCount(); i++) {
			tr = tracks.getTrack(i);
			if (tr.ifEndedOnMitosis()) {
				// do white blob tracking...
				endSlice = cellGraph.getNodeSliceByGlobalIndex(tr.getEndAdjIndex());
				nodeIndex = cellGraph.getNodeIndexByGlobalIndex(tr.getEndAdjIndex());

				center = componentsList.get(currSlice).getComponentMassCenter(nodeIndex);
				if (endSlice > componentsList.size() - 5) // dont bother with mitosys that started just before the end
															// of the sequence
					continue;
				WhiteBlobsDetection whiteBlob = new WhiteBlobsDetection(i, center.getX(), center.getY(), radius);
				whiteBlobsTracking.addWhiteBlob(endSlice + 1, whiteBlob);
			}
		}
		
		// now analyse images and white blobs... 
		trackWhiteBlobs(whiteBlobsTracking);
	}
	
	public void trackWhiteBlobs(WhiteBlobsTracking whiteBlobsTracking) {
		
	}

	/*
	 * looks for the closest white blob in the slice in ROI centered in 'center' and
	 * 'radius'
	 */
	public void findClosestWhiteBlob(int slice, Point center, int radius) {

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
	
	// following 3 functions are for getting TRA metrics from TrackMate's results (ROIs)
	
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
//				System.out.println("Arc made during back tracking: " + v1 + " -- " + v2
//						+ " with comp1(closest) child count being " + comp1.getComponentChildCount(closestIndex)
//						+ "and comp2 child count being " + comp2.getComponentChildCount(i));
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
