package properties;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;

import cellTracking.ImageFunctions;
import cellTracking.ImageProcessorCalculator;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.FreehandRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.Wand;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import inra.ijpb.morphology.Strel;
import inra.ijpb.math.ImageCalculator;
import inra.ijpb.morphology.Morphology.Operation;

public class StackDetection {
	private ArrayList<SliceDetections> stack;
	private ImagePlus _ctcResultImp;

	public StackDetection() {
		stack = new ArrayList<SliceDetections>(10);
	}

	public void fillStack(ImagePlus ctcResultImp) {
		_ctcResultImp = ctcResultImp;
		ImageStack imgstack = ctcResultImp.getStack();
		for (int i = 0; i < imgstack.getSize(); i++) {
			ImageProcessor img = imgstack.getProcessor(i + 1);
			SliceDetections detection = new SliceDetections(img, i);
			stack.add(detection);
		}
	}

	public boolean checkTrackCorrectness(int trackIndex, int startSlice, int endSlice) {
		for (int i = startSlice; i <= endSlice; i++) {
			if (!stack.get(i).isTrackInSlice(trackIndex))
				return false;
		}
		return true;
	}

	public void show() {
		ImageStack istack = new ImageStack(_ctcResultImp.getWidth(), _ctcResultImp.getHeight(),
				_ctcResultImp.getStackSize());
		for (int i = 0; i < stack.size(); ++i) {
			ImageProcessor ip = stack.get(i).detectionsImage();
			istack.setProcessor(ip, i + 1);
		}
		ImagePlus imp = new ImagePlus("stack", istack);
		imp.show();
	}

	public Roi detectionRoi(int slice, int trackIndex) {
		return stack.get(slice).detectionRoi(trackIndex);
	}

	private void setRoi(Roi roi, int slice, int trackIndex) {
		stack.get(slice).setRoi(roi, trackIndex);
	}

	public void changeDetectionsToRing(int dilationRadius) {
		for (int slice = 0; slice < stack.size(); ++slice) {
			HashMap<Integer, Detection> map = stack.get(slice).detectionsMap();
			for (int trackIndex : map.keySet()) {
				Roi roi = makeRingRoi(slice, trackIndex, dilationRadius);
				setRoi(roi, slice, trackIndex);
			}
		}

	}

	public Roi makeRingRoi(int slice, int trackIndex, int dilationRadius) {
		Roi roi = detectionRoi(slice, trackIndex);

//		int xbl, xbr, ybl, ybr;
//		Rectangle box = roi.getBounds();
//		xbl = box.x;
//		xbr = box.x + box.width;
//		ybl = box.y;
//		ybr = box.y + box.height;
//		
//		int pLeft, pRight, pTop, pBottom;
		//pLeft = xbl

		ImageProcessor mask = roi.getMask();
		ImageProcessor mask2 = new ByteProcessor(mask.getWidth() + 2 * dilationRadius,
				mask.getHeight() + 2 * dilationRadius);
		for (int y = 0; y < mask.getHeight(); ++y)
			for (int x = 0; x < mask.getWidth(); ++x) {
				mask2.set(x + dilationRadius, y + dilationRadius, mask.get(x, y));
			}
		mask = mask2.duplicate();

		mask2 = ImageFunctions.operationMorph(mask2, Operation.DILATION, Strel.Shape.DISK, dilationRadius);


		int xNew = roi.getBounds().x - dilationRadius;
		int yNew = roi.getBounds().y - dilationRadius;

		ShapeRoi sr = new ShapeRoi(roi);

		Wand w = new Wand(mask2);
		w.autoOutline(0, 0, 255, 255, Wand.FOUR_CONNECTED);
		if (w.npoints > 0) { // we have a roi from the wand...

			roi = new PolygonRoi(w.xpoints, w.ypoints, w.npoints, Roi.TRACED_ROI);
			roi.setPosition(slice);
			roi.setLocation(xNew, yNew);
		}

		ShapeRoi sr2 = new ShapeRoi(roi);
		sr2 = sr2.xor(sr);
		sr2.setPosition(slice);

		// ImagePlus maskImp = new ImagePlus("new mask", sr2.getMask());
		// maskImp.show();

		return sr2;
	}
}
