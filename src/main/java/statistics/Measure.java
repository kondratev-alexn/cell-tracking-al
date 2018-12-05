package statistics;

import java.awt.Point;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ImageProcessor;

public interface Measure {	
	public String name();
	public double calculate(Roi roi, ImagePlus imp);
	
	public String toString(double val);
	
	static boolean isPointIn(Point p, ImageProcessor ip) {
		if (p.x < 0)
			return false;
		if (p.y < 0)
			return false;
		if (p.x >= ip.getWidth())
			return false;
		if (p.y >= ip.getHeight())
			return false;
		return true;
	}
	
}
