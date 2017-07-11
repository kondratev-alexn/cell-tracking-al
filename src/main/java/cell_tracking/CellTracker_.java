package cell_tracking;

import java.awt.AWTEvent;
import java.util.Arrays;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Macro;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.Blitter;
import ij.process.FloatProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.Duplicator;
import ij.plugin.ImageCalculator;
import cell_tracking.MexicanHatFilter;
import ij.plugin.ContrastEnhancer;

public class CellTracker_ implements ExtendedPlugInFilter, DialogListener {
	private int nPasses = 1;          // The number of passes (filter directions * color channels * stack slices)
	private int nChannels = 1;        // The number of color channels
	private int pass;                 // Current pass
	private final float sigmaMax = 50;	// max value of sigmas
	
	protected ImagePlus image;
	protected ImageProcessor img_proc; //current imageProcessor
	
	protected GaussianBlur gBlur;
	protected Duplicator dupl;
	protected ImageCalculator imgCalc;
	

	// image property members
	private int width;
	private int height;

	// plugin parameters
	public double value;
	public String name;
	
	// sigmas for bandpass algorithm
	public double sigma1 = 3.00;
	public double sigma2 = 5.00;
	public double sigma3;
	
	private boolean calledAsPlugin;
	private MexicanHatFilter mHat;
	private ContrastEnhancer enchancer;
	private Gaussian gaussian;
	private ImageProcessorCalculator calc;
	
	@Override
	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}

		image = imp;
		gBlur = new GaussianBlur();
		dupl = new Duplicator();
		imgCalc = new ImageCalculator();
		mHat = new MexicanHatFilter();
		enchancer = new ContrastEnhancer();
		gaussian = new Gaussian();
		calc = new ImageProcessorCalculator();
		//return DOES_8G | DOES_16 | DOES_32 | DOES_RGB;
		return DOES_ALL;
	}

	@Override
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
		calledAsPlugin = true;
        nChannels = imp.getProcessor().getNChannels();
        
        GenericDialog gd = new GenericDialog(command);
        //sigma = Math.abs(sigma);
        gd.addNumericField("Sigma1:", sigma1, 2);
        gd.addNumericField("Sigma2:", sigma2, 2);
        gd.addNumericField("Sigma (hat)", 5.00, 2);
        /*if (imp.getCalibration()!=null && !imp.getCalibration().getUnits().equals("pixels")) {
            hasScale = true;
            gd.addCheckbox("Scaled Units ("+imp.getCalibration().getUnits()+")", sigmaScaled);
        } else
            sigmaScaled = false;*/
        gd.addPreviewCheckbox(pfr);
        gd.addDialogListener(this);
        gd.showDialog();                    // input by the user (or macro) happens here
        if (gd.wasCanceled()) return DONE;
        IJ.register(this.getClass());       // protect static class variables (parameters) from garbage collection
        return IJ.setupDialog(imp, SNAPSHOT);  // ask whether to process all slices of stack (if a stack)
	}

	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		sigma1 = gd.getNextNumber();
		sigma2 = gd.getNextNumber();
		sigma3 = gd.getNextNumber();
		if (sigma1 > sigmaMax) sigma1 = sigmaMax;
		if (sigma2 > sigmaMax) sigma2 = sigmaMax;
		if (sigma3 > sigmaMax) sigma3 = sigmaMax;
		
        if (sigma1 < 0 || sigma2 < 0 || sigma3 < 0 || gd.invalidNumber())
            return false;
        return true;
	}

	@Override
	public void setNPasses(int nPasses) {
		this.nPasses = 2 * nChannels * nPasses;
        pass = 0;
	}

	@Override
	public void run(ImageProcessor ip) {
		img_proc = ip;
		// get width and height
		width = ip.getWidth();
		height = ip.getHeight();
		/*
		ip.medianFilter();
		mHat.mexicanHat(ip, sigma3);
		*/
		ImageProcessor ip_t = ip.duplicate();
		gaussian.GradientMagnitudeGaussian(ip, (float)sigma1);
		gaussian.GradientMagnitudeGaussian(ip_t, (float)sigma2);
		//calc.sub(ip, ip_t);
		//bandwidthFilter(ip);
		//ip = img_proc;
		//image.updateAndDraw();

		/*if (showDialog()) {
			process(ip);
			image.updateAndDraw();
		}*/

		enchancer.stretchHistogram(ip, 0);
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

	// Select processing method depending on image type
	public void process(ImageProcessor ip) {
		/* int type = image.getType();
		if (type == ImagePlus.GRAY16)
			bandwidthFilter((short[]) ip.getPixels());
		else {
			throw new RuntimeException("not supported");
		} */
		bandwidthFilter(ip);
	}

	private void bandwidthFilter(ImageProcessor ip) {
		ImagePlus img1 = dupl.run(image);
		ImagePlus img2 = dupl.run(image);	
//		ip.copy
		img1.getProcessor().blurGaussian(sigma1);
		img2.getProcessor().blurGaussian(sigma2);
		img1 = imgCalc.run("sub create float", img1, img2);
		FloatProcessor fp = null;
		fp = img1.getProcessor().toFloat(0, fp);
		ip.setPixels(0, fp);
		//ip = img1.getProcessor();
	}

	// processing of GRAY8 images
	public void process(byte[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += (byte)value;
			}
		}
	}

	// processing of GRAY16 images
	public void process(short[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += (short)value;
			}
		}
	}

	// processing of GRAY32 images
	public void process(float[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += (float)value;
			}
		}
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

			// open the Clown sample
			ImagePlus image = IJ.openImage("C:\\Tokyo\\170704DataSeparated\\C0002\\c0010901\\T0104.tif");
			ImageConverter converter = new ImageConverter(image);
			converter.convertToGray32();
			image.show();

			// run the plugin
			IJ.runPlugIn(clazz.getName(), "");
		}
	}

	/* method for getting floag images histogram, since it's not implemented 
	public int[] getHistogram(FloatProcessor fp) {
		int[] histogram = new int[65536];
		float[] pixels = (float[])fp.getPixels();
		for (int y=0; y<fp.getHeight(); y++) {
			int i = y*fp.getWidth();
			for (int x=0; x<fp.getWidth(); x++)
					histogram[pixels[i++]&0xffff]++;
		}
		return histogram;
	}*/

}
