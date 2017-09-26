package cellTracking;

import ij.process.ImageProcessor;

/* Blob detection using Hessian */
public class BlobDetector {
	
	private ImageProcessor ip;
	/* array of sigmas used for different scales */
	private float[] scaleSigmas;
	
	BlobDetector(ImageProcessor image, float[] sigmas) {
		ip = image;
		scaleSigmas = sigmas;
	}
	
}
