package cellTracking;

import ij.plugin.ImageCalculator;
import ij.plugin.filter.Convolver;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

/* class for calculating gausian kernels, its derivatives and convolving with them 
 * It's sufficient to make kernels only; everything else can be done with ImageJ convolving operations
 * */
public class Gaussian {

	/* Compute Gaussian derivative dx (changes ip) */
	public void GaussianDerivativeX(ImageProcessor ip, float sigma) {
		FloatProcessor fp_dx = null;
		fp_dx = ip.duplicate().convertToFloatProcessor();

		float[] kern_diff = gaussDer1D(sigma);
		float[] kern_x = gauss1D(sigma);
		Convolver con = new Convolver();
		con.convolveFloat1D(fp_dx, kern_diff, kern_diff.length, 1); // x direction (dx)
		con.convolveFloat1D(fp_dx, kern_x, 1, kern_x.length); // y direction (y)
		ip.setPixels(0, fp_dx);
	}

	/* Compute Gaussian derivative dy (changes ip) */
	public void GaussianDerivativeY(ImageProcessor ip, float sigma) {
		FloatProcessor fp_dy = null;
		fp_dy = (FloatProcessor) ip.duplicate();

		float[] kern_diff = gaussDer1D(sigma);
		float[] kern_x = gauss1D(sigma);
		Convolver con = new Convolver();
		con.convolveFloat1D(fp_dy, kern_diff, 1, kern_diff.length); // y direction (dy)
		con.convolveFloat1D(fp_dy, kern_x, kern_x.length, 1); // x direction (x)
		ip.setPixels(0, fp_dy);
	}

	public void GaussianDerivativeXX(ImageProcessor ip, float sigma) {
		FloatProcessor temp = ip.duplicate().convertToFloatProcessor();

		float v;
		GaussianBlur(temp, sigma);
		float[] kern_der2 = { 1, -2, 1 };
		Convolver con = new Convolver();
		con.convolveFloat1D(temp, kern_der2, kern_der2.length, 1);
		
		ip.setPixels(0, temp);
	}

	public void GaussianDerivativeYY(ImageProcessor ip, float sigma) {
		FloatProcessor temp = ip.duplicate().convertToFloatProcessor();
		float v;
		GaussianBlur(temp, sigma);
		float[] kern_der2 = { 1, -2, 1 };
		Convolver con = new Convolver();
		con.convolveFloat1D(temp, kern_der2, 1, kern_der2.length);
		
		ip.setPixels(0, temp);
	}

	public void GaussianDerivativeXY(ImageProcessor ip, float sigma) {
		FloatProcessor temp = ip.duplicate().convertToFloatProcessor();
		float v;
		GaussianBlur(temp, sigma);
		float[] kern_der2 = { -0.5f, 0, 0.5f };
		Convolver con = new Convolver();
		con.convolveFloat1D(temp, kern_der2, 1, kern_der2.length);
		con.convolveFloat1D(temp, kern_der2, kern_der2.length, 1);

		ip.setPixels(0, temp);
	}

	public void GaussianBlur(ImageProcessor ip, float sigma) {
		FloatProcessor fp = ip.convertToFloatProcessor();

		float[] kern = gauss1D(sigma);

		Convolver con = new Convolver();
		con.convolveFloat1D(fp, kern, 1, kern.length); // x direction
		con.convolveFloat1D(fp, kern, kern.length, 1); // y direction

		ip.setPixels(0, fp);
	}

	public void GradientMagnitudeGaussian(ImageProcessor ip, float sigma) {
		// FloatProcessor fpy = (FloatProcessor)ip.duplicate();
		FloatProcessor fpy = ip.duplicate().convertToFloatProcessor();
		GaussianDerivativeX(ip, sigma);
		GaussianDerivativeY(fpy, sigma);
		ImageProcessorCalculator.multiply(ip, ip);
		ImageProcessorCalculator.multiply(fpy, fpy);
		ImageProcessorCalculator.add(ip, fpy);
		ImageProcessorCalculator.sqrt(ip);
	}

	/* get 1d gaussian kernel, normalized */
	private float[] gauss1D(float sigma) {
		if (sigma == 0) {
			float[] one = { 1 };
			return one;
		}
		if (sigma < 0)
			sigma = -sigma;
		/* mask size is ~[6 sigma x 6 sigma] */
		int r = (int) Math.round(6 * sigma + 0.5); // ex. for sigma=1 r=5
		if (r % 2 == 0)
			r = r + 1;
		final int r2 = r / 2;

		/* masks with gauss coefficients */
		float[] kernel = new float[r];

		final double sig = 1 / (sigma * Math.sqrt(2 * Math.PI));
		for (int k = 0; k < r; k++) {
			kernel[k] = (float) (Math.exp(-(k - r2) * (k - r2) / (2 * sigma * sigma)) * sig);
		}
		return kernel;
	}

	/*
	 * gaussian derivative kernel 1d. The kernel is normalized so that left absolute
	 * value of "left" and "right" parts equals 1
	 */
	private float[] gaussDer1D(float sigma) {
		if (sigma == 0) {
			float[] one = { 1 };
			return one;
		}
		if (sigma < 0)
			sigma = -sigma;
		int r = (int) Math.round(6 * sigma + 0.5); // ex. for sigma=1 r=5
		if (r % 2 == 0)
			r = r + 1;
		int r2 = r / 2;
		float[] maskDerX = new float[r];
		final double sigPi = 1 / (sigma * sigma * sigma * Math.sqrt(2 * Math.PI));
		float half_sum = 0;
		for (int k = 0; k < r; k++) {
			maskDerX[k] = (float) ((k - r2) * Math.exp(-(k - r2) * (k - r2) / (2 * sigma * sigma)) * sigPi);
			if (k < r2)
				half_sum += maskDerX[k];
		}
		for (int k = 0; k < r; k++) {
			maskDerX[k] = maskDerX[k] / half_sum;
		}
		return maskDerX;
	}
}
