package cellTracking;

import javax.imageio.ImageReadParam;

import ij.ImagePlus;
import ij.process.ImageProcessor;

/* calculates Hessian of the image */
public class Hessian {
	
	/* image for which hessian is taken */
	private ImageProcessor ip;
	
	/* Hessian components */
	private ImageProcessor Ixx;
	private ImageProcessor Ixy;
	private ImageProcessor Iyy;
	
	private ImageProcessor lambda1;
	private ImageProcessor lambda2;

	/* get image for processing in constructor */
	Hessian(ImageProcessor image) {
		ip = image;
	}
	
	/* calculate Hessian of the Image for later usage */
	public void calculateHessian(float sigma) {
		ImageProcessor result = ip.duplicate();
		Gaussian gaus = new Gaussian();
		Ixx=ip.duplicate(); Ixy=ip.duplicate(); Iyy=ip.duplicate();
		gaus.GaussianDerivativeX(Ixx, (float)Math.sqrt(sigma));
		Ixy = Ixx.duplicate();
		gaus.GaussianDerivativeX(Ixx, (float)Math.sqrt(sigma));
		gaus.GaussianDerivativeY(Ixy, (float)Math.sqrt(sigma));
		gaus.GaussianDerivativeY(Iyy, (float)Math.sqrt(sigma));
		gaus.GaussianDerivativeY(Iyy, (float)Math.sqrt(sigma));
		
		float det, Pxx, Pxy, Pyy;
		lambda1 = ip.duplicate();
		lambda2 = ip.duplicate();
		for (int i=0; i<ip.getPixelCount(); i++) {
			Pxx = Ixx.getf(i);
			Pxy = Ixy.getf(i);
			Pyy = Iyy.getf(i);
			det = (float) Math.sqrt((Pxx-Pyy) *(Pxx-Pyy) + 4*Pxy*Pxy);
			lambda1.setf(i, (float)((Pxx+Pyy+det)/2));
			lambda2.setf(i, (float)((Pxx+Pyy-det)/2));
		}
	}
	
	public ImageProcessor getLambda1() {
		return lambda1;
	}
	
	public ImageProcessor getLambda2() {
		return lambda2;
	}
	
	public ImageProcessor getLambdasRatio() {
		ImageProcessorCalculator calc = new ImageProcessorCalculator();
		ImageProcessor res = lambda1.duplicate();
		calc.divide(res, lambda2);
		return res;
	}
}
