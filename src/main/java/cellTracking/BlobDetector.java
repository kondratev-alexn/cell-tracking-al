package cellTracking;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

/* Blob detection using Hessian */
public class BlobDetector {

	private ImageProcessor ip;
	/* array of sigmas used for different scales */
	private float[] scaleSigmas;
	/* stack of hessians with different sigmas */
	private Hessian[] hessians;

	BlobDetector(ImageProcessor image, float[] sigmas) {
		ip = image;
		scaleSigmas = sigmas;
		hessians = new Hessian[sigmas.length];
		for (int i = 0; i < hessians.length; i++) {
			hessians[i] = new Hessian(ip);
			hessians[i].calculateHessian(sigmas[i]);
		}
	}

	/*
	 * return image with dots corresponding to blob centers and their intensities -
	 * to sigmas
	 */
	public ByteProcessor findBlobsBy3x3LocalMaxima(float thresholdLambda, boolean binary, boolean useLambda2) {
		ImageProcessor stack[] = new ImageProcessor[hessians.length];
		for (int z = 0; z < hessians.length; z++) {
			if (useLambda2)
				stack[z] = hessians[z].getLambda2();
			else
				stack[z] = hessians[z].getLambda1();
			// ImageProcessorCalculator.linearCombination(0.5f, stack[z], 0.5f,
			// hessians[z].getLambda2());
		}
		ByteProcessor result = new ByteProcessor(ip.getWidth(), ip.getHeight());
		for (int y = 1; y < ip.getHeight() - 1; y++)
			for (int x = 1; x < ip.getWidth() - 1; x++) {
				result.setf(x, y, 0);
				for (int z = 1; z < hessians.length - 1; z++) {
					if (isLocalMaximumThresholded3D(stack, x, y, z, thresholdLambda))
						// normalizeValue0_255(scaleSigmas[z], scaleSigmas[0],
						// scaleSigmas[scaleSigmas.length - 1]));
						if (binary)
							result.set(x, y, 255);
						else
							result.setf(x, y, scaleSigmas[z]);
				}
			}
		return result;
	}

	/*
	 * return maximum sigmas image max(I_s1, ..., I_s2)
	 */
	public ImageProcessor findBlobsByMaxSigmasImage() {
		ImageProcessor stack[] = new ImageProcessor[hessians.length];
		for (int z = 0; z < hessians.length; z++) {
			stack[z] = hessians[z].getLambda2();
		}
		ImageProcessor sigmaMax = ip.duplicate();
		float max;
		for (int y = 0; y < ip.getHeight(); y++)
			for (int x = 0; x < ip.getWidth(); x++) {
				max = Float.MIN_VALUE;
				for (int z = 0; z < hessians.length; z++) {
					if (stack[z].getf(x, y) > max)
						max = stack[z].getf(x, y);
				}
				sigmaMax.setf(x, y, max);
			}
		return sigmaMax;
	}

	/*
	 * return true if point (x,y; z) in hessian stack is local maximum in 3x3x3
	 * region
	 */
	private boolean isLocalMaximumThresholded3D(ImageProcessor[] stack, int x, int y, int z, float threshold) {
		float p = stack[z].getf(x, y);
		if (Math.abs(p) < threshold)
			return false;
		for (int dx = -1; dx < 2; dx++)
			for (int dy = -1; dy < 2; dy++)
				for (int dz = -1; dz < 2; dz++) {
					if (z + dz < 0 || z + dz > stack.length - 1)
						continue;
					if (p < stack[z + dz].getf(x + dx, y + dy))
						return false;
				}
		return true;
	}

	/* aux. function */
	private float normalizeValue0_255(float v, float min, float max) {
		return (v - min) / (max - min) * 255;
	}

}
