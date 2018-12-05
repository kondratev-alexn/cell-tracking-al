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
		ImageProcessor ip = imp.getStack().getProcessor(roi.getPosition() + 1);
		double mean = 0;
		int count = 0;
		for (Point p : roi) {
			if (Measure.isPointIn(p, ip)) {
				mean += ip.getf(p.x, p.y);
				++count;
			}
		}
		mean /= count;

		double sd = 0;
		for (Point p : roi) {
			if (Measure.isPointIn(p, ip)) {
				int v = ip.get(p.x, p.y);
				sd += (v - mean) * (v - mean);
			}
		}
		sd = Math.sqrt(sd / count);

		return sd;
	}

	@Override
	public String toString(double val) {
		return String.format("%.5f", val);
	}
}
