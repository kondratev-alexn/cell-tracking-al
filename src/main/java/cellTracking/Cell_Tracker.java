package cellTracking;

import java.awt.AWTEvent;
import java.awt.Scrollbar;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Vector;
import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;
import java.nio.file.Files;
import evaluation.EvaluationFromRoi;
import evaluation.PluginParameters;
import graph.CellTrackingGraph;
import graph.MitosisInfo;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import ij.plugin.Converter;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.FloatProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.StackConverter;
import networkDeploy.UNetSegmentation;
import visualization.Visualization;

public class Cell_Tracker implements ExtendedPlugInFilter, DialogListener {
	
	private int flags = DOES_ALL | KEEP_PREVIEW | FINAL_PROCESSING | NO_CHANGES;

	private int nPasses = 1; // The number of passes (filter directions * color channels * stack slices)
	private int nChannels = 1; // The number of color channels
	private int pass; // Current pass

	private int nSlices;

	/** need to keep the instance of ImagePlus */
	private ImagePlus imagePlus;

	/** keep the original image, to restore it after the preview */
	private ImageProcessor baseImage;

	/** Keep instance of result image */
	private ImageProcessor result;

	/* roi manager for current slice */
	private RoiManager roiManager;

	/* image containing tracking results in ctc format */
	ImagePlus ctcComponents;
	
	/* stacks for saving mitosis start/end results from network */
	ImageStack mitosisStart;
	ImageStack mitosisEnd;

	private int currSlice = 1; // slice number for stack processing
	private int selectedSlice; // currently selected slice by slider

	/* numerical parameters for UI */
	private double softmaxThreshold = 0.50; // now its threshold for lambda2+lambda1 in blob detection
	private int minArea = 50;
	private int maxArea = 1400;
	private float minCircularity = 0.55f;
	private float maxCircularity = 1.0f;
	
	// tracks filtering parameters
	private int minTrackLength = 3;

	private float childPenaltyThreshold = 0.275f;
	private float mitosisStartIntensityCoefficient = 1.00f;


	/* booleans for CheckBoxes */
	private boolean isConfocal = true;
	private boolean trackMitosis = true;

	private boolean filterComponents = true;
	private boolean previewing = false;

	private boolean startedProcessing = false; // comes true after user has selected whether to process stacks or not
	
	private boolean addRois = true; //add rois to roi manager or not
	private boolean noImageJProcessing = false;
	private boolean useWatershedPostProcessing = true;
	private boolean removeBorderComponents = true;
	private boolean saveSegmentation = false;
	
	Path destinationFolder = Paths.get("");

	String ctcTifResult, ctcTxtResult, infoFilePath;

	private boolean saveResultsInFolder = false;

	private ImageComponentsAnalysis prevComponentsAnalysis = null; // for getting masks of segmented cells in next
																	// slices
	private NearestNeighbourTracking tracking = null;

	private UNetSegmentation uNetSegmentation;
	
	public void initPlugin(ImagePlus imp) {
		nSlices = imp.getStackSize();
		tracking = new NearestNeighbourTracking();
		ctcComponents = null;
		ctcTifResult = "";
		ctcTxtResult = "";
		infoFilePath = "";
		
		// convert to float if plugin just started
		if (nSlices == 1) {
			Converter conv = new Converter();
			conv.run("32-bit");
		} else {
			StackConverter stackConv = new StackConverter(imp);
			stackConv.convertToGray32();
		}
		try {
			instancePlugins();
		} catch (IOException | UnsupportedKerasConfigurationException | InvalidKerasConfigurationException e) {
			e.printStackTrace();
		}
	}

	@Override
	public int setup(String arg, ImagePlus imp) {
		if (imp == null) {
			IJ.showMessage("Image is required");
			return DONE;
		}
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}
		
		imagePlus = imp;

		if (arg.equals("final")) {
			// replace the preview image by the original image
			if (!noImageJProcessing) {
				imagePlus.updateAndDraw();
				roiManager.selectAndMakeVisible(imagePlus, -1);
				if (!doesStacks()) {
					return DONE;
				}
			}
			
			String segmentationResultPath = "";
			if (saveSegmentation) {
				String name = imp.getShortTitle() + "_segmentation.tif";
				saveSegmentationResult(Paths.get(System.getProperty("user.dir")), name);
				segmentationResultPath = Paths.get(System.getProperty("user.dir")).resolve(name).toString();
			}

			// there goes tracking
			int maxRadiusDark = 25, slices = 3;
			double oneSliceScoreThreshold = 0.33;
			double scoreThreshold = 0.6;
			double timeDecayCoefficient = 0.3;

			IJ.log("Tracking dark nuclei...");
			ArrayList<ComponentStateLink> linkNormal = new ArrayList<ComponentStateLink>();
			linkNormal.add(new ComponentStateLink(State.NORMAL, State.NORMAL));
			
			ArrayList<ComponentStateLink> linksNormalAndStart = new ArrayList<ComponentStateLink>();
			linksNormalAndStart.add(new ComponentStateLink(State.NORMAL, State.NORMAL));
			linksNormalAndStart.add(new ComponentStateLink(State.NORMAL, State.MITOSIS_START));

			ArrayList<ComponentStateLink> linkEndEnd = new ArrayList<ComponentStateLink>();
			linkEndEnd.add(new ComponentStateLink(State.MITOSIS_END, State.MITOSIS_END));

			ArrayList<ComponentStateLink> linkNormalStart = new ArrayList<ComponentStateLink>();
			linkNormalStart.add(new ComponentStateLink(State.NORMAL, State.MITOSIS_START));

			ArrayList<ComponentStateLink> linkStartStart = new ArrayList<ComponentStateLink>();
			linkStartStart.add(new ComponentStateLink(State.MITOSIS_START, State.MITOSIS_START));

			ArrayList<ComponentStateLink> linkEndNormal = new ArrayList<ComponentStateLink>();
			linkEndNormal.add(new ComponentStateLink(State.MITOSIS_END, State.NORMAL));
			
			// track nuclei only
			tracking.trackComponentsOneSlice(maxRadiusDark, oneSliceScoreThreshold, true, linkNormal, -1);
			tracking.trackComponentsOneSlice(30, oneSliceScoreThreshold+2, true, linksNormalAndStart, -1);
			tracking.trackComponentsOneSlice(100, oneSliceScoreThreshold+10, true, linkNormal, -1);
			tracking.trackComponentsMultiSlice(maxRadiusDark, slices, scoreThreshold, timeDecayCoefficient, true, linkNormal, -1);
//			tracking.trackComponentsMultiSlice(100, slices, scoreThreshold+10, timeDecayCoefficient, true, linkNormal, -1);

			//connect mitosis end to normal
			tracking.trackComponentsOneSlice(40, 0.8, true, linkEndNormal, -1);
//			tracking.trackComponentsMultiSlice(40, 2, 1.2, 0.4f, true, linkEndNormal, -1);
			
			// connect mitosis end components
			tracking.trackComponentsOneSlice(30, 0.7, true, linkEndEnd, -1);
			
			// connect nuclei with mitosis start
			tracking.trackComponentsOneSlice(70, 0.8, true, linkNormalStart, -1);
//			tracking.trackComponentsMultiSlice(70, 2, 0.8, 0.6, true, linkNormalStart, -1);
			
			// connect mit start with itself
			tracking.trackComponentsOneSlice(25, 0.7, true, linkStartStart, -1);
			
			tracking.fillTracksAdj(minTrackLength);
			IJ.log("Tracking dark nuclei finished.");
						
			if (trackMitosis) {
				IJ.log("Tracking mitosis...");

				ArrayList<ComponentStateLink> linksParentChild = new ArrayList<ComponentStateLink>();
				linksParentChild.add(new ComponentStateLink(State.MITOSIS_START, State.MITOSIS_END));
				linksParentChild.add(new ComponentStateLink(State.NORMAL, State.MITOSIS_END));
				linksParentChild.add(new ComponentStateLink(State.MITOSIS_START, State.NORMAL));
				tracking.divisionTracking(60, 1, 10, 0.4f, linksParentChild, null);

				ArrayList<ComponentStateLink> linksParentChildNN = new ArrayList<ComponentStateLink>();
				linksParentChildNN.add(new ComponentStateLink(State.NORMAL, State.NORMAL));
//				tracking.divisionTracking(60, 3, 10, 0.4f, linksParentChildNN, null);
				IJ.log("Mitosis tracking finished");
			}
			
			// draw mitosis start, end on the image for visualisation
			ImagePlus unetMitosisVisualization = Visualization.drawMitosisStartEndFromUnet(imp, mitosisStart, mitosisEnd);
			IJ.save(unetMitosisVisualization, "G:\\Tokyo\\mitosis_vis.tif");
			
			if (!noImageJProcessing) {
				imp.show();
			}

			IJ.log("Displaying results.");
			
			final String separator = System.getProperty("file.separator");

			// prompt a folder to save results
			String trackingResultsDir = destinationFolder.toString() + separator;

			if (saveResultsInFolder) {
				DirectoryChooser dirChoose = new DirectoryChooser("Select a folder to save tracking results.");
				trackingResultsDir = dirChoose.getDirectory();
			}
			if (trackingResultsDir == null)
				trackingResultsDir = System.getProperty("user.dir") + '\\';

			String mitosisInfoFileName = imp.getShortTitle() + "_mitosis_info.ser";
			infoFilePath = trackingResultsDir + mitosisInfoFileName;

			//here mitosis info file is created 
			CellTrackingGraph resultGraph = new CellTrackingGraph(tracking, roiManager, imp, infoFilePath, minTrackLength);

			// TRA components show and save
			String nameTif = imp.getShortTitle() + "_tracking_results";
			String tifPath = trackingResultsDir + nameTif + ".tif";
			ctcTifResult = tifPath;

			// here tracking ctc image is created
			ctcComponents = resultGraph.showTrackedComponentImages(ctcTifResult, true, !noImageJProcessing);
			//resultGraph.printTrackedGraph();
			
//			ImagePlus coloredTrackImage = resultGraph.drawComponentColoredByFullTracks(imp);
//			IJ.save(coloredTrackImage, "colored_tracks.tif");
//			coloredTrackImage.show();

			String txtResultName = imp.getShortTitle() + "_tracking_results.txt";
			String txtPath = trackingResultsDir + txtResultName;
			ctcTxtResult = txtPath;
			// resultGraph.writeTracksToFile_ctc_afterAnalysis(txtResultName);
			resultGraph.writeTracksToFile_ctc_afterAnalysis(ctcTxtResult, false);
			IJ.log("Text result file created at: " + ctcTxtResult);		
			
			if (!segmentationResultPath.isEmpty()) {
				//move segmentation results to selected directory 
				String name = imp.getShortTitle() + "_segmentation.tif";
				try {					
					Files.move(Paths.get(segmentationResultPath), Paths.get(trackingResultsDir).resolve(name), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			//reading back to display in color
			MitosisInfo mitosisInfo = MitosisInfo.DeserializeMitosisInfo(infoFilePath);
			if (mitosisInfo == null)
				mitosisInfo = new MitosisInfo();
			currSlice = 1;
			return DONE;
		}

		nSlices = imp.getStackSize();
		tracking = new NearestNeighbourTracking();
		addRois = true;
		ctcComponents = null;
		ctcTifResult = "";
		ctcTxtResult = "";
		infoFilePath = "";
		
		saveResultsInFolder = !arg.equals("no save");

		mitosisStart = new ImageStack(imp.getWidth(), imp.getHeight(), nSlices);
		mitosisEnd = new ImageStack(imp.getWidth(), imp.getHeight(), nSlices);

		if (saveResultsInFolder) {
			System.out.println("Saving in folder");
		}

		// convert to float if plugin just started
		if (nSlices == 1) {
			Converter conv = new Converter();
			conv.run("32-bit");
		} else {
			StackConverter stackConv = new StackConverter(imp);
			stackConv.convertToGray32();
		}
		try {
			instancePlugins();
		} catch (IOException | UnsupportedKerasConfigurationException | InvalidKerasConfigurationException e) {
			e.printStackTrace();
		}
		// here maybe add flags like flags |= DOES_NOTHING
		return flags;
	}

	public String textResultsPath() {
		return ctcTxtResult;
	}

	public String tifResultPath() {
		return ctcTifResult;
	}

	public String mitosisInfoFilePath() {
		return infoFilePath;
	}

	/*
	 * creates instances of used plugins
	 */
	private void instancePlugins() throws IOException, UnsupportedKerasConfigurationException, InvalidKerasConfigurationException {
		String model_name = "model_fluo_resunet_mitosis_512_weights.h5";
		uNetSegmentation = new UNetSegmentation(model_name);
	}
	
	public void setParameters(ImagePlus imp, PluginParameters parameters) {
		setParameters(imp, parameters.softmaxThreshold, 
				parameters.minArea, 
				parameters.maxArea, 
				parameters.minCirc, 
				parameters.maxCirc, 
				parameters.minTrackLength, 
				parameters.trackMitosis,
				parameters.filterComponents, 
				parameters.useWatershedPostProcessing,
				parameters.removeBorderDetections,
				parameters.addRois, 
				parameters.noImageJProcessing,
				parameters.saveSegmentation,
				parameters.destinationFolder);
	}
	
	public void setParameters(ImagePlus imp, float softmaxThreshold, int minArea, int maxArea, float minCirc, float maxCirc,
			int minTrackLength, boolean trackMitosis, boolean filterComponents, boolean useWatershedPostProcessing, 
			boolean removeBorderDetections, 
			boolean addRois, boolean noImageJProcessing, boolean saveSegmentation, Path destinationFolder) {
		nChannels = imp.getProcessor().getNChannels();
		currSlice = 1; // for when algorithm starts processing stack
		selectedSlice = 1;
		startedProcessing = true;
		previewing = false;

		roiManager = RoiManager.getInstance();
		if (roiManager == null)
			roiManager = new RoiManager();
		roiManager.reset();		
		
		this.softmaxThreshold = softmaxThreshold;
		this.minArea = minArea;
		this.maxArea = maxArea;
		this.minCircularity = minCirc;
		this.maxCircularity = maxCirc;
		this.minTrackLength = minTrackLength;
		this.trackMitosis = trackMitosis;
		this.filterComponents = filterComponents;
		this.useWatershedPostProcessing = useWatershedPostProcessing;
		this.removeBorderComponents = removeBorderDetections;
		this.addRois = addRois;
		this.noImageJProcessing = noImageJProcessing;
		this.saveSegmentation = saveSegmentation;
		this.destinationFolder = destinationFolder;
	}

	@Override
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
		// Normal setup
		this.imagePlus = imp;
		this.baseImage = imp.getProcessor().duplicate();

		nChannels = imp.getProcessor().getNChannels();
		currSlice = 1; // when algorithm starts processing stack
		selectedSlice = imp.getCurrentSlice();

		GenericDialog gd = new GenericDialog(command);
		fillGenericDialog(gd, pfr);

		gd.showDialog(); // input by the user (or macro) happens here		

		if (gd.wasCanceled()) {
			resetPreview();
			return DONE;
		}

		IJ.register(this.getClass()); // protect static class variables (parameters) from garbage collection
		// return flags;
		flags = IJ.setupDialog(imp, flags); // ask whether to process all slices of stack (if a stack)

		// after the answer, the processing is started. So show roiManager and set flag
		startedProcessing = true;
		previewing = false;

		// here reset everything like preview etc.
		currSlice = doesStacks() ? 1 : selectedSlice;
		roiManager = RoiManager.getInstance();
		if (roiManager == null)
			roiManager = new RoiManager();
		roiManager.reset();

		IJ.log("Starting segmentation.");
		return flags;
	}

	private void fillGenericDialog(GenericDialog gd, PlugInFilterRunner pfr) {		
		gd.addNumericField("Softmax threshold", softmaxThreshold, 2);
		gd.addNumericField("Min area", minArea, 0);
		gd.addNumericField("Max area", maxArea, 0);
		gd.addNumericField("Min circularity", minCircularity, 3);
		gd.addNumericField("Max circularity", maxCircularity, 3);
		gd.addNumericField("Minimum track length", minTrackLength, 0);
		gd.addNumericField("Bright blob childs threshold", childPenaltyThreshold, 3);
		gd.addNumericField("Intensity change coefficient (mitosis)", mitosisStartIntensityCoefficient, 3);
		gd.addCheckbox("Confocal data", isConfocal);
		gd.addCheckbox("Track Mitosis", trackMitosis);
		gd.addCheckbox("Filter components", filterComponents);
		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		gd.addSlider("Slice", 1, nSlices, selectedSlice);
	}

	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		boolean wasPreview = this.previewing;
		resetPreview();
		parseDialogParameters(gd);
		imagePlus.setSlice(selectedSlice);
		baseImage = imagePlus.getStack().getProcessor(selectedSlice).duplicate();

		// if preview checkbox was unchecked, replace the preview image by the original
		if (wasPreview && !this.previewing) {
			resetPreview();
		}
		return true;
	}

	// extract chosen parameters
	private void parseDialogParameters(GenericDialog gd) {
		softmaxThreshold = gd.getNextNumber();
		minArea = (int) gd.getNextNumber();
		maxArea = (int) gd.getNextNumber();
		minCircularity = (float) gd.getNextNumber();
		maxCircularity = (float) gd.getNextNumber();
		minTrackLength = (int) gd.getNextNumber();
		childPenaltyThreshold = (float) gd.getNextNumber();
		mitosisStartIntensityCoefficient = (float) gd.getNextNumber();

		isConfocal = gd.getNextBoolean();
		trackMitosis = gd.getNextBoolean();
		filterComponents = gd.getNextBoolean();

		previewing = gd.getPreviewCheckbox().getState();

		// change image if current slice changed
		Vector<?> sliders = gd.getSliders();
		selectedSlice = ((Scrollbar) sliders.get(0)).getValue();
		currSlice = selectedSlice;
	}

	@Override
	public void setNPasses(int nPasses) {
		// this.nPasses = 2 * nChannels * nPasses;
		this.nPasses = nPasses;
		pass = 0;
	}

	private boolean doesStacks() {
		return (flags & DOES_STACKS) != 0;
	}

	@Override
	public void run(ImageProcessor ip) {
		if (startedProcessing) { // stack processing
			if (previewing) // if preview was on, return the stack to original images
				resetPreview();
			result = ip.duplicate();
		} else
			result = baseImage.duplicate();

		
		//result = maximaWatershedSegmentation(result, ip, sigma3, minThreshold, maxThreshold);
		if (!isConfocal) {
			//we need preprocessing for epifluorescence data (for old version of weights)
//			result = preprocessing(result, 1.4f, 60, true);
		}
		
		/* create a stack from previous, current and next slice */
		ImageStack stack3 = create3Stack();
		ImageProcessor[] watershedResMitosisMasks = uNetSegmentation.binarySegmentationFromMaskWithMarkers(stack3, 0.5f, 0.6f);
		
		mitosisStart.setProcessor(watershedResMitosisMasks[1], currSlice);
		mitosisEnd.setProcessor(watershedResMitosisMasks[2], currSlice);
		
		ImageProcessor allComps = watershedResMitosisMasks[0].duplicate();
		ImageProcessorCalculator.add(allComps, watershedResMitosisMasks[1]);
		ImageProcessorCalculator.add(allComps, watershedResMitosisMasks[2]);

		ImageComponentsAnalysis compMitosisStart = new ImageComponentsAnalysis(watershedResMitosisMasks[1], ip, true); 
		ImageComponentsAnalysis compMitosisEnd = new ImageComponentsAnalysis(watershedResMitosisMasks[2], ip, true); 
		compMitosisStart.getFilteredComponentsIp(50, 1000, 0.6f, 1.0f, 0, 1000, false, removeBorderComponents);
		compMitosisStart.improveMitosisEndComponentContours();
		compMitosisEnd.getFilteredComponentsIp(50, 1000, 0.6f, 1.0f, 0, 1000, false, removeBorderComponents);
		
		ImageProcessor mitStartMarkers = compMitosisStart.getSinglePixelComponents();
		ImageProcessor mitEndMarkers = compMitosisEnd.getSinglePixelComponents();
		
		// from fillComponentProperties function...
		if (filterComponents) {
			// get labeled component image and fill
			ImageComponentsAnalysis compAnalisys = new ImageComponentsAnalysis(allComps, ip, true); 

			ip = compAnalisys.getFilteredComponentsIp(minArea, maxArea, minCircularity, maxCircularity, 0, 1000,
					false, removeBorderComponents);
			
			// here set component's state by marks image
			compAnalisys.setComponentsStateByMarks(mitStartMarkers, State.MITOSIS_START);
			compAnalisys.setComponentsStateByMarks(mitEndMarkers, State.MITOSIS_END);
			// works fine
			
			if (startedProcessing && addRois) // add roi only if we started processing
				compAnalisys.addRoisToManager(roiManager, imagePlus, currSlice, "");

			if (startedProcessing || doesStacks()) {
				prevComponentsAnalysis = compAnalisys;
				tracking.addComponentsAnalysis(compAnalisys);
			}
		}

		result = result.convertToFloatProcessor();
		result.resetMinAndMax();

		if (startedProcessing) { // process stacks
			++currSlice;
		}

		if (previewing && !startedProcessing) {
			// Fill up the values of original image with values of the result
			for (int i = 0; i < ip.getPixelCount(); i++) {
				ip.setf(i, result.getf(i));
			}
			ip.resetMinAndMax();
		}
	}
	
	public void runOnImagePlus(ImagePlus imp) {
		ImageStack stack = imp.getStack();
		for (int i=1; i<=stack.getSize(); ++i) {
			ImageProcessor ip = stack.getProcessor(i);
			run(ip);
		}
	}
	
	public void saveSegmentationResult(Path dir, String name) {
		ImagePlus segmentation = tracking.segmentationResult();
		IJ.save(segmentation, dir.resolve(name).toString());
	}
	
	public void loadSegmentationByImage(ImagePlus segmentation, ImagePlus intensityImp, boolean removeBorderComponents) {
		ImageStack stack = segmentation.getStack();
		tracking.clearComponentsList();
		for (int i=0; i<stack.getSize(); ++i) {
			ImageComponentsAnalysis comps = new ImageComponentsAnalysis(stack.getProcessor(i+1).duplicate(), 
					intensityImp.getStack().getProcessor(i+1).duplicate(), true);
			comps.filterComponents(minArea, maxArea, minCircularity, maxCircularity, 0, 1000, false, removeBorderComponents);
			tracking.addComponentsAnalysis(comps);
		}
	}
	
	public void loadSegmentationByPath(Path segmentationResultPath, ImagePlus intensityImp, boolean removeBorderComponents) {
		ImagePlus segmentation = new ImagePlus(segmentationResultPath.toString());
		loadSegmentationByImage(segmentation, intensityImp, removeBorderComponents);
	}
	
	/* creates stack from current imagePlus and current slice */
	private ImageStack create3Stack() {
		int w = imagePlus.getWidth();
		int h = imagePlus.getHeight();
		ImageStack res = new ImageStack(w,h,3);
		if (currSlice==1) {
			res.setProcessor(imagePlus.getStack().getProcessor(1).duplicate(), 1);
			res.setProcessor(imagePlus.getStack().getProcessor(1).duplicate(), 2);
			res.setProcessor(imagePlus.getStack().getProcessor(2).duplicate(), 3);
			return res;
		}
		
		int lastSlice = imagePlus.getStack().getSize(); 
		if (currSlice==lastSlice) {
			res.setProcessor(imagePlus.getStack().getProcessor(lastSlice-1).duplicate(), 1);
			res.setProcessor(imagePlus.getStack().getProcessor(lastSlice).duplicate(), 2);
			res.setProcessor(imagePlus.getStack().getProcessor(lastSlice).duplicate(), 3);
			return res;
		}
		
		for  (int i=-1; i<=1; ++i) { 
			res.setProcessor(imagePlus.getStack().getProcessor(currSlice+i).duplicate(), i+2);
		}
		
		return res;
	}

	private ImageProcessor fillComponentProperties(ImageProcessor ip, ImageProcessor intensityImg, boolean addBrightMarkers, ImageProcessor marksBrightBinary,
			boolean addToRoiManager) {
		if (filterComponents) {
			// get labeled component image and fill
			ImageComponentsAnalysis compAnalisys = new ImageComponentsAnalysis(ip, intensityImg, true); 

			if (addBrightMarkers) {
				compAnalisys.setComponentsStateByMarks(marksBrightBinary, State.MITOSIS);
			}

			boolean discardWhiteBlobs = addBrightMarkers;
			ip = compAnalisys.getFilteredComponentsIp(minArea, maxArea, minCircularity, maxCircularity, 0, 1000,
					discardWhiteBlobs, removeBorderComponents);
			
			// here set component's state by marks image (indicate bright blobs)
			if (startedProcessing && addRois && addToRoiManager) // add roi only if we started processing
				compAnalisys.addRoisToManager(roiManager, imagePlus, currSlice, "");

			if (startedProcessing || doesStacks()) {
				prevComponentsAnalysis = compAnalisys;
				tracking.addComponentsAnalysis(compAnalisys);
			}
		}
		return ip;
	}

	private void resetPreview() {
		ImageProcessor image;
		image = imagePlus.getProcessor();
		if (image instanceof FloatProcessor) {
			for (int i = 0; i < image.getPixelCount(); i++)
				image.setf(i, this.baseImage.getf(i));
		} else {
			for (int i = 0; i < image.getPixelCount(); i++)
				image.set(i, this.baseImage.get(i));
		}
		imagePlus.resetDisplayRange();
		imagePlus.updateAndDraw();
	}

	public void showAbout() {
		IJ.showMessage("Cell tracking", "A cell tracking method for fluorescent microscopy images");
	}

	public static void main(String[] args) {
		boolean testImageJ = true;
		boolean traConvert = false;
		// traConvert = true;
		if (traConvert) {
			EvaluationFromRoi eval = new EvaluationFromRoi();
			new ImageJ();
			String roiFilePath = "G:\\Tokyo\\trackingResults\\c0010901_easy_ex-matchedROI.zip";
			String imageFilePath = "G:\\Tokyo\\example_sequences\\c0010901_easy_ex.tif";

			roiFilePath = "G:\\Tokyo\\trackingResults\\c0010907_easy_ex-matchedROI.zip";
			imageFilePath = "G:\\Tokyo\\example_sequences\\c0010907_easy_ex.tif";

			roiFilePath = "G:\\Tokyo\\trackingResults\\c0010906_medium_double_nuclei_ex-matchedROI.zip";
			imageFilePath = "G:\\Tokyo\\example_sequences\\c0010906_medium_double_nuclei_ex.tif";

			roiFilePath = "G:\\Tokyo\\trackingResults\\c0010913_hard_ex-matchedROI.zip";
			imageFilePath = "G:\\Tokyo\\example_sequences\\c0010913_hard_ex.tif";

			eval.convertToTRAformat(roiFilePath, imageFilePath);
			return;
		}
		if (!testImageJ) {
			System.out.println("Test");
			// tra.writeTracksToFile_ctc("tracks.txt", null);
		} else {
			// set the plugins.dir property to make the plugin appear in the Plugins menu
			Class<?> clazz = Cell_Tracker.class;
			String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
			String pluginsDir = url.substring("file:".length(),
					url.length() - clazz.getName().length() - ".class".length());
			System.setProperty("plugins.dir", pluginsDir);
			System.out.println(System.getProperty("os.arch"));
			// start ImageJ
			new ImageJ();
			ImagePlus image;
			
			// ImagePlus image_stack20 = IJ.openImage("G:\\Tokyo\\C002_Movement.tif");
			// ImagePlus image_c14 =
			// IJ.openImage("G:\\Tokyo\\170704DataSeparated\\C0002\\c0010914_C002.tif");
			// ImagePlus image_stack3 = IJ.openImage("G:\\Tokyo\\\\movement_3images.tif");
			ImagePlus image_stack10 = IJ.openImage("G:\\Tokyo\\c01_11_slices.tif");
			// ImagePlus image_shorter_bright_blobs =
			// IJ.openImage("G:\\Tokyo\\Short_c1_ex.tif");

//			ImagePlus image_ex_01 = IJ.openImage("G:\\Tokyo\\example_sequences\\c0010901_easy_ex.tif");
//			ImagePlus image_ex_06 = IJ.openImage("G:\\Tokyo\\example_sequences\\c0010906_medium_double_nuclei_ex.tif");
//			ImagePlus image_ex_07 = IJ.openImage("G:\\Tokyo\\example_sequences\\c0010907_easy_ex.tif");
			ImagePlus image_ex_13 = IJ.openImage("G:\\Tokyo\\example_sequences\\c0010913_hard_ex.tif");

//			ImagePlus confocal_1 = IJ.openImage("G:\\Tokyo\\Confocal\\181221-q8156901-tiff\\c2\\181221-q8156901hfC2c2.tif");

//			image = image_ex_01;
//			 image = image_ex_07;
			// image = image_stack20;
//			image = image_stack10;
			// image = image_stack3;
			// image = image_c10;
			// image = image_ez_division;
			// image = image_test_tracking;
			// image = image_shorter_bright_blobs;
//			image = image_ex_06;
			image = image_ex_13;
			//image = confocal_1;
			ImageConverter converter = new ImageConverter(image);
			converter.convertToGray32();
			image.show();

			// run the plugin
			IJ.runPlugIn(clazz.getName(), "");
		}
	}
}
