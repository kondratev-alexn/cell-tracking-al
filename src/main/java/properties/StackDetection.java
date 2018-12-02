package properties;

import java.util.ArrayList;

import cellTracking.ImageFunctions;
import cellTracking.ImageProcessorCalculator;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import inra.ijpb.morphology.Strel;
import inra.ijpb.math.ImageCalculator;
import inra.ijpb.morphology.Morphology.Operation;

public class StackDetection {
	private ArrayList<SliceDetections> stack;
	
	public StackDetection() {
		stack = new ArrayList<SliceDetections>(10);
	}
	
	public void fillStack(ImagePlus ctcResultImp) {
		ImageStack imgstack = ctcResultImp.getStack();
		for (int i=0; i<imgstack.getSize(); i++) {
			ImageProcessor img = imgstack.getProcessor(i+1);
			SliceDetections detection = new SliceDetections(img, i);
			stack.add(detection);
			System.out.printf("Slice %d added %n", i);
		}
	}
	
	public boolean checkTrackCorrectness(int trackIndex, int startSlice, int endSlice) {
		for (int i=startSlice; i<= endSlice; i++) {
			if (!stack.get(i).isTrackInSlice(trackIndex))
				return false;
		}
		return true;
	}
	
	public Roi detectionRoi(int slice, int trackIndex) {
		return stack.get(slice).detectionRoi(trackIndex);
	}
	
	public Roi makeRingRoi(int slice, int trackIndex, int dilationRadius) {
		Roi roi = detectionRoi(slice, trackIndex);		
		
		ImageProcessor mask = roi.getMask();
		ImageProcessor mask2 = new ByteProcessor(mask.getWidth() + 2*dilationRadius, mask.getHeight() + 2*dilationRadius);
		for (int y=0; y<mask.getHeight(); ++y)
			for (int x=0; x<mask.getWidth(); ++x) {
				mask2.set(x+dilationRadius, y+dilationRadius, mask.get(x, y));
			}
		mask = mask2.duplicate();

		mask2 = ImageFunctions.operationMorph(mask2, Operation.DILATION, Strel.Shape.DISK, dilationRadius);
		mask = ImageCalculator.combineImages(mask2, mask, ImageCalculator.Operation.XOR);
		
		ImagePlus imp = new ImagePlus("mask", mask);
		imp.show();
		
		return roi;
	}
}
