package cellTracking;

import java.util.ArrayList;

import ij.process.ImageProcessor;
import point.Point;

public class PenaltyFunction {

	/*
	 * calculates the penalty function between component with index=i1 in comp1 and
	 * i2 in comp. (So the less it is, the better, the more the chance they should
	 * be connected) Current problem with it: only counts for slice-slice, bad for
	 * slice-multislice mb change distance for overlap
	 */
	public static double penalFunctionNN(ImageComponentsAnalysis comp1, int i1, ImageComponentsAnalysis comp2, int i2,
			double maxRadius) {
		Point m1 = comp1.getComponentMassCenter(i1);
		Point m2 = comp2.getComponentMassCenter(i2);
		double dist = Point.dist(m1, m2);
		if (dist > maxRadius)
			return 100;

		int area1, area2;
		float circ1, circ2;
		float intensity1, intensity2;

		double p_circ, p_area, p_int, p_dist, p_overlap;
		area1 = comp1.getComponentArea(i1);
		circ1 = comp1.getComponentCircularity(i1);
		intensity1 = comp1.getComponentAvrgIntensity(i1);
		area2 = comp2.getComponentArea(i2);
		circ2 = comp2.getComponentCircularity(i2);
		intensity2 = comp2.getComponentAvrgIntensity(i2);
		p_area = normVal(area1, area2);
		p_circ = normVal(circ1, circ2);
		p_int = normVal(intensity1, intensity2);
		p_overlap = calculateOverlapScore(comp1, i1, comp2, i2);

		// ! Important to do this since we need penal function, so that less=better,
		// while SEG is opposite
		p_overlap = 1 - p_overlap;

		int minDist_in2 = findClosestPointIndex(m1, comp2, maxRadius);
		int minDist_in1 = findClosestPointIndex(m2, comp1, maxRadius);
		double minDist1 = Double.MAX_VALUE, minDist2 = Double.MAX_VALUE;

		// if closest component was not found closer than maxRadius, then let minDist be
		// huge, so score will be =1
		if (minDist_in2 != -1)
			minDist1 = Point.dist(m1, comp2.getComponentMassCenter(minDist_in2));
		if (minDist_in1 != -1)
			minDist2 = Point.dist(m2, comp1.getComponentMassCenter(minDist_in1));

		if (minDist_in1 == -1 && minDist_in2 == -1)
			p_dist = 1;
		else
			p_dist = normVal(Math.min(minDist1, minDist2), dist);

		// weights for area,circularity, avrg intensity, distance and overlap values
		double w_a = 0.8;
		double w_c = 0.2;
		double w_i = 0.4;
		double w_d = 1;
		double w_overlap = 0.8;
		double w_sum = w_a + w_c + w_i + w_d + w_overlap;
		w_a /= w_sum; // normalize value to [0,1]
		w_c /= w_sum;
		w_i /= w_sum;
		w_d /= w_sum;
		w_overlap /= w_sum;
		double penal = w_a * p_area + w_c * p_circ + w_i * p_int + w_d * p_dist + w_overlap * p_overlap;
		// System.out.format("Score between component with area %d, intensity %f, circ
		// %f %n", area1, intensity1, circ1);
		// System.out.format("and component with area %d, intensity %f, circ %f %n",
		// area2, intensity2, circ2);
		// System.out.format("with dist between them %f and min dist %f is %f %n%n",
		// dist, Math.min(minDist1, minDist2),
		// penal);
		return penal;
	}

	/* gets difference between value in [0,1] */
	private static double normVal(double v1, double v2) {
		double v = Math.abs(v1 - v2) / Math.sqrt(v1 * v1 + v2 * v2);
		return v;
	}

	/*
	 * returns index of the component in comp, which mass center is the closest to p
	 */
	private static int findClosestPointIndex(Point p, ImageComponentsAnalysis comp, double radius) {
		int min_i = -1;
		double min_dist = Double.MAX_VALUE;
		double dist;
		for (int i = 0; i < comp.getComponentsCount(); i++) {
			dist = Point.dist(p, comp.getComponentMassCenter(i));
			if (dist < radius && dist < min_dist) {
				min_dist = dist;
				min_i = i;
			}
		}
		return min_i;
	}

	/* calculates |A & B|/|A | B| where A,B are component masks */
	private static double calculateOverlapScore(ImageComponentsAnalysis comp1, int i1, ImageComponentsAnalysis comp2,
			int i2) {
		int union = 0, cross = 0;
		ImageProcessor compsImage1, compsImage2;
		int intensity1, intensity2;

		int x10, x11, y10, y11, x20, x21, y20, y21;
		x10 = comp1.getComponentX0(i1);
		x11 = comp1.getComponentX1(i1);
		y10 = comp1.getComponentY0(i1);
		y11 = comp1.getComponentY1(i1);

		x20 = comp2.getComponentX0(i2);
		x21 = comp2.getComponentX1(i2);
		y20 = comp2.getComponentY0(i2);
		y21 = comp2.getComponentY1(i2);

		intensity1 = comp1.getComponentDisplayIntensity(i1);
		intensity2 = comp2.getComponentDisplayIntensity(i2);

		union = comp1.getComponentArea(i1) + comp2.getComponentArea(i2); // - cross
		if (union == 0) {
			System.out.println("Trying to compute union score of components with 0 areas");
			return 1;
		}

		compsImage1 = comp1.getImageComponents();
		compsImage2 = comp2.getImageComponents();

		int x0, x1, y0, y1;
		x0 = Math.max(x10, x20);
		y0 = Math.max(y10, y20);
		x1 = Math.min(x11, x21);
		y1 = Math.min(y11, y21);

		int v1, v2;

		for (int y = y0; y <= y1; y++)
			for (int x = x0; x <= x1; x++) {
				v1 = compsImage1.get(x, y);
				v2 = compsImage2.get(x, y);
				if (v1 == intensity1 && v2 == intensity2)
					++cross;
			}
		union = union - cross;
		return (double) cross / union;
	}

}
