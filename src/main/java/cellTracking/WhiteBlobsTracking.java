package cellTracking;

import java.util.ArrayList;
import java.util.Collections;
import javax.security.auth.DestroyFailedException;

import blob_detection.WhiteBlobsComponents;
import graph.Graph;
import graph.Node;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import point.Point;
import point.PointWithScale;
import tracks.Tracks;

/* class for mitosis tracking for the whole sequence. It is wrapping a list of list, where outer list represents each slice, and inner list contains
 * white blob detections in this slice. 
 * Also contains references for components list and tracking graph (from NNR tracking class) for adding and tracking new components */
public class WhiteBlobsTracking {
	private ArrayList<ArrayList<WhiteBlobsDetection>> detectionsLists;
	private ArrayList<ImageComponentsAnalysis> componentsList;
	private Graph g;
	private Tracks tracks;

	public WhiteBlobsTracking(ArrayList<ImageComponentsAnalysis> componentsList, Graph trackingGraph,
			Tracks tracksWithoutMitosis) {
		detectionsLists = new ArrayList<ArrayList<WhiteBlobsDetection>>(componentsList.size());
		this.componentsList = componentsList;
		g = trackingGraph;
		tracks = tracksWithoutMitosis;
		for (int i = 0; i < componentsList.size(); i++) {
			detectionsLists.add(new ArrayList<WhiteBlobsDetection>());
		}
	}

	public int getSlicesCount() {
		return detectionsLists.size();
	}

	public int getDetectionsCount(int slice) {
		return detectionsLists.get(slice).size();
	}

	public boolean hasDetections(int slice) {
		return detectionsLists.get(slice).size() != 0;
	}

	public void addWhiteBlobDetection(int slice, WhiteBlobsDetection detection) {
		detectionsLists.get(slice).add(detection);
	}

	public WhiteBlobsDetection getDetection(int slice, int index) {
		return detectionsLists.get(slice).get(index);
	}

	public void fillAllSliceDetectionsWithCandidates(int slice, ImageProcessor image, int searchRadius) {
		for (int index = 0; index < detectionsLists.get(slice).size(); index++) {
			fillWhiteBlobsDetectionWithBlobCandidates(slice, index, image, searchRadius);
		}
	}
	
	public void fillSliceDetectionsWithUniqueCandidates(int slice, ImageProcessor image) {
		WhiteBlobsComponents whiteBlobs = new WhiteBlobsComponents(image, 5*detectionsLists.get(slice).size());
		whiteBlobs.distributeBlobsBetweenDetections(detectionsLists.get(slice));
	}

	/* fills WhiteBlobsDetection with several white blobs for further analysis */
	private void fillWhiteBlobsDetectionWithBlobCandidates(int slice, int index, ImageProcessor image,
			int searchRadius) {
		WhiteBlobsDetection detection = getDetection(slice, index);
		detection.fillWithBlobCandidates(image, searchRadius);
		
		// debug info, check if sorted
		System.out.println("Printing sorted points, detected in slice " + slice + ", detection " + index);
		for (int i = 0; i < detection.getBlobCentersCount(); i++) {
			System.out.format("i=%d, blobValue = %f %n", i,
					detection.getBlobCenter(i).sortValue(detection.getAreaCenter(), detection.getRadius()));
		}
	}

	/* should be called after detections are filled with blobs */
	public void fillFirstBlobs(int slice) {
		ArrayList<WhiteBlobsDetection> detections = detectionsLists.get(slice);
		WhiteBlobsDetection detection, nextDetection;
		PointWithScale p;
		Node v;
		int index;
		ImageProcessor componentMask;
		float avrgIntensity;
		int intensityInMask = 255;
		for (int i = 0; i < detections.size(); i++) {
			detection = detections.get(i);
			for (int j = 0; j < detection.getBlobCentersCount(); j++) { // start from index 0, since its the closest one
				p = detection.getBlobCenter(j);
				System.out.format("First blob fill: Slice %d, Detection index %d, blob index %d %n", slice, i, j);
				if (!isPointInOtherDetections(p.point, slice)) { // search for the closest blob

					// draw component in components image (as filled circle, radius = sqrt(2)*sigma)
					componentMask = detection.getBlobMaskImage(componentsList.get(slice).getWidth(),
							componentsList.get(slice).getHeight(), j, intensityInMask);
					// ImagePlus imp = new ImagePlus("component mask", componentMask);
					// imp.show();
					index = componentsList.get(slice).addComponent(componentMask, intensityInMask, State.MITOSIS);
					detection.setFirstBlobComponentIndex(index);

					// connect to graph
					if (index != -1) { // component was succesfully added
						detection.setFirstBlobIndex(j);
						detection.setFirstDetected();
						avrgIntensity = componentsList.get(slice).getComponentAvrgIntensity(index);

						v = new Node(slice, index);
						g.addArcFromIndexToNodeAddable(detection.getNodeIndex(), v);
						detection.setFirstBlobNodeIndex(g.getNodeIndex(v));

						componentsList.get(slice).setComponentState(index, State.MITOSIS);
						componentsList.get(slice).setComponentHasParent(index);

						System.out.format("first blob's (%f, %f) avrg intensity is %f, blob val is %f %n",
								p.point.getX(), p.point.getY(), avrgIntensity, p.value);

						// if intensity of the first blob severely dropped compared to its parent, then
						// we should search for child tracks
						if (avrgIntensity < detection.getParentFirstBlobAverageIntensity() * 0.65) {
							System.out.println("track terminated due to intensity change");
							// look for child tracks
							connectFirstBlobToTwoTracks(detection, 40, 4, 6);

							// disable search of the second blob, since its not needed anymore
							detection.resetSecondDetectionNeeded();
							break;
						}

						// create new WhiteBlobDetection for the next slice, for first blobs only if
						// second detection needed is not needed
						if (!detection.isSecondDetectionNeeded()) {
							nextDetection = new WhiteBlobsDetection(p.point.getX(), p.point.getY(), slice + 1,
									detection.getRadius(), g.getNodeIndex(v), true,
									detection.getCandidateTrackIndexes(), avrgIntensity);
							addWhiteBlobDetection(slice + 1, nextDetection);
							System.out.println("Detection for slice " + (slice + 1)
									+ " created, after second detection not needed");
						}
						break;
					} else {
						System.out.println("component wasn't added during first blob fill");
						// terminate track
					}
				}
			}
			// here maybe do something if there was no detection
		}
	}

	/*
	 * should be called after detections are filled with blobs AND first blobs are
	 * set
	 */
	public void fillSecondBlobs(int slice, double childsPenalThreshold) {
		ArrayList<WhiteBlobsDetection> detections = detectionsLists.get(slice);
		WhiteBlobsDetection detection, nextDetection;
		Node v;
		int index1, index2, prevSlice, prevIndex;
		double x, y;
		ImageProcessor componentMask;
		int intensityInMask = 255;
		PointWithScale p;
		Point parentCenter;
		float parentAvrgIntensity, thisFirstBlobAvrgIntensity;
		boolean isChilds;
		for (int i = 0; i < detections.size(); i++) {
			detection = detections.get(i);
			// first check if there are enough blobs. If only one, create next detection
			if (detection.getBlobCentersCount() == 1 && detection.isFirstDetected()) {

				index1 = detection.getFirstBlobComponentIndex();
				thisFirstBlobAvrgIntensity = componentsList.get(slice).getComponentAvrgIntensity(index1);
				x = componentsList.get(slice).getComponentMassCenter(index1).getX();
				y = componentsList.get(slice).getComponentMassCenter(index1).getY();

				nextDetection = new WhiteBlobsDetection(x, y, slice + 1, detection.getRadius(),
						detection.getFirstBlobNodeIndex(), true, detection.getCandidateTrackIndexes(),
						thisFirstBlobAvrgIntensity);

				addWhiteBlobDetection(slice + 1, nextDetection);
				System.out.println("Detection added during second blob fill, only 1 white blob candidate");
				continue; // go to next detection
			}
			if (detection.isFirstDetected() && detection.isSecondDetectionNeeded()) {
				for (int j = 0; j < detection.getBlobCentersCount(); j++) { // start from the second blob (?)
					p = detection.getBlobCenter(j);
					if (isPointInOtherDetections(p.point, slice))
						continue;
					// draw component in components image (as filled circle, radius = sqrt(2)*sigma)
					componentMask = detection.getBlobMaskImage(componentsList.get(slice).getWidth(),
							componentsList.get(slice).getHeight(), j, intensityInMask);
					// ImagePlus imp = new ImagePlus("component mask", componentMask);
					// imp.show();
					index1 = detection.getFirstBlobComponentIndex();
					index2 = componentsList.get(slice).addComponent(componentMask, intensityInMask, State.MITOSIS);

					if (index2 != -1) { // component was succesfully added

						// checking if childs
						prevIndex = g.getNodeIndexByGlobalIndex(detection.getNodeIndex());
						prevSlice = g.getNodeSliceByGlobalIndex(detection.getNodeIndex());
						System.out.format("checking for child in slice %d %n", slice);
						parentCenter = componentsList.get(prevSlice).getComponentMassCenter(prevIndex);
						parentAvrgIntensity = componentsList.get(prevSlice).getComponentAvrgIntensity(prevIndex);

						isChilds = componentsList.get(slice).checkIfChildComponents(index1, index2, parentCenter,
								parentAvrgIntensity, childsPenalThreshold);

						if (isChilds) {
							v = new Node(slice, index2);
							g.addArcFromIndexToNodeAddable(detection.getNodeIndex(), v);

							detection.setSecondBlobIndex(j);
							detection.setSecondDetected();

							componentsList.get(slice).setComponentState(index2, State.MITOSIS);
							componentsList.get(slice).setComponentHasParent(index2);

							detection.setSecondBlobNodeIndex(g.getNodeIndex(v));
							// terminate
							searchChildBlobsAfterMitosis_2to2(detection, slice, 10, 30);
							break;

						} else {
							// remove component from components
							componentsList.get(slice).removeComponentByIndex(index2);

							thisFirstBlobAvrgIntensity = componentsList.get(slice).getComponentAvrgIntensity(index1);

							// create new WhiteBlobDetection for the next slice, second detection needed.
							x = componentsList.get(slice).getComponentMassCenter(index1).getX();
							y = componentsList.get(slice).getComponentMassCenter(index1).getY();

							nextDetection = new WhiteBlobsDetection(x, y, slice + 1, detection.getRadius(),
									detection.getFirstBlobNodeIndex(), true, detection.getCandidateTrackIndexes(),
									thisFirstBlobAvrgIntensity);

							System.out.format(
									"child not detected, creating detection with parent adj %d at (%f, %f) %n",
									detection.getFirstBlobNodeIndex(), x, y);
							addWhiteBlobDetection(slice + 1, nextDetection);
							break;
						}
					} else {
						System.out.println("White blob component wasn't added at slice during second blob fill " + slice
								+ ", adding detection to next slice");
						// connectFirstBlobToTwoTracks(detection, 30, 0, 6);
						thisFirstBlobAvrgIntensity = componentsList.get(slice).getComponentAvrgIntensity(index1);

						// create new WhiteBlobDetection for the next slice, second detection needed.
						x = componentsList.get(slice).getComponentMassCenter(index1).getX();
						y = componentsList.get(slice).getComponentMassCenter(index1).getY();

						nextDetection = new WhiteBlobsDetection(x, y, slice + 1, detection.getRadius(),
								detection.getFirstBlobNodeIndex(), true, detection.getCandidateTrackIndexes(),
								thisFirstBlobAvrgIntensity);

						System.out.format("Creating detection with parent adj %d at (%f, %f) %n",
								detection.getFirstBlobNodeIndex(), x, y);
						addWhiteBlobDetection(slice + 1, nextDetection);
						break;
						// terminate track
					}
				}
			}
		}
	}

	// fills detections in slice with indexes of tracks that start in the next slice
	// should be called only if first blobs are filled
	// track indexes should be inherited by next detections for future analysis
	public void fillTrackCandidateIndexes(int slice, float searchTracksRadius) {
		if (slice >= detectionsLists.size())
			return;
		ArrayList<WhiteBlobsDetection> detections = detectionsLists.get(slice);
		ArrayList<Integer> trackIndexesStartingNextSlice;
		WhiteBlobsDetection detection, nextDetection;
		int componentIndex;
		Point mc;

		trackIndexesStartingNextSlice = tracks.getTrackIndexesListStartingInSlice(slice + 1);

		for (int i = 0; i < detections.size(); i++) {
			detection = detections.get(i);
			if (!detection.isFirstDetected()) // only search for detections with first blob detected
				continue;

			for (int j = 0; j < trackIndexesStartingNextSlice.size(); j++) {
				componentIndex = tracks.getFirstComponentIndexForTrack(trackIndexesStartingNextSlice.get(j));
				mc = componentsList.get(slice + 1).getComponentMassCenter(componentIndex);

				if (mc.distTo(detection.getFirstBlobCenter()) <= searchTracksRadius)
					detection.addTrackCandidateIndex(trackIndexesStartingNextSlice.get(j));
			}
		}
	}	

	// 1-2 division for case when intensity of the first blob drops
	// track are sought from detectionSlice - slicesBefore to detetionSlice +
	// slicesThrough - 1
	// if slicesThrough == 0, then only consider tracks that started right away
	public void connectFirstBlobToTwoTracks(WhiteBlobsDetection detection, int searchTracksRadius, int slicesBefore,
			int slicesThroughAfter) {
		if (!detection.isFirstDetected())
			return;

		int detectionSlice = detection.getSlice();
		int componentIndex;
		int trackStartingSlice;
		Point mc;
		ArrayList<Integer> possibleTracksIndexes = new ArrayList<Integer>(10);
		double minDistance = Double.MAX_VALUE, dist;

		for (int i = 0; i < tracks.tracksCount(); i++) {
			if (tracks.isTrackWhiteBlobParent(i))
				continue;
			if (tracks.getLength(i) < 3)
				continue;
			trackStartingSlice = tracks.getStartSliceForTrack(i);
			if (trackStartingSlice < detectionSlice - slicesBefore
					|| trackStartingSlice >= detectionSlice + slicesThroughAfter)
				continue;

			componentIndex = tracks.getFirstComponentIndexForTrack(i);
			if (componentsList.get(trackStartingSlice).getComponentHasParent(componentIndex))
				continue;

			mc = componentsList.get(trackStartingSlice).getComponentMassCenter(componentIndex);

			dist = mc.distTo(detection.getFirstBlobCenter());
			if (dist <= searchTracksRadius) {
				if (dist < minDistance) // also find min distance for calculating penal function
					minDistance = dist;
				possibleTracksIndexes.add(i);
			}
		}

		System.out.println("possibleTrackIndexes: " + possibleTracksIndexes.toString());

		// in case no tracks were found
		if (possibleTracksIndexes.isEmpty())
			return;

		// !!! - the detection has no components in the image, because it detected track
		// end. So we must connect childs to its parent node, which is .getNodeIndex()

		// in case only 1 track was found
		if (possibleTracksIndexes.size() == 1) {
			int trIndex = possibleTracksIndexes.get(0);
			g.addArcFromIndexToIndexAddable(detection.getNodeIndex(), tracks.getStartAdjIndexForTrack(trIndex));
			tracks.setTrackAsWhiteBlobParent(trIndex);

			int dSlice1 = detectionSlice - tracks.getStartSliceForTrack(trIndex);
			System.out.println("slice differences is: " + dSlice1);
			// if in the same slice then dSlice=0, should remove first component
			if (dSlice1 <= 0)
				tracks.disconnectFirstComponentsFromTrack(trIndex, -dSlice1 - 1);

			componentsList.get(detection.getSlice()).incComponentChildCount(detection.getFirstBlobIndex());
			componentsList.get(tracks.getStartSliceForTrack(trIndex))
					.setComponentHasParent(tracks.getFirstComponentIndexForTrack(trIndex));
			System.out.println("Only 1 child; added arc from " + detection.getNodeIndex() + " to "
					+ tracks.getStartAdjIndexForTrack(trIndex));

			return;
		}

		ArrayList<Double> tracksPenalScores = new ArrayList<Double>(possibleTracksIndexes.size());
		double score;
		int blobComponentIndex = detection.getFirstBlobComponentIndex();

		// first, fill track scores
		for (int i = 0; i < possibleTracksIndexes.size(); i++) {
			componentIndex = tracks.getFirstComponentIndexForTrack(possibleTracksIndexes.get(i));
			trackStartingSlice = tracks.getStartSliceForTrack(possibleTracksIndexes.get(i));
			// score = calculateChildPenalScore(slice, index1, slice2, index2,
			// parentCenterPoint, timePenalCoefficient)
			score = penalFunction1to1(componentsList.get(detectionSlice), blobComponentIndex,
					componentsList.get(trackStartingSlice), componentIndex, minDistance);
			tracksPenalScores.add(score);
		}
		System.out.println("score unsorted: " + tracksPenalScores.toString());

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

		System.out.println("score sorted: " + tracksPenalScores.toString());
		System.out.println("track indexes sorted: " + possibleTracksIndexes.toString());

		// now find best pair that will become childs
		// only look in no more than 5 best candidates

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
						detection.getFirstBlobCenter(), 0.3f);
				System.out.println("Child score between tracks " + possibleTracksIndexes.get(i) + " and "
						+ possibleTracksIndexes.get(j) + "is " + score);
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
		int dSlice1 = detectionSlice - tracks.getStartSliceForTrack(bestTrackIndex1);
		int dSlice2 = detectionSlice - tracks.getStartSliceForTrack(bestTrackIndex2);
		System.out.println("slice differences are: " + dSlice1 + ", " + dSlice2);
		// if in the same slice then dSlice=0, should remove first component
		if (dSlice1 >= 0)
			tracks.disconnectFirstComponentsFromTrack(bestTrackIndex1, dSlice1 + 1);
		if (dSlice2 >= 0)
			tracks.disconnectFirstComponentsFromTrack(bestTrackIndex2, dSlice2 + 1);

		// now connect them to graph
		int nodeToIndex1 = tracks.getStartAdjIndexForTrack(bestTrackIndex1);
		int nodeToIndex2 = tracks.getStartAdjIndexForTrack(bestTrackIndex2);
		g.addArcFromIndexToIndexAddable(detection.getFirstBlobNodeIndex(), nodeToIndex1);
		g.addArcFromIndexToIndexAddable(detection.getFirstBlobNodeIndex(), nodeToIndex2);
		tracks.setTrackAsWhiteBlobParent(bestTrackIndex1);
		tracks.setTrackAsWhiteBlobParent(bestTrackIndex2);

		componentsList.get(detection.getSlice()).incComponentChildCount(detection.getFirstBlobIndex());
		componentsList.get(tracks.getStartSliceForTrack(bestTrackIndex1))
				.setComponentHasParent(tracks.getFirstComponentIndexForTrack(bestTrackIndex1));

		componentsList.get(detection.getSlice()).incComponentChildCount(detection.getFirstBlobIndex());
		componentsList.get(tracks.getStartSliceForTrack(bestTrackIndex2))
				.setComponentHasParent(tracks.getFirstComponentIndexForTrack(bestTrackIndex2));

		System.out.println("Added arc from " + detection.getFirstBlobNodeIndex() + " to " + nodeToIndex1);
		System.out.println("Added arc from " + detection.getFirstBlobNodeIndex() + " to " + nodeToIndex2);
	}

	/*
	 * search for two tracks to with two white blobs in detection after mitosis.
	 * SlicesThrough is in how many slices we are searching. Only consider tracks
	 * that start no further than searchRadius. Tracks and graph are 'connected'.
	 */
	private void searchChildBlobsAfterMitosis_2to2(WhiteBlobsDetection detection, int detectionTerminateSlice,
			int slicesThrough, int searchRadius) {
		// detection must have 2 blobs detected
		// if (!detection.isFirstDetected() || !detection.isSecondDetected())
		// return;
		// // Track tr;
		// int startIndex, trSlice, compIndex;
		// Point center1 = detection.getFirstBlobCenter();
		// Point center2 = detection.getSecondBlobCenter();
		// Point trackStartCenter;
		//
		// // first, find tracks in [slice, slice+slicesThrough] that are not further
		// than
		// // searchRadius
		// for (int i = 0; i < tracks.tracksCount(); i++) {
		// startIndex = tracks.getStartAdjIndexForTrack(i);
		//
		// trSlice = g.getNodeSliceByGlobalIndex(startIndex);
		// compIndex = g.getNodeIndexByGlobalIndex(startIndex);
		// if (trSlice < detectionTerminateSlice || trSlice > detectionTerminateSlice +
		// slicesThrough)
		// continue;
		//
		// trackStartCenter =
		// componentsList.get(trSlice).getComponentMassCenter(compIndex);
		// if (Point.dist(trackStartCenter, center1) > searchRadius
		// && Point.dist(trackStartCenter, center2) > searchRadius)
		// continue;
		//
		// // now consider current track as child candidate
		// // first find closest for the first blob
		// }
		//
		searchClosestChildTrack_1to1(detection, true, detectionTerminateSlice, slicesThrough, searchRadius);
		searchClosestChildTrack_1to1(detection, false, detectionTerminateSlice, slicesThrough, searchRadius);
	}

	/*
	 * finds closest child track that is no further than radius, starts in [slice,
	 * slice+slicesThrough]. The parent is either first blob in detection, or second
	 * blob. The detection is from slice-1 (?)
	 */
	private void searchClosestChildTrack_1to1(WhiteBlobsDetection detection, boolean isFirstBlob,
			int detectionTerminateSlice, int slicesThrough, int searchRadius) {
		if (!detection.isFirstDetected())
			return;
		if (!detection.isSecondDetected() && !isFirstBlob)
			return;
		Point center = isFirstBlob ? detection.getFirstBlobCenter() : detection.getSecondBlobCenter();

		int startIndex, trSlice, compIndex, closestTrackIndex = -1;
		Point trackStartCenter;
		double dist, minDist;

		for (int currSlice = detectionTerminateSlice + 1; currSlice <= detectionTerminateSlice
				+ slicesThrough; currSlice++) {
			minDist = Double.MAX_VALUE;
			closestTrackIndex = -1;
			for (int i = 0; i < tracks.tracksCount(); i++) {
				if (tracks.isTrackWhiteBlobParent(i))
					continue;
				startIndex = tracks.getStartAdjIndexForTrack(i);

				trSlice = tracks.getStartSliceForTrack(i);
				compIndex = g.getNodeIndexByGlobalIndex(startIndex);
				if (trSlice != currSlice)
					continue;
				if (componentsList.get(trSlice).getComponentHasParent(compIndex))
					continue;

				trackStartCenter = componentsList.get(trSlice).getComponentMassCenter(compIndex);
				dist = Point.dist(trackStartCenter, center);
				if (dist > searchRadius)
					continue;

				dist = dist + dist * (trSlice - detectionTerminateSlice) * 0.4; // add some penalty for time
				if (dist < minDist) {
					minDist = dist;
					closestTrackIndex = i;
				}

				// now consider current track as child candidate
				// first find closest for the first blob
			}

			if (closestTrackIndex == -1) // track not found in currSlice, search in next
				continue;
			break; // else, closest track found,
		}

		if (closestTrackIndex != -1) {
			// connect it with one of the blobs;
			int blobNodeIndex = -1;

			startIndex = tracks.getStartAdjIndexForTrack(closestTrackIndex);

			if (isFirstBlob)
				blobNodeIndex = detection.getFirstBlobNodeIndex();
			else
				blobNodeIndex = detection.getSecondBlobNodeIndex();

			g.addArcFromIndexToIndexAddable(blobNodeIndex, startIndex);
			tracks.setTrackAsWhiteBlobParent(closestTrackIndex);
			componentsList.get(detection.getSlice()).incComponentChildCount(detection.getFirstBlobIndex());
			componentsList.get(tracks.getStartSliceForTrack(closestTrackIndex))
					.setComponentHasParent(tracks.getFirstComponentIndexForTrack(closestTrackIndex));
		}
	}

	// checks if the point is already someone's first or second blob
	private boolean isPointInOtherDetections(Point p, int slice) {
		ArrayList<WhiteBlobsDetection> detections = detectionsLists.get(slice);
		WhiteBlobsDetection detection;
		for (int i = 0; i < detections.size(); i++) {
			// if (i == notIncludedDetectionIndex)
			// continue;
			detection = detections.get(i); // search only in already "approved" blobs (first and second)
			if (p.equals(detection.getFirstBlobCenter())) {
				return true;
			}
			if (p.equals(detection.getSecondBlobCenter())) {
				return true;
			}
		}
		return false;
	}

	/* return the image of candidate components in slice */
	public ImageProcessor getComponentCandidatesImage(int slice) {
		ImageProcessor result = new ShortProcessor(componentsList.get(slice).getWidth(),
				componentsList.get(slice).getHeight());
		ArrayList<WhiteBlobsDetection> detections = detectionsLists.get(slice);
		WhiteBlobsDetection detection;
		PointWithScale p;
		int intensity = 1;
		for (int i = 0; i < detections.size(); i++) {
			detection = detections.get(i);
			for (int j = 0; j < detection.getBlobCentersCount(); j++) {
				p = detection.getBlobCenter(j);
				ImageFunctions.drawCircle(result, (float) (p.sigma * 1.41), (int) p.point.getX(), (int) p.point.getY(),
						intensity++, false, true);
			}
		}
		return result;
	}

	/* calculates penal score for child components (the less, the better) */
	public double calculateChildPenalScore(int slice1, int index1, int slice2, int index2, Point parentCenterPoint,
			float timePenalCoefficient) {
		double score = 0;
		// take into account avrg intensity, size, distance from parent center
		double c_int, c_size, c_distance; // coefficients
		c_int = 1; // for intensity of the child blobs
		c_size = 0.7; // for size of child blobs
		c_distance = 0.6; // for difference in distance between child blobs and parent

		double int1, int2, size1, size2, dist1, dist2;
		int1 = componentsList.get(slice1).getComponentAvrgIntensity(index1);
		int2 = componentsList.get(slice2).getComponentAvrgIntensity(index2);

		size1 = componentsList.get(slice1).getComponentArea(index1);
		size2 = componentsList.get(slice2).getComponentArea(index2);

		dist1 = Point.dist(componentsList.get(slice1).getComponentMassCenter(index1), parentCenterPoint);
		dist2 = Point.dist(componentsList.get(slice2).getComponentMassCenter(index2), parentCenterPoint);

		double int_score = ImageComponentsAnalysis.normVal(int1, int2);
		double size_score = ImageComponentsAnalysis.normVal(size1, size2);
		double dist_score = ImageComponentsAnalysis.normVal(dist1, dist2);

		double coef_sum = c_int + c_size + c_distance;

		// if slice2 is next to slice1, then time_penal should be zero
		double time_penal = Math.abs(slice2 - slice1 - 1) * timePenalCoefficient;

		return (1 + time_penal) * (c_int * int_score + c_size * size_score + c_distance * dist_score) / coef_sum;
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
		double w_a = 0.8;
		double w_i = 0.4;
		double w_d = 1;
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
}