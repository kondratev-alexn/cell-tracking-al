package point;

/* class representing 2d-point or vector. For convenient computation of distances between points */
public class Point {
	private double x;
	private double y;

	public Point(double _x, double _y) {
		x = _x;
		y = _y;
	}

	public void setX(double _x) {
		x = _x;
	}

	public void setY(double _y) {
		y = _y;
	}

	public void set_xy(double _x, double _y) {
		x = _x;
		y = _y;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public double dist() {
		return Math.sqrt(x * x + y * y);
	}

	/* arithmetic functions in place */
	public void addIn(Point other) {
		x += other.x;
		y += other.y;
	}

	public static Point center(Point p1, Point p2) {
		return new Point((p1.getX() + p2.getX()) / 2, (p1.getY() + p2.getY()) / 2);
	}

	public void subIn(Point other) {
		x -= other.x;
		y -= other.y;
	}

	public void multByConstIn(double c) {
		x *= c;
		y *= c;
	}

	public void divideByConstIn(double c) {
		x /= c;
		y /= c;
	}

	/* arithmetic functions returning Point */
	static public Point add(Point p1, Point p2) {
		return new Point(p1.x + p2.x, p1.y + p2.y);
	}

	// p1-p2
	static public Point sub(Point p1, Point p2) {
		return new Point(p1.x - p2.x, p1.y - p2.y);
	}

	static public Point multByConst(Point p, double c) {
		return new Point(c * p.x, c * p.y);
	}

	static public Point divideByConst(Point p, double c) {
		return new Point(p.x / c, p.y / c);
	}

	// static distance measure
	static public double dist(Point p1, Point p2) {
		return sub(p1, p2).dist();
	}

	// distance from this point to another
	public double distTo(Point pointTo) {
		return sub(this, pointTo).dist();
	}

	// swaps two points
	static public void swap(Point p1, Point p2) {
		double x1 = p1.getX(), y1 = p1.getY();
		p1.set_xy(p2.getX(), p2.getY());
		p2.set_xy(x1, y1);
	}

	@Override
	public String toString() {
		return "Point [x=" + x + ", y=" + y + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(x);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(y);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Point other = (Point) obj;
		if (Double.doubleToLongBits(x) != Double.doubleToLongBits(other.x))
			return false;
		if (Double.doubleToLongBits(y) != Double.doubleToLongBits(other.y))
			return false;
		return true;
	}
}
