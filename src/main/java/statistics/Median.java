package statistics;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;

public class Median implements Measure {

	@Override
	public String name() {
		return "Median";
	}

	@Override
	public double calculate(Roi roi, ImagePlus imp) {
		ImageProcessor ip = imp.getStack().getProcessor(roi.getPosition());
		Object pixels = ip.getPixels();
		if (pixels instanceof float[]) {
			// use sort
			ArrayList<Float> values = new ArrayList<Float>(100);
			for (Point p : roi) {
				if (Measure.isPointIn(p, ip)) {
					float v = ip.getf(p.x, p.y);
					if (Float.isFinite(v)) {
						values.add(v);
					}
				}
			}
			int size = values.size();
			Collections.sort(values);
			if (size % 2 == 0)
			    return ((double)values.get(size/2) + (double)values.get(size/2 - 1))/2;
			else
			    return (double) values.get(size/2);
		}

		// otherwise, use histogram
		int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
		int count = 0;

		for (Point p : roi) {
			if (Measure.isPointIn(p, ip)) {
				int v = ip.get(p.x, p.y);
				if (v < min)
					min = v;
				if (v > max)
					max = v;
				++count;
			}
		}

		int[] hist = new int[max - min + 1];
		for (Point p : roi) {
			if (Measure.isPointIn(p, ip)) {
				int v = ip.get(p.x, p.y);
				hist[v - min]++;
			}
		}

		double sum = 0;
		int i = min - 1;
		double halfCount = count / 2.0;
		do {
			sum += hist[++i - min];
		} while (sum <= halfCount && i < max);

		return i;
	}

	@Override
	public String toString(double val) {
		return String.format("%.3f", val);
	}
}
