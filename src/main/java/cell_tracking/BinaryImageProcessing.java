package cell_tracking;

import ij.process.ImageProcessor;
import java.util.ArrayList;
import java.util.List;
import inra.ijpb.morphology.AttributeFiltering;
import inra.ijpb.morphology.GeodesicReconstruction;
import inra.ijpb.morphology.Morphology.Operation;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.Strel.Shape;
import inra.ijpb.morphology.attrfilt.AreaOpeningQueue;
import inra.ijpb.plugins.*;

/* Class for processing binary images */
// better use already existing MorphoLibJ plugin...
public class BinaryImageProcessing {
	
	/**
	 * @deprecated - doesn't work, using from morpholibj
	 * labels connected components in binary image with different intensity values */
	public void labelBinary(ImageProcessor ip) {
		// let's try to make two-path algorithm
		// first, set all 255 values to -1 (aka "not seen")
		final int w = ip.getWidth(), h = ip.getHeight();
		for (int y=0; y < h; y++)
			for (int x=0; x < w; x++)
				if (ip.getf(x, y) > 10) ip.setf(x, y, -1);

		ArrayList<ArrayList<Integer>> eqTable = new ArrayList<ArrayList<Integer>>();
		ArrayList<Integer> eqSet;
		
		//forward path
		int label = 0, cur_label = 0;
		for (int y=0; y < h; y++) {
			for (int x=0; x < w; x++) {
				if (ip.getf(x, y) != 0) {
					label = getLabel(ip, x, y);
					if (label == 0) { //no labels on top or left -> mb new connected region -> increment current label
						cur_label++;
						ip.setf(x, y, cur_label);
						eqSet = new ArrayList<Integer>(); // index of array is the label
						eqSet.add(cur_label);
						eqTable.add(eqSet);
					}
					else //if there is label on top or left -> use it
						ip.setf(x, y, label);
				}
			}
		}
		
		//backward path
		for (int y = h-1; y >= 0; y--) {
			for (int x = w-1; x >= 0; x--) {
				if (ip.getf(x,y) == -1) ip.setf(x, y, label);
				if (ip.getf(x, y) != 0) {
					label = getLabel(ip, x, y);
					ip.setf(x, y, label);
				}
			}
		}
	}
	
	/* function does invertion, then opening with big radius to remove holes and reduce concavities in binary image */
	public void FillAndAmoothForegroundFloatBinary(ImageProcessor ip) {	
		Operation op = Operation.CLOSING;
		Shape shape = Shape.DISK;
		Strel strel = shape.fromRadius(10);
		
		ip = op.apply(ip, strel);
		
		/*bp = (ByteProcessor) op.apply(bp, strel);
		bp = (ByteProcessor) GeodesicReconstruction.killBorders(bp);
		GrayscaleAttributeFilteringPlugin attrFilt = new GrayscaleAttributeFilteringPlugin();
		AreaOpeningQueue algo = new AreaOpeningQueue();
		algo.setConnectivity(4);
		result = algo.process(result, 80);*/
		
	}
	
	/* returns label of top or left pixel if it has one. Otherwise returns the next index */
	private int getLabel(ImageProcessor ip, int x, int y) {
		if (x!= 0 && ip.getf(x - 1, y) > 0) //first check the left pixel and return its label (if has one)
			return (int)ip.getf(x - 1, y);
		if (y!= 0 && ip.getf(x, y - 1) > 0) //then check the top pixel and maybe return its label (if has one)
			return (int)ip.getf(x, y - 1);
		return 0; // get next label
	}
}
