package statistics;

import java.awt.Point;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;

public class StandartDeviation implements Measure {

	@Override
	public String name() {
		return "StdDev";
	}

	@Override
	public double calculate(Roi roi, ImagePlus imp) {
		ImageProcessor ip = imp.getStack().getProcessor(roi.getPosition());
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

		double sd = 0;
		for (Point p : roi) {
			if (Measure.isPointIn(p, ip)) {
				float v = ip.getf(p.x, p.y);
				if (Float.isFinite(v)) {
					sd += (v - mean) * (v - mean);
				}
			}
		}
		sd = Math.sqrt(sd / count);

		return sd;
	}

	@Override
	public String toString(double val) {
		return String.format("%.3f", val);
	}
}
