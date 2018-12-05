package statistics;

import java.awt.Point;

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
		ImageProcessor ip = imp.getStack().getProcessor(roi.getPosition() + 1);
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
		return String.format("%.5f", val);
	}
}
