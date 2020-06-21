package cellTracking;

import java.awt.AWTEvent;
import java.awt.Scrollbar;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Vector;

import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;
import java.nio.file.Files;

//import com.google.common.io.Files;

import evaluation.EvaluationFromRoi;
import evaluation.PluginParameters;
import graph.CellTrackingGraph;
import graph.MitosisInfo;
import histogram.FloatHistogram;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import ij.plugin.Converter;
import ij.plugin.filter.BackgroundSubtracter;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.filter.RankFilters;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.StackConverter;
import inra.ijpb.binary.BinaryImages;
import inra.ijpb.binary.ChamferWeights;
import inra.ijpb.morphology.Morphology.Operation;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.Strel.Shape;
import inra.ijpb.watershed.ExtendedMinimaWatershed;
import inra.ijpb.watershed.MarkerControlledWatershedTransform2D;
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
	//
	/* for storing thresholded intensity image */
	private ImageProcessor thresholdedIntensity;

	/** Contains mask for previously segmented cells */
	private ImageProcessor cellMask;

	/* roi manager for current slice */
	private RoiManager roiManager;

	/* image containing tracking results in ctc format */
	ImagePlus ctcComponents;
	
	/* stacks for saving mitosis start/end results from network */
	ImageStack mitosisStart;
	ImageStack mitosisEnd;

	private int currSlice = 1; // slice number for stack processing
	private int selectedSlice; // currently selected slice by slider

	// sigmas for bandpass algorithm, also in UI
	public double sigma1 = 1.40;
	public double sigma2 = 10.00;
	public double sigma3 = 0.80;

	/* numerical parameters for UI */
	private double softmaxThreshold = 0.50; // now its threshold for lambda2+lambda1 in blob detection
	private double heightToleranceBright = 0.20;
	private int rollingBallRadius = 20; // for background subtraction
	private int topHatRadius = 20;
	private double gaussianSigma = 2;
	private double minThreshold = 20;
	private double maxThreshold = 50;
	private int minArea = 100;
	private int maxArea = 1400;
	private float minCircularity = 0.55f;
	private float maxCircularity = 1.0f;
	private int dilationRadius = 1;
	private int maximumNumberOfBlobs = 60; // how many dark blobs will be detected
	
	// tracks filtering parameters
	private int minTrackLength = 5;

	private float blobMergeThreshold = 0.32f; // threshold, below which blobs will be merged
	private float childPenaltyThreshold = 0.275f;
	private float mitosisStartIntensityCoefficient = 1.00f;

	private float[] sigmas = { 6, 9, 12, 16, 32 };

	/* booleans for CheckBoxes */
	private boolean isConfocal = true;
	private boolean isTestMode = false;
	private boolean useGaussian = true;
	private boolean isBandpass = false;
	private boolean trackMitosis = false;

	private boolean showImageForWatershedding = false;
	private boolean filterComponents = true;
	private boolean previewing = false;

	private boolean startedProcessing = false; // comes true after user has selected whether to process stacks or not
	private boolean showBlobs = false;
	private boolean useMaximumFinder = false; //whether to use maxFinder plugin or blob detection
	
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

	private final float sigmaMax = 50; // max value of sigmas

	// plugins
	private Gaussian gaussian;
	private BackgroundSubtracter backgroundSub;
	private RankFilters rankFilters;
	private UNetSegmentation uNetSegmentation;
	
	public void initPlugin(ImagePlus imp) {
		nSlices = imp.getStackSize();
		tracking = new NearestNeighbourTracking();
		cellMask = null;
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
			// resetPreview();
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
			int maxRadiusDark = 25, maxRadiusBright = 18, slices = 3;
			double oneSliceScoreThreshold = 0.33;
			double scoreThreshold = 0.6;
			double timeDecayCoefficient = 0.3;
			// tracking.trackComponents(maxRadiusDark, maxRadiusBright, slices,
			// scoreThreshold);
			// tracking.trackComponentsOneAndMultiSlice(maxRadiusDark, slices,
			// scoreThreshold, oneSliceScoreThreshold, timeDecayCoefficient);

			IJ.log("Tracking dark nuclei...");
			tracking.trackComponentsOneSlice(maxRadiusDark, oneSliceScoreThreshold);
			tracking.trackComponentsMultiSlice(maxRadiusDark, slices, scoreThreshold, timeDecayCoefficient);
			tracking.fillTracksAdj(minTrackLength);
			IJ.log("Tracking dark nuclei finished.");

			// old version
//			if (trackMitosis) {
//				IJ.log("Tracking mitosis...");
//				// check for mitosis start by two ideas (intensity change / bright blob nearby)
//				tracking.analyzeTracksForMitosisByAverageIntensity(mitosisStartIntensityCoefficient);
//				tracking.analyzeTracksForMitosisByWhiteBlob(0.5f);				
//				tracking.startMitosisTracking(30, childPenaltyThreshold);
//				IJ.log("Mitosis tracking finished");
//			}
			
			if (trackMitosis) {
				IJ.log("Tracking mitosis...");
				tracking.divisionTracking(75, 3, 10, 0.4f);				
				IJ.log("Mitosis tracking finished");
			}
			
			// draw mitosis start, end on the image for visualisation
			ImagePlus unetMitosisVisualization = Visualization.drawMitosisStartEndFromUnet(imp, mitosisStart, mitosisEnd);
			IJ.save(unetMitosisVisualization, "G:\\Tokyo\\mitosis_vis.tif");

			// System.out.println(tracking.getGraph());
			// System.out.println(tracking.getGraph().checkNoEqualNodes());
			// ImageProcessor ip = imp.getProcessor();
			// tracking.drawTracksIp(ip);
			// ImagePlus trResult = tracking.drawTracksImagePlus(imp);

			if (!noImageJProcessing) {
				imp.show();
			}
			
			// save mitosis start and end results for now
			IJ.save(new ImagePlus("start", mitosisStart), "G:\\Tokyo\\mitosis_start.tif");
			IJ.save(new ImagePlus("end", mitosisEnd), "G:\\Tokyo\\mitosis_end.tif");

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
		cellMask = null;
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
		gaussian = new Gaussian();
		backgroundSub = new BackgroundSubtracter();
		rankFilters = new RankFilters();
//		String model_name = "unet_confocal_fluo_model_full.h5";
//		String model_name = "resunet_3stack_model_full.h5";
		String model_name = "model_fluo_resunet_mitosis_512_weights.h5";
		uNetSegmentation = new UNetSegmentation(model_name);
//		foo mess
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
		minCircularity = minCirc;
		maxCircularity = maxCirc;
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
		currSlice = 1; // for when algorithm starts processing stack
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
		// gd.addNumericField("Dilation Radius (postprocessing)", dilationRadius, 0);
		gd.addNumericField("Minimum track length", minTrackLength, 0);
		gd.addNumericField("Bright blob childs threshold", childPenaltyThreshold, 3);
		gd.addNumericField("Intensity change coefficient (mitosis)", mitosisStartIntensityCoefficient, 3);
		// gd.addCheckbox("test mode", isTestMode);
		// gd.addCheckbox("Show Image before Watershedding", showImageForWatershedding);
		gd.addCheckbox("Confocal data", isConfocal);
		gd.addCheckbox("Track Mitosis", trackMitosis);
		gd.addCheckbox("Filter components", filterComponents);
//		gd.addCheckbox("Use MaximumFinder filter", useMaximumFinder);
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

		if (sigma1 < 0 || sigma2 < 0 || sigma3 < 0 || gd.invalidNumber())
			return false;

		// if preview checkbox was unchecked, replace the preview image by the original
		if (wasPreview && !this.previewing) {
			resetPreview();
		}
		return true;
	}

	// extract chosen parameters
	private void parseDialogParameters(GenericDialog gd) {
//		maximumNumberOfBlobs = (int) gd.getNextNumber();
//		gaussianSigma = gd.getNextNumber();
//		rollingBallRadius = (int) gd.getNextNumber();
//		sigma3 = gd.getNextNumber();
		softmaxThreshold = gd.getNextNumber();
		minArea = (int) gd.getNextNumber();
		maxArea = (int) gd.getNextNumber();
		minCircularity = (float) gd.getNextNumber();
		maxCircularity = (float) gd.getNextNumber();
		minTrackLength = (int) gd.getNextNumber();
		// dilationRadius = (int) gd.getNextNumber();
//		blobMergeThreshold = (float) gd.getNextNumber();
		childPenaltyThreshold = (float) gd.getNextNumber();
		mitosisStartIntensityCoefficient = (float) gd.getNextNumber();

		// isTestMode = gd.getNextBoolean();
		// showImageForWatershedding = gd.getNextBoolean();
		isConfocal = gd.getNextBoolean();
		trackMitosis = gd.getNextBoolean();
//		showBlobs = gd.getNextBoolean();
		filterComponents = gd.getNextBoolean();
//		useMaximumFinder = gd.getNextBoolean();

		previewing = gd.getPreviewCheckbox().getState();

		// change image if current slice changed
		Vector sliders = gd.getSliders();
		selectedSlice = ((Scrollbar) sliders.get(0)).getValue();
		currSlice = selectedSlice;

		if (sigma1 > sigmaMax)
			sigma1 = sigmaMax;
		if (sigma2 > sigmaMax)
			sigma2 = sigmaMax;
		if (sigma3 > sigmaMax)
			sigma3 = sigmaMax;
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
		if (!isConfocal) { //we need preprocessing for epifluorescence data (for old version of weights)
			//result = preprocessing(result, 1.4f, 60, true);
		}
		
		
		/* create a stack from previous,current and next slice */
		ImageStack stack3 = create3Stack();
		ImageProcessor[] watershedResMitosisMasks = uNetSegmentation.binarySegmentationFromMaskWithMarkers(stack3, 0.5f, 0.4f);
				
		mitosisStart.setProcessor(watershedResMitosisMasks[1], currSlice);
		mitosisEnd.setProcessor(watershedResMitosisMasks[2], currSlice);
		
		result = fillComponentProperties(watershedResMitosisMasks[0], ip, false, null);
//		fillComponentProperties(watershedResMitosisMasks[1], ip, false, null);
//		fillComponentProperties(watershedResMitosisMasks[2], ip, false, null);
//			ImageProcessor binary = uNetSegmentation.binarySegmentation(result, (float) softmaxThreshold);
//			if (useWatershedPostProcessing) {
//				//make distance-based watershed transform here to separate touching cells
//				ChamferWeights weights = ChamferWeights.BORGEFORS;
//				final ImageProcessor dist = BinaryImages.distanceMap(binary, weights.getFloatWeights(), true);
//				dist.invert();
//				ImageProcessor watershedded = ExtendedMinimaWatershed.extendedMinimaWatershed(dist, binary, 1, 4, 32, false);
//				result = fillComponentProperties(watershedded, ip, false, null);
//			}
//			else result = fillComponentProperties(binary, ip, false, null);

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
	
	private ImageProcessor preprocessing(ImageProcessor ip, float gaussianSigma, int rollingBallRadius, boolean useGaussian) {
		if (useGaussian) {
			rankFilters.rank(result, 1, RankFilters.MEDIAN);
			//gaussian.GaussianBlur(ip, (float) gaussianSigma);
		}

		if (rollingBallRadius > 0)
			backgroundSub.rollingBallBackground(ip, rollingBallRadius, false, false, false, true, false);
		ImageFunctions.normalizeInPlace(ip, 0, 1);
		return ip;
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

	/*
	 * Yaginuma version of the algorithm, with finding maxima and marker-controlled
	 * watershed, then post-processing (0 ver) I changed gradient threshold to canny
	 * edge detection for flooding
	 * 
	 * (1 ver) Marker controlled watershed on gradient of the bandpass (or
	 * intensity), markers are minima of the bandpass (or intensity). (1 ver)
	 * Bandpass or intensity is selected by the checkbox
	 */
	@Deprecated
	private ImageProcessor maximaWatershedSegmentation(ImageProcessor ip, ImageProcessor original, double sigma,
			double minThreshold, double maxThreshold) {
		ip = preprocessing(ip, (float)gaussianSigma, rollingBallRadius, useGaussian);
		
		if (isTestMode) {
			ImageProcessor testResult = testFunction(result);

			if (previewing && !doesStacks()) {
				for (int i = 0; i < ip.getPixelCount(); i++) {
					ip.setf(i, testResult.getf(i));
				}

				ip.resetMinAndMax();
			}
			
			return ip;
		}
		
		// assume ip is already preprocessed, i.e. filtered, background subtracted
		ImageProcessor watershedImage = ip.duplicate();
		ImageProcessor intensityImg;
		ImageProcessor watershedMask;
		intensityImg = ip.duplicate();

		int blobDetection_x_radius = 3;
		int blobDetection_y_radius = 3;

		if (isBandpass) {
			bandpassFilter(watershedImage);
		}
		// create mask for watershed
		watershedMask = watershedImage.duplicate();
		// threshold = markerImg.duplicate(); //this should be otsu thresholded image
		// with convex hulling afterwards
		// ImageFunctions.thresholdMinMax(watershedMask, minThreshold, maxThreshold);

		// cellMask = ImageFunctions.getWhiteObjectsMask(ip, 1, 15);
		// imp_mask = new ImagePlus("mask", test_mask);
		// imp_mask.show();
		// ip = imp_mask.getProcessor();
		// ImagePlus wat = new ImagePlus("mask", watershedMask);

		if (topHatRadius > 0)
			watershedImage = ImageFunctions.operationMorph(watershedImage, Operation.TOPHAT, Strel.Shape.DISK,
					topHatRadius);

		ImageFunctions.normalizeInPlace(watershedImage, 0, 1);
		ImageFunctions.normalizeInPlace(ip, 0, 1);

		BlobDetector blobs = new BlobDetector(ip, null, sigmas);
		float[] sigmas_bright = { 4, 7, 10, 15, 20 };

		// detect bright blobs
		ImageProcessor ip_brightBlobs = intensityImg.duplicate();
		ImageProcessorCalculator.constMultiply(ip_brightBlobs, -1);
		BlobDetector brightBlobs = new BlobDetector(ip_brightBlobs, cellMask, sigmas_bright);

		// ImageProcessor findMaximaImage = blobs.findBlobsByMaxSigmasImage();
		ImageProcessor marksDarkBinary, marksBrightBinary;
		// marks = maxfinder.findMaxima(findMaximaImage, heightTolerance,
		// MaximumFinder.SINGLE_POINTS, true);
		marksDarkBinary = blobs.findBlobsByLocalMaximaAsImage((float) softmaxThreshold, true, true, maximumNumberOfBlobs,
				blobDetection_x_radius, blobDetection_y_radius, true);
		ImageProcessor marksSigma = blobs.findBlobsByLocalMaximaAsImage((float) softmaxThreshold, false, true,
				maximumNumberOfBlobs, blobDetection_x_radius, blobDetection_y_radius, true);

		// find bright markers to create more watershed seeds outside of cells
		marksBrightBinary = brightBlobs.findBlobsByLocalMaximaAsImage((float) heightToleranceBright, true, true,
				maximumNumberOfBlobs / 4, blobDetection_x_radius, blobDetection_y_radius, false);

		if (useMaximumFinder) {
			MaximumFinder maxFinder = new MaximumFinder();
			original.invert();
			ByteProcessor pluginMaxima = maxFinder.findMaxima(original, softmaxThreshold, MaximumFinder.SINGLE_POINTS, true);
			marksDarkBinary = pluginMaxima;
			original.invert();
		}

		ImageProcessor circles = original.duplicate();
		ImageFunctions.drawCirclesBySigmaMarkerks(circles, marksSigma, true, false);

		ImageProcessor closingImage = ImageFunctions.operationMorph(ip, Operation.CLOSING, Shape.DISK, 20);

		ImageProcessorCalculator.sub(watershedImage, closingImage);
		ImageFunctions.normalizeInPlace(watershedImage, 0, 1);
		// watershedImage = ImageProcessorCalculator.invertedImage(watershedImage);

		if (showImageForWatershedding) {
			ImagePlus imp = new ImagePlus("preprocessed", watershedImage);
			imp.show();
			return watershedImage;
		}

		ImageFunctions.mergeMarkers(marksDarkBinary, prevComponentsAnalysis, dilationRadius);
		if (blobMergeThreshold > 0)
			marksDarkBinary = ImageFunctions.mergeBinaryMarkersInTheSameRegion(watershedImage, marksDarkBinary, 35,
					blobMergeThreshold);

		// combine markers from bright and dark blobs, AFTER DARK BLOBS MERGING
		boolean addBrightMarkers = false;
		if (addBrightMarkers) {
			ImageFunctions.addMarkers(marksDarkBinary, marksBrightBinary);
		}

		if (showBlobs && previewing && !startedProcessing) {
			ImageFunctions.normalizeInPlace(marksDarkBinary, 0, 2);
			// marksDarkBinary = ImageFunctions.operationMorph(marksDarkBinary,
			// Operation.DILATION, Strel.Shape.DISK, 2);
			ip = original;
			// ImageFunctions.colorCirclesBySigmaMarkers(ip, marksSigma, true, false);
			// ImageFunctions.colorCirclesBySigmaMarkers(ip, marksCopy, true, true, 7);
			// ImageFunctions.colorCirclesBySigmaMarkers(ip, marksDarkBinary, true, true,
			// 7);
			ImageFunctions.normalizeInPlace(watershedImage, 0, 255);
			// ImageFunctions.colorCirclesBySigmaMarkers(watershedImage, marksDarkBinary,
			// true, true, 7);
			ImageFunctions.drawCirclesBySigmaMarkerks(ip, marksDarkBinary, true, false);
			// ImagePlus imp = new ImagePlus("markers", ip);
			// imp.show();
			return ip;
		}

		// intensityImg = watershedImage;
		ImageFunctions.LabelMarker(marksDarkBinary);
		if (sigma > 0)
			gaussian.GradientMagnitudeGaussian(watershedImage, (float) sigma); // watershed is better on gradient...
		// watershedImage = ImageFunctions.operationMorph(watershedImage,
		// Operation.OPENING, Strel.Shape.DISK, 3);
		// ImagePlus grad = new ImagePlus("grad", watershedImage);
		// grad.show();
		MarkerControlledWatershedTransform2D watershed = new MarkerControlledWatershedTransform2D(watershedImage,
				marksDarkBinary, null, 4);
		ip = watershed.applyWithPriorityQueue();

		// here draw and show colored basins
		// ImageFunctions.colorWatershedBasins(ip);

		return ip;
	}
	
	private ImageProcessor fillComponentProperties(ImageProcessor ip, ImageProcessor intensityImg, boolean addBrightMarkers, ImageProcessor marksBrightBinary) {
		if (filterComponents) {
			// get labeled component image and fill
			ImageComponentsAnalysis compAnalisys = new ImageComponentsAnalysis(ip, intensityImg, true); 

			if (addBrightMarkers) {
				compAnalisys.setComponentsBrightBlobStateByMarks(marksBrightBinary);
			}

			boolean discardWhiteBlobs = addBrightMarkers;
			ip = compAnalisys.getFilteredComponentsIp(minArea, maxArea, minCircularity, maxCircularity, 0, 1000,
					discardWhiteBlobs, removeBorderComponents);
			
			// here set component's state by marks image (indicate bright blobs)
			if (startedProcessing && addRois) // add roi only if we started processing
				compAnalisys.addRoisToManager(roiManager, imagePlus, currSlice);

			if (startedProcessing || doesStacks()) {
				prevComponentsAnalysis = compAnalisys;
				tracking.addComponentsAnalysis(compAnalisys);
			}
		}
		return ip;
	}

	private void bandpassFilter(ImageProcessor ip) {
		ImageProcessor ip1 = ip.duplicate();
		ImageProcessor ip2 = ip.duplicate();
		ip2.blurGaussian(sigma2);
		ip1.blurGaussian(sigma1);
		ImageProcessorCalculator.sub(ip1, ip2);
		FloatProcessor fp = null;
		fp = ip1.toFloat(0, fp);
		ip.setPixels(0, fp);
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

	/* function for algorithms testing */
	private ImageProcessor testFunction(ImageProcessor ip) {
		ImageProcessor result = ip.duplicate();

		ImagePlus preprocessed = new ImagePlus("pre", result);
		preprocessed.show();
		// do some testing and return
		// ImageFunctions.normalize(result, 0f, 1f);
		// result = ip;
		// ImageProcessorCalculator.sub(result,
		// imagePlus.getStack().getProcessor(selectedSlice - 1));
		ImageProcessor original = result.duplicate();
		ImageProcessor test = result.duplicate();

		cellMask = ImageFunctions.getWhiteObjectsMask(ip, 1, 15);
		if (isBandpass)
			ImageProcessorCalculator.constMultiply(result, -1);

		FloatHistogram hist = new FloatHistogram(original);
		float otsu = hist.otsuThreshold();

		if (topHatRadius > 0)
			original = ImageFunctions.operationMorph(original, Operation.TOPHAT, Strel.Shape.DISK, topHatRadius);

		if (maximumNumberOfBlobs > 0) {
			test = ImageFunctions.operationMorph(test, Operation.CLOSING, Shape.DISK, maximumNumberOfBlobs);
			ImageProcessorCalculator.sub(original, test);
		}

		result = original;

		// ImageProcessor whiteMask = ImageFunctions.maskThresholdMoreThan(test, otsu,
		// null);
		// original = whiteMask;
		// original = ImageFunctions.operationMorph(whiteMask, Operation.OPENING,
		// Shape.DISK, maximumNumberOfCells);
		// FloatHistogram histMasked = new FloatHistogram(test, whiteMask);
		// float otsu2 = histMasked.otsuThreshold();
		// original = ImageFunctions.maskThresholdLessThan(test, otsu2, whiteMask);

		/*
		 * Hessian hess = new Hessian(result); hess.calculateHessian((float) sigma1); if
		 * (filterComponents) result = hess.getLambda2(); else result =
		 * hess.getLambda1();
		 */
		// float[] sigmas = { 1, 20, 30, 50};
		// ImageFunctions.drawLine(result, 100, 100, 150,50);
		float[] sigmas_bright = { 7, 10, 15, 20 };
		BlobDetector blobs = new BlobDetector(result, null, sigmas_bright);

		ImageProcessor blobDots = blobs.findBlobsByLocalMaximaAsImage((float) softmaxThreshold, false, filterComponents,
				4, 1, 1, true);
		// result = blobs.findBlobsByMaxSigmasImage();
		// ImageFunctions.drawCirclesBySigmaMarkerks(original, blobDots, true);
		// ImageFunctions.drawGaussian(result, 200, 200, (float) sigma1);
		// original = ImageFunctions.operationMorph(original, Operation.BOTTOMHAT,
		// Strel.Shape.DISK, topHatRadius);
		// original = MinimaAndMaxima.extendedMaxima(original, minCircularity);
		// original = MinimaAndMaxima.regionalMinimaByReconstruction(original, 4);
		// original = MinimaAndMaxima.extendedMinima(original, otsu);

		return result;
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
			ImagePlus image_stack10 = IJ.openImage("G:\\Tokyo\\C002_10.tif");
			// ImagePlus image_shorter_bright_blobs =
			// IJ.openImage("G:\\Tokyo\\Short_c1_ex.tif");

//			ImagePlus image_ex_01 = IJ.openImage("G:\\Tokyo\\example_sequences\\c0010901_easy_ex.tif");
//			ImagePlus image_ex_06 = IJ.openImage("G:\\Tokyo\\example_sequences\\c0010906_medium_double_nuclei_ex.tif");
			ImagePlus image_ex_07 = IJ.openImage("G:\\Tokyo\\example_sequences\\c0010907_easy_ex.tif");
//			ImagePlus image_ex_13 = IJ.openImage("G:\\Tokyo\\example_sequences\\c0010913_hard_ex.tif");

//			ImagePlus confocal_1 = IJ.openImage("G:\\Tokyo\\Confocal\\181221-q8156901-tiff\\c2\\181221-q8156901hfC2c2.tif");

//			image = image_ex_01;
			 image = image_ex_07;
			// image = image_stack20;
//			image = image_stack10;
			// image = image_stack3;
			// image = image_c10;
			// image = image_ez_division;
			// image = image_test_tracking;
			// image = image_shorter_bright_blobs;
//			image = image_ex_06;
//			image = image_ex_13;
			//image = confocal_1;
			ImageConverter converter = new ImageConverter(image);
			converter.convertToGray32();
			image.show();

			// run the plugin
			IJ.runPlugIn(clazz.getName(), "");
		}
	}

	/**
	 * Creates the name for result image, by adding a suffix to the base name of
	 * original image. "Taken from MorphoLibJ"
	 */
	private String createResultImageName(ImagePlus baseImage) {
		return baseImage.getShortTitle() + "-" + "result";
	}
}
