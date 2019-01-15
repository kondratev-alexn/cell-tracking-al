package statistics;

import java.awt.Point;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;

public class Max implements Measure {

	@Override
	public String name() {		
		return "Max";
	}

	@Override
	public double calculate(Roi roi, ImagePlus imp) {
		ImageProcessor ip = imp.getStack().getProcessor(roi.getPosition());
		double max = Double.MIN_VALUE;
		for (Point p : roi) {
			if (Measure.isPointIn(p, ip)) {
				float v = ip.getf(p.x, p.y);
				if (Float.isFinite(v)) {
					if (max<v)
						max=v;
				}
			}
		}
		return max;
	}

	@Override
	public String toString(double val) {
		return String.format("%.3f", val);
	}

}
