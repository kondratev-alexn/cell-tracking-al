package cell_tracking;

/* Class for information about connected components*/
public class ComponentProperties {
	public int intensity; //intensity used for the component in the image
	public float perimeter;
	public int area;
	public float circularity;
	public int xmin, xmax, ymin, ymax;	//corner coordinates for containing rectangle
	
	public void calcCircularity() {
		circularity = (perimeter==0) ? 0 : (float) (4*Math.PI*area/perimeter/perimeter);
		if (circularity > 1) circularity = 1;
	}
	
	@Override
	public String toString() {
		return "Comp #: " + intensity + ", Perimeter: " + perimeter + ", Area: " + area + ", Circularity: " + circularity + 
				", Bounding box: (" + xmin + ", " + ymin + ") - (" + xmax + ", " + ymax + ").";
	}
}
