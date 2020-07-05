package cellTracking;

import java.util.ArrayList;
import java.util.Collections;

import graph.Graph;
import point.Point;
import tracks.TrackAdj;
import tracks.TracksAdj;

/**
 * Class with methods to track division events
 * 
 */
public class DivisionTracking {
	//private ArrayList<ArrayList<WhiteBlobsDetection>> detectionsLists;
	private ArrayList<ImageComponentsAnalysis> componentsList;
	private Graph g;
	private TracksAdj tracks;
	
	public DivisionTracking() {
		
	}
	
	public void setTrackingInfo(TracksAdj tracks, Graph g, ArrayList<ImageComponentsAnalysis> compList) {
		this.tracks = tracks;
		componentsList = compList;
		this.g = g;
	}

	/**
	 * tracking that connects 1 component to 2 searching them before the slice and
	 * after.
	 * @param track which last components is expected to be dividing
	 * @param searchTracksRadius radius to search daughter tracks
	 * @param minSlicesDifference
	 * @param maxSlicesDifference
	 */
	public void simpleDivisionTracking(TrackAdj track, int searchTracksRadius, int minSlicesDifference,
			int maxSlicesDifference, float timeCoef,
			ArrayList<ComponentStateLink> allowedConnectionsParentChild, ArrayList<ComponentStateLink> allowedConnectionsChildren) {
		
		int endIndex = track.getEndAdjIndex();		
		int parentComponentSlice = g.getNodeSliceByGlobalIndex(endIndex);
		int parentComponentIndex = g.getNodeIndexByGlobalIndex(endIndex);		
		
		//dont start division tracking for tracks that ended just before last frame
		if (parentComponentSlice >= componentsList.size() - 1)
			return;
		Point parentComponentCenter = componentsList.get(parentComponentSlice).getComponentMassCenter(parentComponentIndex);
		
		int componentIndex;
		int trackStartingSlice;
		int dSlice;
		Point mc;
		ArrayList<Integer> possibleTracksIndexes = new ArrayList<Integer>(10);
		double minDistance = Double.MAX_VALUE, dist;

		for (int i = 0; i < tracks.tracksCount(); i++) {
			if (tracks.isTrackWhiteBlobParent(i))
				continue;
			trackStartingSlice = tracks.getStartSliceForTrack(i);
			dSlice = parentComponentSlice - trackStartingSlice; 
			// would need to remove dSlice + 1 components
			if (dSlice + 1 >= tracks.getLength(i)) 
				continue;
			if (trackStartingSlice - parentComponentSlice < minSlicesDifference)
				continue;
			if (trackStartingSlice - parentComponentSlice > maxSlicesDifference)
				continue;
//			if (trackStartingSlice < parentComponentSlice - minSlicesDifference
//					|| trackStartingSlice >= parentComponentSlice + maxSlicesDifference)
//				continue;

			componentIndex = tracks.getFirstComponentIndexForTrack(i);
			if (componentsList.get(trackStartingSlice).getComponentHasParent(componentIndex))
				continue;

			mc = componentsList.get(trackStartingSlice).getComponentMassCenter(componentIndex);

			dist = mc.distTo(parentComponentCenter);
			boolean isAllowedStates = false;
			for (ComponentStateLink statelink: allowedConnectionsParentChild) {
				boolean isOk = statelink.checkStates(componentsList.get(parentComponentSlice), parentComponentIndex,
						componentsList.get(trackStartingSlice), componentIndex);
				isAllowedStates = isAllowedStates || isOk;
			}
			if (dist <= searchTracksRadius) {
				if (dist < minDistance) // also find min distance for calculating penal function
					minDistance = dist;
				if (isAllowedStates) {
					possibleTracksIndexes.add(i);
				}
			}
		}

//		System.out.println("possibleTrackIndexes: " + possibleTracksIndexes.toString());

		// in case no tracks were found
		if (possibleTracksIndexes.isEmpty())
			return;

		// !!! - the detection has no components in the image, because it detected track
		// end. So we must connect childs to its parent node, which is .getNodeIndex()

		// in case only 1 track was found
		if (possibleTracksIndexes.size() == 1) {
			int trIndex = possibleTracksIndexes.get(0);

			int dSlice1 = parentComponentSlice - tracks.getStartSliceForTrack(trIndex);
//			System.out.println("slice differences is: " + dSlice1);
			// if in the same slice then dSlice=0, should remove first component
			if (dSlice1 >= 0) {
				if (!tracks.disconnectFirstComponentsFromTrack(trIndex, dSlice1 + 1)) {
					//trying to remove the whole track, do something
					return;
				}
			}

			int nodeToIndex = tracks.getStartAdjIndexForTrack(trIndex);
			g.addArcFromIndexToIndexAddable(endIndex, nodeToIndex);
			tracks.setTrackAsWhiteBlobParent(trIndex);

			componentsList.get(parentComponentSlice).incComponentChildCount(parentComponentIndex);
			componentsList.get(tracks.getStartSliceForTrack(trIndex))
					.setComponentHasParent(tracks.getFirstComponentIndexForTrack(trIndex));
			
			System.out.println("Only 1 child; added arc from " + endIndex + " to "
					+ tracks.getStartAdjIndexForTrack(trIndex));
			return;
		}

		ArrayList<Double> tracksPenalScores = new ArrayList<Double>(possibleTracksIndexes.size());
		double score;
		int blobComponentIndex = parentComponentIndex;

		// first, fill track scores
		for (int i = 0; i < possibleTracksIndexes.size(); i++) {
			componentIndex = tracks.getFirstComponentIndexForTrack(possibleTracksIndexes.get(i));
			trackStartingSlice = tracks.getStartSliceForTrack(possibleTracksIndexes.get(i));
			
			int sliceDelta = trackStartingSlice - parentComponentSlice;
			score = timeCoef * sliceDelta + penalFunction1to1(componentsList.get(parentComponentSlice), blobComponentIndex,
					componentsList.get(trackStartingSlice), componentIndex, minDistance);

			System.out.println("Parent-child score between parent track ended in slice "
					+ parentComponentSlice + " and candidate track " + possibleTracksIndexes.get(i) + " starting in slice " + trackStartingSlice + 
					" with X0 at " + componentsList.get(trackStartingSlice).getComponentX0(componentIndex) + " is " + score);
			
			// need to go through each track here for back-checking
//			for (int tri = 0; tri < tracks.tracksCount(); ++tri) {
//				TrackAdj tr = tracks.getTrack(i);
//				int backBestIndex = NearestNeighbourTracking.findBestScoringComponentIndex(componentsList.get(trackStartingSlice), componentIndex, 
//						componentsList.get(parentComponentSlice),
//						searchTracksRadius,
//						5, allowedConnectionsParentChild);
//				if (backBestIndex != blobComponentIndex)
//					score = score + 2;
//			}
			State state1, state2;
			state1 = componentsList.get(parentComponentSlice).getComponentState(blobComponentIndex);
			state2 = componentsList.get(trackStartingSlice).getComponentState(componentIndex);
			// improve score for pair mit_start -> mit_end
//			if (state1 == State.MITOSIS_START && state2 == State.MITOSIS_START)
//				score = score + 100;
//			if (state1 == State.MITOSIS_START && state2 == State.NORMAL)
//				score = score + 0.1;
//			if (state1 == State.MITOSIS_START && state2 == State.MITOSIS_END)
//				score = score - 0.75;
//			if (state1 == State.NORMAL && state2 == State.MITOSIS_START)
//				score = score + 100;
//			if (state1 == State.NORMAL && state2 == State.NORMAL)
//				score = score;
//			if (state1 == State.NORMAL && state2 == State.MITOSIS_END)
//				score = score + 0.5;
//			if (state1 == State.MITOSIS_END && state2 == State.MITOSIS_START)
//				score = score + 100;
//			if (state1 == State.MITOSIS_END && state2 == State.NORMAL) // ???? not sure what to do here
//				score = score + 0.01;
//			if (state1 == State.MITOSIS_END && state2 == State.MITOSIS_END)
//				score = score + 100;
			tracksPenalScores.add(score);
		}
//		System.out.println("score unsorted: " + tracksPenalScores.toString());

		// then sort them
		int currBestIndex = 0;
		double currMinScore = Double.MAX_VALUE;
		for (int i = 0; i < possibleTracksIndexes.size(); i++) {
			currBestIndex = i;
			currMinScore = tracksPenalScores.get(i);
			for (int j = i + 1; j < possibleTracksIndexes.size(); j++) {
				if (tracksPenalScores.get(j) < currMinScore) {
					currMinScore = tracksPenalScores.get(j);
					currBestIndex = j;
				}
			}
			// set best to position i
			Collections.swap(possibleTracksIndexes, i, currBestIndex);
			Collections.swap(tracksPenalScores, i, currBestIndex);
		}

//		System.out.println("score sorted: " + tracksPenalScores.toString());
//		System.out.println("track indexes sorted: " + possibleTracksIndexes.toString());

		// now find best pair that will become children
		// only look at no more than 5 best candidates

		double bestPairScore = Double.MAX_VALUE;
		int bestIndex1 = 0, bestIndex2 = 1;
		int compIndex1, compIndex2, trSlice1, trSlice2;

		for (int i = 0; i < possibleTracksIndexes.size() && i < 5; i++) {
			compIndex1 = tracks.getFirstComponentIndexForTrack(possibleTracksIndexes.get(i));
			trSlice1 = tracks.getStartSliceForTrack(possibleTracksIndexes.get(i));

			for (int j = i + 1; j < possibleTracksIndexes.size() && j < 5; j++) {
				compIndex2 = tracks.getFirstComponentIndexForTrack(possibleTracksIndexes.get(j));
				trSlice2 = tracks.getStartSliceForTrack(possibleTracksIndexes.get(j));

				score = calculateChildPenalScore(trSlice1, compIndex1, trSlice2, compIndex2,
						parentComponentCenter, parentComponentSlice, minDistance);
				State state1,state2;
				state1 = componentsList.get(trSlice1).getComponentState(compIndex1);
				state2 = componentsList.get(trSlice2).getComponentState(compIndex2);
				// improve score greatly if both children are mitosis end
//				if (state1==State.MITOSIS_END && state2==State.MITOSIS_END)
//					score = score - 0.75;
//				if (state1 == State.MITOSIS_START || state2 == State.MITOSIS_START)
//					score = score + 100;
				System.out.println("Child score between tracks " + possibleTracksIndexes.get(i) + " and "
						+ possibleTracksIndexes.get(j) + " is " + score);
				if (score < bestPairScore) {
					bestPairScore = score;
					bestIndex1 = i;
					bestIndex2 = j;
				}
			}
		}

		int bestTrackIndex1 = possibleTracksIndexes.get(bestIndex1);
		int bestTrackIndex2 = possibleTracksIndexes.get(bestIndex2);

		System.out.println("best track index 1: " + bestTrackIndex1 + ", best track index 2: " + bestTrackIndex2);

		// clear first components in these tracks if they started before or in the same
		// slice
		int dSlice1 = parentComponentSlice - tracks.getStartSliceForTrack(bestTrackIndex1);
		int dSlice2 = parentComponentSlice - tracks.getStartSliceForTrack(bestTrackIndex2);
		System.out.println("slice differences are: " + dSlice1 + ", " + dSlice2);
		// if in the same slice then dSlice=0, should remove first component
		
		// try removing first components so that daughter tracks start in the same slice
//		int maxSliceDif = dSlice1;
//		if (dSlice2<maxSliceDif)
//			maxSliceDif = dSlice2;
//
//		if (maxSliceDif >= 0) {
//			tracks.disconnectFirstComponentsFromTrack(bestTrackIndex1, maxSliceDif + 1);
//			tracks.disconnectFirstComponentsFromTrack(bestTrackIndex2, maxSliceDif + 1);
//		}
		
//		we should disconnect components from track that starts earlier to the part their start at the same slice
		int sliceDif = tracks.getStartSliceForTrack(bestTrackIndex1) - tracks.getStartSliceForTrack(bestTrackIndex2);
		if (sliceDif > 0) { 
//			track2 started earlier, disconnect components from it
			tracks.disconnectFirstComponentsFromTrack(bestTrackIndex2, sliceDif);
		}
		else if (sliceDif<0) {
//			track1 started earlier
			tracks.disconnectFirstComponentsFromTrack(bestTrackIndex1, -sliceDif);
		}
		
//		if (dSlice1 >= 0) 
//			tracks.disconnectFirstComponentsFromTrack(bestTrackIndex1, dSlice1 + 1);
//		if (dSlice2 >= 0)
//			tracks.disconnectFirstComponentsFromTrack(bestTrackIndex2, dSlice2 + 1);

		// now connect them to graph
		int nodeToIndex1 = tracks.getStartAdjIndexForTrack(bestTrackIndex1);
		int nodeToIndex2 = tracks.getStartAdjIndexForTrack(bestTrackIndex2);
		g.addArcFromIndexToIndexAddable(endIndex, nodeToIndex1);
		g.addArcFromIndexToIndexAddable(endIndex, nodeToIndex2);
		tracks.setTrackAsWhiteBlobParent(bestTrackIndex1);
		tracks.setTrackAsWhiteBlobParent(bestTrackIndex2);

		componentsList.get(parentComponentSlice).incComponentChildCount(parentComponentIndex);
		componentsList.get(tracks.getStartSliceForTrack(bestTrackIndex1))
				.setComponentHasParent(tracks.getFirstComponentIndexForTrack(bestTrackIndex1));

		componentsList.get(parentComponentSlice).incComponentChildCount(parentComponentIndex);
		componentsList.get(tracks.getStartSliceForTrack(bestTrackIndex2))
				.setComponentHasParent(tracks.getFirstComponentIndexForTrack(bestTrackIndex2));

		System.out.println("Added arc from " + endIndex + " to " + nodeToIndex1);
		System.out.println("Added arc from " + endIndex + " to " + nodeToIndex2);
	}
	
	/* calculates penal score (the less, the better) */
	private double penalFunction1to1(ImageComponentsAnalysis comp1, int i1, ImageComponentsAnalysis comp2, int i2,
			double minDistance) {
		Point m1 = comp1.getComponentMassCenter(i1);
		Point m2 = comp2.getComponentMassCenter(i2);
		double dist = Point.dist(m1, m2);

		int area1, area2;
		float intensity1, intensity2;
		double p_area, p_int, p_dist;
		area1 = comp1.getComponentArea(i1);
		intensity1 = comp1.getComponentAvrgIntensity(i1);
		area2 = comp2.getComponentArea(i2);
		intensity2 = comp2.getComponentAvrgIntensity(i2);
		p_area = ImageComponentsAnalysis.normVal(area1, area2);
		p_int = ImageComponentsAnalysis.normVal(intensity1, intensity2);

		p_dist = ImageComponentsAnalysis.normVal(minDistance, dist);

		// weights for area, avrg intensity and distance values
		double w_a = 0.4;
		double w_i = 0.4;
		double w_d = 1.2;
		double w_sum = w_a + w_i + w_d;
		w_a /= w_sum; // normalize value to [0,1]
		w_i /= w_sum;
		w_d /= w_sum;
		double penal = w_a * p_area + w_i * p_int + w_d * p_dist;
		// System.out.format("Score between component with area %d, intensity %f, circ
		// %f %n", area1, intensity1, circ1);
		// System.out.format("and component with area %d, intensity %f, circ %f %n",
		// area2, intensity2, circ2);
		// System.out.format("with dist between them %f and min dist %f is %f %n%n",
		// dist, Math.min(minDist1, minDist2),
		// penal);
		return penal;
	}
	
	/* calculates penal score for child components (the less, the better) */
	public double calculateChildPenalScore(int slice1, int index1, int slice2, int index2, Point parentCenterPoint, int parentSlice,
			double minDistance) {
		// take into account avrg intensity, size, distance from parent center
		double c_intens_diff, c_size_diff, c_diff_distance, c_dist, c_time_to_parent, c_children_time_difference; // coefficients
		c_intens_diff = 0.1; // for intensity of the child blobs
		c_size_diff = 0.1; // for size of child blobs
		c_diff_distance = 0.1; // for difference in distance between child blobs and parent
		c_dist = 1.2;
		c_time_to_parent = 2;
		c_children_time_difference = 1.5;
		

		double int1, int2, size1, size2, dist1, dist2;
		int1 = componentsList.get(slice1).getComponentAvrgIntensity(index1);
		int2 = componentsList.get(slice2).getComponentAvrgIntensity(index2);

		size1 = componentsList.get(slice1).getComponentArea(index1);
		size2 = componentsList.get(slice2).getComponentArea(index2);

		dist1 = Point.dist(componentsList.get(slice1).getComponentMassCenter(index1), parentCenterPoint);
		dist2 = Point.dist(componentsList.get(slice2).getComponentMassCenter(index2), parentCenterPoint);

		//normVal calculates score based on difference rather thatn on actual distance
		
		// difference in intensity - ok
		double intens_diff_score = ImageComponentsAnalysis.normVal(int1, int2);
		
		//difference in size - ok
		double size_diff_score = ImageComponentsAnalysis.normVal(size1, size2);
		
		//difference in distance
		double dist_difference_score = ImageComponentsAnalysis.normVal(dist1, dist2);
		
		double dist_to_parent_score = ImageComponentsAnalysis.normVal(dist1, minDistance) + ImageComponentsAnalysis.normVal(dist2, minDistance);

		// if slice2 is in the same slice as slice1, then time_penal should be zero
		double time_children_difference_penal = ImageComponentsAnalysis.normVal(slice2, slice1);
		//also we dont take into account the time difference between childs and parent
		 
		double time_to_parent_penal = ImageComponentsAnalysis.normVal(slice1, parentSlice) + ImageComponentsAnalysis.normVal(slice2, parentSlice);
		double score = 
				c_intens_diff * intens_diff_score + 
				c_size_diff * size_diff_score + 
				c_diff_distance * dist_difference_score + 
				c_dist * dist_to_parent_score + 
				c_time_to_parent * time_to_parent_penal + 
				c_children_time_difference * time_children_difference_penal;
		return  score;
	}
}
