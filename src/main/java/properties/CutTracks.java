package properties;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import graph.MitosisInfo;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import net.imglib2.roi.RectangleRegionOfInterest;

/**
 * Class to cut stacks of cell images in a single track for registration test
 *
 */
public class CutTracks {

	public CutTracks() {
		
	}
	
	ImagePlus getImpFromTRAFolder(String path) {
		return null;		
	}
	
	static void dosmth(String tifPath, String txtPath, String stackPath, String seqFolderName, int cutRadius) {
		// first, load tracks and original images in internal format (should use previously coded one)
		ImagePlus imp_res = new ImagePlus(tifPath);

		MitosisInfo mitosisInfo = new MitosisInfo();
		
		StackDetection stackDetection = new StackDetection();
		stackDetection.fillStack(imp_res, mitosisInfo);
		System.out.println("Stack filled");
		IJ.log("Stack with ROIs filled");

		stackDetection.fillTracks(txtPath);
		System.out.println("Tracks map filled");
		IJ.log("Track information filled");

		System.out.println("");
		System.out.println(mitosisInfo.toString());
		stackDetection.setDetectionTrackInformation();

		boolean sort = true;
		stackDetection.addToRoiManager(sort);
		
		ImagePlus imp = IJ.openImage(stackPath);
		
		HashMap<Integer, TrackCTC> tracks = stackDetection.tracks().tracksMap();
		for (TrackCTC track: tracks.values()) {
			// create folder for the current track
		    String directoryName = seqFolderName.concat(System.getProperty("file.separator") + "track_" + track.index());

		    File directory = new File(directoryName);
		    if (! directory.exists()){
		        directory.mkdirs();
		    }
			
			// cut image in each slice
			for (int i=track.startSlice(); i<=track.endSlice();++i) {
				Roi roi = stackDetection.detectionRoi(i, track.index());
			    double[] centroid = roi.getContourCentroid();
			    int centerX = (int)Math.round(centroid[0]);
			    int centerY = (int)Math.round(centroid[1]);
			    Roi cropRoi = new Roi(centerX-cutRadius, centerY-cutRadius, cutRadius*2+1, cutRadius*2+1);
//			    System.out.println(centerX + ":" + centerY);
			    
			    ImageProcessor temp = imp.getStack().getProcessor(i+1).duplicate();
			    temp.setRoi(cropRoi);
			    ImageProcessor crop = temp.crop();
			    String pathSave = directoryName + System.getProperty("file.separator") + "crop"+i+".tif";
			    IJ.save(new ImagePlus("crop"+i, crop), pathSave);
			}
		}
	}
	
	public static void main(String[] args) {
		ArrayList<String> imageList = new ArrayList<String>();
		// for images itself
//		imageList.add("G:\\Tokyo\\metrics\\Tracking GT\\c0010901_easy_ex-8bit.tif");
//		imageList.add("G:\\Tokyo\\metrics\\Tracking GT\\c0010906_medium_double_nuclei_ex-8bit.tif");
//		imageList.add("G:\\Tokyo\\metrics\\Tracking GT\\c0010907_easy_ex-8bit.tif");
//		imageList.add("G:\\Tokyo\\metrics\\Tracking GT\\c0010913_hard_ex-8bit.tif");
//		imageList.add("G:\\Tokyo\\CTC Datasets\\Fluo-N2DH-GOWT1\\01.tif");
//		imageList.add("G:\\Tokyo\\CTC Datasets\\Fluo-N2DH-GOWT1\\02.tif");
//		imageList.add("G:\\Tokyo\\CTC Datasets\\Fluo-N2DL-HeLa\\01.tif");
//		imageList.add("G:\\Tokyo\\CTC Datasets\\Fluo-N2DL-HeLa\\02.tif");
		
		// for masks
		imageList.add("G:\\Tokyo\\metrics\\Stacks masks\\seq01_masks.tif");
		imageList.add("G:\\Tokyo\\metrics\\Stacks masks\\seq06_masks.tif");
		imageList.add("G:\\Tokyo\\metrics\\Stacks masks\\seq07_masks.tif");
		imageList.add("G:\\Tokyo\\metrics\\Stacks masks\\seq13_masks.tif");
		
		imageList.add("G:\\Tokyo\\CTC Datasets\\Fluo-N2DH-GOWT1\\01_SEG_GT.tif");
		imageList.add("G:\\Tokyo\\CTC Datasets\\Fluo-N2DH-GOWT1\\02_SEG_GT.tif");
		imageList.add("G:\\Tokyo\\CTC Datasets\\Fluo-N2DL-HeLa\\01_SEG_GT.tif");
		imageList.add("G:\\Tokyo\\CTC Datasets\\Fluo-N2DL-HeLa\\02_SEG_GT.tif");
		ArrayList<String> txtList = new ArrayList<String>();
		txtList.add("G:\\Tokyo\\metrics\\Tracking GT\\c0010901_easy_ex-8bit-tracks.txt");
		txtList.add("G:\\Tokyo\\metrics\\Tracking GT\\c0010906_medium_double_nuclei_ex-8bit-tracks.txt");
		txtList.add("G:\\Tokyo\\metrics\\Tracking GT\\c0010907_easy_ex-8bit-tracks.txt");
		txtList.add("G:\\Tokyo\\metrics\\Tracking GT\\c0010913_hard_ex-8bit-tracks.txt");
		txtList.add("G:\\Tokyo\\CTC Datasets\\Fluo-N2DH-GOWT1\\01_GT\\TRA\\man_track.txt");
		txtList.add("G:\\Tokyo\\CTC Datasets\\Fluo-N2DH-GOWT1\\02_GT\\TRA\\man_track.txt");
		txtList.add("G:\\Tokyo\\CTC Datasets\\Fluo-N2DL-HeLa\\01_GT\\TRA\\man_track.txt");
		txtList.add("G:\\Tokyo\\CTC Datasets\\Fluo-N2DL-HeLa\\02_GT\\TRA\\man_track.txt");
		ArrayList<String> traList = new ArrayList<String>();
		traList.add("G:\\Tokyo\\metrics\\Tracking GT\\c0010901_easy_ex-8bit-tracks.tif");
		traList.add("G:\\Tokyo\\metrics\\Tracking GT\\c0010906_medium_double_nuclei_ex-8bit-tracks.tif");
		traList.add("G:\\Tokyo\\metrics\\Tracking GT\\c0010907_easy_ex-8bit-tracks.tif");
		traList.add("G:\\Tokyo\\metrics\\Tracking GT\\c0010913_hard_ex-8bit-tracks.tif");
		traList.add("G:\\Tokyo\\CTC Datasets\\Fluo-N2DH-GOWT1\\01_TRA_GT.tif");
		traList.add("G:\\Tokyo\\CTC Datasets\\Fluo-N2DH-GOWT1\\02_TRA_GT.tif");
		traList.add("G:\\Tokyo\\CTC Datasets\\Fluo-N2DL-HeLa\\01_TRA_GT.tif");
		traList.add("G:\\Tokyo\\CTC Datasets\\Fluo-N2DL-HeLa\\02_TRA_GT.tif");
		ArrayList<String> saveFolderList = new ArrayList<String>();
//		saveFolderList.add("G:\\Tokyo\\Cropped Sequences\\c01");
//		saveFolderList.add("G:\\Tokyo\\Cropped Sequences\\c06");
//		saveFolderList.add("G:\\Tokyo\\Cropped Sequences\\c07");
//		saveFolderList.add("G:\\Tokyo\\Cropped Sequences\\c13");
//		saveFolderList.add("G:\\Tokyo\\Cropped Sequences\\Fluo-N2DH-GOWT1_01");
//		saveFolderList.add("G:\\Tokyo\\Cropped Sequences\\Fluo-N2DH-GOWT1_02");
//		saveFolderList.add("G:\\Tokyo\\Cropped Sequences\\Fluo-N2DL-HeLa_01");
//		saveFolderList.add("G:\\Tokyo\\Cropped Sequences\\Fluo-N2DL-HeLa_02");
		

		saveFolderList.add("G:\\Tokyo\\Cropped Sequences\\c01_masks");
		saveFolderList.add("G:\\Tokyo\\Cropped Sequences\\c06_masks");
		saveFolderList.add("G:\\Tokyo\\Cropped Sequences\\c07_masks");
		saveFolderList.add("G:\\Tokyo\\Cropped Sequences\\c13_masks");
		saveFolderList.add("G:\\Tokyo\\Cropped Sequences\\Fluo-N2DH-GOWT1_01_masks");
		saveFolderList.add("G:\\Tokyo\\Cropped Sequences\\Fluo-N2DH-GOWT1_02_masks");
		saveFolderList.add("G:\\Tokyo\\Cropped Sequences\\Fluo-N2DL-HeLa_01_masks");
		saveFolderList.add("G:\\Tokyo\\Cropped Sequences\\Fluo-N2DL-HeLa_02_masks");
		ArrayList<Integer> cutRadii = new ArrayList<Integer>();
		cutRadii.add(20);
		cutRadii.add(20);
		cutRadii.add(20);
		cutRadii.add(20);
		cutRadii.add(50);
		cutRadii.add(50);
		cutRadii.add(25);
		cutRadii.add(25);
		
		for (int i=0; i<imageList.size(); ++i) {
			dosmth(traList.get(i), txtList.get(i), imageList.get(i), saveFolderList.get(i), cutRadii.get(i));
		}
		System.out.println("Finish");
	}
}
