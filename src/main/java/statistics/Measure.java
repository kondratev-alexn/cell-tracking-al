package statistics;

import ij.ImagePlus;
import ij.gui.Roi;

public interface Measure {	
	public String name();
	public double calculate(Roi roi, ImagePlus imp);
	
	public String toString(double val);
	
}
