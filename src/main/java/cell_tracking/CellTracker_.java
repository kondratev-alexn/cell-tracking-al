package cell_tracking;

import java.awt.AWTEvent;

import java.util.Arrays;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Macro;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.ImageLayout;
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
import cell_tracking.MexicanHatFilter;
import ij.plugin.ContrastEnhancer;
import ij.plugin.Converter;
import ij.plugin.filter.BackgroundSubtracter;
import ij.plugin.filter.RankFilters;
import inra.ijpb.binary.BinaryImages;
import inra.ijpb.morphology.AttributeFiltering;
import inra.ijpb.morphology.GeodesicReconstruction;
import inra.ijpb.morphology.Morphology.Operation;
import inra.ijpb.morphology.Strel;
import inra.ijpb.morphology.Strel.Shape;
import inra.ijpb.morphology.attrfilt.AreaOpeningQueue;
import inra.ijpb.plugins.*;

public class CellTracker_ implements ExtendedPlugInFilter, DialogListener {
	
	private int flags = DOES_ALL | KEEP_PREVIEW | FINAL_PROCESSING | NO_CHANGES;
	
	private int nPasses = 1;          // The number of passes (filter directions * color channels * stack slices)
	private int nChannels = 1;        // The number of color channels
	private int pass;                 // Current pass
	
	private int nSlices;
		
	/** need to keep the instance of ImagePlus */ 
	private ImagePlus imagePlus;
	
	/** keep the original image, to restore it after the preview */
	private ImageProcessor baseImage;
	
	/** Keep instance of result image */
	private ImageProcessor result;		
	
	/* image for displaing result on stacks */
	private ImagePlus stackImage;
	
	private int currSlice = 1;
	private int selectedSlice;

	// sigmas for bandpass algorithm
	public double sigma1 = 1.40;
	public double sigma2 = 5.00;
	public double sigma3;
	
	private double medianSize = 5;
	private double minThreshold = -50;
	private double maxThreshold = -2;
	private boolean useMedian = false;
	private boolean isBandpass = false;
	private boolean doThreshold = false;
	private boolean previewing = false;

	private final float sigmaMax = 50;	// max value of sigmas
	
	// plugins
	private GaussianBlur gBlur;
	private Duplicator dupl;
	private ImageCalculator imgCalc;
	private MexicanHatFilter mHat;
	private ContrastEnhancer enchancer;
	private Gaussian gaussian;
	private ImageProcessorCalculator calc;
	private BackgroundSubtracter backgroundSub;
	private MorphologicalFilterPlugin morph; 
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
				
		if (arg.equals("final")) 
		{
			// replace the preview image by the original image 
			resetPreview();
			imagePlus.updateAndDraw();
	    	
			stackImage.copyScale(imagePlus);
			stackImage.resetDisplayRange();
			stackImage.show();
			// Create a new ImagePlus with the filter result
			/*String newName = createResultImageName(imagePlus);
			ImagePlus resPlus = new ImagePlus(newName, result);
			resPlus.copyScale(imagePlus);
			resPlus.resetDisplayRange();
			resPlus.show();*/
			return DONE;
		}
		
		nSlices = imp.getStackSize();
		// convert to float if plugin just started
		if (nSlices == 1) {
			Converter conv = new Converter();
			conv.run("32-bit");
		}
		else {
			StackConverter stackConv = new StackConverter(imp);
			stackConv.convertToGray32();
		}
		instancePlugins();

		return flags;
	}
	
	/* creates instances of used plugins 
	 * */
	private void instancePlugins() {
		gBlur = new GaussianBlur();
		dupl = new Duplicator();
		imgCalc = new ImageCalculator();
		mHat = new MexicanHatFilter();
		enchancer = new ContrastEnhancer();
		gaussian = new Gaussian();
		calc = new ImageProcessorCalculator();
		backgroundSub = new BackgroundSubtracter();
		morph = new MorphologicalFilterPlugin();
		rankFilters = new RankFilters();
	}

	@Override
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
		// Normal setup
    	this.imagePlus = imp;
    	this.baseImage = imp.getProcessor().duplicate();
    	stackImage = imp.duplicate();
        nChannels = imp.getProcessor().getNChannels();
        
        GenericDialog gd = new GenericDialog(command);
        fillGenericDialog(gd, pfr);
        
        gd.showDialog();                    // input by the user (or macro) happens here
        
        if (gd.wasCanceled()) { 
        	resetPreview();
        	return DONE;
        }
        
        currSlice = 1;
        selectedSlice = imp.getCurrentSlice();
        IJ.register(this.getClass());       // protect static class variables (parameters) from garbage collection
        //return flags;
        flags = IJ.setupDialog(imp, flags); // ask whether to process all slices of stack (if a stack)
        
        return flags;  
	}
	
	private void fillGenericDialog(GenericDialog gd, PlugInFilterRunner pfr) {
		gd.addNumericField("median filter size", medianSize, 0);
		gd.addNumericField("Sigma1:", sigma1, 2);
        gd.addNumericField("Sigma2:", sigma2, 2);
        gd.addNumericField("Sigma (hat)", 5.00, 2);
        gd.addNumericField("Min threshold", minThreshold, 3);
        gd.addNumericField("Max threshold", maxThreshold, 3);
        gd.addCheckbox("Median Filter", true);
        gd.addCheckbox("Bandpass?", true);
        gd.addCheckbox("Threshold", true);
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
	}

	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		boolean wasPreview = this.previewing;
		parseDialogParameters(gd);
        if (sigma1 < 0 || sigma2 < 0 || sigma3 < 0 || gd.invalidNumber())
            return false;
    	
    	// if preview checkbox was unchecked, replace the preview image by the original image
    	if (wasPreview && !this.previewing)     	{
    		resetPreview();
    	}
    	return true;
	}

	private void parseDialogParameters(GenericDialog gd) 
	{
		// extract chosen parameters
		medianSize = gd.getNextNumber();
		sigma1 = gd.getNextNumber();
		sigma2 = gd.getNextNumber();
		sigma3 = gd.getNextNumber();
		minThreshold = gd.getNextNumber();
		maxThreshold = gd.getNextNumber();
		useMedian = gd.getNextBoolean();
		isBandpass = gd.getNextBoolean();
		doThreshold = gd.getNextBoolean();
		previewing = gd.getPreviewCheckbox().getState();		
		
		if (sigma1 > sigmaMax) sigma1 = sigmaMax;
		if (sigma2 > sigmaMax) sigma2 = sigmaMax;
		if (sigma3 > sigmaMax) sigma3 = sigmaMax;
	}

	@Override
	public void setNPasses(int nPasses) {
		//this.nPasses = 2 * nChannels * nPasses;
		this.nPasses = nPasses;
        pass = 0;
	}

	@Override
	public void run(ImageProcessor ip) {
		if ((flags&DOES_STACKS) !=0 ) {//stack processing
			result = ip.duplicate();
		}
		else 
			result = baseImage.duplicate();
		
		
		ImageComponentsAnalisis compAnalisys;
		
		//if we already processed image for previewing the current slide, then don't process it again
		if (!(currSlice == selectedSlice && previewing && (flags&DOES_STACKS) !=0)) {
			if (useMedian) 
				rankFilters.rank(result, medianSize/2, RankFilters.MEDIAN);
			backgroundSub.rollingBallBackground(result, 20, false, false, false, false, false);	
			//ImageFunctions.normalize(result, 0.0f, 255.0f);
			segmentation(result);
			compAnalisys = new ImageComponentsAnalisis(result);
			System.out.println(compAnalisys.toString());
		}
		
		if (nSlices == 1)
			stackImage.setProcessor(result);
		else 
			stackImage.getImageStack().setProcessor(result, currSlice);
		//IJ.selectWindow(stackImage.getID());
		//System.out.println("aftershow");
		
		if ((flags & DOES_STACKS) != 0) { //process stacks
			currSlice++;
			//System.out.println("in curr_slice++");
		}
		
		if (previewing && (flags&DOES_STACKS) == 0)
    	{
			// System.out.println("in prev");
    		// Fill up the values of original image with values of the result
    		for (int i = 0; i < ip.getPixelCount(); i++)     		{
    			ip.setf(i, result.getf(i));
    		}
    		ip.resetMinAndMax();
        }
	}
	
	private void segmentation(ImageProcessor ip) {
		if (isBandpass) {
			bandpassFilter(result);
			//ImageFunctions.normalize(result, 0f, 255f);
		}
		else { //gradient
			gaussian.GradientMagnitudeGaussian(result, (float)sigma1);
			ImageFunctions.clippingIntensity(result, 0, 500);
			//gaussian.GradientMagnitudeGaussian(result_t, (float)sigma2);
			//calc.sub(result, result_t);
		}
		if (doThreshold) {
			calc.threshold(result, minThreshold, maxThreshold);
			//result = GeodesicReconstruction.fillHoles(result);
			/*
			Operation op = Operation.BOTTOMHAT;
			Shape shape = Shape.DISK;
			Strel strel = shape.fromRadius(20);

			result = op.apply(result, strel);
			result.invert();
			
			result = GeodesicReconstruction.killBorders(result);
			//bp = (ByteProcessor) op.apply(bp, strel);
			//bp = (ByteProcessor) GeodesicReconstruction.killBorders(bp);
			GrayscaleAttributeFilteringPlugin attrFilt = new GrayscaleAttributeFilteringPlugin();
			AreaOpeningQueue algo = new AreaOpeningQueue();
			algo.setConnectivity(4);
			result = algo.process(result, 80);*/
			
			//attrFilt.
			//calc.byteToFloatBinary((FloatProcessor)result, bp);
			//ImagePlus img_t = new ImagePlus("test", bp);
			//img_t.show();
			//IJ.selectWindow(img_t.getID());
		}
	}
	
	void componentFiltering(ImageProcessor ip) {
		
	}

	/**
	 * Process an image.
	 * <p>
	 * Please provide this method even if {@link ij.plugin.filter.PlugInFilter} does require it;
	 * the method {@link ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)} can only
	 * handle 2-dimensional data.
	 * </p>
	 * <p>
	 * If your plugin does not change the pixels in-place, make this method return the results and
	 * change the {@link #setup(java.lang.String, ij.ImagePlus)} method to return also the
	 * <i>DOES_NOTHING</i> flag.
	 * </p>
	 *
	 * @param image the image (possible multi-dimensional)
	 */
	public void process(ImagePlus image) {
		// slice numbers start with 1 for historical reasons
		for (int i = 1; i <= image.getStackSize(); i++)
			process(image.getStack().getProcessor(i));
	}

	public void process(ImageProcessor ip) {
		/* int type = image.getType();
		if (type == ImagePlus.GRAY16)
			bandwidthFilter((short[]) ip.getPixels());
		else {
			throw new RuntimeException("not supported");
		} */
		bandpassFilter(ip);
	}

	private void bandpassFilter(ImageProcessor ip) {
		ImageProcessor ip1 = ip.duplicate();
		ImageProcessor ip2 = ip.duplicate();
		ip2.blurGaussian(sigma2);
		ip1.blurGaussian(sigma1);
		calc.sub(ip1, ip2);
		FloatProcessor fp = null;
		fp = ip1.toFloat(0, fp);
		ip.setPixels(0, fp);
		/*
		ImagePlus img1 = dupl.run(image);
		ImagePlus img2 = dupl.run(image);	
		
		img1.getProcessor().blurGaussian(sigma1);
		img2.getProcessor().blurGaussian(sigma2);
		img1 = imgCalc.run("sub create float", img1, img2);
		FloatProcessor fp = null;
		fp = img1.getProcessor().toFloat(0, fp);
		ip.setPixels(0, fp); */
		//ip = img1.getProcessor();
	}

	public void showAbout() {
		IJ.showMessage("ProcessPixels",
			"a template for processing each pixel of an image"
		);
	}
	
	public static void main(String[] args) {
		boolean testImageJ = true;
		if (!testImageJ) {
			System.out.println("HELLO THERE");
			Gaussian gaus = new Gaussian();
		}
		else {
			// set the plugins.dir property to make the plugin appear in the Plugins menu
			Class<?> clazz = CellTracker_.class;
			String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
			String pluginsDir = url.substring("file:".length(), url.length() - clazz.getName().length() - ".class".length());
			System.setProperty("plugins.dir", pluginsDir);

			// start ImageJ
			new ImageJ();

			// open one image of sequence. T0104 is for segmentation, T0050 is with mitosis
			ImagePlus image = IJ.openImage("C:\\Tokyo\\170704DataSeparated\\C0002\\c0010901\\T0001.tif");
			ImagePlus image_stack20 = IJ.openImage("C:\\Tokyo\\C002_Movement.tif");
			ImagePlus image105 = IJ.openImage("C:\\Tokyo\\170704DataSeparated\\C0002\\c0010901\\T0105.tif");
			ImagePlus image_c10 = IJ.openImage("C:\\Tokyo\\170704DataSeparated\\C0002\\c0010910\\T0001.tif");			
			
			//image = image_stack20;
			//image = image_c10;
			ImageConverter converter = new ImageConverter(image);
			converter.convertToGray32();
			image.show();

			// run the plugin
			IJ.runPlugIn(clazz.getName(), "");
		}
	}
	
	private void resetPreview()
	{
		ImageProcessor image = this.imagePlus.getProcessor();
		if (image instanceof FloatProcessor)
		{
			for (int i = 0; i < image.getPixelCount(); i++)
				image.setf(i, this.baseImage.getf(i));
		}
		else
		{
			for (int i = 0; i < image.getPixelCount(); i++)
				image.set(i, this.baseImage.get(i));
		}
		imagePlus.resetDisplayRange();
		imagePlus.updateAndDraw();
	}
	
	/**
	 * Creates the name for result image, by adding a suffix to the base name
	 * of original image.
	 * "Taken from MorphoLibJ"
	 */
	private String createResultImageName(ImagePlus baseImage) 
	{
		return baseImage.getShortTitle() + "-" + "result";
	}
}
