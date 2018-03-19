package cellTracking;

import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import point.Point;

import java.util.ArrayList;

/* Blob detection using Hessian */
public class BlobDetector {

	private ImageProcessor ip;
	private ImageProcessor mask;
	/* array of sigmas used for different scales */
	private float[] scaleSigmas;
	/* stack of hessians with different sigmas */
	private Hessian[] hessians;

	BlobDetector(ImageProcessor image, ImageProcessor blobs_mask, float[] sigmas) {
		ip = image;
		mask = blobs_mask;
		scaleSigmas = sigmas;
		hessians = new Hessian[sigmas.length];
		for (int i = 0; i < hessians.length; i++) {
			hessians[i] = new Hessian(ip);
			hessians[i].calculateHessian(sigmas[i]);
		}
	}

	/* returns N maxima blobs as a list of points */
	public ArrayList<Point> findBlobsBy3x3LocalMaximaAsPoints(float thresholdLambda, boolean useLaplacian,
			int leaveNMax) {
		ImageProcessor stack[] = new ImageProcessor[hessians.length];
		ArrayList<Point> list = new ArrayList<Point>(5);
		for (int z = 0; z < hessians.length; z++) {
			if (useLaplacian) {
				stack[z] = hessians[z].getLambda2();
				ImageProcessorCalculator.add(stack[z], hessians[z].getLambda1()); // laplacian
			} else
				stack[z] = hessians[z].getLambda1();
		}
		ByteProcessor result = new ByteProcessor(ip.getWidth(), ip.getHeight());
		ArrayList<Float> maxima_v = new ArrayList<Float>(20);
		ArrayList<Integer> maxima_x = new ArrayList<Integer>(20);
		ArrayList<Integer> maxima_y = new ArrayList<Integer>(20);
		ArrayList<Integer> maxima_z = new ArrayList<Integer>(20);

		for (int y = 1; y < ip.getHeight() - 1; y++)
			for (int x = 1; x < ip.getWidth() - 1; x++) {
				result.setf(x, y, 0);
				if (mask != null && mask.get(x, y) <= 0)
					continue;
				for (int z = 0; z < hessians.length; z++) {
					if (isLocalMaximumThresholded3D(stack, x, y, z, thresholdLambda))
						if (leaveNMax != -1) {
							maxima_x.add(x);
							maxima_y.add(y);
							maxima_z.add(z);
							maxima_v.add(stack[z].getf(x, y));
						} else {
							list.add(new Point(x, y));
						}
				}
			}
		if (leaveNMax != -1) {
			// sort max list
			int max_j = -1;
			float max = -1;
			float t;
			int tx, ty, tz;
			for (int i = 0; i < maxima_v.size(); i++) {
				max = -1;
				for (int j = i; j < maxima_v.size(); j++) {
					// find max
					if (maxima_v.get(j) > max) {
						max = maxima_v.get(j);
						max_j = j;
					}
				}
				// swap max element and current element
				t = maxima_v.get(max_j);
				tx = maxima_x.get(max_j);
				ty = maxima_y.get(max_j);
				tz = maxima_z.get(max_j);

				maxima_v.set(max_j, maxima_v.get(i));
				maxima_x.set(max_j, maxima_x.get(i));
				maxima_y.set(max_j, maxima_y.get(i));
				maxima_z.set(max_j, maxima_z.get(i));

				maxima_v.set(i, t);
				maxima_x.set(i, tx);
				maxima_y.set(i, ty);
				maxima_z.set(i, tz);
			}
			
			int x, y;
			Point p;
			for (int i = 0; i < Math.min(leaveNMax, maxima_v.size()); i++) {
				x = maxima_x.get(i);
				y = maxima_y.get(i);
				p = new Point(x, y);
				list.add(p);
			}
		}
		return list;
	}

	/*
	 * return image with dots corresponding to blob centers and their intensities -
	 * to sigmas. 'leaveNMax' parameter corresponds to how many maxima point choose.
	 * Set to -1 if you want all points above threshold. If useLaplacian is false, then lambda1 will be used
	 */
	public ByteProcessor findBlobsBy3x3LocalMaximaAsImage(float thresholdLambda, boolean binary, boolean useLaplacian,
			int leaveNMax) {
		ImageProcessor stack[] = new ImageProcessor[hessians.length];
		for (int z = 0; z < hessians.length; z++) {
			if (useLaplacian) {
				stack[z] = hessians[z].getLambda2();
				ImageProcessorCalculator.add(stack[z], hessians[z].getLambda1()); // laplacian
			} else
				stack[z] = hessians[z].getLambda1();
			// ImageProcessorCalculator.linearCombination(0.5f, stack[z], 0.5f,
			// hessians[z].getLambda2());
		}
		ByteProcessor result = new ByteProcessor(ip.getWidth(), ip.getHeight());
		ArrayList<Float> maxima_v = new ArrayList<Float>(20);
		ArrayList<Integer> maxima_x = new ArrayList<Integer>(20);
		ArrayList<Integer> maxima_y = new ArrayList<Integer>(20);
		ArrayList<Integer> maxima_z = new ArrayList<Integer>(20);

		for (int y = 1; y < ip.getHeight() - 1; y++)
			for (int x = 1; x < ip.getWidth() - 1; x++) {
				result.setf(x, y, 0);
				if (mask != null && mask.get(x, y) <= 0)
					continue;
				for (int z = 0; z < hessians.length; z++) {
					if (isLocalMaximumThresholded3D(stack, x, y, z, thresholdLambda))
						// normalizeValue0_255(scaleSigmas[z], scaleSigmas[0],
						// scaleSigmas[scaleSigmas.length - 1]));
						if (leaveNMax != -1) {
							maxima_x.add(x);
							maxima_y.add(y);
							maxima_z.add(z);
							maxima_v.add(stack[z].getf(x, y));
						} else {
							if (binary)
								result.set(x, y, 255);
							else
								result.setf(x, y, scaleSigmas[z]);
						}
				}
			}
		if (leaveNMax != -1) {
			// sort max list
			int max_j = -1;
			float max = -1;
			float t;
			int tx, ty, tz;
			for (int i = 0; i < maxima_v.size(); i++) {
				max = -1;
				for (int j = i; j < maxima_v.size(); j++) {
					// find max
					if (maxima_v.get(j) > max) {
						max = maxima_v.get(j);
						max_j = j;
					}
				}
				// swap max element and current element
				t = maxima_v.get(max_j);
				tx = maxima_x.get(max_j);
				ty = maxima_y.get(max_j);
				tz = maxima_z.get(max_j);

				maxima_v.set(max_j, maxima_v.get(i));
				maxima_x.set(max_j, maxima_x.get(i));
				maxima_y.set(max_j, maxima_y.get(i));
				maxima_z.set(max_j, maxima_z.get(i));

				maxima_v.set(i, t);
				maxima_x.set(i, tx);
				maxima_y.set(i, ty);
				maxima_z.set(i, tz);
			}

			// for (int i = 0; i < maxima_v.size(); i++) {
			// System.out.printf("%.5f \n", maxima_v.get(i));
			// }
			// System.out.println();

			int x, y;
			for (int i = 0; i < Math.min(leaveNMax, maxima_v.size()); i++) {
				x = maxima_x.get(i);
				y = maxima_y.get(i);
				if (binary)
					result.set(x, y, 255);
				else
					result.setf(x, y, scaleSigmas[maxima_z.get(i)]);
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
