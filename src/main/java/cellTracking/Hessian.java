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
		Gaussian gaus = new Gaussian();
		Ixx=ip.duplicate(); Ixy=ip.duplicate(); Iyy=ip.duplicate();
		gaus.GaussianDerivativeX(Ixx, sigma);
		Ixy = Ixx.duplicate();
		gaus.GaussianDerivativeX(Ixx, sigma);
		gaus.GaussianDerivativeY(Ixy, sigma);
		gaus.GaussianDerivativeY(Iyy, sigma);
		gaus.GaussianDerivativeY(Iyy, sigma);
		
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
	
	/* test function to find better lambdas function.
	 * Some comments: lambda2 is negative, with large absolute value along the edges, zero on the edges.
	 * lambda 1 has large values in the center of the blobs if sigma is right. 
	 * So mb l
	 */
	public ImageProcessor testValue() {
		ImageProcessor res = getLambda2();
		//res = getLambda1();
		return res;
	}
	
	/* get correct lambdas ration; if one f the values is less than threshold, then the result is zero*/
	public ImageProcessor getLambdasRatio(float threshold, boolean absLambda1, boolean absLambda2) {
		ImageProcessor res = lambda1.duplicate();
		float v1,v2;
		for (int i=0; i<lambda1.getPixelCount(); i++) {
			v1 = lambda1.getf(i); 
			v2 = lambda2.getf(i);
			if (absLambda1 && v1<0) v1 = -v1;
			if (absLambda2 && v2<0) v2 = -v2;			
			if (Math.abs(v1) < threshold || Math.abs(v2) < threshold)
				 res.setf(i, 0);
			else res.setf(i, v1/lambda2.getf(i));
		}
		return res;
	}
}
