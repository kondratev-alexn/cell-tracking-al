package statistics;

import ij.ImagePlus;
import ij.gui.Roi;

public class CoefficientOfVariation implements Measure {
	
	@Override
	public String name() {
		return "CoefVar";
	}

	@Override
	public double calculate(Roi roi, ImagePlus imp) {
		Measure mean = new Mean();
		Measure sd = new StandartDeviation();
		double meanv = mean.calculate(roi, imp);
		double sdv = sd.calculate(roi, imp);
		return sdv/meanv;
	}

	@Override
	public String toString(double val) {
		return String.format("%.5f", val); 
	}


}
