package properties;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;

import cellTracking.ImageFunctions;
import graph.CellTrackingGraph;
import graph.Graph;
import graph.MitosisInfo;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.Wand;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.Morphology.Operation;
import visualization.ColorPicker;

public class StackDetection {
	private ArrayList<SliceDetections> stack;
	private TrackCTCMap tracks;
	private ImagePlus _ctcResultImp;

	public StackDetection() {
		stack = new ArrayList<SliceDetections>(10);
		tracks = new TrackCTCMap();
	}

	public void fillStack(ImagePlus ctcResultImp, MitosisInfo mitosisInfo) {
		_ctcResultImp = ctcResultImp;

		ImageStack imgstack = ctcResultImp.getStack();
		for (int i = 0; i < imgstack.getSize(); i++) {
			ImageProcessor img = imgstack.getProcessor(i + 1);
			SliceDetections detection = new SliceDetections(img, i + 1, mitosisInfo);
			stack.add(detection);
			// System.out.println("Detection added " + i);
		}
	}

	public void fillTracks(String ctcResultTxt) {
		tracks.fillTracks(ctcResultTxt, this);
	}

	public ArrayList<SliceDetections> detections() {
		return stack;
	}

	public TrackCTCMap tracks() {
		return tracks;
	}

	public boolean checkTrackCorrectness(int trackIndex, int startSlice, int endSlice) {
		for (int i = startSlice; i <= endSlice; i++) {
			if (!stack.get(i).isTrackInSlice(trackIndex))
				return false;
		}
		return true;
	}

	public void addToRoiManager(boolean sort) {
		if (sort)
			addToRoiManagerTrackSort();
		else
			addToRoiManagerNoSort();
	}

	public void addToRoiManagerNoSort() {
		RoiManager manager = RoiManager.getInstance();
		if (manager == null)
			manager = new RoiManager();
		manager.reset();

		for (int slice = 0; slice < stack.size(); ++slice) {
			HashMap<Integer, Detection> map = stack.get(slice).detectionsMap();
			for (int trackIndex : map.keySet()) {
				Roi roi = detectionRoi(slice, trackIndex);
				manager.addRoi(roi);
			}
		}
	}

	public void addToRoiManagerTrackSort() {
		RoiManager manager = RoiManager.getInstance();
		if (manager == null)
			manager = new RoiManager();
		manager.reset();

		for (TrackCTC track : tracks.tracksMap().values()) {
			int trackIndex = track.index();
			for (int slice = track.startSlice(); slice <= track.endSlice(); ++slice) {
				Roi roi = detectionRoi(slice, trackIndex);
				manager.addRoi(roi);
			}
		}
	}

	public TrackCTCMap tracksCTC() {
		return tracks;
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

	public boolean isMitosisDetection(int trackIndex, int slice) {
		return stack.get(slice).detectionsMap().get(trackIndex).isMitosis();
	}

	public void changeDetectionsToRing(int dilationRadius) {
		for (int slice = 0; slice < stack.size(); ++slice) {
			HashMap<Integer, Detection> map = stack.get(slice).detectionsMap();
			for (int trackIndex : map.keySet()) {
				if (!isMitosisDetection(trackIndex, slice)) {
					Roi roi = makeRingRoi(slice, trackIndex, dilationRadius);
					setRoi(roi, slice, trackIndex);
				}
			}
		}
	}

	/**
	 * fill detections with information about its track i.e. its parent, number in
	 * track
	 */
	public void setDetectionTrackInformation() {
		for (TrackCTC track : tracks.tracksMap().values()) {
			int trackIndex = track.index();
			int label = 0;
			for (int slice = track.startSlice(); slice <= track.endSlice(); ++slice) {
				Roi roi = detectionRoi(slice, trackIndex);
				int parentIndex = label == 0 ? track.parentIndex() : -1;
				boolean isMitosis = isMitosisDetection(trackIndex, slice);
				String name = CellTrackingGraph.roiName(trackIndex, slice, label, parentIndex, isMitosis);
				roi.setName(name);
				++label;
			}
		}
	}

	public ImagePlus drawMitosis(ImagePlus impOriginal) {
		final ImageStack st = impOriginal.getStack();
		//ImageStack stack = new ImageStack(st.getWidth(), st.getHeight(), st.getSize());
		ImageStack stack = new ImageStack(st.getWidth(), st.getHeight(), st.getSize());
		for (int n=1; n<=stack.getSize(); ++n) { //convert to color processors
			stack.setProcessor(st.getProcessor(n).duplicate().convertToColorProcessor(), n);
		}
		
		int mitosisCount = 0;
		for (Integer parentIndex: tracks.tracksMap().keySet()) {
			TrackCTC parentTrack = tracks.tracksMap().get(parentIndex);
			int childCount = parentTrack._childIndexes.size();
			if (childCount != 2) 
				continue;
			
			int idx1 = parentTrack._childIndexes.get(0);
			int idx2 = parentTrack._childIndexes.get(1);
			
			int sliceParent = parentTrack.endSlice();
			int sliceChild1 = tracks.tracksMap().get(idx1).startSlice();
			int sliceChild2 = tracks.tracksMap().get(idx2).startSlice();

			// draw 3 components from result ctc image by intensity and slices			
			ColorProcessor cpParent = (ColorProcessor) stack.getProcessor(sliceParent+1);
			ColorProcessor cpChild1 = (ColorProcessor) stack.getProcessor(sliceChild1+1);
			ColorProcessor cpChild2 = (ColorProcessor) stack.getProcessor(sliceChild2+1);
			
			drawCTCComponent(_ctcResultImp, cpParent, sliceParent, parentIndex, ColorPicker.color(mitosisCount));
			drawCTCComponent(_ctcResultImp, cpChild1, sliceChild1, idx1, ColorPicker.color(mitosisCount));
			drawCTCComponent(_ctcResultImp, cpChild2, sliceChild2, idx2, ColorPicker.color(mitosisCount));
			
			++mitosisCount;			
		}
		

		ImagePlus result = new ImagePlus("Division Events", stack);
		return result;
	}
	
	public void drawCTCComponent(ImagePlus ctcResult, ColorProcessor cpDrawOn, int slice, int intensity, Color color) {
		ImageProcessor ip = ctcResult.getStack().getProcessor(slice+1);
		for (int y = 0; y < cpDrawOn.getHeight(); y++)
			for (int x = 0; x < cpDrawOn.getWidth(); x++) {
				if (ip.get(x, y) == intensity) {
					cpDrawOn.setColor(color);
					cpDrawOn.drawPixel(x, y);		
				}
			}
	}

	public ImagePlus colorTracks() {
		ImagePlus imp = new ImagePlus();

		return imp;
	}

	public Roi makeRingRoi(int slice, int trackIndex, int dilationRadius) {
		Roi roi = detectionRoi(slice, trackIndex);
		System.out.format("make ring roi for slice %d track %d %n", slice, trackIndex);

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
			roi.setPosition(slice + 1);
			roi.setLocation(xNew, yNew);
		}

		ShapeRoi sr2 = new ShapeRoi(roi);
		sr2 = sr2.xor(sr);
		sr2.setPosition(slice + 1);

		// ImagePlus maskImp = new ImagePlus("new mask", sr2.getMask());
		// maskImp.show();

		return sr2;
	}
}
