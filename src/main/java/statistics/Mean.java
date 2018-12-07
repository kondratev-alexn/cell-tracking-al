package statistics;

import java.awt.Point;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;

public class Mean implements Measure {

	@Override
	public String name() {
		return "Mean";
	}

	@Override
	public double calculate(Roi roi, ImagePlus imp) {
		ImageProcessor ip = imp.getStack().getProcessor(roi.getPosition() + 1);
		double mean = 0;
		int count = 0;
		for (Point p : roi) {
			if (Measure.isPointIn(p, ip)) {
				float v = ip.getf(p.x, p.y);
				if (Float.isFinite(v)) {
					mean += v;
					++count;
				}
			}
		}
		mean /= count;
		return mean;
	}

	@Override
	public String toString(double val) {
		return String.format("%.3f", val);
	}

}
