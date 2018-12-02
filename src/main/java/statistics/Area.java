package statistics;

import java.awt.Point;

import ij.ImagePlus;
import ij.gui.Roi;

public class Area implements Measure {
	
	@Override
	public String name() {
		return "Area";
	}

	@Override
	public double calculate(Roi roi, ImagePlus imp) {
		int count = 0;
		for (Point p: roi) {
			++count;
		}
		return count;
	}

	@Override
	public String toString(double val) {
		return String.format("%.0f", val);
	}


}
