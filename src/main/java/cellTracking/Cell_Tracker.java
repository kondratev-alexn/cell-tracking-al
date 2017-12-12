package cellTracking;

import java.awt.AWTEvent;
import java.awt.Scrollbar;
import java.util.Arrays;
import java.util.Vector;

import graph.Graph;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Macro;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.ImageLayout;
import ij.gui.ShapeRoi;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.StackConverter;
import ij.process.StackProcessor;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.Duplicator;
import ij.plugin.ImageCalculator;
import ij.plugin.ContrastEnhancer;
import ij.plugin.Converter;
import ij.plugin.filter.BackgroundSubtracter;
import ij.plugin.filter.RankFilters;
import ij.plugin.frame.RoiManager;
//import ij.plugin.
import ij.process.AutoThresholder;
import inra.ijpb.binary.BinaryImages;
import inra.ijpb.binary.ChamferWeights;
import inra.ijpb.morphology.AttributeFiltering;
import inra.ijpb.morphology.GeodesicReconstruction;
import inra.ijpb.morphology.Morphology.Operation;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.Strel.Shape;
import inra.ijpb.morphology.attrfilt.AreaOpeningQueue;
import inra.ijpb.plugins.*;
import inra.ijpb.watershed.ExtendedMinimaWatershed;
import inra.ijpb.watershed.MarkerControlledWatershedTransform2D;
import inra.ijpb.watershed.Watershed;
import ij.plugin.filter.MaximumFinder;

import fiji.threshold.Auto_Threshold;
import evaluation.TrackingEvaluation;

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

	/* contains mask for previously segmented cells */
	private ImageProcessor cellMask;

	/* image for displaing result on stacks */
	private ImagePlus stackImage;

	/* roi manager for current slice */
	private RoiManager roiManager;

	private int currSlice = 1; // slice number for stack processing
	private int selectedSlice; // currently selected slice

	// sigmas for bandpass algorithm, also in UI
	public double sigma1 = 1.40;
	public double sigma2 = 6.00;
	public double sigma3 = 0.80;

	/* numerical parameters for UI */
	private double heightTolerance = 0.01; // now its threshold for lambda2+lambda1 in blob detection
	private double heightToleranceBright = 0.20;
	private int rollingBallRadius = 20; // for background subtraction
	private int closingRadius = 2;
	private double medianRadius = 2;
	private double minThreshold = 20;
	private double maxThreshold = 50;
	private int minArea = 100;
	private int maxArea = 1200;
	private float minCircularity = 0.55f;
	private float maxCircularity = 1.0f;
	private int dilationRadius = 2;

	private float[] sigmas = { 7, 9, 12, 16, 32 };

	/* booleans for CheckBoxes */
	private boolean isTestMode = false;
	private boolean useMedian = false;
	private boolean isBandpass = true;
	private boolean useOtsuThreshold = false;

	private boolean showImageForWatershedding = false;
	private boolean filterComponents = true;
	private boolean previewing = false;

	private boolean roiBrowserActive = false; // becomes true after the processing
	private boolean startedProcessing = false; // comes true after user has selected whether to process stacks or not

	private ImageComponentsAnalysis prevComponentsAnalysis = null; // for getting masks of segmented cells in next
																	// slices
	private NearestNeighbourTracking tracking = null;

	private final float sigmaMax = 50; // max value of sigmas

	// plugins
	private Gaussian gaussian;
	private BackgroundSubtracter backgroundSub;
	private RankFilters rankFilters;

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

		if (arg.equals("final")) {
			// replace the preview image by the original image
			// resetPreview();
			imagePlus.updateAndDraw();

			stackImage.copyScale(imagePlus);
			stackImage.resetDisplayRange();
			// stackImage.show();

			// here we should show dialog with a slider to browse acquired rois
			// EDIT: no, just show roi manager, since it can handle roi in stack images
			/*
			 * roiBrowserActive = false; //currRoiManager = rois[selectedSlice];
			 * GenericDialog gd = new GenericDialog("Browse ROIs"); gd.addSlider("Slice", 1,
			 * nSlices, selectedSlice); gd.addDialogListener(this);
			 * 
			 * gd.showDialog(); // input by the user (or macro) happens here
			 * 
			 * if (gd.wasCanceled()) { resetPreview(); return DONE; }
			 */
			roiManager.selectAndMakeVisible(imagePlus, -1);
			tracking.trackComponents(20, 13, 3);
			
			System.out.println(tracking.getGraph());
			System.out.println(tracking.getGraph().checkNoEqualNodes());
			ImageProcessor ip = imp.getProcessor();
			// tracking.drawTracksIp(ip);
			ImagePlus trResult = tracking.drawTracksImagePlus(imp);
			trResult.setTitle("Tracking results");
			imp.show();
			trResult.show();

			Graph cellGraph = tracking.getGraph();
			// System.out.println(cellGraph);

			// Create a new ImagePlus with the filter result
			/*
			 * String newName = createResultImageName(imagePlus); ImagePlus resPlus = new
			 * ImagePlus(newName, result); resPlus.copyScale(imagePlus);
			 * resPlus.resetDisplayRange(); resPlus.show();
			 */
			return DONE;
		}

		nSlices = imp.getStackSize();
		tracking = new NearestNeighbourTracking(nSlices);
		cellMask = null;

		// convert to float if plugin just started
		if (nSlices == 1) {
			Converter conv = new Converter();
			conv.run("32-bit");
		} else {
			StackConverter stackConv = new StackConverter(imp);
			stackConv.convertToGray32();
		}
		instancePlugins();
		// here maybe add flags like flags |= DOES_NOTHING
		return flags;
	}

	/*
	 * creates instances of used plugins
	 */
	private void instancePlugins() {
		gaussian = new Gaussian();
		backgroundSub = new BackgroundSubtracter();
		rankFilters = new RankFilters();
	}

	@Override
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
		// Normal setup
		this.imagePlus = imp;
		this.baseImage = imp.getProcessor().duplicate();
		stackImage = imp.duplicate();
		nChannels = imp.getProcessor().getNChannels();
		currSlice = 1; // for when algorith starts processing stack
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
		currSlice = doesStacks() ? 1 : selectedSlice;
		roiManager = RoiManager.getInstance();
		if (roiManager == null)
			roiManager = new RoiManager();
		roiManager.reset();

		return flags;
	}

	private void fillGenericDialog(GenericDialog gd, PlugInFilterRunner pfr) {
		gd.addNumericField("Median filter radius", medianRadius, 2);
		gd.addNumericField("Rolling ball radius", rollingBallRadius, 0);
		gd.addNumericField("Closing radius", closingRadius, 0);
		gd.addNumericField("Sigma1 (bandpass):", sigma1, 2);
		gd.addNumericField("Sigma2 (bandpass):", sigma2, 2);
		gd.addNumericField("Sigma3 (gradient)", sigma3, 2);
		// gd.addNumericField("Min threshold", minThreshold, 3);
		// gd.addNumericField("Max threshold", maxThreshold, 3);
		gd.addNumericField("Laplacian tolerance (dark blobs)", heightTolerance, 2);
		gd.addNumericField("Laplacian tolerance (bright blobs)", heightToleranceBright, 2);
		gd.addNumericField("Min area", minArea, 0);
		gd.addNumericField("Max area", maxArea, 0);
		gd.addNumericField("Min circularity", minCircularity, 3);
		gd.addNumericField("Max circularity", maxCircularity, 3);
		gd.addNumericField("Dilation Radius (postprocessing)", dilationRadius, 0);
		gd.addCheckbox("test mode", isTestMode);
		gd.addCheckbox("Median Filter", useMedian);
		gd.addCheckbox("Bandpass", isBandpass);
		// gd.addCheckbox("Use Auto Otsu threshold", useOtsuThreshold);
		gd.addCheckbox("Show Image before Watershedding", showImageForWatershedding);
		gd.addCheckbox("Filter components", filterComponents);
		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		gd.addSlider("Slice", 1, nSlices, selectedSlice);
	}

	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		if (roiBrowserActive) { // for listening to roi browser dialog
			Vector sliders = gd.getSliders();
			selectedSlice = ((Scrollbar) sliders.get(0)).getValue();
			// resetPreview();
			imagePlus.setSlice(selectedSlice);

			return true;
		}
		boolean wasPreview = this.previewing;
		parseDialogParameters(gd);
		if (sigma1 < 0 || sigma2 < 0 || sigma3 < 0 || gd.invalidNumber())
			return false;

		// if preview checkbox was unchecked, replace the preview image by the original
		// image
		if (wasPreview && !this.previewing) {
			resetPreview();
		}
		return true;
	}

	private void parseDialogParameters(GenericDialog gd) {
		// extract chosen parameters
		medianRadius = gd.getNextNumber();
		rollingBallRadius = (int) gd.getNextNumber();
		closingRadius = (int) gd.getNextNumber();
		sigma1 = gd.getNextNumber();
		sigma2 = gd.getNextNumber();
		sigma3 = gd.getNextNumber();
		// minThreshold = gd.getNextNumber();
		// maxThreshold = gd.getNextNumber();
		heightTolerance = gd.getNextNumber();
		heightToleranceBright = gd.getNextNumber();
		minArea = (int) gd.getNextNumber();
		maxArea = (int) gd.getNextNumber();
		minCircularity = (float) gd.getNextNumber();
		maxCircularity = (float) gd.getNextNumber();
		dilationRadius = (int) gd.getNextNumber();
		isTestMode = gd.getNextBoolean();
		useMedian = gd.getNextBoolean();
		isBandpass = gd.getNextBoolean();
		// useOtsuThreshold = gd.getNextBoolean();
		showImageForWatershedding = gd.getNextBoolean();
		filterComponents = gd.getNextBoolean();
		previewing = gd.getPreviewCheckbox().getState();

		// change image if current slice changed
		Vector sliders = gd.getSliders();
		selectedSlice = ((Scrollbar) sliders.get(0)).getValue();
		resetPreview();
		imagePlus.setSlice(selectedSlice);
		stackImage.setSliceWithoutUpdate(selectedSlice);
		baseImage = imagePlus.getProcessor().duplicate();

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
			result = ip.duplicate();
		} else
			result = baseImage.duplicate();

		// preprocess image first
		// ImageFunctions.normalize(result, 0f, 1f);
		if (useMedian) {
			// rankFilters.rank(result, medianRadius, RankFilters.MEDIAN);
			gaussian.GaussianBlur(result, (float) medianRadius);
		}
		cellMask = ImageFunctions.getWhiteObjectsMask(ip, 1, 15);
		
		backgroundSub.rollingBallBackground(result, rollingBallRadius, false, false, false, true, false);
		ImageFunctions.normalize(result, 0, 1);

		if (isTestMode) {
			// do some testing and return
			// ImageFunctions.normalize(result, 0f, 1f);
			// result = ip;
			// ImageProcessorCalculator.sub(result,
			// imagePlus.getStack().getProcessor(selectedSlice - 1));
			cellMask = ImageFunctions.getWhiteObjectsMask(ip, 1, 15);
			if (isBandpass)
				ImageProcessorCalculator.constMultiply(result, -1);
			/*
			 * Hessian hess = new Hessian(result); hess.calculateHessian((float) sigma1); if
			 * (filterComponents) result = hess.getLambda2(); else result =
			 * hess.getLambda1();
			 */
			// float[] sigmas = { 1, 20, 30, 50};
			// ImageFunctions.drawLine(result, 100, 100, 150,50);
			float[] sigmas_bright = { 7, 10, 15, 20 };
			BlobDetector blobs = new BlobDetector(result, cellMask, sigmas_bright);

			ImageProcessor blobDots = blobs.findBlobsBy3x3LocalMaxima((float) heightTolerance, false, filterComponents,
					20);
			// result = blobs.findBlobsByMaxSigmasImage();
			ImageFunctions.drawCirclesBySigmaMarkerks(result, blobDots, true);
			// ImageFunctions.drawGaussian(result, 200, 200, (float) sigma1);

			if (previewing && !doesStacks()) {
				for (int i = 0; i < ip.getPixelCount(); i++) {
					ip.setf(i, result.getf(i));
				}

				ip.resetMinAndMax();
			}
			return;
		}

		// segmentation(result);
		result = maximaWatershedSegmentation(result, sigma3, minThreshold, maxThreshold);

		result = result.convertToFloatProcessor();
		result.resetMinAndMax();
		// }

		if (nSlices == 1)
			stackImage.setProcessor(result);
		else
			stackImage.getImageStack().setProcessor(result, currSlice);

		if (startedProcessing) { // process stacks
			currSlice++;
			// System.out.println("in curr_slice++");
		}

		if (previewing && !startedProcessing) {
			// Fill up the values of original image with values of the result
			for (int i = 0; i < ip.getPixelCount(); i++) {
				ip.setf(i, result.getf(i));
			}
			// roiManager.selectAndMakeVisible(imagePlus, 0);
			// roiManager.setEditMode(imagePlus, true);
			ip.resetMinAndMax();
		}
	}

	/*
	 * Yaginuma version of the algorithm, with finding maxima and marker-controlled
	 * watershed, then post-processing (0 ver) I changed gradient threshold to canny
	 * edge detection for flooding
	 * 
	 * (1 ver) Marker controlled watershed on gradient of the badnpass (or
	 * intensity), markers are minima of the bandpass (or intensity). (1 ver)
	 * Bandpass or intensity is selected by the checkbox
	 */
	private ImageProcessor maximaWatershedSegmentation(ImageProcessor ip, double sigma, double minThreshold,
			double maxThreshold) {
		// assume ip is already preprocessed, i.e. filtered, background subtracted
		ImageProcessor markerImg = ip.duplicate();
		ImageProcessor intensityImg;
		ImageProcessor watershedMask;
		intensityImg = markerImg.duplicate();

		if (isBandpass) {
			bandpassFilter(markerImg);
		}
		// create mask for watershed
		watershedMask = markerImg.duplicate();
		// threshold = markerImg.duplicate(); //this should be otsu thresholded image
		// with convex hulling aftewards
		ImageFunctions.threshold(watershedMask, minThreshold, maxThreshold);
		
		//cellMask = ImageFunctions.getWhiteObjectsMask(ip, 1, 15);
		//imp_mask = new ImagePlus("mask", test_mask);
		//imp_mask.show();
		// ip = imp_mask.getProcessor();
		//ImagePlus wat = new ImagePlus("mask", watershedMask);

		markerImg = ImageFunctions.operationMorph(markerImg, Operation.CLOSING, Strel.Shape.DISK, closingRadius);

		if (showImageForWatershedding) {
			ImageFunctions.normalize(markerImg, 0, 255);
			return markerImg;
		}

		MaximumFinder maxfinder = new MaximumFinder();
		ImageFunctions.normalize(markerImg, 0, 255);
		ImageFunctions.normalize(ip, 0f, 1f);

		BlobDetector blobs = new BlobDetector(ip, cellMask, sigmas);
		float[] sigmas_bright = { 7, 10, 15, 20 };

		// detect bright blobs
		ImageProcessor ip_brightBlobs = ip.duplicate();
		ImageProcessorCalculator.constMultiply(ip_brightBlobs, -1);
		BlobDetector brightBlobs = new BlobDetector(ip_brightBlobs, cellMask, sigmas_bright);

		// ImageProcessor findMaximaImage = blobs.findBlobsByMaxSigmasImage();
		ImageProcessor marks, marksBright;
		// marks = maxfinder.findMaxima(findMaximaImage, heightTolerance,
		// MaximumFinder.SINGLE_POINTS, true);
		marks = blobs.findBlobsBy3x3LocalMaxima((float) heightTolerance, true, true, 30);
		marksBright = brightBlobs.findBlobsBy3x3LocalMaxima((float) heightToleranceBright, true, true, 4);

		//ImagePlus imp = new ImagePlus("marks", marks);
		//imp.show();
		
		ImageFunctions.mergeMarkers(marks, prevComponentsAnalysis, dilationRadius);

		gaussian.GradientMagnitudeGaussian(markerImg, (float) sigma);

		// tried adding lambda2 to gradient - bad
		Hessian hess = new Hessian(ip);
		hess.calculateHessian(1f);
		ImageProcessor l2 = hess.getLambda2();
		// ImageFunctions.divideByNegativeValues(markerImg, l2);
		// ImageProcessorCalculator.linearCombination(0.8f, markerImg, 0.0f, l2);

		// combine markerks from bright and dark blobs
		ImageFunctions.addMarkers(marks, marksBright);
		ImageFunctions.LabelMarker(marks);

		// ImageFunctions.normalize(markerImg, 0, 255);
		MarkerControlledWatershedTransform2D watershed = new MarkerControlledWatershedTransform2D(markerImg, marks,
				cellMask, 4);
		ip = watershed.applyWithPriorityQueue();
//		ImagePlus water = new ImagePlus("water", ip);
//		water.show();
		if (filterComponents) {
			ImageComponentsAnalysis compAnalisys;
			ImageFunctions.normalize(intensityImg, 0, 255);
			ImageFunctions.subtractBackgroundMinMedian(intensityImg, 8);
			compAnalisys = new ImageComponentsAnalysis(ip, intensityImg); // get labelled component image and fill
																			// properties
			ip = compAnalisys.getFilteredComponentsIp(minArea, maxArea, minCircularity, maxCircularity, 0, 1000);
			if (startedProcessing) // add roi only if we started processing
				compAnalisys.addRoisToManager(roiManager, imagePlus, currSlice);

			if (startedProcessing || doesStacks())
				prevComponentsAnalysis = compAnalisys;
			tracking.addComponentsAnalysis(compAnalisys);
		}
		return ip;
	}

	/**
	 * Process an image.
	 * <p>
	 * Please provide this method even if {@link ij.plugin.filter.PlugInFilter} does
	 * require it; the method
	 * {@link ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)} can only
	 * handle 2-dimensional data.
	 * </p>
	 * <p>
	 * If your plugin does not change the pixels in-place, make this method return
	 * the results and change the {@link #setup(java.lang.String, ij.ImagePlus)}
	 * method to return also the <i>DOES_NOTHING</i> flag.
	 * </p>
	 *
	 * @param image
	 *            the image (possible multi-dimensional)
	 */
	public void process(ImagePlus image) {
		// slice numbers start with 1 for historical reasons
		for (int i = 1; i <= image.getStackSize(); i++)
			process(image.getStack().getProcessor(i));
	}

	public void process(ImageProcessor ip) {
		bandpassFilter(ip);
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

	private void showRoisForSlice(int sliceNumber) {
		int[] indexes = { 0, 1 };
		roiManager.setSelectedIndexes(indexes);
	}

	private void resetPreview() {
		ImageProcessor image = this.imagePlus.getProcessor();
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
		IJ.showMessage("ProcessPixels", "a template for processing each pixel of an image");
	}

	public static void main(String[] args) {
		boolean testImageJ = false;
		if (!testImageJ) {
			System.out.println("HELLO THERE");
			TrackingEvaluation tra = new TrackingEvaluation();
			tra.writeTracksToFile_ctc("tracks.txt", null);
		} else {
			// set the plugins.dir property to make the plugin appear in the Plugins menu
			Class<?> clazz = Cell_Tracker.class;
			String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
			String pluginsDir = url.substring("file:".length(),
					url.length() - clazz.getName().length() - ".class".length());
			System.setProperty("plugins.dir", pluginsDir);

			// start ImageJ
			new ImageJ();

			// open one image of sequence. T0104 is for segmentation, T0050 is with mitosis
			ImagePlus image = IJ.openImage("C:\\Tokyo\\170704DataSeparated\\C0002\\c0010901\\T0001.tif");
			ImagePlus image_stack20 = IJ.openImage("C:\\Tokyo\\C002_Movement.tif");
			ImagePlus image105 = IJ.openImage("C:\\Tokyo\\170704DataSeparated\\C0002\\c0010901\\T0105.tif");
			ImagePlus image_c10 = IJ.openImage("C:\\Tokyo\\170704DataSeparated\\C0002\\c0010910\\T0001.tif");
			ImagePlus image_stack3 = IJ.openImage("C:\\Tokyo\\\\movement_3images.tif");
			ImagePlus image_bright_blobs = IJ.openImage("C:\\Tokyo\\example_sequences\\c0010901_easy_ex.tif");
			ImagePlus image_ez_division = IJ.openImage("C:\\Tokyo\\division.tif");
			ImagePlus image_stack10 = IJ.openImage("C:\\Tokyo\\C002_10.tif");

			image = image_bright_blobs;
			image = image_stack20;
			//image = image_stack10;
			// image = image_stack3;
			// image = image_c10;
			//image = image_ez_division;
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

	/*
	 * first try on segmentation algorithm - threshold of bandpass filter with
	 * postprocessing of segmented blobs
	 */
	private void segmentation(ImageProcessor ip) {
		if (isBandpass) {
			bandpassFilter(result);
			// ImageFunctions.normalize(result, 0f, 255f);
		} else { // gradient
			gaussian.GradientMagnitudeGaussian(result, (float) sigma1);
			// ImageFunctions.clippingIntensity(result, 0, 500);
			// gaussian.GradientMagnitudeGaussian(result_t, (float)sigma2);
			// calc.sub(result, result_t);
		}
		ImageProcessor intensityImg = result.duplicate();
		if (showImageForWatershedding) {
			// need to use the result of the algo from the first frame somehow, and/or
			// result of intensity thresholding
			ImageFunctions.threshold(result, minThreshold, maxThreshold);

			if (filterComponents) {
				// first, do some watershedding
				float[] weights = ChamferWeights.BORGEFORS.getFloatWeights();
				final ImageProcessor dist = BinaryImages.distanceMap(ImageFunctions.getBinary(result), weights, true);
				dist.invert();
				// ImagePlus test = new ImagePlus("dist", dist);
				// test.show();

				result = ExtendedMinimaWatershed.extendedMinimaWatershed(dist, result, 1, 4, false);

				ImageComponentsAnalysis compAnalisys, brightBlobsAnalisys;
				compAnalisys = new ImageComponentsAnalysis(result, intensityImg);
				brightBlobsAnalisys = new ImageComponentsAnalysis(result, intensityImg);

				result = compAnalisys.getFilteredComponentsIp(minArea, maxArea, minCircularity, maxCircularity, 0, 255);

				/*
				 * here, after the main part of the algorithm, do the following to obtain better
				 * segmentation: - Do closing with disc_5 radius on T-d bandpass image. - Get
				 * T-d intensity image (median 8, Tmin = -100, Tmax = 15) - Watershed T-d
				 * intensity image - add black background from intensity T-d to bandpassed
				 * (simple AND) - ??? - PROFIT. Now sells are separated. I hope.
				 */

				/*
				 * Operation op = Operation.CLOSING; Strel.Shape shape = Strel.Shape.DISK; Strel
				 * strel = shape.fromRadius(5); result = result.convertToShortProcessor();
				 * op.apply(result, strel); //closing with disc_5 R result =
				 * result.convertToFloatProcessor();
				 */
				rankFilters.rank(thresholdedIntensity, 4, RankFilters.MEDIAN);
				backgroundSub.rollingBallBackground(thresholdedIntensity, 20, false, false, false, false, false);
				ImageFunctions.threshold(thresholdedIntensity, -100, 15);

				// ImageFunctions.AND(result, thresholdedIntensity);
			}
		}
	}
}
