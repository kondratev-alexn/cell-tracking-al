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

	static public double dist(Point p1, Point p2) {
		return sub(p1, p2).dist();
	}
}
