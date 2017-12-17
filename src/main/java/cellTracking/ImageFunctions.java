package cellTracking;

import fiji.threshold.Auto_Threshold;
import ij.ImagePlus;
import ij.plugin.filter.RankFilters;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import inra.ijpb.morphology.Strel;
import point.Point;
import inra.ijpb.morphology.Morphology.Operation;

/* Class for different image processing functions, like maybe filtering for certain processors, or normalizing the image */
public class ImageFunctions {

	/*
	 * normalizes image from [ip.min,ip.max] to [minValue, maxValue]. Works in
	 * place.
	 */
	public static void normalize(ImageProcessor ip, float minValue, float maxValue) {
		// find max and min first
		float ipmin = Float.MAX_VALUE, ipmax = Float.MIN_VALUE;
		float v;
		for (int i = 0; i < ip.getPixelCount(); i++) {
			v = ip.getf(i);
			if (v < ipmin)
				ipmin = v;
			if (v > ipmax)
				ipmax = v;
		}

		// normalize
		for (int i = 0; i < ip.getPixelCount(); i++) {
			v = ip.getf(i);
			ip.setf(i, (v - ipmin) / (ipmax - ipmin) * (maxValue - minValue) + minValue);
		}
	}

	/* leave areas with values between minValue and maxValue */
	public static void threshold(ImageProcessor ip, double minValue, double maxValue) {
		int w = ip.getWidth(), h = ip.getHeight();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				/*
				 * if (ip.getf(x, y) < minValue) ip.setf(x, y, (float)minValue); else if
				 * (ip.getf(x, y) > maxValue) ip.setf(x, y, (float)maxValue);
				 */
				if (ip.getf(x, y) < minValue || ip.getf(x, y) > maxValue)
					ip.setf(x, y, 0);
				else
					ip.setf(x, y, 255);
			}
		}
	}

	public static ImageProcessor getWhiteObjectsMask(ImageProcessor ip, int openingRadius, int closingRadius) {
		ImageProcessor cellMask = ip.convertToByte(true);
		ImagePlus imp_mask = new ImagePlus("mask", cellMask);
		Auto_Threshold threshold = new Auto_Threshold();
		threshold.exec(imp_mask, "Otsu", false, false, true, false, false, false);
		cellMask = imp_mask.getProcessor();
		cellMask = ImageFunctions.operationMorph(cellMask, Operation.OPENING, Strel.Shape.SQUARE, openingRadius);
		cellMask = ImageFunctions.operationMorph(cellMask, Operation.CLOSING, Strel.Shape.DISK, closingRadius);
		return cellMask;
	}

	/* computes AND of two float-binary images and saves result into ip1 */
	public static void AND(ImageProcessor ip1, ImageProcessor ip2) {
		// if (ip1.getPixelCount() != ip2.getPixelCount()) return;
		for (int i = 0; i < ip1.getPixelCount(); i++) {
			ip1.setf(i, ip1.get(i) & ip2.get(i));
		}
	}

	/* computes OR of two float-binary images and saves result into ip1 */
	public static void OR(ImageProcessor ip1, ImageProcessor ip2) {
		// if (ip1.getPixelCount() != ip2.getPixelCount()) return;
		for (int i = 0; i < ip1.getPixelCount(); i++) {
			if (ip1.getf(i) > 10 || ip2.getf(i) > 10)
				ip1.setf(i, 255);
		}
	}

	/*
	 * returns the result of morphological operation "op" from MorpholibJ plugin
	 * with element type "shape" and "radius"
	 */
	public static ImageProcessor operationMorph(ImageProcessor ip, Operation op, Strel.Shape shape, int radius) {
		Strel strel = shape.fromRadius(radius);
		return op.apply(ip, strel);
	}

	/* copies src image to dest image */
	public static void Copy(ImageProcessor dest, ImageProcessor src) {
		for (int i = 0; i < src.getPixelCount(); i++) {
			dest.setf(i, src.getf(i));
		}
	}

	/*
	 * creates labeled markers from binary ones (markers are maxima/minima single
	 * points)
	 */
	public static void LabelMarker(ImageProcessor marker) {
		int nextLabel = 1;
		for (int i = 0; i < marker.getPixelCount(); i++) {
			if (marker.getf(i) > 0)
				marker.set(i, nextLabel++);
		}
	}

	/* Add markers from marker2 to marker1 */
	public static void addMarkers(ImageProcessor marker1, ImageProcessor marker2) {
		for (int i = 0; i < marker1.getPixelCount(); i++) {
			if (marker2.getf(i) > 0)
				marker1.set(i, 255);
		}
	}

	/* applies median filter with radius "radius", finds minimum and subtracts it */
	public static void subtractBackgroundMinMedian(ImageProcessor ip, double radius) {
		ImageProcessor median = ip.duplicate();
		if (radius != 0) {
			RankFilters filter = new RankFilters();
			filter.rank(median, radius, RankFilters.MEDIAN);
		}

		// ImagePlus plus = new ImagePlus("median", median);
		// plus.show();
		// find minimum
		float min = 5000;
		for (int i = 0; i < median.getPixelCount(); i++) {
			if (median.getf(i) < min)
				min = median.getf(i);
		}
		// System.out.println(min);
		// subtract minimum
		float v;
		for (int i = 0; i < ip.getPixelCount(); i++) {
			v = ip.getf(i) - min;
			ip.setf(i, v);
		}
	}

	/*
	 * merge markers that appear in the same components in comps. Component masks
	 * are generated through dilation with disk radius "d"
	 */
	public static void mergeMarkers(ImageProcessor markers, ImageComponentsAnalysis comps, int d) {
		if (comps == null)
			return;
		ImageProcessor mask;
		for (int i = 0; i < comps.getComponentsCount(); i++) {
			if (comps.getComponentArea(i) < 2000) { // dont do this for big background regions
				mask = comps.getDilatedComponentImage(i, d);
				ImageComponentsAnalysis.mergeMarkersByComponentMask(markers, mask, comps.getComponentX0(i) - d,
						comps.getComponentY0(i) - d);
			}
		}
	}

	/* draws '+' in point (x,y) inplace ip */
	public static void drawX(ImageProcessor ip, int x, int y) {
		ip.setf(x, y, (float) ip.getMax());
		if (x <= 0 || x >= ip.getWidth() - 1 || y <= 0 || y >= ip.getHeight() - 1)
			return;
		ip.setf(x + 1, y, (float) ip.getMax());
		ip.setf(x, y + 1, (float) ip.getMax());
		ip.setf(x - 1, y, (float) ip.getMax());
		ip.setf(x, y - 1, (float) ip.getMax());
	}

	public static void drawLine(ImageProcessor ip, int x0, int y0, int x1, int y1) {
		drawLine(ip, new Point(x0, y0), new Point(x1, y1));
	}

	/* draw line in-place */
	public static void drawLine(ImageProcessor ip, Point p0, Point p1) {
		double x0 = p0.getX();
		double y0 = p0.getY();
		double x1 = p1.getX();
		double y1 = p1.getY();
		double dx = (p1.getX() - p0.getX()) / Point.dist(p0, p1) / 2;
		double dy = (p1.getY() - p0.getY()) / Point.dist(p0, p1) / 2;

		float v = (float) ip.getMax();

		if (dx == 0 && dy == 0)
			return;
		// Abs(v[i]-v[i-1])<k*dx => v[
		if (dx > 0)
			for (int k = 0; (x0 + k * dx < x1); k++) {
				ip.setf((int) (x0 + k * dx), (int) (y0 + k * dy), v);
			}
		else if (dx < 0)
			for (int k = 0; (x0 + k * dx >= x1); k++) {
				ip.setf((int) (x0 + k * dx), (int) (y0 + k * dy), v);
			}
		else // dx==0
		{
			if (dy > 0)
				for (int k = 0; (y0 + k * dy < y1); k++) {
					ip.setf((int) (x0 + k * dx), (int) (y0 + k * dy), v);
				}
			else // dy<=0
				for (int k = 0; (y0 + k * dy >= y1); k++) {
					ip.setf((int) (x0 + k * dx), (int) (y0 + k * dy), v);
				}
		}

		/*
		 * int t; float dx = x1 - x0; float dy = y1 - y0; int ddx = dx > 0 ? 1 : -1; int
		 * ddy = dy > 0 ? 1 : -1; if (dx != 0) { float derr = Math.abs(dy / dx); // note
		 * that this division needs to be done in a way that preserves the // fractional
		 * part float err = 0; int y = y0; for (int x = x0; x <= x1; x+=ddx) {
		 * ip.setf(x, y, (float) ip.getMax()); err += derr; if (err >= 0.5) { y+=ddy;
		 * err -= 1; } } } else { // dx == 0, then x1=x0, draw for (int y = y0; y <= y1;
		 * y++) { ip.setf(x0, y, (float) ip.getMax()); } }
		 */
	}

	// Canny edge detection with thresholds t1,t2 and sigma-gaussian blur
	public static ImageProcessor Canny(ImageProcessor ip, double sigma, double t1, double t2, int offx, int offy,
			boolean useOtsuThreshold) {
		final int h = ip.getHeight();
		final int w = ip.getWidth();
		ImageProcessor Gx = ip.duplicate();
		ImageProcessor Gy = ip.duplicate();
		Gaussian gaus = new Gaussian();

		gaus.GaussianDerivativeX(Gx, (float) sigma);
		gaus.GaussianDerivativeY(Gy, (float) sigma);
		final double Pi8 = Math.PI / 8;
		final double Pi4 = Math.PI / 4;

		ImageProcessor Grad = ip.duplicate();
		gaus.GradientMagnitudeGaussian(Grad, (float) sigma);
		// Grad = operationMorph(Grad, Operation.EROSION, Strel.Shape.DISK, 1);
		normalize(Grad, 0, 255);
		ImageProcessor t = Grad.duplicate();
		double theta;
		double v1 = 0, v2 = 0;

		// Non-maximum supression
		for (int y = 1; y < h - 1; y++)
			for (int x = 1; x < w - 1; x++) {
				theta = Math.atan2(Gy.getf(x, y), Gx.getf(x, y));
				// theta = theta > Math.PI / 2 ? theta - Math.PI/2 : theta + Math.PI / 2; //
				// make orthogonal angle from -pi:pi
				// first type of gradient orientation - not so good
				if (theta <= Pi8 && theta > -Pi8 || theta <= -7 * Pi8 || theta > 7 * Pi8) // gradient is horizontal
				{
					if (t.getf(x, y) < t.getf(x - 1, y))
						Grad.setf(x, y, 0);
					if (t.getf(x, y) < t.getf(x + 1, y))
						Grad.setf(x, y, 0);
				} else if (theta > 3 * Pi8 && theta <= 5 * Pi8 || theta > -5 * Pi8 && theta <= -3 * Pi8) // grad is
																											// vertical
				{
					if (t.getf(x, y) < t.getf(x, y - 1))
						Grad.setf(x, y, 0);
					if (t.getf(x, y) < t.getf(x, y + 1))
						Grad.setf(x, y, 0);
				} else if (theta > -7 * Pi8 && theta <= -5 * Pi8 || theta <= 3 * Pi8 && theta > Pi8) // grad is 135
				{
					if (t.getf(x, y) < t.getf(x - 1, y - 1))
						Grad.setf(x, y, 0);
					if (t.getf(x, y) < t.getf(x + 1, y + 1))
						Grad.setf(x, y, 0);
				} else // grad is 45
				{
					if (t.getf(x, y) < t.getf(x + 1, y - 1))
						Grad.setf(x, y, 0);
					if (t.getf(x, y) < t.getf(x - 1, y + 1))
						Grad.setf(x, y, 0);
				}
				/*
				 * 
				 * if (theta > -Pi4 && theta <= 0 || theta > 3*Pi4 && theta <= Math.PI) { //e-se
				 * if (theta > 0) theta = theta - Math.PI; theta = - theta / Pi4; // norm to
				 * [0,1], 1 for se/nw v1 = (1-theta) * t.getf(x+1,y) + theta * t.getf(x+1,y+1);
				 * v2 = (1-theta) * t.getf(x-1,y) + theta * t.getf(x-1,y-1); } else if (theta >
				 * 0 && theta <= Pi4 || theta >= - Math.PI && theta <= -3*Pi4) { //e-ne if
				 * (theta < 0) theta = theta + Math.PI; theta = theta / Pi4; // [0,1], 1 for
				 * ne/sw v1 = (1-theta) * t.getf(x+1,y) + theta * t.getf(x+1,y-1); v2 =
				 * (1-theta) * t.getf(x-1,y) + theta * t.getf(x-1,y+1); } else if (theta > Pi4
				 * && theta <= 2*Pi4 || theta > -3*Pi4 && theta <= -2*Pi4) { //n-ne if (theta <
				 * 0) theta = theta + Math.PI; theta = (theta - Pi4) / Pi4; // [0,1], 1 for n/s
				 * v1 = (1-theta) * t.getf(x+1,y-1) + theta * t.getf(x,y-1); v2 = (1-theta) *
				 * t.getf(x-1,y+1) + theta * t.getf(x,y+1); } else { // n-nw if (theta < 0)
				 * theta = theta + Math.PI; theta = (theta - Math.PI/2) / Pi4; // [0,1], 1 for
				 * nw/se v1 = (1-theta) * t.getf(x,y-1) + theta * t.getf(x-1,y-1); v2 =
				 * (1-theta) * t.getf(x,y+1) + theta * t.getf(x+1,y+1); }
				 * System.out.println(theta); if (t.getf(x, y) < v1 || t.getf(x,y) < v2)
				 * Grad.setf(x,y, 0);
				 */
			}

		if (useOtsuThreshold) {
			t2 = otsu(t.convertToByteProcessor(true).getHistogram(), t.getPixelCount());
			t1 = t2 / 2.0;
		}

		// Double threshholding and tracking by hysteresis
		for (int y = 1; y < h - 1; y++)
			for (int x = 1; x < w - 1; x++) {
				if (Grad.getf(x, y) < t1)
					Grad.setf(x, y, 0);
				else if (Grad.getf(x, y) >= t1 && Grad.getf(x, y) < t2 && !CheckNearStrongEdge(Grad, x, y, t1, t2))
					Grad.setf(x, y, 0);
				else
					;// Grad[x, y] = 255;
			}

		for (int y = 0; y < h; y++)
			for (int x = 0; x < offx; x++) {
				Grad.setf(x, y, 0);
				Grad.setf(w - 1 - x, y, 0);
			}
		for (int x = 0; x < w; x++)
			for (int y = 0; y < offy; y++) {
				Grad.setf(x, y, 0);
				Grad.setf(x, h - 1 - y, 0);
			}

		for (int y = 1; y < h - 1; y++)
			for (int x = 1; x < w - 1; x++) {
				if (Grad.get(x, y) != 0)
					Grad.setf(x, y, 255);
			}
		return Grad;
	}

	/* return interpolated value between two pixels, clockwise-oriented */
	private float interpolatePixelByAngle(float p1, float p2, double theta) {
		theta = (theta + Math.PI) / Math.PI / 2.0; // norm to [0,1]
		return 0;
	}

	/*
	 * Function for Canny edge detection algorithm. Return true if edge at the point
	 * (x,y) belongs to the strong edge.
	 */
	private static boolean CheckNearStrongEdge(ImageProcessor grad, int x, int y, double t1, double t2) {
		if (grad.getf(x - 1, y - 1) >= t2)
			return true;
		if (grad.getf(x - 1, y + 1) >= t2)
			return true;
		if (grad.getf(x, y - 1) >= t2)
			return true;
		if (grad.getf(x, y + 1) >= t2)
			return true;
		if (grad.getf(x + 1, y - 1) >= t2)
			return true;
		if (grad.getf(x + 1, y + 1) >= t2)
			return true;
		if (grad.getf(x - 1, y) >= t2)
			return true;
		if (grad.getf(x + 1, y) >= t2)
			return true;
		return false;
	}

	/*
	 * calculates otsu threshold, taken from wikipedia. Histogram is 8-bit image
	 * histogtram, pixelsNumber is total number of pixels
	 */
	public static float otsu(int[] histogram, int pixelsNumber) {
		float sum = 0;
		for (int i = 0; i < histogram.length; i++) // normally it will be 255 but sometimes we want to change step
			sum += i * histogram[i];
		float sumB = 0, wB = 0, wF = 0, mB, mF, max = 0, between, threshold = 0;
		for (int i = 0; i < 256; ++i) {
			wB += histogram[i];
			if (wB == 0)
				continue;
			wF = pixelsNumber - wB;
			if (wF == 0)
				break;
			sumB += i * histogram[i];
			mB = sumB / wB;
			mF = (sum - sumB) / wF;
			between = (float) (wB * wF * Math.pow(mB - mF, 2));
			if (between > max) {
				max = between;
				threshold = i;
			}
		}
		return threshold;
	}

	/* draws circle of radius r around point (x,y) in ip */
	public static void drawCircle(ImageProcessor ip, float r, int x, int y, boolean drawCentre) {
		float maxv = Float.MIN_VALUE;
		for (int i = 0; i < ip.getPixelCount(); i++)
			if (ip.getf(i) > maxv)
				maxv = ip.getf(i);
		float eps = r;
		for (int j = (int) (y - r - 2); j < y + r + 1; j++)
			for (int i = (int) (x - r - 2); i < x + r + 1; i++)
				if (Math.abs((i - x) * (i - x) + (j - y) * (j - y) - r * r) < eps && i >= 0 && j >= 0
						&& i < ip.getWidth() && j < ip.getHeight())
					ip.setf(i, j, maxv);
		if (drawCentre)
			ip.setf(x, y, maxv);
	}

	public static void drawCirclesBySigmaMarkerks(ImageProcessor ip, ImageProcessor blobMarkers, boolean drawCentre) {
		float sigma;
		for (int y = 0; y < ip.getHeight(); y++)
			for (int x = 0; x < ip.getWidth(); x++) {
				sigma = blobMarkers.getf(x, y);
				if (sigma > 0)
					drawCircle(ip, sigma * 1.41f, x, y, drawCentre);
			}
	}

	/* draws gaussian on ip in (x,y) with dev sigma */
	public static void drawGaussian(ImageProcessor ip, int x, int y, float sigma) {
		final int r = (int) (3 * sigma + 1);
		float v;
		for (int j = y - r; j < y + r + 1; j++)
			for (int i = x - r; i < x + r + 1; i++) {
				if (j >= 0 && i >= 0 && i < ip.getWidth() && j < ip.getHeight()) {
					v = (float) Math.exp(-((i - x) * (i - x) + (j - y) * (j - y)) / sigma / sigma);
					ip.setf(i, j, v);
				}
			}
	}

	/* divides ip on lambda's absolute values if their sign is negative */
	public static void divideByNegativeValues(ImageProcessor ip, ImageProcessor lambda) {
		float v;
		final float eps = -0.01f;
		for (int i = 0; i < ip.getPixelCount(); i++) {
			v = lambda.getf(i);
			if (v < eps)
				ip.setf(i, ip.getf(i) / v);
		}
	}

	/*
	 * sets pixels below minValue to minValue, and higher than maxValue to maxValue
	 */
	public static void clippingIntensity(ImageProcessor ip, float minValue, float maxValue) {
		for (int i = 0; i < ip.getPixelCount(); i++) {
			if (ip.getf(i) < minValue)
				ip.setf(i, minValue);
			else if (ip.getf(i) > maxValue)
				ip.setf(i, maxValue);
		}
	}

	/* return binary image by 0-value thresholding */
	public static ImageProcessor getBinary(ImageProcessor fp) {
		int w = fp.getWidth(), h = fp.getHeight();
		ImageProcessor ip = new ByteProcessor(w, h);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (fp.getf(x, y) > 0)
					ip.setf(x, y, 255);
			}
		}
		return ip;
	}

	/* "convert" float binary image to byte binary image with values 0, 255 */
	public static void floatToByteBinary(ByteProcessor ip, FloatProcessor fp) {
		int w = ip.getWidth(), h = ip.getHeight();
		if (w != fp.getWidth() || h != fp.getHeight())
			return;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (fp.getf(x, y) > 200)
					ip.setf(x, y, 255);
			}
		}
	}

	/* "convert" byte binary image tofloat binary image with values 0, 255 */
	public static void byteToFloatBinary(FloatProcessor ip, ByteProcessor fp) {
		int w = ip.getWidth(), h = ip.getHeight();
		if (w != fp.getWidth() || h != fp.getHeight())
			return;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (fp.getf(x, y) > 200)
					ip.setf(x, y, 255);
			}
		}
	}
}
