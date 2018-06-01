package point;

/* simple wrapper class, adding scale and "power" on this scale (ex. laplacian value) */
public class PointWithScale {
	public Point point;
	public double sigma;
	public double value;

	public PointWithScale(Point p, double sigmaScale, double value) {
		point = p;
		sigma = sigmaScale;
		this.value = value;
	}

	public PointWithScale(double x, double y, double sigmaScale, double value) {
		point = new Point(x, y);
		sigma = sigmaScale;
		this.value = value;
	}

	static public void swap(PointWithScale pws1, PointWithScale pws2) {
		Point.swap(pws1.point, pws2.point);
		double t;
		t = pws1.sigma;
		pws1.sigma = pws2.sigma;
		pws2.sigma = t;

		t = pws1.value;
		pws1.value = pws2.value;
		pws2.value = t;
	}

	/*
	 * calculates values based on point's 'value' and distance to the
	 * 'distancePoint'. Used for sorting the points. (Distance value is 'radial'
	 * function ~ 1-r)
	 */
	public double sortValue(Point distancePoint, double maxDistance) {
		double w_d, w_v;
		w_d = 0.5;
		w_v = 0.5;

		double dist = Point.dist(point, distancePoint);
		double dist_v = 0;
		if (dist < maxDistance)
			dist_v = 1 - dist / maxDistance;

		return value * w_v + dist_v * w_d;
	}

	@Override
	public String toString() {
		return "PointWithScale [point=" + point + ", sigma=" + sigma + ", value=" + value + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((point == null) ? 0 : point.hashCode());
		long temp;
		temp = Double.doubleToLongBits(sigma);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(value);
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
		PointWithScale other = (PointWithScale) obj;
		if (point == null) {
			if (other.point != null)
				return false;
		} else if (!point.equals(other.point))
			return false;
		if (Double.doubleToLongBits(sigma) != Double.doubleToLongBits(other.sigma))
			return false;
		if (Double.doubleToLongBits(value) != Double.doubleToLongBits(other.value))
			return false;
		return true;
	}
}
