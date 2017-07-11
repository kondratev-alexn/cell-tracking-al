package cell_tracking;

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
}
