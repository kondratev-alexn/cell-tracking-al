package statistics;

import ij.ImagePlus;
import ij.gui.Roi;

public class Slice implements Measure {

	@Override
	public String name() {
		return "Slice"; 
	}

	@Override
	public double calculate(Roi roi, ImagePlus imp) {
		return roi.getPosition();
	}
	

	@Override
	public String toString(double val) {
		return String.format("%.0f", val); 
	}

}
