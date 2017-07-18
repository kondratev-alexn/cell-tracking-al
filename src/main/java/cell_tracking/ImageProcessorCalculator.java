package cell_tracking;

import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

/* Class providing different mathematical operations on images 
 * All results are written into the first argument */
public class ImageProcessorCalculator {

	/* Adds two images, ip1 = ip1 + ip2 */
	public void add(ImageProcessor ip1, ImageProcessor ip2) {
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
	
	/* Subtract two images, ip1 = ip1 - ip2 */
	public void sub(ImageProcessor ip1, ImageProcessor ip2) {
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
	public void multiply(ImageProcessor ip1, ImageProcessor ip2) {
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
	
	public void threshold(ImageProcessor ip, double minValue, double maxValue) {
		int w = ip.getWidth(), h = ip.getHeight();
		for (int y=0; y < h; y++) {
			for (int x=0; x < w; x++) {
				 /*if (ip.getf(x, y) < minValue)
					ip.setf(x, y, (float)minValue);
				else if (ip.getf(x, y) > maxValue)
					ip.setf(x, y, (float)maxValue); */
				if (ip.getf(x, y) < minValue || ip.getf(x,y) > maxValue)
					ip.setf(x, y, 0);
				else ip.setf(x, y, 255);				
			}
		}
	}
	
	/* "convert" float binary image to byte binary image with values 0, 255 */
	public void floatToByteBinary(ByteProcessor ip, FloatProcessor fp) {
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
	public void byteToFloatBinary(FloatProcessor ip, ByteProcessor fp) {
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
