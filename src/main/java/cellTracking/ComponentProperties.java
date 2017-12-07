package cellTracking;

import point.Point;

enum State {
	NORMAL, MITOSIS, DIVIDING
}

/* Class for information about connected components */
public class ComponentProperties {
	public int displayIntensity; // intensity used for the component in the image
	public float avrgIntensity; // average intensity of the component
	public float perimeter;
	public int area;
	public float circularity;
	public int xmin, xmax, ymin, ymax; // corner coordinates for containing rectangle

	public boolean trackedDown; // true if component was tracked during tracking
	public State state = State.NORMAL;

	public Point massCenter; // point - center of mass of the component

	public void calcCircularity() {
		circularity = (perimeter == 0) ? 0 : (float) (4 * Math.PI * area / perimeter / perimeter);
		if (circularity > 1)
			circularity = 1;
	}

	public void setDefaultValues(int w, int h) {
		displayIntensity = -1;
		avrgIntensity = 0;
		perimeter = 0;
		area = 0;
		circularity = 0;
		xmin = w - 1;
		xmax = 0;
		ymin = h - 1;
		ymax = 0;
		trackedDown = false;
		state = State.NORMAL;
		massCenter = new Point(0, 0);
	}

	@Override
	public String toString() {
		return "Comp #: " + displayIntensity + ", Perimeter: " + perimeter + ", Area: " + area + ", Circularity: "
				+ circularity + ", Bounding box: (" + xmin + ", " + ymin + ") - (" + xmax + ", " + ymax + ").";
	}
}
