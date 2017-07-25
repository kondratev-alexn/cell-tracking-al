package cell_tracking;

import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

/* Class for different image processing functions, like maybe filtering for certain processors, or normalizing the image */
public class ImageFunctions {

	/* normalizes image from [ip.min,ip.max] to [minValue, maxValue]. Works in place.  */
	public static void normalize(ImageProcessor ip, float minValue, float maxValue) {
		//find max and min first
		float ipmin = Float.MAX_VALUE, ipmax = Float.MIN_VALUE;
		float v;
		for (int i=0; i<ip.getPixelCount(); i++) {
			v = ip.getf(i);
			if (v < ipmin) ipmin = v;
			if (v > ipmax) ipmax = v;
		}
		System.out.println(ipmin);
		System.out.println(ipmax);
		
		// normalize
		for (int i=0; i<ip.getPixelCount(); i++) {
			v = ip.getf(i);
			ip.setf(i, (v-ipmin)/(ipmax-ipmin) * (maxValue - minValue) + minValue);
		}		
	}
	
	/* sets pixels below minValue to minValue, and higher than maxValue to maxValue */ 
	public static void clippingIntensity(ImageProcessor ip, float minValue, float maxValue) {
		for (int i=0; i<ip.getPixelCount(); i++) {
			if (ip.getf(i) < minValue) 
				ip.setf(i, minValue);
			else if (ip.getf(i) > maxValue) 
				ip.setf(i, maxValue);
		}
	}
	
	/* return binary image by 200-value thresholding */
	public static ImageProcessor getBinary(ImageProcessor fp) {
		int w = fp.getWidth(), h = fp.getHeight();
		ImageProcessor ip = new ByteProcessor(w, h);
		for (int y=0; y < h; y++) {
			for (int x=0; x < w; x++) {
				if (fp.getf(x,y) > 200 )
					ip.setf(x, y, 255);
			}
		}
		return ip;
	}
	
	/* "convert" float binary image to byte binary image with values 0, 255 */
	public static void floatToByteBinary(ByteProcessor ip, FloatProcessor fp) {
		int w = ip.getWidth(), h = ip.getHeight();
		if (w != fp.getWidth() || h!= fp.getHeight())
			return;
		for (int y=0; y < h; y++) {
			for (int x=0; x < w; x++) {
				if (fp.getf(x,y) > 200 )
					ip.setf(x, y, 255);
			}
		}
	}
	
	/* "convert" byte binary image tofloat binary image with values 0, 255 */
	public static void byteToFloatBinary(FloatProcessor ip, ByteProcessor fp) {
		int w = ip.getWidth(), h = ip.getHeight();
		if (w != fp.getWidth() || h!= fp.getHeight())
			return;
		for (int y=0; y < h; y++) {
			for (int x=0; x < w; x++) {
				if (fp.getf(x,y) > 200 )
					ip.setf(x, y, 255);
			}
		}
	}
}
