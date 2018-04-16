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
import ij.plugin.ImageCalculator;
import ij.plugin.filter.BackgroundSubtracter;
import ij.plugin.filter.RankFilters;
import ij.plugin.frame.RoiManager;
import inra.ijpb.morphology.Morphology.Operation;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.Strel.Shape;
import inra.ijpb.morphology.MinimaAndMaxima;
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

	/* image for displaing result on stacks */
	private ImagePlus stackImage;

	/* roi manager for current slice */
	private RoiManager roiManager;

	private int currSlice = 1; // slice number for stack processing
	private int selectedSlice; // currently selected slice

	// sigmas for bandpass algorithm, also in UI
	public double sigma1 = 1.40;
	public double sigma2 = 10.00;
	public double sigma3 = 0.80;

	/* numerical parameters for UI */
	private double heightTolerance = 0.01; // now its threshold for lambda2+lambda1 in blob detection
	private double heightToleranceBright = 0.20;
	private int rollingBallRadius = 20; // for background subtraction
	private int topHatRadius = 20;
	private double medianRadius = 2;
	private double minThreshold = 20;
	private double maxThreshold = 50;
	private int minArea = 100;
	private int maxArea = 1200;
	private float minCircularity = 0.55f;
	private float maxCircularity = 1.0f;
	private int dilationRadius = 2;
	private int maximumNumberOfCells = 40; // how many dark blobs will be detected
	
	private float blobMergeThreshold = 0.3f; //threshold, below which blobs will be merged

	private float[] sigmas = { 4, 7, 9, 12, 16, 32 };

	/* booleans for CheckBoxes */
	private boolean isTestMode = false;
	private boolean useMedian = true;
	private boolean isBandpass = true;
	private boolean useOtsuThreshold = false;

	private boolean showImageForWatershedding = false;
	private boolean filterComponents = true;
	private boolean previewing = false;

	private boolean roiBrowserActive = false; // becomes true after the processing
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

			stackImage.copyScale(imagePlus);
			stackImage.resetDisplayRange();
			// stackImage.show();

			roiManager.selectAndMakeVisible(imagePlus, -1);

			// there goes tracking
			int maxRadiusDark = 25, maxRadiusBright = 18, slices = 3;
			double oneSliceScoreThreshold = 0.25;
			double scoreThreshold = 2;
			double timeDecayCoefficient = 1;
			// tracking.trackComponents(maxRadiusDark, maxRadiusBright, slices,
			// scoreThreshold);
			// tracking.trackComponentsOneAndMultiSlice(maxRadiusDark, slices,
			// scoreThreshold, oneSliceScoreThreshold, timeDecayCoefficient);
			tracking.trackComponentsOneSlice(maxRadiusDark, oneSliceScoreThreshold);
			tracking.fillTracks();
			tracking.analyzeTracksForMitosis();
			tracking.startMitosisTracking(40, 0.12);
			tracking.trackComponentsMultiSlice(maxRadiusDark, slices, scoreThreshold, timeDecayCoefficient);

			// System.out.println(tracking.getGraph());
			// System.out.println(tracking.getGraph().checkNoEqualNodes());
			ImageProcessor ip = imp.getProcessor();
			// tracking.drawTracksIp(ip);
			ImagePlus trResult = tracking.drawTracksImagePlus(imp);
			trResult.setTitle("Tracking results");
			imp.show();
			trResult.show();

			Graph cellGraph = tracking.getGraph();

			CellTrackingGraph resultGraph = new CellTrackingGraph(tracking, roiManager, imp);
			resultGraph.showTrackedComponentImages();
			// resultGraph.printTrackedGraph();
			resultGraph.writeTracksToFile_ctc_afterAnalysis("res_track.txt");
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
		gd.addNumericField("Maximum number of cells", maximumNumberOfCells, 0);
		gd.addNumericField("Median filter radius", medianRadius, 2);
		gd.addNumericField("Rolling ball radius", rollingBallRadius, 0);
		gd.addNumericField("Closing radius", topHatRadius, 0);
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
		gd.addNumericField("Blob merge threshold", blobMergeThreshold, 3);
		gd.addCheckbox("test mode", isTestMode);
		gd.addCheckbox("Median Filter", useMedian);
		gd.addCheckbox("Bandpass", isBandpass);
		// gd.addCheckbox("Use Auto Otsu threshold", useOtsuThreshold);
		gd.addCheckbox("Show Image before Watershedding", showImageForWatershedding);
		gd.addCheckbox("Filter components", filterComponents);
		gd.addCheckbox("Show blobs", showBlobs);
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
		maximumNumberOfCells = (int) gd.getNextNumber();
		medianRadius = gd.getNextNumber();
		rollingBallRadius = (int) gd.getNextNumber();
		topHatRadius = (int) gd.getNextNumber();
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
		blobMergeThreshold = (float) gd.getNextNumber();
		
		isTestMode = gd.getNextBoolean();
		useMedian = gd.getNextBoolean();
		isBandpass = gd.getNextBoolean();
		// useOtsuThreshold = gd.getNextBoolean();
		showImageForWatershedding = gd.getNextBoolean();
		filterComponents = gd.getNextBoolean();
		showBlobs = gd.getNextBoolean();
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
			if (previewing) // if preview was on, return the stack to original images
				resetPreview();
			result = ip.duplicate();
		} else
			result = baseImage.duplicate();

		if (useMedian) {
			// rankFilters.rank(result, medianRadius, RankFilters.MEDIAN);
			gaussian.GaussianBlur(result, (float) medianRadius);
		}
		cellMask = ImageFunctions.getWhiteObjectsMask(ip, 1, 15);

		if (rollingBallRadius > 0)
			backgroundSub.rollingBallBackground(result, rollingBallRadius, false, false, false, true, false);
		ImageFunctions.normalize(result, 0, 1);

		if (isTestMode) {
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

			test = ImageFunctions.operationMorph(test, Operation.CLOSING, Shape.DISK, maximumNumberOfCells);
			ImageProcessorCalculator.sub(original, test);

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

			ImageProcessor blobDots = blobs.findBlobsBy3x3LocalMaximaAsImage((float) heightTolerance, false,
					filterComponents, 4);
			// result = blobs.findBlobsByMaxSigmasImage();
			// ImageFunctions.drawCirclesBySigmaMarkerks(original, blobDots, true);
			// ImageFunctions.drawGaussian(result, 200, 200, (float) sigma1);
			// original = ImageFunctions.operationMorph(original, Operation.BOTTOMHAT,
			// Strel.Shape.DISK, topHatRadius);
			// original = MinimaAndMaxima.extendedMaxima(original, minCircularity);
			// original = MinimaAndMaxima.regionalMinimaByReconstruction(original, 4);
			// original = MinimaAndMaxima.extendedMinima(original, otsu);

			if (previewing && !doesStacks()) {
				for (int i = 0; i < ip.getPixelCount(); i++) {
					ip.setf(i, original.getf(i));
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
	 * (1 ver) Marker controlled watershed on gradient of the bandpass (or
	 * intensity), markers are minima of the bandpass (or intensity). (1 ver)
	 * Bandpass or intensity is selected by the checkbox
	 */
	private ImageProcessor maximaWatershedSegmentation(ImageProcessor ip, double sigma, double minThreshold,
			double maxThreshold) {
		// assume ip is already preprocessed, i.e. filtered, background subtracted
		ImageProcessor watershedImage = ip.duplicate();
		ImageProcessor intensityImg;
		ImageProcessor watershedMask;
		intensityImg = ip.duplicate();

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
		ImageProcessor ip_brightBlobs = ip.duplicate();
		ImageProcessorCalculator.constMultiply(ip_brightBlobs, -1);
		BlobDetector brightBlobs = new BlobDetector(ip_brightBlobs, cellMask, sigmas_bright);

		// ImageProcessor findMaximaImage = blobs.findBlobsByMaxSigmasImage();
		ImageProcessor marksDarkBinary, marksBrightBinary;
		// marks = maxfinder.findMaxima(findMaximaImage, heightTolerance,
		// MaximumFinder.SINGLE_POINTS, true);
		marksDarkBinary = blobs.findBlobsBy3x3LocalMaximaAsImage((float) heightTolerance, true, true,
				maximumNumberOfCells);
		ImageProcessor marksSigma = blobs.findBlobsBy3x3LocalMaximaAsImage((float) heightTolerance, false, true,
				maximumNumberOfCells);

		// find bright markers to create more watershed seeds outside of cells
		marksBrightBinary = brightBlobs.findBlobsBy3x3LocalMaximaAsImage((float) heightToleranceBright, true, true,
				maximumNumberOfCells / 4);

		ImageProcessor circles = ip.duplicate();
		ImageFunctions.drawCirclesBySigmaMarkerks(circles, marksSigma, true);

		if (minCircularity > 0.551)
			return circles;

		// combine markers from bright and dark blobs
		boolean addBrightMarkers = true;
		if (addBrightMarkers) {
			ImageFunctions.addMarkers(marksDarkBinary, marksBrightBinary);
		}

		FloatHistogram hist = new FloatHistogram(ip);
		float otsu = hist.otsuThreshold();

		ImageProcessor closingImage = ImageFunctions.operationMorph(ip, Operation.CLOSING, Shape.DISK, 20);

		ImageProcessorCalculator.sub(watershedImage, closingImage);
		ImageFunctions.normalize(watershedImage, 0, 1);
		// watershedImage = ImageProcessorCalculator.invertedImage(watershedImage);


		if (showImageForWatershedding) {
			ImageFunctions.normalize(watershedImage, 0, 255);
			return watershedImage;
		}

		ImageFunctions.mergeMarkers(marksDarkBinary, prevComponentsAnalysis, dilationRadius);
		marksDarkBinary = ImageFunctions.mergeBinaryMarkersInTheSameRegion(watershedImage, marksDarkBinary, 30,
				blobMergeThreshold);
		
		if (showBlobs) {
			ImageFunctions.normalize(marksDarkBinary, 0, 5);
			ImageFunctions.drawCirclesBySigmaMarkerks(watershedImage, marksDarkBinary, true);
			return watershedImage;
		}

		ImageFunctions.LabelMarker(marksDarkBinary);
		if (sigma > 0)
			gaussian.GradientMagnitudeGaussian(watershedImage, (float) sigma); //watershed is better on gradient...
		MarkerControlledWatershedTransform2D watershed = new MarkerControlledWatershedTransform2D(watershedImage,
				marksDarkBinary, null, 4);
		ip = watershed.applyWithPriorityQueue();

		if (filterComponents) {
			ImageComponentsAnalysis compAnalisys;

			compAnalisys = new ImageComponentsAnalysis(ip, intensityImg, true); // get labelled component image and fill

			boolean discardWhiteBlobs = true;
			compAnalisys.setComponentsBrightBlobStateByMarks(marksBrightBinary);
			if (discardWhiteBlobs)
				compAnalisys.discardWhiteBlobComponents();
			ip = compAnalisys.getFilteredComponentsIp(minArea, maxArea, minCircularity, maxCircularity, 0, 1000);

			// here set component's state by marks image (indicate bright blobs)
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
		boolean testImageJ = true;
		boolean traConvert = false;
		if (traConvert) {
			EvaluationFromRoi eval = new EvaluationFromRoi();
			new ImageJ();
			String roiFilePath = "C:\\Tokyo\\trackingResults\\c0010901_easy_ex-matchedROI.zip";
			String imageFilePath = "C:\\Tokyo\\example_sequences\\c0010901_easy_ex.tif";
			eval.convertToTRAformat(roiFilePath, imageFilePath);
			return;
		}
		if (!testImageJ) {
			System.out.println("HELLO THERE");
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
			ImagePlus image_bright_blobs = IJ.openImage("C:\\Tokyo\\example_sequences\\c0010901_easy_ex.tif");
			ImagePlus image_ez_division = IJ.openImage("C:\\Tokyo\\division.tif");
			ImagePlus image_stack10 = IJ.openImage("C:\\Tokyo\\C002_10.tif");
			ImagePlus image_test_tracking = IJ.openImage("C:\\Tokyo\\test_multi.tif");
			ImagePlus image_shorter_bright_blobs = IJ.openImage("C:\\Tokyo\\Short_c1_ex.tif");

			// image = image_bright_blobs;
			// image = image_stack20;
			// image = image_stack10;
			// image = image_stack3;
			// image = image_c10;
			// image = image_ez_division;
			// image = image_test_tracking;
			image = image_shorter_bright_blobs;
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
