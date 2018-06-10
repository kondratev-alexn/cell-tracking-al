package blob_detection;

import java.util.ArrayList;
import java.util.Collections;

import cellTracking.BlobDetector;
import cellTracking.WhiteBlobsDetection;
import ij.process.ImageProcessor;
import point.PointWithScale;

/* class that stores all white blobs of a single image */
public class WhiteBlobsComponents {
	private ImageProcessor image;
	private float[] sigmas = { 4, 7, 10, 15, 20 };
	private ArrayList<PointWithScale> blobs;
	private boolean[] used;
	
	final private int maxDistance = 100; // for blob's sortValue
	
	public WhiteBlobsComponents(ImageProcessor intensityImage, int blobsCount) {
		image = intensityImage;
		BlobDetector detector = new BlobDetector(image, null, sigmas);
		blobs = detector.findBlobsByLocalMaximaAsPoints(0.001f, true, blobsCount, 2, 2, true);
		used = new boolean[blobs.size()];
	}
	
	private void setBlobUsed(int index) {
		used[index] = true;
	}
	
	private boolean isBlobUsed(int index) {
		return used[index];
	}

	public void distributeBlobsBetweenDetections(ArrayList<WhiteBlobsDetection> detections) {
		/*
		 * algorithm: 
		 * for each detection, sort blobs by distance-value, remember best N blobs 
		 * add blobs that are best for only 1 detection to the corresponding detection 
		 * for each blob that is best for several components: allot it to the detection with best distance-value for it between candidate detections
		 * DONE!
		 * this algorithm changes "filling detection" part in white blobs tracking
		 * afterwards begin fill first blobs, etc.
		 */
		
		ArrayList<ArrayList<Integer>> allBlobIndexesDetection = new ArrayList<ArrayList<Integer>>(detections.size());
		int count = 7;
		for (int i = 0; i<detections.size(); i++) {
			ArrayList<Integer> blobIndexes = sortBestBlobsForDetection(detections.get(i), count);
			allBlobIndexesDetection.add(blobIndexes);
		}
		
		distributeBlobs(detections, allBlobIndexesDetection);
	}

	/* returns sorted index array for 'blobs' list of size 'count' */
	private ArrayList<Integer> sortBestBlobsForDetection(WhiteBlobsDetection detection, int count) {
		if (blobs.size() < count)
			count = blobs.size();
		
		ArrayList<Integer> result = new ArrayList<Integer>(count);

		PointWithScale blob;
		int max_index;
		double v, max_v;
		for (int i = 0; i<count && i<blobs.size(); i++) {
			max_v = Double.MIN_VALUE;
			max_index = 0;
			for (int j = 0; j < blobs.size(); j++) {
				if (result.contains(j))
					continue;
				blob = blobs.get(j);
				v = blob.sortValue(detection.getAreaCenter(), maxDistance);
				if (v > max_v) {
					max_v = v;
					max_index = j;
				}
			}
			result.add(max_index);
		}
		return result;
	}
	
	private void distributeBlobs(ArrayList<WhiteBlobsDetection> detections, ArrayList<ArrayList<Integer>> allBlobIndexesDetection ) {		
		if (detections.size() != allBlobIndexesDetection.size()) {
			System.out.println("Size of detections list and best blob indexes differs");
			return;
		}
		
		int size = detections.size();
		ArrayList<Integer> indexes;
		int index;
		
		for (int i=0; i<size; i++) {
			indexes = allBlobIndexesDetection.get(i);
			for (int j=0; j<indexes.size(); j++) {
				index = indexes.get(j);
				if (isBlobUsed(index))
					continue;
				if (isIndexUnique(index, allBlobIndexesDetection, i)) {
					detections.get(i).addBlobCenter(blobs.get(index));
					setBlobUsed(index);
				}
				else { //several detections compete for the blob
					if (addBlobToBestDetection(index, detections, allBlobIndexesDetection))
						setBlobUsed(index);
				}
			}
		}
	}
	
	private boolean isIndexUnique(int index, ArrayList<ArrayList<Integer>> allBlobIndexesDetection, int detectionIndex) {
		ArrayList<Integer> indexes;
		for (int i=0; i<allBlobIndexesDetection.size(); i++) {
			if (i == detectionIndex)
				continue;
			indexes = allBlobIndexesDetection.get(i);
			if (indexes.contains(index))
				return false;
		}
		return true;
	}
	
	/* returns true if blob was added */
	private boolean addBlobToBestDetection(int blobIndex, ArrayList<WhiteBlobsDetection> detections, ArrayList<ArrayList<Integer>> allBlobIndexesDetection) {
		ArrayList<Integer> indexes;
		ArrayList<WhiteBlobsDetection> detectionsForBlob = new ArrayList<WhiteBlobsDetection>(3);
		for (int i=0; i<allBlobIndexesDetection.size(); i++) {
			indexes = allBlobIndexesDetection.get(i);
			if (indexes.contains(blobIndex))
				detectionsForBlob.add(detections.get(i));
		}
		
		if (detectionsForBlob.isEmpty())
			return false;

		double v, max_v = Double.MIN_VALUE;
		int index_max = 0;
		for (int i=0; i<detectionsForBlob.size(); i++) {
			v = blobs.get(blobIndex).sortValue(detectionsForBlob.get(i).getAreaCenter(), maxDistance);
			if (v > max_v) {
				max_v = v;
				index_max = i;
			}
		}
		
		detectionsForBlob.get(index_max).addBlobCenter(blobs.get(blobIndex));
		return true;
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
}
