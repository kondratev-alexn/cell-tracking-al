package cellTracking;

import java.awt.AWTEvent;
import java.awt.Scrollbar;
import java.util.Vector;

import graph.CellTrackingGraph;
import graph.Graph;
import histogram.FloatHistogram;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.FloatProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.StackConverter;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.Converter;
import ij.plugin.filter.BackgroundSubtracter;
import ij.plugin.filter.RankFilters;
import ij.plugin.frame.RoiManager;
import inra.ijpb.morphology.Morphology.Operation;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.Strel.Shape;
import inra.ijpb.watershed.MarkerControlledWatershedTransform2D;
import evaluation.EvaluationFromRoi;
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

	/* roi manager for current slice */
	private RoiManager roiManager;

	private int currSlice = 1; // slice number for stack processing
	private int selectedSlice; // currently selected slice by slider

	// sigmas for bandpass algorithm, also in UI
	public double sigma1 = 1.40;
	public double sigma2 = 10.00;
	public double sigma3 = 0.80;

	/* numerical parameters for UI */
	private double heightTolerance = 0.01; // now its threshold for lambda2+lambda1 in blob detection
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

	private float blobMergeThreshold = 0.32f; // threshold, below which blobs will be merged
	private float childPenaltyThreshold = 0.275f;
	private float mitosisStartIntensityCoefficient = 1.00f;

	private float[] sigmas = { 6, 9, 12, 16, 32 };

	/* booleans for CheckBoxes */
	private boolean isTestMode = false;
	private boolean useGaussian = true;
	private boolean isBandpass = false;

	private boolean showImageForWatershedding = false;
	private boolean filterComponents = true;
	private boolean previewing = false;

	private boolean startedProcessing = false; // comes true after user has selected whether to process stacks or not
	private boolean showBlobs = false;

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

			roiManager.selectAndMakeVisible(imagePlus, -1);

			// there goes tracking
			int maxRadiusDark = 25, maxRadiusBright = 18, slices = 3;
			double oneSliceScoreThreshold = 0.33;
			double scoreThreshold = 0.6;
			double timeDecayCoefficient = 0.3;
			// tracking.trackComponents(maxRadiusDark, maxRadiusBright, slices,
			// scoreThreshold);
			// tracking.trackComponentsOneAndMultiSlice(maxRadiusDark, slices,
			// scoreThreshold, oneSliceScoreThreshold, timeDecayCoefficient);
			tracking.trackComponentsOneSlice(maxRadiusDark, oneSliceScoreThreshold);
			tracking.trackComponentsMultiSlice(maxRadiusDark, slices, scoreThreshold, timeDecayCoefficient);
			tracking.fillTracks();
			// check for mitosis start by two ideas (intensity change / bright blob nearby)
			tracking.analyzeTracksForMitosisByAverageIntensity(mitosisStartIntensityCoefficient);
			tracking.analyzeTracksForMitosisByWhiteBlob(0.5f);
			tracking.startMitosisTracking(30, childPenaltyThreshold);
			// tracking.trackComponentsMultiSlice(maxRadiusDark, 4, scoreThreshold,
			// timeDecayCoefficient);

			// System.out.println(tracking.getGraph());
			// System.out.println(tracking.getGraph().checkNoEqualNodes());
			ImageProcessor ip = imp.getProcessor();
			// tracking.drawTracksIp(ip);
			// ImagePlus trResult = tracking.drawTracksImagePlus(imp);

			imp.show();

			Graph cellGraph = tracking.getGraph();

			CellTrackingGraph resultGraph = new CellTrackingGraph(tracking, roiManager, imp);
			resultGraph.showTrackedComponentImages(); //TRA components show
			ImagePlus coloredTracksImage = resultGraph.drawComponentColoredByFullTracks(imp);
			coloredTracksImage.show();

			resultGraph.writeTracksToFile_ctc_afterAnalysis(imp.getShortTitle() + "_tracking_results.txt");
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
		tracking = new NearestNeighbourTracking();
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
		// imagePlus.setStack(stackImage.getStack().duplicate());
		// imagePlus.setSlice(1);

		currSlice = doesStacks() ? 1 : selectedSlice;
		roiManager = RoiManager.getInstance();
		if (roiManager == null)
			roiManager = new RoiManager();
		roiManager.reset();

		return flags;
	}

	private void fillGenericDialog(GenericDialog gd, PlugInFilterRunner pfr) {
		gd.addNumericField("Number of blobs", maximumNumberOfBlobs, 0);
		gd.addNumericField("Gaussian Filter Sigma", gaussianSigma, 2);
		gd.addNumericField("Rolling ball radius", rollingBallRadius, 0);
		// gd.addNumericField("Closing radius", topHatRadius, 0);
		// gd.addNumericField("Sigma1 (bandpass):", sigma1, 2);
		// gd.addNumericField("Sigma2 (bandpass):", sigma2, 2);
		gd.addNumericField("Gradient Sigma", sigma3, 2);
		// gd.addNumericField("Min threshold", minThreshold, 3);
		// gd.addNumericField("Max threshold", maxThreshold, 3);
		// gd.addNumericField("Laplacian tolerance (dark blobs)", heightTolerance, 2);
		// gd.addNumericField("Laplacian tolerance (bright blobs)",
		// heightToleranceBright, 2);
		gd.addNumericField("Min area", minArea, 0);
		gd.addNumericField("Max area", maxArea, 0);
		gd.addNumericField("Min circularity", minCircularity, 3);
		gd.addNumericField("Max circularity", maxCircularity, 3);
		// gd.addNumericField("Dilation Radius (postprocessing)", dilationRadius, 0);
		gd.addNumericField("Blob merge threshold", blobMergeThreshold, 3);
		gd.addNumericField("Bright blob childs threshold", childPenaltyThreshold, 3);
		gd.addNumericField("Intensity change coefficient (mitosis)", mitosisStartIntensityCoefficient, 3);
		// gd.addCheckbox("test mode", isTestMode);
		// gd.addCheckbox("Use Gaussian Filter", useGaussian);
		// gd.addCheckbox("Bandpass", isBandpass);
		// gd.addCheckbox("Use Auto Otsu threshold", useOtsuThreshold);
		// gd.addCheckbox("Show Image before Watershedding", showImageForWatershedding);
		gd.addCheckbox("Show blobs", showBlobs);
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

		if (sigma1 < 0 || sigma2 < 0 || sigma3 < 0 || gd.invalidNumber())
			return false;

		// if preview checkbox was unchecked, replace the preview image by the original
		// image
		if (wasPreview && !this.previewing) {
			resetPreview();
		}
		return true;
	}

	// extract chosen parameters
	private void parseDialogParameters(GenericDialog gd) {
		maximumNumberOfBlobs = (int) gd.getNextNumber();
		gaussianSigma = gd.getNextNumber();
		rollingBallRadius = (int) gd.getNextNumber();
		// topHatRadius = (int) gd.getNextNumber();
		// sigma1 = gd.getNextNumber();
		// sigma2 = gd.getNextNumber();
		sigma3 = gd.getNextNumber();
		// minThreshold = gd.getNextNumber();
		// maxThreshold = gd.getNextNumber();
		// heightTolerance = gd.getNextNumber();
		// heightToleranceBright = gd.getNextNumber();
		minArea = (int) gd.getNextNumber();
		maxArea = (int) gd.getNextNumber();
		minCircularity = (float) gd.getNextNumber();
		maxCircularity = (float) gd.getNextNumber();
		// dilationRadius = (int) gd.getNextNumber();
		blobMergeThreshold = (float) gd.getNextNumber();
		childPenaltyThreshold = (float) gd.getNextNumber();
		mitosisStartIntensityCoefficient = (float) gd.getNextNumber();

		// isTestMode = gd.getNextBoolean();
		// useGaussian = gd.getNextBoolean();
		// isBandpass = gd.getNextBoolean();
		// useOtsuThreshold = gd.getNextBoolean();
		// showImageForWatershedding = gd.getNextBoolean();
		showBlobs = gd.getNextBoolean();
		filterComponents = gd.getNextBoolean();

		previewing = gd.getPreviewCheckbox().getState();

		// change image if current slice changed
		Vector sliders = gd.getSliders();
		selectedSlice = ((Scrollbar) sliders.get(0)).getValue();

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

		if (useGaussian) {
			// rankFilters.rank(result, medianRadius, RankFilters.MEDIAN);
			gaussian.GaussianBlur(result, (float) gaussianSigma);
		}

		cellMask = ImageFunctions.getWhiteObjectsMask(ip, 1, 15);

		if (rollingBallRadius > 0)
			backgroundSub.rollingBallBackground(result, rollingBallRadius, false, false, false, true, false);
		ImageFunctions.normalize(result, 0, 1);

		if (isTestMode) {
			ImageProcessor testResult = testFunction(result);

			if (previewing && !doesStacks()) {
				for (int i = 0; i < ip.getPixelCount(); i++) {
					ip.setf(i, testResult.getf(i));
				}

				ip.resetMinAndMax();
			}
			return;
		}

		result = maximaWatershedSegmentation(result, ip, sigma3, minThreshold, maxThreshold);

		result = result.convertToFloatProcessor();
		result.resetMinAndMax();

		if (startedProcessing) { // process stacks
			currSlice++;
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
	 * (1 ver) Marker controlled watershed on gradient of the bandpass (or
	 * intensity), markers are minima of the bandpass (or intensity). (1 ver)
	 * Bandpass or intensity is selected by the checkbox
	 */
	private ImageProcessor maximaWatershedSegmentation(ImageProcessor ip, ImageProcessor original, double sigma,
			double minThreshold, double maxThreshold) {
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
		// with convex hulling aftewards
		// ImageFunctions.thresholdMinMax(watershedMask, minThreshold, maxThreshold);

		// cellMask = ImageFunctions.getWhiteObjectsMask(ip, 1, 15);
		// imp_mask = new ImagePlus("mask", test_mask);
		// imp_mask.show();
		// ip = imp_mask.getProcessor();
		// ImagePlus wat = new ImagePlus("mask", watershedMask);

		if (topHatRadius > 0)
			watershedImage = ImageFunctions.operationMorph(watershedImage, Operation.TOPHAT, Strel.Shape.DISK,
					topHatRadius);

		ImageFunctions.normalize(watershedImage, 0, 1);
		ImageFunctions.normalize(ip, 0, 1);

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
		marksDarkBinary = blobs.findBlobsByLocalMaximaAsImage((float) heightTolerance, true, true, maximumNumberOfBlobs,
				blobDetection_x_radius, blobDetection_y_radius, true);
		ImageProcessor marksSigma = blobs.findBlobsByLocalMaximaAsImage((float) heightTolerance, false, true,
				maximumNumberOfBlobs, blobDetection_x_radius, blobDetection_y_radius, true);

		// find bright markers to create more watershed seeds outside of cells
		marksBrightBinary = brightBlobs.findBlobsByLocalMaximaAsImage((float) heightToleranceBright, true, true,
				maximumNumberOfBlobs / 4, blobDetection_x_radius, blobDetection_y_radius, false);

		ImageProcessor circles = original.duplicate();
		ImageFunctions.drawCirclesBySigmaMarkerks(circles, marksSigma, true, false);

		FloatHistogram hist = new FloatHistogram(ip);
		float otsu = hist.otsuThreshold();

		ImageProcessor closingImage = ImageFunctions.operationMorph(ip, Operation.CLOSING, Shape.DISK, 20);

		ImageProcessorCalculator.sub(watershedImage, closingImage);
		ImageFunctions.normalize(watershedImage, 0, 1);
		// watershedImage = ImageProcessorCalculator.invertedImage(watershedImage);

		
		if (showImageForWatershedding) {
			ImagePlus imp = new ImagePlus("preprocessed", watershedImage);
			imp.show();
			return watershedImage;
		}
		
		ImageProcessor marksCopy = marksDarkBinary.duplicate();
		ImageFunctions.addMarkers(marksCopy, marksBrightBinary);

		ImageFunctions.mergeMarkers(marksDarkBinary, prevComponentsAnalysis, dilationRadius);
		if (blobMergeThreshold > 0)
			marksDarkBinary = ImageFunctions.mergeBinaryMarkersInTheSameRegion(watershedImage, marksDarkBinary, 35,
					blobMergeThreshold);

		// combine markers from bright and dark blobs, AFTER DARK BLOBS MERGING
		boolean addBrightMarkers = true;
		if (addBrightMarkers) {
			ImageFunctions.addMarkers(marksDarkBinary, marksBrightBinary);
		}

		if (showBlobs) {
			ImageFunctions.normalize(marksDarkBinary, 0, 2);
			// marksDarkBinary = ImageFunctions.operationMorph(marksDarkBinary,
			// Operation.DILATION, Strel.Shape.DISK, 2);
			ip = original;
			// ImageFunctions.colorCirclesBySigmaMarkers(ip, marksSigma, true, false);
			ImageFunctions.colorCirclesBySigmaMarkers(ip, marksCopy, true, true, 7);
			ImageFunctions.colorCirclesBySigmaMarkers(ip, marksDarkBinary, true, true, 7);
			ImageFunctions.normalize(watershedImage, 0, 255);
			ImageFunctions.colorCirclesBySigmaMarkers(watershedImage, marksDarkBinary, true, true, 7);
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
		
		//here draw and show colored basins
		//ImageFunctions.colorWatershedBasins(ip);

		if (filterComponents) {
			ImageComponentsAnalysis compAnalisys;

			compAnalisys = new ImageComponentsAnalysis(ip, intensityImg, true); // get labelled component image and fill

			compAnalisys.setComponentsBrightBlobStateByMarks(marksBrightBinary);

			boolean discardWhiteBlobs = true;
			ip = compAnalisys.getFilteredComponentsIp(minArea, maxArea, minCircularity, maxCircularity, 0, 1000,
					discardWhiteBlobs);
			ImagePlus filtered = new ImagePlus("filtered", ip);
			// filtered.show();

			// here set component's state by marks image (indicate bright blobs)
			if (startedProcessing) // add roi only if we started processing
				compAnalisys.addRoisToManager(roiManager, imagePlus, currSlice);

			if (startedProcessing || doesStacks()) {
				prevComponentsAnalysis = compAnalisys;
				tracking.addComponentsAnalysis(compAnalisys);
			}
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

		ImageProcessor blobDots = blobs.findBlobsByLocalMaximaAsImage((float) heightTolerance, false, filterComponents,
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
			String roiFilePath = "C:\\Tokyo\\trackingResults\\c0010901_easy_ex-matchedROI.zip";
			String imageFilePath = "C:\\Tokyo\\example_sequences\\c0010901_easy_ex.tif";

			roiFilePath = "C:\\Tokyo\\trackingResults\\c0010907_easy_ex-matchedROI.zip";
			imageFilePath = "C:\\Tokyo\\example_sequences\\c0010907_easy_ex.tif";

			roiFilePath = "C:\\Tokyo\\trackingResults\\c0010906_medium_double_nuclei_ex-matchedROI.zip";
			imageFilePath = "C:\\Tokyo\\example_sequences\\c0010906_medium_double_nuclei_ex.tif";

			roiFilePath = "C:\\Tokyo\\trackingResults\\c0010913_hard_ex-matchedROI.zip";
			imageFilePath = "C:\\Tokyo\\example_sequences\\c0010913_hard_ex.tif";

			eval.convertToTRAformat(roiFilePath, imageFilePath);
			return;
		}
		if (!testImageJ) {
			System.out.println("Test");
			TrackingEvaluation tra = new TrackingEvaluation();
			// tra.writeTracksToFile_ctc("tracks.txt", null);
		} else {
			// set the plugins.dir property to make the plugin appear in the Plugins menu
			Class<?> clazz = Cell_Tracker.class;
			String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
			String pluginsDir = url.substring("file:".length(),
					url.length() - clazz.getName().length() - ".class".length());
			System.setProperty("plugins.dir", pluginsDir);

			// start ImageJ
			new ImageJ();
			ImagePlus image;
			ImagePlus image_stack20 = IJ.openImage("C:\\Tokyo\\C002_Movement.tif");
			ImagePlus image_c14 = IJ.openImage("C:\\Tokyo\\170704DataSeparated\\C0002\\c0010914_C002.tif");
			ImagePlus image_stack3 = IJ.openImage("C:\\Tokyo\\\\movement_3images.tif");
			ImagePlus image_stack10 = IJ.openImage("C:\\Tokyo\\C002_10.tif");
			ImagePlus image_shorter_bright_blobs = IJ.openImage("C:\\Tokyo\\Short_c1_ex.tif");

			ImagePlus image_ex_01 = IJ.openImage("C:\\Tokyo\\example_sequences\\c0010901_easy_ex.tif");
			ImagePlus image_ex_06 = IJ.openImage("C:\\Tokyo\\example_sequences\\c0010906_medium_double_nuclei_ex.tif");
			ImagePlus image_ex_07 = IJ.openImage("C:\\Tokyo\\example_sequences\\c0010907_easy_ex.tif");
			ImagePlus image_ex_13 = IJ.openImage("C:\\Tokyo\\example_sequences\\c0010913_hard_ex.tif");

			image = image_ex_01;
			//image = image_ex_07;
			// image = image_stack20;
			// image = image_stack10;
			// image = image_stack3;
			// image = image_c10;
			// image = image_ez_division;
			// image = image_test_tracking;
			// image = image_shorter_bright_blobs;
			// image = image_ex_06;
			// image = image_ex_13;
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
