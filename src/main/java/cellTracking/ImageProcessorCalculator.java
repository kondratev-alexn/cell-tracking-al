package cellTracking;

import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

/* Class providing different mathematical operations on images 
 * All results are written into the first argument */
public class ImageProcessorCalculator {

	/* Adds two images, ip1 = ip1 + ip2 */
	static public void add(ImageProcessor ip1, ImageProcessor ip2) {
		int w = ip1.getWidth(), h = ip1.getHeight();
		if (w != ip2.getWidth() || h!= ip2.getHeight())
			return;
		for (int y=0; y < h; y++) {
			for (int x=0; x < w; x++) {
				float sum = ip1.getf(x,y) + ip2.getf(x,y);
				ip1.setf(x, y, sum);
			}
		}
	}
	
	/* Linear combination of two images, ip1 = alpha*ip1 + beta*ip2 */
	static public void linearCombination(float alpha, ImageProcessor ip1, float beta, ImageProcessor ip2) {
		int w = ip1.getWidth(), h = ip1.getHeight();
		if (w != ip2.getWidth() || h!= ip2.getHeight())
			return;
		for (int i=0; i<ip1.getPixelCount(); i++) {
			float sum = alpha*ip1.getf(i) + beta*ip2.getf(i);
			ip1.setf(i, sum);
		}
	}
	
	/* Subtract two images, ip1 = ip1 - ip2 */
	static public void sub(ImageProcessor ip1, ImageProcessor ip2) {
		int w = ip1.getWidth(), h = ip1.getHeight();
		if (w != ip2.getWidth() || h!= ip2.getHeight())
			return;
		for (int y=0; y < h; y++) {
			for (int x=0; x < w; x++) {
				float res = ip1.getf(x,y) - ip2.getf(x,y);
				ip1.setf(x, y, res);
			}
		}
	}
	
	/* multiplies two images (unexpected result for binary or short images) */
	static public void multiply(ImageProcessor ip1, ImageProcessor ip2) {
		int w = ip1.getWidth(), h = ip1.getHeight();
		if (w != ip2.getWidth() || h!= ip2.getHeight())
			return;
		for (int y=0; y < h; y++) {
			for (int x=0; x < w; x++) {
				float sum = ip1.getf(x,y)*ip2.getf(x,y);
				ip1.setf(x, y, sum);
			}
		}
	}
	
	/* divide: ip1 = ip1/ip2. If ip2 absolute values are less than eps, the result is zero. (unexpected result for binary or short images) */
	static public void divide(ImageProcessor ip1, ImageProcessor ip2, float eps) {
		int w = ip1.getWidth(), h = ip1.getHeight();
		if (w != ip2.getWidth() || h!= ip2.getHeight())
			return;
		float sum;
		for (int y=0; y < h; y++) {
			for (int x=0; x < w; x++) {
				if (Math.abs(ip2.getf(x, y)) < eps)
					sum = 0;
				else 
					sum = ip1.getf(x,y)/ip2.getf(x,y);
				ip1.setf(x, y, sum);
			}
		}
	}
	
	/* calculates square root for each pixel */
	static public void sqrt(ImageProcessor ip) {
		for (int i=0; i<ip.getPixelCount(); i++) {
			ip.setf(i, (float) Math.sqrt(ip.getf(i)));
		}
	}
	
	/* take absolute values of the image (|ip|) */
	static public void abs(ImageProcessor ip) {
		for (int i=0; i<ip.getPixelCount(); i++) {
			ip.setf(i, Math.abs(ip.getf(i)));
		}
	}
	
	/* multiply image by constant value */
	static public void constMultiply(ImageProcessor ip, float val) {
		for (int i=0; i<ip.getPixelCount(); i++) {
			ip.setf(i, val * ip.getf(i));
		}
	}
	
	/* return inverted image */
	static public ImageProcessor invertedImage(ImageProcessor ip) {
		ImageProcessor result = ip.duplicate();
		for (int i=0; i<ip.getPixelCount(); i++) {
			result.setf(i, -ip.getf(i));
		}
		return result;
	}
}
