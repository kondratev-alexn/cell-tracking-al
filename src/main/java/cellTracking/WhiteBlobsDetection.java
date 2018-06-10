package cellTracking;

import java.util.ArrayList;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import point.Point;
import point.PointWithScale;

public class WhiteBlobsDetection {
	private int parentTrackIndex;
	private Point center;
	private int radius;
	private int slice;
	private int blobSearchCount; // number of blobs to be searched in this detection (should be 1 ~ 5)
	private int nodeIndex; // node index in the tracking graph. Used for connecting to other tracks.
							// The node is for 'parent'

	private int firstBlobNodeIndex = -1; // index of the node corresponding to the first blob
	private int secondBlobNodeIndex = -1; // index of the node corresponding to the second blob

	// indexes of the main 2 blobs in blobCenters list
	private int firstBlobIndex = -1;
	private int secondBlobIndex = -1;

	// for storing information about the blob
	private int firstBlobComponentIndex = -1;
	private int secondBlobComponentIndex = -1;

	// private Point firstBlobCenter;
	// private Point secondBlobCenter;
	private ArrayList<PointWithScale> blobCenters; // list of blob candidates for cell mitosis

	// list of track indexes in global Tracks that are candidates for being childs.
	// Tracks that start in the NEXT slice should go there for each detection
	private ArrayList<Integer> trackCandidatesIndexes;

	private float parentFirstBlobAverageIntensity;

	private boolean isFirstDetected = false;
	private boolean isSecondDetected = false;
	private boolean isSecondDetectionNeeded = false;

	/*
	 * Class containing information about a cell during mitosis in one slide. For
	 * the first slice there is only 1 blob possible, then there can be 1 or 2
	 * (maybe stop detecting after two child-worthy blobs were detected?..)
	 */
	public WhiteBlobsDetection(double xCenter, double yCenter, int slice, int radius, int nodeIndexInTrackingGraph,
			boolean needSecondDetection, ArrayList<Integer> previousTrackCandidateIndexes, float parentAvrgIntensity) {
		center = new Point(xCenter, yCenter);
		this.slice = slice;
		// firstBlobCenter = new Point(0, 0);
		// secondBlobCenter = new Point(0, 0);
		blobCenters = new ArrayList<PointWithScale>(blobSearchCount);
		trackCandidatesIndexes = new ArrayList<Integer>(previousTrackCandidateIndexes);
		parentFirstBlobAverageIntensity = parentAvrgIntensity;
		this.radius = radius;
		nodeIndex = nodeIndexInTrackingGraph;
		isSecondDetectionNeeded = needSecondDetection;
		blobSearchCount = 5;
	}

	// public void setFirstBlobCenter(double x, double y) {
	// firstBlobCenter.set_xy(x, y);
	// }
	//
	// public void setSecondBlobCenter(double x, double y) {
	// secondBlobCenter.set_xy(x, y);
	// }
	
	public int getSlice() {
		return slice;
	}

	public void addTrackCandidateIndex(int index) {
		trackCandidatesIndexes.add(index);
	}

	public int getTrackCandidatesCount() {
		return trackCandidatesIndexes.size();
	}

	public int getTrackCandidateIndex(int i) {
		return trackCandidatesIndexes.get(i);
	}

	public ArrayList<Integer> getCandidateTrackIndexes() {
		return trackCandidatesIndexes;
	}

	public PointWithScale getFirstBlobCenterWithScale() {
		if (firstBlobIndex != -1)
			return blobCenters.get(firstBlobIndex);
		else
			return null;
	}

	public PointWithScale getSecondBlobCenterWithScale() {
		if (secondBlobIndex != -1)
			return blobCenters.get(secondBlobIndex);
		else
			return null;
	}
	
	public float getParentFirstBlobAverageIntensity() {
		return parentFirstBlobAverageIntensity;
	}

	public int getFirstBlobNodeIndex() {
		return firstBlobNodeIndex;
	}

	public void setFirstBlobNodeIndex(int firstBlobNodeIndex) {
		this.firstBlobNodeIndex = firstBlobNodeIndex;
	}

	public int getSecondBlobNodeIndex() {
		return secondBlobNodeIndex;
	}

	public void setSecondBlobNodeIndex(int secondBlobNodeIndex) {
		this.secondBlobNodeIndex = secondBlobNodeIndex;
	}

	public Point getFirstBlobCenter() {
		if (firstBlobIndex != -1)
			return blobCenters.get(firstBlobIndex).point;
		else
			return null;
	}

	public Point getSecondBlobCenter() {
		if (secondBlobIndex != -1)
			return blobCenters.get(secondBlobIndex).point;
		else
			return null;
	}

	public int getFirstBlobComponentIndex() {
		return firstBlobComponentIndex;
	}

	public void setFirstBlobComponentIndex(int firstBlobComponentIndex) {
		this.firstBlobComponentIndex = firstBlobComponentIndex;
	}

	public int getSecondBlobComponentIndex() {
		return secondBlobComponentIndex;
	}

	public void setSecondBlobComponentIndex(int secondBlobComponentIndex) {
		this.secondBlobComponentIndex = secondBlobComponentIndex;
	}

	public int getNodeIndex() {
		return nodeIndex;
	}

	public int getRadius() {
		return radius;
	}

	public void addBlobCenter(PointWithScale p) {
		blobCenters.add(p);
	}

	public void addBlobCenter(double x, double y, double sigma, double value) {
		blobCenters.add(new PointWithScale(x, y, sigma, value));
	}

	public PointWithScale getBlobCenter(int index) {
		return blobCenters.get(index);
	}

	public int getBlobCentersCount() {
		return blobCenters.size();
	}

	public int getParentTrackIndex() {
		return parentTrackIndex;
	}

	public Point getAreaCenter() {
		return center;
	}

	public void setFirstDetected() {
		isFirstDetected = true;
	}

	public void setSecondDetected() {
		isSecondDetected = true;
	}

	public void setSecondDetectionNeeded() {
		isSecondDetectionNeeded = true;
	}
	
	public void resetSecondDetectionNeeded() {
		isSecondDetectionNeeded = false;
	}

	public boolean isFirstDetected() {
		return isFirstDetected;
	}

	public boolean isSecondDetected() {
		return isSecondDetected;
	}

	public boolean isSecondDetectionNeeded() {
		return isSecondDetectionNeeded;
	}

	public int getBlobSearchCount() {
		return blobSearchCount;
	}

	public void setFirstBlobIndex(int index) {
		firstBlobIndex = index;
	}

	public void setSecondBlobIndex(int index) {
		secondBlobIndex = index;
	}

	public int getFirstBlobIndex() {
		return firstBlobIndex;
	}

	public int getSecondBlobIndex() {
		return secondBlobIndex;
	}
	
	public void fillWithBlobCandidates(ImageProcessor image, int searchRadius) {
		ImageProcessor mask = getDetectionMaskImage(image.getWidth(), image.getHeight(), searchRadius);
		float[] sigmas = { 7, 10, 15, 20 };
		BlobDetector detector = new BlobDetector(image, mask, sigmas);
		// search some blobs and get the closest one to the center
		ArrayList<PointWithScale> points = detector.findBlobsByLocalMaximaAsPoints(0.001f, true,
				getBlobSearchCount(), 2, 2, true);
		System.out.println(slice);
		System.out.println(points);

		PointWithScale p_i, bestPoint;
		double val;
		double max_val;
		// we should sort the list here by distance (and blob value)
		for (int i = 0; i < points.size(); i++) { // sort points by distance from center (the closer the better)
			p_i = points.get(i);
			max_val = Double.MIN_VALUE;
			bestPoint = points.get(i);
			for (int j = i; j < points.size(); j++) { // find maximum
				val = points.get(j).sortValue(getAreaCenter(), getRadius());
				// Point.dist(points.get(j).point, detection.getAreaCenter());
				if (val > max_val) {
					bestPoint = points.get(j);
					max_val = val;
				}
			}
			// set found max at position i
			// System.out.println("best point before swapping " + bestPoint.toString());
			// System.out.println("i-th point before swapping " + points.get(i).toString());
			PointWithScale.swap(bestPoint, points.get(i));
			// System.out.println("best point after swapping " + bestPoint.toString());
			// System.out.println("i-th point after swapping " + points.get(i).toString());
			addBlobCenter(points.get(i)); // blobs are added from the 'best' one
		}
	}

	public ImageProcessor getBlobMaskImage(int width, int height, int blobIndex, int intensity) {
		ImageProcessor mask = new ByteProcessor(width, height);
		PointWithScale pws = blobCenters.get(blobIndex);
		//System.out.println("sigma = " + pws.sigma);
		ImageFunctions.drawCircle(mask, (float) pws.sigma * 1.41f, (int) pws.point.getX(), (int) pws.point.getY(),
				intensity, false, true);
		return mask;
	}

	public ImageProcessor getDetectionMaskImage(int width, int height, int radius) {
		ImageProcessor mask = new ByteProcessor(width, height);
		int xc = (int) center.getX();
		int yc = (int) center.getY();
		for (int y = 0; y < height; y++)
			for (int x = 0; x < width; x++) {
				if (Math.abs(x - xc) <= radius && Math.abs(y - yc) <= radius) {
					mask.set(x, y, 255);
				}
			}
		return mask;
	}
	

	public boolean isBestBlobValueAboveThreshold(float threshold) {
		if (blobCenters.isEmpty())
			return false;
		PointWithScale p = blobCenters.get(0);
		return p.value > threshold;
	}
}