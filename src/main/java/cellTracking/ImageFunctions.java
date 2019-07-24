package cellTracking;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.RGBImageFilter;
import java.util.ArrayList;

import ij.ImagePlus;
import ij.plugin.filter.RankFilters;
import ij.process.AutoThresholder;
import ij.process.AutoThresholder.Method;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import inra.ijpb.morphology.Strel;
import point.Point;
import visualization.ColorPicker;
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
	public static void thresholdMinMax(ImageProcessor ip, double minValue, double maxValue) {
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

	public static ImageProcessor maskThresholdMoreThan(ImageProcessor ip, double maxValue, ImageProcessor mask) {
		int w = ip.getWidth(), h = ip.getHeight();
		ImageProcessor result = new ByteProcessor(w, h);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (mask != null && mask.getf(x, y) < 1)
					continue;
				if (ip.getf(x, y) > maxValue)
					result.set(x, y, 255);
				else
					result.setf(x, y, 0);
			}
		}
		return result;
	}

	public static ImageProcessor maskThresholdLessThan(ImageProcessor ip, double minValue, ImageProcessor mask) {
		int w = ip.getWidth(), h = ip.getHeight();
		ImageProcessor result = new ByteProcessor(w, h);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (mask != null && mask.getf(x, y) < 1)
					continue;
				if (ip.getf(x, y) < minValue)
					result.set(x, y, 255);
				else
					result.setf(x, y, 0);
			}
		}
		return result;
	}

	public static ImageProcessor getWhiteObjectsMask(ImageProcessor ip, int openingRadius, int closingRadius) {
		ImageProcessor cellMask = ip.convertToByte(true);
		AutoThresholder threshold = new AutoThresholder();
		threshold.getThreshold(Method.Otsu, cellMask.getHistogram());
		//threshold.exec(imp_mask, "Otsu", false, false, true, false, false, false);
		cellMask.setAutoThreshold("Otsu", true, 1);
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
	 * merges markers by thresholding the derivative of the intensity profile
	 * between the blobs
	 */
	public static ImageProcessor mergeBinaryMarkersInTheSameRegion(ImageProcessor ip, ImageProcessor markerIp,
			double mergeRadius, float derivativeThreshold) {
		ArrayList<Point> markers = new ArrayList<Point>(20);
		for (int y = 0; y < markerIp.getHeight(); y++)
			for (int x = 0; x < markerIp.getWidth(); x++) {
				if (markerIp.getf(x, y) > 0)
					markers.add(new Point(x, y));
			}

		// System.out.println(markers);

		boolean merged = false;
		Point p1, p2, p;
		for (int i = 0; i < markers.size(); i++) {
			p1 = markers.get(i);
			merged = false;
			for (int j = i + 1; j < markers.size(); j++) {
				p2 = markers.get(j);
				if (p1.distTo(p2) > mergeRadius)
					continue;
				// if (arePointsInSameRegion(ip, p1.getX(), p1.getY(), p2.getX(), p2.getY(),
				// derivativeThreshold)) {
				if (arePointsInSameRegionLongerProfiles(ip, p1.getX(), p1.getY(), p2.getX(), p2.getY(),
						derivativeThreshold)) {
					// merge markers
					p = Point.center(p1, p2);
					markers.add(j + 1, p);
					markers.remove(j); // remove j first, since j>i
					markers.remove(i);
					merged = true;
					i = -1;

					// System.out.println(markers);
					break;
				}
			}
		}

		ImageProcessor result = new ByteProcessor(ip.getWidth(), ip.getHeight());
		int x, y;
		for (int i = 0; i < markers.size(); i++) {
			x = (int) markers.get(i).getX();
			y = (int) markers.get(i).getY();
			result.set(x, y, 255);
		}
		// ImagePlus imp = new ImagePlus("hey", result);
		// imp.show();
		return result;
	}

	/*
	 * ip should be normalized so that total change in the function can be
	 * adequately thresholded
	 */
	private static boolean arePointsInSameRegion(ImageProcessor ip, double x1, double y1, double x2, double y2,
			float totalVariationThreshold) {
		int nPoints = (int) Point.dist(new Point(x1, y1), new Point(x2, y2)) + 1;
		if (nPoints < 3)
			return true;
		float[] values = new float[nPoints];
		float[] derivative = new float[nPoints - 1];
		double dx = (x2 - x1) / (nPoints - 1);
		double dy = (y2 - y1) / (nPoints - 1);
		double x, y;
		float max = Float.MIN_VALUE, min = Float.MAX_VALUE;

		for (int i = 0; i < nPoints; i++) {
			x = x1 + i * dx;
			y = y1 + i * dy;
			values[i] = bilinearValue(ip, x, y);
			if (values[i] < min)
				min = values[i];
			if (values[i] > max)
				max = values[i];
		}

		// for (int i = 0; i < nPoints; i++) {
		// values[i] = (values[i] - min) / (max - min);
		// }

		float totalVariation = 0;
		float maxDerivative = Float.MIN_VALUE;
		for (int i = 0; i < nPoints - 1; i++) {
			derivative[i] = values[i + 1] - values[i];
			if (Math.abs(derivative[i]) > maxDerivative)
				maxDerivative = Math.abs(derivative[i]);
			// totalVariation += Math.abs(derivative[i]);
		}

		// System.out.println("deriv sum between points (" + x1 + ", " + y1 + ") and ("
		// + x2 + ", " + y2 + ") is " + derivativeSum);
		// for (int i=0; i< nPoints; i++)
		// System.out.print(values[i] + ", ");
		// System.out.println();

		// return totalVariation < totalVariationThreshold;
		return maxDerivative < totalVariationThreshold;
	}

	private static boolean arePointsInSameRegionLongerProfiles(ImageProcessor ip, double x1, double y1, double x2,
			double y2, double threshold) {
		int nPoints = (int) Point.dist(new Point(x1, y1), new Point(x2, y2)) + 1;
		if (nPoints < 3)
			return true;

		// first create longer profile, namely add k*length of the profile length to
		// start and end
		double k = nPoints > 10 ? 1.5 : 2;
		double ddx = x2 - x1;
		double ddy = y2 - y1;
		double x_start = x1 - k * ddx;
		double y_start = y1 - k * ddy;
		double x_end = x2 + k * ddx;
		double y_end = y2 + k * ddy;

		if (x_start < 0)
			x_start = 0;
		if (x_start > ip.getWidth() - 2)
			x_start = ip.getWidth() - 2;
		if (x_end < 0)
			x_end = 0;
		if (x_end > ip.getWidth() - 2)
			x_end = ip.getWidth() - 2;
		if (y_start < 0)
			y_start = 0;
		if (y_start > ip.getHeight() - 2)
			y_start = ip.getHeight() - 2;
		if (y_end < 0)
			y_end = 0;
		if (y_end > ip.getHeight() - 2)
			y_end = ip.getHeight() - 2;

		nPoints = (int) Point.dist(new Point(x_start, y_start), new Point(x_end, y_end)) + 1;

		float[] profile = new float[nPoints];
		float[] derivative = new float[nPoints - 1];
		double dx = (x_end - x_start) / (nPoints - 1);
		double dy = (y_end - y_start) / (nPoints - 1);
		double x, y;
		float max = Float.MIN_VALUE, min = Float.MAX_VALUE;
		float max_inner = Float.MIN_VALUE, min_inner = Float.MAX_VALUE;

		for (int i = 0; i < nPoints; i++) {
			x = x_start + i * dx;
			y = y_start + i * dy;
			profile[i] = bilinearValue(ip, x, y);
			if (profile[i] < min)
				min = profile[i];
			if (profile[i] > max)
				max = profile[i];
			if (dx != 0) {
				if ((dx > 0 && x >= x1 || dx < 0 && x <= x1) && (dx > 0 && x <= x2 || dx < 0 && x >= x2)) {
					if (profile[i] < min_inner)
						min_inner = profile[i];
					if (profile[i] > max_inner)
						max_inner = profile[i];
				}
			} else {
				if ((dy > 0 && y >= y1 || dy < 0 && y <= y1) && (dy > 0 && y <= y2 || dy < 0 && y >= y2)) {
					if (profile[i] < min_inner)
						min_inner = profile[i];
					if (profile[i] > max_inner)
						max_inner = profile[i];
				}
			}
		}
		// normalize
		for (int i = 0; i < nPoints; i++) {
			profile[i] = (profile[i] - min) / (max - min);
		}
		min_inner = (min_inner - min) / (max - min);
		max_inner = (max_inner - min) / (max - min);

		double div_inner = max_inner - min_inner;

//		if (div_inner < threshold) {
//			System.out.println("printing profile for merging points (" + x1 + ", " + y1 + ") and (" + x2 + ", " + y2 + "), div "
//					+ div_inner);
//			for (int i = 0; i < nPoints; i++) {
//				System.out.print(profile[i] + ", ");
//			}
//			System.out.println();
//		} else {
//			System.out.println("***");
//			System.out.println("printing profile for not merged points (" + x1 + ", " + y1 + ") and (" + x2 + ", " + y2 + "), div "
//					+ div_inner);
//			for (int i = 0; i < nPoints; i++) {
//				System.out.print(profile[i] + ", ");
//			}
//			System.out.println();
//		}

		return div_inner < threshold;
	}

	private static float bilinearValue(ImageProcessor ip, double x, double y) {
		float f00, f01, f10, f11;
		int xx = (int) x;
		int yy = (int) y;
		double dx = x - xx;
		double dy = y - yy;

		f00 = ip.getf(xx, yy);
		f01 = ip.getf(xx, yy + 1);
		f10 = ip.getf(xx + 1, yy);
		f11 = ip.getf(xx + 1, yy + 1);

		return (float) (f00 * (1 - dx) * (1 - dy) + f01 * (1 - dx) * dy + f10 * dx * (1 - dy) + f11 * dx * dy);
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
	public static void drawCircle(ImageProcessor ip, float r, int x, int y, float intensity, boolean drawCentre,
			boolean fill) {
		// float maxv = Float.MIN_VALUE;
		// for (int i = 0; i < ip.getPixelCount(); i++)
		// if (ip.getf(i) > maxv)
		// maxv = ip.getf(i);
		float eps = r;
		for (int j = (int) (y - r - 2); j < y + r + 1; j++)
			for (int i = (int) (x - r - 2); i < x + r + 1; i++)
				if (i >= 0 && j >= 0 && i < ip.getWidth() && j < ip.getHeight())
					if (fill) {
						if (((i - x) * (i - x) + (j - y) * (j - y)) <= r * r)
							ip.setf(i, j, intensity);
					} else if (Math.abs((i - x) * (i - x) + (j - y) * (j - y) - r * r) < eps)
						ip.setf(i, j, intensity);
		if (drawCentre)
			ip.setf(x, y, intensity);
	}

	public static void drawCirclesBySigmaMarkerks(ImageProcessor ip, ImageProcessor blobMarkers, boolean drawCentre,
			boolean fill) {
		float sigma;
		float maxv = Float.MIN_VALUE;
		for (int i = 0; i < ip.getPixelCount(); i++)
			if (ip.getf(i) > maxv)
				maxv = ip.getf(i);
		for (int y = 0; y < ip.getHeight(); y++)
			for (int x = 0; x < ip.getWidth(); x++) {
				sigma = blobMarkers.getf(x, y);
				if (sigma > 0)
					drawCircle(ip, sigma * 1.41f, x, y, maxv, drawCentre, fill);
			}
	}

	public static void colorCirclesBySigmaMarkers(ImageProcessor ip, ImageProcessor blobMarkers, boolean drawCentre,
			boolean drawDot, int lineWidth) {
		ImageProcessor circles = new ByteProcessor(blobMarkers.getWidth(), blobMarkers.getHeight());
		drawCirclesBySigmaMarkerks(circles, blobMarkers, drawCentre, false);
		ColorProcessor cim = new ColorProcessor(blobMarkers.getWidth(), blobMarkers.getHeight());
		// cim.setChannel(2, circles.convertToByteProcessor());
		cim = ip.duplicate().convertToColorProcessor();
		Color green = new Color(0, 255, 0);
		cim.setColor(green);
		cim.setLineWidth(lineWidth);

		double sigma;
		int radius;
		for (int y = 0; y < ip.getHeight(); y++)
			for (int x = 0; x < ip.getWidth(); x++) {
				sigma = blobMarkers.getf(x, y);
				if (sigma > 0) {
					if (drawDot)
						cim.drawDot(x,y);
					else {
						radius = (int) (sigma * 1.41f);
						cim.drawOval(x - radius, y - radius, 2 * radius + 1, 2 * radius + 1);
						cim.drawOval(x - radius + 1, y - radius + 1, 2 * radius - 1, 2 * radius - 1);
					}
				}
				// drawCircle(ip, sigma * 1.41f, x, y, maxv, drawCentre, fill);
			}

		ImagePlus res = new ImagePlus("colored", cim);
		res.show();
	}
	
	public static void colorWatershedBasins(ImageProcessor basins) {
		ColorProcessor cim = new ColorProcessor(basins.getWidth(), basins.getHeight());
		for (int y = 0; y < cim.getHeight(); y++)
			for (int x = 0; x < cim.getWidth(); x++) {
				int intensity = (int)basins.getf(x, y);
				Color color = ColorPicker.color(intensity);
				cim.setColor(color);
				cim.drawPixel(x, y);
			}
		ImagePlus res = new ImagePlus("basins colored", cim);
		res.show();
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
