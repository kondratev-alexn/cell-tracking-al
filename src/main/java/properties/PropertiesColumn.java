package properties;

import ij.ImagePlus;
import statistics.Measure;

public class PropertiesColumn {
	ImagePlus imp;
	String name;
	Measure measure;
	
	public PropertiesColumn(ImagePlus image, String name, Measure measure) {
		imp = image;
		this.name = name;
		this.measure = measure;
	}
}
